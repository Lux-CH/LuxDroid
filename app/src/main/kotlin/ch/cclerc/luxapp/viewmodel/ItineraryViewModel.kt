package ch.cclerc.luxapp.viewmodel

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import ch.cclerc.luxapp.domain.TripOption
import ch.cclerc.luxapp.domain.map.LatLng
import ch.cclerc.luxapp.domain.map.PolylineCodec
import ch.cclerc.luxapp.domain.map.VehicleVisualisation
import ch.cclerc.luxapp.domain.map.WalkingPathMetrics
import ch.cclerc.luxapp.domain.map.buildWalkingPathMetrics
import ch.cclerc.luxapp.domain.map.epochSeconds
import ch.cclerc.luxapp.domain.map.interpolateCoordinate
import ch.cclerc.luxapp.domain.map.interpolateOnPath
import ch.cclerc.luxapp.domain.map.reduceCoordinatesIfNeeded
import ch.cclerc.luxapp.ui.theme.defaultLegAccent
import ch.cclerc.luxapp.ui.theme.getLegColor
import ch.cclerc.luxcom.api.getTrip
import ch.cclerc.luxcom.model.Place
import ch.cclerc.luxcom.model.TransportationMode
import ch.cclerc.luxcom.model.trip.Itinerary
import ch.cclerc.luxcom.model.trip.Leg
import ch.cclerc.luxcom.relay.RelayClient
import ch.cclerc.luxcom.relay.RelayLiveFeed
import java.time.Instant
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class MapTrackingMode { NONE, FOLLOW, FOLLOW_WITH_HEADING }

@Immutable
data class RouteOverlay(
    val id: String,
    val coordinates: List<LatLng>,
    val color: Color
)

@Immutable
data class VehicleAnnotation(
    val id: String,
    val coordinate: LatLng,
    val routeShortName: String?,
    val color: Color
)

@Immutable
data class WalkingAnnotation(
    val id: String,
    val coordinate: LatLng
)

@Immutable
data class StopAnnotation(
    val id: String,
    val place: Place,
    val coordinate: LatLng,
    val color: Color,
    val isTerminal: Boolean,
    val isIntermediate: Boolean
) {
    companion object {
        fun of(
            place: Place,
            color: Color,
            isTerminal: Boolean = false,
            isIntermediate: Boolean = false
        ): StopAnnotation {
            val renamed = when (place.name) {
                "START" -> place.copy(name = "Début")
                "END" -> place.copy(name = "Fin")
                else -> place
            }
            return StopAnnotation(
                id = "${place.stopId ?: ""}_${place.name}_${place.lat}_${place.lon}",
                place = renamed,
                coordinate = LatLng(place.lat, place.lon),
                color = color,
                isTerminal = isTerminal,
                isIntermediate = isIntermediate
            )
        }
    }
}

@Immutable
data class MapBounds(
    val coordinates: List<LatLng>,
    val paddingFraction: Double = MAP_BOUNDS_PADDING_FRACTION
)

const val MAP_BOUNDS_PADDING_FRACTION: Double = 0.1

class ItineraryViewModel private constructor(
    initialTripId: String,
    initialItinerary: Itinerary?,
    val destinationName: String?,
    private val accent: Color
) {

    companion object {
        fun forTrip(
            tripId: String,
            otherTripOptions: List<TripOption> = emptyList(),
            accent: Color = defaultLegAccent()
        ): ItineraryViewModel = ItineraryViewModel(tripId, null, null, accent).also {
            it.setTripOptions(otherTripOptions)
        }

        fun forItinerary(
            itinerary: Itinerary,
            destinationName: String? = null,
            accent: Color = defaultLegAccent()
        ): ItineraryViewModel = ItineraryViewModel("", itinerary, destinationName, accent)

        private const val ZOOM_THRESHOLD_METERS = 50_000.0
        private const val MAX_ROUTE_OVERLAY_POINTS_PER_LEG = 450
        private const val MAX_WALKING_PATH_POINTS_PER_LEG = 220
        private val VEHICLE_UPDATE_INTERVAL = 2.seconds
        private val WALKING_UPDATE_INTERVAL = 1.seconds
        private val ITINERARY_FALLBACK_INTERVAL = 10.seconds
    }

    var tripId: String by mutableStateOf(initialTripId)
        private set

    var itinerary: Itinerary? by mutableStateOf(initialItinerary)
        private set

    var mapAnnotations: List<StopAnnotation> by mutableStateOf(emptyList())
        private set

    var routeOverlays: List<RouteOverlay> by mutableStateOf(emptyList())
        private set

    var vehicleAnnotations: List<VehicleAnnotation> by mutableStateOf(emptyList())
        private set

    var walkingAnnotations: List<WalkingAnnotation> by mutableStateOf(emptyList())
        private set

    var showingIntermediateStops: Boolean by mutableStateOf(true)
        private set

    var isLoading: Boolean by mutableStateOf(true)
        private set

    var errorMessage: String? by mutableStateOf(null)
        private set

    var selectedStop: Place? by mutableStateOf(null)

    var trackingMode: MapTrackingMode by mutableStateOf(MapTrackingMode.NONE)

    var otherTripOptions: List<TripOption> by mutableStateOf(emptyList())
        private set

    var isSwitchingTrip: Boolean by mutableStateOf(false)
        private set

    var targetBounds: MapBounds? by mutableStateOf(null)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val liveFeed = RelayLiveFeed<Itinerary>(scope)

    private var legKeyFrames: Map<String, List<VehicleVisualisation.KeyFrame>> = emptyMap()
    private var walkingLegPaths: Map<String, WalkingPathMetrics> = emptyMap()
    private var vehicleUpdateJob: Job? = null
    private var walkingUpdateJob: Job? = null
    private var shouldStop = false

    fun setTripOptions(options: List<TripOption>) {
        val seen = mutableSetOf<String>()
        otherTripOptions = options
            .sortedBy { it.startTime }
            .filter { seen.add(it.id) }
    }

    suspend fun loadItinerary() {
        if (shouldStop) return

        if (itinerary != null) {
            processItinerary(shouldCalculateMapPosition = true)
            isLoading = false
            startItineraryRefresh()
            return
        }

        isLoading = true
        errorMessage = null

        if (RelayClient.shared.isConnected.value) {
            startItineraryRefresh()
            return
        }

        try {
            val fetched = getTrip(tripId)
            itinerary = fetched
            processItinerary(shouldCalculateMapPosition = true)
            startItineraryRefresh()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            errorMessage = error.message ?: error.toString()
        }

        isLoading = false
    }

    suspend fun switchToTrip(tripId: String) {
        if (tripId == this.tripId) return

        isSwitchingTrip = true
        stopAllTasks()

        vehicleAnnotations = emptyList()
        walkingAnnotations = emptyList()
        this.tripId = tripId
        itinerary = null
        mapAnnotations = emptyList()
        routeOverlays = emptyList()
        targetBounds = null
        legKeyFrames = emptyMap()
        walkingLegPaths = emptyMap()
        errorMessage = null
        shouldStop = false
        selectedStop = null

        try {
            loadItinerary()
        } finally {
            isSwitchingTrip = false
        }
    }

    fun setCameraDistance(distance: Double) {
        val shouldShow = distance < ZOOM_THRESHOLD_METERS
        if (shouldShow == showingIntermediateStops) return
        showingIntermediateStops = shouldShow
    }

    fun cycleTrackingMode() {
        trackingMode = when (trackingMode) {
            MapTrackingMode.NONE -> MapTrackingMode.FOLLOW
            MapTrackingMode.FOLLOW -> MapTrackingMode.FOLLOW_WITH_HEADING
            MapTrackingMode.FOLLOW_WITH_HEADING -> MapTrackingMode.NONE
        }
    }

    fun disableTrackingIfNeeded() {
        if (trackingMode != MapTrackingMode.NONE) trackingMode = MapTrackingMode.NONE
    }

    fun stopAllTasks() {
        shouldStop = true
        liveFeed.stop()
        stopVehicleUpdates()
        stopWalkingUpdates()
    }

    fun cleanup() {
        stopAllTasks()
        scope.cancel()
    }

    private fun startItineraryRefresh() {
        if (tripId.isEmpty() || shouldStop) return

        liveFeed.stop()

        val currentTripId = tripId

        liveFeed.start(
            fallbackOnly = false,
            fallbackInterval = ITINERARY_FALLBACK_INTERVAL,
            stream = { RelayClient.shared.trip(currentTripId) },
            fallbackFetch = {
                try {
                    getTrip(currentTripId)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Throwable) {
                    null
                }
            },
            onUpdate = { newItinerary ->
                if (shouldStop) return@start
                val isInitialLoad = itinerary == null
                itinerary = newItinerary
                isLoading = false
                scope.launch { processItinerary(shouldCalculateMapPosition = isInitialLoad) }
            }
        )
    }

    private fun processItinerary(shouldCalculateMapPosition: Boolean) {
        val current = itinerary
        if (current == null || shouldStop) {
            errorMessage = "Missing itinerary data"
            return
        }

        val (annotations, overlays) = createAnnotationsAndOverlays(current)
        mapAnnotations = annotations
        routeOverlays = overlays

        if (shouldCalculateMapPosition && targetBounds == null) {
            calculateMapPosition()
        }

        prepareWalkingLegCoordinates(current.legs)
        prepareVehicleKeyFrames(current.legs)

        startVehicleUpdates()
        startWalkingUpdates()
    }

    fun legIdentifier(leg: Leg): String =
        "${leg.routeShortName ?: ""}_${leg.headsign ?: ""}_${leg.startTime.epochSecond}"

    private fun legColorOf(leg: Leg): Color = getLegColor(leg, false, accent)

    private fun createAnnotationsAndOverlays(
        itinerary: Itinerary
    ): Pair<List<StopAnnotation>, List<RouteOverlay>> {
        val annotations = mutableListOf<StopAnnotation>()
        val overlays = mutableListOf<RouteOverlay>()

        itinerary.legs.firstOrNull()?.let { firstLeg ->
            annotations.add(
                StopAnnotation.of(firstLeg.from, legColorOf(firstLeg), isTerminal = true)
            )
        }

        for ((index, leg) in itinerary.legs.withIndex()) {
            val legColor = legColorOf(leg)

            leg.intermediateStops?.forEach { stop ->
                annotations.add(StopAnnotation.of(stop, legColor, isIntermediate = true))
            }

            val isLastLeg = index == itinerary.legs.lastIndex
            val nextLeg = if (isLastLeg) null else itinerary.legs[index + 1]
            val isTransferPoint = nextLeg != null && nextLeg.mode != leg.mode

            val annotationColor =
                if (nextLeg != null && leg.mode == TransportationMode.WALK) legColorOf(nextLeg) else legColor

            var terminalPlace = nextLeg?.from ?: leg.to
            if (isLastLeg && terminalPlace.name == "END" && destinationName != null) {
                terminalPlace = terminalPlace.copy(name = destinationName)
            }

            annotations.add(
                StopAnnotation.of(
                    place = terminalPlace,
                    color = annotationColor,
                    isTerminal = isLastLeg || isTransferPoint,
                    isIntermediate = !isLastLeg && !isTransferPoint
                )
            )

            createRouteOverlay(leg, legColor)?.let { overlays.add(it) }
        }

        return removeDuplicateAnnotations(annotations) to overlays
    }

    private fun removeDuplicateAnnotations(annotations: List<StopAnnotation>): List<StopAnnotation> {
        val unique = mutableListOf<StopAnnotation>()
        val seenCoordinates = mutableMapOf<String, Int>()

        for (annotation in annotations) {
            val key = "${annotation.coordinate.latitude},${annotation.coordinate.longitude}"
            val existingIndex = seenCoordinates[key]
            if (existingIndex != null) {
                if (annotation.isTerminal && !unique[existingIndex].isTerminal) {
                    unique[existingIndex] = annotation
                }
            } else {
                unique.add(annotation)
                seenCoordinates[key] = unique.lastIndex
            }
        }

        return unique
    }

    private fun createRouteOverlay(leg: Leg, color: Color): RouteOverlay? {
        val coordinates = PolylineCodec.decode(leg.legGeometry.points, 1e6)
        if (coordinates.isEmpty()) return null

        return RouteOverlay(
            id = legIdentifier(leg),
            coordinates = reduceCoordinatesIfNeeded(coordinates, MAX_ROUTE_OVERLAY_POINTS_PER_LEG),
            color = color
        )
    }

    private fun calculateMapPosition() {
        if (mapAnnotations.isEmpty()) return

        val coordinates = mutableListOf<LatLng>()
        mapAnnotations.forEach { coordinates.add(it.coordinate) }
        routeOverlays.forEach { coordinates.addAll(it.coordinates) }

        targetBounds = MapBounds(coordinates, MAP_BOUNDS_PADDING_FRACTION)
    }

    private fun prepareVehicleKeyFrames(legs: List<Leg>) {
        if (shouldStop) return

        val frames = mutableMapOf<String, List<VehicleVisualisation.KeyFrame>>()
        for (leg in legs) {
            if (leg.mode == TransportationMode.WALK || leg.mode == TransportationMode.BIKE) continue
            frames[legIdentifier(leg)] =
                VehicleVisualisation.calculateKeyFrames(leg, leg.legGeometry.points, 1e6)
        }
        legKeyFrames = frames
    }

    private fun prepareWalkingLegCoordinates(legs: List<Leg>) {
        if (shouldStop) return

        val paths = mutableMapOf<String, WalkingPathMetrics>()
        for (leg in legs) {
            if (leg.mode != TransportationMode.WALK) continue
            val legId = legIdentifier(leg)
            val coordinates = PolylineCodec.decode(leg.legGeometry.points, 1e6)
            paths[legId] = if (coordinates.size >= 2) {
                buildWalkingPathMetrics(
                    reduceCoordinatesIfNeeded(coordinates, MAX_WALKING_PATH_POINTS_PER_LEG)
                )
            } else {
                buildWalkingPathMetrics(
                    listOf(LatLng(leg.from.lat, leg.from.lon), LatLng(leg.to.lat, leg.to.lon))
                )
            }
        }
        walkingLegPaths = paths
    }

    private fun startVehicleUpdates() {
        if (shouldStop) return
        if (legKeyFrames.isEmpty()) {
            vehicleAnnotations = emptyList()
            return
        }

        stopVehicleUpdates()

        vehicleUpdateJob = scope.launch {
            while (isActive && !shouldStop) {
                updateVehiclePositions()
                delay(VEHICLE_UPDATE_INTERVAL)
            }
        }
    }

    private fun startWalkingUpdates() {
        if (shouldStop || walkingUpdateJob != null) return
        if (walkingLegPaths.isEmpty()) {
            walkingAnnotations = emptyList()
            return
        }

        walkingUpdateJob = scope.launch {
            while (isActive && !shouldStop) {
                updateWalkingPositions()
                delay(WALKING_UPDATE_INTERVAL)
            }
        }
    }

    private fun stopVehicleUpdates() {
        vehicleUpdateJob?.cancel()
        vehicleUpdateJob = null
    }

    private fun stopWalkingUpdates() {
        walkingUpdateJob?.cancel()
        walkingUpdateJob = null
    }

    private fun updateVehiclePositions() {
        val current = itinerary
        if (current == null || shouldStop) return

        val now = Instant.now()
        val nowSeconds = now.epochSeconds()

        vehicleAnnotations = current.legs.mapNotNull { leg ->
            if (leg.mode == TransportationMode.WALK || leg.mode == TransportationMode.BIKE) return@mapNotNull null
            if (leg.startTime.isAfter(now) || leg.endTime.isBefore(now)) return@mapNotNull null

            val legId = legIdentifier(leg)
            val keyFrames = legKeyFrames[legId] ?: return@mapNotNull null
            val position = VehicleVisualisation.interpolatePosition(nowSeconds, keyFrames)
                ?: return@mapNotNull null

            VehicleAnnotation(
                id = legId,
                coordinate = position,
                routeShortName = leg.routeShortName,
                color = legColorOf(leg)
            )
        }
    }

    private fun updateWalkingPositions() {
        val current = itinerary
        if (current == null || shouldStop) return

        val now = Instant.now()
        val nowSeconds = now.epochSeconds()

        walkingAnnotations = current.legs.mapNotNull { leg ->
            if (leg.mode != TransportationMode.WALK) return@mapNotNull null
            if (leg.startTime.isAfter(now) || leg.endTime.isBefore(now)) return@mapNotNull null

            val legId = legIdentifier(leg)
            val position = walkingPosition(leg, legId, nowSeconds) ?: return@mapNotNull null
            WalkingAnnotation(id = legId, coordinate = position)
        }
    }

    private fun walkingPosition(leg: Leg, legId: String, timestamp: Double): LatLng? {
        val startTime = leg.startTime.epochSeconds()
        val endTime = leg.endTime.epochSeconds()

        if (endTime <= startTime) return LatLng(leg.from.lat, leg.from.lon)

        val progress = max(0.0, min(1.0, (timestamp - startTime) / (endTime - startTime)))

        val pathMetrics = walkingLegPaths[legId]
        if (pathMetrics != null) {
            val coordinate = interpolateOnPath(pathMetrics, progress)
            if (coordinate != null) return coordinate
        }

        return interpolateCoordinate(
            LatLng(leg.from.lat, leg.from.lon),
            LatLng(leg.to.lat, leg.to.lon),
            progress
        )
    }
}

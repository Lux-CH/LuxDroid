package ch.cclerc.luxapp.domain.onboard

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ch.cclerc.luxapp.data.CrowdConsent
import ch.cclerc.luxapp.data.Settings
import ch.cclerc.luxapp.domain.map.LatLng
import ch.cclerc.luxapp.domain.map.VehicleVisualisation
import ch.cclerc.luxapp.domain.map.distanceTo
import ch.cclerc.luxapp.domain.station.StationLayoutStore
import ch.cclerc.luxapp.domain.station.StationWalk
import ch.cclerc.luxcom.api.reverseGeocode
import ch.cclerc.luxcom.model.LocationType
import ch.cclerc.luxcom.model.Place
import ch.cclerc.luxcom.model.feedback.InfoResponse
import ch.cclerc.luxcom.model.feedback.ReportAttribute
import ch.cclerc.luxcom.model.trip.Itinerary
import ch.cclerc.luxcom.model.trip.Leg
import ch.cclerc.luxcom.model.Disruption
import ch.cclerc.luxcom.relay.RelayClient
import ch.cclerc.luxcom.relay.RelayLiveFeed
import ch.cclerc.luxcom.station.StationLayout
import ch.cclerc.luxcom.station.TrainFormation
import java.time.Instant
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal fun Instant.secondsSince(other: Instant): Double = (toEpochMilli() - other.toEpochMilli()) / 1000.0
internal fun Instant.plusSecondsDouble(seconds: Double): Instant = plusMillis((seconds * 1000).toLong())

val Place.coordinate: LatLng get() = LatLng(lat, lon)

class OnboardSession(context: Context, itinerary: Itinerary, destinationName: String?) {
    val offRouteDistance = 35.0
    val arrivalRadius = 20.0
    val stopRadius = 30.0
    val usableAccuracy = 80.0
    val crowdReportInterval = 10.0
    val rerouteCooldown = 20.0

    internal val appContext: Context = context.applicationContext
    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val destinationName: String = destinationName
        ?: itinerary.legs.lastOrNull()?.let { if (it.to.name == "END") "votre destination" else it.to.name }
        ?: "votre destination"

    var legs: List<Leg> by mutableStateOf(itinerary.legs)
    var paths: List<RoutePath> by mutableStateOf(emptyList())
    internal var stopAlongs: List<List<Double>> = emptyList()
    var maneuvers: List<List<WalkManeuver>> by mutableStateOf(emptyList())
        internal set
    internal val reroutedWalks = mutableSetOf<Int>()
    internal var stationLayouts: Map<Int, StationLayout> = emptyMap()

    var phase: OnboardPhase by mutableStateOf(OnboardPhase.WALKING)
    var legIndex: Int by mutableStateOf(0)
    var userLocation: OnboardFix? by mutableStateOf(null)
    var heading: Double? by mutableStateOf(null)
    var alongInLeg: Double by mutableStateOf(0.0)
    var isOffRoute: Boolean by mutableStateOf(false)
    var hasWeakGPS: Boolean by mutableStateOf(false)
    var hasTrainGPS: Boolean by mutableStateOf(false)
    var legDisruptions: List<LegDisruption> by mutableStateOf(emptyList())
    internal val tripPaths = mutableMapOf<Int, Pair<RoutePath, Double>>()
    internal var walkOffset = Double.POSITIVE_INFINITY
    internal var legKeyFrames: Pair<String, List<VehicleVisualisation.KeyFrame>>? = null
    internal var knownDisruptions: List<Disruption>? = null
    internal var announcedDisruptionIds: Set<String>? = null
    var nextManeuver: WalkManeuver? by mutableStateOf(null)
    var distanceToManeuver: Double? by mutableStateOf(null)
    var followingManeuver: WalkManeuver? by mutableStateOf(null)
    var nextStopIndex: Int by mutableStateOf(1)
    var dwellingStopIndex: Int? by mutableStateOf(null)
    var arrivalDate: Instant by mutableStateOf(itinerary.endTime)
    var remainingDistance: Double by mutableStateOf(0.0)
    var connectionRisk: ConnectionRisk by mutableStateOf(ConnectionRisk.COMFORTABLE)
    var alert: OnboardAlert? by mutableStateOf(null)
    var crowdStatus: CrowdState? by mutableStateOf(null)
    var approachingVehicle: RelayClient.CrowdVehicle? by mutableStateOf(null)
    var isSharingPosition: Boolean by mutableStateOf(Settings.sharesOnboardPosition)
    var rideInfo: InfoResponse? by mutableStateOf(null)
    val rideReports = mutableStateMapOf<ReportAttribute, Int>()
    var showsCrowdPrompt: Boolean by mutableStateOf(false)
    var replan: ReplanProposal? by mutableStateOf(null)
    var isBehindOrAheadOfSchedule: Boolean by mutableStateOf(false)
    var positionDelay: Double? by mutableStateOf(null)
    internal var positionDelayAt: Instant = Instant.EPOCH
    var hasEstimatedVehicle: Boolean by mutableStateOf(false)
    var isReplanning: Boolean by mutableStateOf(false)
    var formation: TrainFormation? by mutableStateOf(null)
    internal var formationTarget = ""
    internal var formationFetchedAt: Instant = Instant.EPOCH
    internal var formationJob: Job? = null

    val needsSharingConsent: Boolean get() = Settings.onboardCrowdConsent == CrowdConsent.UNDECIDED

    var now: Instant = Instant.now()
        internal set

    var voiceEnabled: Boolean by mutableStateOf(Settings.onboardVoiceGuidance)
        private set

    fun toggleVoice(enabled: Boolean) {
        voiceEnabled = enabled
        announcer.voiceEnabled = enabled
        Settings.onboardVoiceGuidance = enabled
        if (!enabled) announcer.stop()
    }

    internal val locationProvider = OnboardLocationProvider(appContext)
    internal val motion = OnboardMotionDetector(appContext)
    internal var measuredPace: Double? = null
    internal val tripKeyFrames = mutableMapOf<Int, Pair<List<VehicleVisualisation.KeyFrame>, Instant>>()
    internal var paceSamples = 0
    val announcer = OnboardAnnouncer(appContext, Settings.onboardVoiceGuidance)
    internal val liveActivity = OnboardLiveActivityController(appContext)
    internal val liveFeeds = mutableMapOf<Int, RelayLiveFeed<Itinerary>>()
    internal val vehicleJobs = mutableMapOf<Int, Job>()
    internal var rideInfoJob: Job? = null
    internal var crowdPromptJob: Job? = null
    internal val promptedLegs = mutableSetOf<Int>()
    internal var lastAtBoardingStop: Instant? = null
    internal var boarding: Boarding? = null
    internal val retargetedLegs = mutableSetOf<Int>()
    internal var replanJob: Job? = null
    internal var arrivalJob: Job? = null
    internal var isTracking = false
    internal var lastReplanAt: Instant = Instant.EPOCH
    internal val declinedReplanLegs = mutableSetOf<Int>()
    internal var earlierJob: Job? = null
    internal var lastEarlierCheckAt: Instant = Instant.EPOCH
    internal val declinedEarlierLegs = mutableSetOf<Int>()
    internal val earlierBoardingLegs = mutableSetOf<Int>()
    var nearbyStationLayouts: Map<Int, StationLayout> by mutableStateOf(emptyMap())
    internal var nearbyStationsCheck: Pair<LatLng, Instant>? = null
    internal var nearbyStationsJob: Job? = null
    internal val liveVehicles = mutableMapOf<Int, RelayClient.CrowdVehicle?>()
    internal var tickJob: Job? = null
    internal var crowdAckJob: Job? = null
    internal var alertDismissJob: Job? = null
    internal var rerouteJob: Job? = null
    internal var isRunning = false
    internal var hasResolvedStart = false
    internal var lastFixAt: Instant? = null
    internal var compassHeading: Double? = null
    internal var compassAccuracy: Double = -1.0
    internal var compassAt: Instant = Instant.EPOCH
    internal var offRouteStreak = 0
    internal var trainGPSStreak = 0
    internal var trainGPSAt: Instant = Instant.EPOCH
    internal var lastTrainFix: Instant = Instant.EPOCH
    internal var trainFix: Pair<Double, Double> = 0.0 to 0.0
    internal var lastRerouteAt: Instant = Instant.EPOCH
    internal var lastCrowdReportAt: Instant = Instant.EPOCH
    internal val spokenManeuvers = mutableSetOf<String>()
    internal var announcedStopAlerts = mutableSetOf<String>()
    internal val announcedDelays = mutableMapOf<Int, Int>()
    internal var announcedCancellations = mutableSetOf<Int>()
    internal var announcedRisk = ConnectionRisk.COMFORTABLE
    internal var missedDepartureAlerted = mutableSetOf<Int>()

    data class Boarding(val legIndex: Int, val stopId: String, val at: Instant)

    init {
        val builtPaths = mutableListOf<RoutePath>()
        val builtAlongs = mutableListOf<List<Double>>()
        for (leg in legs) {
            val (path, alongs) = buildPath(leg)
            builtPaths.add(path)
            builtAlongs.add(alongs)
        }
        paths = builtPaths
        stopAlongs = builtAlongs
        maneuvers = buildManeuvers(legs, builtPaths)
        remainingDistance = builtPaths.sumOf { it.length }
    }

    fun loadStationLayouts() {
        val currentLegs = legs
        scope.launch {
            val layouts = StationLayoutStore.layouts(currentLegs)
            if (!isRunning || layouts.isEmpty()) return@launch
            stationLayouts = layouts
            val rebuilt = buildManeuvers(legs, paths, layouts)
            val updated = maneuvers.toMutableList()
            for (index in rebuilt.indices) {
                if (index in updated.indices && index !in reroutedWalks && StationWalk.of(legs, index) != null) {
                    updated[index] = rebuilt[index]
                }
            }
            maneuvers = updated
            updateManeuvers()
        }
    }

    fun updateNearbyStations() {
        if (phase != OnboardPhase.WALKING && phase != OnboardPhase.WAITING) return
        if (nearbyStationsJob != null) return
        val location = usableLocation ?: return
        val coordinate = location.coordinate
        val check = nearbyStationsCheck
        if (check != null && check.first.distanceTo(coordinate) < 200 && now.secondsSince(check.second) < 300) return
        nearbyStationsCheck = coordinate to now
        nearbyStationsJob = scope.launch {
            val stops = runCatching {
                reverseGeocode(coordinate.latitude, coordinate.longitude, LocationType.STOP)
            }.getOrDefault(emptyList())
            val stations = stops
                .filter { it.servesMainlineRail || it.modes.isEmpty() }
                .filter { coordinate.distanceTo(LatLng(it.lat, it.lon)) < 400 }
                .take(3)
            val found = mutableMapOf<Int, StationLayout>()
            for (station in stations) {
                StationLayoutStore.layout(station.id)?.let { found[it.uic] = it }
            }
            nearbyStationsJob = null
            if (!isRunning || found.isEmpty()) return@launch
            val merged = nearbyStationLayouts + found
            val kept = if (merged.size <= 4) {
                merged
            } else {
                merged.entries
                    .sortedBy { if (found.containsKey(it.key)) 0 else 1 }
                    .take(4)
                    .associate { it.key to it.value }
            }
            if (kept.keys != nearbyStationLayouts.keys) {
                nearbyStationLayouts = kept
            }
        }
    }

    val stationWalk: StationWalk?
        get() = if (phase == OnboardPhase.WALKING) StationWalk.of(legs, legIndex) else null

    val isInStation: Boolean
        get() {
            val walk = stationWalk ?: return false
            val path = currentPath ?: return false
            return when (walk.kind) {
                StationWalk.Kind.TRANSFER -> true
                StationWalk.Kind.ENTERING -> path.length - alongInLeg < 250
                StationWalk.Kind.LEAVING -> alongInLeg < 250
            }
        }

    fun start() {
        if (isRunning || legs.isEmpty()) return
        isRunning = true

        enterLeg(0, announce = false)

        locationProvider.onLocation = { fix -> handle(fix) }
        locationProvider.onHeading = { heading -> handle(heading) }
        locationProvider.start()
        motion.start()
        isTracking = true

        RelayClient.shared.setBackgroundKeepAlive(true)
        startLiveFeeds()
        startCrowdAcks()
        loadStationLayouts()

        tickJob = scope.launch {
            while (isActive) {
                delay(1_000)
                tick()
            }
        }

        liveActivity.start(destinationName.capitalizedFirstLetter, activityState())
        announcer.speak(startAnnouncement())
    }

    fun stop() {
        formationJob?.cancel()
        if (!isRunning) return
        isRunning = false
        tickJob?.cancel()
        crowdAckJob?.cancel()
        alertDismissJob?.cancel()
        rerouteJob?.cancel()
        liveFeeds.values.forEach { it.stop() }
        liveFeeds.clear()
        vehicleJobs.values.forEach { it.cancel() }
        vehicleJobs.clear()
        rideInfoJob?.cancel()
        crowdPromptJob?.cancel()
        replanJob?.cancel()
        arrivalJob?.cancel()
        stopTracking()
        announcer.stop()
        liveActivity.end(if (phase == OnboardPhase.ARRIVED) activityState() else null)
        scope.launch {
            delay(3_000)
            announcer.shutdown()
            scope.cancel()
        }
    }

    fun refreshMotion() {
        if (!isTracking) return
        motion.stop()
        motion.start()
    }

    fun stopTracking() {
        if (!isTracking) return
        isTracking = false
        locationProvider.stop()
        motion.stop()
        RelayClient.shared.stopOnboardReports()
        RelayClient.shared.setBackgroundKeepAlive(false)
    }

    val currentLeg: Leg? get() = legs.getOrNull(legIndex)

    val currentPath: RoutePath? get() = paths.getOrNull(legIndex)

    val currentStops: List<Place> get() = currentLeg?.allStops ?: emptyList()

    val upcomingStops: List<Place>
        get() {
            val stops = currentStops
            if ((phase != OnboardPhase.RIDING && phase != OnboardPhase.WAITING) || stops.isEmpty()) return emptyList()
            val start = if (phase == OnboardPhase.WAITING) 0 else min(nextStopIndex, stops.size - 1)
            return stops.subList(start, stops.size)
        }

    val stopsRemaining: Int get() = max(0, currentStops.size - nextStopIndex)

    val nextTransitLeg: Pair<Int, Leg>?
        get() {
            val start = if (phase == OnboardPhase.WAITING) legIndex else legIndex + 1
            if (start >= legs.size) return null
            for (index in start until legs.size) {
                if (legs[index].isTransit) return index to legs[index]
            }
            return null
        }

    val legProgress: Double
        get() {
            val path = currentPath
            if (path == null || path.length <= 0) return if (phase == OnboardPhase.ARRIVED) 1.0 else 0.0
            return max(0.0, min(1.0, alongInLeg / path.length))
        }

    val stopProgress: Double
        get() {
            if (phase != OnboardPhase.RIDING || legIndex !in legs.indices) {
                return if (phase == OnboardPhase.ARRIVED) 1.0 else 0.0
            }
            val alongs = stopAlongs[legIndex]
            if (alongs.size < 2) return legProgress
            val segment = max(0, min(alongs.size - 2, nextStopIndex - 1))
            val start = alongs[segment]
            val end = alongs[segment + 1]
            val fraction = if (end > start) max(0.0, min(1.0, (alongInLeg - start) / (end - start))) else 0.0
            return (segment + fraction) / (alongs.size - 1).toDouble()
        }

    val currentSegmentTimes: Pair<Instant, Instant>?
        get() {
            if (phase != OnboardPhase.RIDING) return null
            val leg = currentLeg ?: return null
            val stops = leg.allStops
            if (stops.size < 2) return null
            val segment = max(0, min(stops.size - 2, nextStopIndex - 1))
            val departure = stops[segment].departure ?: stops[segment].scheduledDeparture ?: stops[segment].arrival
            val arrival = stops[segment + 1].arrival ?: stops[segment + 1].scheduledArrival ?: stops[segment + 1].departure
            if (departure == null || arrival == null || !arrival.isAfter(departure)) return null
            return departure to arrival
        }

    fun placeName(place: Place, isDestination: Boolean = false): String {
        if (place.name == "END" || (isDestination && place.name.isEmpty())) return destinationName
        if (place.name == "START") return "votre position"
        return place.name
    }

    val followsTimetable: Boolean
        get() {
            val leg = currentLeg ?: return false
            if (!leg.isTransit) return false
            return (leg.mode.isMainlineRail && phase == OnboardPhase.WAITING) ||
                (leg.ridesByTimetable && phase == OnboardPhase.RIDING && !hasTrainGPS)
        }

    val riderCoordinate: LatLng?
        get() {
            if (phase == OnboardPhase.RIDING && currentLeg?.ridesByTimetable == true) {
                currentPath?.coordinate(alongInLeg)?.let { return it }
            }
            if (phase == OnboardPhase.WALKING && !isOffRoute && walkOffset <= 20) {
                currentPath?.coordinate(alongInLeg)?.let { return it }
            }
            return userLocation?.coordinate
        }

    fun confirmBoarded() {
        if (phase != OnboardPhase.WAITING) return
        ch.cclerc.luxapp.core.HapticFeedback.lightImpact()
        board()
    }

    fun skipToNextStep() {
        ch.cclerc.luxapp.core.HapticFeedback.lightImpact()
        if (phase == OnboardPhase.WAITING) board() else completeLeg()
    }

    fun requestNotificationPermission() {
        announcer.prepare()
    }

    fun tick() {
        if (!isRunning) return
        now = Instant.now()
        currentLeg?.let { leg ->
            locationProvider.isSaving = phase == OnboardPhase.WAITING && !leg.mode.isMainlineRail &&
                leg.startTime.secondsSince(now) > 120
        }
        replan?.let { proposal ->
            val autoApplyAt = proposal.autoApplyAt
            if (autoApplyAt != null && !now.isBefore(autoApplyAt)) acceptReplan()
        }
        replan?.let { proposal ->
            if (proposal.reason == ReplanReason.EARLIER &&
                (phase != OnboardPhase.WAITING || !(proposal.firstTransit?.startTime ?: now).isAfter(now.plusSeconds(20)))
            ) {
                replan = null
            }
        }
        lookForEarlierDeparture()
        catchUpWithVehicle()
        updateEstimates()
        refreshFormationIfNeeded()
        val fixAt = lastFixAt
        if (fixAt != null && now.secondsSince(fixAt) > 45) {
            hasWeakGPS = true
        } else if (fixAt == null && now.secondsSince(legs.firstOrNull()?.startTime ?: now) > 0) {
            hasWeakGPS = true
        }
        evaluate()
    }

    val formationPlatformSectors: List<String>
        get() {
            val leg = nextTransitLeg?.second ?: return emptyList()
            val uic = StationLayout.uic(leg.from.stopId) ?: return emptyList()
            val layout = StationLayoutStore.cached(uic) ?: return emptyList()
            val track = layout.track(leg.from.track ?: leg.from.scheduledTrack, leg.from.stopId) ?: return emptyList()
            return track.sectors.map { it.s }
        }

    fun refreshFormationIfNeeded() {
        var target = ""
        val next = nextTransitLeg?.second
        if ((phase == OnboardPhase.WALKING || phase == OnboardPhase.WAITING) && next != null &&
            next.mode.isMainlineRail
        ) {
            val tripId = next.tripId
            val stopId = next.from.stopId
            if (tripId != null && stopId != null) target = "$tripId|$stopId"
        }
        if (target != formationTarget) {
            formationTarget = target
            formationJob?.cancel()
            formationFetchedAt = Instant.EPOCH
            if (formation != null) formation = null
        }
        if (target.isEmpty() || now.secondsSince(formationFetchedAt) <= 180) return
        formationFetchedAt = now
        val parts = target.split("|", limit = 2)
        formationJob?.cancel()
        formationJob = scope.launch {
            val received = runCatching { RelayClient.shared.formation(parts[0], parts[1]).first() }
            if (formationTarget != target || received.isFailure) return@launch
            formation = received.getOrNull()
        }
    }

    companion object {
        fun canStart(itinerary: Itinerary, date: Instant = Instant.now()): Boolean {
            if (itinerary.legs.isEmpty()) return false
            return itinerary.startTime.secondsSince(date) < 3 * 3600 && itinerary.endTime.secondsSince(date) > -10 * 60
        }

        fun buildManeuvers(
            legs: List<Leg>,
            paths: List<RoutePath>,
            stations: Map<Int, StationLayout> = emptyMap()
        ): List<List<WalkManeuver>> = legs.indices.map { index ->
            if (legs[index].isTransit || index !in paths.indices) return@map emptyList()
            val walk = StationWalk.of(legs, index)
            WalkManeuverBuilder.maneuvers(
                steps = legs[index].steps.orEmpty(),
                path = paths[index],
                station = walk,
                access = walk?.let { stations[it.uic]?.access }.orEmpty()
            )
        }

        fun buildPath(leg: Leg): Pair<RoutePath, List<Double>> {
            val stops = leg.allStops
            val stopCoordinates = stops.map { it.coordinate }
            val fallback = if (leg.isTransit) stopCoordinates else listOf(stopCoordinates.first(), stopCoordinates.last())
            val full = RoutePath.encoded(leg.legGeometry.points, 1e6, fallback)

            if (!leg.isTransit) return full to emptyList()

            val alongs = full.projectSequence(stopCoordinates)
            val first = alongs.firstOrNull()
            val last = alongs.lastOrNull()
            if (first == null || last == null || last - first <= 1) return full to alongs
            val path = full.sliced(first, last)
            return path to alongs.map { it - first }
        }
    }
}

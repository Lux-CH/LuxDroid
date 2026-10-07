package ch.cclerc.luxapp.ui.stops

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import ch.cclerc.luxapp.ui.settings.ShortcutEditorView
import ch.cclerc.luxapp.ui.navigation.LuxSheetRequest
import ch.cclerc.luxapp.ui.navigation.LocalSheetController
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import ch.cclerc.luxapp.ui.map.rememberMarkerImageStore
import ch.cclerc.luxapp.ui.map.MarkerLayer
import ch.cclerc.luxapp.ui.map.MarkerImageHost
import ch.cclerc.luxapp.ui.map.MarkerAnchor
import ch.cclerc.luxapp.domain.ConnectionService
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import ch.cclerc.luxapp.domain.shortcut.ShortcutManager
import ch.cclerc.luxapp.domain.shortcut.UserShortcut
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.cclerc.luxapp.core.HapticFeedback
import ch.cclerc.luxapp.core.LocationService
import ch.cclerc.luxapp.core.SFSymbol
import ch.cclerc.luxapp.domain.map.LatLng
import ch.cclerc.luxapp.domain.map.distanceTo
import ch.cclerc.luxapp.domain.station.StationDetail
import ch.cclerc.luxapp.domain.station.StationLayoutStore
import ch.cclerc.luxapp.domain.station.StationOverlayContent
import ch.cclerc.luxapp.ui.anim.scaleClickable
import ch.cclerc.luxapp.ui.components.IosActivityIndicator
import ch.cclerc.luxapp.ui.itinerary.presentItinerary
import ch.cclerc.luxapp.ui.map.LuxMapView
import ch.cclerc.luxapp.ui.map.StationShapeLayers
import ch.cclerc.luxapp.ui.map.StationStyle
import ch.cclerc.luxapp.ui.map.cameraDistanceMeters
import ch.cclerc.luxapp.ui.map.rememberLuxCameraState
import ch.cclerc.luxapp.ui.map.rememberLuxMapStyle
import ch.cclerc.luxapp.ui.map.zoomForCameraDistance
import ch.cclerc.luxapp.ui.navigation.DetentSheet
import ch.cclerc.luxapp.ui.navigation.DetentSheetState
import ch.cclerc.luxapp.ui.navigation.LocalCoverController
import ch.cclerc.luxapp.ui.navigation.SheetDetent
import ch.cclerc.luxapp.ui.stop.StopDepartureSheet
import ch.cclerc.luxapp.ui.theme.LuxMaterials
import ch.cclerc.luxapp.ui.theme.LuxTheme
import ch.cclerc.luxapp.ui.theme.iosShadow
import ch.cclerc.luxapp.viewmodel.MapTrackingMode
import ch.cclerc.luxcom.api.reverseGeocode
import ch.cclerc.luxcom.map.getMapSearchResults
import ch.cclerc.luxcom.map.getMapStops
import ch.cclerc.luxcom.model.LocationType
import ch.cclerc.luxcom.model.Place
import ch.cclerc.luxcom.model.SearchResult
import ch.cclerc.luxcom.model.TransportationMode
import ch.cclerc.luxcom.station.StationLayout
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.expressions.dsl.Feature
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.convertToColor
import org.maplibre.compose.expressions.dsl.asNumber
import org.maplibre.compose.expressions.dsl.convertToNumber
import org.maplibre.compose.expressions.dsl.convertToString
import org.maplibre.compose.expressions.dsl.dp
import org.maplibre.compose.expressions.dsl.format
import org.maplibre.compose.expressions.dsl.offset
import org.maplibre.compose.expressions.dsl.span
import org.maplibre.compose.expressions.value.SymbolAnchor
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.util.ClickResult
import org.maplibre.spatialk.geojson.Position

data class StopsMapSelection(val stop: SearchResult, val track: String?) {
    val id: String get() = "${stop.id}|${track ?: ""}"
}

class MapStation(val stop: SearchResult, val quais: List<Place>, val importance: Double) {
    val id: String get() = stop.id

    companion object {
        suspend fun load(minLat: Double, minLon: Double, maxLat: Double, maxLon: Double): List<MapStation> {
            val order = mutableListOf<String>()
            val anchors = mutableMapOf<String, Place>()
            val modes = mutableMapOf<String, MutableList<TransportationMode>>()
            val quais = mutableMapOf<String, MutableList<Place>>()
            for (place in getMapStops(minLat, minLon, maxLat, maxLon)) {
                val id = place.parentId ?: place.stopId
                if (id.isNullOrEmpty()) continue
                val track = place.track ?: place.scheduledTrack
                val stopId = place.stopId
                if (!track.isNullOrEmpty() && stopId != null && quais[id]?.any { it.stopId == stopId } != true) {
                    quais.getOrPut(id) { mutableListOf() }.add(place)
                }
                for (mode in place.modes) {
                    val list = modes.getOrPut(id) { mutableListOf() }
                    if (mode !in list) list.add(mode)
                }
                val anchor = anchors[id]
                if (anchor != null) {
                    if (track == null && (anchor.track ?: anchor.scheduledTrack) != null) anchors[id] = place
                } else {
                    order.add(id)
                    anchors[id] = place
                }
            }
            return order.mapNotNull { id ->
                val anchor = anchors[id] ?: return@mapNotNull null
                val stop = SearchResult(
                    type = LocationType.STOP,
                    tokens = listOf(emptyList()),
                    name = anchor.name,
                    id = id,
                    lat = anchor.lat,
                    lon = anchor.lon,
                    level = anchor.level,
                    areas = emptyList(),
                    score = 0.0,
                    modes = modes[id].orEmpty(),
                    groupedStopIds = listOf(id)
                )
                MapStation(stop, quais[id].orEmpty(), anchor.importance ?: 0.0)
            }
        }
    }
}

data class StopsMapPin(
    val coordinate: LatLng,
    val name: String? = null,
    val nearby: List<SearchResult> = emptyList(),
    val isLoading: Boolean = true,
    val shortcut: UserShortcut? = null
)

private data class QuaiPin(
    val key: String,
    val stop: SearchResult,
    val track: String,
    val isRail: Boolean,
    val coordinate: LatLng
)

private data class VisibleBounds(val minLat: Double, val minLon: Double, val maxLat: Double, val maxLon: Double) {
    fun contains(other: VisibleBounds): Boolean =
        other.minLat >= minLat && other.maxLat <= maxLat && other.minLon >= minLon && other.maxLon <= maxLon

    fun contains(lat: Double, lon: Double): Boolean = lat in minLat..maxLat && lon in minLon..maxLon

    fun expanded(fraction: Double): VisibleBounds {
        val dLat = (maxLat - minLat) * fraction
        val dLon = (maxLon - minLon) * fraction
        return VisibleBounds(minLat - dLat, minLon - dLon, maxLat + dLat, maxLon + dLon)
    }
}

object StopsMapStyle {
    fun primaryMode(modes: List<TransportationMode>): TransportationMode? =
        modes.firstOrNull { it.isMainlineRail }
            ?: modes.firstOrNull { it == TransportationMode.SUBWAY || it == TransportationMode.METRO }
            ?: modes.firstOrNull { it == TransportationMode.TRAM }
            ?: modes.firstOrNull { it == TransportationMode.FERRY }
            ?: modes.firstOrNull { it == TransportationMode.FUNICULAR }
            ?: modes.firstOrNull()

    @Composable
    fun color(mode: TransportationMode?): Color {
        val colors = LuxTheme.colors
        if (mode == null) return colors.systemBlue
        if (mode.isMainlineRail) return Color(red = 0.92f, green = 0f, blue = 0f)
        return when (mode) {
            TransportationMode.SUBWAY, TransportationMode.METRO -> colors.systemPurple
            TransportationMode.TRAM -> colors.systemOrange
            TransportationMode.FERRY -> colors.systemCyan
            TransportationMode.FUNICULAR -> Color(0xFFA2845E)
            else -> colors.systemBlue
        }
    }
}

private object StopsMapMemory {
    var savedCamera: CameraPosition? = null
}

private const val MAX_LOAD_DISTANCE = 9_000.0
private const val MAX_STATIONS = 1_500
private val SelectionSpring = 600.milliseconds

@Composable
fun StopsMapScreen(
    onDismiss: () -> Unit,
    onGo: (SearchResult) -> Unit
) {
    val colors = LuxTheme.colors
    val accent = LuxTheme.accent
    val style = rememberLuxMapStyle()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val coverController = LocalCoverController.current
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomSafe = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val initialLocation = remember { LocationService.location.value }

    val cameraState = rememberLuxCameraState(
        StopsMapMemory.savedCamera ?: initialLocation?.let {
            CameraPosition(target = Position(longitude = it.longitude, latitude = it.latitude), zoom = 15.0)
        } ?: CameraPosition(target = Position(longitude = 8.23, latitude = 46.80), zoom = 6.6)
    )
    val markerImages = rememberMarkerImageStore()

    var selection by remember { mutableStateOf<StopsMapSelection?>(null) }
    var presentedSelection by remember { mutableStateOf<StopsMapSelection?>(null) }
    var pin by remember { mutableStateOf<StopsMapPin?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var isZoomedOut by remember { mutableStateOf(false) }
    var trackingMode by remember { mutableStateOf(MapTrackingMode.NONE) }
    var focusToken by remember { mutableIntStateOf(0) }
    var showsHint by remember { mutableStateOf(true) }
    var pinCardHeight by remember { mutableStateOf(0.dp) }
    var stations by remember { mutableStateOf<Map<String, MapStation>>(emptyMap()) }
    var stationContents by remember { mutableStateOf<Map<Int, StationOverlayContent>>(emptyMap()) }
    val pendingContents = remember { mutableMapOf<Int, StationOverlayContent>() }
    var layoutFlushJob by remember { mutableStateOf<Job?>(null) }
    val layoutJobs = remember { mutableMapOf<Int, Job>() }
    var detail by remember { mutableStateOf(StationDetail.HIDDEN) }
    var visibleBounds by remember { mutableStateOf<VisibleBounds?>(null) }
    val requestedLayouts = remember { mutableSetOf<Int>() }
    val loadedBounds = remember { mutableListOf<VisibleBounds>() }
    var pendingBounds by remember { mutableStateOf<VisibleBounds?>(null) }
    var loadJob by remember { mutableStateOf<Job?>(null) }
    var pinJob by remember { mutableStateOf<Job?>(null) }

    val sheetState = remember { DetentSheetState(listOf(SheetDetent.Medium, SheetDetent.Large), dismissible = true) }
    val shortcuts by ShortcutManager.shared.shortcuts.collectAsStateWithLifecycle()
    val sheets = LocalSheetController.current

    DisposableEffect(Unit) {
        LocationService.startMonitoring()
        onDispose {
            StopsMapMemory.savedCamera = cameraState.position
            LocationService.stopMonitoring()
        }
    }

    LaunchedEffect(Unit) {
        ConnectionService.warmUp()
        delay(4_000)
        showsHint = false
    }

    fun select(stop: SearchResult, track: String? = null) {
        val next = StopsMapSelection(stop, track)
        if (next == selection) return
        selection = next
        presentedSelection = next
        HapticFeedback.softImpact()
        scope.launch { sheetState.animateTo(SheetDetent.Medium) }
    }

    fun deselect() {
        selection = null
    }

    fun dropPin(coordinate: LatLng, shortcut: UserShortcut? = null) {
        if (shortcut == null) HapticFeedback.mediumImpact() else HapticFeedback.softImpact()
        selection = null
        pin = StopsMapPin(coordinate, name = shortcut?.name, shortcut = shortcut)
        pinJob?.cancel()
        pinJob = scope.launch {
            val names = async { runCatching { reverseGeocode(coordinate.latitude, coordinate.longitude) }.getOrNull() }
            val stops = async { runCatching { getMapSearchResults(coordinate.latitude, coordinate.longitude) }.getOrNull() }
            val resolved = names.await()
            val found = stops.await().orEmpty()
                .sortedBy { coordinate.distanceTo(LatLng(it.lat, it.lon)) }
            val current = pin ?: return@launch
            if (current.coordinate != coordinate) return@launch
            pin = current.copy(
                name = if (shortcut == null) resolved?.firstOrNull { it.type != LocationType.STOP }?.name else current.name,
                nearby = found.take(6),
                isLoading = false
            )
            focusToken += 1
        }
    }

    fun clearPin() {
        pinJob?.cancel()
        pin = null
    }

    fun openShortcut(shortcut: UserShortcut) {
        if (shortcut.stopId != null) {
            clearPin()
            select(shortcut.toSearchResult())
            return
        }
        if (pin?.shortcut?.id == shortcut.id) return
        dropPin(LatLng(shortcut.coordinates.latitude, shortcut.coordinates.longitude), shortcut)
    }

    fun destination(pin: StopsMapPin): SearchResult {
        pin.shortcut?.let { return it.toSearchResult() }
        val name = pin.name ?: "Repère sur la carte"
        return SearchResult(
            type = LocationType.PLACE,
            tokens = listOf(listOf(0, name.length)),
            name = name,
            id = "map-pin-${pin.coordinate.latitude},${pin.coordinate.longitude}",
            lat = pin.coordinate.latitude,
            lon = pin.coordinate.longitude,
            areas = emptyList(),
            score = 1.0
        )
    }

    fun go(destination: SearchResult) {
        selection = null
        onGo(destination)
    }

    BackHandler(enabled = selection != null || pin != null) {
        if (selection != null) deselect() else clearPin()
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(colors.secondarySystemBackground)
    ) {
        val mapWidth = maxWidth
        val mapHeight = maxHeight
        val bottomInset = when {
            selection != null -> mapHeight * 0.5f
            pin != null -> pinCardHeight + 16.dp
            else -> 0.dp
        }
        val revealTop = topInset + 64.dp + 40.dp

        LaunchedEffect(Unit) {
            if (StopsMapMemory.savedCamera != null) return@LaunchedEffect
            val start = initialLocation ?: return@LaunchedEffect
            cameraState.position = cameraState.position.copy(
                zoom = zoomForCameraDistance(1_800.0, start.latitude, mapHeight)
            )
        }

        fun reveal(coordinate: LatLng) {
            val projection = cameraState.projection ?: return
            val point = projection.screenLocationFromPosition(Position(longitude = coordinate.longitude, latitude = coordinate.latitude))
            val bottom = mapHeight * 0.5f - 40.dp
            if (bottom <= revealTop) return
            val targetY = revealTop + (bottom - revealTop) * 0.8f
            val center = projection.positionFromScreenLocation(DpOffset(point.x, mapHeight / 2 + point.y - targetY))
            scope.launch { cameraState.animateTo(cameraState.position.copy(target = center), SelectionSpring) }
        }

        fun focus(target: StopsMapPin) {
            val projection = cameraState.projection ?: return
            val point = projection.screenLocationFromPosition(Position(longitude = target.coordinate.longitude, latitude = target.coordinate.latitude))
            val bottom = mapHeight - bottomInset - 60.dp
            if (bottom <= revealTop || (point.y >= revealTop && point.y <= bottom)) return
            val targetY = (revealTop + bottom) / 2
            val center = projection.positionFromScreenLocation(DpOffset(mapWidth / 2, mapHeight / 2 + point.y - targetY))
            scope.launch { cameraState.animateTo(cameraState.position.copy(target = center), SelectionSpring) }
        }

        LaunchedEffect(focusToken) {
            if (focusToken == 0) return@LaunchedEffect
            pin?.let { focus(it) }
        }

        LaunchedEffect(selection?.id) {
            val current = selection ?: return@LaunchedEffect
            reveal(LatLng(current.stop.lat, current.stop.lon))
        }

        fun loadLayouts() {
            if (detail < StationDetail.TRACKS) return
            val area = visibleBounds?.expanded(0.5) ?: return
            for (station in stations.values) {
                if (!station.stop.servesMainlineRail) continue
                val uic = StationLayout.uic(station.id) ?: continue
                if (uic in requestedLayouts || !area.contains(station.stop.lat, station.stop.lon)) continue
                requestedLayouts.add(uic)
                val stopId = station.id
                layoutJobs[uic] = scope.launch {
                    val layout = StationLayoutStore.layout(stopId)
                    layoutJobs.remove(uic)
                    layout ?: return@launch
                    pendingContents[layout.uic] = StationOverlayContent.of(emptyList(), mapOf(layout.uic to layout))
                    if (layoutFlushJob == null) {
                        layoutFlushJob = scope.launch {
                            delay(60)
                            layoutFlushJob = null
                            snapshotFlow { cameraState.isCameraMoving }.first { !it }
                            stationContents = stationContents + pendingContents
                            pendingContents.clear()
                        }
                    }
                }
            }
        }

        fun pruneLayouts(kept: Set<Int>) {
            stationContents = stationContents.filterKeys { it in kept }
            layoutJobs.keys.filter { it !in kept }.forEach { uic -> layoutJobs.remove(uic)?.cancel() }
            requestedLayouts.retainAll(kept)
            pendingContents.keys.retainAll(kept)
        }

        fun apply(result: List<MapStation>) {
            if (result.all { stations[it.id] != null }) {
                loadLayouts()
                return
            }
            val merged = stations.toMutableMap()
            result.forEach { merged[it.id] = it }
            if (merged.size > MAX_STATIONS) {
                val target = cameraState.position.target
                val center = LatLng(target.latitude, target.longitude)
                stations = merged.values
                    .sortedBy { center.distanceTo(LatLng(it.stop.lat, it.stop.lon)) }
                    .take(MAX_STATIONS)
                    .associateBy { it.id }
                loadedBounds.clear()
                pruneLayouts(stations.keys.mapNotNull { StationLayout.uic(it) }.toSet())
            } else {
                stations = merged
            }
            loadLayouts()
        }

        fun scheduleLoad() {
            val distance = cameraState.cameraDistanceMeters(mapHeight)
            val zoomedOut = distance > MAX_LOAD_DISTANCE
            if (isZoomedOut != zoomedOut) isZoomedOut = zoomedOut
            if (zoomedOut) {
                loadJob?.cancel()
                pendingBounds = null
                isLoading = false
                return
            }
            loadLayouts()
            val visible = visibleBounds ?: return
            if (loadedBounds.any { it.contains(visible) }) return
            if (pendingBounds?.contains(visible) == true) return
            val rect = visible.expanded(0.25)
            loadJob?.cancel()
            pendingBounds = rect
            loadJob = scope.launch {
                delay(250)
                isLoading = true
                val result = runCatching { MapStation.load(rect.minLat, rect.minLon, rect.maxLat, rect.maxLon) }.getOrNull()
                isLoading = false
                pendingBounds = null
                if (result == null) return@launch
                loadedBounds.add(rect)
                if (loadedBounds.size > 40) loadedBounds.removeAt(0)
                snapshotFlow { cameraState.isCameraMoving }.first { !it }
                apply(result)
            }
        }

        LaunchedEffect(cameraState, mapHeight) {
            snapshotFlow { cameraState.position }
                .distinctUntilChanged()
                .collect {
                    val next = StationDetail.of(cameraState.cameraDistanceMeters(mapHeight))
                    if (next != detail) {
                        detail = next
                        loadLayouts()
                    }
                }
        }

        LaunchedEffect(cameraState, mapHeight) {
            snapshotFlow { cameraState.position }
                .debounce(150)
                .collect {
                    val box = cameraState.projection?.queryVisibleBoundingBox() ?: return@collect
                    visibleBounds = VisibleBounds(box.southwest.latitude, box.southwest.longitude, box.northeast.latitude, box.northeast.longitude)
                    scheduleLoad()
                }
        }

        LaunchedEffect(trackingMode) {
            if (trackingMode == MapTrackingMode.NONE) return@LaunchedEffect
            val mode = trackingMode
            combine(LocationService.location, LocationService.heading) { user, heading -> user to heading }
                .collectLatest { (user, heading) ->
                    user ?: return@collectLatest
                    val current = cameraState.position
                    val bearing = if (mode == MapTrackingMode.FOLLOW_WITH_HEADING) (heading ?: current.bearing.toFloat()).toDouble() else 0.0
                    cameraState.animateTo(
                        current.copy(target = Position(longitude = user.longitude, latitude = user.latitude), bearing = bearing),
                        300.milliseconds
                    )
                }
        }

        val stationContent = remember(stationContents) { StationOverlayContent.merged(stationContents.values) }
        val quais = remember(stations, stationContents, detail, selection) {
            quaiPins(stations, stationContents, detail, selection, null)
        }
        val stationColors = StationColorPalette()
        val stationsJson = remember(stations, selection, stationColors) {
            stationsGeoJson(stations.values, selection?.takeIf { it.track == null }?.stop?.id, stationColors)
        }

        LuxMapView(
            styleJson = style.json,
            modifier = Modifier.matchParentSize(),
            cameraState = cameraState,
            routeAnchorLayerId = style.firstLabelLayerId,
            showUserLocation = true,
            onUserGesture = { if (trackingMode != MapTrackingMode.NONE) trackingMode = MapTrackingMode.NONE },
            onMapClick = { if (selection != null) deselect() },
            onMapLongClick = { coordinate -> dropPin(coordinate) },
            underlay = {
                StationShapeLayers(
                    content = stationContent,
                    detail = detail,
                    dark = style.isDark,
                    idPrefix = "stops-map-station"
                )
            }
        ) {
            val stationSource = rememberGeoJsonSource(GeoJsonData.JsonString(stationsJson))
            val onStationClick: (List<org.maplibre.spatialk.geojson.Feature<*, kotlinx.serialization.json.JsonObject?>>) -> ClickResult = { features ->
                val id = features.firstNotNullOfOrNull { (it.properties?.get("id") as? JsonPrimitive)?.content }
                val station = id?.let { stations[it] }
                if (station != null) {
                    select(station.stop)
                    ClickResult.Consume
                } else {
                    ClickResult.Pass
                }
            }
            CircleLayer(
                id = "stops-map-station-dot",
                source = stationSource,
                visible = !isZoomedOut,
                radius = StationLayerStyle.radius,
                color = StationLayerStyle.color,
                strokeColor = StationLayerStyle.strokeColor,
                strokeWidth = StationLayerStyle.strokeWidth,
                sortKey = StationLayerStyle.dotSortKey,
                onClick = onStationClick
            )
            val labelColor = colors.label
            val haloColor = colors.systemBackground
            val textColor = remember(labelColor) { const(labelColor) }
            val textHalo = remember(haloColor) { const(haloColor) }
            SymbolLayer(
                id = "stops-map-station-label",
                source = stationSource,
                visible = !isZoomedOut,
                textField = StationLayerStyle.textField,
                textFont = StationLayerStyle.textFont,
                textSize = StationLayerStyle.textSize,
                textColor = textColor,
                textHaloColor = textHalo,
                textHaloWidth = StationLayerStyle.textHaloWidth,
                textAnchor = StationLayerStyle.textAnchor,
                textOffset = StationLayerStyle.textOffset,
                textMaxWidth = StationLayerStyle.textMaxWidth,
                textOptional = StationLayerStyle.textOptional,
                sortKey = StationLayerStyle.labelSortKey
            )

            MarkerLayer(
                id = "stops-map-quais",
                store = markerImages,
                items = quais,
                positionOf = { it.coordinate },
                imageKeyOf = { quai ->
                    val isSelected = selection?.let { it.stop.id == quai.stop.id && it.track == quai.track } == true
                    "${quai.track}|${quai.isRail}|$isSelected"
                },
                sortKeyOf = { quai -> if (selection?.let { it.stop.id == quai.stop.id && it.track == quai.track } == true) 1.0 else 0.0 },
                onClick = { quai -> select(quai.stop, quai.track) }
            ) { quai ->
                val isSelected = selection?.let { it.stop.id == quai.stop.id && it.track == quai.track } == true
                QuaiSign(quai.track, quai.isRail, isSelected, accent)
            }

            MarkerLayer(
                id = "stops-map-shortcuts",
                store = markerImages,
                items = shortcuts,
                positionOf = { LatLng(it.coordinates.latitude, it.coordinates.longitude) },
                imageKeyOf = { "${it.symbol}|${it.name}" },
                anchor = MarkerAnchor.Top,
                offsetY = -ShortcutBadgeSize / 2,
                onClick = { shortcut -> openShortcut(shortcut) }
            ) { shortcut ->
                ShortcutBadge(shortcut, accent)
            }

            val dropped = pin?.takeIf { it.shortcut == null }
            MarkerLayer(
                id = "stops-map-pin",
                store = markerImages,
                items = listOfNotNull(dropped),
                positionOf = { it.coordinate },
                imageKeyOf = { it.name ?: "" },
                anchor = MarkerAnchor.Bottom
            ) {
                DroppedPinMarker(it.name)
            }
        }

        MarkerImageHost(markerImages)

        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = topInset + 8.dp, start = 16.dp, end = 16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MapCircleButton("xmark") { onDismiss() }
                MapCircleButton(
                    when (trackingMode) {
                        MapTrackingMode.FOLLOW -> "location.fill"
                        MapTrackingMode.FOLLOW_WITH_HEADING -> "location.north.line.fill"
                        MapTrackingMode.NONE -> "location"
                    }
                ) {
                    trackingMode = when (trackingMode) {
                        MapTrackingMode.NONE -> MapTrackingMode.FOLLOW
                        MapTrackingMode.FOLLOW -> MapTrackingMode.FOLLOW_WITH_HEADING
                        MapTrackingMode.FOLLOW_WITH_HEADING -> MapTrackingMode.NONE
                    }
                }
            }
            val status = when {
                isZoomedOut -> "Zoomez pour voir les arrêts"
                isLoading -> "Chargement…"
                showsHint && pin == null -> "Maintenez pour placer un repère"
                else -> null
            }
            AnimatedVisibility(
                visible = status != null,
                enter = scaleIn(initialScale = 0.9f) + fadeIn(),
                exit = scaleOut(targetScale = 0.9f) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 2.dp)
            ) {
                var shown by remember { mutableStateOf(status ?: "") }
                if (status != null) shown = status
                Row(
                    Modifier
                        .height(40.dp)
                        .iosShadow(Color.Black.copy(alpha = 0.18f), 2.dp, shape = CircleShape)
                        .background(LuxMaterials.ultraThick(), CircleShape)
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isLoading && !isZoomedOut) IosActivityIndicator(size = 14.dp)
                    Text(
                        shown,
                        style = LuxTheme.type.footnote.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.label,
                        maxLines = 1
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = pin != null && selection == null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut()
        ) {
            var shownPin by remember { mutableStateOf(pin) }
            pin?.let { shownPin = it }
            shownPin?.let { current ->
                PinCard(
                    pin = current,
                    userLocation = initialLocation?.let { LatLng(it.latitude, it.longitude) },
                    onClear = { clearPin() },
                    onCreateShortcut = {
                        val location = destination(current)
                        sheets.present(
                            LuxSheetRequest(cornerRadius = 38.dp) {
                                ShortcutEditorView(
                                    shortcutToEdit = null,
                                    onDismiss = { sheets.dismiss() },
                                    prefilledLocation = location,
                                    onSave = { shortcut ->
                                        clearPin()
                                        openShortcut(shortcut)
                                    }
                                )
                            }
                        )
                    },
                    onSelect = { stop -> select(stop) },
                    onGo = { go(destination(current)) },
                    modifier = Modifier
                        .padding(start = 12.dp, end = 12.dp, bottom = 8.dp + bottomSafe)
                        .onSizeChanged { pinCardHeight = with(density) { it.height.toDp() } }
                )
            }
        }

        AnimatedVisibility(
            visible = selection != null,
            modifier = Modifier.fillMaxSize(),
            enter = slideInVertically(tween(300, easing = EaseOut)) { it },
            exit = slideOutVertically(tween(300, easing = EaseOut)) { it }
        ) {
            DetentSheet(state = sheetState, cornerRadius = 36.dp, onDismiss = { deselect() }) {
                presentedSelection?.let { shown ->
                    StopDepartureSheet(
                        stop = shown.stop,
                        track = shown.track,
                        onGo = { go(shown.stop) },
                        onOpenTrip = { tripId, options ->
                            selection = null
                            scope.launch {
                                delay(350)
                                coverController.presentItinerary(tripId = tripId, otherTripOptions = options)
                            }
                        }
                    )
                }
            }
        }
    }
}

private object StationLayerStyle {
    val radius = Feature.get("radius").asNumber().dp
    val color = Feature.get("color").convertToColor()
    val strokeColor = const(Color.White)
    val strokeWidth = const(2.dp)
    val dotSortKey = Feature.get("importance").convertToNumber()
    val textField = format(span(Feature.get("name").convertToString()))
    val textFont = const(listOf(const("Noto Sans Bold")))
    val textSize = const(11.sp)
    val textHaloWidth = const(1.5.dp)
    val textAnchor = const(SymbolAnchor.Top)
    val textOffset = offset(0.em, 1.1.em)
    val textMaxWidth = const(8.em)
    val textOptional = const(true)
    val labelSortKey = Feature.get("rank").convertToNumber()
}

private data class StationColorPalette(
    val rail: Color,
    val metro: Color,
    val tram: Color,
    val ferry: Color,
    val funicular: Color,
    val other: Color
)

@Composable
private fun StationColorPalette(): StationColorPalette = StationColorPalette(
    rail = StopsMapStyle.color(TransportationMode.RAIL),
    metro = StopsMapStyle.color(TransportationMode.SUBWAY),
    tram = StopsMapStyle.color(TransportationMode.TRAM),
    ferry = StopsMapStyle.color(TransportationMode.FERRY),
    funicular = StopsMapStyle.color(TransportationMode.FUNICULAR),
    other = StopsMapStyle.color(null)
)

private fun StationColorPalette.of(mode: TransportationMode?): Color = when {
    mode == null -> other
    mode.isMainlineRail -> rail
    mode == TransportationMode.SUBWAY || mode == TransportationMode.METRO -> metro
    mode == TransportationMode.TRAM -> tram
    mode == TransportationMode.FERRY -> ferry
    mode == TransportationMode.FUNICULAR -> funicular
    else -> other
}

private fun Color.css(): String {
    val r = (red * 255).roundToInt()
    val g = (green * 255).roundToInt()
    val b = (blue * 255).roundToInt()
    return "rgba($r,$g,$b,$alpha)"
}

private fun stationsGeoJson(stations: Collection<MapStation>, selectedId: String?, palette: StationColorPalette): String =
    buildJsonObject {
        put("type", "FeatureCollection")
        putJsonArray("features") {
            for (station in stations) {
                val importance = min(1.0, station.importance * 3)
                val selected = station.id == selectedId
                addJsonObject {
                    put("type", "Feature")
                    putJsonObject("properties") {
                        put("id", station.id)
                        put("name", station.stop.name)
                        put("color", palette.of(StopsMapStyle.primaryMode(station.stop.modes)).css())
                        put("radius", if (selected) 13.0 else 9.0)
                        put("importance", if (selected) 2.0 else importance)
                        put("rank", if (selected) -2.0 else -importance)
                    }
                    putJsonObject("geometry") {
                        put("type", "Point")
                        putJsonArray("coordinates") {
                            add(station.stop.lon)
                            add(station.stop.lat)
                        }
                    }
                }
            }
        }
    }.toString()

private fun quaiPins(
    stations: Map<String, MapStation>,
    contents: Map<Int, StationOverlayContent>,
    detail: StationDetail,
    selection: StopsMapSelection?,
    area: VisibleBounds?
): List<QuaiPin> {
    val desired = linkedMapOf<String, QuaiPin>()
    val railStations = mutableMapOf<Int, MapStation>()
    for (station in stations.values) {
        if (!station.stop.servesMainlineRail) continue
        val uic = StationLayout.uic(station.id) ?: continue
        if (contents[uic] != null) railStations[uic] = station
    }

    fun add(station: MapStation, track: String, coordinate: LatLng, isRail: Boolean) {
        val key = "${station.id}|$track"
        val isSelected = station.id == selection?.stop?.id && track == selection.track
        if ((detail < StationDetail.ALL_LABELS && !isSelected) || key in desired) return
        if (!isSelected && area != null && !area.contains(coordinate.latitude, coordinate.longitude)) return
        desired[key] = QuaiPin(key, station.stop, track, isRail, coordinate)
    }

    for ((uic, station) in railStations) {
        for (label in contents[uic]?.labels.orEmpty()) add(station, label.text, label.coordinate, isRail = true)
    }
    for (station in stations.values) {
        val hasLayout = StationLayout.uic(station.id)?.let { railStations[it] != null } ?: false
        for (quai in station.quais) {
            val track = quai.track ?: quai.scheduledTrack ?: continue
            val isRail = quai.modes.any { it.isMainlineRail }
            if (isRail && hasLayout) continue
            add(station, StationLayout.normalizedTrack(track), LatLng(quai.lat, quai.lon), isRail)
        }
    }
    return desired.values.toList()
}

@Composable
private fun QuaiSign(text: String, isRail: Boolean, isSelected: Boolean, accent: Color) {
    val inner = if (isSelected) 5.dp else 3.dp
    val outer = if (isSelected) 7.dp else 4.dp
    val size = if (isSelected) 24.dp else 17.dp
    Box(
        Modifier
            .padding(8.dp)
            .iosShadow(Color.Black.copy(alpha = 0.25f), if (isSelected) 3.dp else 1.5.dp, shape = RoundedCornerShape(outer))
            .background(if (isSelected) accent else Color.White, RoundedCornerShape(outer))
            .padding(if (isSelected) 2.dp else 1.dp)
    ) {
        Box(
            Modifier
                .widthIn(min = size)
                .height(size)
                .background(if (isRail) StationStyle.signBlue else Color(0.28f, 0.28f, 0.28f), RoundedCornerShape(inner))
                .padding(horizontal = if (isSelected) 5.dp else 3.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text,
                fontSize = if (isSelected) 14.sp else 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1
            )
        }
    }
}

private val ShortcutBadgeSize = 34.dp

@Composable
private fun ShortcutBadge(shortcut: UserShortcut, accent: Color) {
    val colors = LuxTheme.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(
            Modifier
                .size(ShortcutBadgeSize)
                .iosShadow(Color.Black.copy(alpha = 0.25f), 3.dp, offsetY = 1.dp, shape = CircleShape)
                .background(accent, CircleShape)
                .border(2.5.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            SFSymbol(name = shortcut.symbol, size = (ShortcutBadgeSize.value * 0.42f).sp, color = Color.White, weight = 600)
        }
        Text(
            shortcut.name,
            style = LuxTheme.type.caption.copy(
                fontWeight = FontWeight.SemiBold,
                shadow = Shadow(colors.systemBackground, blurRadius = 3f)
            ),
            color = colors.label,
            maxLines = 1
        )
    }
}

@Composable
private fun DroppedPinMarker(name: String?) {
    val colors = LuxTheme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(34.dp)
                .iosShadow(Color.Black.copy(alpha = 0.25f), 3.dp, shape = CircleShape)
                .background(colors.systemRed, CircleShape)
                .border(2.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            SFSymbol(name = "mappin", size = 16.sp, color = Color.White, weight = 700)
        }
        Box(
            Modifier
                .size(width = 3.dp, height = 8.dp)
                .background(colors.systemRed, RoundedCornerShape(1.5.dp))
        )
        if (name != null) {
            Text(
                name,
                style = LuxTheme.type.caption.copy(fontWeight = FontWeight.SemiBold),
                color = colors.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .widthIn(max = 160.dp)
                    .background(colors.systemBackground.copy(alpha = 0.8f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun MapCircleButton(symbol: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(45.dp)
            .iosShadow(Color.Black.copy(alpha = 0.18f), 2.dp, shape = CircleShape)
            .clip(CircleShape)
            .background(LuxMaterials.ultraThick(), CircleShape)
            .border(0.5.dp, LuxTheme.colors.label.copy(alpha = 0.1f), CircleShape)
            .clickable {
                HapticFeedback.softImpact()
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        SFSymbol(name = symbol, size = 17.sp, color = LuxTheme.accent, weight = 600)
    }
}

@Composable
private fun PinCard(
    pin: StopsMapPin,
    userLocation: LatLng?,
    onClear: () -> Unit,
    onCreateShortcut: () -> Unit,
    onSelect: (SearchResult) -> Unit,
    onGo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LuxTheme.colors
    val accent = LuxTheme.accent
    val shape = RoundedCornerShape(34.dp)
    Column(
        modifier
            .fillMaxWidth()
            .iosShadow(Color.Black.copy(alpha = 0.15f), 8.dp, shape = shape)
            .background(LuxMaterials.regular(), shape)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val shortcut = pin.shortcut
            Box(
                Modifier
                    .size(30.dp)
                    .background(if (shortcut != null) accent else colors.systemRed, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                SFSymbol(name = shortcut?.symbol ?: "mappin", size = if (shortcut != null) 14.sp else 15.sp, color = Color.White, weight = if (shortcut != null) 600 else 700)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    pin.name ?: "Repère sur la carte",
                    style = LuxTheme.type.headline,
                    color = if (pin.isLoading && pin.name == null) colors.tertiaryLabel else colors.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                userLocation?.let { user ->
                    Text(
                        "À ${formatMapDistance(user.distanceTo(pin.coordinate))} de vous",
                        style = LuxTheme.type.subheadline,
                        color = colors.secondaryLabel
                    )
                }
            }
            if (shortcut == null) {
                val enabled = !(pin.isLoading && pin.name == null)
                Box(
                    Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(colors.tertiarySystemFill, CircleShape)
                        .scaleClickable(enabled = enabled) { onCreateShortcut() }
                        .semantics { contentDescription = "Créer un raccourci" },
                    contentAlignment = Alignment.Center
                ) {
                    SFSymbol(name = "star", size = 12.sp, color = colors.secondaryLabel, weight = 700)
                }
            }
            SFSymbol(
                name = "xmark.circle.fill",
                size = 26.sp,
                color = colors.secondaryLabel,
                modifier = Modifier.scaleClickable { onClear() }
            )
        }

        if (pin.isLoading) {
            Row(
                Modifier.height(44.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IosActivityIndicator(size = 14.dp)
                Text("Recherche des arrêts proches…", style = LuxTheme.type.subheadline, color = colors.secondaryLabel)
            }
        } else if (pin.nearby.isNotEmpty()) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                pin.nearby.forEach { stop ->
                    NearbyChip(stop, pin.coordinate) { onSelect(stop) }
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .height(50.dp)
                .scaleClickable { onGo() }
                .background(accent, CircleShape),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SFSymbol(name = "arrow.triangle.turn.up.right.diamond.fill", size = 16.sp, color = Color.White, weight = 600)
            Text("Y aller", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        }
    }
}

@Composable
private fun NearbyChip(stop: SearchResult, from: LatLng, onClick: () -> Unit) {
    val colors = LuxTheme.colors
    val mode = StopsMapStyle.primaryMode(stop.modes)
    Row(
        Modifier
            .height(48.dp)
            .clip(CircleShape)
            .background(colors.tertiarySystemFill.copy(alpha = colors.tertiarySystemFill.alpha * 0.6f + 0.04f), CircleShape)
            .clickable { onClick() }
            .padding(start = 8.dp, end = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(26.dp)
                .background(StopsMapStyle.color(mode), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            SFSymbol(name = "signpost.right", size = 12.sp, color = Color.White, weight = 700)
        }
        Column {
            Text(
                stop.name,
                style = LuxTheme.type.subheadline.copy(fontWeight = FontWeight.SemiBold),
                color = colors.label,
                maxLines = 1
            )
            Text(
                formatMapDistance(from.distanceTo(LatLng(stop.lat, stop.lon))),
                style = LuxTheme.type.caption,
                color = colors.secondaryLabel
            )
        }
    }
}

private fun formatMapDistance(meters: Double): String =
    if (meters >= 1000) String.format(java.util.Locale.getDefault(), "%.1f km", meters / 1000) else "${meters.roundToInt()} m"


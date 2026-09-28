package ch.cclerc.luxapp.ui.onboard

import ch.cclerc.luxapp.ui.map.rememberMarkerImageStore
import ch.cclerc.luxapp.ui.map.StationSignLayers
import ch.cclerc.luxapp.ui.map.MarkerLayer
import ch.cclerc.luxapp.ui.map.MarkerImageStore
import ch.cclerc.luxapp.ui.map.MarkerImageHost
import ch.cclerc.luxapp.ui.map.MarkerAnchor
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.cclerc.luxapp.core.SFSymbol
import ch.cclerc.luxapp.domain.map.LatLng
import ch.cclerc.luxapp.domain.map.distanceTo
import ch.cclerc.luxapp.domain.onboard.Angle360
import ch.cclerc.luxapp.domain.onboard.OnboardPhase
import ch.cclerc.luxapp.domain.onboard.OnboardSession
import ch.cclerc.luxapp.domain.onboard.RoutePath
import ch.cclerc.luxapp.domain.onboard.WalkManeuver
import ch.cclerc.luxapp.domain.onboard.allStops
import ch.cclerc.luxapp.domain.onboard.bearingTo
import ch.cclerc.luxapp.domain.onboard.capitalizedFirstLetter
import ch.cclerc.luxapp.domain.onboard.coordinate
import ch.cclerc.luxapp.domain.onboard.approachingVehicleCoordinate
import ch.cclerc.luxapp.domain.onboard.estimatedVehicleCoordinate
import ch.cclerc.luxapp.domain.onboard.isTransit
import ch.cclerc.luxapp.domain.onboard.offset
import ch.cclerc.luxapp.domain.onboard.remainingApproach
import ch.cclerc.luxapp.domain.onboard.scheduledWalkerCoordinate
import ch.cclerc.luxapp.domain.station.StationDetail
import ch.cclerc.luxapp.domain.station.StationLayoutStore
import ch.cclerc.luxapp.domain.station.StationOverlayContent
import ch.cclerc.luxapp.domain.station.coordinate
import ch.cclerc.luxapp.ui.components.linePillAppearance
import ch.cclerc.luxapp.ui.map.AnnotationBottomAnchor
import ch.cclerc.luxapp.ui.map.AnnotationOverlay
import ch.cclerc.luxapp.ui.map.AnnotationOverlayItem
import ch.cclerc.luxapp.ui.map.LuxMapView
import ch.cclerc.luxapp.ui.map.StationShapeLayers
import ch.cclerc.luxapp.ui.map.boundingBoxOf
import ch.cclerc.luxapp.ui.map.cameraDistanceMeters
import ch.cclerc.luxapp.ui.map.rememberLuxCameraState
import ch.cclerc.luxapp.ui.map.rememberLuxMapStyle
import ch.cclerc.luxapp.ui.map.rememberMapProjector
import ch.cclerc.luxapp.ui.map.toCssColorString
import ch.cclerc.luxapp.ui.map.zoomForCameraDistance
import ch.cclerc.luxapp.ui.theme.LuxTheme
import ch.cclerc.luxapp.ui.theme.TpgFontFamily
import ch.cclerc.luxapp.ui.theme.getLegColor
import ch.cclerc.luxcom.model.TransportationMode
import ch.cclerc.luxcom.model.trip.Leg
import ch.cclerc.luxcom.station.StationLayout
import java.time.Instant
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.expressions.dsl.Feature
import org.maplibre.compose.expressions.dsl.asNumber
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.convertToColor
import org.maplibre.compose.expressions.dsl.dp
import org.maplibre.compose.expressions.dsl.step
import org.maplibre.compose.sources.GeoJsonOptions
import org.maplibre.compose.expressions.value.LineCap
import org.maplibre.compose.expressions.value.LineJoin
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.FillLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.util.MaplibreComposable
import org.maplibre.spatialk.geojson.Position

private val MapWalkBlue = Color(red = 0.1f, green = 0.5f, blue = 1f)
private val MapWalkBlueDark = Color(red = 0.05f, green = 0.3f, blue = 0.7f)
private const val TRANSITION_SECONDS = 0.4
private val LineMetricsOptions = GeoJsonOptions(lineMetrics = true)

private class OnboardCameraMotion {
    var puck by mutableStateOf<LatLng?>(null)
    var puckHeading by mutableStateOf<Double?>(null)
    var displayedAlong = 0.0
    var appliedAlong by mutableDoubleStateOf(-1.0)
    var appliedAlongAt = 0.0
    var followDistance = 430.0
    var followPitch = 40.0
    var followHeading: Double? = null
    var hasPlacedCamera = false
    var transition: Pair<CameraPosition, Double>? = null
    var lastFrame: Double? = null
    var detail by mutableStateOf(StationDetail.HIDDEN)
    var approach by mutableStateOf<Pair<Int, List<LatLng>>?>(null)
    var approachAt = 0.0
    var puckAlong: Pair<Int, Double>? = null
    var approachingVehicle by mutableStateOf<LatLng?>(null)
}

@Composable
fun OnboardMapView(
    session: OnboardSession,
    isFollowing: Boolean,
    showsOverview: Boolean,
    topInset: Dp,
    bottomInset: Dp,
    onUserMovedMap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val style = rememberLuxMapStyle()
    val dark = style.isDark
    val accent = LuxTheme.accent
    val cameraState = rememberLuxCameraState()
    val projector = rememberMapProjector(cameraState)
    val markerImages = rememberMarkerImageStore()
    val motion = remember { OnboardCameraMotion() }
    val following by rememberUpdatedState(isFollowing && !showsOverview)

    var itineraryLayouts by remember { mutableStateOf<Map<Int, StationLayout>>(emptyMap()) }
    val legsSignature = session.legs.joinToString(",") { "${it.tripId}|${it.from.stopId}|${it.to.stopId}" }
    LaunchedEffect(legsSignature) {
        itineraryLayouts = StationLayoutStore.layouts(session.legs)
    }
    val layouts = session.nearbyStationLayouts + itineraryLayouts
    val trackSignature = session.legs.joinToString(",") { "${it.from.track}>${it.to.track}" }
    val stationContent = remember(layouts.keys, trackSignature) { StationOverlayContent.of(session.legs, layouts) }

    LaunchedEffect(session.phase) {
        if (motion.hasPlacedCamera && following) motion.transition = cameraState.position to nowSeconds()
    }
    LaunchedEffect(isFollowing, showsOverview) {
        if (isFollowing && !showsOverview && motion.hasPlacedCamera) {
            motion.transition = cameraState.position to nowSeconds()
        }
    }

    BoxWithConstraints(modifier) {
        val mapHeight = maxHeight
        val visible = (mapHeight - topInset - bottomInset).value
        val followPadding = PaddingValues(
            top = topInset + (visible * 0.36f).coerceAtLeast(0f).dp,
            bottom = bottomInset,
            start = 10.dp,
            end = 10.dp
        )

        LaunchedEffect(showsOverview) {
            if (!showsOverview) return@LaunchedEffect
            motion.transition = null
            val points = mutableListOf<LatLng>()
            val startLeg = if (session.phase == OnboardPhase.ARRIVED) 0 else session.legIndex
            for (index in startLeg until session.legs.size) {
                val path = session.paths.getOrNull(index) ?: continue
                points += if (index == session.legIndex) path.slice(session.alongInLeg, path.length) else path.coordinates
            }
            session.userLocation?.let { points.add(it.coordinate) }
            val box = boundingBoxOf(points, 0.0) ?: return@LaunchedEffect
            cameraState.animateTo(
                box,
                0.0,
                0.0,
                PaddingValues(top = topInset + 40.dp, bottom = bottomInset + 40.dp, start = 40.dp, end = 40.dp),
                1_100.milliseconds
            )
        }

        LaunchedEffect(Unit) {
            while (true) {
                withFrameNanos { nanos ->
                    val timestamp = nanos / 1e9
                    val dt = min(0.1, max(0.0, timestamp - (motion.lastFrame ?: (timestamp - 1.0 / 60))))
                    motion.lastFrame = timestamp
                    fun blend(constant: Double) = 1 - exp(-dt / constant)

                    glidePuck(session, motion, ::blend)
                    if (timestamp - motion.approachAt > 1) {
                        motion.approachAt = timestamp
                        val next = session.remainingApproach(Instant.now())
                        if (next?.first != motion.approach?.first || next?.second?.size != motion.approach?.second?.size ||
                            next?.second?.firstOrNull() != motion.approach?.second?.firstOrNull()
                        ) {
                            motion.approach = next
                        }
                    }
                    glideRouteSplit(session, motion, timestamp, ::blend)
                    val approaching = session.approachingVehicleCoordinate(Instant.now())
                    if (approaching != motion.approachingVehicle) motion.approachingVehicle = approaching
                    if (following) {
                        driveCamera(session, motion, cameraState, followPadding, mapHeight, timestamp, ::blend)
                    }
                    val distance = cameraState.cameraDistanceMeters(mapHeight)
                    val settled = settledDetail(motion.detail, distance)
                    if (settled != motion.detail) motion.detail = settled
                }
            }
        }

        val routeData = routeFeatures(session, accent)
        val currentLength = session.currentPath?.length ?: 0.0
        val fraction = if (currentLength > 0) motion.appliedAlong / currentLength else 0.0
        val approach = motion.approach
        val arrow = arrowFor(session)
        val stopDots = stopDotFeatures(session, accent)

        LuxMapView(
            styleJson = style.json,
            modifier = Modifier.matchParentSize(),
            cameraState = cameraState,
            routeAnchorLayerId = style.firstLabelLayerId,
            showUserLocation = false,
            onUserGesture = onUserMovedMap,
            underlay = {
                StationShapeLayers(
                    content = stationContent,
                    detail = motion.detail,
                    dark = dark,
                    idPrefix = "onboard-station"
                )
                ApproachLayer(approach?.let { (index, coordinates) ->
                    coordinates to getLegColor(session.legs[index], false, accent).copy(alpha = 0.35f)
                })
                RouteLayers(routeData, fraction, session.currentLeg?.isTransit == true)
                ArrowLayers(arrow)
                StopDotLayer(stopDots.first)
            }
        ) {
            StationSignLayers(stationContent, motion.detail, markerImages, idPrefix = "onboard-station")
            SectorChips(session, itineraryLayouts, motion.detail, markerImages)
            LevelChangePins(session, markerImages)
            MarkerLayer(
                id = "onboard-stop-labels",
                store = markerImages,
                items = stopDots.second,
                positionOf = { it.first },
                imageKeyOf = { it.second },
                anchor = MarkerAnchor.Top
            ) { (_, name) ->
                MapLabel(name, Modifier.padding(top = 12.dp))
            }
            val destination = session.legs.lastOrNull()?.let { listOf(LatLng(it.to.lat, it.to.lon)) }.orEmpty()
            val destinationName = session.destinationName.capitalizedFirstLetter
            MarkerLayer(
                id = "onboard-destination",
                store = markerImages,
                items = destination,
                positionOf = { it },
                imageKeyOf = { destinationName },
                anchor = MarkerAnchor.Bottom
            ) {
                DestinationFlag(destinationName)
            }
        }

        MarkerImageHost(markerImages)
        val overlayModifier = Modifier.matchParentSize()

        val nextLeg = session.nextTransitLeg?.second
        val vehicle = motion.approachingVehicle
        if (session.approachingVehicle != null && vehicle != null && nextLeg != null) {
            AnnotationOverlayItem(vehicle.latitude, vehicle.longitude, projector, overlayModifier) {
                LiveVehicleBadge(nextLeg, accent)
            }
        } else if (session.hasEstimatedVehicle && nextLeg != null) {
            session.estimatedVehicleCoordinate(Instant.now())?.let { coordinate ->
                AnnotationOverlayItem(coordinate.latitude, coordinate.longitude, projector, overlayModifier) {
                    EstimatedVehicle(nextLeg, accent)
                }
            }
        }

        if (session.isBehindOrAheadOfSchedule) {
            session.scheduledWalkerCoordinate(Instant.now())?.let { ghost ->
                AnnotationOverlayItem(ghost.latitude, ghost.longitude, projector, overlayModifier) {
                    Box(
                        Modifier
                            .alpha(0.55f)
                            .size(24.dp)
                            .background(MapWalkBlue, CircleShape)
                            .border(2.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        SFSymbol(name = "figure.walk", size = 12.sp, color = Color.White, weight = 700)
                    }
                }
            }
        }

        motion.puck?.let { puck ->
            val camera = cameraState.position
            val relative = motion.puckHeading?.let { Angle360.delta(camera.bearing, it) }
            AnnotationOverlayItem(puck.latitude, puck.longitude, projector, overlayModifier) {
                NavigationPuck(puckStyle(session, accent), relative, camera.tilt)
            }
        }
    }
}

private fun nowSeconds(): Double = System.nanoTime() / 1e9

private fun settledDetail(current: StationDetail, distance: Double): StationDetail {
    val detail = StationDetail.of(distance)
    if (detail == current) return detail
    val farther = StationDetail.of(distance * 1.12)
    val nearer = StationDetail.of(distance / 1.12)
    return if (current in farther..nearer) current else detail
}

private fun glidePuck(session: OnboardSession, motion: OnboardCameraMotion, blend: (Double) -> Double) {
    val target = session.riderCoordinate ?: return
    val current = motion.puck
    val path = session.currentPath
    motion.puck = if (session.riderIsOnPath && path != null) {
        val goal = session.alongInLeg
        val previous = motion.puckAlong
        val along = if (previous != null && previous.first == session.legIndex && abs(goal - previous.second) < 250) {
            previous.second + (goal - previous.second) * blend(0.27)
        } else {
            goal
        }
        motion.puckAlong = session.legIndex to along
        val onPath = path.coordinate(along)
        if (onPath != null && (current == null || current.distanceTo(onPath) < 60)) onPath else target
    } else if (current != null && current.distanceTo(target) < 250) {
        motion.puckAlong = null
        val factor = blend(0.27)
        LatLng(
            current.latitude + (target.latitude - current.latitude) * factor,
            current.longitude + (target.longitude - current.longitude) * factor
        )
    } else {
        motion.puckAlong = null
        target
    }
    val targetHeading = session.heading
    val heading = motion.puckHeading
    motion.puckHeading = when {
        targetHeading == null -> null
        heading == null -> targetHeading
        else -> Angle360.normalized(heading + Angle360.delta(heading, targetHeading) * blend(0.23))
    }
}

private fun glideRouteSplit(session: OnboardSession, motion: OnboardCameraMotion, timestamp: Double, blend: (Double) -> Double) {
    val path = session.currentPath ?: return
    if (path.length <= 0) return
    val target = session.alongInLeg
    motion.displayedAlong = if (abs(target - motion.displayedAlong) > 200) {
        target
    } else {
        motion.displayedAlong + (target - motion.displayedAlong) * blend(0.4)
    }
    if (abs(motion.displayedAlong - motion.appliedAlong) <= 1 || timestamp - motion.appliedAlongAt <= 0.2) return
    motion.appliedAlong = motion.displayedAlong
    motion.appliedAlongAt = timestamp
}

private fun followTarget(session: OnboardSession): Pair<Double, Double> = when (session.phase) {
    OnboardPhase.WALKING -> if (session.isInStation) 260.0 to 30.0 else 430.0 to 40.0
    OnboardPhase.WAITING -> 520.0 to 35.0
    OnboardPhase.RIDING -> {
        val speed = max(0.0, session.userLocation?.speed ?: 0.0)
        min(2600.0, max(800.0, 700 + speed * 55)) to 45.0
    }
    OnboardPhase.ARRIVED -> 650.0 to 30.0
}

private fun cameraFor(
    center: LatLng,
    distance: Double,
    pitch: Double,
    heading: Double,
    padding: PaddingValues,
    mapHeight: Dp
): CameraPosition = CameraPosition(
    bearing = heading,
    target = Position(longitude = center.longitude, latitude = center.latitude),
    tilt = pitch,
    zoom = zoomForCameraDistance(distance, center.latitude, mapHeight),
    padding = padding
)

private fun driveCamera(
    session: OnboardSession,
    motion: OnboardCameraMotion,
    cameraState: org.maplibre.compose.camera.CameraState,
    padding: PaddingValues,
    mapHeight: Dp,
    timestamp: Double,
    blend: (Double) -> Double
) {
    val (distance, pitch) = followTarget(session)
    val targetHeading = motion.puckHeading ?: motion.followHeading ?: 0.0
    val center = motion.puck ?: session.riderCoordinate ?: session.currentPath?.coordinate(session.alongInLeg) ?: return

    if (!motion.hasPlacedCamera) {
        motion.hasPlacedCamera = true
        motion.followDistance = distance
        motion.followPitch = pitch
        motion.followHeading = targetHeading
        cameraState.position = cameraFor(center, distance, pitch, targetHeading, padding, mapHeight)
        return
    }

    val transition = motion.transition
    if (transition != null) {
        val progress = min(1.0, (nowSeconds() - transition.second) / TRANSITION_SECONDS)
        val eased = 1 - (1 - progress).pow(3)
        val goal = cameraFor(center, distance, pitch, targetHeading, padding, mapHeight)
        val from = transition.first
        cameraState.position = CameraPosition(
            bearing = Angle360.normalized(from.bearing + Angle360.delta(from.bearing, goal.bearing) * eased),
            target = Position(
                longitude = from.target.longitude + (goal.target.longitude - from.target.longitude) * eased,
                latitude = from.target.latitude + (goal.target.latitude - from.target.latitude) * eased
            ),
            tilt = from.tilt + (goal.tilt - from.tilt) * eased,
            zoom = from.zoom + (goal.zoom - from.zoom) * eased,
            padding = padding
        )
        if (progress >= 1) {
            motion.transition = null
            motion.followDistance = distance
            motion.followPitch = pitch
            motion.followHeading = targetHeading
        }
        return
    }

    motion.followDistance += (distance - motion.followDistance) * blend(0.8)
    motion.followPitch += (pitch - motion.followPitch) * blend(0.8)
    val current = motion.followHeading
    motion.followHeading = if (current == null) {
        targetHeading
    } else {
        Angle360.normalized(current + Angle360.delta(current, targetHeading) * blend(0.3))
    }
    cameraState.position = cameraFor(center, motion.followDistance, motion.followPitch, motion.followHeading ?: 0.0, padding, mapHeight)
}

private data class RouteData(
    val past: List<List<LatLng>>,
    val current: List<LatLng>,
    val currentColor: Color,
    val future: List<Pair<List<LatLng>, Color>>
)

private fun routeFeatures(session: OnboardSession, accent: Color): RouteData {
    val legs = session.legs
    val paths = session.paths
    val currentIndex = if (session.phase == OnboardPhase.ARRIVED) legs.size else session.legIndex
    val past = mutableListOf<List<LatLng>>()
    val future = mutableListOf<Pair<List<LatLng>, Color>>()
    var current = emptyList<LatLng>()
    var currentColor = MapWalkBlue
    legs.forEachIndexed { index, leg ->
        val path = paths.getOrNull(index) ?: return@forEachIndexed
        if (path.coordinates.size < 2) return@forEachIndexed
        val color = if (leg.isTransit) getLegColor(leg, false, accent) else MapWalkBlue
        when {
            index < currentIndex -> past.add(path.coordinates)
            index == currentIndex -> {
                current = path.coordinates
                currentColor = color
            }
            else -> future.add(path.coordinates to color.copy(alpha = 0.75f))
        }
    }
    return RouteData(past, current, currentColor, future)
}

private fun lineFeatures(lines: List<Pair<List<LatLng>, Color?>>): String = buildJsonObject {
    put("type", "FeatureCollection")
    putJsonArray("features") {
        lines.forEach { (line, color) ->
            if (line.size < 2) return@forEach
            addJsonObject {
                put("type", "Feature")
                putJsonObject("properties") { color?.let { put("color", it.toCssColorString()) } }
                putJsonObject("geometry") {
                    put("type", "LineString")
                    putJsonArray("coordinates") {
                        line.forEach { point -> addJsonArray { add(point.longitude); add(point.latitude) } }
                    }
                }
            }
        }
    }
}.toString()

@Composable
@MaplibreComposable
private fun SimpleLine(id: String, lines: List<Pair<List<LatLng>, Color?>>, color: Color?, width: Dp) {
    val json = remember(lines) { lineFeatures(lines) }
    val source = rememberGeoJsonSource(GeoJsonData.JsonString(json))
    LineLayer(
        id = id,
        source = source,
        color = if (color != null) const(color) else Feature.get("color").convertToColor(),
        width = const(width),
        cap = const(LineCap.Round),
        join = const(LineJoin.Round)
    )
}

@Composable
@MaplibreComposable
private fun ApproachLayer(approach: Pair<List<LatLng>, Color>?) {
    SimpleLine("onboard-approach", listOfNotNull(approach?.let { it.first to it.second }), null, 5.dp)
}

@Composable
@MaplibreComposable
private fun RouteLayers(data: RouteData, fraction: Double, currentIsTransit: Boolean) {
    SimpleLine("onboard-past", data.past.map { it to null }, Color.Gray.copy(alpha = 0.45f), 5.dp)
    SimpleLine("onboard-future-casing", data.future.map { it.first to null }, Color.White, 8.dp)
    SimpleLine("onboard-future", data.future.map { it.first to it.second }, null, 5.dp)

    val json = remember(data.current) { lineFeatures(listOf(data.current to null)) }
    val source = rememberGeoJsonSource(GeoJsonData.JsonString(json), LineMetricsOptions)
    val split = fraction.coerceIn(0.0001, 0.9999)
    val clear = Color.Transparent

    SplitLine("onboard-travelled", source, split, Color.Gray.copy(alpha = 0.5f), clear, 6.dp)
    SplitLine("onboard-casing", source, split, clear, Color.White, if (currentIsTransit) 11.dp else 10.dp)
    SplitLine("onboard-remaining", source, split, clear, data.currentColor, if (currentIsTransit) 7.dp else 6.dp)
}

@Composable
@MaplibreComposable
private fun SplitLine(
    id: String,
    source: org.maplibre.compose.sources.GeoJsonSource,
    split: Double,
    before: Color,
    after: Color,
    width: Dp
) {
    LineLayer(
        id = id,
        source = source,
        gradient = step(Feature.lineProgress(), const(before), split to const(after)),
        width = const(width),
        cap = const(LineCap.Round),
        join = const(LineJoin.Round)
    )
}

private class TurnArrow(val shaft: List<LatLng>, val head: List<LatLng>)

private fun turnArrow(path: RoutePath, along: Double): TurnArrow? {
    val start = max(0.0, along - 12)
    val end = min(path.length, along + 9)
    if (end - along <= 4) return null
    val shaftEnd = path.coordinate(end) ?: return null
    val beforeEnd = path.coordinate(end - 4) ?: return null
    if (beforeEnd.distanceTo(shaftEnd) <= 0.5) return null
    val bearing = beforeEnd.bearingTo(shaftEnd)
    val shaft = path.slice(start, end)
    if (shaft.size < 2) return null
    return TurnArrow(
        shaft,
        listOf(
            shaftEnd.offset(6.5, bearing),
            shaftEnd.offset(4.8, bearing + 90),
            shaftEnd.offset(4.8, bearing - 90)
        )
    )
}

private fun arrowFor(session: OnboardSession): TurnArrow? {
    if (session.phase != OnboardPhase.WALKING || session.isOffRoute) return null
    val path = session.currentPath ?: return null
    val maneuver: WalkManeuver = session.nextManeuver ?: return null
    if (maneuver.along >= path.length - 20 || path.length - session.alongInLeg <= 25) return null
    return turnArrow(path, maneuver.along)
}

private fun polygonJson(ring: List<LatLng>?): String = buildJsonObject {
    put("type", "FeatureCollection")
    putJsonArray("features") {
        if (ring != null && ring.size >= 3) {
            addJsonObject {
                put("type", "Feature")
                putJsonObject("properties") {}
                putJsonObject("geometry") {
                    put("type", "Polygon")
                    putJsonArray("coordinates") {
                        addJsonArray {
                            (ring + ring.first()).forEach { point -> addJsonArray { add(point.longitude); add(point.latitude) } }
                        }
                    }
                }
            }
        }
    }
}.toString()

@Composable
@MaplibreComposable
private fun ArrowLayers(arrow: TurnArrow?) {
    val shaft = listOfNotNull(arrow?.let { it.shaft to null })
    val headJson = remember(arrow) { polygonJson(arrow?.head) }
    val headOutlineJson = remember(arrow) { lineFeatures(listOfNotNull(arrow?.let { (it.head + it.head.first()) to null })) }
    val headSource = rememberGeoJsonSource(GeoJsonData.JsonString(headJson))
    val headOutlineSource = rememberGeoJsonSource(GeoJsonData.JsonString(headOutlineJson))

    SimpleLine("onboard-arrow-shaft-dark", shaft, MapWalkBlueDark, 11.dp)
    FillLayer(id = "onboard-arrow-head-dark", source = headSource, color = const(MapWalkBlueDark))
    LineLayer(
        id = "onboard-arrow-head-outline",
        source = headOutlineSource,
        color = const(MapWalkBlueDark),
        width = const(3.dp),
        join = const(LineJoin.Round)
    )
    SimpleLine("onboard-arrow-shaft", shaft, Color.White, 6.dp)
    FillLayer(id = "onboard-arrow-head", source = headSource, color = const(Color.White))
}

private data class GroundDot(val point: LatLng, val diameter: Float, val ring: Color, val ringWidth: Float)

private fun focusedTransitLeg(session: OnboardSession): Pair<Int, Leg>? {
    if (session.phase == OnboardPhase.ARRIVED) return null
    val leg = session.currentLeg
    if (leg != null && leg.isTransit) return session.legIndex to leg
    return session.nextTransitLeg
}

private fun stopDotFeatures(session: OnboardSession, accent: Color): Pair<List<GroundDot>, List<Pair<LatLng, String>>> {
    val (index, leg) = focusedTransitLeg(session) ?: return emptyList<GroundDot>() to emptyList()
    val stops = leg.allStops
    val color = getLegColor(leg, false, accent)
    val passed = if (index == session.legIndex && session.phase == OnboardPhase.RIDING) session.nextStopIndex else 0
    val path = session.paths.getOrNull(index)
    val alongs = session.stopAlongs.getOrNull(index).orEmpty()
    val destination = session.legs.lastOrNull()?.to?.coordinate
    val dots = mutableListOf<GroundDot>()
    val labels = mutableListOf<Pair<LatLng, String>>()
    stops.forEachIndexed { stopIndex, stop ->
        val isEnd = stopIndex == 0 || stopIndex == stops.size - 1
        var coordinate = stop.coordinate
        val isDestination = destination?.let { coordinate.distanceTo(it) < 80 } ?: false
        if (alongs.size == stops.size) {
            path?.coordinate(alongs[stopIndex])?.let { onLine -> if (onLine.distanceTo(coordinate) < 60) coordinate = onLine }
        }
        val diameter = if (isEnd) 16f else 9f
        val ringWidth = if (isEnd) 4f else 2.5f
        dots.add(GroundDot(coordinate, diameter, if (stopIndex < passed) Color.Gray else color, ringWidth))
        if (isEnd && !isDestination) labels.add(coordinate to stop.name)
    }
    return dots to labels
}

@Composable
@MaplibreComposable
private fun StopDotLayer(dots: List<GroundDot>) {
    val json = remember(dots) {
        buildJsonObject {
            put("type", "FeatureCollection")
            putJsonArray("features") {
                dots.forEach { dot ->
                    addJsonObject {
                        put("type", "Feature")
                        putJsonObject("properties") {
                            put("radius", dot.diameter / 2)
                            put("ring", dot.ring.toCssColorString())
                            put("ringWidth", dot.ringWidth)
                        }
                        putJsonObject("geometry") {
                            put("type", "Point")
                            putJsonArray("coordinates") { add(dot.point.longitude); add(dot.point.latitude) }
                        }
                    }
                }
            }
        }.toString()
    }
    val source = rememberGeoJsonSource(GeoJsonData.JsonString(json))
    CircleLayer(
        id = "onboard-stop-dots",
        source = source,
        radius = Feature.get("radius").asNumber().dp,
        color = const(Color.White),
        strokeColor = Feature.get("ring").convertToColor(),
        strokeWidth = Feature.get("ringWidth").asNumber().dp
    )
}

@Composable
@MaplibreComposable
private fun SectorChips(
    session: OnboardSession,
    layouts: Map<Int, StationLayout>,
    detail: StationDetail,
    store: MarkerImageStore
) {
    var sectors: List<StationLayout.Sector> = emptyList()
    var covered: Set<String>? = null
    var firstClass: Set<String> = emptySet()
    val leg = session.nextTransitLeg?.second
    if (detail >= StationDetail.ALL_LABELS && (session.phase == OnboardPhase.WALKING || session.phase == OnboardPhase.WAITING) &&
        leg != null && leg.mode.isMainlineRail
    ) {
        val layout = StationLayout.uic(leg.from.stopId)?.let { layouts[it] }
        val track = layout?.track(leg.from.track ?: leg.from.scheduledTrack, leg.from.stopId)
        if (track != null) {
            sectors = track.sectors
            session.formation?.let {
                covered = it.coveredSectors
                firstClass = it.sectors.first.toSet()
            }
        }
    }
    val coveredSet = covered
    MarkerLayer(
        id = "onboard-sectors",
        store = store,
        items = sectors,
        positionOf = { it.coordinate },
        imageKeyOf = { "${it.s}|${coveredSet?.contains(it.s)}|${it.s in firstClass}" }
    ) { sector ->
        SectorChipView(sector.s, coveredSet?.contains(sector.s), sector.s in firstClass)
    }
}

@Composable
@MaplibreComposable
private fun LevelChangePins(session: OnboardSession, store: MarkerImageStore) {
    if (session.phase != OnboardPhase.WALKING) return
    val path = session.currentPath ?: return
    val changes = session.maneuvers.getOrNull(session.legIndex).orEmpty().filter { it.isLevelChange }
    val pins = changes.mapNotNull { change -> path.coordinate(change.along)?.let { it to change } }
    val shape = RoundedCornerShape(7.dp)
    MarkerLayer(
        id = "onboard-level-changes",
        store = store,
        items = pins,
        positionOf = { it.first },
        imageKeyOf = { it.second.symbolName }
    ) { (_, change) ->
        Box(
            Modifier
                .shadow(2.dp, shape)
                .size(26.dp)
                .background(MapWalkBlue, shape)
                .border(2.dp, Color.White, shape)
                .semantics { contentDescription = change.instruction },
            contentAlignment = Alignment.Center
        ) {
            SFSymbol(name = change.symbolName, size = 13.sp, color = Color.White, weight = 700)
        }
    }
}

@Composable
private fun MapLabel(text: String, modifier: Modifier = Modifier) {
    val colors = LuxTheme.colors
    Text(
        text = text,
        style = LuxTheme.type.caption.copy(
            fontWeight = FontWeight.SemiBold,
            shadow = androidx.compose.ui.graphics.Shadow(colors.systemBackground, blurRadius = 4f)
        ),
        color = colors.label,
        maxLines = 1,
        modifier = modifier
    )
}

@Composable
private fun DestinationFlag(name: String) {
    Box(Modifier.size(30.dp)) {
        Box(
            Modifier
                .shadow(3.dp, CircleShape)
                .size(30.dp)
                .background(gradientOf(LuxTheme.colors.systemRed), CircleShape)
                .border(2.5.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            SFSymbol(name = "flag.checkered", size = 13.sp, color = Color.White, weight = 700)
        }
        MapLabel(
            name,
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = 32.dp)
                .wrapContentSize(unbounded = true)
        )
    }
}

@Composable
private fun LiveVehicleBadge(leg: Leg, accent: Color) {
    val color = getLegColor(leg, false, accent)
    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .shadow(4.dp, CircleShape, ambientColor = color, spotColor = color)
                .size(32.dp)
                .background(color, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                leg.routeShortName ?: "",
                style = TextStyle(fontFamily = TpgFontFamily, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
            )
        }
        Box(
            Modifier
                .offset(x = 13.dp, y = (-13).dp)
                .shadow(1.5.dp, CircleShape)
                .size(16.dp)
                .background(Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            SFSymbol(name = "dot.radiowaves.up.forward", size = 8.sp, color = color, weight = 700)
        }
    }
}

@Composable
private fun EstimatedVehicle(leg: Leg, accent: Color) {
    val appearance = linePillAppearance(leg.routeShortName ?: "", leg.agencyId, leg.mode, accent)
    Box(
        Modifier
            .alpha(0.85f)
            .background(appearance.lineColor, RoundedCornerShape(13.dp))
            .border(2.5.dp, Color.White, RoundedCornerShape(13.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            leg.routeShortName ?: "",
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            color = appearance.textColorOnLineColor
        )
    }
}

sealed interface PuckStyle {
    data object Walker : PuckStyle
    data class Vehicle(val color: Color, val textColor: Color, val symbol: String) : PuckStyle
}

fun vehicleSymbol(mode: TransportationMode): String = when (mode) {
    TransportationMode.TRAM -> "lightrail.fill"
    TransportationMode.FERRY -> "ferry.fill"
    TransportationMode.SUBWAY, TransportationMode.METRO -> "tram.tunnel.fill"
    TransportationMode.FUNICULAR -> "cablecar.fill"
    TransportationMode.BUS, TransportationMode.COACH -> "bus.fill"
    else -> if (mode.isRail) "tram.fill" else "bus.fill"
}

private fun puckStyle(session: OnboardSession, accent: Color): PuckStyle {
    val leg = session.currentLeg
    if (session.phase == OnboardPhase.RIDING && leg != null) {
        val appearance = linePillAppearance(leg.routeShortName ?: "", leg.agencyId, leg.mode, accent)
        return PuckStyle.Vehicle(appearance.lineColor, appearance.textColorOnLineColor, vehicleSymbol(leg.mode))
    }
    return PuckStyle.Walker
}

private val HeadingArrowShape = GenericShape { size, _ ->
    moveTo(size.width / 2, 0f)
    lineTo(size.width, size.height)
    lineTo(size.width / 2, size.height - size.height * 0.32f)
    lineTo(0f, size.height)
    close()
}

@Composable
fun NavigationPuck(style: PuckStyle, heading: Double?, pitch: Double) {
    AnimatedContent(
        targetState = style,
        transitionSpec = { (scaleIn() + fadeIn()) togetherWith (scaleOut() + fadeOut()) },
        modifier = Modifier
            .graphicsLayer {
                rotationX = pitch.toFloat()
                cameraDistance = 12f * density
            }
            .semantics { contentDescription = "Votre position" },
        label = "puck"
    ) { shown ->
        when (shown) {
            PuckStyle.Walker -> Box(Modifier.size(76.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(64.dp).background(WalkBlue.copy(alpha = 0.15f), CircleShape))
                Box(Modifier.shadow(4.dp, CircleShape).size(42.dp).background(Color.White, CircleShape))
                if (heading != null) {
                    SFSymbol(
                        name = "location.north.fill",
                        size = 21.sp,
                        color = WalkBlue,
                        weight = 900,
                        modifier = Modifier.rotate(heading.toFloat())
                    )
                } else {
                    Box(Modifier.size(22.dp).background(gradientOf(WalkBlue), CircleShape))
                }
            }
            is PuckStyle.Vehicle -> Box(Modifier.size(76.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(76.dp).background(shown.color.copy(alpha = 0.16f), CircleShape))
                if (heading != null) {
                    Box(Modifier.size(76.dp).rotate(heading.toFloat()), contentAlignment = Alignment.TopCenter) {
                        Box(
                            Modifier
                                .offset(y = 5.dp)
                                .shadow(1.5.dp, HeadingArrowShape)
                                .size(width = 18.dp, height = 16.dp)
                                .background(Color.White, HeadingArrowShape)
                                .padding(2.dp)
                                .background(shown.color, HeadingArrowShape)
                        )
                    }
                }
                val shape = RoundedCornerShape(14.dp)
                Box(
                    Modifier
                        .shadow(4.dp, shape)
                        .size(46.dp)
                        .background(gradientOf(shown.color), shape)
                        .border(3.dp, Color.White, shape),
                    contentAlignment = Alignment.Center
                ) {
                    SFSymbol(name = shown.symbol, size = 21.sp, color = shown.textColor, weight = 600)
                }
            }
        }
    }
}

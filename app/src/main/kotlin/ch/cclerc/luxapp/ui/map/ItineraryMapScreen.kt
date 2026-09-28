package ch.cclerc.luxapp.ui.map

import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.maplibre.compose.camera.CameraState
import androidx.compose.ui.unit.DpOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.cclerc.luxapp.core.LocationService
import ch.cclerc.luxapp.domain.map.LatLng
import ch.cclerc.luxapp.viewmodel.ItineraryViewModel
import ch.cclerc.luxapp.viewmodel.MapTrackingMode
import ch.cclerc.luxapp.viewmodel.StopAnnotation
import ch.cclerc.luxcom.model.Place
import kotlin.math.max
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.spatialk.geojson.BoundingBox
import org.maplibre.spatialk.geojson.Position

const val ITINERARY_NEARBY_CAMERA_DISTANCE_METERS = 10_000.0

private const val MINIMUM_BOUNDS_SPAN_DEGREES = 0.0009
private val BoundsFitDuration = 500.milliseconds
private val TrackingFollowDuration = 300.milliseconds
private val RevealDuration = 600.milliseconds

@Composable
fun ItineraryMapScreen(
    viewModel: ItineraryViewModel,
    sheetPeekHeight: Dp,
    modifier: Modifier = Modifier,
    fromNearby: Boolean = false,
    selectedStop: Place? = null,
    stopSheetHeight: Dp = 0.dp,
    onOpenStop: (Place) -> Unit = {},
    onMapTap: () -> Unit = {},
    onTrackingCancelled: () -> Unit = {}
) {
    val style = rememberLuxMapStyle()
    val cameraState = rememberLuxCameraState()
    val markerImages = rememberMarkerImageStore()


    var didFitBounds by remember { mutableStateOf(false) }

    val overlays = remember(viewModel.routeOverlays) {
        viewModel.routeOverlays.map { RouteOverlay(it.id, it.coordinates, it.color) }
    }
    val stops = viewModel.mapAnnotations
    val vehicles = rememberAnimatedVehicleAnnotations(viewModel.vehicleAnnotations)
    val walking = rememberAnimatedWalkingAnnotations(viewModel.walkingAnnotations)

    DisposableEffect(Unit) {
        LocationService.startMonitoring()
        onDispose {
            LocationService.stopMonitoring()
            viewModel.trackingMode = MapTrackingMode.NONE
            viewModel.stopAllTasks()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.loadItinerary()
    }

    BoxWithConstraints(modifier = modifier) {
        val mapHeight = maxHeight
        val cameraPadding = remember(sheetPeekHeight) { PaddingValues(bottom = sheetPeekHeight) }

        LaunchedEffect(viewModel.targetBounds, cameraPadding) {
            if (didFitBounds) return@LaunchedEffect
            val bounds = viewModel.targetBounds ?: return@LaunchedEffect
            val box = boundingBoxOf(bounds.coordinates, bounds.paddingFraction) ?: return@LaunchedEffect
            cameraState.animateTo(box, 0.0, 0.0, cameraPadding, BoundsFitDuration)
            didFitBounds = true
        }

        LaunchedEffect(fromNearby, viewModel.isLoading, mapHeight) {
            if (!fromNearby || viewModel.isLoading) return@LaunchedEffect
            val userLocation = LocationService.location.value ?: return@LaunchedEffect
            val zoom = zoomForCameraDistance(
                ITINERARY_NEARBY_CAMERA_DISTANCE_METERS,
                userLocation.latitude,
                mapHeight
            )
            cameraState.animateTo(
                cameraState.position.copy(
                    target = Position(
                        longitude = userLocation.longitude,
                        latitude = userLocation.latitude
                    ),
                    zoom = zoom,
                    padding = cameraPadding
                ),
                BoundsFitDuration
            )
            didFitBounds = true
        }

        LaunchedEffect(viewModel.trackingMode) {
            val mode = viewModel.trackingMode
            if (mode == MapTrackingMode.NONE) return@LaunchedEffect
            combine(LocationService.location, LocationService.heading) { user, heading -> user to heading }
                .collectLatest { (userLocation, heading) ->
                    userLocation ?: return@collectLatest
                    val current = cameraState.position
                    val bearing = if (mode == MapTrackingMode.FOLLOW_WITH_HEADING) {
                        (heading ?: current.bearing.toFloat()).toDouble()
                    } else {
                        0.0
                    }
                    cameraState.animateTo(
                        current.copy(
                            target = Position(
                                longitude = userLocation.longitude,
                                latitude = userLocation.latitude
                            ),
                            bearing = bearing
                        ),
                        TrackingFollowDuration
                    )
                }
        }

        val mapWidth = maxWidth
        LaunchedEffect(selectedStop) {
            val stop = selectedStop ?: return@LaunchedEffect
            revealCoordinate(
                cameraState,
                LatLng(stop.lat, stop.lon),
                mapWidth,
                mapHeight,
                top = 140.dp,
                bottom = mapHeight - stopSheetHeight - 40.dp
            )
            viewModel.disableTrackingIfNeeded()
        }

        MarkerImageHost(markerImages)

        LuxMapView(
            styleJson = style.json,
            modifier = Modifier.matchParentSize(),
            cameraState = cameraState,
            routeOverlays = overlays,
            routeAnchorLayerId = style.firstLabelLayerId,
            showUserLocation = true,
            contentPaddingBottom = sheetPeekHeight,
            onUserGesture = {
                viewModel.disableTrackingIfNeeded()
                onTrackingCancelled()
            },
            onMapClick = { if (selectedStop != null) onMapTap() },
            underlay = {
                StationShapeLayers(
                    content = viewModel.stationOverlay,
                    detail = viewModel.stationDetail,
                    dark = style.isDark
                )
            },
            onCameraChange = { position ->
                val metersPerDp = CameraDistance.metersPerDpForZoom(
                    position.zoom,
                    position.target.latitude
                )
                viewModel.setCameraDistance(
                    CameraDistance.cameraDistanceMeters(metersPerDp, mapHeight.value)
                )
            }
        ) {
            StationSignLayers(
                content = viewModel.stationOverlay,
                detail = viewModel.stationDetail,
                store = markerImages
            )
            StopDotLayers(
                stops = stops,
                showingIntermediateStops = viewModel.showingIntermediateStops,
                selectedPlace = selectedStop,
                onStopClick = { annotation -> onOpenStop(annotation.place) }
            )
            WalkingDotLayer(walking = walking)
            VehicleMarkerLayers(vehicles = vehicles)
        }
    }
}

suspend fun revealCoordinate(
    cameraState: CameraState,
    coordinate: LatLng,
    width: Dp,
    height: Dp,
    top: Dp,
    bottom: Dp
) {
    if (height <= 0.dp || bottom <= top) return
    val projection = cameraState.projection ?: return
    val point = projection.screenLocationFromPosition(Position(longitude = coordinate.longitude, latitude = coordinate.latitude))
    val targetY = top + (bottom - top) * 0.8f
    val center = projection.positionFromScreenLocation(
        DpOffset(width / 2 + point.x - width / 2, height / 2 + point.y - targetY)
    )
    cameraState.animateTo(cameraState.position.copy(target = center), RevealDuration)
}

fun boundingBoxOf(coordinates: List<LatLng>, paddingFraction: Double): BoundingBox? {
    if (coordinates.isEmpty()) return null

    var minLatitude = Double.MAX_VALUE
    var maxLatitude = -Double.MAX_VALUE
    var minLongitude = Double.MAX_VALUE
    var maxLongitude = -Double.MAX_VALUE

    coordinates.forEach { coordinate ->
        if (coordinate.latitude < minLatitude) minLatitude = coordinate.latitude
        if (coordinate.latitude > maxLatitude) maxLatitude = coordinate.latitude
        if (coordinate.longitude < minLongitude) minLongitude = coordinate.longitude
        if (coordinate.longitude > maxLongitude) maxLongitude = coordinate.longitude
    }

    val latitudeSpan = max(maxLatitude - minLatitude, MINIMUM_BOUNDS_SPAN_DEGREES)
    val longitudeSpan = max(maxLongitude - minLongitude, MINIMUM_BOUNDS_SPAN_DEGREES)
    val latitudePadding = latitudeSpan * paddingFraction
    val longitudePadding = longitudeSpan * paddingFraction

    return BoundingBox(
        (minLongitude - longitudePadding).coerceIn(-180.0, 180.0),
        (minLatitude - latitudePadding).coerceIn(-90.0, 90.0),
        (maxLongitude + longitudePadding).coerceIn(-180.0, 180.0),
        (maxLatitude + latitudePadding).coerceIn(-90.0, 90.0)
    )
}

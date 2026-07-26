package ch.cclerc.luxapp.ui.map

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.cclerc.luxapp.core.LocationService
import ch.cclerc.luxapp.domain.map.LatLng
import ch.cclerc.luxapp.ui.theme.LuxTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.maplibre.compose.camera.CameraMoveReason
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.CameraState
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.expressions.dsl.Feature
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.convertToColor
import org.maplibre.compose.expressions.value.LineCap
import org.maplibre.compose.expressions.value.LineJoin
import org.maplibre.compose.layers.Anchor
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.location.LocationPuck
import org.maplibre.compose.location.LocationPuckColors
import org.maplibre.compose.location.rememberDefaultLocationProvider
import org.maplibre.compose.location.rememberUserLocationState
import org.maplibre.compose.map.GestureOptions
import org.maplibre.compose.map.MapOptions
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.OrnamentOptions
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.GeoJsonSource
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.util.ClickResult
import org.maplibre.compose.util.MaplibreComposable
import org.maplibre.spatialk.geojson.Position

@Immutable
data class RouteOverlay(
    val id: String,
    val coordinates: List<LatLng>,
    val color: Color
)

val LuxRouteLineWidth = 4.dp

const val LUX_ROUTE_LAYER_ID = "lux-route-lines"

const val LUX_USER_LOCATION_ID_PREFIX = "lux-user-location"

@Composable
fun rememberLuxCameraState(firstPosition: CameraPosition = CameraPosition()): CameraState =
    rememberCameraState(firstPosition)

@Composable
fun LuxMapView(
    styleJson: String,
    modifier: Modifier = Modifier,
    cameraState: CameraState = rememberLuxCameraState(),
    routeOverlays: List<RouteOverlay> = emptyList(),
    routeAnchorLayerId: String? = null,
    showUserLocation: Boolean = false,
    contentPaddingBottom: Dp = 0.dp,
    onUserGesture: () -> Unit = {},
    onCameraChange: (CameraPosition) -> Unit = {},
    onMapClick: (LatLng) -> Unit = {},
    content: @Composable @MaplibreComposable () -> Unit = {}
) {
    val baseStyle = remember(styleJson) { BaseStyle.Json(styleJson) }
    val cameraPadding = remember(contentPaddingBottom) { PaddingValues(bottom = contentPaddingBottom) }
    val options = remember {
        MapOptions(
            gestureOptions = GestureOptions.Standard,
            ornamentOptions = OrnamentOptions.AllDisabled
        )
    }

    LaunchedEffect(cameraState, cameraPadding) {
        val current = cameraState.position
        if (current.padding != cameraPadding) {
            cameraState.position = current.copy(padding = cameraPadding)
        }
    }

    LaunchedEffect(cameraState, onUserGesture) {
        snapshotFlow { cameraState.moveReason }
            .distinctUntilChanged()
            .collect { reason -> if (reason == CameraMoveReason.GESTURE) onUserGesture() }
    }

    LaunchedEffect(cameraState, onCameraChange) {
        snapshotFlow { cameraState.position }
            .distinctUntilChanged()
            .collect { position -> onCameraChange(position) }
    }

    MaplibreMap(
        modifier = modifier,
        baseStyle = baseStyle,
        cameraState = cameraState,
        options = options,
        onMapClick = { position, _ ->
            onMapClick(LatLng(position.latitude, position.longitude))
            ClickResult.Pass
        }
    ) {
        RouteLineOverlays(overlays = routeOverlays, anchorLayerId = routeAnchorLayerId)
        if (showUserLocation) {
            UserLocationPuck(cameraState = cameraState)
        }
        content()
    }
}

@Composable
@MaplibreComposable
private fun RouteLineOverlays(overlays: List<RouteOverlay>, anchorLayerId: String?) {
    val featureCollection = remember(overlays) { routeFeatureCollectionJson(overlays) }
    val source = rememberGeoJsonSource(data = GeoJsonData.JsonString(featureCollection))

    if (anchorLayerId != null) {
        Anchor.Below(anchorLayerId) {
            RouteLineLayer(source)
        }
    } else {
        RouteLineLayer(source)
    }
}

@Composable
@MaplibreComposable
private fun RouteLineLayer(source: GeoJsonSource) {
    LineLayer(
        id = LUX_ROUTE_LAYER_ID,
        source = source,
        color = Feature.get("color").convertToColor(),
        width = const(LuxRouteLineWidth),
        cap = const(LineCap.Round),
        join = const(LineJoin.Round)
    )
}

@Composable
@MaplibreComposable
private fun UserLocationPuck(cameraState: CameraState) {
    val context = LocalContext.current
    val authorization by LocationService.authorizationState.collectAsStateWithLifecycle()
    val granted = remember(context, authorization) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }

    if (granted) {
        GrantedUserLocationPuck(cameraState)
    }
}

@Composable
@MaplibreComposable
private fun GrantedUserLocationPuck(cameraState: CameraState) {
    val userLocationState = rememberUserLocationState(
        locationProvider = rememberDefaultLocationProvider()
    )
    val accent = LuxTheme.accent
    val puckColors = remember(accent) {
        LocationPuckColors(
            dotFillColorCurrentLocation = accent,
            dotFillColorOldLocation = accent,
            dotStrokeColor = Color.White,
            shadowColor = Color.Black.copy(alpha = 0.2f),
            accuracyStrokeColor = Color.Transparent,
            accuracyFillColor = Color.Transparent,
            bearingColor = Color.Transparent
        )
    }

    LocationPuck(
        idPrefix = LUX_USER_LOCATION_ID_PREFIX,
        location = userLocationState.location,
        cameraState = cameraState,
        bearing = null,
        colors = puckColors,
        showBearing = false,
        showBearingAccuracy = false
    )
}

fun routeFeatureCollectionJson(overlays: List<RouteOverlay>): String = buildJsonObject {
    put("type", "FeatureCollection")
    putJsonArray("features") {
        overlays.forEach { overlay ->
            if (overlay.coordinates.size >= 2) {
                addJsonObject {
                    put("type", "Feature")
                    put("id", overlay.id)
                    putJsonObject("properties") {
                        put("id", overlay.id)
                        put("color", overlay.color.toCssColorString())
                    }
                    putJsonObject("geometry") {
                        put("type", "LineString")
                        putJsonArray("coordinates") {
                            overlay.coordinates.forEach { coordinate ->
                                addJsonArray {
                                    add(coordinate.longitude)
                                    add(coordinate.latitude)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}.toString()

fun Color.toCssColorString(): String {
    val argb = toArgb()
    val red = (argb shr 16) and 0xFF
    val green = (argb shr 8) and 0xFF
    val blue = argb and 0xFF
    val alpha = ((argb shr 24) and 0xFF) / 255f
    return "rgba($red, $green, $blue, $alpha)"
}

fun LatLng.toPosition(): Position = Position(longitude = longitude, latitude = latitude)

fun Position.toLatLng(): LatLng = LatLng(latitude = latitude, longitude = longitude)

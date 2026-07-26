package ch.cclerc.luxapp.ui.map

import androidx.compose.ui.unit.Dp
import ch.cclerc.luxapp.domain.map.degreesToRadians
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.tan
import org.maplibre.compose.camera.CameraState

object CameraDistance {

    const val FIELD_OF_VIEW_DEGREES = 30.0
    const val EARTH_CIRCUMFERENCE_METERS = 40_075_016.686
    const val TILE_SIZE_DP = 512.0

    val HALF_FIELD_OF_VIEW_TANGENT = tan(FIELD_OF_VIEW_DEGREES / 2.0 * PI / 180.0)
    val METERS_PER_DP_AT_ZOOM_ZERO = EARTH_CIRCUMFERENCE_METERS / TILE_SIZE_DP

    fun cameraDistanceMeters(metersPerDp: Double, mapHeightDp: Float): Double {
        if (mapHeightDp <= 0f || metersPerDp <= 0.0) return 0.0
        return metersPerDp * mapHeightDp / (2.0 * HALF_FIELD_OF_VIEW_TANGENT)
    }

    fun metersPerDpForCameraDistance(cameraDistanceMeters: Double, mapHeightDp: Float): Double {
        if (mapHeightDp <= 0f || cameraDistanceMeters <= 0.0) return 0.0
        return cameraDistanceMeters * 2.0 * HALF_FIELD_OF_VIEW_TANGENT / mapHeightDp
    }

    fun metersPerDpForZoom(zoom: Double, latitude: Double): Double =
        METERS_PER_DP_AT_ZOOM_ZERO * cos(latitude.degreesToRadians()) / 2.0.pow(zoom)

    fun zoomForCameraDistance(
        cameraDistanceMeters: Double,
        latitude: Double,
        mapHeightDp: Float
    ): Double {
        val metersPerDp = metersPerDpForCameraDistance(cameraDistanceMeters, mapHeightDp)
        if (metersPerDp <= 0.0) return 0.0
        val zoomZero = METERS_PER_DP_AT_ZOOM_ZERO * cos(latitude.degreesToRadians())
        if (zoomZero <= 0.0) return 0.0
        return ln(zoomZero / metersPerDp) / ln(2.0)
    }

    fun cameraDistanceForZoom(zoom: Double, latitude: Double, mapHeightDp: Float): Double =
        cameraDistanceMeters(metersPerDpForZoom(zoom, latitude), mapHeightDp)
}

fun CameraState.cameraDistanceMeters(mapHeightDp: Float): Double =
    CameraDistance.cameraDistanceMeters(metersPerDpAtTarget, mapHeightDp)

fun CameraState.cameraDistanceMeters(mapHeightDp: Dp): Double =
    CameraDistance.cameraDistanceMeters(metersPerDpAtTarget, mapHeightDp.value)

fun zoomForCameraDistance(
    cameraDistanceMeters: Double,
    latitude: Double,
    mapHeightDp: Float
): Double = CameraDistance.zoomForCameraDistance(cameraDistanceMeters, latitude, mapHeightDp)

fun zoomForCameraDistance(
    cameraDistanceMeters: Double,
    latitude: Double,
    mapHeightDp: Dp
): Double = CameraDistance.zoomForCameraDistance(cameraDistanceMeters, latitude, mapHeightDp.value)

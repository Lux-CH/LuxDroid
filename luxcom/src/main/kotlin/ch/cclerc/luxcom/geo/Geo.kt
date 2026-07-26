package ch.cclerc.luxcom.geo

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal data class BoundingBox(
    val minLat: Double,
    val minLon: Double,
    val maxLat: Double,
    val maxLon: Double
)

internal fun calculateBoundingBox(centerLat: Double, centerLon: Double, radiusMeters: Double): BoundingBox {
    val earthRadius = 6371000.0

    val angularDistance = radiusMeters / earthRadius

    val latOffset = angularDistance * (180.0 / PI)

    val lonOffset = angularDistance * (180.0 / PI) / cos(centerLat * PI / 180.0)

    return BoundingBox(
        minLat = centerLat - latOffset,
        minLon = centerLon - lonOffset,
        maxLat = centerLat + latOffset,
        maxLon = centerLon + lonOffset
    )
}

fun calculateDistance(userLat: Double, userLon: Double, stopLat: Double, stopLon: Double): Double {
    val earthRadius = 6371000.0
    val dLat = (stopLat - userLat) * PI / 180.0
    val dLon = (stopLon - userLon) * PI / 180.0
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(userLat * PI / 180.0) * cos(stopLat * PI / 180.0) * sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return earthRadius * c
}

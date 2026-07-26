package ch.cclerc.luxapp.domain.map

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

const val EARTH_RADIUS_METERS = 6_371_000.0

data class LatLng(val latitude: Double, val longitude: Double)

fun Double.degreesToRadians(): Double = this * PI / 180.0

fun Double.radiansToDegrees(): Double = this * 180.0 / PI

fun LatLng.distanceTo(other: LatLng): Double {
    val lat1 = latitude.degreesToRadians()
    val lat2 = other.latitude.degreesToRadians()
    val x = (other.longitude - longitude).degreesToRadians() * cos((lat1 + lat2) / 2)
    val y = lat2 - lat1
    return sqrt(x * x + y * y) * EARTH_RADIUS_METERS
}

fun LatLng.squaredDistanceTo(other: LatLng): Double {
    val dLat = other.latitude - latitude
    val dLon = (other.longitude - longitude) * cos(latitude.degreesToRadians())
    return dLat * dLat + dLon * dLon
}

fun LatLng.greatCircleBearing(other: LatLng): Double {
    val deltaLon = other.longitude.degreesToRadians() - longitude.degreesToRadians()
    val lat1 = latitude.degreesToRadians()
    val lat2 = other.latitude.degreesToRadians()

    val y = sin(deltaLon) * cos(lat2)
    val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(deltaLon)
    val bearing = atan2(y, x).radiansToDegrees()

    return (bearing + 360.0) % 360.0
}

fun interpolateCoordinate(from: LatLng, to: LatLng, progress: Double): LatLng = LatLng(
    latitude = from.latitude + (to.latitude - from.latitude) * progress,
    longitude = from.longitude + (to.longitude - from.longitude) * progress
)

data class WalkingPathMetrics(
    val coordinates: List<LatLng>,
    val cumulativeDistances: List<Double>,
    val totalDistance: Double
)

fun buildWalkingPathMetrics(coordinates: List<LatLng>): WalkingPathMetrics {
    if (coordinates.isEmpty()) {
        return WalkingPathMetrics(emptyList(), emptyList(), 0.0)
    }

    if (coordinates.size == 1) {
        return WalkingPathMetrics(coordinates, listOf(0.0), 0.0)
    }

    val cumulativeDistances = ArrayList<Double>(coordinates.size)
    cumulativeDistances.add(0.0)

    var totalDistance = 0.0
    for (index in 1 until coordinates.size) {
        totalDistance += coordinates[index - 1].distanceTo(coordinates[index])
        cumulativeDistances.add(totalDistance)
    }

    return WalkingPathMetrics(coordinates, cumulativeDistances, totalDistance)
}

fun interpolateOnPath(pathMetrics: WalkingPathMetrics, progress: Double): LatLng? {
    val coordinates = pathMetrics.coordinates
    val first = coordinates.firstOrNull() ?: return null
    val last = coordinates.lastOrNull() ?: return first

    if (coordinates.size == 1 || progress <= 0.0) return first
    if (progress >= 1.0) return last
    if (pathMetrics.totalDistance <= 0.0) return first

    val targetDistance = pathMetrics.totalDistance * progress
    val cumulativeDistances = pathMetrics.cumulativeDistances

    for (index in 1 until cumulativeDistances.size) {
        val traversedDistance = cumulativeDistances[index - 1]
        val nextTraversedDistance = cumulativeDistances[index]
        val segmentDistance = nextTraversedDistance - traversedDistance

        if (targetDistance <= nextTraversedDistance) {
            val segmentProgress = if (segmentDistance > 0.0) {
                (targetDistance - traversedDistance) / segmentDistance
            } else {
                0.0
            }

            return interpolateCoordinate(coordinates[index - 1], coordinates[index], segmentProgress)
        }
    }

    return last
}

package ch.cclerc.luxapp.domain.onboard

import ch.cclerc.luxapp.domain.map.LatLng
import ch.cclerc.luxapp.domain.map.PolylineCodec
import ch.cclerc.luxapp.domain.map.distanceTo
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class RoutePath(coordinates: List<LatLng>) {

    data class Projection(
        val along: Double,
        val offset: Double,
        val coordinate: LatLng,
        val segment: Int
    )

    val coordinates: List<LatLng>
    val cumulative: List<Double>

    init {
        val cleaned = ArrayList<LatLng>(coordinates.size)
        for (coordinate in coordinates) {
            if (!coordinate.latitude.isFinite() || !coordinate.longitude.isFinite()) continue
            if (coordinate.latitude !in -90.0..90.0 || coordinate.longitude !in -180.0..180.0) continue
            val last = cleaned.lastOrNull()
            if (last != null && last.latitude == coordinate.latitude && last.longitude == coordinate.longitude) continue
            cleaned.add(coordinate)
        }
        this.coordinates = cleaned

        val distances = ArrayList<Double>(cleaned.size)
        if (cleaned.isNotEmpty()) distances.add(0.0)
        for (index in 1 until cleaned.size) {
            distances.add(distances[index - 1] + cleaned[index - 1].distanceTo(cleaned[index]))
        }
        this.cumulative = distances
    }

    val length: Double get() = cumulative.lastOrNull() ?: 0.0
    val isEmpty: Boolean get() = coordinates.size < 2

    fun project(point: LatLng, hint: Double? = null): Projection? {
        if (coordinates.size < 2) {
            val only = coordinates.firstOrNull() ?: return null
            return Projection(0.0, only.distanceTo(point), only, 0)
        }

        val metersPerDegreeLat = 111_320.0
        val metersPerDegreeLon = 111_320.0 * cos(point.latitude * PI / 180)

        var bestScore = Double.POSITIVE_INFINITY
        var best: Projection? = null
        for (index in 0 until coordinates.size - 1) {
            val a = coordinates[index]
            val b = coordinates[index + 1]
            val ax = (a.longitude - point.longitude) * metersPerDegreeLon
            val ay = (a.latitude - point.latitude) * metersPerDegreeLat
            val bx = (b.longitude - point.longitude) * metersPerDegreeLon
            val by = (b.latitude - point.latitude) * metersPerDegreeLat
            val dx = bx - ax
            val dy = by - ay
            val lengthSquared = dx * dx + dy * dy
            val t = if (lengthSquared > 0) max(0.0, min(1.0, -(ax * dx + ay * dy) / lengthSquared)) else 0.0
            val px = ax + t * dx
            val py = ay + t * dy
            val offset = sqrt(px * px + py * py)
            val along = cumulative[index] + t * (cumulative[index + 1] - cumulative[index])

            val score = offset + (hint?.let { abs(along - it) * 0.15 } ?: 0.0)
            if (best == null || score < bestScore) {
                bestScore = score
                best = Projection(
                    along = along,
                    offset = offset,
                    coordinate = LatLng(
                        a.latitude + (b.latitude - a.latitude) * t,
                        a.longitude + (b.longitude - a.longitude) * t
                    ),
                    segment = index
                )
            }
        }
        return best
    }

    fun projectSequence(points: List<LatLng>): List<Double> {
        val result = ArrayList<Double>(points.size)
        var floor = 0.0
        for (point in points) {
            val along = max(floor, project(point, floor)?.along ?: floor)
            result.add(along)
            floor = along
        }
        return result
    }

    fun coordinate(along: Double): LatLng? {
        val first = coordinates.firstOrNull() ?: return null
        if (coordinates.size < 2 || along <= 0) return first
        if (along >= length) return coordinates.last()

        var low = 0
        var high = cumulative.size - 1
        while (high - low > 1) {
            val mid = (low + high) / 2
            if (cumulative[mid] <= along) low = mid else high = mid
        }
        val span = cumulative[high] - cumulative[low]
        val t = if (span > 0) (along - cumulative[low]) / span else 0.0
        val a = coordinates[low]
        val b = coordinates[high]
        return LatLng(a.latitude + (b.latitude - a.latitude) * t, a.longitude + (b.longitude - a.longitude) * t)
    }

    fun bearing(along: Double, lookAhead: Double = 25.0): Double? {
        val from = coordinate(max(0.0, along - 5)) ?: return null
        val to = coordinate(min(length, along + lookAhead)) ?: return null
        if (from.distanceTo(to) <= 1) return null
        return from.bearingTo(to)
    }

    fun slice(start: Double, end: Double): List<LatLng> {
        if (coordinates.size < 2) return coordinates
        val lower = max(0.0, min(start, end))
        val upper = min(length, max(start, end))
        if (upper <= lower) return emptyList()
        val first = coordinate(lower) ?: return emptyList()
        val last = coordinate(upper) ?: return emptyList()

        val result = mutableListOf(first)
        for (index in coordinates.indices) {
            if (cumulative[index] > lower && cumulative[index] < upper) result.add(coordinates[index])
        }
        result.add(last)
        return result
    }

    fun sliced(start: Double, end: Double): RoutePath = RoutePath(slice(start, end))

    companion object {
        fun encoded(encoded: String, precision: Double = 1e6, fallback: List<LatLng> = emptyList()): RoutePath {
            val decoded = runCatching { PolylineCodec.decode(encoded, precision) }.getOrDefault(emptyList())
            return RoutePath(if (decoded.size >= 2) decoded else fallback)
        }
    }
}

fun LatLng.bearingTo(other: LatLng): Double {
    val lat1 = latitude * PI / 180
    val lat2 = other.latitude * PI / 180
    val deltaLon = (other.longitude - longitude) * PI / 180
    val y = sin(deltaLon) * cos(lat2)
    val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(deltaLon)
    val degrees = atan2(y, x) * 180 / PI
    return if (degrees < 0) degrees + 360 else degrees
}

fun LatLng.offset(distance: Double, bearing: Double): LatLng {
    val radians = bearing * PI / 180
    val dLat = distance * cos(radians) / 111_320
    val dLon = distance * sin(radians) / (111_320 * cos(latitude * PI / 180))
    return LatLng(latitude + dLat, longitude + dLon)
}

object Angle360 {
    fun delta(from: Double, to: Double): Double {
        var difference = (to - from) % 360
        if (difference > 180) difference -= 360
        if (difference < -180) difference += 360
        return difference
    }

    fun normalized(angle: Double): Double {
        val value = angle % 360
        return if (value < 0) value + 360 else value
    }
}

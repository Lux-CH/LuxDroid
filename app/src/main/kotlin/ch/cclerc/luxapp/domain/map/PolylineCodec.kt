package ch.cclerc.luxapp.domain.map

import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

object PolylineCodec {

    fun decode(encoded: String, divisor: Double = 1e6): List<LatLng> {
        val coordinates = ArrayList<LatLng>()
        var index = 0
        var lat = 0
        var lon = 0

        while (index < encoded.length) {
            val latDelta = decodeValue(encoded, index) ?: return coordinates
            index = latDelta.second
            lat += latDelta.first

            val lonDelta = decodeValue(encoded, index) ?: return coordinates
            index = lonDelta.second
            lon += lonDelta.first

            coordinates.add(LatLng(lat / divisor, lon / divisor))
        }

        return coordinates
    }

    private fun decodeValue(encoded: String, startIndex: Int): Pair<Int, Int>? {
        var index = startIndex
        var shift = 0
        var result = 0
        var byte: Int

        do {
            if (index >= encoded.length) return null
            byte = encoded[index++].code - 63
            if (byte < 0) return null
            result = result or ((byte and 0x1f) shl shift)
            shift += 5
        } while (byte >= 0x20)

        val value = if (result and 1 != 0) (result shr 1).inv() else result shr 1
        return value to index
    }
}

fun reduceCoordinatesIfNeeded(coordinates: List<LatLng>, maxPoints: Int): List<LatLng> {
    if (coordinates.size <= maxPoints || maxPoints <= 2) return coordinates

    val lastIndex = coordinates.size - 1
    val step = lastIndex.toDouble() / (maxPoints - 1).toDouble()
    val reduced = ArrayList<LatLng>(maxPoints)
    reduced.add(coordinates[0])

    for (reducedIndex in 1 until maxPoints - 1) {
        val sourceIndex = round(reducedIndex.toDouble() * step).toInt()
        val clampedSourceIndex = min(max(sourceIndex, 1), lastIndex - 1)
        reduced.add(coordinates[clampedSourceIndex])
    }

    reduced.add(coordinates[lastIndex])
    return reduced
}

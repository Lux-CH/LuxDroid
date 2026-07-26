package ch.cclerc.luxapp.domain.map

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PolylineCodecTest {

    @Test
    fun decodesKnownGoogleFixture() {
        val decoded = PolylineCodec.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@", 1e5)

        assertEquals(3, decoded.size)
        assertClose(38.5, decoded[0].latitude)
        assertClose(-120.2, decoded[0].longitude)
        assertClose(40.7, decoded[1].latitude)
        assertClose(-120.95, decoded[1].longitude)
        assertClose(43.252, decoded[2].latitude)
        assertClose(-126.453, decoded[2].longitude)
    }

    @Test
    fun divisorScalesCoordinates() {
        val fine = PolylineCodec.decode("_p~iF~ps|U", 1e6)
        assertClose(3.85, fine[0].latitude)
        assertClose(-12.02, fine[0].longitude)
    }

    @Test
    fun roundTripsThroughTestEncoder() {
        val source = listOf(
            LatLng(46.516, 6.632),
            LatLng(46.520, 6.640),
            LatLng(46.527, 6.651)
        )
        val decoded = PolylineCodec.decode(encodePolyline(source, 1e6), 1e6)

        assertEquals(source.size, decoded.size)
        source.forEachIndexed { index, expected ->
            assertClose(expected.latitude, decoded[index].latitude, 1e-6)
            assertClose(expected.longitude, decoded[index].longitude, 1e-6)
        }
    }

    @Test
    fun decodesEmptyStringToEmptyList() {
        assertTrue(PolylineCodec.decode("", 1e6).isEmpty())
    }

    @Test
    fun reduceKeepsInputWhenUnderBudget() {
        val coordinates = straightLine(10)
        assertEquals(coordinates, reduceCoordinatesIfNeeded(coordinates, 10))
        assertEquals(coordinates, reduceCoordinatesIfNeeded(coordinates, 50))
    }

    @Test
    fun reduceIgnoresDegenerateBudgets() {
        val coordinates = straightLine(10)
        assertEquals(coordinates, reduceCoordinatesIfNeeded(coordinates, 2))
        assertEquals(coordinates, reduceCoordinatesIfNeeded(coordinates, 0))
    }

    @Test
    fun reduceProducesExactCountAndKeepsEndpoints() {
        val coordinates = straightLine(1000)
        val reduced = reduceCoordinatesIfNeeded(coordinates, 220)

        assertEquals(220, reduced.size)
        assertEquals(coordinates.first(), reduced.first())
        assertEquals(coordinates.last(), reduced.last())
    }

    @Test
    fun reduceClampsInteriorSamplesInsideEndpoints() {
        val coordinates = straightLine(1000)
        val reduced = reduceCoordinatesIfNeeded(coordinates, 5)

        val interior = reduced.subList(1, reduced.size - 1)
        interior.forEach { point ->
            val index = coordinates.indexOf(point)
            assertTrue(index in 1..(coordinates.size - 2), "interior sample escaped clamp: $index")
        }

        val latitudes = reduced.map { it.latitude }
        assertEquals(latitudes.sorted(), latitudes)
    }

    private fun straightLine(count: Int): List<LatLng> =
        (0 until count).map { LatLng(46.5 + it * 0.001, 6.6) }

    private fun assertClose(expected: Double, actual: Double, tolerance: Double = 1e-9) {
        assertTrue(abs(expected - actual) <= tolerance, "expected $expected but was $actual")
    }
}

fun encodePolyline(coordinates: List<LatLng>, divisor: Double): String {
    val builder = StringBuilder()
    var previousLat = 0L
    var previousLon = 0L

    coordinates.forEach { coordinate ->
        val lat = Math.round(coordinate.latitude * divisor)
        val lon = Math.round(coordinate.longitude * divisor)
        encodeValue((lat - previousLat).toInt(), builder)
        encodeValue((lon - previousLon).toInt(), builder)
        previousLat = lat
        previousLon = lon
    }

    return builder.toString()
}

private fun encodeValue(value: Int, builder: StringBuilder) {
    var encoded = if (value < 0) (value shl 1).inv() else value shl 1
    while (encoded >= 0x20) {
        builder.append(((0x20 or (encoded and 0x1f)) + 63).toChar())
        encoded = encoded shr 5
    }
    builder.append((encoded + 63).toChar())
}

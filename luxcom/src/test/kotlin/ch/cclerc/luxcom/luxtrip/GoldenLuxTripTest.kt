package ch.cclerc.luxcom.luxtrip

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GoldenLuxTripTest {

    private fun goldenBytes(): ByteArray {
        val stream = checkNotNull(javaClass.classLoader.getResourceAsStream("fixtures/golden.luxtrip"))
        return stream.use { it.readBytes() }
    }

    @Test
    fun decodesIosProducedFile() {
        val itinerary = LuxTripCodec.decode(goldenBytes())
        assertTrue(itinerary.legs.isNotEmpty())
        assertTrue(itinerary.duration > 0)
        assertTrue(!itinerary.endTime.isBefore(itinerary.startTime))
        assertNotNull(itinerary.legs.first().from.name)
        assertNotNull(itinerary.legs.last().to.name)
    }

    @Test
    fun reencodesByteIdentical() {
        val bytes = goldenBytes()
        val itinerary = LuxTripCodec.decode(bytes)
        assertContentEquals(bytes, LuxTripCodec.encode(itinerary))
    }
}

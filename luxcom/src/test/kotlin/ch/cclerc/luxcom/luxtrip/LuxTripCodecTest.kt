package ch.cclerc.luxcom.luxtrip

import ch.cclerc.luxcom.model.Place
import ch.cclerc.luxcom.model.TransportationMode
import ch.cclerc.luxcom.model.trip.Direction
import ch.cclerc.luxcom.model.trip.Itinerary
import ch.cclerc.luxcom.model.trip.Leg
import ch.cclerc.luxcom.model.trip.LegGeometry
import ch.cclerc.luxcom.model.trip.StepInstruction
import ch.cclerc.luxcom.model.trip.StepPolyline
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LuxTripCodecTest {

    private val start: Instant = Instant.ofEpochSecond(1_000_000_000L)
    private val end: Instant = Instant.ofEpochSecond(1_000_003_600L)

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    private fun str(value: String) = bytes(0xa0 or value.length) + value.toByteArray(Charsets.US_ASCII)

    private val tsStart = bytes(0xd6, 0xff, 0x3b, 0x9a, 0xca, 0x00)
    private val tsEnd = bytes(0xd6, 0xff, 0x3b, 0x9a, 0xd8, 0x10)

    private fun emptyLegsItinerary(duration: Int) = Itinerary(
        duration = duration,
        startTime = start,
        endTime = end,
        transfers = 0,
        legs = emptyList()
    )

    private fun emptyLegsVector(durationBytes: ByteArray) =
        bytes(0x85) +
            str("duration") + durationBytes +
            str("startTime") + tsStart +
            str("endTime") + tsEnd +
            str("transfers") + bytes(0x00) +
            str("legs") + bytes(0x90)

    private val minimalPlace = Place(
        name = "A",
        lat = 1.0,
        lon = 2.0,
        vertexType = Place.VertexType.NORMAL
    )

    private fun minimalLeg(from: Place = minimalPlace, to: Place = minimalPlace) = Leg(
        mode = TransportationMode.WALK,
        from = from,
        to = to,
        duration = 0,
        startTime = start,
        endTime = end,
        scheduledStartTime = start,
        scheduledEndTime = end,
        realTime = false,
        legGeometry = LegGeometry("", 0)
    )

    private fun singleLegItinerary(leg: Leg) = Itinerary(
        duration = 0,
        startTime = start,
        endTime = end,
        transfers = 0,
        legs = listOf(leg)
    )

    private val singleLegPreamble =
        bytes(0x85) +
            str("duration") + bytes(0x00) +
            str("startTime") + tsStart +
            str("endTime") + tsEnd +
            str("transfers") + bytes(0x00) +
            str("legs") + bytes(0x91)

    @Test
    fun encodesMinimalItineraryToExactBytes() {
        val encoded = LuxTripCodec.encode(emptyLegsItinerary(127))
        assertContentEquals(emptyLegsVector(bytes(0x7f)), encoded)
    }

    @Test
    fun timestampsUseTimestamp32Ext() {
        val encoded = LuxTripCodec.encode(emptyLegsItinerary(127))
        val startOffset = 1 + str("duration").size + 1 + str("startTime").size
        assertContentEquals(tsStart, encoded.copyOfRange(startOffset, startOffset + 6))
        val decoded = LuxTripCodec.decode(encoded)
        assertEquals(start, decoded.startTime)
        assertEquals(end, decoded.endTime)
    }

    @Test
    fun fixintBoundaryAt127And128() {
        val at127 = LuxTripCodec.encode(emptyLegsItinerary(127))
        val at128 = LuxTripCodec.encode(emptyLegsItinerary(128))
        assertContentEquals(emptyLegsVector(bytes(0x7f)), at127)
        assertContentEquals(emptyLegsVector(bytes(0xcc, 0x80)), at128)
        assertEquals(at127.size + 1, at128.size)
    }

    @Test
    fun omittedNilsShrinkLegMapHeader() {
        val minimal = LuxTripCodec.encode(singleLegItinerary(minimalLeg()))
        val withHeadsign = LuxTripCodec.encode(singleLegItinerary(minimalLeg().copy(headsign = "X")))
        assertContentEquals(singleLegPreamble, minimal.copyOfRange(0, singleLegPreamble.size))
        assertContentEquals(singleLegPreamble, withHeadsign.copyOfRange(0, singleLegPreamble.size))
        assertEquals(0x8a, minimal[singleLegPreamble.size].toInt() and 0xff)
        assertEquals(0x8b, withHeadsign[singleLegPreamble.size].toInt() and 0xff)
    }

    @Test
    fun omittedNilsShrinkPlaceMapHeader() {
        val fromPrefix = singleLegPreamble + bytes(0x8a) + str("mode") + str("WALK") + str("from")
        val minimal = LuxTripCodec.encode(singleLegItinerary(minimalLeg()))
        val withStopId = LuxTripCodec.encode(
            singleLegItinerary(minimalLeg(from = minimalPlace.copy(stopId = "s")))
        )
        assertContentEquals(fromPrefix, minimal.copyOfRange(0, fromPrefix.size))
        assertContentEquals(fromPrefix, withStopId.copyOfRange(0, fromPrefix.size))
        assertEquals(0x86, minimal[fromPrefix.size].toInt() and 0xff)
        assertEquals(0x87, withStopId[fromPrefix.size].toInt() and 0xff)
    }

    @Test
    fun roundTripsAcrossOptionalPermutations() {
        val step = StepInstruction(
            relativeDirection = Direction.left,
            distance = 12.5,
            fromLevel = 0.0,
            toLevel = 1.0,
            streetName = "Rue",
            exit = "",
            stayOn = false,
            area = true,
            polyline = StepPolyline("abc", 5, 3)
        )
        val richPlace = Place(
            name = "Stop",
            stopId = "st1",
            parentId = "p1",
            lat = 46.2,
            lon = 6.15,
            level = -1.0,
            arrival = start,
            departure = end,
            scheduledArrival = start,
            scheduledDeparture = end,
            scheduledTrack = "3",
            track = "4",
            vertexType = Place.VertexType.TRANSIT,
            modes = listOf(TransportationMode.TRAM, TransportationMode.BUS),
            importance = 0.5
        )
        val base = minimalLeg()
        val variants = listOf(
            base,
            base.copy(headsign = "Gare"),
            base.copy(steps = listOf(step, step.copy(osmWay = 123456))),
            base.copy(steps = emptyList()),
            base.copy(alternatives = listOf(listOf(base.copy(headsign = "alt")), emptyList())),
            base.copy(interlineWithPreviousLeg = true),
            base.copy(interlineWithPreviousLeg = false),
            base.copy(
                distance = 100.5,
                routeShortName = "12",
                intermediateStops = listOf(richPlace),
                agencyId = "TPG",
                tripId = "trip9"
            ),
            base.copy(
                mode = TransportationMode.TRAM,
                from = richPlace,
                to = richPlace.copy(importance = null),
                realTime = true,
                distance = 0.0,
                headsign = "Bel-Air",
                routeShortName = "18",
                intermediateStops = emptyList(),
                legGeometry = LegGeometry("poly", 42),
                agencyId = "TPG",
                tripId = "t1",
                steps = listOf(step),
                interlineWithPreviousLeg = true,
                alternatives = listOf(listOf(base))
            )
        )
        for (variant in variants) {
            val itinerary = Itinerary(
                duration = 300,
                startTime = start,
                endTime = end,
                transfers = 1,
                legs = listOf(variant)
            )
            val decoded = LuxTripCodec.decode(LuxTripCodec.encode(itinerary))
            assertEquals(itinerary.duration, decoded.duration)
            assertEquals(itinerary.startTime, decoded.startTime)
            assertEquals(itinerary.endTime, decoded.endTime)
            assertEquals(itinerary.transfers, decoded.transfers)
            assertEquals(itinerary.legs, decoded.legs)
        }
        val multiLeg = Itinerary(
            duration = 600,
            startTime = start,
            endTime = end,
            transfers = variants.size - 1,
            legs = variants
        )
        assertEquals(multiLeg.legs, LuxTripCodec.decode(LuxTripCodec.encode(multiLeg)).legs)
    }

    @Test
    fun decodeSkipsUnknownKeys() {
        val data = bytes(0x87) +
            str("duration") + bytes(0x2a) +
            str("mystery") + bytes(0x81) + str("x") + bytes(0x01) +
            str("startTime") + tsStart +
            str("endTime") + tsEnd +
            str("transfers") + bytes(0x02) +
            str("legs") + bytes(0x90) +
            str("bogus") + str("hi")
        val decoded = LuxTripCodec.decode(data)
        assertEquals(42, decoded.duration)
        assertEquals(start, decoded.startTime)
        assertEquals(end, decoded.endTime)
        assertEquals(2, decoded.transfers)
        assertTrue(decoded.legs.isEmpty())
    }

    @Test
    fun decodeToleratesExplicitNils() {
        val data = bytes(0x85) +
            str("duration") + bytes(0xc0) +
            str("startTime") + tsStart +
            str("endTime") + bytes(0xc0) +
            str("transfers") + bytes(0x01) +
            str("legs") + bytes(0xc0)
        val decoded = LuxTripCodec.decode(data)
        assertEquals(0, decoded.duration)
        assertEquals(start, decoded.startTime)
        assertEquals(Instant.EPOCH, decoded.endTime)
        assertEquals(1, decoded.transfers)
        assertTrue(decoded.legs.isEmpty())
    }

    @Test
    fun decodeToleratesNilLegFields() {
        val data = bytes(0x85) +
            str("duration") + bytes(0x00) +
            str("startTime") + tsStart +
            str("endTime") + tsEnd +
            str("transfers") + bytes(0x00) +
            str("legs") + bytes(0x91) + bytes(0x81) + str("headsign") + bytes(0xc0)
        val decoded = LuxTripCodec.decode(data)
        assertEquals(1, decoded.legs.size)
        val leg = decoded.legs[0]
        assertNull(leg.headsign)
        assertEquals(TransportationMode.OTHER, leg.mode)
        assertEquals("", leg.from.name)
        assertEquals(LegGeometry("", 0), leg.legGeometry)
    }

    @Test
    fun decodeIsKeyOrderIndependent() {
        val shuffled = bytes(0x85) +
            str("legs") + bytes(0x90) +
            str("transfers") + bytes(0x00) +
            str("endTime") + tsEnd +
            str("startTime") + tsStart +
            str("duration") + bytes(0x7f)
        val decoded = LuxTripCodec.decode(shuffled)
        assertEquals(127, decoded.duration)
        assertEquals(start, decoded.startTime)
        assertEquals(end, decoded.endTime)
        assertEquals(0, decoded.transfers)
        assertTrue(decoded.legs.isEmpty())
        assertContentEquals(emptyLegsVector(bytes(0x7f)), LuxTripCodec.encode(decoded))
    }
}

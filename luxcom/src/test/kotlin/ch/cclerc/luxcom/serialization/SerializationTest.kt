package ch.cclerc.luxcom.serialization

import ch.cclerc.luxcom.model.LocationType
import ch.cclerc.luxcom.model.Place
import ch.cclerc.luxcom.model.SearchResult
import ch.cclerc.luxcom.model.TransportationMode
import ch.cclerc.luxcom.model.stop.StopTime
import java.time.Instant
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SerializationTest {

    @Test
    fun unknownTransportationModeDecodesToOther() {
        assertEquals(TransportationMode.OTHER, LuxJson.decodeFromString<TransportationMode>("\"MAGLEV\""))
        assertEquals(TransportationMode.OTHER, LuxJson.decodeFromString<TransportationMode>("\"\""))
    }

    @Test
    fun transportationModeRoundTripsExactRawValue() {
        for (mode in TransportationMode.entries) {
            val encoded = LuxJson.encodeToString(mode)
            assertEquals("\"${mode.rawValue}\"", encoded)
            assertEquals(mode, LuxJson.decodeFromString<TransportationMode>(encoded))
        }
        assertEquals(
            TransportationMode.REGIONAL_FAST_RAIL,
            LuxJson.decodeFromString<TransportationMode>("\"REGIONAL_FAST_RAIL\"")
        )
    }

    @Test
    fun instantEncodesWholeSecondIsoFormat() {
        val encoded = LuxJson.encodeToString(InstantIso8601Serializer, Instant.parse("2026-07-26T09:30:00Z"))
        assertEquals("\"2026-07-26T09:30:00Z\"", encoded)
    }

    @Test
    fun instantDecodeAcceptsFractionalSecondsAndReencodesTruncated() {
        val decoded = LuxJson.decodeFromString(InstantIso8601Serializer, "\"2026-07-26T09:30:00.123456Z\"")
        assertEquals(1785058200L, decoded.epochSecond)
        assertEquals(123456000, decoded.nano)
        assertEquals(
            "\"2026-07-26T09:30:00Z\"",
            LuxJson.encodeToString(InstantIso8601Serializer, decoded)
        )
    }

    @Test
    fun placeAppliesDefaultsWhenFieldsAbsent() {
        val json = """{"name":"Genève, Bel-Air","lat":46.20439,"lon":6.141,"vertexType":"NORMAL"}"""
        val decodedPlace = LuxJson.decodeFromString<Place>(json)
        assertEquals("Genève, Bel-Air", decodedPlace.name)
        assertEquals(46.20439, decodedPlace.lat, 0.0)
        assertEquals(6.141, decodedPlace.lon, 0.0)
        assertEquals(0.0, decodedPlace.level, 0.0)
        assertTrue(decodedPlace.modes.isEmpty())
        assertNull(decodedPlace.stopId)
        assertNull(decodedPlace.parentId)
        assertNull(decodedPlace.arrival)
        assertNull(decodedPlace.departure)
        assertNull(decodedPlace.track)
        assertNull(decodedPlace.importance)
        assertEquals(Place.VertexType.NORMAL, decodedPlace.vertexType)
    }

    @Test
    fun searchResultRoundTripsWithDefaultAreaKey() {
        val json = """
            {
              "type": "STOP",
              "tokens": [[0, 6]],
              "name": "Genève, gare Cornavin",
              "id": "ch:8587057",
              "lat": 46.21022,
              "lon": 6.14229,
              "score": 0.93,
              "modes": ["RAIL", "BUS"],
              "areas": [
                {"name": "Genève", "adminLevel": 8, "matched": false, "default": true},
                {"name": "Suisse", "adminLevel": 2, "matched": false}
              ]
            }
        """.trimIndent()
        val decoded = LuxJson.decodeFromString<SearchResult>(json)
        assertEquals(LocationType.STOP, decoded.type)
        assertEquals(listOf(listOf(0, 6)), decoded.tokens)
        assertEquals("ch:8587057", decoded.id)
        assertEquals(0.93, decoded.score, 0.0)
        assertEquals(listOf(TransportationMode.RAIL, TransportationMode.BUS), decoded.modes)
        assertTrue(decoded.servesRail)
        assertEquals(2, decoded.areas.size)
        assertEquals(true, decoded.areas[0].default)
        assertNull(decoded.areas[1].default)
        assertNull(decoded.level)
        assertNull(decoded.hasRailNeighbour)
        assertTrue(decoded.groupedStopIds.isEmpty())

        val encoded = LuxJson.encodeToString(decoded)
        assertTrue(encoded.contains("\"default\":true"))
        assertFalse(encoded.contains("hasRailNeighbour"))
        assertEquals(decoded, LuxJson.decodeFromString<SearchResult>(encoded))
    }

    @Test
    fun stopTimeDecodesRealisticMotisPayload() {
        val json = """
            {
              "place": {
                "name": "Genève, gare Cornavin",
                "stopId": "ch:8587057:1",
                "parentId": "ch:8587057",
                "lat": 46.21022,
                "lon": 6.14229,
                "level": 0.0,
                "departure": "2026-07-26T09:31:00Z",
                "scheduledDeparture": "2026-07-26T09:30:00Z",
                "track": "C",
                "scheduledTrack": "C",
                "vertexType": "TRANSIT",
                "pickupType": "NORMAL"
              },
              "mode": "BUS",
              "realTime": true,
              "headsign": "Ferney-Voltaire, Mairie",
              "routeShortName": "F",
              "tripId": "trip_20260726_f_0930",
              "agencyId": "tpg",
              "cancelled": false,
              "source": "gtfs-rt"
            }
        """.trimIndent()
        val decoded = LuxJson.decodeFromString<StopTime>(json)
        assertEquals("Genève, gare Cornavin", decoded.place.name)
        assertEquals("ch:8587057:1", decoded.place.stopId)
        assertEquals("ch:8587057", decoded.place.parentId)
        assertEquals(Instant.parse("2026-07-26T09:31:00Z"), decoded.place.departure)
        assertEquals(Instant.parse("2026-07-26T09:30:00Z"), decoded.place.scheduledDeparture)
        assertNull(decoded.place.arrival)
        assertEquals("C", decoded.place.track)
        assertEquals(Place.VertexType.TRANSIT, decoded.place.vertexType)
        assertEquals(TransportationMode.BUS, decoded.mode)
        assertTrue(decoded.realTime)
        assertEquals("Ferney-Voltaire, Mairie", decoded.headsign)
        assertEquals("F", decoded.routeShortName)
        assertEquals("trip_20260726_f_0930", decoded.tripId)
        assertEquals("trip_20260726_f_0930", decoded.id)
        assertEquals("tpg", decoded.agencyId)
        assertFalse(decoded.cancelled)
        assertFalse(decoded.mode.isRail)
    }
}

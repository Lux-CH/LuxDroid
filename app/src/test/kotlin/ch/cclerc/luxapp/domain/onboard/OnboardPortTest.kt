package ch.cclerc.luxapp.domain.onboard

import ch.cclerc.luxapp.domain.DisruptionManager
import ch.cclerc.luxapp.domain.map.LatLng
import ch.cclerc.luxapp.domain.search.SearchResultScorer
import ch.cclerc.luxcom.model.Disruption
import ch.cclerc.luxcom.model.LocationType
import ch.cclerc.luxcom.model.Place
import ch.cclerc.luxcom.model.SearchResult
import ch.cclerc.luxcom.model.TransportationMode
import ch.cclerc.luxcom.model.trip.Leg
import ch.cclerc.luxcom.model.trip.LegGeometry
import ch.cclerc.luxcom.station.StationLayout
import ch.cclerc.luxcom.station.TrainFormation
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OnboardPortTest {

    private val start = Instant.ofEpochSecond(1_700_000_000)

    private fun place(name: String, stopId: String? = null) = Place(
        name = name,
        stopId = stopId,
        lat = 46.2,
        lon = 6.14,
        vertexType = Place.VertexType.TRANSIT
    )

    private fun leg(
        line: String? = "12",
        agency: String? = "881",
        from: Place = place("A", "ch_ch:1:sloid:1001"),
        to: Place = place("B", "ch_ch:1:sloid:1002"),
        tripId: String? = null
    ) = Leg(
        mode = TransportationMode.TRAM,
        from = from,
        to = to,
        duration = 600,
        startTime = start,
        endTime = start.plusSeconds(600),
        scheduledStartTime = start,
        scheduledEndTime = start.plusSeconds(600),
        realTime = true,
        routeShortName = line,
        legGeometry = LegGeometry(points = "", length = 0),
        agencyId = agency,
        tripId = tripId
    )

    private fun disruption(
        line: String = "",
        agency: String? = null,
        stopIds: List<String>? = null,
        tripIds: List<String>? = null,
        periods: List<Disruption.Period>? = null
    ) = Disruption(
        line = line,
        lineBackground = "",
        lineDisruption = "Travaux - $line",
        agencyId = agency,
        stopIds = stopIds,
        tripIds = tripIds,
        periods = periods
    )

    @Test
    fun matchesGenevaLineUnderEitherAgencyName() {
        val found = DisruptionManager.matching(listOf(disruption(line = "12")), leg(agency = "Transports Publics Genevois"))
        assertEquals(1, found.size)
    }

    @Test
    fun ignoresOtherAgenciesAndInactivePeriods() {
        val otherAgency = disruption(line = "12", agency = "11")
        val later = disruption(
            line = "12",
            periods = listOf(Disruption.Period(start.plusSeconds(3600), start.plusSeconds(7200)))
        )
        assertTrue(DisruptionManager.matching(listOf(otherAgency, later), leg()).isEmpty())
    }

    @Test
    fun singleStopDisruptionOnlyMatchesEndpoints() {
        val atEndpoint = disruption(agency = "11", stopIds = listOf("ch:1:sloid:1002"))
        val elsewhere = disruption(agency = "11", stopIds = listOf("ch:1:sloid:9999"))
        val found = DisruptionManager.matching(listOf(atEndpoint, elsewhere), leg(line = null, agency = "11"))
        assertEquals(listOf(atEndpoint), found)
    }

    @Test
    fun matchesSbbTripKey() {
        val sbb = disruption(agency = "11", tripIds = listOf("20260924_4242"))
        val found = DisruptionManager.matching(listOf(sbb), leg(line = null, agency = "11", tripId = "20260924_10:00_ch:1:x_4242"))
        assertEquals(1, found.size)
    }

    @Test
    fun scorerToleratesTypos() {
        fun stop(name: String, id: String) = SearchResult(
            type = LocationType.STOP, tokens = emptyList(), name = name, id = id,
            lat = 46.21, lon = 6.14, score = 0.0
        )
        val ranked = SearchResultScorer().ranked(
            listOf(listOf(stop("Plainpalais", "p"), stop("Genève, Cornavin", "c"))),
            "cornavn",
            46.2,
            6.14
        )
        assertEquals("c", ranked.first().id)
    }

    @Test
    fun stationHelpersNormaliseIds() {
        assertEquals(8501008, StationLayout.uic("ch_Parentch:1:sloid:1008"))
        assertEquals(8501008, StationLayout.uic("ch_ch:1:sloid:1008:4:7"))
        assertEquals("10", StationLayout.normalizedTrack("010"))
        assertEquals("0", StationLayout.normalizedTrack("0"))
        assertEquals("A–C, E", TrainFormation.sectorText(listOf("C", "A", "B", "E")))
    }

    @Test
    fun routePathProjectsAndSlices() {
        val path = RoutePath((0..10).map { LatLng(46.5 + it * 0.001, 6.6) })
        val projection = assertNotNull(path.project(LatLng(46.505, 6.6005)))
        assertEquals(path.length / 2, projection.along, path.length * 0.02)
        val slice = path.slice(path.length * 0.25, path.length * 0.75)
        assertTrue(slice.size >= 2)
        assertEquals(46.5025, slice.first().latitude, 1e-4)
    }
}

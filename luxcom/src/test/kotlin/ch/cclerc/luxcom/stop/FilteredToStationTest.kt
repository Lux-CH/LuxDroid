package ch.cclerc.luxcom.stop

import ch.cclerc.luxcom.model.Place
import ch.cclerc.luxcom.model.TransportationMode
import ch.cclerc.luxcom.model.stop.StopTime
import ch.cclerc.luxcom.model.stop.StopTimes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class FilteredToStationTest {

    private fun place(
        name: String,
        stopId: String? = null,
        parentId: String? = null,
        lat: Double = 46.21022,
        lon: Double = 6.14229
    ) = Place(
        name = name,
        stopId = stopId,
        parentId = parentId,
        lat = lat,
        lon = lon,
        vertexType = Place.VertexType.TRANSIT
    )

    private fun stopTime(place: Place, mode: TransportationMode, tripId: String) = StopTime(
        place = place,
        mode = mode,
        realTime = false,
        headsign = null,
        routeShortName = "1",
        tripId = tripId,
        agencyId = "tpg",
        cancelled = false
    )

    private fun times(vararg stopTimes: StopTime) =
        StopTimes(stopTimes.toList(), previousPageCursor = "prev", nextPageCursor = "next")

    private fun tripIds(result: StopTimes) = result.stopTimes.map { it.tripId }

    @Test
    fun allHomeReturnsSameInstance() {
        val fixture = times(
            stopTime(place("Genève, Bel-Air", stopId = "H"), TransportationMode.BUS, "t1"),
            stopTime(place("Genève, Bel-Air", stopId = "H:1", parentId = "H"), TransportationMode.TRAM, "t2")
        )
        assertSame(fixture, fixture.filteredToStation(stopId = "H"))
    }

    @Test
    fun noHomeReturnsSameInstance() {
        val fixture = times(
            stopTime(place("Genève, Stand", stopId = "S1"), TransportationMode.BUS, "t1"),
            stopTime(place("Genève, Coutance", stopId = "S2"), TransportationMode.BUS, "t2")
        )
        assertSame(fixture, fixture.filteredToStation(stopId = "H"))
    }

    @Test
    fun plainBusStopDropsAllExtras() {
        val fixture = times(
            stopTime(place("Genève, Bel-Air", stopId = "H"), TransportationMode.BUS, "home1"),
            stopTime(place("Genève, Stand", stopId = "S1"), TransportationMode.BUS, "busExtra"),
            stopTime(place("Genève, gare Cornavin", stopId = "S2"), TransportationMode.RAIL, "railExtra")
        )
        val result = fixture.filteredToStation(stopId = "H", name = "Genève, Bel-Air")
        assertEquals(listOf("home1"), tripIds(result))
    }

    @Test
    fun forecourtNamedNonRailHomeKeepsOnlyRailExtras() {
        val fixture = times(
            stopTime(place("Nyon, gare", stopId = "H"), TransportationMode.BUS, "home1"),
            stopTime(place("Nyon", stopId = "S1"), TransportationMode.RAIL, "railExtra"),
            stopTime(place("Nyon, Centre-Ville", stopId = "S2"), TransportationMode.BUS, "busExtra")
        )
        val result = fixture.filteredToStation(stopId = "H", name = "Nyon, gare")
        assertEquals(listOf("home1", "railExtra"), tripIds(result))
    }

    @Test
    fun nullNameNonRailHomeKeepsRailExtras() {
        val fixture = times(
            stopTime(place("Nyon, gare", stopId = "H"), TransportationMode.BUS, "home1"),
            stopTime(place("Nyon", stopId = "S1"), TransportationMode.REGIONAL_RAIL, "railExtra"),
            stopTime(place("Nyon, Centre-Ville", stopId = "S2"), TransportationMode.BUS, "busExtra")
        )
        val result = fixture.filteredToStation(stopId = "H")
        assertEquals(listOf("home1", "railExtra"), tripIds(result))
    }

    @Test
    fun railHomeKeepsExtrasFromForecourtNamedParents() {
        val fixture = times(
            stopTime(place("Genève", stopId = "H:1", parentId = "H"), TransportationMode.RAIL, "home1"),
            stopTime(place("Genève, gare Cornavin", stopId = "F1:1", parentId = "F1"), TransportationMode.BUS, "forecourtBus"),
            stopTime(place("Bahnhofquai", stopId = "F2"), TransportationMode.TRAM, "forecourtTram"),
            stopTime(place("Genève, Coutance", stopId = "B1:1", parentId = "B1"), TransportationMode.BUS, "otherBus"),
            stopTime(place("Lausanne", stopId = "R1:1", parentId = "R1"), TransportationMode.RAIL, "railExtra")
        )
        val result = fixture.filteredToStation(stopId = "H")
        assertEquals(listOf("home1", "forecourtBus", "forecourtTram"), tripIds(result))
    }

    @Test
    fun railHomeFallsBackToNearestParentWithoutForecourtNames() {
        val fixture = times(
            stopTime(place("Genève, Cornavin", stopId = "H"), TransportationMode.BUS, "home1"),
            stopTime(place("Genève, Coutance", stopId = "NEAR:1", parentId = "NEAR", lat = 46.21050, lon = 6.14260), TransportationMode.BUS, "nearBus"),
            stopTime(place("Genève, Coutance", stopId = "NEAR:2", parentId = "NEAR", lat = 46.21052, lon = 6.14262), TransportationMode.TRAM, "nearTram"),
            stopTime(place("Genève, Servette", stopId = "FAR:1", parentId = "FAR", lat = 46.21500, lon = 6.15000), TransportationMode.BUS, "farBus"),
            stopTime(place("Lausanne", stopId = "R1"), TransportationMode.RAIL, "railExtra")
        )
        val result = fixture.filteredToStation(
            stopId = "H",
            name = "Genève, Cornavin",
            lat = 46.21022,
            lon = 6.14229,
            servesRail = true
        )
        assertEquals(listOf("home1", "nearBus", "nearTram"), tripIds(result))
    }

    @Test
    fun railHomeWithoutCoordinatesKeepsAllNonRailExtras() {
        val fixture = times(
            stopTime(place("Genève", stopId = "H"), TransportationMode.SUBURBAN, "home1"),
            stopTime(place("Genève, Coutance", stopId = "B1"), TransportationMode.BUS, "busExtra1"),
            stopTime(place("Genève, Servette", stopId = "B2"), TransportationMode.BUS, "busExtra2"),
            stopTime(place("Lausanne", stopId = "R1"), TransportationMode.RAIL, "railExtra")
        )
        val result = fixture.filteredToStation(stopId = "H")
        assertEquals(listOf("home1", "busExtra1", "busExtra2"), tripIds(result))
    }

    @Test
    fun groupedStopIdsMembershipCountsAsHome() {
        val fixture = times(
            stopTime(place("Genève, Bel-Air", stopId = "G2:1", parentId = "G2"), TransportationMode.BUS, "memberByParent"),
            stopTime(place("Genève, Bel-Air", stopId = "G1"), TransportationMode.TRAM, "memberByStopId"),
            stopTime(place("Genève, Stand", stopId = "ZZ:1", parentId = "ZZ"), TransportationMode.BUS, "busExtra"),
            stopTime(place("Genève", stopId = "ZZ:2", parentId = "ZZ"), TransportationMode.RAIL, "railExtra")
        )
        val result = fixture.filteredToStation(stopId = "MAIN", groupedStopIds = listOf("G1", "G2"))
        assertEquals(listOf("memberByParent", "memberByStopId", "railExtra"), tripIds(result))
    }

    @Test
    fun sameNameWithin200mMatchesHome() {
        val fixture = times(
            stopTime(place("GENEVE, BEL-AIR", stopId = "X1", lat = 46.20574, lon = 6.14100), TransportationMode.BUS, "within200m"),
            stopTime(place("Genève, Bel-Air", stopId = "Y1", lat = 46.20664, lon = 6.14100), TransportationMode.BUS, "beyond200m")
        )
        val result = fixture.filteredToStation(
            stopId = "H1",
            name = "Genève, Bel-Air",
            lat = 46.20439,
            lon = 6.14100
        )
        assertEquals(listOf("within200m"), tripIds(result))
    }

    @Test
    fun filteringPreservesCursors() {
        val fixture = StopTimes(
            stopTimes = listOf(
                stopTime(place("Genève, Bel-Air", stopId = "H"), TransportationMode.BUS, "home1"),
                stopTime(place("Genève, Stand", stopId = "S1"), TransportationMode.BUS, "busExtra")
            ),
            previousPageCursor = "cursor-before",
            nextPageCursor = "cursor-after"
        )
        val result = fixture.filteredToStation(stopId = "H", name = "Genève, Bel-Air")
        assertEquals(listOf("home1"), tripIds(result))
        assertEquals("cursor-before", result.previousPageCursor)
        assertEquals("cursor-after", result.nextPageCursor)
    }
}

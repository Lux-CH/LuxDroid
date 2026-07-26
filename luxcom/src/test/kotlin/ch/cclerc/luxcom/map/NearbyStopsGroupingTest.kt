package ch.cclerc.luxcom.map

import ch.cclerc.luxcom.geo.StopGrouping
import ch.cclerc.luxcom.geo.calculateBoundingBox
import ch.cclerc.luxcom.geo.departureRadiusMeters
import ch.cclerc.luxcom.model.Place
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyStopsGroupingTest {

    private fun place(name: String, lat: Double, lon: Double, stopId: String) = Place(
        name = name,
        stopId = stopId,
        lat = lat,
        lon = lon,
        vertexType = Place.VertexType.TRANSIT
    )

    private val gare = place("Nyon, gare", 46.38310, 6.24100, "A")
    private val gareNord = place("Nyon, gare/nord", 46.38580, 6.24100, "B")
    private val gareTwin = place("Nyon, gare", 46.38535, 6.24100, "C")
    private val gareClose = place("Nyon, gare", 46.38445, 6.24100, "F")
    private val centreVille = place("Nyon, Centre-Ville", 46.38310, 6.24100, "E")

    @Test
    fun groupsSameNameWithin200m() {
        val groups = groupPlacesByStop(listOf(gare, gareClose))
        assertEquals(1, groups.size)
        assertEquals(listOf("A", "F"), groups[0].map { it.stopId })
    }

    @Test
    fun doesNotGroupSameNameBeyond200m() {
        val groups = groupPlacesByStop(listOf(gare, gareTwin))
        assertEquals(2, groups.size)
        assertEquals(listOf("A"), groups[0].map { it.stopId })
        assertEquals(listOf("C"), groups[1].map { it.stopId })
    }

    @Test
    fun groupsFamilyMembersUpTo350m() {
        val groups = groupPlacesByStop(listOf(gare, gareNord))
        assertEquals(1, groups.size)
        assertEquals(listOf("A", "B"), groups[0].map { it.stopId })
    }

    @Test
    fun chainsMembershipThroughFamilyMembers() {
        val groups = groupPlacesByStop(listOf(gare, gareNord, gareTwin))
        assertEquals(1, groups.size)
        assertEquals(listOf("A", "B", "C"), groups[0].map { it.stopId })
    }

    @Test
    fun neverGroupsAcrossFamiliesEvenWhenCoLocated() {
        val groups = groupPlacesByStop(listOf(gare, centreVille))
        assertEquals(2, groups.size)
        assertEquals(listOf("A"), groups[0].map { it.stopId })
        assertEquals(listOf("E"), groups[1].map { it.stopId })
    }

    @Test
    fun emptyInputYieldsNoGroups() {
        assertTrue(groupPlacesByStop(emptyList()).isEmpty())
    }

    @Test
    fun boundingBoxMatchesReferenceValues() {
        val bbox = calculateBoundingBox(46.2044, 6.1432, 500.0)
        assertEquals(46.19990339197041, bbox.minLat, 1e-9)
        assertEquals(46.20889660802959, bbox.maxLat, 1e-9)
        assertEquals(6.136702835349233, bbox.minLon, 1e-9)
        assertEquals(6.149697164650767, bbox.maxLon, 1e-9)
    }

    @Test
    fun boundingBoxIsCenteredAndGrowsWithRadius() {
        val small = calculateBoundingBox(46.2044, 6.1432, 500.0)
        val large = calculateBoundingBox(46.2044, 6.1432, 500.0 + departureRadiusMeters)
        assertEquals(46.2044, (small.minLat + small.maxLat) / 2, 1e-9)
        assertEquals(6.1432, (small.minLon + small.maxLon) / 2, 1e-9)
        assertTrue(large.minLat < small.minLat)
        assertTrue(large.maxLat > small.maxLat)
        assertTrue(large.minLon < small.minLon)
        assertTrue(large.maxLon > small.maxLon)
    }

    @Test
    fun railNeighbourRadiusUsesDepartureRadius() {
        val stationLat = 46.21022
        val stationLon = 6.14229
        val nearLat = stationLat + 0.00225
        val farLat = stationLat + 0.00284
        assertTrue(StopGrouping.distance(stationLat, stationLon, nearLat, stationLon) <= departureRadiusMeters)
        assertFalse(StopGrouping.distance(stationLat, stationLon, farLat, stationLon) <= departureRadiusMeters)
    }

    @Test
    fun railNeighbourPrefilterCoversDepartureRadiusAtLocalLatitudes() {
        val lat = 46.2044
        val lon = 6.1432
        val maxLatDelta = departureRadiusMeters / 111_000 * 1.1
        val maxLonDelta = maxLatDelta / cos(lat * PI / 180)

        val north = lat + 0.002697964817756192
        assertEquals(departureRadiusMeters, StopGrouping.distance(lat, lon, north, lon), 0.001)
        assertTrue(abs(north - lat) <= maxLatDelta)

        val east = lon + 0.0038982987904600985
        assertEquals(departureRadiusMeters, StopGrouping.distance(lat, lon, lat, east), 0.5)
        assertTrue(abs(east - lon) <= maxLonDelta)
    }
}

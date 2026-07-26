package ch.cclerc.luxcom.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StopGroupingTest {

    @Test
    fun normalizedNameFoldsDiacriticsAndCase() {
        assertEquals("geneve, bel-air", StopGrouping.normalizedName("Genève, Bel-Air"))
        assertEquals("geneve, bel-air", StopGrouping.normalizedName("GENEVE, BEL-AIR"))
        assertEquals(
            StopGrouping.normalizedName("Genève, Bel-Air"),
            StopGrouping.normalizedName("GENEVE, BEL-AIR")
        )
        assertEquals("zurich hb", StopGrouping.normalizedName("Zürich HB"))
    }

    @Test
    fun normalizedNameTrimsWhitespace() {
        assertEquals("geneve, bel-air", StopGrouping.normalizedName("  Genève, Bel-Air  "))
    }

    @Test
    fun stationFamilyTruncatesAtSlash() {
        assertEquals("nyon", StopGrouping.stationFamily("Nyon, gare/nord"))
        assertEquals("meyrin, vaudagne", StopGrouping.stationFamily("Meyrin, Vaudagne/Village"))
        assertEquals("zurich", StopGrouping.stationFamily("Zürich, Bahnhofplatz/HB"))
    }

    @Test
    fun stationFamilyCollapsesForecourtSecondComponent() {
        assertEquals("nyon", StopGrouping.stationFamily("Nyon, gare"))
        assertEquals("geneve", StopGrouping.stationFamily("Genève, gare Cornavin"))
        assertEquals("bellinzona", StopGrouping.stationFamily("Bellinzona, Stazione"))
    }

    @Test
    fun stationFamilyKeepsNonForecourtNames() {
        assertEquals("geneve, bel-air", StopGrouping.stationFamily("Genève, Bel-Air"))
        assertEquals("zurich hb", StopGrouping.stationFamily("Zürich HB"))
    }

    @Test
    fun isStationForecourtMatchesKeywords() {
        assertTrue(StopGrouping.isStationForecourt("gare"))
        assertTrue(StopGrouping.isStationForecourt("gare Cornavin"))
        assertTrue(StopGrouping.isStationForecourt("Bahnhofplatz"))
        assertTrue(StopGrouping.isStationForecourt("Stazione FS"))
        assertFalse(StopGrouping.isStationForecourt("Bel-Air"))
        assertFalse(StopGrouping.isStationForecourt("Centre-Ville"))
    }

    @Test
    fun isGenericLocationTermTrimsAndLowercases() {
        assertTrue(StopGrouping.isGenericLocationTerm("gare"))
        assertTrue(StopGrouping.isGenericLocationTerm(" Gare "))
        assertTrue(StopGrouping.isGenericLocationTerm("Gare Cornavin"))
        assertTrue(StopGrouping.isGenericLocationTerm("P+R"))
        assertFalse(StopGrouping.isGenericLocationTerm("Bel-Air"))
        assertFalse(StopGrouping.isGenericLocationTerm("gare cornavin nord"))
    }

    @Test
    fun equirectangularDistanceMatchesReferenceValues() {
        assertEquals(655.823, StopGrouping.distance(46.21022, 6.14229, 46.20439, 6.14100), 0.5)
        assertEquals(1031.600, StopGrouping.distance(46.19735, 6.14090, 46.19850, 6.12760), 0.5)
        assertEquals(0.0, StopGrouping.distance(46.2044, 6.1432, 46.2044, 6.1432), 0.0)
    }

    @Test
    fun distanceIsSymmetric() {
        val forward = StopGrouping.distance(46.21022, 6.14229, 46.20439, 6.14100)
        val backward = StopGrouping.distance(46.20439, 6.14100, 46.21022, 6.14229)
        assertEquals(forward, backward, 1e-9)
    }

    @Test
    fun isSameStopRequiresNameAndProximity() {
        assertTrue(
            StopGrouping.isSameStop("GENEVE, BEL-AIR", 46.20574, 6.14100, "Genève, Bel-Air", 46.20439, 6.14100)
        )
        assertFalse(
            StopGrouping.isSameStop("Genève, Bel-Air", 46.20664, 6.14100, "Genève, Bel-Air", 46.20439, 6.14100)
        )
        assertFalse(
            StopGrouping.isSameStop("Genève, Stand", 46.20574, 6.14100, "Genève, Bel-Air", 46.20439, 6.14100)
        )
        assertTrue(
            StopGrouping.isSameStop(
                "Genève, Bel-Air", 46.20664, 6.14100,
                "Genève, Bel-Air", 46.20439, 6.14100,
                limit = StopGrouping.familyMaxDistance
            )
        )
    }

    @Test
    fun isSameStopNormalizedComparesAgainstPreNormalizedName() {
        assertTrue(
            StopGrouping.isSameStopNormalized("Genève, Bel-Air", 46.20574, 6.14100, "geneve, bel-air", 46.20439, 6.14100)
        )
        assertFalse(
            StopGrouping.isSameStopNormalized("Genève, Bel-Air", 46.20574, 6.14100, "Genève, Bel-Air", 46.20439, 6.14100)
        )
    }

    @Test
    fun groupingConstantsMatchSwift() {
        assertEquals(200.0, StopGrouping.maxDistance, 0.0)
        assertEquals(350.0, StopGrouping.familyMaxDistance, 0.0)
        assertEquals(300.0, departureRadiusMeters, 0.0)
    }
}

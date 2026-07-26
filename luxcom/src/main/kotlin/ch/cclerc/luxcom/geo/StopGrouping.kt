package ch.cclerc.luxcom.geo

import java.text.Normalizer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

const val departureRadiusMeters: Double = 300.0

object StopGrouping {
    const val maxDistance: Double = 200.0
    const val familyMaxDistance: Double = 350.0

    private val combiningMarks = Regex("\\p{Mn}+")

    fun normalizedName(name: String): String =
        combiningMarks.replace(Normalizer.normalize(name, Normalizer.Form.NFD), "")
            .lowercase()
            .trim()

    val genericLocationTerms: Set<String> = setOf(
        "centre", "gare", "place", "douane", "gare cornavin", "p+r", "bahnhof", "stazione"
    )

    fun isGenericLocationTerm(term: String): Boolean =
        genericLocationTerms.contains(term.trim().lowercase())

    fun stationFamily(name: String): String {
        var base = normalizedName(name)
        val slash = base.indexOf('/')
        if (slash >= 0) {
            base = base.substring(0, slash)
        }
        val parts = base.split(",").map { it.trim() }
        if (parts.size <= 1 || !isStationForecourt(parts[1])) {
            return base.trim().lowercase()
        }
        return parts[0].lowercase()
    }

    fun isStationForecourt(name: String): Boolean {
        val n = name.lowercase()
        return n.contains("gare") || n.contains("bahnhof") || n.contains("stazione")
    }

    fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6_371_000.0
        val dLat = (lat2 - lat1) * PI / 180
        val dLon = (lon2 - lon1) * PI / 180 * cos((lat1 + lat2) / 2 * PI / 180)
        return earthRadius * sqrt(dLat * dLat + dLon * dLon)
    }

    fun isSameStop(
        name: String,
        lat: Double,
        lon: Double,
        otherName: String,
        otherLat: Double,
        otherLon: Double,
        limit: Double = maxDistance
    ): Boolean = isSameStopNormalized(name, lat, lon, normalizedName(otherName), otherLat, otherLon, limit)

    fun isSameStopNormalized(
        name: String,
        lat: Double,
        lon: Double,
        otherNormalizedName: String,
        otherLat: Double,
        otherLon: Double,
        limit: Double = maxDistance
    ): Boolean =
        distance(lat, lon, otherLat, otherLon) <= limit && normalizedName(name) == otherNormalizedName
}

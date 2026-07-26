package ch.cclerc.luxapp.domain.search

import ch.cclerc.luxcom.geo.calculateDistance
import ch.cclerc.luxcom.model.LocationType
import ch.cclerc.luxcom.model.SearchResult
import java.text.Normalizer

class SearchResultScorer {

    fun ranked(
        results: List<SearchResult>,
        query: String,
        userLat: Double?,
        userLon: Double?
    ): List<SearchResult> {
        val queryTokens = tokenize(query)
        if (queryTokens.isEmpty()) return results

        val normalizedQuery = queryTokens.joinToString(" ")

        val scored = results.map { result ->
            val textScore = textScore(result.name, queryTokens, normalizedQuery)
            val distance = distance(userLat, userLon, result)
            val proximityScore = proximityScore(distance)

            var composite = TEXT_WEIGHT * textScore + PROXIMITY_WEIGHT * proximityScore
            if (result.type == LocationType.STOP) {
                composite += STOP_RELEVANCE_WEIGHT * textScore
            }

            ScoredResult(result, composite, distance)
        }

        return scored.sortedWith(comparator).map { it.result }
    }

    private val comparator = Comparator<ScoredResult> { lhs, rhs ->
        if (lhs.score != rhs.score) return@Comparator rhs.score.compareTo(lhs.score)

        if (lhs.result.type != rhs.result.type) {
            val lhsIsStop = lhs.result.type == LocationType.STOP
            return@Comparator if (lhsIsStop) -1 else 1
        }

        if (lhs.distance != rhs.distance) return@Comparator lhs.distance.compareTo(rhs.distance)

        lhs.result.name.compareTo(rhs.result.name, ignoreCase = true)
    }

    private fun textScore(name: String, queryTokens: List<String>, normalizedQuery: String): Double {
        val nameTokens = tokenize(name)
        if (nameTokens.isEmpty()) return 0.0

        val normalizedName = nameTokens.joinToString(" ")

        if (normalizedName == normalizedQuery) return 1.0
        if (normalizedName.startsWith(normalizedQuery)) return 0.95
        if (normalizedName.contains(normalizedQuery)) return 0.85

        var matchTotal = 0.0
        for (queryToken in queryTokens) {
            var best = 0.0
            for (nameToken in nameTokens) {
                best = maxOf(best, tokenSimilarity(queryToken, nameToken))
                if (best == 1.0) break
            }
            matchTotal += best
        }

        val coverageScore = matchTotal / queryTokens.size.toDouble()

        val firstQueryToken = queryTokens.first()
        val firstNameToken = nameTokens.first()
        if (firstNameToken.startsWith(firstQueryToken)) {
            return minOf(1.0, coverageScore + 0.05)
        }

        return coverageScore
    }

    private fun tokenSimilarity(query: String, candidate: String): Double = when {
        candidate == query -> 1.0
        candidate.startsWith(query) -> 0.9
        query.startsWith(candidate) -> 0.75
        candidate.contains(query) -> 0.6
        else -> 0.0
    }

    private fun proximityScore(distance: Double): Double {
        if (distance >= Double.MAX_VALUE) return 0.0
        return PROXIMITY_HALF_DISTANCE / (PROXIMITY_HALF_DISTANCE + distance)
    }

    private fun distance(userLat: Double?, userLon: Double?, result: SearchResult): Double {
        if (userLat == null || userLon == null) return Double.MAX_VALUE
        if (!isValidCoordinate(result.lat, result.lon)) return Double.MAX_VALUE
        if (result.lat == 0.0 && result.lon == 0.0) return Double.MAX_VALUE
        return calculateDistance(userLat, userLon, result.lat, result.lon)
    }

    private fun tokenize(text: String): List<String> {
        val folded = fold(text)

        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        for (character in folded) {
            if (character.isLetterOrDigit()) {
                current.append(character)
            } else if (current.isNotEmpty()) {
                tokens.add(canonical(current.toString()))
                current.setLength(0)
            }
        }
        if (current.isNotEmpty()) {
            tokens.add(canonical(current.toString()))
        }
        return tokens
    }

    private fun canonical(token: String): String = TOKEN_SYNONYMS[token] ?: token

    private data class ScoredResult(
        val result: SearchResult,
        val score: Double,
        val distance: Double
    )

    companion object {
        private const val PROXIMITY_HALF_DISTANCE = 4_000.0

        private const val TEXT_WEIGHT = 0.60
        private const val PROXIMITY_WEIGHT = 0.25
        private const val STOP_RELEVANCE_WEIGHT = 0.20

        private val TOKEN_SYNONYMS: Map<String, String> = mapOf(
            "st" to "saint", "ste" to "sainte", "sts" to "saints", "stes" to "saintes",
            "av" to "avenue", "ave" to "avenue",
            "bd" to "boulevard", "blvd" to "boulevard",
            "rte" to "route",
            "ch" to "chemin",
            "mt" to "mont"
        )

        private val COMBINING_MARKS = Regex("\\p{Mn}+")

        fun fold(text: String): String =
            COMBINING_MARKS.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "").lowercase()

        fun isValidCoordinate(lat: Double, lon: Double): Boolean =
            !lat.isNaN() && !lon.isNaN() && lat >= -90.0 && lat <= 90.0 && lon >= -180.0 && lon <= 180.0
    }
}

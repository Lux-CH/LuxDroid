package ch.cclerc.luxapp.domain.search

import ch.cclerc.luxcom.geo.calculateDistance
import ch.cclerc.luxcom.model.LocationType
import ch.cclerc.luxcom.model.SearchResult
import ch.cclerc.luxcom.model.TransportationMode
import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min

class SearchResultScorer {

    fun ranked(
        sources: List<List<SearchResult>>,
        query: String,
        userLat: Double?,
        userLon: Double?
    ): List<SearchResult> {
        val entries = sources.flatMap { source ->
            source.mapIndexed { index, result -> result to index }
        }

        val queryTokens = tokenize(query)
        if (queryTokens.isEmpty()) return entries.map { it.first }

        val hasUserLocation = userLat != null && userLon != null

        val scored = entries.map { (result, sourceRank) ->
            val textScore = textScore(result, queryTokens)
            val distance = distance(userLat, userLon, result)
            val proximityScore = proximityScore(distance, hasUserLocation)

            var composite = TEXT_WEIGHT * textScore +
                PROXIMITY_WEIGHT * proximityScore +
                SOURCE_RANK_WEIGHT * sourceRankScore(sourceRank)
            if (result.type == LocationType.STOP) {
                composite += (STOP_WEIGHT + IMPORTANCE_WEIGHT * importance(result)) * textScore
            }

            ScoredResult(result, composite, distance)
        }

        return scored.sortedWith(comparator).map { it.result }
    }

    private val comparator = Comparator<ScoredResult> { lhs, rhs ->
        if (lhs.score != rhs.score) return@Comparator rhs.score.compareTo(lhs.score)
        if (lhs.distance != rhs.distance) return@Comparator lhs.distance.compareTo(rhs.distance)
        lhs.result.name.compareTo(rhs.result.name, ignoreCase = true)
    }

    private fun textScore(result: SearchResult, queryTokens: List<String>): Double {
        val nameTokens = tokenize(result.name)
        if (nameTokens.isEmpty()) return 0.0

        val localNameTokens = localNameTokens(result, nameTokens)
        val candidates = mutableListOf(localNameTokens, nameTokens)
        if (result.type == LocationType.STOP) {
            val withoutStationWords = localNameTokens.filter { it !in STATION_WORDS }
            if (withoutStationWords.isNotEmpty() && withoutStationWords.size < localNameTokens.size) {
                candidates.add(withoutStationWords)
            }
        }
        val phraseScore = candidates.maxOf { phraseScore(it, queryTokens) }

        val areaTokens = result.areas.flatMap { tokenize(it.name) }.toSet()
        var matchTotal = 0.0
        for (queryToken in queryTokens) {
            val nameMatch = nameTokens.maxOfOrNull { tokenSimilarity(queryToken, it) } ?: 0.0
            val areaMatch = areaTokens.maxOfOrNull { tokenSimilarity(queryToken, it) } ?: 0.0
            matchTotal += max(nameMatch, 0.9 * areaMatch)
        }
        val coverageScore = LOOSE_MATCH_SCORE * matchTotal / queryTokens.size.toDouble()

        return max(phraseScore, coverageScore)
    }

    private fun localNameTokens(result: SearchResult, nameTokens: List<String>): List<String> {
        val commaIndex = result.name.indexOf(',')
        if (commaIndex >= 0) {
            val localTokens = tokenize(result.name.substring(commaIndex + 1))
            if (localTokens.isNotEmpty()) return localTokens
        }

        for (area in result.areas) {
            val areaTokens = tokenize(area.name)
            if (areaTokens.isNotEmpty() && nameTokens.size > areaTokens.size &&
                nameTokens.subList(0, areaTokens.size) == areaTokens
            ) {
                return nameTokens.drop(areaTokens.size)
            }
        }

        return nameTokens
    }

    private fun phraseScore(candidate: List<String>, queryTokens: List<String>): Double {
        if (candidate.isEmpty()) return 0.0

        val orderedQueryTokens = houseNumberLast(queryTokens)
        val candidatePhrase = " " + candidate.joinToString(" ") + " "
        val queryPhrase = " " + orderedQueryTokens.joinToString(" ")
        val endsWithNumber = orderedQueryTokens.lastOrNull()?.let(::isNumber) ?: false
        val boundedQueryPhrase = if (endsWithNumber) "$queryPhrase " else queryPhrase

        if (candidatePhrase == "$queryPhrase ") return 1.0
        if (candidatePhrase.startsWith(boundedQueryPhrase)) return 0.95
        if (candidatePhrase.contains(boundedQueryPhrase)) return LOOSE_MATCH_SCORE

        val candidateText = candidate.joinToString(" ")
        val queryText = orderedQueryTokens.joinToString(" ")
        if (!containsDigit(candidateText) && !containsDigit(queryText) && isTypo(candidateText, queryText)) {
            return 0.85
        }
        return 0.0
    }

    private fun houseNumberLast(tokens: List<String>): List<String> {
        if (tokens.size <= 1 || !isNumber(tokens.first())) return tokens
        return tokens.drop(1) + tokens.first()
    }

    private fun tokenSimilarity(query: String, candidate: String): Double = when {
        candidate == query -> 1.0
        candidate.startsWith(query) -> if (query.length >= 2 && !isNumber(query)) 0.9 else 0.5
        isTypo(candidate, query) -> 0.7
        else -> 0.0
    }

    private fun isTypo(candidate: String, query: String): Boolean {
        if (query.length < 4 || candidate.length < 3 || isNumber(query)) return false

        val allowedEdits = if (query.length >= 8) 2 else 1
        if (abs(candidate.length - query.length) > allowedEdits) return false

        return editDistance(query, candidate, allowedEdits) <= allowedEdits
    }

    private fun editDistance(lhs: String, rhs: String, limit: Int): Int {
        var previousPrevious = IntArray(rhs.length + 1)
        var previous = IntArray(rhs.length + 1) { it }
        var current = IntArray(rhs.length + 1)

        for (i in 1..lhs.length) {
            current[0] = i
            var rowMinimum = current[0]
            for (j in 1..rhs.length) {
                val cost = if (lhs[i - 1] == rhs[j - 1]) 0 else 1
                current[j] = min(min(previous[j] + 1, current[j - 1] + 1), previous[j - 1] + cost)
                if (i > 1 && j > 1 && lhs[i - 1] == rhs[j - 2] && lhs[i - 2] == rhs[j - 1]) {
                    current[j] = min(current[j], previousPrevious[j - 2] + 1)
                }
                rowMinimum = min(rowMinimum, current[j])
            }
            if (rowMinimum > limit) return rowMinimum
            val recycled = previousPrevious
            previousPrevious = previous
            previous = current
            current = recycled
        }

        return previous[rhs.length]
    }

    private fun importance(result: SearchResult): Double = when {
        result.modes.any { it in HUB_MODES } -> 1.0
        result.servesMainlineRail -> 0.5
        else -> 0.0
    }

    private fun containsDigit(text: String): Boolean = text.any { it.isDigit() }

    private fun isNumber(token: String): Boolean = token.isNotEmpty() && token.all { it.isDigit() }

    private fun proximityScore(distance: Double, hasUserLocation: Boolean): Double {
        if (!hasUserLocation) return 0.0
        if (distance >= Double.MAX_VALUE) return UNKNOWN_PLACE_PROXIMITY

        val kilometers = distance / 1_000
        return max(0.0, 1 - log10(1 + kilometers) / log10(1 + PROXIMITY_HORIZON_KILOMETERS))
    }

    private fun sourceRankScore(rank: Int): Double = 1 / (1 + 0.35 * rank)

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
        private const val TEXT_WEIGHT = 0.55
        private const val PROXIMITY_WEIGHT = 0.22
        private const val SOURCE_RANK_WEIGHT = 0.15
        private const val STOP_WEIGHT = 0.08
        private const val IMPORTANCE_WEIGHT = 0.08
        private const val LOOSE_MATCH_SCORE = 0.75
        private const val PROXIMITY_HORIZON_KILOMETERS = 300.0
        private const val UNKNOWN_PLACE_PROXIMITY = 0.3

        private val STATION_WORDS = setOf("gare", "bahnhof", "stazione", "station", "hb", "hbf", "bf")

        private val HUB_MODES = setOf(TransportationMode.LONG_DISTANCE, TransportationMode.HIGHSPEED_RAIL)

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

package ch.cclerc.luxapp.domain.search

import ch.cclerc.luxcom.api.geocode
import ch.cclerc.luxcom.geo.calculateDistance
import ch.cclerc.luxcom.model.LocationType
import ch.cclerc.luxcom.model.SearchResult
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

class HybridLocationSearchService(
    private val poiProvider: PoiSearchProvider = PhotonPoiSearchProvider()
) {

    private val scorer = SearchResultScorer()

    suspend fun search(query: String, userLat: Double?, userLon: Double?): List<SearchResult> = coroutineScope {
        val stopSearch = async { luxGeocode(query, LocationType.STOP, userLat, userLon) }
        val addressSearch = async { searchAddresses(query, userLat, userLon) }
        val placeSearch = async { searchPlaces(query, userLat, userLon) }

        val stopResults = stopSearch.await()
        val addressResults = addressSearch.await()
        val placeOutcome = placeSearch.await()

        val sources = mutableListOf(stopResults, placeOutcome.results, addressResults)

        if (placeOutcome.wasRateLimited) {
            sources += luxGeocode(query, null, userLat, userLon)
        }

        if (placeOutcome.styles.isNotEmpty()) {
            SearchResultVisualStyleStore.setStyles(placeOutcome.styles)
        }

        val ranked = scorer.ranked(sources, query, userLat, userLon)
        deduplicatedByNameAndProximity(deduplicated(ranked))
    }

    suspend fun resolve(result: SearchResult): SearchResult {
        if (result.lat != 0.0 || result.lon != 0.0) return result
        return poiProvider.resolve(result)
    }

    private suspend fun searchAddresses(query: String, userLat: Double?, userLon: Double?): List<SearchResult> {
        if (!looksLikeAddress(query)) return emptyList()
        return luxGeocode(query, LocationType.ADDRESS, userLat, userLon)
    }

    private fun looksLikeAddress(query: String): Boolean {
        if (query.any { it.isDigit() }) return true

        val tokens = SearchResultScorer.fold(query)
            .split(NON_ALPHANUMERIC_UNICODE)
            .filter { it.isNotEmpty() }

        return tokens.any { token ->
            token in STREET_KEYWORDS || STREET_SUFFIXES.any { token.endsWith(it) }
        }
    }

    private fun serverQuery(query: String): String = query
        .replace(SAINTE_PATTERN, "ste")
        .replace(SAINT_PATTERN, "st")

    private suspend fun luxGeocode(
        query: String,
        type: LocationType?,
        userLat: Double?,
        userLon: Double?
    ): List<SearchResult> {
        val text = serverQuery(query)
        return try {
            if (userLat != null && userLon != null) {
                geocode(text = text, type = type, place = userLat to userLon, placeBias = 2)
            } else {
                geocode(text = text, type = type)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            emptyList()
        }
    }

    private suspend fun searchPlaces(query: String, userLat: Double?, userLon: Double?): PoiSearchOutcome = try {
        withTimeoutOrNull(PLACE_SEARCH_TIMEOUT_MS) { poiProvider.search(query, userLat, userLon) }
            ?: PoiSearchOutcome(wasRateLimited = true)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        PoiSearchOutcome(wasRateLimited = true)
    }

    private fun deduplicated(results: List<SearchResult>): List<SearchResult> {
        val seen = mutableSetOf<String>()
        val deduped = mutableListOf<SearchResult>()

        for (result in results) {
            if (!seen.add(result.id)) continue
            deduped.add(result)
        }

        return deduped
    }

    private fun deduplicatedByNameAndProximity(results: List<SearchResult>): List<SearchResult> {
        val keptByNormalizedName = mutableMapOf<String, MutableList<SearchResult>>()
        val deduplicated = mutableListOf<SearchResult>()

        for (result in results) {
            val normalizedName = normalizedName(result.name)
            if (normalizedName.isEmpty()) {
                deduplicated.add(result)
                continue
            }

            val existing = keptByNormalizedName[normalizedName].orEmpty()
            val isDuplicate = existing.any { kept ->
                val bothAddresses = kept.type == LocationType.ADDRESS && result.type == LocationType.ADDRESS
                areWithinDuplicateThreshold(
                    kept,
                    result,
                    if (bothAddresses) ADDRESS_DUPLICATE_DISTANCE_THRESHOLD else DUPLICATE_DISTANCE_THRESHOLD
                )
            }
            if (isDuplicate) continue

            keptByNormalizedName.getOrPut(normalizedName) { mutableListOf() }.add(result)
            deduplicated.add(result)
        }

        return deduplicated
    }

    private fun normalizedName(name: String): String =
        SearchResultScorer.fold(name)
            .split(NON_ALPHANUMERIC)
            .filter { it.isNotEmpty() }
            .joinToString(" ")

    private fun areWithinDuplicateThreshold(lhs: SearchResult, rhs: SearchResult, threshold: Double): Boolean {
        if (!SearchResultScorer.isValidCoordinate(lhs.lat, lhs.lon)) return false
        if (!SearchResultScorer.isValidCoordinate(rhs.lat, rhs.lon)) return false
        if (lhs.lat == 0.0 && lhs.lon == 0.0) return false
        if (rhs.lat == 0.0 && rhs.lon == 0.0) return false

        return calculateDistance(lhs.lat, lhs.lon, rhs.lat, rhs.lon) <= threshold
    }

    private companion object {
        const val DUPLICATE_DISTANCE_THRESHOLD = 120.0
        const val ADDRESS_DUPLICATE_DISTANCE_THRESHOLD = 400.0
        const val PLACE_SEARCH_TIMEOUT_MS = 1_300L
        val NON_ALPHANUMERIC = Regex("[^a-z0-9]+")
        val NON_ALPHANUMERIC_UNICODE = Regex("[^\\p{L}\\p{N}]+")
        val SAINTE_PATTERN = Regex("(?i)\\bsainte\\b")
        val SAINT_PATTERN = Regex("(?i)\\bsaint\\b")

        val STREET_KEYWORDS = setOf(
            "rue", "route", "rte", "chemin", "ch", "avenue", "av", "ave", "boulevard", "bd", "blvd",
            "place", "pl", "quai", "promenade", "allee", "impasse", "passage", "sentier", "square",
            "rampe", "esplanade", "cours", "strasse", "str", "weg", "gasse", "platz",
            "via", "viale", "piazza", "corso", "vicolo"
        )
        val STREET_SUFFIXES = listOf("strasse", "weg", "gasse", "platz")
    }
}

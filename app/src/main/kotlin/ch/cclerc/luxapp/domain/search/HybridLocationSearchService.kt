package ch.cclerc.luxapp.domain.search

import ch.cclerc.luxcom.api.geocode
import ch.cclerc.luxcom.geo.calculateDistance
import ch.cclerc.luxcom.model.LocationType
import ch.cclerc.luxcom.model.SearchResult
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class HybridLocationSearchService(
    private val poiProvider: PoiSearchProvider = PhotonPoiSearchProvider()
) {

    private val scorer = SearchResultScorer()

    suspend fun search(query: String, userLat: Double?, userLon: Double?): List<SearchResult> = coroutineScope {
        val stopSearch = async { searchStops(query, userLat, userLon) }
        val placeSearch = async { searchPlaces(query, userLat, userLon) }

        val stopResults = stopSearch.await()
        val placeOutcome = placeSearch.await()

        val merged = mutableListOf<SearchResult>()
        merged += stopResults
        merged += placeOutcome.results

        if (placeOutcome.wasRateLimited) {
            merged += searchLuxFallbackAll(query, userLat, userLon)
        }

        if (placeOutcome.styles.isNotEmpty()) {
            SearchResultVisualStyleStore.setStyles(placeOutcome.styles)
        }

        val deduplicatedById = deduplicated(merged)
        val ranked = scorer.ranked(deduplicatedById, query, userLat, userLon)
        deduplicatedByNameAndProximity(ranked)
    }

    suspend fun resolve(result: SearchResult): SearchResult {
        if (result.lat != 0.0 || result.lon != 0.0) return result
        return poiProvider.resolve(result)
    }

    private suspend fun searchStops(query: String, userLat: Double?, userLon: Double?): List<SearchResult> =
        try {
            if (userLat != null && userLon != null) {
                geocode(text = query, type = LocationType.STOP, place = userLat to userLon, placeBias = 2)
            } else {
                geocode(text = query, type = LocationType.STOP)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            emptyList()
        }

    private suspend fun searchLuxFallbackAll(
        query: String,
        userLat: Double?,
        userLon: Double?
    ): List<SearchResult> = try {
        if (userLat != null && userLon != null) {
            geocode(text = query, place = userLat to userLon, placeBias = 2)
        } else {
            geocode(text = query)
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        emptyList()
    }

    private suspend fun searchPlaces(query: String, userLat: Double?, userLon: Double?): PoiSearchOutcome = try {
        poiProvider.search(query, userLat, userLon)
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
            val isDuplicate = existing.any { areWithinDuplicateThreshold(it, result) }
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

    private fun areWithinDuplicateThreshold(lhs: SearchResult, rhs: SearchResult): Boolean {
        if (!SearchResultScorer.isValidCoordinate(lhs.lat, lhs.lon)) return false
        if (!SearchResultScorer.isValidCoordinate(rhs.lat, rhs.lon)) return false
        if (lhs.lat == 0.0 && lhs.lon == 0.0) return false
        if (rhs.lat == 0.0 && rhs.lon == 0.0) return false

        return calculateDistance(lhs.lat, lhs.lon, rhs.lat, rhs.lon) <= DUPLICATE_DISTANCE_THRESHOLD
    }

    private companion object {
        const val DUPLICATE_DISTANCE_THRESHOLD = 120.0
        val NON_ALPHANUMERIC = Regex("[^a-z0-9]+")
    }
}

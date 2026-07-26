package ch.cclerc.luxapp.domain.search

import ch.cclerc.luxcom.model.SearchResult

data class PoiSearchOutcome(
    val results: List<SearchResult> = emptyList(),
    val styles: Map<String, SearchResultVisualStyle> = emptyMap(),
    val wasRateLimited: Boolean = false
)

interface PoiSearchProvider {
    suspend fun search(query: String, userLat: Double?, userLon: Double?): PoiSearchOutcome

    suspend fun resolve(result: SearchResult): SearchResult = result
}

package ch.cclerc.luxcom.api

import ch.cclerc.luxcom.model.LocationType
import ch.cclerc.luxcom.model.SearchResult
import ch.cclerc.luxcom.net.ApiClient

suspend fun reverseGeocode(
    lat: Double,
    lon: Double,
    type: LocationType? = null
): List<SearchResult> {
    val queryItems = mutableListOf(
        "place" to "$lat, $lon"
    )

    if (type != null) {
        queryItems.add("type" to type.name)
    }

    return ApiClient.fetch(endpoint = "/reverse-geocode", queryItems = queryItems)
}

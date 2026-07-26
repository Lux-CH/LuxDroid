package ch.cclerc.luxcom.api

import ch.cclerc.luxcom.model.LocationType
import ch.cclerc.luxcom.model.SearchResult
import ch.cclerc.luxcom.net.ApiClient

suspend fun geocode(
    text: String,
    language: String = "fr",
    type: LocationType? = null,
    place: Pair<Double, Double>? = null,
    placeBias: Int? = null
): List<SearchResult> {
    val queryItems = mutableListOf(
        "text" to text,
        "language" to language
    )

    if (type != null) {
        queryItems.add("type" to type.name)
    }

    if (place != null) {
        queryItems.add("place" to "${place.first}, ${place.second}")
    }

    if (placeBias != null) {
        queryItems.add("placeBias" to placeBias.toString())
    }

    return ApiClient.fetch(endpoint = "/geocode", queryItems = queryItems)
}

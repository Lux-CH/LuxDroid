package ch.cclerc.luxcom.api

import ch.cclerc.luxcom.model.trip.Itinerary

suspend fun getTrip(tripId: String): Itinerary {
    val queryItems = listOf("tripId" to tripId)
    return fetchWithCrossBackendRetry(endpoint = "/trip", apiVersion = "v4", queryItems = queryItems)
}

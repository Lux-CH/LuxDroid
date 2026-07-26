package ch.cclerc.luxcom.model.trip

import ch.cclerc.luxcom.model.Place
import kotlinx.serialization.Serializable

@Serializable
data class Trip(
    val from: Place,
    val to: Place,
    val direct: List<Itinerary>,
    val itineraries: List<Itinerary>,
    val previousPageCursor: String,
    val nextPageCursor: String
)

package ch.cclerc.luxapp.ui.itinerary

import ch.cclerc.luxapp.domain.TripOption
import ch.cclerc.luxapp.ui.navigation.CoverController
import ch.cclerc.luxapp.ui.navigation.LuxCoverRequest
import ch.cclerc.luxcom.model.trip.Itinerary

fun CoverController.presentItinerary(
    tripId: String,
    fromNearby: Boolean = false,
    otherTripOptions: List<TripOption> = emptyList()
) {
    present(
        LuxCoverRequest {
            ItineraryView(
                tripId = tripId,
                fromNearby = fromNearby,
                onDismiss = { dismiss() },
                otherTripOptions = otherTripOptions
            )
        }
    )
}

fun CoverController.presentItinerary(
    itinerary: Itinerary,
    fromNearby: Boolean = false,
    destinationName: String? = null
) {
    present(
        LuxCoverRequest {
            ItineraryView(
                itinerary = itinerary,
                fromNearby = fromNearby,
                onDismiss = { dismiss() },
                destinationName = destinationName
            )
        }
    )
}

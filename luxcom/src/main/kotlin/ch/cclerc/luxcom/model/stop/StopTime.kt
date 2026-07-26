package ch.cclerc.luxcom.model.stop

import ch.cclerc.luxcom.model.Place
import ch.cclerc.luxcom.model.TransportationMode
import kotlinx.serialization.Serializable

@Serializable
data class StopTime(
    val place: Place,
    val mode: TransportationMode,
    val realTime: Boolean,
    val headsign: String? = null,
    val routeShortName: String,
    val tripId: String,
    val agencyId: String,
    val cancelled: Boolean
) {
    val id: String
        get() = tripId
}

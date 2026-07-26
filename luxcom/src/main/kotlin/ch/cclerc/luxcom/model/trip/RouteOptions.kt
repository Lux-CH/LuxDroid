package ch.cclerc.luxcom.model.trip

import ch.cclerc.luxcom.model.PedestrianProfile
import ch.cclerc.luxcom.model.TransportationMode
import java.time.Instant

data class RouteOptions(
    val from: RouteLocation,
    val to: RouteLocation,
    val via: List<String>?,
    val viaMinimumStay: List<Int>,
    val time: Instant?,
    val arriveBy: Boolean,
    val maxTransfers: Int,
    val minTransferTime: Int,
    val pedestrianProfile: PedestrianProfile,
    val pedestrianSpeed: Double?,
    val transitModes: List<TransportationMode>?,
    val numItineraries: Int?,
    var pageCursor: String? = null,
    val timetableView: Boolean,
    val maxPreTransitTime: Int?,
    val maxPostTransitTime: Int?,
    val numLegAlternatives: Int? = null
) {
    val useRoutedTransfers: Boolean? = true
    val maxMatchingDistance: Int? = 250
    val detailedTransfers: Boolean? = true

    class RouteLocation private constructor(
        val stopId: String?,
        val lat: Double?,
        val lon: Double?,
        val level: Double?
    ) {
        constructor(stopId: String) : this(stopId, null, null, null)

        constructor(lat: Double, lon: Double, level: Double? = null) : this(null, lat, lon, level)

        val queryValue: String
            get() {
                if (stopId != null) {
                    return stopId
                }
                if (lat != null && lon != null) {
                    if (level != null) {
                        return "$lat,$lon,$level"
                    }
                    return "$lat,$lon"
                }
                return "0,0"
            }
    }
}

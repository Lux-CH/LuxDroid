package ch.cclerc.luxcom.api

import ch.cclerc.luxcom.model.trip.RouteOptions
import ch.cclerc.luxcom.model.trip.Trip
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private const val PLAN_ENDPOINT = "/plan"
private const val PLAN_API_VERSION = "v4"

suspend fun getRoute(options: RouteOptions): Trip {
    val queryItems = mutableListOf(
        "fromPlace" to options.from.queryValue,
        "toPlace" to options.to.queryValue,
        "arriveBy" to options.arriveBy.toString(),
        "maxTransfers" to options.maxTransfers.toString(),
        "additionalTransferTime" to options.minTransferTime.toString(),
        "pedestrianProfile" to options.pedestrianProfile.name,
        "useRoutedTransfers" to (options.useRoutedTransfers ?: true).toString(),
        "maxMatchingDistance" to (options.maxMatchingDistance ?: 250).toString(),
        "detailedTransfers" to (options.detailedTransfers ?: true).toString(),
        "timetableView" to options.timetableView.toString(),
        "fastestDirectFactor" to "1.5"
    )

    val time = options.time
    if (time != null) {
        queryItems.add("time" to DateTimeFormatter.ISO_INSTANT.format(time.truncatedTo(ChronoUnit.SECONDS)))
    }

    val via = options.via
    if (!via.isNullOrEmpty()) {
        for (viaStop in via) {
            queryItems.add("via" to viaStop)
        }
    }

    if (options.viaMinimumStay.isNotEmpty()) {
        for (stayTime in options.viaMinimumStay) {
            queryItems.add("viaMinimumStay" to stayTime.toString())
        }
    }

    val transitModes = options.transitModes
    if (!transitModes.isNullOrEmpty()) {
        for (m in transitModes) {
            queryItems.add("transitModes" to m.rawValue)
        }
    }

    val numItineraries = options.numItineraries
    if (numItineraries != null) {
        queryItems.add("numItineraries" to numItineraries.toString())
    }

    val speed = options.pedestrianSpeed
    if (speed != null && speed != 1.2) {
        queryItems.add("pedestrianSpeed" to speed.toString())
    }

    val pageCursor = options.pageCursor
    if (pageCursor != null) {
        queryItems.add("pageCursor" to pageCursor)
    }

    val maxPreTransitTime = options.maxPreTransitTime
    if (maxPreTransitTime != null) {
        queryItems.add("maxPreTransitTime" to maxPreTransitTime.toString())
    }

    val maxPostTransitTime = options.maxPostTransitTime
    if (maxPostTransitTime != null) {
        queryItems.add("maxPostTransitTime" to maxPostTransitTime.toString())
    }

    val numLegAlternatives = options.numLegAlternatives
    if (numLegAlternatives != null) {
        queryItems.add("numLegAlternatives" to numLegAlternatives.toString())
    }

    return fetchWithCrossBackendRetry(
        endpoint = PLAN_ENDPOINT,
        apiVersion = PLAN_API_VERSION,
        queryItems = queryItems
    )
}

package ch.cclerc.luxcom.api

import ch.cclerc.luxcom.model.stop.StopTimes
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

suspend fun getDeparturesForStop(
    stopId: String,
    time: Instant = Instant.now(),
    arriveBy: Boolean = false,
    both: Boolean = false,
    direction: String? = null,
    mode: List<String>? = listOf("TRANSIT"),
    numberOfEvents: Int,
    radius: Int? = null,
    pageCursor: String? = null
): StopTimes {
    val queryItems = mutableListOf(
        "stopId" to stopId,
        "time" to DateTimeFormatter.ISO_INSTANT.format(time.truncatedTo(ChronoUnit.SECONDS)),
        "arriveBy" to arriveBy.toString(),
        "n" to numberOfEvents.toString()
    )

    if (both) {
        queryItems.add("both" to "true")
    }

    if (direction != null) {
        queryItems.add("direction" to direction)
    }

    if (mode != null) {
        for (m in mode) {
            queryItems.add("mode" to m)
        }
    }

    if (radius != null) {
        queryItems.add("exactRadius" to "true")
        queryItems.add("radius" to radius.toString())
    }

    if (pageCursor != null) {
        queryItems.add("pageCursor" to pageCursor)
    }

    return fetchWithCrossBackendRetry(
        endpoint = "/stoptimes",
        apiVersion = "v4",
        queryItems = queryItems
    )
}

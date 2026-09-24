package ch.cclerc.luxapp.domain.routing

import ch.cclerc.luxcom.api.getRoute
import ch.cclerc.luxcom.model.LocationType
import ch.cclerc.luxcom.model.Place
import ch.cclerc.luxcom.model.SearchResult
import ch.cclerc.luxcom.model.TransportationMode
import ch.cclerc.luxcom.model.trip.Itinerary
import ch.cclerc.luxcom.model.trip.Leg
import ch.cclerc.luxcom.model.trip.RouteOptions
import ch.cclerc.luxcom.model.trip.Trip
import java.time.Duration
import java.time.Instant

data class PlannedRoute(
    val itineraries: List<Itinerary>,
    val direct: List<Itinerary>,
    val previousPageCursor: String,
    val nextPageCursor: String
) {
    constructor(trip: Trip) : this(
        itineraries = trip.itineraries,
        direct = trip.direct,
        previousPageCursor = trip.previousPageCursor,
        nextPageCursor = trip.nextPageCursor
    )
}

data class PlannedVia(
    val location: SearchResult,
    val stay: Int
) {
    val isStop: Boolean get() = location.type == LocationType.STOP
}

object StitchedRoutePlanner {

    private class Segment(
        val from: RouteOptions.RouteLocation,
        val to: RouteOptions.RouteLocation,
        val stopVias: List<PlannedVia>,
        val endName: String?,
        val startName: String?,
        val stayAfterSeconds: Long
    )

    private const val MAX_EXTRA_PAGES = 2

    fun needsStitching(vias: List<PlannedVia>): Boolean = vias.any { !it.isStop }

    suspend fun plan(base: RouteOptions, vias: List<PlannedVia>): PlannedRoute {
        val segments = makeSegments(base, vias)
        return if (base.arriveBy) planBackward(base, segments) else planForward(base, segments)
    }

    private fun makeSegments(base: RouteOptions, vias: List<PlannedVia>): List<Segment> {
        val segments = mutableListOf<Segment>()
        var from = base.from
        var startName: String? = null
        var pendingStops = mutableListOf<PlannedVia>()

        for (via in vias) {
            if (via.isStop) {
                pendingStops.add(via)
                continue
            }
            val place = RouteOptions.RouteLocation(via.location.lat, via.location.lon, via.location.level)
            segments.add(
                Segment(
                    from = from, to = place, stopVias = pendingStops,
                    endName = via.location.name, startName = startName,
                    stayAfterSeconds = via.stay * 60L
                )
            )
            from = place
            startName = via.location.name
            pendingStops = mutableListOf()
        }

        segments.add(
            Segment(
                from = from, to = base.to, stopVias = pendingStops,
                endName = null, startName = startName, stayAfterSeconds = 0
            )
        )
        return segments
    }

    private fun options(
        segment: Segment,
        base: RouteOptions,
        time: Instant?,
        arriveBy: Boolean,
        pageCursor: String?
    ): RouteOptions {
        val stopIds = segment.stopVias.map { it.location.id }
        val stays = segment.stopVias.map { it.stay }
        return RouteOptions(
            from = segment.from,
            to = segment.to,
            via = stopIds.ifEmpty { null },
            viaMinimumStay = if (stays.any { it > 0 }) stays else emptyList(),
            time = time,
            arriveBy = arriveBy,
            maxTransfers = base.maxTransfers,
            minTransferTime = base.minTransferTime,
            pedestrianProfile = base.pedestrianProfile,
            pedestrianSpeed = base.pedestrianSpeed,
            transitModes = base.transitModes,
            numItineraries = base.numItineraries,
            pageCursor = pageCursor,
            timetableView = base.timetableView,
            maxPreTransitTime = if (segment.startName == null) base.maxPreTransitTime else null,
            maxPostTransitTime = if (segment.endName == null) base.maxPostTransitTime else null,
            numLegAlternatives = base.numLegAlternatives
        )
    }

    private suspend fun planForward(base: RouteOptions, segments: List<Segment>): PlannedRoute {
        val first = getRoute(options(segments[0], base, base.time, false, base.pageCursor))
        var chains = (first.itineraries + first.direct).map { listOf(it) }

        for (index in 1 until segments.size) {
            val segment = segments[index]
            val stay = segments[index - 1].stayAfterSeconds
            val readyTimes = chains.map { it.last().endTime.plusSeconds(stay) }
            val earliest = readyTimes.minOrNull() ?: break
            val latest = readyTimes.maxOrNull() ?: break

            var response = getRoute(options(segment, base, earliest, false, null))
            val candidates = response.itineraries.toMutableList()
            val directs = response.direct
            var pages = 0
            while ((candidates.maxOfOrNull { it.startTime } ?: Instant.MIN) < latest &&
                pages < MAX_EXTRA_PAGES && response.nextPageCursor.isNotEmpty()
            ) {
                response = getRoute(options(segment, base, earliest, false, response.nextPageCursor))
                candidates += response.itineraries
                pages += 1
            }

            chains = chains.mapNotNull { chain ->
                val ready = chain.last().endTime.plusSeconds(stay)
                val transit = candidates.filter { it.startTime >= ready }
                val walks = directs.map { it.shifted(Duration.between(it.startTime, ready)) }
                val best = (transit + walks).minByOrNull { it.endTime } ?: return@mapNotNull null
                chain + best
            }
        }

        return PlannedRoute(
            itineraries = merged(chains, segments),
            direct = emptyList(),
            previousPageCursor = first.previousPageCursor,
            nextPageCursor = first.nextPageCursor
        )
    }

    private suspend fun planBackward(base: RouteOptions, segments: List<Segment>): PlannedRoute {
        val lastIndex = segments.lastIndex
        val last = getRoute(options(segments[lastIndex], base, base.time, true, base.pageCursor))
        var chains = (last.itineraries + last.direct).map { listOf(it) }

        for (index in (0 until lastIndex).reversed()) {
            val segment = segments[index]
            val stay = segment.stayAfterSeconds
            val deadlines = chains.map { it.first().startTime.minusSeconds(stay) }
            val earliest = deadlines.minOrNull() ?: break
            val latest = deadlines.maxOrNull() ?: break

            var response = getRoute(options(segment, base, latest, true, null))
            val candidates = response.itineraries.toMutableList()
            val directs = response.direct
            var pages = 0
            while ((candidates.minOfOrNull { it.endTime } ?: Instant.MAX) > earliest &&
                pages < MAX_EXTRA_PAGES && response.previousPageCursor.isNotEmpty()
            ) {
                response = getRoute(options(segment, base, latest, true, response.previousPageCursor))
                candidates += response.itineraries
                pages += 1
            }

            chains = chains.mapNotNull { chain ->
                val deadline = chain.first().startTime.minusSeconds(stay)
                val transit = candidates.filter { it.endTime <= deadline }
                val walks = directs.map { it.shifted(Duration.between(it.endTime, deadline)) }
                val best = (transit + walks).maxByOrNull { it.startTime } ?: return@mapNotNull null
                listOf(best) + chain
            }
        }

        return PlannedRoute(
            itineraries = merged(chains, segments),
            direct = emptyList(),
            previousPageCursor = last.previousPageCursor,
            nextPageCursor = last.nextPageCursor
        )
    }

    private fun merged(chains: List<List<Itinerary>>, segments: List<Segment>): List<Itinerary> {
        val journeys = chains.mapNotNull { chain ->
            if (chain.size != segments.size) null else stitch(chain, segments)
        }
        return removingDominated(journeys).sortedBy { it.startTime }
    }

    private fun stitch(chain: List<Itinerary>, segments: List<Segment>): Itinerary {
        val legs = mutableListOf<Leg>()
        chain.forEachIndexed { index, part ->
            val segment = segments[index]
            part.legs.forEachIndexed { legIndex, leg ->
                val isFirst = legIndex == 0
                val isLast = legIndex == part.legs.lastIndex
                legs.add(
                    leg.renamingEndpoints(
                        fromName = if (isFirst) segment.startName else null,
                        toName = if (isLast) segment.endName else null
                    )
                )
            }
        }

        val start = chain.first().startTime
        val end = chain.last().endTime
        val partsWithTransit = chain.count { part -> part.legs.any { it.mode != TransportationMode.WALK } }
        val transfers = chain.sumOf { it.transfers } + maxOf(0, partsWithTransit - 1)

        return Itinerary(
            duration = Duration.between(start, end).seconds.toInt(),
            startTime = start,
            endTime = end,
            transfers = transfers,
            legs = legs
        )
    }

    private fun removingDominated(journeys: List<Itinerary>): List<Itinerary> =
        journeys.filterIndexed { index, journey ->
            journeys.withIndex().none { (otherIndex, other) ->
                if (otherIndex == index) return@none false
                val noWorse = other.startTime >= journey.startTime &&
                    other.endTime <= journey.endTime &&
                    other.transfers <= journey.transfers
                val strictlyBetter = other.startTime > journey.startTime ||
                    other.endTime < journey.endTime ||
                    other.transfers < journey.transfers
                noWorse && (strictlyBetter || otherIndex < index)
            }
        }

    private fun Itinerary.shifted(interval: Duration): Itinerary {
        if (interval.isZero) return this
        return Itinerary(
            duration = duration,
            startTime = startTime.plus(interval),
            endTime = endTime.plus(interval),
            transfers = transfers,
            legs = legs.map { it.shifted(interval) }
        )
    }

    private fun Leg.shifted(interval: Duration): Leg = copy(
        from = from.shifted(interval),
        to = to.shifted(interval),
        startTime = startTime.plus(interval),
        endTime = endTime.plus(interval),
        scheduledStartTime = scheduledStartTime.plus(interval),
        scheduledEndTime = scheduledEndTime.plus(interval)
    )

    private fun Leg.renamingEndpoints(fromName: String?, toName: String?): Leg {
        if (fromName == null && toName == null) return this
        return copy(
            from = if (fromName != null) from.copy(name = fromName) else from,
            to = if (toName != null) to.copy(name = toName) else to
        )
    }

    private fun Place.shifted(interval: Duration): Place = copy(
        arrival = arrival?.plus(interval),
        departure = departure?.plus(interval),
        scheduledArrival = scheduledArrival?.plus(interval),
        scheduledDeparture = scheduledDeparture?.plus(interval)
    )
}

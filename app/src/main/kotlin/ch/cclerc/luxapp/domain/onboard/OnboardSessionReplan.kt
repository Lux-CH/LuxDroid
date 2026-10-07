package ch.cclerc.luxapp.domain.onboard

import ch.cclerc.luxapp.core.HapticFeedback
import ch.cclerc.luxapp.domain.intelligence.IntelligenceLearner
import ch.cclerc.luxapp.domain.intelligence.IntelligenceStore
import ch.cclerc.luxapp.domain.intelligence.TripIntelligence
import ch.cclerc.luxapp.domain.intelligence.TripSuggestion
import ch.cclerc.luxapp.domain.intelligence.intelligenceSignature
import ch.cclerc.luxapp.domain.map.LatLng
import ch.cclerc.luxapp.domain.map.distanceTo
import ch.cclerc.luxapp.domain.search.RouteOptionsStore
import ch.cclerc.luxapp.ui.itinerary.formatTime
import ch.cclerc.luxcom.api.getDeparturesForStop
import ch.cclerc.luxcom.api.getRoute
import ch.cclerc.luxcom.api.getTrip
import ch.cclerc.luxcom.model.Place
import ch.cclerc.luxcom.model.trip.Itinerary
import ch.cclerc.luxcom.model.trip.Leg
import ch.cclerc.luxcom.model.trip.RouteOptions
import java.time.Instant
import java.util.UUID
import kotlin.math.max
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

enum class ReplanReason { CONNECTION, MISSED_DEPARTURE, CANCELLED, EARLIER, FASTER, ALTERNATIVE }

class ReplanProposal(
    val reason: ReplanReason,
    var replaceFrom: Int,
    val legs: List<Leg>,
    val arrival: Instant,
    val lateBy: Double,
    val autoApplyAt: Instant?,
    val expiresAt: Instant? = null,
    val exitName: String? = null,
    val ridingTripId: String? = null,
    val transfers: Int? = null,
    val insight: TripSuggestion.Reason? = null,
    val source: Itinerary? = null
) {
    val id: String = UUID.randomUUID().toString()
    val firstTransit: Leg? get() = legs.firstOrNull { it.isTransit }
    val nextTransit: Leg?
        get() = if (ridingTripId == null) firstTransit else legs.firstOrNull { it.isTransit && it.tripId != ridingTripId }

    override fun equals(other: Any?): Boolean = other is ReplanProposal && other.id == id
    override fun hashCode(): Int = id.hashCode()
}

val OnboardSession.walkingSpeed: Double
    get() {
        walkingPace?.let { return it }
        val configured = RouteOptionsStore.pedestrianSpeed(RouteOptionsStore.load())
        return max(0.5, configured)
    }

fun OnboardSession.requestReplan(reason: ReplanReason) {
    if (!isRunning || phase == OnboardPhase.ARRIVED || isReplanning || replan != null) return
    if (legIndex in declinedReplanLegs || now.secondsSince(lastReplanAt) <= 60) return
    val destination = legs.lastOrNull()?.to ?: return

    val replaceFrom: Int
    val origin: RouteOptions.RouteLocation
    val departure: Instant
    val leg = currentLeg
    val stopId = leg?.to?.stopId
    if (phase == OnboardPhase.RIDING && leg != null && !leg.cancelled && stopId != null) {
        replaceFrom = legIndex + 1
        origin = RouteOptions.RouteLocation(stopId)
        departure = leg.endTime
    } else {
        val coordinate = userLocation?.coordinate ?: leg?.from?.coordinate ?: return
        replaceFrom = legIndex
        origin = RouteOptions.RouteLocation(coordinate.latitude, coordinate.longitude)
        departure = now
    }
    if (replaceFrom >= legs.size) return
    val options = savedRouteOptions(origin, routeTarget(destination), departure)
    val currentArrival = arrivalDate
    val currentNext = legs.subList(replaceFrom, legs.size).firstOrNull { it.isTransit }?.tripId
    val near = userLocation?.coordinate ?: currentLeg?.let { LatLng(it.from.lat, it.from.lon) }

    lastReplanAt = now
    isReplanning = true
    replanJob?.cancel()
    replanJob = scope.launch {
        val result = runCatching { getRoute(options) }.getOrNull()
        val candidates = result?.itineraries.orEmpty().filter { !it.startTime.isBefore(departure.minusSeconds(60)) }
        var choice = candidates.minByOrNull { it.endTime }
        var insight: TripSuggestion.Reason? = null
        val fastestEnd = choice?.endTime
        if (IntelligenceStore.isIntelligentMode && candidates.size > 1 && fastestEnd != null) {
            val context = TripIntelligence.liveContext(near, departure, candidates, includeCrowd = false)
            val ranked = TripIntelligence.rank(candidates, context)
            val top = ranked.firstOrNull { !it.itinerary.endTime.isAfter(fastestEnd.plusSeconds(2 * 60)) }
            if (top != null && top.itinerary.intelligenceSignature != choice?.intelligenceSignature) {
                choice = top.itinerary
                TripIntelligence.fastest(ranked)?.let { fastest ->
                    insight = TripIntelligence.reasons(top, fastest, context).firstOrNull()
                }
            }
        }
        isReplanning = false
        val best = choice
        if (best == null) {
            showAlert(
                OnboardAlert(OnboardAlert.Severity.WARNING, "arrow.triangle.branch", "Aucune alternative trouvée", null),
                spoken = null,
                urgency = OnboardAnnouncer.Urgency.NOTICE
            )
            return@launch
        }
        if (reason == ReplanReason.CONNECTION && best.legs.firstOrNull { it.isTransit }?.tripId == currentNext) {
            return@launch
        }
        val proposal = ReplanProposal(
            reason = reason,
            replaceFrom = replaceFrom,
            legs = best.legs,
            arrival = best.endTime,
            lateBy = best.endTime.secondsSince(currentArrival),
            autoApplyAt = Instant.now().plusSeconds(60),
            transfers = best.transfers,
            insight = insight,
            source = best
        )
        replan = proposal
        proposal.firstTransit?.let { transit ->
            announcer.announce(
                "Nouvel itinéraire : prenez ${transit.spokenLineName}, ${spokenDeparture(transit.startTime)}, arrivée à ${formatTime(proposal.arrival)}.",
                notificationTitle = "Nouvel itinéraire proposé",
                urgency = OnboardAnnouncer.Urgency.CRITICAL
            )
        }
    }
}

fun OnboardSession.acceptReplan() {
    val proposal = replan ?: return
    applyReplan(proposal)
}

fun OnboardSession.applyReplan(proposal: ReplanProposal) {
    if (proposal.replaceFrom > legs.size) return
    HapticFeedback.success()
    replan = null

    val keep = proposal.replaceFrom
    walkBackDestination = null
    walkBackJob?.cancel()
    legs = legs.subList(0, keep) + proposal.legs
    val newPaths = paths.subList(0, keep).toMutableList()
    val newAlongs = stopAlongs.subList(0, keep).toMutableList()
    for (leg in proposal.legs) {
        val (path, alongs) = OnboardSession.buildPath(leg)
        newPaths.add(path)
        newAlongs.add(alongs)
    }
    paths = newPaths
    stopAlongs = newAlongs
    val rebuildFrom = if (keep > 0 && (keep - 1) !in reroutedWalks) keep - 1 else keep
    val rebuilt = OnboardSession.buildManeuvers(legs, paths, stationLayouts)
    maneuvers = maneuvers.subList(0, rebuildFrom) + rebuilt.subList(rebuildFrom, rebuilt.size)
    announcedCancellations = announcedCancellations.filter { it < keep }.toMutableSet()
    missedDepartureAlerted = missedDepartureAlerted.filter { it < keep }.toMutableSet()
    announcedStopAlerts = announcedStopAlerts.filter { (it.substringBefore("-").toIntOrNull() ?: 0) < keep }.toMutableSet()
    retargetedLegs.retainAll { it < keep }
    reroutedWalks.retainAll { it < keep }
    announcedRisk = ConnectionRisk.COMFORTABLE
    startLiveFeeds()
    refreshDisruptions()
    loadStationLayouts()

    val staysAboard = phase == OnboardPhase.RIDING && keep == legIndex && legs[keep].tripId == proposal.ridingTripId
    if (staysAboard) {
        nextStopIndex = 1
        announcedStopAlerts = announcedStopAlerts.filter { !it.startsWith("$keep-") }.toMutableSet()
    } else if (keep <= legIndex) {
        enterLeg(keep)
    }
    arrivalDate = proposal.arrival
    evaluate()
    showAlert(
        OnboardAlert(
            severity = OnboardAlert.Severity.SUCCESS,
            symbolName = "checkmark.circle.fill",
            title = "Itinéraire mis à jour",
            message = "Arrivée prévue à ${formatTime(proposal.arrival)}"
        ),
        spoken = null,
        urgency = OnboardAnnouncer.Urgency.GUIDANCE
    )
}

fun OnboardSession.declineReplan() {
    if (replan?.reason == ReplanReason.EARLIER) {
        declinedEarlierLegs.add(legIndex)
    } else {
        declinedReplanLegs.add(legIndex)
    }
    replan = null
}

val OnboardSession.isAtTransfer: Boolean
    get() {
        if ((phase != OnboardPhase.WALKING && phase != OnboardPhase.WAITING) || legIndex <= 0 || nextTransitLeg == null) return false
        return legs.subList(0, legIndex).any { it.isTransit }
    }

val OnboardSession.transferOptionsKey: String
    get() = nextTransitLeg?.let { "${it.first}|${it.second.tripId ?: ""}" } ?: ""

fun OnboardSession.refreshTransferOptions() {
    val next = nextTransitLeg
    val leg = currentLeg
    val destination = legs.lastOrNull()?.to
    if (!isRunning || !isAtTransfer || next == null || leg == null || destination == null) {
        transferOptionsJob?.cancel()
        transferOptionsJob = null
        if (transferOptions.isNotEmpty()) transferOptions = emptyList()
        return
    }
    val nextIndex = next.first
    val departing = transferOptions.filter { option ->
        option.firstTransit?.let { !it.startTime.isAfter(now.plusSeconds(30)) } ?: false
    }
    if (departing.isNotEmpty()) {
        transferOptions = transferOptions.filter { it !in departing }
    }
    val key = transferOptionsKey
    val check = transferOptionsCheck
    if (check != null && check.first == key && now.secondsSince(check.second) < 180) return
    if (transferOptionsJob != null) return
    if (transferOptionsCheck?.first != key && transferOptions.isNotEmpty()) {
        transferOptions = emptyList()
    }

    val stopId = leg.from.stopId
    val location = userLocation?.coordinate
    val origin = when {
        phase == OnboardPhase.WAITING && stopId != null -> RouteOptions.RouteLocation(stopId)
        location != null -> RouteOptions.RouteLocation(location.latitude, location.longitude)
        stopId != null -> RouteOptions.RouteLocation(stopId)
        else -> RouteOptions.RouteLocation(leg.from.lat, leg.from.lon)
    }
    transferOptionsCheck = key to now
    val index = legIndex
    val planned = legs.subList(nextIndex, legs.size).mapNotNull { it.tripId }.toSet()
    val options = savedRouteOptions(origin, routeTarget(destination), now)
    val near = location ?: LatLng(leg.from.lat, leg.from.lon)
    transferOptionsJob = scope.launch {
        val result = runCatching { getRoute(options) }.getOrNull()
        val current = Instant.now()
        var ordered = result?.itineraries.orEmpty()
            .filter { itinerary ->
                if (itinerary.legs.any { it.cancelled } || itinerary.legs.mapNotNull { it.tripId }.toSet() == planned) {
                    return@filter false
                }
                val first = itinerary.legs.firstOrNull { it.isTransit } ?: return@filter true
                first.startTime.isAfter(current.plusSeconds(60))
            }
            .sortedBy { it.endTime }
        var insight: TripSuggestion.Reason? = null
        var context: TripIntelligence.Context? = null
        if (IntelligenceStore.isIntelligentMode && ordered.isNotEmpty()) {
            val live = TripIntelligence.liveContext(near, current, ordered)
            val ranked = TripIntelligence.rank(ordered, live)
            ordered = ranked.map { it.itinerary }
            insight = TripIntelligence.insight(ranked, live)
            context = live
        }
        transferOptionsJob = null
        if (!isRunning || !isAtTransfer || transferOptionsKey != key) return@launch
        transferContext = context
        val seen = mutableSetOf<String>()
        transferOptions = ordered
            .filter { itinerary -> seen.add(itinerary.legs.firstOrNull { it.isTransit }?.tripId ?: "walk") }
            .take(3)
            .mapIndexed { offset, itinerary ->
                ReplanProposal(
                    reason = ReplanReason.ALTERNATIVE,
                    replaceFrom = index,
                    legs = itinerary.legs,
                    arrival = itinerary.endTime,
                    lateBy = itinerary.endTime.secondsSince(arrivalDate),
                    autoApplyAt = null,
                    transfers = itinerary.transfers,
                    insight = if (offset == 0) insight else null,
                    source = itinerary
                )
            }
    }
}

fun OnboardSession.useTransferOption(option: ReplanProposal) {
    if (!isAtTransfer || legIndex < option.replaceFrom) return
    val chosen = option.source
    val context = transferContext
    if (chosen != null && context != null) {
        val others = transferOptions.mapNotNull { it.source }.filter { it.intelligenceSignature != chosen.intelligenceSignature }
        IntelligenceLearner.observe(chosen, others, context, IntelligenceLearner.Signal.TRANSFER_OPTION)
    }
    option.replaceFrom = legIndex
    transferOptionsJob?.cancel()
    transferOptionsJob = null
    transferOptions = emptyList()
    applyReplan(option)
    transferOptionsCheck = transferOptionsKey to now
}

fun routeTarget(destination: Place): RouteOptions.RouteLocation {
    val stopId = destination.stopId
    return if (destination.vertexType == Place.VertexType.TRANSIT && stopId != null) {
        RouteOptions.RouteLocation(stopId)
    } else {
        RouteOptions.RouteLocation(destination.lat, destination.lon)
    }
}

fun OnboardSession.lookForEarlierDeparture() {
    if (!isRunning || phase != OnboardPhase.WAITING || replan != null || isReplanning || earlierJob != null) return
    if (legIndex in declinedEarlierLegs || now.secondsSince(lastEarlierCheckAt) <= 180) return
    val leg = currentLeg ?: return
    if (!leg.isTransit || leg.cancelled || leg.startTime.secondsSince(now) <= 180) return
    val stopId = leg.from.stopId ?: return
    val destination = legs.lastOrNull()?.to ?: return
    val location = usableLocation ?: return
    if (leg.from.coordinate.distanceTo(location.coordinate) >= 120) return

    lastEarlierCheckAt = now
    val index = legIndex
    val plannedDeparture = leg.startTime
    val options = savedRouteOptions(RouteOptions.RouteLocation(stopId), routeTarget(destination), now)
    earlierJob = scope.launch {
        val result = runCatching { getRoute(options) }.getOrNull()
        earlierJob = null
        if (!isRunning || phase != OnboardPhase.WAITING || legIndex != index || replan != null) return@launch
        val current = Instant.now()
        val candidates = result?.itineraries.orEmpty().filter { itinerary ->
            val first = itinerary.legs.firstOrNull { it.isTransit }
            first != null && !first.cancelled &&
                first.startTime.isAfter(current.plusSeconds(45)) &&
                first.startTime.isBefore(plannedDeparture.minusSeconds(60)) &&
                itinerary.endTime.isBefore(arrivalDate.minusSeconds(120))
        }
        val best = candidates.minByOrNull { it.endTime } ?: return@launch
        val transit = best.legs.firstOrNull { it.isTransit } ?: return@launch
        val proposal = ReplanProposal(
            reason = ReplanReason.EARLIER,
            replaceFrom = index,
            legs = best.legs,
            arrival = best.endTime,
            lateBy = best.endTime.secondsSince(arrivalDate),
            autoApplyAt = null
        )
        replan = proposal
        announcer.announce(
            "Départ plus tôt possible : ${transit.spokenLineName}, ${spokenDeparture(transit.startTime)}, arrivée à ${formatTime(proposal.arrival)}.",
            notificationTitle = "Départ plus tôt possible",
            urgency = OnboardAnnouncer.Urgency.NOTICE
        )
    }
}

fun OnboardSession.lookForFasterConnection() {
    if (!isRunning || phase != OnboardPhase.RIDING || replan != null || isReplanning || earlierJob != null) return
    if (legIndex >= legs.size - 1) return
    val leg = currentLeg ?: return
    if (!leg.isTransit) return
    val tripId = leg.tripId ?: return
    val destination = legs.lastOrNull()?.to ?: return
    val stops = leg.allStops
    val exits = stops.indices.filter { it >= max(1, nextStopIndex) }.takeLast(4)
    if (exits.isEmpty()) return
    val index = legIndex
    val target = routeTarget(destination)
    val currentArrival = arrivalDate
    earlierJob = scope.launch {
        val results = exits.mapNotNull { exit ->
            val stop = stops[exit]
            val stopId = stop.stopId ?: return@mapNotNull null
            val time = (stop.arrival ?: stop.scheduledArrival ?: stop.departure ?: Instant.now()).plusSeconds(30)
            val options = savedRouteOptions(RouteOptions.RouteLocation(stopId), target, time)
            exit to async { runCatching { getRoute(options) }.getOrNull() }
        }
        var best: Pair<Int, Itinerary>? = null
        for ((exit, deferred) in results) {
            val trip = deferred.await()
            val arrivalAtExit = stops[exit].arrival ?: stops[exit].scheduledArrival ?: Instant.now()
            for (itinerary in trip?.itineraries.orEmpty()) {
                if (itinerary.startTime.isBefore(arrivalAtExit.minusSeconds(60)) || itinerary.legs.any { it.tripId == tripId }) continue
                if (best == null || itinerary.endTime.isBefore(best.second.endTime)) best = exit to itinerary
            }
        }
        earlierJob = null
        val found = best ?: return@launch
        if (!isRunning || phase != OnboardPhase.RIDING || legIndex != index || replan != null) return@launch
        if (!found.second.endTime.isBefore(currentArrival.minusSeconds(120))) return@launch
        val exitsAtAlight = found.first == stops.size - 1
        val newLegs = found.second.legs.toMutableList()
        if (!exitsAtAlight) {
            val shortened = LegLiveMerger.slice(leg, 0, found.first) ?: return@launch
            newLegs.add(0, shortened)
        }
        val exit = stops[found.first]
        val proposal = ReplanProposal(
            reason = ReplanReason.FASTER,
            replaceFrom = if (exitsAtAlight) index + 1 else index,
            legs = newLegs,
            arrival = found.second.endTime,
            lateBy = found.second.endTime.secondsSince(currentArrival),
            autoApplyAt = null,
            expiresAt = (exit.arrival ?: exit.scheduledArrival)?.minusSeconds(30),
            exitName = placeName(exit),
            ridingTripId = tripId
        )
        replan = proposal
        val next = proposal.nextTransit?.let { ", puis prenez ${it.spokenLineName}" } ?: ""
        announcer.announce(
            "Correspondance plus rapide : descendez à ${proposal.exitName ?: ""}$next, arrivée à ${formatTime(proposal.arrival)}.",
            notificationTitle = "Correspondance plus rapide",
            urgency = OnboardAnnouncer.Urgency.NOTICE
        )
    }
}

fun OnboardSession.boardEarlierVehicle(leg: Leg) {
    val index = legIndex
    if (index in earlierBoardingLegs) return
    earlierBoardingLegs.add(index)
    board(verifiable = false)
    val stopId = leg.from.stopId ?: return
    val plannedTrip = leg.tripId
    val isRail = leg.mode.isMainlineRail
    scope.launch {
        val departures = runCatching {
            getDeparturesForStop(stopId = stopId, time = Instant.now().minusSeconds(15 * 60), numberOfEvents = 30)
        }.getOrNull()
        val current = Instant.now()
        val candidates = departures?.stopTimes.orEmpty()
            .filter { it.tripId != plannedTrip && !it.cancelled }
            .filter { if (isRail) it.mode.isMainlineRail else it.routeShortName == leg.routeShortName }
            .filter { isRail || it.headsign == null || leg.headsign == null || it.headsign == leg.headsign }
            .mapNotNull { stopTime ->
                val departure = stopTime.place.departure ?: stopTime.place.scheduledDeparture ?: return@mapNotNull null
                if (departure.isAfter(current.plusSeconds(60)) || !departure.isAfter(current.minusSeconds(12 * 60))) {
                    return@mapNotNull null
                }
                stopTime.tripId to departure
            }
            .sortedByDescending { it.second }
            .take(3)
        for ((tripId, _) in candidates) {
            val trip = runCatching { getTrip(tripId) }.getOrNull() ?: continue
            if (legIndex != index || phase != OnboardPhase.RIDING) return@launch
            if (LegLiveMerger.merge(legs[index], trip, tripId) != null) {
                retargetCurrentLeg(tripId)
                return@launch
            }
        }
    }
}

fun savedRouteOptions(
    from: RouteOptions.RouteLocation,
    to: RouteOptions.RouteLocation,
    time: Instant
): RouteOptions = RouteOptionsStore.load().copy(
    from = from,
    to = to,
    via = null,
    viaMinimumStay = emptyList(),
    time = time,
    arriveBy = false,
    numItineraries = 5,
    pageCursor = null,
    timetableView = true
)

fun OnboardSession.updateConnectionRisk() {
    val risk = computeConnectionRisk()
    if (risk != connectionRisk) connectionRisk = risk
    if (risk == ConnectionRisk.MISSED) {
        requestReplan(ReplanReason.CONNECTION)
    } else if (risk == ConnectionRisk.COMFORTABLE && replan?.reason == ReplanReason.CONNECTION) {
        replan = null
    }
    if (risk == announcedRisk) return
    val previous = announcedRisk
    announcedRisk = risk
    val next = nextTransitLeg?.second ?: return

    if (risk == ConnectionRisk.MISSED && previous != ConnectionRisk.MISSED) {
        showAlert(
            OnboardAlert(
                severity = OnboardAlert.Severity.CRITICAL,
                symbolName = "arrow.triangle.branch",
                title = "Correspondance compromise",
                message = "${next.spokenLineName.capitalizedFirstLetter} part à ${formatTime(next.startTime)} de ${placeName(next.from)}."
            ),
            spoken = "Attention, votre correspondance avec ${next.spokenLineName} risque d'être manquée.",
            urgency = OnboardAnnouncer.Urgency.CRITICAL
        )
    } else if (risk == ConnectionRisk.TIGHT && previous == ConnectionRisk.COMFORTABLE) {
        showAlert(
            OnboardAlert(
                severity = OnboardAlert.Severity.WARNING,
                symbolName = "hare.fill",
                title = "Correspondance serrée",
                message = "${next.spokenLineName.capitalizedFirstLetter} part à ${formatTime(next.startTime)}. Pressez le pas."
            ),
            spoken = null,
            urgency = OnboardAnnouncer.Urgency.NOTICE
        )
    }
}

fun OnboardSession.computeConnectionRisk(): ConnectionRisk {
    if (phase == OnboardPhase.ARRIVED || phase == OnboardPhase.WAITING) return ConnectionRisk.COMFORTABLE
    val (nextIndex, next) = nextTransitLeg ?: return ConnectionRisk.COMFORTABLE

    val walkBetween = ((legIndex + 1) until nextIndex).sumOf { index ->
        if (legs[index].isTransit) 0.0 else walkTime(index)
    }
    val leg = currentLeg
    val path = currentPath
    val ready = if (phase == OnboardPhase.RIDING && leg != null) {
        currentLegArrival.plusSecondsDouble(walkBetween)
    } else if (path != null && leg != null) {
        now.plusSecondsDouble(remainingWalkTime(leg, path) + walkBetween)
    } else {
        return ConnectionRisk.COMFORTABLE
    }

    val margin = next.startTime.secondsSince(ready)
    if (margin < -30) return ConnectionRisk.MISSED
    if (margin < 120) return ConnectionRisk.TIGHT
    return ConnectionRisk.COMFORTABLE
}

val OnboardSession.walkingPace: Double?
    get() {
        motion.walkingSpeed?.let { return max(0.5, it) }
        val pace = measuredPace ?: return null
        if (paceSamples < 10) return null
        return max(0.5, pace)
    }

fun OnboardSession.walkTime(index: Int): Double {
    val leg = legs[index]
    walkingPace?.let { return paths[index].length / it }
    return leg.duration.toDouble()
}

fun OnboardSession.remainingWalkTime(leg: Leg, path: RoutePath): Double {
    val remaining = max(0.0, path.length - alongInLeg)
    walkingPace?.let { return remaining / it }
    if (path.length <= 0 || leg.duration <= 0) return remaining / walkingSpeed
    return remaining / path.length * leg.duration
}

fun OnboardSession.updateArrival() {
    if (phase == OnboardPhase.ARRIVED) return

    var remaining = max(0.0, (currentPath?.length ?: 0.0) - alongInLeg)
    for (index in (legIndex + 1) until legs.size) remaining += paths[index].length
    remainingDistance = Math.round(remaining / 10) * 10.0

    val lastTransit = legs.indexOfLast { it.isTransit }
    val leg = currentLeg
    val path = currentPath
    val estimate = if (lastTransit >= 0 && lastTransit >= legIndex) {
        val walkAfter = ((lastTransit + 1) until legs.size).sumOf { walkTime(it) }
        val lastArrival = if (lastTransit == legIndex && phase == OnboardPhase.RIDING) currentLegArrival else legs[lastTransit].endTime
        lastArrival.plusSecondsDouble(walkAfter)
    } else if (leg != null && path != null && !leg.isTransit) {
        now.plusSecondsDouble(remainingWalkTime(leg, path))
    } else {
        now.plusSecondsDouble(remaining / walkingSpeed)
    }
    if (kotlin.math.abs(estimate.secondsSince(arrivalDate)) >= 30) arrivalDate = estimate
}

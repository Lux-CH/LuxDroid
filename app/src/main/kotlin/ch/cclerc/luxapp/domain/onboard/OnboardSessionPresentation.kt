package ch.cclerc.luxapp.domain.onboard

import ch.cclerc.luxapp.data.CrowdConsent
import ch.cclerc.luxapp.data.Settings
import ch.cclerc.luxapp.domain.DisruptionManager
import ch.cclerc.luxapp.ui.itinerary.DisruptionGroup
import ch.cclerc.luxapp.ui.itinerary.shortTitle
import ch.cclerc.luxapp.ui.itinerary.summary
import ch.cclerc.luxapp.ui.stop.expanded.getTrackType
import ch.cclerc.luxapp.ui.theme.getLegColor
import ch.cclerc.luxcom.api.getLCBInfo
import ch.cclerc.luxcom.api.sendLCBReport
import ch.cclerc.luxcom.model.Disruption
import ch.cclerc.luxcom.model.feedback.Report
import ch.cclerc.luxcom.model.feedback.ReportAttribute
import ch.cclerc.luxcom.model.trip.Leg
import ch.cclerc.luxcom.relay.RelayClient
import java.time.Instant
import kotlin.math.ceil
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

fun OnboardSession.dismissAlert() {
    alert = null
}

fun OnboardSession.showAlert(
    alert: OnboardAlert,
    spoken: String?,
    urgency: OnboardAnnouncer.Urgency,
    persistent: Boolean = false
) {
    this.alert = alert
    if (spoken != null) {
        announcer.announce(spoken, notificationTitle = alert.title, urgency = urgency)
    }
    alertDismissJob?.cancel()
    alertDismissJob = scope.launch {
        delay(if (persistent) 25_000 else 8_000)
        if (this@showAlert.alert == alert) dismissAlert()
    }
}

fun spokenDeparture(date: Instant): String {
    val minutes = ceil(date.secondsSince(Instant.now()) / 60).toInt()
    if (minutes <= 0) return "départ imminent"
    if (minutes == 1) return "départ dans 1 minute"
    return "départ dans $minutes minutes"
}

fun OnboardSession.startAnnouncement(): String {
    val leg = currentLeg
    return when {
        leg == null -> "C'est parti."
        phase == OnboardPhase.WALKING ->
            "C'est parti. Marchez jusqu'à ${placeName(leg.to, isDestination = legIndex == legs.size - 1)}."
        phase == OnboardPhase.WAITING -> "C'est parti. Prenez ${leg.spokenLineName}, ${spokenDeparture(leg.startTime)}."
        phase == OnboardPhase.RIDING -> "C'est parti. Descendez à ${placeName(leg.to)}."
        else -> "C'est parti."
    }
}

fun OnboardSession.activityState(): OnboardActivityState {
    val leg = currentLeg
    var state = OnboardActivityState(
        phase = OnboardPhase.WALKING,
        title = "",
        subtitle = "",
        symbolName = "figure.walk",
        arrivalDate = arrivalDate,
        progress = legProgress,
        vehicleIsLive = false,
        isUrgent = false
    )

    fun describe(leg: Leg) {
        state = state.copy(line = leg.routeShortName, lineColor = getLegColor(leg), headsign = leg.headsign)
    }

    when (phase) {
        OnboardPhase.ARRIVED -> state = state.copy(
            phase = OnboardPhase.ARRIVED,
            title = "Vous êtes arrivé",
            subtitle = destinationName,
            symbolName = "checkmark",
            progress = 1.0
        )
        OnboardPhase.WALKING -> {
            val maneuver = nextManeuver
            state = when {
                isOffRoute -> state.copy(title = "Recalcul de l'itinéraire…", symbolName = "arrow.triangle.turn.up.right.diamond.fill")
                maneuver != null -> state.copy(title = maneuver.instruction, symbolName = maneuver.symbolName)
                leg != null -> state.copy(
                    title = "Marchez jusqu'à ${placeName(leg.to, isDestination = legIndex == legs.size - 1)}",
                    symbolName = "figure.walk"
                )
                else -> state
            }
            state = state.copy(phase = OnboardPhase.WALKING, distanceMeters = distanceToManeuver)
            val next = nextTransitLeg?.second
            if (next != null) {
                describe(next)
                state = state.copy(
                    targetDate = next.startTime,
                    delayMinutes = next.departureDelayMinutes,
                    subtitle = placeName(next.from)
                )
            } else {
                state = state.copy(subtitle = destinationName)
            }
        }
        OnboardPhase.WAITING -> {
            state = state.copy(phase = OnboardPhase.WAITING)
            if (leg != null) {
                describe(leg)
                state = state.copy(
                    title = placeName(leg.from),
                    subtitle = leg.from.track?.let(::getTrackType) ?: "",
                    symbolName = "tram.fill",
                    targetDate = leg.startTime,
                    delayMinutes = leg.departureDelayMinutes
                )
            }
        }
        OnboardPhase.RIDING -> {
            state = state.copy(phase = OnboardPhase.RIDING)
            if (leg != null) {
                describe(leg)
                val stops = leg.allStops
                val segment = if (followsTimetable || hasWeakGPS) currentSegmentTimes else null
                state = state.copy(
                    title = placeName(leg.to),
                    subtitle = if (stopsRemaining > 1 && nextStopIndex < stops.size) {
                        "Prochain arrêt : ${stops[nextStopIndex].name}"
                    } else {
                        "Descendez au prochain arrêt"
                    },
                    symbolName = "tram.fill",
                    targetDate = currentLegArrival,
                    delayMinutes = currentLegDelayMinutes,
                    stopsRemaining = stopsRemaining,
                    totalStops = stops.size,
                    passedStops = minOf(stops.size, nextStopIndex),
                    progress = stopProgress,
                    segmentStart = segment?.first,
                    segmentEnd = segment?.second,
                    fromName = placeName(leg.from),
                    toName = placeName(leg.to),
                    isUrgent = stopsRemaining <= 1,
                    vehicleIsLive = isSharingPosition && crowdStatus != null
                )
            }
        }
    }

    state.targetDate?.let { target ->
        val seconds = target.secondsSince(now)
        state = state.copy(countdownMinutes = if (seconds >= 60) ceil(seconds / 60).toInt() else 0)
    }

    if ((phase == OnboardPhase.WALKING || phase == OnboardPhase.WAITING) && approachingVehicle != null) {
        state = state.copy(vehicleIsLive = true, vehicleDistanceMeters = approachingVehicleDistance)
    }
    return state
}

fun OnboardSession.setSharing(enabled: Boolean) {
    Settings.onboardCrowdConsent = if (enabled) CrowdConsent.GRANTED else CrowdConsent.DECLINED
    isSharingPosition = enabled
    if (!enabled) {
        crowdStatus = null
        RelayClient.shared.stopOnboardReports()
    } else {
        lastCrowdReportAt = Instant.EPOCH
    }
}

val OnboardSession.canReportRide: Boolean
    get() {
        if (!Settings.crowdbackAllowed || phase != OnboardPhase.RIDING) return false
        val leg = currentLeg ?: return false
        return leg.tripId != null && leg.routeShortName != null
    }

fun OnboardSession.reportRide(attribute: ReportAttribute, level: Int) {
    if (!canReportRide) return
    val leg = currentLeg ?: return
    val tripId = leg.tripId ?: return
    val line = leg.routeShortName ?: return
    val coordinate = userLocation?.coordinate ?: return
    rideReports[attribute] = level
    val report = Report(tripId, line, coordinate.latitude, coordinate.longitude, attribute, level)
    scope.launch {
        runCatching { sendLCBReport(report) }
        refreshRideInfo()
    }
}

fun OnboardSession.dismissCrowdPrompt() {
    showsCrowdPrompt = false
}

fun OnboardSession.startRideInfo() {
    rideInfoJob?.cancel()
    rideInfo = null
    if (!Settings.crowdbackAllowed) return
    rideInfoJob = scope.launch {
        while (isActive) {
            refreshRideInfo()
            delay(120_000)
        }
    }
}

suspend fun OnboardSession.refreshRideInfo() {
    val leg = currentLeg ?: return
    if (!leg.isTransit) return
    val tripId = leg.tripId ?: return
    val line = leg.routeShortName ?: return
    val coordinate = userLocation?.coordinate ?: leg.from.coordinate
    val info = runCatching { getLCBInfo(tripId, line, coordinate.latitude, coordinate.longitude) }.getOrNull()
    if (currentLeg?.tripId != tripId) return
    rideInfo = info
}

fun OnboardSession.scheduleCrowdPrompt() {
    crowdPromptJob?.cancel()
    val index = legIndex
    if (!Settings.crowdbackAllowed || index in promptedLegs) return
    crowdPromptJob = scope.launch {
        delay(60_000)
        if (legIndex != index || !canReportRide || rideReports[ReportAttribute.CROWD] != null || stopsRemaining <= 1) {
            return@launch
        }
        promptedLegs.add(index)
        showsCrowdPrompt = true
        delay(45_000)
        dismissCrowdPrompt()
    }
}

fun OnboardSession.reportCrowdPosition(leg: Leg, offsetOK: Boolean) {
    if (!isSharingPosition || !offsetOK) return
    val tripId = leg.tripId
    if (tripId.isNullOrEmpty()) return
    val location = usableLocation ?: return
    if (location.horizontalAccuracy > (if (leg.mode.isMainlineRail) 20.0 else 50.0)) return
    if (leg.mode.isMainlineRail && !hasTrainGPS) return
    if (now.secondsSince(lastCrowdReportAt) < crowdReportInterval) return
    lastCrowdReportAt = now
    val board = boarding?.takeIf { it.legIndex == legIndex }
    RelayClient.shared.reportOnboardPosition(
        tripId = tripId,
        latitude = location.coordinate.latitude,
        longitude = location.coordinate.longitude,
        accuracy = location.horizontalAccuracy,
        speed = location.speed.takeIf { it >= 0 },
        boardStopId = board?.stopId,
        boardedAt = board?.at,
        timestamp = location.timestamp
    )
}

fun OnboardSession.startCrowdAcks() {
    crowdAckJob = scope.launch {
        RelayClient.shared.crowdAcks.collect { ack ->
            if (ack.tripId != currentLeg?.tripId || phase != OnboardPhase.RIDING) return@collect
            val watched = ack.watched
            if (watched != null && watched != crowdWatched) {
                crowdWatched = watched
                if (watched) lastCrowdReportAt = Instant.EPOCH
            }
            val state: CrowdState? = when (ack.status) {
                "ok" -> CrowdState.Contributing(ack.riders ?: 1, ack.delay ?: 0)
                "learning" -> CrowdState.Learning
                "unverified" -> {
                    ack.likelyTripId?.let { retargetCurrentLeg(it) }
                    CrowdState.Unverified
                }
                else -> null
            }
            if (state != null) crowdStatus = state
        }
    }
}

data class LegDisruption(
    val legIndex: Int,
    val leg: Leg,
    val disruption: Disruption
) {
    val id: String get() = disruption.id

    override fun equals(other: Any?): Boolean =
        other is LegDisruption && other.legIndex == legIndex && other.disruption == disruption

    override fun hashCode(): Int = 31 * legIndex + disruption.hashCode()
}

val OnboardSession.endsWithButton: Boolean
    get() = phase == OnboardPhase.WAITING || (phase != OnboardPhase.ARRIVED && legDisruptions.isNotEmpty())

val OnboardSession.disruptionGroups: List<DisruptionGroup>
    get() = legDisruptions.groupBy { it.legIndex }
        .toSortedMap()
        .map { (index, items) -> DisruptionGroup("$index", items.firstOrNull()?.leg, items.map { it.disruption }) }

fun OnboardSession.updateDisruptions(all: List<Disruption>) {
    knownDisruptions = all
    refreshDisruptions()
}

fun OnboardSession.refreshDisruptions() {
    if (phase == OnboardPhase.ARRIVED) {
        if (legDisruptions.isNotEmpty()) legDisruptions = emptyList()
        return
    }
    val known = knownDisruptions ?: return
    val seen = mutableSetOf<String>()
    val found = mutableListOf<LegDisruption>()
    for (index in legIndex until legs.size) {
        if (!legs[index].isTransit) continue
        for (disruption in DisruptionManager.matching(known, legs[index])) {
            if (seen.add(disruption.id)) found.add(LegDisruption(index, legs[index], disruption))
        }
    }
    if (found != legDisruptions) legDisruptions = found

    val announced = announcedDisruptionIds
    if (announced == null) {
        announcedDisruptionIds = seen
        return
    }
    found.firstOrNull { it.id !in announced }?.let { fresh ->
        val line = fresh.leg.routeShortName ?: fresh.leg.spokenLineName
        showAlert(
            OnboardAlert(
                severity = OnboardAlert.Severity.WARNING,
                symbolName = "exclamationmark.triangle.fill",
                title = "$line · ${fresh.disruption.shortTitle}",
                message = fresh.disruption.summary
            ),
            spoken = "Perturbation sur ${fresh.leg.spokenLineName}.",
            urgency = OnboardAnnouncer.Urgency.NOTICE,
            persistent = true
        )
    }
    announcedDisruptionIds = announced + seen
}

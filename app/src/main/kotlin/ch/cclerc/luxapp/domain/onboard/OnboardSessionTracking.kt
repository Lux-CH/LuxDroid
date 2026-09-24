package ch.cclerc.luxapp.domain.onboard

import ch.cclerc.luxapp.core.HapticFeedback
import ch.cclerc.luxapp.domain.map.LatLng
import ch.cclerc.luxapp.domain.map.PolylineCodec
import ch.cclerc.luxapp.domain.map.VehicleVisualisation
import ch.cclerc.luxapp.domain.map.distanceTo
import ch.cclerc.luxapp.domain.map.epochSeconds
import ch.cclerc.luxcom.model.trip.Leg
import java.time.Instant
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ch.cclerc.luxcom.api.getRoute
import ch.cclerc.luxcom.model.TransportationMode
import ch.cclerc.luxcom.model.trip.RouteOptions

fun OnboardSession.handle(fix: OnboardFix, date: Instant = Instant.now()) {
    if (!isRunning || fix.horizontalAccuracy < 0) return
    now = date
    lastFixAt = now
    hasWeakGPS = fix.horizontalAccuracy > usableAccuracy
    userLocation = fix
    motion.record(fix)
    if (phase == OnboardPhase.WALKING && fix.horizontalAccuracy <= 30 && fix.speed >= 0.4 && fix.speed <= 3) {
        measuredPace = measuredPace?.let { it * 0.92 + fix.speed * 0.08 } ?: fix.speed
        paceSamples += 1
    }
    updateHeading()

    if (!hasResolvedStart && fix.horizontalAccuracy <= usableAccuracy) {
        hasResolvedStart = true
        resolveStartingPoint(fix)
    }
    evaluate()
    updateNearbyStations()
}

fun OnboardSession.handle(reading: OnboardHeading) {
    if (reading.accuracy < 0) return
    compassHeading = reading.trueHeading
    compassAccuracy = reading.accuracy
    compassAt = reading.timestamp
    updateHeading()
}

fun OnboardSession.updateHeading() {
    val compassIsReliable = compassHeading != null && compassAccuracy >= 0 && compassAccuracy <= 25 &&
        Instant.now().secondsSince(compassAt) < 5
    val location = userLocation
    val target: Double? = if (phase == OnboardPhase.RIDING) {
        if (!followsTimetable && location != null && location.speed > 2.5 && location.course >= 0) {
            location.course
        } else {
            currentPath?.bearing(alongInLeg) ?: compassHeading
        }
    } else if (compassIsReliable) {
        compassHeading
    } else if (location != null && location.speed > 0.8 && location.course >= 0 &&
        location.courseAccuracy >= 0 && location.courseAccuracy <= 30
    ) {
        location.course
    } else if (phase == OnboardPhase.WALKING && !isOffRoute && location != null) {
        currentPath?.bearing(alongInLeg)
    } else {
        compassHeading
    }
    if (target == null) return
    val current = heading
    if (current != null && abs(Angle360.delta(current, target)) < 1) return
    heading = target
}

fun OnboardSession.resolveStartingPoint(fix: OnboardFix) {
    val point = fix.coordinate
    for ((index, leg) in legs.withIndex()) {
        val path = paths[index]
        val projection = path.project(point) ?: continue

        if (leg.isTransit) {
            val alongs = stopAlongs[index]
            val boardAlong = alongs.firstOrNull() ?: 0.0
            val alightAlong = alongs.lastOrNull() ?: path.length
            val railRiding = leg.mode.isMainlineRail && projection.offset < 500 &&
                now.isAfter(leg.startTime) && now.isBefore(leg.endTime)
            val riding = railRiding || (projection.offset < 60 &&
                projection.along > boardAlong + 80 &&
                projection.along < alightAlong - 30 &&
                now.isAfter(leg.startTime.minusSeconds(120)) &&
                now.isBefore(leg.endTime.plusSeconds(600)))
            if (riding) {
                if (index != legIndex) enterLeg(index, announce = false)
                board(announce = false, verifiable = false)
                return
            }
            val atStop = leg.from.coordinate.distanceTo(point) < 80
            if (atStop && now.isBefore(leg.startTime.plusSeconds(180))) {
                if (index != legIndex) enterLeg(index, announce = false)
                return
            }
        } else if (projection.offset < 40) {
            if (index != legIndex) enterLeg(index, announce = false)
            return
        }
    }
}

fun OnboardSession.evaluate() {
    when (phase) {
        OnboardPhase.WALKING -> evaluateWalking()
        OnboardPhase.WAITING -> evaluateWaiting()
        OnboardPhase.RIDING -> evaluateRiding()
        OnboardPhase.ARRIVED -> Unit
    }
    updateConnectionRisk()
    updateArrival()
    updateApproachingVehicle()
    liveActivity.update(activityState())
}

val OnboardSession.usableLocation: OnboardFix?
    get() {
        val location = userLocation ?: return null
        if (location.horizontalAccuracy > usableAccuracy) return null
        val fixAt = lastFixAt ?: return null
        if (now.secondsSince(fixAt) >= 45) return null
        return location
    }

fun OnboardSession.evaluateWalking() {
    val leg = currentLeg ?: return
    val path = currentPath ?: return
    val location = usableLocation
    val projection = location?.let { path.project(it.coordinate, alongInLeg) }
    if (location == null || projection == null) {
        walkOffset = Double.POSITIVE_INFINITY
        updateManeuvers()
        return
    }

    walkOffset = projection.offset
    alongInLeg = projection.along
    val tolerance = max(offRouteDistance, location.horizontalAccuracy)
    if (projection.offset > tolerance) {
        offRouteStreak += 1
    } else if (projection.offset < tolerance * 0.7) {
        offRouteStreak = 0
        if (isOffRoute) isOffRoute = false
    }
    if (offRouteStreak >= 3 && !isOffRoute && !isInStation) {
        isOffRoute = true
        reroute()
    }

    updateManeuvers()
    speakManeuverIfNeeded()

    val reachedEnd = location.coordinate.distanceTo(leg.to.coordinate) < max(arrivalRadius, location.horizontalAccuracy * 0.6) ||
        (!isOffRoute && path.length - alongInLeg < arrivalRadius)
    if (reachedEnd) {
        completeLeg()
        return
    }

    if (legIndex + 1 < legs.size && legs[legIndex + 1].isTransit) {
        val next = legs[legIndex + 1]
        if (next.from.coordinate.distanceTo(location.coordinate) < stopRadius) {
            completeLeg()
        }
    }
}

fun OnboardSession.updateManeuvers() {
    val list = maneuvers.getOrNull(legIndex).orEmpty()
    val upcoming = list.filter { it.along > alongInLeg + 3 }
    nextManeuver = upcoming.firstOrNull()
    val distance = upcoming.firstOrNull()?.let { it.along - alongInLeg }
        ?: currentPath?.let { max(0.0, it.length - alongInLeg) }
    distanceToManeuver = distance?.let { (it * 2).roundToInt() / 2.0 }
    val first = upcoming.firstOrNull()
    followingManeuver = if (first != null && upcoming.size > 1 && upcoming[1].along - first.along < 60) upcoming[1] else null
}

fun OnboardSession.speakManeuverIfNeeded() {
    val maneuver = nextManeuver ?: return
    val distance = distanceToManeuver ?: return
    val key = "$legIndex-${maneuver.along.toInt()}"
    if (distance <= 18 && "$key-now" !in spokenManeuvers) {
        spokenManeuvers.add("$key-now")
        spokenManeuvers.add("$key-soon")
        announcer.announce(maneuver.shortInstruction, urgency = OnboardAnnouncer.Urgency.GUIDANCE)
    } else if (distance <= 90 && distance > 35 && "$key-soon" !in spokenManeuvers) {
        spokenManeuvers.add("$key-soon")
        val rounded = ((distance / 10).roundToInt() * 10)
        announcer.announce(
            "Dans $rounded mètres, ${maneuver.instruction.lowercasedFirstLetter}",
            urgency = OnboardAnnouncer.Urgency.GUIDANCE
        )
    }
}

fun OnboardSession.evaluateWaiting() {
    val leg = currentLeg ?: return
    val path = currentPath ?: return
    alongInLeg = 0.0

    if (leg.mode.isMainlineRail) {
        if (now.isAfter(leg.startTime.plusSeconds(20))) {
            board()
            return
        }
        val location = usableLocation
        val projection = location?.let { path.project(it.coordinate, 0.0) }
        if (location != null && projection != null) {
            if (leg.from.coordinate.distanceTo(location.coordinate) < 250) {
                lastAtBoardingStop = now
            }
            val boardAlong = stopAlongs[legIndex].firstOrNull() ?: 0.0
            val leaving = projection.offset < 60 && projection.along > boardAlong + 200 && location.speed > 7
            val recentlyAtStop = lastAtBoardingStop?.let { now.secondsSince(it) < 600 } == true
            if (leaving && now.isBefore(leg.startTime.minusSeconds(120)) && recentlyAtStop) {
                boardEarlierVehicle(leg)
            }
        }
        return
    }

    val location = usableLocation
    val projection = location?.let { path.project(it.coordinate, 0.0) }
    if (location != null && projection != null) {
        if (leg.from.coordinate.distanceTo(location.coordinate) < max(40.0, location.horizontalAccuracy)) {
            lastAtBoardingStop = now
        }
        val boardAlong = stopAlongs[legIndex].firstOrNull() ?: 0.0
        val movingAway = projection.offset < 50 && projection.along > boardAlong + 70 && location.speed > 2
        if (movingAway && now.isAfter(leg.startTime.minusSeconds(120))) {
            board()
            return
        }
        if (movingAway && lastAtBoardingStop?.let { now.secondsSince(it) < 600 } == true) {
            boardEarlierVehicle(leg)
            return
        }

        val stillAtStop = leg.from.coordinate.distanceTo(location.coordinate) < 100
        if (stillAtStop && now.isAfter(leg.startTime.plusSeconds(180)) && legIndex !in missedDepartureAlerted) {
            missedDepartureAlerted.add(legIndex)
            showAlert(
                OnboardAlert(
                    severity = OnboardAlert.Severity.WARNING,
                    symbolName = "exclamationmark.triangle.fill",
                    title = "Départ manqué ?",
                    message = "${leg.spokenLineName.capitalizedFirstLetter} est parti. Si vous êtes à bord, touchez « Je suis à bord »."
                ),
                spoken = "Il semble que vous ayez manqué ${leg.spokenLineName}.",
                urgency = OnboardAnnouncer.Urgency.NOTICE
            )
            requestReplan(ReplanReason.MISSED_DEPARTURE)
        }
    } else if (now.isAfter(leg.startTime.plusSeconds(90))) {
        board(verifiable = false)
    }
}

fun OnboardSession.evaluateRiding() {
    val leg = currentLeg ?: return
    val path = currentPath ?: return
    val alongs = stopAlongs[legIndex]
    if (alongs.size < 2) return

    if (leg.ridesByTimetable) {
        evaluateRidingTrain(leg, alongs)
        return
    }

    var locatedByGPS = false
    val location = usableLocation
    val projection = location?.let { path.project(it.coordinate, alongInLeg) }
    if (location != null && projection != null && projection.offset < max(80.0, location.horizontalAccuracy)) {
        val clearlyBehind = location.horizontalAccuracy <= 30 && alongInLeg - projection.along > 40
        alongInLeg = if (clearlyBehind) projection.along else max(alongInLeg - 15, projection.along)
        locatedByGPS = true
    } else {
        alongInLeg = max(alongInLeg, estimatedAlongByTime(leg, alongs))
    }

    dwellingStopIndex = alongs.indexOfFirst { abs(it - alongInLeg) <= stopRadius }.takeIf { it >= 0 }
    val next = alongs.indexOfFirst { it > alongInLeg + stopRadius * 0.7 }.takeIf { it >= 0 } ?: (alongs.size - 1)
    if (next != nextStopIndex) nextStopIndex = max(1, next)
    announceStopsIfNeeded(leg)

    if (locatedByGPS) {
        reportCrowdPosition(leg, offsetOK = true)
        updatePositionDelay(leg, alongs)
        checkDelay(leg, legIndex)
    } else if (positionDelay != null && now.secondsSince(positionDelayAt) > 60) {
        positionDelay = null
    }

    val alightAlong = alongs.lastOrNull() ?: path.length
    val gpsLocation = usableLocation
    if (locatedByGPS && gpsLocation != null) {
        val atAlight = gpsLocation.coordinate.distanceTo(leg.to.coordinate) < 45
        if (atAlight && (gpsLocation.speed < 1.5 || alongInLeg >= alightAlong - 10)) {
            completeLeg()
            return
        }
        if (alongInLeg > alightAlong + 150) {
            showAlert(
                OnboardAlert(
                    severity = OnboardAlert.Severity.CRITICAL,
                    symbolName = "exclamationmark.octagon.fill",
                    title = "Vous avez dépassé votre arrêt",
                    message = "Descendez au prochain arrêt et suivez le nouvel itinéraire à pied."
                ),
                spoken = "Vous avez dépassé votre arrêt. Descendez au prochain arrêt.",
                urgency = OnboardAnnouncer.Urgency.CRITICAL
            )
            completeLeg(announce = false)
            return
        }
    } else if (now.isAfter(leg.endTime.plusSeconds(45))) {
        completeLeg()
    }
}

fun OnboardSession.evaluateRidingTrain(leg: Leg, alongs: List<Double>) {
    val timetable = estimatedAlongByTime(leg, alongs)
    var gpsAlong: Double? = null
    val location = userLocation
    val projection = location?.let { currentPath?.project(it.coordinate, alongInLeg) }
    if (location != null && location.horizontalAccuracy >= 0 && location.horizontalAccuracy <= 25 &&
        now.secondsSince(location.timestamp) < 4 && projection != null &&
        projection.offset < 40 && abs(projection.along - timetable) < 2500
    ) {
        gpsAlong = projection.along
        if (location.timestamp.isAfter(lastTrainFix)) {
            lastTrainFix = location.timestamp
            trainFix = projection.along to max(0.0, location.speed)
            trainGPSStreak += 1
            trainGPSAt = now
        }
    } else if (now.secondsSince(trainGPSAt) >= 15) {
        trainGPSStreak = 0
    }
    val locked = trainGPSStreak >= 3 && now.secondsSince(trainGPSAt) < 15
    if (locked != hasTrainGPS) hasTrainGPS = locked

    if (locked) {
        if (gpsAlong != null && alongInLeg - gpsAlong > 60) {
            alongInLeg = gpsAlong
        } else {
            val along = gpsAlong ?: (trainFix.first + trainFix.second * now.secondsSince(trainGPSAt))
            alongInLeg = max(alongInLeg - 30, along)
        }
    } else {
        alongInLeg = timetable
    }
    dwellingStopIndex = alongs.indexOfFirst { abs(it - alongInLeg) <= stopRadius }.takeIf { it >= 0 }
    val next = alongs.indexOfFirst { it > alongInLeg + stopRadius * 0.7 }.takeIf { it >= 0 } ?: (alongs.size - 1)
    if (next != nextStopIndex) nextStopIndex = max(1, next)
    announceStopsIfNeeded(leg)
    updateHeading()

    val alightAlong = alongs.lastOrNull() ?: alongInLeg
    val fix = userLocation
    if (locked && fix != null) {
        val stoppedAtAlight = leg.to.coordinate.distanceTo(fix.coordinate) < 120 && fix.speed >= 0 && fix.speed < 1.5
        if (stoppedAtAlight || alongInLeg > alightAlong + 400) completeLeg()
    } else if (now.isAfter(leg.endTime.plusSeconds(15))) {
        completeLeg()
    }
}

val OnboardSession.estimatedAlightTime: Instant?
    get() {
        if (phase != OnboardPhase.RIDING || followsTimetable) return null
        val delay = positionDelay ?: return null
        if (now.secondsSince(positionDelayAt) >= 60) return null
        val leg = currentLeg ?: return null
        val scheduled = leg.to.scheduledArrival ?: leg.to.scheduledDeparture ?: return null
        val estimate = scheduled.plusSecondsDouble(delay)
        return if (estimate.isAfter(now)) estimate else now
    }

val OnboardSession.currentLegArrival: Instant
    get() = estimatedAlightTime ?: currentLeg?.endTime ?: now

val OnboardSession.currentLegDelayMinutes: Int
    get() {
        val delay = positionDelay
        if (estimatedAlightTime != null && delay != null) return (delay / 60).roundToInt()
        return currentLeg?.arrivalDelayMinutes ?: 0
    }

fun OnboardSession.updatePositionDelay(leg: Leg, alongs: List<Double>) {
    if (alongInLeg <= (alongs.firstOrNull() ?: 0.0) + 60) return
    val stops = leg.allStops
    fun arrival(index: Int): Instant? = stops[index].scheduledArrival ?: stops[index].scheduledDeparture
    fun departure(index: Int): Instant? = stops[index].scheduledDeparture ?: stops[index].scheduledArrival

    var delay: Double? = null
    val stop = dwellingStopIndex
    val stopArrive = stop?.let(::arrival)
    val stopLeave = stop?.let(::departure)
    if (stop != null && stopArrive != null && stopLeave != null) {
        delay = when {
            now.isBefore(stopArrive) -> now.secondsSince(stopArrive)
            !now.isAfter(stopLeave) -> 0.0
            else -> now.secondsSince(stopLeave)
        }
    } else {
        val segment = (0 until alongs.size - 1).firstOrNull { alongInLeg >= alongs[it] && alongInLeg < alongs[it + 1] }
        if (segment != null) {
            val leave = departure(segment)
            val arrive = arrival(segment + 1)
            if (leave != null && arrive != null) {
                val span = alongs[segment + 1] - alongs[segment]
                val fraction = if (span > 0) (alongInLeg - alongs[segment]) / span else 0.0
                val scheduled = leave.plusSecondsDouble(arrive.secondsSince(leave) * fraction)
                delay = now.secondsSince(scheduled)
            }
        }
    }
    val value = delay ?: return
    if (value <= -300 || value >= 5400) return
    positionDelay = positionDelay?.let { it * 0.7 + value * 0.3 } ?: value
    positionDelayAt = now
}

fun OnboardSession.estimatedAlongByTime(leg: Leg, alongs: List<Double>): Double {
    keyFrameAlong(leg)?.let { return it }
    val stops = leg.allStops
    val times = stops.mapIndexed { index, stop ->
        (if (index == 0) stop.departure ?: stop.arrival else stop.arrival ?: stop.departure)
            ?: leg.startTime.plusSecondsDouble(index.toDouble() / max(1, stops.size - 1) * leg.duration)
    }
    val first = times.firstOrNull() ?: return alongs.firstOrNull() ?: 0.0
    if (!now.isAfter(first)) return alongs.firstOrNull() ?: 0.0
    for (index in 0 until times.size - 1) {
        val departure = stops[index].departure ?: times[index]
        val arrival = times[index + 1]
        if (now.isBefore(departure)) return alongs[index]
        if (now.isBefore(arrival)) {
            val fraction = if (arrival.isAfter(departure)) now.secondsSince(departure) / arrival.secondsSince(departure) else 1.0
            return alongs[index] + (alongs[index + 1] - alongs[index]) * fraction
        }
    }
    return alongs.lastOrNull() ?: 0.0
}

private fun OnboardSession.keyFrameAlong(leg: Leg): Double? {
    val times = leg.allStops.mapNotNull { (it.arrival ?: it.departure)?.epochSecond }.sum()
    val key = "$legIndex|${leg.tripId ?: ""}|$times"
    if (legKeyFrames?.first != key) {
        legKeyFrames = key to VehicleVisualisation.calculateKeyFrames(leg, leg.legGeometry.points, 1e6)
    }
    val frames = legKeyFrames?.second ?: return null
    if (frames.isEmpty()) return null
    val path = currentPath ?: return null
    val position = VehicleVisualisation.interpolatePosition(now.epochSeconds(), frames) ?: return null
    return path.project(position, alongInLeg)?.along
}

fun OnboardSession.announceStopsIfNeeded(leg: Leg) {
    val stops = leg.allStops
    val alightName = placeName(leg.to)
    val remaining = stopsRemaining
    val key = "$legIndex"

    val alongs = stopAlongs.getOrNull(legIndex).orEmpty()
    if (alongs.size >= 2 && phase == OnboardPhase.RIDING && "$key-final" !in announcedStopAlerts) {
        val alight = alongs[alongs.size - 1]
        val previous = alongs[alongs.size - 2]
        if (alight - previous > 300 && alongInLeg > previous && alight - alongInLeg <= 20) {
            announcedStopAlerts.add("$key-final")
            announcer.speak("Descendez maintenant, $alightName.")
        }
    }

    if (remaining == 1 && "$key-next" !in announcedStopAlerts) {
        announcedStopAlerts.add("$key-next")
        announcedStopAlerts.add("$key-two")
        announcer.announce(
            "Descendez au prochain arrêt, $alightName.",
            notificationTitle = "Descendez au prochain arrêt",
            urgency = OnboardAnnouncer.Urgency.CRITICAL
        )
    } else if (remaining == 2 && stops.size >= 4 && "$key-two" !in announcedStopAlerts) {
        announcedStopAlerts.add("$key-two")
        showAlert(
            OnboardAlert(
                severity = OnboardAlert.Severity.INFO,
                symbolName = "bell.fill",
                title = "Descente dans 2 arrêts",
                message = alightName
            ),
            spoken = "Préparez-vous à descendre dans 2 arrêts, à $alightName.",
            urgency = OnboardAnnouncer.Urgency.NOTICE
        )
    }
}

fun OnboardSession.enterLeg(index: Int, announce: Boolean = true) {
    if (index !in legs.indices) return
    val leg = legs[index]
    val newPhase = if (leg.isTransit) {
        if (leg.interlineWithPreviousLeg == true && index > 0) OnboardPhase.RIDING else OnboardPhase.WAITING
    } else {
        OnboardPhase.WALKING
    }
    legIndex = index
    phase = newPhase
    alongInLeg = 0.0
    offRouteStreak = 0
    isOffRoute = false
    nextStopIndex = 1
    dwellingStopIndex = null
    hasTrainGPS = false
    trainGPSStreak = 0
    crowdStatus = null
    rideReports.clear()
    positionDelay = null
    showsCrowdPrompt = false
    crowdPromptJob?.cancel()
    lastAtBoardingStop = null
    if (boarding?.legIndex != index) boarding = null

    if (leg.isTransit) {
        startRideInfo()
        if (announce) {
            val direction = leg.headsign?.let { " direction $it" } ?: ""
            announcer.announce(
                "Prenez ${leg.spokenLineName}$direction, ${spokenDeparture(leg.startTime)}.",
                notificationTitle = "Prochaine étape",
                urgency = OnboardAnnouncer.Urgency.NOTICE
            )
        }
    } else {
        rideInfoJob?.cancel()
        rideInfo = null
        if (paths[index].length < 15 && index < legs.size - 1) {
            completeLeg(announce)
            return
        }
        updateManeuvers()
        if (announce) {
            val target = placeName(leg.to, isDestination = index == legs.size - 1)
            announcer.announce(
                "Marchez jusqu'à $target.",
                notificationTitle = "Prochaine étape",
                urgency = OnboardAnnouncer.Urgency.GUIDANCE
            )
        }
    }
    refreshDisruptions()
    liveActivity.update(activityState())
}

fun OnboardSession.board(announce: Boolean = true, verifiable: Boolean = true) {
    val leg = currentLeg ?: return
    if (!leg.isTransit) return
    val stopId = leg.from.stopId
    if (verifiable && stopId != null) {
        boarding = OnboardSession.Boarding(legIndex, stopId, lastAtBoardingStop ?: now)
    }
    phase = OnboardPhase.RIDING
    nextStopIndex = 1
    scheduleCrowdPrompt()
    if (announce) {
        val count = leg.allStops.size - 1
        announcer.announce(
            "Vous êtes à bord. Descendez à ${placeName(leg.to)} dans $count arrêts.",
            urgency = OnboardAnnouncer.Urgency.GUIDANCE
        )
    }
}

fun OnboardSession.completeLeg(announce: Boolean = true) {
    if (phase == OnboardPhase.RIDING && isSharingPosition) {
        ch.cclerc.luxcom.relay.RelayClient.shared.stopOnboardReports()
    }
    if (legIndex >= legs.size - 1) {
        arrive()
    } else {
        enterLeg(legIndex + 1, announce)
    }
}

fun OnboardSession.arrive() {
    if (phase == OnboardPhase.ARRIVED) return
    phase = OnboardPhase.ARRIVED
    alongInLeg = currentPath?.length ?: 0.0
    arrivalDate = Instant.now()
    remainingDistance = 0.0
    HapticFeedback.success()
    announcer.announce(
        "Vous êtes arrivé à $destinationName.",
        notificationTitle = "Vous êtes arrivé",
        urgency = OnboardAnnouncer.Urgency.NOTICE
    )
    liveActivity.update(activityState())
    arrivalJob?.cancel()
    arrivalJob = scope.launch {
        delay(30_000)
        if (phase != OnboardPhase.ARRIVED) return@launch
        stopTracking()
        liveActivity.end(activityState())
    }
}

fun OnboardSession.reroute() {
    val leg = currentLeg ?: return
    if (leg.isTransit) return
    val location = usableLocation ?: return
    if (now.secondsSince(lastRerouteAt) <= rerouteCooldown) return
    lastRerouteAt = now
    val index = legIndex

    rerouteJob?.cancel()
    rerouteJob = scope.launch {
        val coordinates = walkingRoute(location.coordinate, leg.to.coordinate) ?: return@launch
        if (legIndex != index || phase != OnboardPhase.WALKING) return@launch
        val path = RoutePath(coordinates)
        if (path.isEmpty) return@launch
        paths = paths.toMutableList().also { it[index] = path }
        maneuvers = maneuvers.toMutableList().also { it[index] = WalkManeuverBuilder.maneuvers(emptyList(), path) }
        reroutedWalks.add(index)
        alongInLeg = 0.0
        offRouteStreak = 0
        spokenManeuvers.clear()
        isOffRoute = false
        announcer.announce("Nouvel itinéraire à pied.", urgency = OnboardAnnouncer.Urgency.GUIDANCE)
        evaluate()
    }
}

private suspend fun walkingRoute(from: LatLng, to: LatLng): List<LatLng>? = runCatching {
    val options = ch.cclerc.luxapp.domain.search.RouteOptionsStore.load().copy(
        from = RouteOptions.RouteLocation(from.latitude, from.longitude),
        to = RouteOptions.RouteLocation(to.latitude, to.longitude),
        via = null,
        viaMinimumStay = emptyList(),
        time = Instant.now(),
        arriveBy = false,
        numItineraries = 1,
        pageCursor = null
    )
    val walk = getRoute(options).direct.firstOrNull { itinerary ->
        itinerary.legs.all { it.mode == TransportationMode.WALK }
    } ?: return@runCatching null
    walk.legs.flatMap { PolylineCodec.decode(it.legGeometry.points, 1e6) }.takeIf { it.size >= 2 }
}.getOrNull()

fun OnboardSession.catchUpWithVehicle() {
    if (!isRunning) return
    if (phase != OnboardPhase.WALKING && phase != OnboardPhase.WAITING) return
    if (!motion.isInVehicle) return
    val running = legs.indices.firstOrNull { index ->
        index >= legIndex && legs[index].isTransit &&
            !now.isBefore(legs[index].startTime.minusSeconds(60)) &&
            !now.isAfter(legs[index].endTime.plusSeconds(120))
    } ?: return
    hasResolvedStart = true
    if (running != legIndex) enterLeg(running, announce = false)
    board(verifiable = false)
}

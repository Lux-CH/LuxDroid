package ch.cclerc.luxapp.domain.onboard

import ch.cclerc.luxcom.api.getRoute
import ch.cclerc.luxcom.model.trip.LegGeometry
import ch.cclerc.luxcom.model.TransportationMode
import ch.cclerc.luxapp.core.HapticFeedback
import ch.cclerc.luxapp.domain.map.LatLng
import ch.cclerc.luxapp.domain.map.VehicleVisualisation
import ch.cclerc.luxapp.domain.map.distanceTo
import ch.cclerc.luxapp.domain.map.epochSeconds
import ch.cclerc.luxapp.domain.station.StationWalk
import ch.cclerc.luxapp.ui.itinerary.formatTime
import ch.cclerc.luxcom.api.getTrip
import ch.cclerc.luxcom.model.Place
import ch.cclerc.luxcom.model.trip.Itinerary
import ch.cclerc.luxcom.model.trip.Leg
import ch.cclerc.luxcom.relay.RelayClient
import ch.cclerc.luxcom.relay.RelayLiveFeed
import java.time.Instant
import kotlin.math.abs
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.launch

fun OnboardSession.startLiveFeeds() {
    liveFeeds.values.forEach { it.stop() }
    liveFeeds.clear()
    vehicleJobs.values.forEach { it.cancel() }
    vehicleJobs.clear()
    liveVehicles.clear()
    liveTracks.clear()
    tripKeyFrames.clear()
    for (index in legs.indices) startLiveFeed(index)
}

fun OnboardSession.startLiveFeed(index: Int) {
    tripKeyFrames.remove(index)
    tripLegs.remove(index)
    liveTracks.remove(index)
    liveFeeds[index]?.stop()
    vehicleJobs[index]?.cancel()
    val leg = legs[index]
    val tripId = leg.tripId
    if (!leg.isTransit || tripId.isNullOrEmpty()) return
    announcedDelays[index] = leg.departureDelayMinutes
    val feed = RelayLiveFeed<Itinerary>(scope)
    feed.start(
        fallbackInterval = 15.seconds,
        stream = { RelayClient.shared.trip(tripId) },
        fallbackFetch = { runCatching { getTrip(tripId) }.getOrNull() },
        onUpdate = { trip -> apply(trip, index) }
    )
    liveFeeds[index] = feed

    vehicleJobs[index] = scope.launch {
        RelayClient.shared.vehicle(tripId).collect { vehicle ->
            liveVehicles[index] = vehicle
            trackVehicle(vehicle, index)
            updateApproachingVehicle()
        }
    }
}

fun OnboardSession.retargetCurrentLeg(tripId: String) {
    val index = legIndex
    if (index in retargetedLegs || index !in legs.indices || legs[index].tripId == tripId) return
    retargetedLegs.add(index)
    scope.launch {
        val trip = runCatching { getTrip(tripId) }.getOrNull() ?: return@launch
        if (legIndex != index) return@launch
        val corrected = LegLiveMerger.merge(legs[index], trip, tripId) ?: return@launch
        val earlier = corrected.scheduledStartTime.isBefore(legs[index].scheduledStartTime)
        legs = legs.toMutableList().also { it[index] = corrected }
        scheduleOffset = null
        positionDelay = null
        crowdStatus = null
        startLiveFeed(index)
        showAlert(
            OnboardAlert(
                severity = OnboardAlert.Severity.INFO,
                symbolName = "arrow.triangle.2.circlepath",
                title = if (earlier) "Vous êtes dans le véhicule précédent" else "Vous êtes dans le véhicule suivant",
                message = "Horaires mis à jour : arrivée à ${placeName(corrected.to)} à ${formatTime(corrected.endTime)}"
            ),
            spoken = null,
            urgency = OnboardAnnouncer.Urgency.NOTICE
        )
        evaluate()
        if (earlier && phase == OnboardPhase.RIDING) lookForFasterConnection()
    }
}

fun OnboardSession.updateApproachingVehicle() {
    val index = nextTransitLeg?.first
    val vehicle = index?.let { liveVehicles[it] }
    if ((phase != OnboardPhase.WALKING && phase != OnboardPhase.WAITING) || vehicle == null || !vehicle.isFresh) {
        if (approachingVehicle != null) approachingVehicle = null
        return
    }
    approachingVehicle = vehicle
}

val OnboardSession.approachingVehicleDistance: Double?
    get() {
        val vehicle = approachingVehicleCoordinate(Instant.now()) ?: return null
        val leg = nextTransitLeg?.second ?: return null
        return leg.from.coordinate.distanceTo(vehicle)
    }

fun OnboardSession.trackVehicle(vehicle: RelayClient.CrowdVehicle?, index: Int) {
    if (vehicle == null) {
        liveTracks.remove(index)
        return
    }
    val now = Instant.now()
    val track = liveTracks[index]
    if (track != null) {
        track.update(vehicle, now)
    } else {
        val trip = tripPaths[index] ?: return
        LiveVehicleTrack.create(trip.first, vehicle, now)?.let { liveTracks[index] = it }
    }
}

fun OnboardSession.approachingVehicleCoordinate(date: Instant): LatLng? {
    val vehicle = approachingVehicle ?: return null
    val fallback = LatLng(vehicle.lat, vehicle.lon)
    val index = nextTransitLeg?.first ?: return fallback
    val track = liveTracks[index] ?: return fallback
    return track.coordinate(date, tripPaths[index]?.second) ?: fallback
}

fun OnboardSession.scheduledWalkerAlong(date: Instant): Double? {
    if (phase != OnboardPhase.WALKING) return null
    val leg = currentLeg ?: return null
    val path = currentPath ?: return null
    if (leg.duration <= 0) return null
    val nextDeparture = if (legIndex + 1 < legs.size && legs[legIndex + 1].isTransit) legs[legIndex + 1].startTime else null
    val end = nextDeparture?.let { if (it.isBefore(leg.endTime)) it else leg.endTime } ?: leg.endTime
    val start = end.minusSeconds(leg.duration.toLong())
    val fraction = (date.secondsSince(start) / maxOf(1.0, end.secondsSince(start))).coerceIn(0.0, 1.0)
    return fraction * path.length
}

fun OnboardSession.scheduledWalkerCoordinate(date: Instant): LatLng? =
    scheduledWalkerAlong(date)?.let { currentPath?.coordinate(it) }

fun OnboardSession.remainingApproach(date: Instant): Pair<Int, List<LatLng>>? {
    if (phase != OnboardPhase.WALKING && phase != OnboardPhase.WAITING) return null
    val index = nextTransitLeg?.first ?: return null
    val (tripPath, boardAlong) = tripPaths[index] ?: return null
    val vehicle = approachingVehicleCoordinate(date) ?: estimatedVehicleCoordinate(date) ?: return null
    val projection = tripPath.project(vehicle, boardAlong) ?: return null
    if (projection.along >= boardAlong - 10) return null
    val coordinates = tripPath.slice(projection.along, boardAlong)
    return if (coordinates.size >= 2) index to coordinates else null
}

fun OnboardSession.estimatedVehicleCoordinate(date: Instant): LatLng? {
    if (phase != OnboardPhase.WALKING && phase != OnboardPhase.WAITING) return null
    if (approachingVehicle != null) return null
    val index = nextTransitLeg?.first ?: return null
    val frames = tripKeyFrames[index]?.first ?: return null
    val position = VehicleVisualisation.interpolatePosition(date.epochSeconds(), frames) ?: return null
    val trip = tripPaths[index] ?: return position
    val projection = trip.first.project(position, trip.second) ?: return position
    return if (projection.along < trip.second - 10) position else null
}

fun OnboardSession.updateEstimates() {
    val walkerAlong = scheduledWalkerAlong(now)
    val showsWalker = userLocation != null && walkerAlong != null && abs(walkerAlong - alongInLeg) > 15
    if (showsWalker != isBehindOrAheadOfSchedule) isBehindOrAheadOfSchedule = showsWalker
    val hasEstimate = estimatedVehicleCoordinate(now) != null
    if (hasEstimate != hasEstimatedVehicle) hasEstimatedVehicle = hasEstimate
}

fun OnboardSession.apply(trip: Itinerary, index: Int) {
    val tripLeg = trip.legs.firstOrNull { it.tripId != null } ?: trip.legs.firstOrNull()
    val lastFrames = tripKeyFrames[index]?.second ?: Instant.EPOCH
    if (tripLeg != null && Instant.now().secondsSince(lastFrames) > 20) {
        tripKeyFrames[index] = VehicleVisualisation.calculateKeyFrames(tripLeg, tripLeg.legGeometry.points, 1e6) to Instant.now()
        tripLegs[index] = tripLeg
        if (index in legs.indices) {
            val tripPath = RoutePath.encoded(tripLeg.legGeometry.points, 1e6)
            tripPath.project(legs[index].from.coordinate)?.let { tripPaths[index] = tripPath to it.along }
        }
    }
    if (!isRunning || index !in legs.indices) return
    val merged = LegLiveMerger.merge(legs[index], trip) ?: return
    val previous = legs[index]
    legs = legs.toMutableList().also { it[index] = merged }
    if (merged.from.track != previous.from.track || merged.to.track != previous.to.track) {
        val rebuilt = OnboardSession.buildManeuvers(legs, paths, stationLayouts)
        val updated = maneuvers.toMutableList()
        for (walk in listOf(index - 1, index + 1)) {
            if (walk in updated.indices && walk !in reroutedWalks) updated[walk] = rebuilt[walk]
        }
        maneuvers = updated
    }

    if (index < legIndex) return
    checkCancellation(merged, index)
    checkDelay(merged, index)
    checkTrackChange(previous, merged, index)
    evaluate()
}

fun OnboardSession.checkTrackChange(previous: Leg, leg: Leg, index: Int) {
    fun track(place: Place): String? = (place.track ?: place.scheduledTrack)?.trim()?.takeIf { it.isNotEmpty() }
    val name = leg.spokenLineName.capitalizedFirstLetter
    val boarded = index == legIndex && phase == OnboardPhase.RIDING

    val oldFrom = track(previous.from)
    val newFrom = track(leg.from)
    val oldTo = track(previous.to)
    val newTo = track(leg.to)
    if (!boarded && leg.startTime.isAfter(now) && oldFrom != null && newFrom != null && oldFrom != newFrom) {
        HapticFeedback.warning()
        val title = "Changement de voie"
        val message = "$name part de ${StationWalk.trackPhrase(newFrom)} au lieu de ${StationWalk.trackPhrase(oldFrom)}"
        showAlert(
            OnboardAlert(OnboardAlert.Severity.CRITICAL, "exclamationmark.arrow.triangle.2.circlepath", title, message),
            spoken = "$title. $message.",
            urgency = OnboardAnnouncer.Urgency.CRITICAL,
            persistent = true
        )
    } else if (leg.endTime.isAfter(now) && index + 1 < legs.size && oldTo != null && newTo != null && oldTo != newTo) {
        val title = "Arrivée sur une autre voie"
        val message = "$name arrive sur ${StationWalk.trackPhrase(newTo)} au lieu de ${StationWalk.trackPhrase(oldTo)}"
        showAlert(
            OnboardAlert(OnboardAlert.Severity.WARNING, "arrow.triangle.swap", title, message),
            spoken = "$title. $message.",
            urgency = OnboardAnnouncer.Urgency.NOTICE
        )
    }
}

fun OnboardSession.checkCancellation(leg: Leg, index: Int) {
    if (!leg.cancelled || index in announcedCancellations) return
    announcedCancellations.add(index)
    showAlert(
        OnboardAlert(
            severity = OnboardAlert.Severity.CRITICAL,
            symbolName = "xmark.octagon.fill",
            title = "${leg.spokenLineName.capitalizedFirstLetter} est supprimé",
            message = "Cherchez un autre itinéraire depuis l'onglet Trajets."
        ),
        spoken = "Attention, ${leg.spokenLineName} est supprimé.",
        urgency = OnboardAnnouncer.Urgency.CRITICAL,
        persistent = true
    )
    requestReplan(ReplanReason.CANCELLED)
}

fun OnboardSession.checkDelay(leg: Leg, index: Int) {
    val boarded = index == legIndex && phase == OnboardPhase.RIDING
    val delay = if (boarded) currentLegDelayMinutes else leg.departureDelayMinutes
    val previous = announcedDelays[index] ?: 0
    if (!(abs(delay - previous) >= 2 || (delay <= 0 && previous >= 2))) return
    announcedDelays[index] = delay

    val name = leg.spokenLineName.capitalizedFirstLetter
    val title: String
    val severity: OnboardAlert.Severity
    if (delay >= 2) {
        title = "$name a $delay min de retard"
        severity = OnboardAlert.Severity.WARNING
    } else if (delay <= -2) {
        title = "$name a ${-delay} min d'avance"
        severity = OnboardAlert.Severity.WARNING
    } else {
        title = "$name est de nouveau à l'heure"
        severity = OnboardAlert.Severity.SUCCESS
    }
    val message = if (boarded) {
        "Arrivée à ${placeName(leg.to)} à ${formatTime(currentLegArrival)}"
    } else {
        "Départ de ${placeName(leg.from)} à ${formatTime(leg.startTime)}"
    }
    showAlert(
        OnboardAlert(severity, if (delay >= 2) "clock.badge.exclamationmark.fill" else "clock.fill", title, message),
        spoken = if (boarded) "$title. $message." else "$title. Départ de ${placeName(leg.from)}, ${spokenDeparture(leg.startTime)}.",
        urgency = OnboardAnnouncer.Urgency.NOTICE
    )
}

fun OnboardSession.checkStillOnBoard() {
    val watch = alightWatch ?: return
    val trip = tripPaths[watch.legIndex]
    if (now.secondsSince(watch.since) >= 180 || legIndex > watch.legIndex + 1 || phase == OnboardPhase.RIDING || trip == null) {
        alightWatch = null
        return
    }
    val (tripPath, boardAlong) = trip
    val location = usableLocation
    val alightOnTrip = tripPath.project(watch.leg.to.coordinate, boardAlong)
    val projection = if (location != null && alightOnTrip != null) tripPath.project(location.coordinate, alightOnTrip.along) else null
    if (location == null || location.horizontalAccuracy > 30 || alightOnTrip == null || projection == null) {
        alightWatch = watch.copy(onBoardSince = null)
        return
    }
    val vehicleSpeed = if (watch.leg.mode.isMainlineRail) 6.0 else 4.0
    val onBoard = projection.offset < 25 && projection.along > alightOnTrip.along + 15 && location.speed > vehicleSpeed
    val since = watch.onBoardSince
    when {
        !onBoard -> alightWatch = watch.copy(onBoardSince = null)
        since == null -> alightWatch = watch.copy(onBoardSince = now)
        now.secondsSince(since) >= 7.5 -> {
            alightWatch = null
            resumeRide(watch, projection.along - alightOnTrip.along)
        }
    }
}

private fun OnboardSession.resumeRide(watch: OnboardSession.AlightWatch, pastAlight: Double) {
    val index = watch.legIndex
    if (index !in legs.indices) return
    val tripLeg = tripLegs[index] ?: return
    val stops = tripLeg.allStops
    fun position(place: Place, lower: Int): Int? = stops.indices.firstOrNull { candidate ->
        candidate > lower && (place.stopId?.let { stops[candidate].stopId == it } ?: (stops[candidate].name == place.name))
    }
    val boardIndex = position(watch.leg.from, -1) ?: return
    val alightIndex = position(watch.leg.to, boardIndex) ?: return
    if (alightIndex + 1 >= stops.size) return
    val extended = LegLiveMerger.slice(tripLeg, boardIndex, alightIndex + 1) ?: return
    val walkBackTarget = walkBackDestination?.takeIf { index == legs.lastIndex - 1 }
        ?: watch.leg.to.takeIf { index == legs.lastIndex && it.vertexType == Place.VertexType.TRANSIT }

    arrivalJob?.cancel()
    legs = legs.toMutableList().also { it[index] = extended }
    val (path, alongs) = OnboardSession.buildPath(extended)
    paths = paths.toMutableList().also { it[index] = path }
    stopAlongs = stopAlongs.toMutableList().also { it[index] = alongs }
    announcedStopAlerts = announcedStopAlerts.filterNot { it.startsWith("$index-") }.toMutableSet()
    if (walkBackTarget != null) setWalkBack(walkBackTarget, extended.to, extended.endTime)
    enterLeg(index, announce = false)
    board(announce = false, verifiable = false)
    val alightAlong = if (alongs.size >= 2) alongs[alongs.size - 2] else 0.0
    alongInLeg = kotlin.math.min(path.length, alightAlong + kotlin.math.max(0.0, pastAlight))
    announcedStopAlerts.add("$index-next")
    announcedStopAlerts.add("$index-two")
    val next = placeName(extended.to)
    showAlert(
        OnboardAlert(
            severity = OnboardAlert.Severity.CRITICAL,
            symbolName = "exclamationmark.octagon.fill",
            title = "Vous avez dépassé votre arrêt",
            message = "Descendez au prochain arrêt, $next."
        ),
        spoken = "Vous avez dépassé votre arrêt. Descendez au prochain arrêt, $next.",
        urgency = OnboardAnnouncer.Urgency.CRITICAL
    )
    evaluate()
}

private fun OnboardSession.setWalkBack(destination: Place, start: Place, date: Instant) {
    val distance = start.coordinate.distanceTo(destination.coordinate)
    val duration = kotlin.math.max(60, (distance / walkingSpeed).toInt())
    val end = date.plusSeconds(duration.toLong())
    val to = Place(
        name = "END",
        lat = destination.lat,
        lon = destination.lon,
        level = destination.level,
        arrival = end,
        scheduledArrival = end,
        vertexType = Place.VertexType.NORMAL
    )
    val walk = Leg(
        mode = TransportationMode.WALK,
        from = start,
        to = to,
        duration = duration,
        startTime = date,
        endTime = end,
        scheduledStartTime = date,
        scheduledEndTime = end,
        realTime = false,
        distance = distance,
        legGeometry = LegGeometry(points = "", length = 0)
    )
    val (path, _) = OnboardSession.buildPath(walk)
    val walkManeuvers = WalkManeuverBuilder.maneuvers(emptyList(), path)
    if (walkBackDestination != null && legs.lastOrNull()?.mode == TransportationMode.WALK) {
        legs = legs.toMutableList().also { it[it.lastIndex] = walk }
        paths = paths.toMutableList().also { it[it.lastIndex] = path }
        stopAlongs = stopAlongs.toMutableList().also { it[it.lastIndex] = emptyList() }
        maneuvers = maneuvers.toMutableList().also { it[it.lastIndex] = walkManeuvers }
    } else {
        legs = legs + walk
        paths = paths + path
        stopAlongs = stopAlongs + listOf(emptyList())
        maneuvers = maneuvers + listOf(walkManeuvers)
    }
    walkBackDestination = destination
    fetchWalkBackRoute(legs.lastIndex, start, destination)
}

private fun OnboardSession.fetchWalkBackRoute(index: Int, start: Place, destination: Place) {
    walkBackJob?.cancel()
    walkBackJob = scope.launch {
        val route = walkingLeg(start.coordinate, destination.coordinate) ?: return@launch
        if (walkBackDestination != destination || index !in legs.indices || legs[index].isTransit) return@launch
        val current = legs[index]
        val path = RoutePath.encoded(route.legGeometry.points, 1e6)
        if (path.isEmpty) return@launch
        val duration = kotlin.math.max(60, route.duration)
        val end = current.startTime.plusSeconds(duration.toLong())
        legs = legs.toMutableList().also {
            it[index] = current.copy(
                duration = duration,
                endTime = end,
                scheduledEndTime = end,
                to = current.to.copy(arrival = end, scheduledArrival = end),
                distance = route.distance ?: current.distance,
                legGeometry = route.legGeometry,
                steps = route.steps
            )
        }
        paths = paths.toMutableList().also { it[index] = path }
        maneuvers = maneuvers.toMutableList().also {
            it[index] = WalkManeuverBuilder.maneuvers(route.steps.orEmpty(), path)
        }
        reroutedWalks.add(index)
        if (legIndex == index) {
            alongInLeg = 0.0
            offRouteStreak = 0
            spokenManeuvers.clear()
            isOffRoute = false
            evaluate()
        }
    }
}

internal suspend fun walkingLeg(from: LatLng, to: LatLng): Leg? = runCatching {
    val options = ch.cclerc.luxapp.domain.search.RouteOptionsStore.load().copy(
        from = ch.cclerc.luxcom.model.trip.RouteOptions.RouteLocation(from.latitude, from.longitude),
        to = ch.cclerc.luxcom.model.trip.RouteOptions.RouteLocation(to.latitude, to.longitude),
        via = null,
        viaMinimumStay = emptyList(),
        time = Instant.now(),
        arriveBy = false,
        numItineraries = 1,
        pageCursor = null
    )
    getRoute(options).direct
        .firstOrNull { itinerary -> itinerary.legs.size == 1 && itinerary.legs[0].mode == TransportationMode.WALK }
        ?.legs?.first()
}.getOrNull()

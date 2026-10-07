package ch.cclerc.luxapp.domain.onboard

import ch.cclerc.luxapp.domain.intelligence.IntelligenceProfile
import ch.cclerc.luxapp.domain.intelligence.IntelligenceStore
import ch.cclerc.luxapp.domain.map.LatLng
import ch.cclerc.luxapp.domain.map.distanceTo
import ch.cclerc.luxapp.domain.search.RouteOptionsStore
import ch.cclerc.luxapp.domain.station.StationLayoutStore
import ch.cclerc.luxapp.domain.station.coordinate
import ch.cclerc.luxcom.model.PedestrianProfile
import ch.cclerc.luxcom.model.trip.Leg
import ch.cclerc.luxcom.relay.RelayClient
import ch.cclerc.luxcom.station.StationLayout
import kotlin.math.min
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

data class PlatformAdvice(
    val alightName: String,
    val exitSector: String,
    val exitKind: StationLayout.Access.Kind?,
    val boardSector: String?,
    val coach: String?,
    val busyTrain: Boolean
)

private val StationLayout.Sector.coordinate: LatLng get() = LatLng(lat, lon)

fun OnboardSession.refreshPlatformAdvice() {
    var target = ""
    var plan: Pair<Int, Leg>? = null
    if (IntelligenceStore.isIntelligentMode) {
        val leg = currentLeg
        if (phase == OnboardPhase.RIDING && leg != null && leg.isTransit) {
            plan = legIndex to leg
        } else if (phase == OnboardPhase.WALKING || phase == OnboardPhase.WAITING) {
            plan = nextTransitLeg
        }
    }
    plan?.second?.let { leg ->
        val tripId = leg.tripId
        val alight = leg.to.stopId
        if (leg.mode.isMainlineRail && tripId != null && alight != null) {
            target = "$tripId|$alight|${leg.to.track ?: leg.to.scheduledTrack ?: ""}|${phase == OnboardPhase.RIDING}|${formation?.coaches?.size ?: 0}"
        }
    }
    if (target == platformAdviceTarget) return
    val sameLeg = target.split("|").take(3) == platformAdviceTarget.split("|").take(3)
    platformAdviceTarget = target
    platformAdviceJob?.cancel()
    if (!sameLeg && platformAdvice != null) platformAdvice = null
    if (plan == null || target.isEmpty()) {
        if (platformAdvice != null) platformAdvice = null
        return
    }

    val (index, leg) = plan
    val tripId = leg.tripId ?: ""
    val alightStopId = leg.to.stopId ?: ""
    val alightName = placeName(leg.to)
    val goal = exitGoal(index)
    val wantsElevator = RouteOptionsStore.load().pedestrianProfile == PedestrianProfile.WHEELCHAIR
    val boardFormation = if (phase == OnboardPhase.RIDING) null else formation
    val avoidsCrowds = IntelligenceStore.profile.crowd != IntelligenceProfile.Crowd.INDIFFERENT

    platformAdviceJob = scope.launch {
        if (goal == null) return@launch
        val layout = StationLayoutStore.layout(alightStopId) ?: return@launch
        val track = layout.track(leg.to.track ?: leg.to.scheduledTrack, alightStopId) ?: return@launch
        if (track.sectors.isEmpty()) return@launch

        val nearPlatform = layout.access.orEmpty().filter { access ->
            (!wantsElevator || access.kind == StationLayout.Access.Kind.ELEVATOR) &&
                track.sectors.any { it.coordinate.distanceTo(access.coordinate) < 45 }
        }
        val exit = nearPlatform.minByOrNull { it.coordinate.distanceTo(goal) }
        val exitPoint = exit?.coordinate ?: goal
        val exitSector = track.sectors.minByOrNull { it.coordinate.distanceTo(exitPoint) }?.s ?: return@launch

        var boardSector: String? = null
        var coachNumber: String? = null
        if (boardFormation != null && boardFormation.coaches.isNotEmpty()) {
            val alightFormation = runCatching { RelayClient.shared.formation(tripId, alightStopId).firstOrNull() }.getOrNull()
            if (alightFormation != null) {
                val usable = alightFormation.coaches.filter { !it.isLocomotive && !it.closed && it.s == exitSector }
                val coach = usable.firstOrNull { !it.isFirstClass && !it.isRestaurant } ?: usable.firstOrNull()
                val number = coach?.n
                val boardCoach = number?.let { n -> boardFormation.coaches.firstOrNull { it.n == n } }
                if (boardCoach != null) {
                    boardSector = boardCoach.s
                    coachNumber = number
                }
            }
        }

        val occupancy = boardFormation?.occupancy?.let { it.second ?: it.first } ?: 0
        val advice = PlatformAdvice(
            alightName = alightName,
            exitSector = exitSector,
            exitKind = exit?.kind,
            boardSector = boardSector,
            coach = coachNumber,
            busyTrain = avoidsCrowds && occupancy >= 2
        )
        if (platformAdviceTarget != target) return@launch
        platformAdvice = advice
    }
}

private fun OnboardSession.exitGoal(index: Int): LatLng? {
    val next = index + 1
    if (next >= legs.size) return null
    if (legs[next].isTransit) return LatLng(legs[next].from.lat, legs[next].from.lon)
    val path = paths[next]
    if (path.isEmpty) return LatLng(legs[next].to.lat, legs[next].to.lon)
    val along = min(120.0, path.length)
    val segment = path.cumulative.indexOfFirst { it >= along }
    if (segment < 0) return path.coordinates.lastOrNull()
    return path.coordinates[segment]
}

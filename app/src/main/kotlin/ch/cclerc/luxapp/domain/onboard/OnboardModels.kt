package ch.cclerc.luxapp.domain.onboard

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import ch.cclerc.luxapp.domain.map.LatLng
import ch.cclerc.luxapp.domain.map.distanceTo
import ch.cclerc.luxapp.domain.station.StationWalk
import ch.cclerc.luxapp.domain.station.coordinate
import ch.cclerc.luxapp.ui.theme.LuxColors
import ch.cclerc.luxcom.model.Place
import ch.cclerc.luxcom.model.TransportationMode
import ch.cclerc.luxcom.model.trip.Direction
import ch.cclerc.luxcom.model.trip.Leg
import ch.cclerc.luxcom.model.trip.StepInstruction
import ch.cclerc.luxcom.station.StationLayout
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

enum class OnboardPhase { WALKING, WAITING, RIDING, ARRIVED }

@Immutable
data class WalkManeuver(
    val symbolName: String,
    val instruction: String,
    val shortInstruction: String,
    val along: Double,
    val isLevelChange: Boolean = false
)

@Immutable
class OnboardAlert(
    val severity: Severity,
    val symbolName: String,
    val title: String,
    val message: String?
) {
    val id: String = UUID.randomUUID().toString()

    enum class Severity {
        INFO, SUCCESS, WARNING, CRITICAL;

        fun color(colors: LuxColors): Color = when (this) {
            INFO -> colors.systemBlue
            SUCCESS -> colors.systemGreen
            WARNING -> colors.systemOrange
            CRITICAL -> colors.systemRed
        }
    }

    override fun equals(other: Any?): Boolean = other is OnboardAlert && other.id == id
    override fun hashCode(): Int = id.hashCode()
}

sealed interface CrowdState {
    data object Learning : CrowdState
    data class Contributing(val riders: Int, val delaySeconds: Int) : CrowdState
    data object Unverified : CrowdState
}

enum class ConnectionRisk { COMFORTABLE, TIGHT, MISSED }

val Leg.ridesByTimetable: Boolean
    get() = mode.isMainlineRail || mode == TransportationMode.SUBWAY || mode == TransportationMode.METRO

val Leg.isTransit: Boolean
    get() = mode != TransportationMode.WALK && mode != TransportationMode.BIKE &&
        mode != TransportationMode.CAR && mode != TransportationMode.RENTAL &&
        mode != TransportationMode.CAR_PARKING

val Leg.allStops: List<Place>
    get() = listOf(from) + intermediateStops.orEmpty() + listOf(to)

val Leg.departureDelayMinutes: Int
    get() {
        if (!realTime) return 0
        return ((startTime.toEpochMilli() - scheduledStartTime.toEpochMilli()) / 60_000.0).roundToInt()
    }

val Leg.arrivalDelayMinutes: Int
    get() {
        if (!realTime) return 0
        return ((endTime.toEpochMilli() - scheduledEndTime.toEpochMilli()) / 60_000.0).roundToInt()
    }

val Leg.spokenLineName: String
    get() {
        val line = routeShortName ?: headsign ?: ""
        val kind = when (mode) {
            TransportationMode.TRAM -> "le tram"
            TransportationMode.FERRY -> "le bateau"
            TransportationMode.SUBWAY, TransportationMode.METRO -> "le métro"
            TransportationMode.FUNICULAR -> "le funiculaire"
            TransportationMode.BUS, TransportationMode.COACH -> "le bus"
            else -> if (mode.isRail) "le train" else "la ligne"
        }
        return if (line.isEmpty()) kind else "$kind $line"
    }

val String.capitalizedFirstLetter: String get() = take(1).uppercase() + drop(1)
val String.lowercasedFirstLetter: String get() = take(1).lowercase() + drop(1)

object WalkManeuverBuilder {

    private fun stepStart(step: StepInstruction): LatLng? {
        val precision = 10.0.pow(if (step.polyline.precision > 0) step.polyline.precision else 7)
        return RoutePath.encoded(step.polyline.points, precision).coordinates.firstOrNull()
    }

    fun maneuvers(
        steps: List<StepInstruction>,
        path: RoutePath,
        station: StationWalk? = null,
        access: List<StationLayout.Access> = emptyList()
    ): List<WalkManeuver> {
        if (path.isEmpty) return emptyList()
        if (station != null) return stationManeuvers(steps, path, station, access)

        val named = mutableListOf<Pair<Double, String>>()
        val explicit = mutableListOf<WalkManeuver>()
        val located = steps.mapNotNull { step -> stepStart(step)?.let { step to it } }
        val positions = path.projectSequence(located.map { it.second })
        for ((index, entry) in located.withIndex()) {
            val step = entry.first
            val along = positions[index]
            val street = step.streetName.trim()
            if (street.isNotEmpty()) named.add(along to street)
            val changesLevel = step.fromLevel != step.toLevel
            val isInformative = step.relativeDirection !in setOf(Direction.continueStraight, Direction.depart) || changesLevel
            if (!isInformative || along <= 3) continue
            val direction = if (changesLevel && step.relativeDirection == Direction.continueStraight) {
                Direction.stairs
            } else {
                step.relativeDirection
            }
            explicit.add(
                WalkManeuver(
                    symbolName = symbol(direction),
                    instruction = instruction(direction, street, step.exit),
                    shortInstruction = instruction(direction, "", step.exit),
                    along = along
                )
            )
        }

        val turns = geometricTurns(path).mapNotNull { (turnAlong, direction) ->
            if (explicit.any { abs(it.along - turnAlong) < 15 }) return@mapNotNull null
            val street = named.firstOrNull { it.first >= turnAlong - 8 && it.first <= turnAlong + 40 }?.second ?: ""
            WalkManeuver(
                symbolName = symbol(direction),
                instruction = instruction(direction, street, ""),
                shortInstruction = instruction(direction, "", ""),
                along = turnAlong
            )
        }
        return (explicit + turns).sortedBy { it.along }
    }

    private class LevelChange(val along: Double, val up: Boolean, val direction: Direction)

    private class Transition(val along: Double, val direction: Direction, val range: Pair<Double, Double>)

    private fun stationManeuvers(
        steps: List<StepInstruction>,
        path: RoutePath,
        walk: StationWalk,
        access: List<StationLayout.Access>
    ): List<WalkManeuver> {
        val located = steps.mapNotNull { step -> stepStart(step)?.let { step to it } }
        val positions = path.projectSequence(located.map { it.second })

        val changes = mutableListOf<LevelChange>()
        val turns = mutableListOf<WalkManeuver>()
        var level: Double? = null
        var transition: Transition? = null

        fun otherEnd(range: Pair<Double, Double>, known: Double): Double =
            if (abs(range.first - known) > abs(range.second - known)) range.first else range.second

        for ((index, entry) in located.withIndex()) {
            val step = entry.first
            val along = positions[index]
            if (step.fromLevel != step.toLevel) {
                if (transition == null) transition = Transition(along, step.relativeDirection, step.fromLevel to step.toLevel)
                continue
            }
            val previous = level ?: transition?.let { otherEnd(it.range, step.toLevel) }
            if (previous != null && step.toLevel != previous) {
                val viaAlong = transition?.along ?: along
                val viaDirection = transition?.direction ?: step.relativeDirection
                changes.add(LevelChange(max(viaAlong, 1.0), step.toLevel > previous, viaDirection))
            }
            level = step.toLevel
            transition = null
            if (walk.kind != StationWalk.Kind.TRANSFER &&
                step.relativeDirection !in setOf(Direction.continueStraight, Direction.depart, Direction.stairs, Direction.elevator) &&
                along > 3
            ) {
                val street = step.streetName.trim()
                turns.add(
                    WalkManeuver(
                        symbolName = symbol(step.relativeDirection),
                        instruction = instruction(step.relativeDirection, street, step.exit),
                        shortInstruction = instruction(step.relativeDirection, "", step.exit),
                        along = along
                    )
                )
            }
        }

        val pendingTransition = transition
        val lastLevel = level
        if (pendingTransition != null && lastLevel != null) {
            val end = otherEnd(pendingTransition.range, lastLevel)
            if (end != lastLevel) {
                changes.add(LevelChange(max(pendingTransition.along, 1.0), end > lastLevel, pendingTransition.direction))
            }
        }

        val levelManeuvers = changes.mapIndexed { index, change ->
            val isLast = index == changes.lastIndex
            val target: String? = when (walk.kind) {
                StationWalk.Kind.LEAVING -> if (isLast) "la sortie" else null
                StationWalk.Kind.TRANSFER, StationWalk.Kind.ENTERING ->
                    if (isLast) walk.toTrack?.let(StationWalk::trackPhrase) else null
            }
            val means = levelMeans(change.direction, path.coordinate(change.along), access)
            val text = levelInstruction(means, change.up, target)
            WalkManeuver(
                symbolName = levelSymbol(means, change.up),
                instruction = text,
                shortInstruction = text,
                along = change.along,
                isLevelChange = true
            )
        }

        val result = (levelManeuvers + turns.filter { turn ->
            levelManeuvers.none { abs(it.along - turn.along) < 15 }
        }).toMutableList()
        if (walk.kind != StationWalk.Kind.TRANSFER) {
            for ((turnAlong, direction) in geometricTurns(path)) {
                if (result.any { abs(it.along - turnAlong) < 15 }) continue
                result.add(
                    WalkManeuver(
                        symbolName = symbol(direction),
                        instruction = instruction(direction, "", ""),
                        shortInstruction = instruction(direction, "", ""),
                        along = turnAlong
                    )
                )
            }
        }
        return result.sortedBy { it.along }
    }

    private enum class LevelMeans { STAIRS, ESCALATOR, ELEVATOR, UNKNOWN }

    private fun levelMeans(direction: Direction, at: LatLng?, access: List<StationLayout.Access>): LevelMeans {
        val kinds = if (at == null) {
            emptyList()
        } else {
            access.map { it.kind to at.distanceTo(it.coordinate) }
                .filter { it.second < 20 }
                .sortedBy { it.second }
                .map { it.first }
        }
        return when (direction) {
            Direction.elevator -> LevelMeans.ELEVATOR
            Direction.stairs ->
                if (StationLayout.Access.Kind.ESCALATOR in kinds && StationLayout.Access.Kind.STAIRS !in kinds) {
                    LevelMeans.ESCALATOR
                } else {
                    LevelMeans.STAIRS
                }
            else -> when (kinds.firstOrNull { it != StationLayout.Access.Kind.ELEVATOR } ?: kinds.firstOrNull()) {
                StationLayout.Access.Kind.STAIRS -> LevelMeans.STAIRS
                StationLayout.Access.Kind.ESCALATOR -> LevelMeans.ESCALATOR
                StationLayout.Access.Kind.ELEVATOR -> LevelMeans.ELEVATOR
                null -> LevelMeans.UNKNOWN
            }
        }
    }

    private fun levelInstruction(means: LevelMeans, up: Boolean, target: String?): String = when {
        means == LevelMeans.ELEVATOR && target != null -> "Prenez l'ascenseur jusqu'à $target"
        means == LevelMeans.ELEVATOR -> "Prenez l'ascenseur"
        means == LevelMeans.STAIRS && up && target != null -> "Montez les escaliers vers $target"
        means == LevelMeans.STAIRS && up -> "Montez les escaliers"
        means == LevelMeans.STAIRS && target != null -> "Descendez les escaliers vers $target"
        means == LevelMeans.STAIRS -> "Descendez les escaliers"
        means == LevelMeans.ESCALATOR && up && target != null -> "Montez par l'escalier roulant vers $target"
        means == LevelMeans.ESCALATOR && up -> "Montez par l'escalier roulant"
        means == LevelMeans.ESCALATOR && target != null -> "Descendez par l'escalier roulant vers $target"
        means == LevelMeans.ESCALATOR -> "Descendez par l'escalier roulant"
        up && target != null -> "Montez vers $target"
        up -> "Montez au niveau supérieur"
        target != null -> "Descendez vers $target"
        else -> "Descendez au niveau inférieur"
    }

    private fun levelSymbol(means: LevelMeans, up: Boolean): String = when (means) {
        LevelMeans.ELEVATOR -> "arrow.up.arrow.down.square"
        LevelMeans.STAIRS, LevelMeans.ESCALATOR -> "figure.stairs"
        LevelMeans.UNKNOWN -> if (up) "arrow.up.forward.circle" else "arrow.down.forward.circle"
    }

    fun geometricTurns(path: RoutePath): List<Pair<Double, Direction>> {
        if (path.length < 30) return emptyList()

        fun turn(along: Double, window: Double): Double {
            val before = path.coordinate(along - window) ?: return 0.0
            val here = path.coordinate(along) ?: return 0.0
            val after = path.coordinate(along + window) ?: return 0.0
            if (before.distanceTo(here) <= 2 || here.distanceTo(after) <= 2) return 0.0
            return Angle360.delta(before.bearingTo(here), here.bearingTo(after))
        }

        val clusters = mutableListOf<MutableList<Pair<Double, Double>>>()
        for (along in path.cumulative) {
            if (along < 12 || along > path.length - 12) continue
            val angle = turn(along, 15.0)
            if (abs(angle) < 30) continue
            val last = clusters.lastOrNull()?.lastOrNull()
            if (last != null && along - last.first < 18) {
                clusters.last().add(along to angle)
            } else {
                clusters.add(mutableListOf(along to angle))
            }
        }

        val kept = mutableListOf<Pair<Double, Double>>()
        for (cluster in clusters) {
            val peak = cluster.maxByOrNull { abs(it.second) } ?: continue
            val angle = turn(peak.first, 25.0)
            if (abs(angle) >= 30) kept.add(peak.first to angle)
        }

        val result = mutableListOf<Pair<Double, Direction>>()
        var index = 0
        while (index < kept.size) {
            if (index + 1 < kept.size &&
                kept[index + 1].first - kept[index].first < 25 &&
                kept[index].second * kept[index + 1].second < 0 &&
                abs(kept[index].second + kept[index + 1].second) < 30
            ) {
                index += 2
                continue
            }
            result.add(kept[index].first to direction(kept[index].second))
            index += 1
        }
        return result
    }

    private fun direction(angle: Double): Direction {
        val right = angle > 0
        return when (abs(angle)) {
            in 160.0..Double.MAX_VALUE -> if (right) Direction.uturnRight else Direction.uturnLeft
            in 120.0..160.0 -> if (right) Direction.hardRight else Direction.hardLeft
            in 55.0..120.0 -> if (right) Direction.right else Direction.left
            else -> if (right) Direction.slightlyRight else Direction.slightlyLeft
        }
    }

    fun symbol(direction: Direction): String = when (direction) {
        Direction.depart -> "figure.walk"
        Direction.hardLeft, Direction.left -> "arrow.turn.up.left"
        Direction.slightlyLeft -> "arrow.up.left"
        Direction.continueStraight -> "arrow.up"
        Direction.slightlyRight -> "arrow.up.right"
        Direction.right, Direction.hardRight -> "arrow.turn.up.right"
        Direction.circleClockwise -> "arrow.clockwise.circle"
        Direction.circleCounterClockwise -> "arrow.counterclockwise.circle"
        Direction.stairs -> "figure.stairs"
        Direction.elevator -> "arrow.up.arrow.down.square"
        Direction.uturnLeft -> "arrow.uturn.left"
        Direction.uturnRight -> "arrow.uturn.right"
    }

    fun instruction(direction: Direction, street: String, exit: String): String {
        val hasStreet = street.isNotEmpty()
        return when (direction) {
            Direction.depart, Direction.continueStraight ->
                if (hasStreet) "Continuez sur $street" else "Continuez tout droit"
            Direction.left -> if (hasStreet) "Tournez à gauche sur $street" else "Tournez à gauche"
            Direction.hardLeft ->
                if (hasStreet) "Tournez franchement à gauche sur $street" else "Tournez franchement à gauche"
            Direction.slightlyLeft -> if (hasStreet) "Serrez à gauche sur $street" else "Serrez à gauche"
            Direction.right -> if (hasStreet) "Tournez à droite sur $street" else "Tournez à droite"
            Direction.hardRight ->
                if (hasStreet) "Tournez franchement à droite sur $street" else "Tournez franchement à droite"
            Direction.slightlyRight -> if (hasStreet) "Serrez à droite sur $street" else "Serrez à droite"
            Direction.circleClockwise, Direction.circleCounterClockwise ->
                if (exit.isNotEmpty()) "Au rond-point, prenez la sortie $exit" else "Traversez le rond-point"
            Direction.stairs -> "Prenez les escaliers"
            Direction.elevator -> "Prenez l'ascenseur"
            Direction.uturnLeft, Direction.uturnRight -> "Faites demi-tour"
        }
    }
}

object LegLiveMerger {

    fun merge(leg: Leg, trip: ch.cclerc.luxcom.model.trip.Itinerary, retargetingTo: String? = null): Leg? {
        val wanted = retargetingTo ?: leg.tripId
        val tripLeg = trip.legs.firstOrNull { it.tripId == wanted } ?: trip.legs.firstOrNull() ?: return null
        val stops = tripLeg.allStops
        val boardIndex = index(leg.from, stops, { it.scheduledDeparture }) ?: return null
        val alightIndex = index(leg.to, stops, { it.scheduledArrival }, boardIndex) ?: return null
        if (alightIndex <= boardIndex) return null

        val from = stops[boardIndex]
        val to = stops[alightIndex]
        val startTime = from.departure ?: from.scheduledDeparture ?: leg.startTime
        val endTime = to.arrival ?: to.scheduledArrival ?: leg.endTime

        return leg.copy(
            from = from,
            to = to,
            duration = max(0, ((endTime.toEpochMilli() - startTime.toEpochMilli()) / 1000).toInt()),
            startTime = startTime,
            endTime = endTime,
            scheduledStartTime = from.scheduledDeparture ?: leg.scheduledStartTime,
            scheduledEndTime = to.scheduledArrival ?: leg.scheduledEndTime,
            realTime = tripLeg.realTime,
            cancelled = tripLeg.cancelled,
            headsign = leg.headsign ?: tripLeg.headsign,
            routeShortName = leg.routeShortName ?: tripLeg.routeShortName,
            intermediateStops = stops.subList(boardIndex + 1, alightIndex).toList(),
            tripId = retargetingTo ?: leg.tripId
        )
    }

    fun slice(tripLeg: Leg, boardIndex: Int, alightIndex: Int): Leg? {
        val stops = tripLeg.allStops
        if (boardIndex !in stops.indices || alightIndex !in stops.indices || alightIndex <= boardIndex) return null
        val from = stops[boardIndex]
        val to = stops[alightIndex]
        val startTime = from.departure ?: from.scheduledDeparture ?: tripLeg.startTime
        val endTime = to.arrival ?: to.scheduledArrival ?: tripLeg.endTime

        return Leg(
            mode = tripLeg.mode,
            from = from,
            to = to,
            duration = max(0, ((endTime.toEpochMilli() - startTime.toEpochMilli()) / 1000).toInt()),
            startTime = startTime,
            endTime = endTime,
            scheduledStartTime = from.scheduledDeparture ?: startTime,
            scheduledEndTime = to.scheduledArrival ?: endTime,
            realTime = tripLeg.realTime,
            cancelled = tripLeg.cancelled,
            distance = null,
            headsign = tripLeg.headsign,
            routeShortName = tripLeg.routeShortName,
            intermediateStops = stops.subList(boardIndex + 1, alightIndex).toList(),
            legGeometry = tripLeg.legGeometry,
            agencyId = tripLeg.agencyId,
            tripId = tripLeg.tripId,
            steps = null
        )
    }

    private fun index(
        place: Place,
        stops: List<Place>,
        scheduled: (Place) -> java.time.Instant?,
        after: Int = -1
    ): Int? {
        val candidates = stops.indices.filter { it > after }
        val stopId = place.stopId
        if (stopId != null) {
            val wanted = scheduled(place)
            if (wanted != null) {
                candidates.firstOrNull { stops[it].stopId == stopId && scheduled(stops[it]) == wanted }?.let { return it }
            }
            candidates.firstOrNull { stops[it].stopId == stopId }?.let { return it }
        }
        return candidates.firstOrNull { stops[it].name == place.name }
    }
}

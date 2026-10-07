package ch.cclerc.luxapp.domain.intelligence

import ch.cclerc.luxcom.model.trip.Itinerary
import kotlin.math.max
import kotlin.math.min

object IntelligenceLearner {
    enum class Signal(val strength: Double) {
        OPENED(0.2),
        TRANSFER_OPTION(0.6),
        STARTED(1.0)
    }

    private class Session(
        val candidates: List<Itinerary>,
        val context: TripIntelligence.Context,
        val credited: MutableMap<String, Double> = mutableMapOf()
    )

    private var lastSearch: Session? = null

    fun remember(candidates: List<Itinerary>, context: TripIntelligence.Context) {
        if (candidates.isEmpty()) return
        lastSearch = Session(candidates, context)
    }

    fun forget() {
        lastSearch = null
    }

    fun observe(chosen: Itinerary, signal: Signal) {
        val session = lastSearch ?: return
        val key = matchKey(chosen)
        val match = session.candidates.firstOrNull { matchKey(it) == key } ?: return
        val already = session.credited[key] ?: 0.0
        val strength = min(1.0, already + signal.strength) - already
        if (strength <= 0) return
        session.credited[key] = already + strength
        learn(match, session.candidates, session.context, strength)
    }

    private fun matchKey(itinerary: Itinerary): String {
        val trips = itinerary.legs.mapNotNull { it.tripId }
        return if (trips.isEmpty()) itinerary.intelligenceSignature else trips.joinToString(",")
    }

    fun observe(chosen: Itinerary, among: List<Itinerary>, context: TripIntelligence.Context, signal: Signal) {
        learn(chosen, among + chosen, context, signal.strength)
    }

    private fun learn(chosen: Itinerary, candidates: List<Itinerary>, context: TripIntelligence.Context, strength: Double) {
        if (!IntelligenceStore.learning.isEnabled || candidates.size <= 1) return

        val ranked = TripIntelligence.rank(candidates, context)
        val pick = ranked.firstOrNull() ?: return
        val signature = chosen.intelligenceSignature
        if (pick.itinerary.intelligenceSignature == signature) return
        val mine = ranked.firstOrNull { it.itinerary.intelligenceSignature == signature } ?: return

        val timingGap = if (context.arriveBy) {
            pick.itinerary.startTime.epochSecond - mine.itinerary.startTime.epochSecond
        } else {
            mine.itinerary.startTime.epochSecond - pick.itinerary.startTime.epochSecond
        }
        if (timingGap >= 10 * 60) return

        fun pull(chosenValue: Double, pickedValue: Double, scale: Double): Double =
            max(-1.0, min(1.0, (chosenValue - pickedValue) / scale))

        var learning = IntelligenceStore.learning
        val harsh = context.weather?.isHarsh == true
        val walkDelta = pull(mine.features.walkMinutes, pick.features.walkMinutes, 5.0)
        learning = if (harsh) {
            learning.nudged(IntelligenceLearning.Key.WEATHER, -0.04 * strength * walkDelta)
        } else {
            learning.nudged(IntelligenceLearning.Key.WALK, -0.02 * strength * walkDelta)
        }

        learning = learning.nudged(
            IntelligenceLearning.Key.TRANSFER,
            -0.3 * strength * pull(mine.features.transfers.toDouble(), pick.features.transfers.toDouble(), 1.0)
        )

        fun shortfall(slack: Double?): Double = max(0.0, 6 - (slack ?: 6.0))
        learning = learning.nudged(
            IntelligenceLearning.Key.MARGIN,
            -0.15 * strength * pull(shortfall(mine.features.tightestSlack), shortfall(pick.features.tightestSlack), 3.0)
        )

        val mineCrowd = mine.features.crowdPeak
        val pickCrowd = pick.features.crowdPeak
        if (mineCrowd != null && pickCrowd != null) {
            learning = learning.nudged(
                IntelligenceLearning.Key.CROWD,
                -0.1 * strength * pull(max(0.0, mineCrowd - 2.5), max(0.0, pickCrowd - 2.5), 1.5)
            )
        }

        IntelligenceStore.learning = learning.copy(
            observations = learning.observations + 1,
            updatedAt = System.currentTimeMillis()
        )
    }

    fun summary(learning: IntelligenceLearning): List<TripSuggestion.Reason> {
        val lines = mutableListOf<TripSuggestion.Reason>()
        if (learning.transfer > 1.5) {
            lines.add(TripSuggestion.Reason("arrow.right", "Vous attendez volontiers un trajet direct"))
        } else if (learning.transfer < -1) {
            lines.add(TripSuggestion.Reason("arrow.triangle.swap", "Les correspondances vous gênent peu"))
        }
        if (learning.walk > 0.15) {
            lines.add(TripSuggestion.Reason("figure.stand", "Vous préférez marcher moins"))
        } else if (learning.walk < -0.15) {
            lines.add(TripSuggestion.Reason("figure.walk", "La marche ne vous dérange pas"))
        }
        if (learning.weather > 0.3) {
            lines.add(TripSuggestion.Reason("umbrella.fill", "Par mauvais temps, vous évitez la marche"))
        } else if (learning.weather < -0.3) {
            lines.add(TripSuggestion.Reason("cloud.rain.fill", "La pluie ne vous arrête pas"))
        }
        if (learning.margin > 1) {
            lines.add(TripSuggestion.Reason("tortoise.fill", "Vous aimez les correspondances larges"))
        } else if (learning.margin < -1) {
            lines.add(TripSuggestion.Reason("hare.fill", "Les correspondances serrées ne vous font pas peur"))
        }
        if (learning.crowd > 1) {
            lines.add(TripSuggestion.Reason("person.fill", "Vous évitez les véhicules bondés"))
        } else if (learning.crowd < -1) {
            lines.add(TripSuggestion.Reason("person.3.fill", "L'affluence ne vous dérange pas"))
        }
        return lines
    }
}

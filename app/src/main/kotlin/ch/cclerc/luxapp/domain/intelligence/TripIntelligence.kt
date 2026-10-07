package ch.cclerc.luxapp.domain.intelligence

import ch.cclerc.luxapp.data.Settings
import ch.cclerc.luxapp.domain.DirectionPreferenceStore
import ch.cclerc.luxapp.domain.LineScoreManager
import ch.cclerc.luxapp.domain.map.LatLng
import ch.cclerc.luxapp.domain.onboard.isTransit
import ch.cclerc.luxapp.ui.onboard.communityLevel
import ch.cclerc.luxcom.api.getLCBInfo
import ch.cclerc.luxcom.geo.StopGrouping
import ch.cclerc.luxcom.model.TransportationMode
import ch.cclerc.luxcom.model.feedback.ReportAttribute
import ch.cclerc.luxcom.model.trip.Itinerary
import ch.cclerc.luxcom.model.trip.Leg
import java.time.Instant
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

class TripSuggestion(
    val itinerary: Itinerary,
    val reasons: List<Reason>,
    val weather: WeatherSnapshot?,
    val minutesLater: Int,
    val chosen: RankedTrip,
    val fastest: RankedTrip
) {
    data class Reason(val symbol: String, val text: String)

    override fun equals(other: Any?): Boolean =
        other is TripSuggestion && other.itinerary.intelligenceSignature == itinerary.intelligenceSignature &&
            other.reasons == reasons && other.weather == weather

    override fun hashCode(): Int = itinerary.intelligenceSignature.hashCode()
}

class TripFeatures(
    val walkMinutes: Double,
    val outdoorWaitMinutes: Double,
    val tightestSlack: Double?,
    val crowdPeak: Double?,
    val habitLine: String?,
    val habitScore: Double,
    val transfers: Int
)

data class CostBreakdown(
    var time: Double = 0.0,
    var transfers: Double = 0.0,
    var walking: Double = 0.0,
    var weather: Double = 0.0,
    var margin: Double = 0.0,
    var crowd: Double = 0.0,
    var habits: Double = 0.0
) {
    val total: Double get() = time + transfers + walking + weather + margin + crowd + habits
}

class RankedTrip(
    val itinerary: Itinerary,
    val features: TripFeatures,
    val cost: CostBreakdown
)

val Itinerary.intelligenceSignature: String
    get() {
        val legs = legs.joinToString(",") { it.tripId ?: "${it.mode.rawValue}${it.startTime.epochSecond}" }
        return "${startTime.epochSecond}|${endTime.epochSecond}|$legs"
    }

val String.normalizedHeadsignKey: String get() = StopGrouping.normalizedName(this)

object TripIntelligence {
    class Context(
        profile: IntelligenceProfile = IntelligenceStore.profile,
        learning: IntelligenceLearning = IntelligenceStore.learning,
        val weather: WeatherSnapshot?,
        val arriveBy: Boolean,
        val crowd: Map<String, Double>
    ) {
        val profile: IntelligenceProfile = profile
        val learning: IntelligenceLearning = if (learning.isEnabled) learning else learning.erased()
    }

    class Weights(
        val walk: Double,
        val weather: Double,
        val transfer: Double,
        val margin: Double,
        val crowd: Double
    )

    fun weights(context: Context): Weights {
        val profile = context.profile
        val learning = context.learning
        val walk = when (profile.walking) {
            IntelligenceProfile.Walking.ENJOYS -> 0.0
            IntelligenceProfile.Walking.NEUTRAL -> 0.3
            IntelligenceProfile.Walking.MINIMAL -> 1.0
        }
        val weatherBase = when (profile.weather) {
            IntelligenceProfile.Weather.INDIFFERENT -> 0.0
            IntelligenceProfile.Weather.WALK_LESS -> 0.8
            IntelligenceProfile.Weather.AVOID_WALKING -> 2.0
        }
        var weather = 0.0
        val snapshot = context.weather
        if (snapshot != null && snapshot.isHarsh) {
            weather = max(0.0, weatherBase + learning.weather)
            if (!snapshot.isWet) weather /= 2
        }
        val crowd = when (profile.crowd) {
            IntelligenceProfile.Crowd.INDIFFERENT -> 0.0
            IntelligenceProfile.Crowd.AVOID -> 2.5
            IntelligenceProfile.Crowd.AVOID_STRONGLY -> 6.0
        }
        return Weights(
            walk = max(0.0, walk + learning.walk),
            weather = weather,
            transfer = max(0.0, profile.directness.minutes + learning.transfer),
            margin = max(0.0, profile.margin.minutes + learning.margin),
            crowd = max(0.0, crowd + learning.crowd)
        )
    }

    fun needsDirectSearch(profile: IntelligenceProfile, maxTransfers: Int): Boolean =
        profile.directness != IntelligenceProfile.Directness.NEVER && maxTransfers > 0

    fun needsLessWalkingSearch(profile: IntelligenceProfile, weather: WeatherSnapshot?): Boolean {
        if (profile.walking == IntelligenceProfile.Walking.MINIMAL) return true
        if (weather == null || !weather.isHarsh) return false
        return profile.weather != IntelligenceProfile.Weather.INDIFFERENT
    }

    suspend fun liveContext(
        near: LatLng?,
        at: Instant,
        candidates: List<Itinerary>,
        arriveBy: Boolean = false,
        includeCrowd: Boolean = true
    ): Context = coroutineScope {
        val profile = IntelligenceStore.profile
        val weather = async {
            near?.let { WeatherService.snapshot(it.latitude, it.longitude, at) }
        }
        val usesCrowd = includeCrowd && profile.crowd != IntelligenceProfile.Crowd.INDIFFERENT && Settings.crowdbackAllowed
        val crowd = if (usesCrowd) crowdLevels(candidates) else emptyMap()
        Context(weather = weather.await(), arriveBy = arriveBy, crowd = crowd)
    }

    suspend fun crowdLevels(itineraries: List<Itinerary>): Map<String, Double> {
        data class Request(val tripId: String, val line: String, val lat: Double, val lon: Double)

        val requests = mutableListOf<Request>()
        val seen = mutableSetOf<String>()
        for (itinerary in itineraries) {
            for (leg in itinerary.legs) {
                if (!leg.isTransit) continue
                val tripId = leg.tripId ?: continue
                val line = leg.routeShortName ?: continue
                if (!seen.add(tripId)) continue
                requests.add(Request(tripId, line, leg.from.lat, leg.from.lon))
            }
        }
        val batch = requests.take(12)
        if (batch.isEmpty()) return emptyMap()

        val levels = java.util.concurrent.ConcurrentHashMap<String, Double>()
        withTimeoutOrNull(2_500) {
            coroutineScope {
                for (request in batch) {
                    async {
                        val info = runCatching {
                            getLCBInfo(request.tripId, request.line, request.lat, request.lon, attribute = "crowd")
                        }.getOrNull()
                        info?.communityLevel(ReportAttribute.CROWD)?.first?.let { levels[request.tripId] = it }
                    }
                }
            }
        }
        return levels.toMap()
    }

    fun rank(candidates: List<Itinerary>, context: Context): List<RankedTrip> {
        val seen = mutableSetOf<String>()
        val unique = candidates.filter { seen.add(it.intelligenceSignature) }
        val cutoff = Instant.now().minusSeconds(60)
        val upcoming = unique.filter { context.arriveBy || it.startTime.isAfter(cutoff) }
        if (upcoming.isEmpty()) return emptyList()

        val earliestEnd = upcoming.minOf { it.endTime }
        val latestStart = upcoming.maxOf { it.startTime }
        val weights = weights(context)

        return upcoming.map { itinerary ->
            val features = features(itinerary, context)
            val cost = breakdown(features, weights)
            cost.time = if (context.arriveBy) {
                (latestStart.toEpochMilli() - itinerary.startTime.toEpochMilli()) / 60_000.0
            } else {
                (itinerary.endTime.toEpochMilli() - earliestEnd.toEpochMilli()) / 60_000.0
            }
            RankedTrip(itinerary, features, cost)
        }.sortedWith { lhs, rhs ->
            if (abs(lhs.cost.total - rhs.cost.total) > 0.001) {
                lhs.cost.total.compareTo(rhs.cost.total)
            } else {
                lhs.itinerary.startTime.compareTo(rhs.itinerary.startTime)
            }
        }
    }

    fun fastest(ranked: List<RankedTrip>): RankedTrip? = ranked.minWithOrNull { lhs, rhs ->
        if (abs(lhs.cost.time - rhs.cost.time) > 0.001) {
            lhs.cost.time.compareTo(rhs.cost.time)
        } else {
            lhs.itinerary.transfers.compareTo(rhs.itinerary.transfers)
        }
    }

    fun suggest(candidates: List<Itinerary>, context: Context): TripSuggestion? {
        val ranked = rank(candidates, context)
        val best = ranked.firstOrNull() ?: return null
        val fastest = fastest(ranked) ?: return null
        return TripSuggestion(
            itinerary = best.itinerary,
            reasons = reasons(best, fastest, context),
            weather = context.weather,
            minutesLater = max(0, (best.cost.time - fastest.cost.time).roundToInt()),
            chosen = best,
            fastest = fastest
        )
    }

    fun features(itinerary: Itinerary, context: Context): TripFeatures {
        val walkMinutes = itinerary.legs.filter { it.mode == TransportationMode.WALK }.sumOf { it.duration } / 60.0

        var outdoorWait = 0.0
        var tightest: Double? = null
        var previousTransit: Leg? = null
        var walkSinceTransit = 0.0
        for (leg in itinerary.legs) {
            if (leg.isTransit) {
                val previous = previousTransit
                if (previous != null && leg.interlineWithPreviousLeg != true) {
                    val gap = (leg.startTime.toEpochMilli() - previous.endTime.toEpochMilli()) / 1000.0
                    val slack = (gap - walkSinceTransit) / 60
                    outdoorWait += max(0.0, slack)
                    tightest = min(tightest ?: slack, slack)
                }
                previousTransit = leg
                walkSinceTransit = 0.0
            } else {
                walkSinceTransit += leg.duration
            }
        }

        val transitLegs = itinerary.legs.filter { it.isTransit }
        val crowdPeak = transitLegs.mapNotNull { leg -> leg.tripId?.let { context.crowd[it] } }.maxOrNull()

        var habitScore = 0.0
        var habitLine: String? = null
        if (context.profile.usesHabits) {
            val lineScores = LineScoreManager.shared.allScores.value
            val topScore = lineScores.maxOfOrNull { it.totalScore } ?: 0.0
            var bestLeg = 0.0
            for (leg in transitLegs) {
                val line = leg.routeShortName ?: continue
                var legScore = 0.0
                if (topScore > 0) {
                    legScore += min(1.0, LineScoreManager.shared.getScore(line) / topScore) * 1.5
                }
                leg.headsign?.let { headsign ->
                    val key = headsign.normalizedHeadsignKey
                    val direction = listOfNotNull(leg.from.parentId, leg.from.stopId).maxOfOrNull {
                        DirectionPreferenceStore.shared.score(it, line, key, leg.startTime)
                    } ?: 0.0
                    legScore += min(1.0, direction / 3) * 2
                }
                habitScore += legScore
                if (legScore > bestLeg && legScore >= 1) {
                    bestLeg = legScore
                    habitLine = line
                }
            }
        }

        return TripFeatures(
            walkMinutes = walkMinutes,
            outdoorWaitMinutes = outdoorWait,
            tightestSlack = tightest,
            crowdPeak = crowdPeak,
            habitLine = habitLine,
            habitScore = min(habitScore, 4.0),
            transfers = itinerary.transfers
        )
    }

    private fun breakdown(features: TripFeatures, weights: Weights): CostBreakdown {
        val cost = CostBreakdown()
        cost.walking = features.walkMinutes * weights.walk
        cost.weather = features.walkMinutes * weights.weather + features.outdoorWaitMinutes * weights.weather * 0.4
        cost.transfers = features.transfers * weights.transfer
        features.tightestSlack?.let { slack ->
            if (slack < weights.margin) cost.margin += (weights.margin - slack) * 1.5
            if (slack < 1) cost.margin += 2
        }
        features.crowdPeak?.let { crowd ->
            cost.crowd = max(0.0, crowd - 2.5) * weights.crowd
        }
        cost.habits = -features.habitScore
        return cost
    }

    fun reasons(best: RankedTrip, fastest: RankedTrip, context: Context): List<TripSuggestion.Reason> {
        val reasons = mutableListOf<TripSuggestion.Reason>()
        val isFastest = best.itinerary.intelligenceSignature == fastest.itinerary.intelligenceSignature

        if (isFastest) {
            reasons.add(TripSuggestion.Reason("bolt.fill", "Le plus rapide"))
        }

        if (best.itinerary.transfers < fastest.itinerary.transfers) {
            val saved = fastest.itinerary.transfers - best.itinerary.transfers
            reasons.add(
                if (best.itinerary.transfers == 0) {
                    TripSuggestion.Reason("arrow.right", "Direct, sans correspondance")
                } else {
                    TripSuggestion.Reason(
                        "arrow.triangle.swap",
                        if (saved == 1) "1 correspondance en moins" else "$saved correspondances en moins"
                    )
                }
            )
        } else if (best.itinerary.transfers == 0 && isFastest && best.itinerary.legs.any { it.isTransit }) {
            reasons.add(TripSuggestion.Reason("arrow.right", "Direct, sans correspondance"))
        }

        val walkSaved = (fastest.features.walkMinutes - best.features.walkMinutes).roundToInt()
        if (walkSaved >= 2) {
            val weather = context.weather
            val text = when {
                weather != null && weather.isWet -> "$walkSaved min de marche en moins sous la pluie"
                weather != null && weather.isCold -> "$walkSaved min de marche en moins dans le froid"
                weather != null && weather.isHot -> "$walkSaved min de marche en moins sous la chaleur"
                else -> "$walkSaved min de marche en moins"
            }
            reasons.add(TripSuggestion.Reason(if (weather?.isWet == true) "umbrella.fill" else "figure.walk", text))
        }

        best.features.crowdPeak?.let { bestCrowd ->
            val fastestCrowd = fastest.features.crowdPeak
            if (fastestCrowd != null && fastestCrowd - bestCrowd >= 1) {
                reasons.add(TripSuggestion.Reason("person.2.fill", "Moins de monde à bord"))
            } else if (bestCrowd < 2.5 && context.profile.crowd != IntelligenceProfile.Crowd.INDIFFERENT) {
                reasons.add(TripSuggestion.Reason("chair.fill", "Places assises signalées"))
            }
        }

        val margin = weights(context).margin
        val bestSlack = best.features.tightestSlack
        val fastestSlack = fastest.features.tightestSlack
        if (bestSlack != null && fastestSlack != null && fastestSlack < margin && bestSlack >= margin) {
            reasons.add(TripSuggestion.Reason("checkmark.shield.fill", "Correspondance moins serrée"))
        }

        best.features.habitLine?.let { line ->
            reasons.add(TripSuggestion.Reason("heart.fill", "Votre ligne $line"))
        }

        if (reasons.isEmpty()) {
            reasons.add(TripSuggestion.Reason("sparkles", "Le meilleur compromis"))
        }
        return reasons.take(3)
    }

    fun insight(ranked: List<RankedTrip>, context: Context): TripSuggestion.Reason? {
        val best = ranked.firstOrNull() ?: return null
        val fastest = fastest(ranked) ?: return null
        if (best.itinerary.intelligenceSignature == fastest.itinerary.intelligenceSignature) return null
        return reasons(best, fastest, context).firstOrNull()
    }
}

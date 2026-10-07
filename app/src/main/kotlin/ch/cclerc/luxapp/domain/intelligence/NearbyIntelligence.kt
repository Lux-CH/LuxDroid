package ch.cclerc.luxapp.domain.intelligence

import ch.cclerc.luxapp.data.Settings
import ch.cclerc.luxapp.domain.DirectionPreferenceStore
import ch.cclerc.luxapp.domain.LineScoreManager
import ch.cclerc.luxapp.domain.map.LatLng
import ch.cclerc.luxapp.domain.map.distanceTo
import ch.cclerc.luxapp.domain.search.RouteOptionsStore
import ch.cclerc.luxapp.ui.onboard.communityLevel
import ch.cclerc.luxcom.api.getDeparturesForStop
import ch.cclerc.luxcom.api.getLCBInfo
import ch.cclerc.luxcom.model.SearchResult
import ch.cclerc.luxcom.model.TransportationMode
import ch.cclerc.luxcom.model.feedback.ReportAttribute
import ch.cclerc.luxcom.model.stop.StopTime
import java.time.Instant
import kotlin.math.ceil
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NearbyIntelligence {
    class Pick(
        val stop: SearchResult,
        val line: String,
        val mode: TransportationMode,
        val agencyId: String,
        val headsign: String,
        val tripId: String,
        val departure: Instant,
        val following: Instant?,
        val walkSeconds: Double,
        val realTime: Boolean,
        val crowd: Double?,
        val weather: WeatherSnapshot?
    ) {
        val leaveAt: Instant get() = departure.minusMillis(((walkSeconds + 60) * 1000).toLong())
        val walkMinutes: Int get() = max(1, ceil(walkSeconds / 60).toInt())

        override fun equals(other: Any?): Boolean =
            other is Pick && other.tripId == tripId && other.departure == departure && other.following == following &&
                other.crowd == crowd && other.weather == weather && other.walkSeconds == walkSeconds

        override fun hashCode(): Int = tripId.hashCode() * 31 + departure.hashCode()
    }

    private val _pick = MutableStateFlow<Pick?>(null)
    val pick: StateFlow<Pick?> = _pick.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var expiryJob: Job? = null
    private var lastInputs: Pair<List<SearchResult>, LatLng?>? = null
    private var lastRefresh = Instant.EPOCH
    private var lastStopIds: List<String> = emptyList()

    fun refresh(stops: List<SearchResult>, location: LatLng?, force: Boolean = false) {
        lastInputs = stops to location
        val candidates = stops.take(3)
        val ids = candidates.map { it.id }
        if (location == null || candidates.isEmpty()) {
            _pick.value = null
            return
        }
        if (!force && ids == lastStopIds && Instant.now().epochSecond - lastRefresh.epochSecond <= 45) return
        lastStopIds = ids
        lastRefresh = Instant.now()

        val profile = IntelligenceStore.profile
        val usesCrowd = profile.crowd != IntelligenceProfile.Crowd.INDIFFERENT && Settings.crowdbackAllowed
        val speed = max(0.6, RouteOptionsStore.pedestrianSpeed(RouteOptionsStore.load()))

        job?.cancel()
        job = scope.launch {
            val now = Instant.now()
            val lineScores = LineScoreManager.shared.allScores.value
            val topLineScore = lineScores.maxOfOrNull { it.totalScore } ?: 0.0

            class Best(val score: Double, val stop: SearchResult, val times: List<StopTime>, val walk: Double)
            var best: Best? = null
            val results = candidates.map { stop ->
                async {
                    val times = runCatching {
                        getDeparturesForStop(stopId = stop.id, time = now, numberOfEvents = 40).stopTimes
                    }.getOrNull().orEmpty()
                    stop to times
                }
            }.awaitAll()
            for ((stop, times) in results) {
                val distance = location.distanceTo(LatLng(stop.lat, stop.lon))
                if (distance > MAX_DISTANCE) continue
                val walk = distance * 1.3 / speed + 30
                val groups = times.filter { !it.cancelled }
                    .groupBy { "${it.routeShortName}|${it.headsign?.normalizedHeadsignKey ?: ""}" }
                for (groupTimes in groups.values) {
                    val first = groupTimes.firstOrNull() ?: continue
                    val direction = DirectionPreferenceStore.shared.score(
                        stop.id,
                        first.routeShortName,
                        first.headsign?.normalizedHeadsignKey ?: "",
                        now
                    )
                    if (direction < MINIMUM_SCORE) continue
                    val line = if (topLineScore > 0) LineScoreManager.shared.getScore(first.routeShortName) / topLineScore * 0.5 else 0.0
                    val score = direction + line - distance / 1000
                    if (best == null || score > best.score) {
                        best = Best(score, stop, groupTimes, walk)
                    }
                }
            }

            val chosen = best
            if (chosen == null) {
                _pick.value = null
                return@launch
            }

            fun time(stopTime: StopTime): Instant? = stopTime.place.departure ?: stopTime.place.arrival
            val catchable = chosen.times
                .mapNotNull { stopTime -> time(stopTime)?.let { stopTime to it } }
                .filter { !it.second.isBefore(now.plusMillis(((chosen.walk - 30) * 1000).toLong())) }
                .sortedBy { it.second }
            val (next, departure) = catchable.firstOrNull() ?: run {
                _pick.value = null
                return@launch
            }
            if (departure.epochSecond - now.epochSecond > HORIZON_S) {
                _pick.value = null
                return@launch
            }

            val weather = async { WeatherService.snapshot(chosen.stop.lat, chosen.stop.lon, departure) }
            val crowd = async {
                if (!usesCrowd) return@async null
                val info = runCatching {
                    getLCBInfo(next.tripId, next.routeShortName, chosen.stop.lat, chosen.stop.lon, attribute = "crowd")
                }.getOrNull()
                info?.communityLevel(ReportAttribute.CROWD)?.first
            }

            val result = Pick(
                stop = chosen.stop,
                line = next.routeShortName,
                mode = next.mode,
                agencyId = next.agencyId,
                headsign = next.headsign ?: "",
                tripId = next.tripId,
                departure = departure,
                following = catchable.drop(1).firstOrNull()?.second,
                walkSeconds = chosen.walk,
                realTime = next.realTime,
                crowd = crowd.await(),
                weather = weather.await()
            )
            _pick.value = result
            scheduleExpiry(departure)
        }
    }

    private fun scheduleExpiry(departure: Instant) {
        expiryJob?.cancel()
        expiryJob = scope.launch {
            delay(max(1_000L, departure.toEpochMilli() - System.currentTimeMillis()))
            _pick.value = null
            lastInputs?.let { (stops, location) -> refresh(stops, location, force = true) }
        }
    }

    fun close() {
        scope.cancel()
    }

    private companion object {
        const val MINIMUM_SCORE = 1.0
        const val HORIZON_S = 45 * 60L
        const val MAX_DISTANCE = 900.0
    }
}

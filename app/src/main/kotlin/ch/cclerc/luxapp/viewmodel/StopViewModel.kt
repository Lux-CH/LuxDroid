package ch.cclerc.luxapp.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import ch.cclerc.luxapp.domain.GroupedStopTime
import ch.cclerc.luxapp.domain.LineScoreManager
import ch.cclerc.luxapp.domain.displayBufferTime
import ch.cclerc.luxcom.api.getDeparturesForStop
import ch.cclerc.luxcom.geo.StopGrouping
import ch.cclerc.luxcom.geo.departureRadiusMeters
import ch.cclerc.luxcom.model.Place
import ch.cclerc.luxcom.model.SearchResult
import ch.cclerc.luxcom.model.stop.StopTime
import ch.cclerc.luxcom.model.stop.StopTimes
import ch.cclerc.luxcom.relay.RelayClient
import ch.cclerc.luxcom.relay.RelayLiveFeed
import ch.cclerc.luxcom.stop.filteredToStation
import java.time.Instant
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class StopViewModel(
    val stop: SearchResult,
    private val fromStops: Boolean,
    val maxGroupsToShow: Int,
    initialTime: Instant? = null
) {
    var stopTimes: StopTimes? by mutableStateOf(null)
        private set

    var routeGroups: Map<String, List<GroupedStopTime>> by mutableStateOf(emptyMap())
        private set

    var routeNames: List<String> by mutableStateOf(emptyList())
        private set

    val currentPages: SnapshotStateMap<String, Int> = mutableStateMapOf()

    var isLoading: Boolean by mutableStateOf(false)
        private set

    var errorMessage: String? by mutableStateOf(null)
        private set

    var isCustomTimeSelected: Boolean by mutableStateOf(false)
        private set

    var customTime: Instant? by mutableStateOf(null)
        private set

    val shownRouteNames: List<String>
        get() = routeNames.take(maxGroupsToShow)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val liveFeed = RelayLiveFeed<StopTimes>(scope)
    private var departureCheckJob: Job? = null
    private var backgroundRefreshJob: Job? = null
    private var currentTime: Instant = Instant.now()
    private var departureCount = 50
    private var hasWidenedDepartureWindow = false

    private val lineScoreManager = LineScoreManager.shared

    init {
        if (initialTime != null) {
            isCustomTimeSelected = true
            currentTime = initialTime
            customTime = initialTime
        }
    }

    fun startMonitoring() {
        scope.launch {
            val relayEligible = !fromStops && !isCustomTimeSelected
            val relayCoversInitialLoad = if (relayEligible) RelayClient.shared.isConnected.value else false
            if (relayCoversInitialLoad) {
                if (stopTimes == null) isLoading = true
            } else if (stopTimes == null) {
                isLoading = true
                refreshDepartures(showLoading = true)
            } else {
                refreshDeparturesInBackground()
            }
        }

        if (!fromStops) {
            startLiveFeed()

            departureCheckJob?.cancel()
            departureCheckJob = scope.launch {
                while (isActive) {
                    delay(10_000)
                    checkAndHandleDepartures()
                }
            }
        }
    }

    fun stopMonitoring() {
        liveFeed.stop()
        departureCheckJob?.cancel()
        backgroundRefreshJob?.cancel()
        departureCheckJob = null
        backgroundRefreshJob = null
        scope.coroutineContext.cancelChildren()
    }

    private fun startLiveFeed() {
        val fallbackOnly = isCustomTimeSelected
        val stopId = stop.id
        val count = departureCount
        val radius = departureRadius

        liveFeed.start(
            fallbackOnly = fallbackOnly,
            fallbackInterval = 5.seconds,
            stream = { RelayClient.shared.departures(stopId = stopId, n = count, radius = radius) },
            fallbackFetch = {
                val fetchTime = if (isCustomTimeSelected) currentTime else Instant.now()
                try {
                    fetchDeparturesAndArrivals(fetchTime)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Throwable) {
                    android.util.Log.d("StopViewModel", "departures error", error)
                    errorMessage = "Impossible de charger les horaires."
                    isLoading = false
                    null
                }
            },
            onUpdate = { freshStopTimes -> applyStopTimes(freshStopTimes) }
        )
    }

    private val departureRadius: Int?
        get() {
            if (stop.servesRail) return departureRadiusMeters.toInt()
            if (stop.groupedStopIds.size > 1) return departureRadiusMeters.toInt()
            if (!StopGrouping.isStationForecourt(stop.name)) return null
            return if (stop.hasRailNeighbour == false) null else departureRadiusMeters.toInt()
        }

    private val thinDepartureThreshold: Int
        get() = if (stop.servesRail) 45 else 30

    private fun widenDepartureWindowIfNeeded(raw: StopTimes, filtered: StopTimes) {
        if (fromStops || isCustomTimeSelected || hasWidenedDepartureWindow) return
        if (raw.stopTimes.size < departureCount) return
        if (filtered.stopTimes.size >= thinDepartureThreshold) return

        hasWidenedDepartureWindow = true
        departureCount = 100

        scope.launch { startLiveFeed() }
    }

    private fun filteredForStation(stopTimes: StopTimes): StopTimes =
        stopTimes.filteredToStation(
            stopId = stop.id,
            name = stop.name,
            lat = stop.lat,
            lon = stop.lon,
            servesRail = stop.servesRail,
            groupedStopIds = stop.groupedStopIds
        )

    private fun applyStopTimes(rawStopTimes: StopTimes) {
        val freshStopTimes = filteredForStation(rawStopTimes)
        isLoading = false
        errorMessage = null
        stopTimes = freshStopTimes
        val times = freshStopTimes.stopTimes
        if (times.isNotEmpty()) {
            groupStopTimes(times)
            checkAndHandleDepartures()
        } else {
            routeGroups = emptyMap()
            routeNames = emptyList()
            currentPages.clear()
        }

        widenDepartureWindowIfNeeded(raw = rawStopTimes, filtered = freshStopTimes)
    }

    suspend fun refreshDepartures(showLoading: Boolean) {
        val timeToUse = if (isCustomTimeSelected) currentTime else Instant.now()
        refreshDepartures(forTime = timeToUse, showLoading = showLoading)
    }

    suspend fun refreshDepartures(forTime: Instant, showLoading: Boolean) {
        if (showLoading) isLoading = true
        backgroundRefreshJob?.cancel()

        val now = Instant.now()
        val isSignificantDifference = abs(forTime.toEpochMilli() - now.toEpochMilli()) > 60_000
        val customTimeChanged = isCustomTimeSelected != isSignificantDifference
        isCustomTimeSelected = isSignificantDifference
        currentTime = forTime
        customTime = if (isSignificantDifference) forTime else null

        if (customTimeChanged && !fromStops) {
            startLiveFeed()
        }

        val job = scope.launch {
            try {
                val freshStopTimes = fetchDeparturesAndArrivals(forTime)
                if (!isActive) return@launch
                applyStopTimes(freshStopTimes)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                android.util.Log.d("StopViewModel", "departures error", error)
                errorMessage = "Impossible de charger les horaires."
            } finally {
                if (showLoading) isLoading = false
            }
        }
        backgroundRefreshJob = job
        job.join()
    }

    private suspend fun fetchDeparturesAndArrivals(time: Instant): StopTimes =
        getDeparturesForStop(
            stopId = stop.id,
            time = time,
            both = true,
            direction = "LATER",
            numberOfEvents = if (fromStops) 100 else departureCount,
            radius = departureRadius
        )

    private fun refreshDeparturesInBackground() {
        backgroundRefreshJob?.cancel()

        val fetchTime = if (isCustomTimeSelected) currentTime else Instant.now()
        if (!isCustomTimeSelected) {
            currentTime = fetchTime
        }

        backgroundRefreshJob = scope.launch {
            try {
                val freshStopTimes = fetchDeparturesAndArrivals(fetchTime)
                if (!isActive) return@launch
                applyStopTimes(freshStopTimes)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                android.util.Log.d("StopViewModel", "departures error", error)
                errorMessage = "Impossible de charger les horaires."
            }
            backgroundRefreshJob = null
        }
    }

    private fun bufferTimeForTransport(stopTime: StopTime): Double = stopTime.displayBufferTime

    private fun getReferenceTime(): Instant = customTime ?: Instant.now()

    private fun survives(stopTime: StopTime, referenceTime: Instant): Boolean {
        val eventTime = stopTime.place.departure ?: stopTime.place.arrival ?: return true
        val buffered = eventTime.plusMillis((bufferTimeForTransport(stopTime) * 1000).toLong())
        return buffered > referenceTime
    }

    fun checkAndHandleDepartures() {
        if (fromStops) return

        val referenceTime = getReferenceTime()
        var needsRefresh = false
        val working = routeGroups.toMutableMap()

        for (routeName in routeNames) {
            val existing = working[routeName] ?: continue
            if (existing.isEmpty()) continue

            val groups = existing.toMutableList()
            var groupsModified = false
            var groupIndex = 0

            while (groupIndex < groups.size) {
                val group = groups[groupIndex]

                val filteredStopTimes = group.stopTimes.filter { survives(it, referenceTime) }

                if (filteredStopTimes.size != group.stopTimes.size) {
                    if (filteredStopTimes.isNotEmpty()) {
                        groups[groupIndex] = GroupedStopTime(
                            routeShortName = group.routeShortName,
                            headsign = group.headsign,
                            stopTimes = filteredStopTimes
                        )
                        groupsModified = true
                    } else {
                        groups.removeAt(groupIndex)
                        groupsModified = true
                        break
                    }
                }

                groupIndex += 1
            }

            if (groupsModified) {
                if (groups.isEmpty()) {
                    working.remove(routeName)
                    needsRefresh = true
                } else {
                    working[routeName] = groups
                }
            }
        }

        routeGroups = working

        outer@ for ((_, groups) in working) {
            if (groups.isEmpty()) continue

            for (group in groups) {
                val firstStopTime = group.stopTimes.firstOrNull() ?: continue
                val eventTime = firstStopTime.place.departure ?: firstStopTime.place.arrival ?: continue
                val bufferTime = bufferTimeForTransport(firstStopTime)
                if (eventTime.plusMillis((bufferTime * 1000).toLong()) < referenceTime) {
                    needsRefresh = true
                    break@outer
                }
            }
        }

        if (needsRefresh) {
            val currentRouteNames = working.keys.toSet()
            val previousRouteNames = routeNames.toSet()

            if (currentRouteNames != previousRouteNames) {
                routeNames = lineScoreManager.getSortedRouteNames(currentRouteNames.toList())
            }

            for (routeName in currentRouteNames) {
                val groupCount = working[routeName]?.size ?: continue
                val currentPage = currentPages[routeName] ?: continue
                if (currentPage >= groupCount && groupCount > 0) {
                    currentPages[routeName] = groupCount - 1
                }
            }
        }
    }

    fun sortStopTimes(stopTimes: List<StopTime>): List<StopTime> {
        val referenceTime = getReferenceTime()

        return stopTimes
            .filter { stopTime ->
                if (isCustomTimeSelected) true else survives(stopTime, referenceTime)
            }
            .sortedBy { eventTime(it) }
    }

    private fun deduplicatedByTrip(stopTimes: List<StopTime>): List<StopTime> {
        val indicesByTrip = mutableMapOf<String, MutableList<Int>>()
        for ((index, stopTime) in stopTimes.withIndex()) {
            indicesByTrip.getOrPut(stopTime.tripId) { mutableListOf() }.add(index)
        }

        val kept = mutableSetOf<Int>()
        for ((_, indices) in indicesByTrip) {
            val ordered = indices.sortedBy { eventTime(stopTimes[it]) }
            val cluster = mutableListOf<Int>()

            fun keepClosestOfCluster() {
                val closest = cluster.minByOrNull { distanceToStop(stopTimes[it].place) }
                if (closest != null) kept.add(closest)
                cluster.clear()
            }

            for (index in ordered) {
                val first = cluster.firstOrNull()
                if (first != null) {
                    val gap = eventTime(stopTimes[index]).toEpochMilli() - eventTime(stopTimes[first]).toEpochMilli()
                    if (gap > DUPLICATE_WINDOW_MILLIS) {
                        keepClosestOfCluster()
                    }
                }
                cluster.add(index)
            }
            keepClosestOfCluster()
        }

        return stopTimes.filterIndexed { index, _ -> kept.contains(index) }
    }

    private fun eventTime(stopTime: StopTime): Instant =
        stopTime.place.departure ?: stopTime.place.arrival ?: DISTANT_FUTURE

    private fun distanceToStop(place: Place): Double {
        val deltaLat = place.lat - stop.lat
        val deltaLon = (place.lon - stop.lon) * cos(stop.lat * PI / 180)
        return deltaLat * deltaLat + deltaLon * deltaLon
    }

    private fun groupStopTimes(rawStopTimes: List<StopTime>) {
        val stopTimes = deduplicatedByTrip(rawStopTimes)
        val referenceTime = getReferenceTime()
        val filteredStopTimes = if (isCustomTimeSelected) {
            stopTimes
        } else {
            stopTimes.filter { survives(it, referenceTime) }
        }

        val grouped = filteredStopTimes.groupBy { it.routeShortName }
        val viewedStopKey = StopGrouping.normalizedName(stop.name)

        val result = mutableMapOf<String, List<GroupedStopTime>>()
        val newCurrentPages = mutableMapOf<String, Int>()

        for ((routeName, routeStopTimes) in grouped) {
            val groupsByHeadsign = routeStopTimes
                .groupBy { it.headsign?.let { headsign -> StopGrouping.normalizedName(headsign) } ?: "" }
                .map { (_, times) ->
                    val sortedTimes = times.sortedBy { eventTime(it) }
                    GroupedStopTime(
                        routeShortName = routeName,
                        headsign = sortedTimes.firstOrNull()?.headsign ?: "",
                        stopTimes = sortedTimes
                    )
                }
                .sortedWith(
                    compareBy<GroupedStopTime> {
                        if (StopGrouping.normalizedName(it.headsign) == viewedStopKey) 1 else 0
                    }.thenBy { it.headsign }
                )

            result[routeName] = groupsByHeadsign
            newCurrentPages[routeName] = min(currentPages[routeName] ?: 0, max(0, groupsByHeadsign.size - 1))
        }

        val sortedRouteNames = lineScoreManager.getSortedRouteNames(grouped.keys.toList())

        routeNames = sortedRouteNames
        routeGroups = result
        currentPages.clear()
        currentPages.putAll(newCurrentPages)
    }

    fun userSelectedLine(routeShortName: String) {
        lineScoreManager.addScore(routeShortName)
    }

    private companion object {
        const val DUPLICATE_WINDOW_MILLIS: Long = 3 * 60 * 1000
        val DISTANT_FUTURE: Instant = Instant.ofEpochSecond(64_092_211_200L)
    }
}

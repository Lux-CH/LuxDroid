package ch.cclerc.luxapp.domain

import ch.cclerc.luxcom.api.getDisruptions
import ch.cclerc.luxcom.model.Disruption
import ch.cclerc.luxcom.model.trip.Leg
import ch.cclerc.luxcom.relay.RelayClient
import ch.cclerc.luxcom.relay.RelayLiveFeed
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DisruptionManager private constructor() {

    companion object {
        val shared by lazy { DisruptionManager() }

        private val stationPattern = Regex("""ch:1:sloid:\d+""")

        fun matching(disruptions: List<Disruption>, leg: Leg): List<Disruption> {
            val agencyId = (if (leg.agencyId == "Transports Publics Genevois") "881" else leg.agencyId)
                ?: return emptyList()
            val line = leg.routeShortName ?: ""
            val tripKey = tripKey(leg.tripId)
            val endpoints = listOf(leg.from, leg.to).mapNotNull { station(it.stopId ?: it.parentId) }.toSet()
            val stations = endpoints + leg.intermediateStops.orEmpty()
                .mapNotNull { station(it.stopId ?: it.parentId) }

            return disruptions.filter { disruption ->
                if ((disruption.agencyId ?: "881") != agencyId) return@filter false
                if (!disruption.isActive(leg.startTime, leg.endTime)) return@filter false
                if (line.isNotEmpty() && disruption.line == line) return@filter true
                if (tripKey != null && disruption.tripIds?.contains(tripKey) == true) return@filter true
                val stopIds = disruption.stopIds ?: return@filter false
                if (stopIds.size == 1) return@filter stopIds[0] in endpoints
                stopIds.count { it in stations } >= 2
            }
        }

        private fun tripKey(tripId: String?): String? {
            val parts = tripId?.let { splitOmittingEmpty(it, '_', 3) } ?: return null
            if (parts.size != 4) return null
            return "${parts[0]}_${parts[3]}"
        }

        private fun splitOmittingEmpty(text: String, separator: Char, maxSplits: Int): List<String> {
            val parts = mutableListOf<String>()
            var start = 0
            var index = 0
            while (index < text.length && parts.size < maxSplits) {
                if (text[index] == separator) {
                    if (index > start) parts.add(text.substring(start, index))
                    start = index + 1
                }
                index++
            }
            if (start < text.length) parts.add(text.substring(start))
            return parts
        }

        private fun station(stopId: String?): String? =
            stopId?.let { stationPattern.find(it)?.value }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val liveFeed = RelayLiveFeed<List<Disruption>>(scope)

    private val _disruptions = MutableStateFlow<List<Disruption>>(emptyList())
    val disruptions: StateFlow<List<Disruption>> = _disruptions.asStateFlow()

    private val _hasLoaded = MutableStateFlow(false)
    val hasLoaded: StateFlow<Boolean> = _hasLoaded.asStateFlow()

    private var started = false

    init {
        start()
    }

    fun start() {
        if (started) return
        started = true

        scope.launch { fetchDisruptions() }

        liveFeed.start(
            fallbackInterval = 60.seconds,
            stream = { RelayClient.shared.disruptions() },
            fallbackFetch = {
                try {
                    getDisruptions()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Throwable) {
                    null
                }
            },
            onUpdate = { fetched ->
                val unique = fetched.toSet()
                if (_disruptions.value.toSet() != unique) _disruptions.value = unique.toList()
                if (!_hasLoaded.value) _hasLoaded.value = true
            }
        )
    }

    suspend fun fetchDisruptions() {
        try {
            _disruptions.value = getDisruptions().toSet().toList()
            _hasLoaded.value = true
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
        }
    }

    fun disruptions(leg: Leg): List<Disruption> = matching(_disruptions.value, leg)
}

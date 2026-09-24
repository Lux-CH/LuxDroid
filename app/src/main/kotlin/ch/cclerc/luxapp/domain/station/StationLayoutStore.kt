package ch.cclerc.luxapp.domain.station

import ch.cclerc.luxcom.model.trip.Leg
import ch.cclerc.luxcom.relay.RelayClient
import ch.cclerc.luxcom.station.StationLayout
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

object StationLayoutStore {

    private val layouts = mutableMapOf<Int, StationLayout>()
    private val missing = mutableMapOf<Int, Instant>()
    private val inflight = mutableMapOf<Int, CompletableDeferred<StationLayout?>>()

    fun cached(uic: Int): StationLayout? = synchronized(layouts) { layouts[uic] }

    suspend fun layout(stopId: String): StationLayout? = withContext(Dispatchers.Main.immediate) {
        val uic = StationLayout.uic(stopId) ?: return@withContext null
        synchronized(layouts) { layouts[uic] }?.let { return@withContext it }
        val missedAt = missing[uic]
        if (missedAt != null && Instant.now().epochSecond - missedAt.epochSecond < 600) return@withContext null
        inflight[uic]?.let { return@withContext it.await() }

        val deferred = CompletableDeferred<StationLayout?>()
        inflight[uic] = deferred
        val layout = fetch("$uic")
        inflight.remove(uic)
        val result = if (layout != null && !layout.empty) {
            synchronized(layouts) { layouts[uic] = layout }
            layout
        } else {
            missing[uic] = Instant.now()
            null
        }
        deferred.complete(result)
        result
    }

    suspend fun layouts(legs: List<Leg>): Map<Int, StationLayout> {
        val stopIds = mutableMapOf<Int, String>()
        for (leg in legs) {
            if (!leg.mode.isMainlineRail) continue
            for (place in listOf(leg.from, leg.to)) {
                val stopId = place.stopId ?: continue
                val uic = StationLayout.uic(stopId) ?: continue
                stopIds[uic] = stopId
            }
        }
        return coroutineScope {
            stopIds.values.map { stopId -> async { layout(stopId) } }
                .awaitAll()
                .filterNotNull()
                .associateBy { it.uic }
        }
    }

    private suspend fun fetch(stationId: String): StationLayout? =
        withTimeoutOrNull(10_000) { RelayClient.shared.station(stationId).first() }
}

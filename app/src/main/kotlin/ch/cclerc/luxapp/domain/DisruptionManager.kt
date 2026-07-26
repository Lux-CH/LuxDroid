package ch.cclerc.luxapp.domain

import ch.cclerc.luxcom.api.getDisruptions
import ch.cclerc.luxcom.model.Disruption
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
        val shared = DisruptionManager()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val liveFeed = RelayLiveFeed<List<Disruption>>(scope)

    private val _disruptions = MutableStateFlow<List<Disruption>>(emptyList())
    val disruptions: StateFlow<List<Disruption>> = _disruptions.asStateFlow()

    private var started = false

    init {
        start()
    }

    fun start() {
        if (started) return
        started = true

        scope.launch { fetchDisruptions() }

        liveFeed.start(
            fallbackInterval = 300.seconds,
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
            onUpdate = { fetched -> _disruptions.value = fetched.toSet().toList() }
        )
    }

    suspend fun fetchDisruptions() {
        try {
            _disruptions.value = getDisruptions().toSet().toList()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
        }
    }

    fun disruptions(line: String): List<Disruption> =
        _disruptions.value.filter { it.line == line }
}

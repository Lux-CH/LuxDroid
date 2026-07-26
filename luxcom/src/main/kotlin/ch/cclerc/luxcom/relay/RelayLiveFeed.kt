package ch.cclerc.luxcom.relay

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class RelayLiveFeed<T> internal constructor(
    private val scope: CoroutineScope,
    private val isConnected: () -> Boolean
) {
    constructor(scope: CoroutineScope) : this(scope, { RelayClient.shared.isConnected.value })

    private var streamJob: Job? = null
    private var fallbackJob: Job? = null

    @Volatile
    private var receivedStreamValue = false

    fun start(
        fallbackOnly: Boolean = false,
        fallbackInterval: Duration,
        stream: (() -> Flow<T>)?,
        fallbackFetch: suspend () -> T?,
        onUpdate: (T) -> Unit
    ) {
        stop()
        receivedStreamValue = false

        if (!fallbackOnly && stream != null) {
            streamJob = scope.launch {
                stream().collect { value ->
                    receivedStreamValue = true
                    onUpdate(value)
                }
            }
        }

        fallbackJob = scope.launch {
            var firstIteration = true
            while (isActive) {
                if (!firstIteration || fallbackOnly) {
                    if (fallbackOnly || !isConnected() || !receivedStreamValue) {
                        val value = fallbackFetch()
                        if (value != null && isActive) {
                            onUpdate(value)
                        }
                    }
                }
                firstIteration = false
                val connected = if (fallbackOnly) false else isConnected()
                delay(if (connected) maxOf(fallbackInterval, 15.seconds) else fallbackInterval)
            }
        }
    }

    fun stop() {
        streamJob?.cancel()
        fallbackJob?.cancel()
        streamJob = null
        fallbackJob = null
    }
}

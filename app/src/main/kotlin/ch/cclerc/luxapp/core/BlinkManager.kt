package ch.cclerc.luxapp.core

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object BlinkManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _isVisible = mutableStateOf(true)
    val isVisible: State<Boolean> get() = _isVisible
    private var subscriberCount = 0
    private var tickerJob: Job? = null

    fun register() {
        subscriberCount += 1
        if (subscriberCount == 1) {
            tickerJob?.cancel()
            tickerJob = scope.launch {
                while (isActive) {
                    delay(1000)
                    _isVisible.value = !_isVisible.value
                }
            }
        }
    }

    fun unregister() {
        subscriberCount = maxOf(0, subscriberCount - 1)
        if (subscriberCount == 0) {
            tickerJob?.cancel()
            tickerJob = null
            _isVisible.value = true
        }
    }
}

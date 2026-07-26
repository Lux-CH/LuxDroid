package ch.cclerc.luxcom.net

import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object ApiState {
    private val mutex = Mutex()

    var primaryServerAvailable = true
        private set

    var lastPrimaryCheck: Instant = Instant.EPOCH
        private set

    val retryPrimaryAfter: Duration = Duration.ofSeconds(30)

    suspend fun shouldUsePrimary(): Boolean = mutex.withLock {
        if (!primaryServerAvailable &&
            Duration.between(lastPrimaryCheck, Instant.now()) > retryPrimaryAfter
        ) {
            primaryServerAvailable = true
        }
        primaryServerAvailable
    }

    suspend fun markPrimaryDown() {
        mutex.withLock {
            primaryServerAvailable = false
            lastPrimaryCheck = Instant.now()
        }
    }

    internal suspend fun reset() {
        mutex.withLock {
            primaryServerAvailable = true
            lastPrimaryCheck = Instant.EPOCH
        }
    }
}

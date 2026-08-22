package ch.cclerc.luxcom.net

import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Tracks whether the primary backend is worth talking to.
 *
 * A single failed request is not enough to abandon primary: once it has proven
 * itself reachable, it takes [FAILURES_BEFORE_SWITCH] failures spanning at least
 * [sustainedFailureWindow] to move over to backup, and then only for
 * [backupPeriod]. Before primary has ever answered, one failure is enough.
 */
object ApiState {
    private val mutex = Mutex()

    private const val FAILURES_BEFORE_SWITCH = 3
    private val backupPeriod: Duration = Duration.ofSeconds(30)
    private val sustainedFailureWindow: Duration = Duration.ofSeconds(8)
    private val streakMemory: Duration = Duration.ofSeconds(60)

    private val realClock: () -> Instant = { Instant.now() }

    /** Overridable so tests can span the failure window without sleeping. */
    internal var clock: () -> Instant = realClock

    private var primaryProven = false
    private var failureStreak = 0
    private var streakStartedAt: Instant = Instant.EPOCH
    private var lastFailureAt: Instant = Instant.EPOCH

    @Volatile
    private var backupUntil: Instant = Instant.EPOCH

    /**
     * Snapshot of the current backend, readable without suspending. Used to aim
     * the cross-backend retry at whichever side the next request will not use.
     */
    val isUsingBackup: Boolean
        get() = clock() < backupUntil

    suspend fun shouldUsePrimary(): Boolean = mutex.withLock {
        if (clock() < backupUntil) return@withLock false
        if (backupUntil != Instant.EPOCH) {
            backupUntil = Instant.EPOCH
            failureStreak = 0
        }
        true
    }

    /** Only re-probe a primary that has answered at least once before. */
    suspend fun shouldRetryPrimary(): Boolean = mutex.withLock { primaryProven }

    suspend fun markPrimaryReachable() {
        mutex.withLock {
            primaryProven = true
            failureStreak = 0
            backupUntil = Instant.EPOCH
        }
    }

    /** Returns true when this failure is the one that moves us onto backup. */
    suspend fun recordPrimaryFailure(): Boolean = mutex.withLock {
        val now = clock()
        if (failureStreak == 0 || Duration.between(lastFailureAt, now) > streakMemory) {
            failureStreak = 0
            streakStartedAt = now
        }
        failureStreak += 1
        lastFailureAt = now

        val sustained = failureStreak >= FAILURES_BEFORE_SWITCH &&
            Duration.between(streakStartedAt, now) >= sustainedFailureWindow
        if (!sustained && primaryProven) return@withLock false

        backupUntil = now.plus(backupPeriod)
        true
    }

    /** Puts the client on backup as if a switch had just been decided. */
    internal suspend fun forceBackup() {
        mutex.withLock { backupUntil = clock().plus(backupPeriod) }
    }

    internal suspend fun reset() {
        mutex.withLock {
            primaryProven = false
            failureStreak = 0
            streakStartedAt = Instant.EPOCH
            lastFailureAt = Instant.EPOCH
            backupUntil = Instant.EPOCH
            clock = realClock
        }
    }
}

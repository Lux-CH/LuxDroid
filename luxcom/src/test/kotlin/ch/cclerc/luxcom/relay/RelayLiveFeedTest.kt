package ch.cclerc.luxcom.relay

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class RelayLiveFeedTest {

    private class Harness {
        var connected = false
        var fetches = 0
        val updates = mutableListOf<Int>()

        val fallbackFetch: suspend () -> Int? = {
            fetches += 1
            fetches
        }

        val onUpdate: (Int) -> Unit = { updates.add(it) }
    }

    @Test
    fun firstIterationSkipsFallbackThenPollsEveryInterval() = runTest {
        val harness = Harness()
        val feed = RelayLiveFeed<Int>(backgroundScope) { harness.connected }
        feed.start(
            fallbackInterval = 5.seconds,
            stream = null,
            fallbackFetch = harness.fallbackFetch,
            onUpdate = harness.onUpdate
        )
        runCurrent()
        assertEquals(0, harness.fetches)
        advanceTimeBy(5.seconds)
        runCurrent()
        assertEquals(1, harness.fetches)
        advanceTimeBy(5.seconds)
        runCurrent()
        assertEquals(2, harness.fetches)
        assertEquals(listOf(1, 2), harness.updates)
        feed.stop()
    }

    @Test
    fun fallbackOnlyPollsImmediatelyAndIgnoresConnection() = runTest {
        val harness = Harness()
        harness.connected = true
        val feed = RelayLiveFeed<Int>(backgroundScope) { harness.connected }
        feed.start(
            fallbackOnly = true,
            fallbackInterval = 5.seconds,
            stream = null,
            fallbackFetch = harness.fallbackFetch,
            onUpdate = harness.onUpdate
        )
        runCurrent()
        assertEquals(1, harness.fetches)
        advanceTimeBy(5.seconds)
        runCurrent()
        assertEquals(2, harness.fetches)
        advanceTimeBy(5.seconds)
        runCurrent()
        assertEquals(3, harness.fetches)
        assertEquals(listOf(1, 2, 3), harness.updates)
        feed.stop()
    }

    @Test
    fun noPollingWhileConnectedAndStreamDelivered() = runTest {
        val harness = Harness()
        harness.connected = true
        val feed = RelayLiveFeed<Int>(backgroundScope) { harness.connected }
        feed.start(
            fallbackInterval = 5.seconds,
            stream = { flowOf(42) },
            fallbackFetch = harness.fallbackFetch,
            onUpdate = harness.onUpdate
        )
        runCurrent()
        assertEquals(listOf(42), harness.updates)
        advanceTimeBy(120.seconds)
        runCurrent()
        assertEquals(0, harness.fetches)
        assertEquals(listOf(42), harness.updates)
        feed.stop()
    }

    @Test
    fun pollingResumesWhenStreamNeverDelivers() = runTest {
        val harness = Harness()
        harness.connected = true
        val feed = RelayLiveFeed<Int>(backgroundScope) { harness.connected }
        feed.start(
            fallbackInterval = 5.seconds,
            stream = { emptyFlow() },
            fallbackFetch = harness.fallbackFetch,
            onUpdate = harness.onUpdate
        )
        runCurrent()
        assertEquals(0, harness.fetches)
        advanceTimeBy(15.seconds)
        runCurrent()
        assertEquals(1, harness.fetches)
        advanceTimeBy(15.seconds)
        runCurrent()
        assertEquals(2, harness.fetches)
        feed.stop()
    }

    @Test
    fun connectedIntervalIsFlooredAtFifteenSeconds() = runTest {
        val harness = Harness()
        harness.connected = true
        val feed = RelayLiveFeed<Int>(backgroundScope) { harness.connected }
        feed.start(
            fallbackInterval = 5.seconds,
            stream = { emptyFlow() },
            fallbackFetch = harness.fallbackFetch,
            onUpdate = harness.onUpdate
        )
        runCurrent()
        advanceTimeBy(5.seconds)
        runCurrent()
        assertEquals(0, harness.fetches)
        advanceTimeBy(9.seconds)
        runCurrent()
        assertEquals(0, harness.fetches)
        advanceTimeBy(1.seconds)
        runCurrent()
        assertEquals(1, harness.fetches)
        feed.stop()
    }

    @Test
    fun disconnectedPollingUsesRawInterval() = runTest {
        val harness = Harness()
        val feed = RelayLiveFeed<Int>(backgroundScope) { harness.connected }
        feed.start(
            fallbackInterval = 5.seconds,
            stream = { emptyFlow() },
            fallbackFetch = harness.fallbackFetch,
            onUpdate = harness.onUpdate
        )
        runCurrent()
        assertEquals(0, harness.fetches)
        advanceTimeBy(5.seconds)
        runCurrent()
        assertEquals(1, harness.fetches)
        advanceTimeBy(5.seconds)
        runCurrent()
        assertEquals(2, harness.fetches)
        feed.stop()
    }

    @Test
    fun stopCancelsPolling() = runTest {
        val harness = Harness()
        val feed = RelayLiveFeed<Int>(backgroundScope) { harness.connected }
        feed.start(
            fallbackOnly = true,
            fallbackInterval = 5.seconds,
            stream = null,
            fallbackFetch = harness.fallbackFetch,
            onUpdate = harness.onUpdate
        )
        runCurrent()
        assertEquals(1, harness.fetches)
        feed.stop()
        advanceTimeBy(60.seconds)
        runCurrent()
        assertEquals(1, harness.fetches)
    }
}

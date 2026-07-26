package ch.cclerc.luxcom.api

import ch.cclerc.luxcom.net.ApiClient
import ch.cclerc.luxcom.net.ApiError
import ch.cclerc.luxcom.net.ApiState
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

class DeparturesCrossBackendRetryTest {

    private lateinit var primary: MockWebServer
    private lateinit var backup: MockWebServer
    private lateinit var originalPrimary: String
    private lateinit var originalBackup: String

    private val emptyStopTimesJson =
        """{"stopTimes":[],"previousPageCursor":"prev","nextPageCursor":"next"}"""
    private val noRadiusBody =
        """{"error":"no radius: stop_found=false, center_parsed=false"}"""
    private val unknownLocationBody =
        """{"error":"Could not find timetable location for stop nl-1234"}"""

    private fun MockWebServer.baseUrl(): String = url("/").toString().trimEnd('/')

    @BeforeTest
    fun setUp() {
        originalPrimary = ApiClient.primaryBaseUrl
        originalBackup = ApiClient.backupBaseUrl
        primary = MockWebServer()
        primary.start()
        backup = MockWebServer()
        backup.start()
        ApiClient.primaryBaseUrl = primary.baseUrl()
        ApiClient.backupBaseUrl = backup.baseUrl()
        runBlocking { ApiState.reset() }
    }

    @AfterTest
    fun tearDown() {
        ApiClient.primaryBaseUrl = originalPrimary
        ApiClient.backupBaseUrl = originalBackup
        primary.shutdown()
        backup.shutdown()
        runBlocking { ApiState.reset() }
    }

    private suspend fun departures() = getDeparturesForStop(stopId = "nl-1234", numberOfEvents = 5)

    @Test
    fun unknownStopOnPrimaryRetriesOnBackup() = runBlocking {
        primary.enqueue(MockResponse().setResponseCode(400).setBody(noRadiusBody))
        backup.enqueue(MockResponse().setBody(emptyStopTimesJson))

        val stopTimes = departures()

        assertEquals("next", stopTimes.nextPageCursor)
        assertEquals(1, primary.requestCount)
        assertEquals(1, backup.requestCount)
        val primaryRequest = primary.takeRequest(2, TimeUnit.SECONDS)
        val backupRequest = backup.takeRequest(2, TimeUnit.SECONDS)
        assertEquals(primaryRequest?.path, backupRequest?.path)
        assertTrue(backupRequest?.path?.startsWith("/v4/stoptimes?") == true)
        assertTrue(ApiState.primaryServerAvailable)
    }

    @Test
    fun unknownTimetableLocationOnPrimaryRetriesOnBackup() = runBlocking {
        primary.enqueue(MockResponse().setResponseCode(400).setBody(unknownLocationBody))
        backup.enqueue(MockResponse().setBody(emptyStopTimesJson))

        val stopTimes = departures()

        assertEquals("next", stopTimes.nextPageCursor)
        assertEquals(1, primary.requestCount)
        assertEquals(1, backup.requestCount)
    }

    @Test
    fun unknownStopOnBackupRetriesOnPrimary() = runBlocking {
        ApiState.markPrimaryDown()
        backup.enqueue(MockResponse().setResponseCode(400).setBody(noRadiusBody))
        primary.enqueue(MockResponse().setBody(emptyStopTimesJson))

        val stopTimes = departures()

        assertEquals("prev", stopTimes.previousPageCursor)
        assertEquals(1, backup.requestCount)
        assertEquals(1, primary.requestCount)
    }

    @Test
    fun otherRequestFailuresAreNotRetried() = runBlocking {
        primary.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"bad request"}"""))

        val error = assertFailsWith<ApiError.RequestFailed> { departures() }

        assertEquals(400, error.statusCode)
        assertEquals(1, primary.requestCount)
        assertEquals(0, backup.requestCount)
    }

    @Test
    fun failedRetryRethrowsOriginalError() = runBlocking {
        primary.enqueue(MockResponse().setResponseCode(400).setBody(noRadiusBody))
        backup.enqueue(MockResponse().setResponseCode(500).setBody("boom"))

        val error = assertFailsWith<ApiError.RequestFailed> { departures() }

        assertEquals(400, error.statusCode)
        assertTrue(error.description.contains("stop_found=false"))
        assertEquals(1, primary.requestCount)
        assertEquals(1, backup.requestCount)
    }
}

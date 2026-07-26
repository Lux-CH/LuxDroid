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

class TripCrossBackendRetryTest {

    private lateinit var primary: MockWebServer
    private lateinit var backup: MockWebServer
    private lateinit var originalPrimary: String
    private lateinit var originalBackup: String

    private val itineraryJson =
        """{"duration":600,"startTime":"2026-07-26T10:00:00Z","endTime":"2026-07-26T10:10:00Z",""" +
            """"transfers":0,"legs":[]}"""
    private val unknownTripBody =
        """{"error":"Could not find trip nl-1234"}"""
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

    @Test
    fun unknownTripOnPrimaryRetriesOnBackup() = runBlocking {
        primary.enqueue(MockResponse().setResponseCode(400).setBody(unknownTripBody))
        backup.enqueue(MockResponse().setBody(itineraryJson))

        val itinerary = getTrip("trip-1")

        assertEquals(600, itinerary.duration)
        assertEquals(1, primary.requestCount)
        assertEquals(1, backup.requestCount)
        val primaryRequest = primary.takeRequest(2, TimeUnit.SECONDS)
        val backupRequest = backup.takeRequest(2, TimeUnit.SECONDS)
        assertEquals(primaryRequest?.path, backupRequest?.path)
        assertTrue(backupRequest?.path?.startsWith("/v4/trip?") == true)
        assertTrue(ApiState.primaryServerAvailable)
    }

    @Test
    fun unknownTimetableLocationOnPrimaryRetriesOnBackup() = runBlocking {
        primary.enqueue(MockResponse().setResponseCode(400).setBody(unknownLocationBody))
        backup.enqueue(MockResponse().setBody(itineraryJson))

        val itinerary = getTrip("trip-1")

        assertEquals(0, itinerary.transfers)
        assertEquals(1, primary.requestCount)
        assertEquals(1, backup.requestCount)
    }

    @Test
    fun unknownTripOnBackupRetriesOnPrimary() = runBlocking {
        ApiState.markPrimaryDown()
        backup.enqueue(MockResponse().setResponseCode(400).setBody(unknownTripBody))
        primary.enqueue(MockResponse().setBody(itineraryJson))

        val itinerary = getTrip("trip-1")

        assertEquals(600, itinerary.duration)
        assertEquals(1, backup.requestCount)
        assertEquals(1, primary.requestCount)
    }

    @Test
    fun otherRequestFailuresAreNotRetried() = runBlocking {
        primary.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"bad request"}"""))

        val error = assertFailsWith<ApiError.RequestFailed> { getTrip("trip-1") }

        assertEquals(400, error.statusCode)
        assertEquals(1, primary.requestCount)
        assertEquals(0, backup.requestCount)
    }

    @Test
    fun failedRetryRethrowsOriginalError() = runBlocking {
        primary.enqueue(MockResponse().setResponseCode(400).setBody(unknownTripBody))
        backup.enqueue(MockResponse().setResponseCode(500).setBody("boom"))

        val error = assertFailsWith<ApiError.RequestFailed> { getTrip("trip-1") }

        assertEquals(400, error.statusCode)
        assertTrue(error.description.contains("Could not find trip"))
        assertEquals(1, primary.requestCount)
        assertEquals(1, backup.requestCount)
    }
}

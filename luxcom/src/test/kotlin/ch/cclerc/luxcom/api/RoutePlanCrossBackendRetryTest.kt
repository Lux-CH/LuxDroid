package ch.cclerc.luxcom.api

import ch.cclerc.luxcom.model.PedestrianProfile
import ch.cclerc.luxcom.model.trip.RouteOptions
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

class RoutePlanCrossBackendRetryTest {

    private lateinit var primary: MockWebServer
    private lateinit var backup: MockWebServer
    private lateinit var originalPrimary: String
    private lateinit var originalBackup: String

    private val placeJson = """{"name":"P","lat":0.0,"lon":0.0,"vertexType":"NORMAL"}"""
    private val emptyTripJson =
        """{"from":$placeJson,"to":$placeJson,"direct":[],"itineraries":[],""" +
            """"previousPageCursor":"prev","nextPageCursor":"next"}"""
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

    private fun options() = RouteOptions(
        from = RouteOptions.RouteLocation("from1"),
        to = RouteOptions.RouteLocation("to1"),
        via = null,
        viaMinimumStay = emptyList(),
        time = null,
        arriveBy = false,
        maxTransfers = 2,
        minTransferTime = 5,
        pedestrianProfile = PedestrianProfile.FOOT,
        pedestrianSpeed = null,
        transitModes = null,
        numItineraries = null,
        timetableView = true,
        maxPreTransitTime = null,
        maxPostTransitTime = null
    )

    @Test
    fun unknownTimetableLocationOnPrimaryRetriesOnBackup() = runBlocking {
        primary.enqueue(MockResponse().setResponseCode(400).setBody(unknownLocationBody))
        backup.enqueue(MockResponse().setBody(emptyTripJson))

        val trip = getRoute(options())

        assertEquals("next", trip.nextPageCursor)
        assertEquals(1, primary.requestCount)
        assertEquals(1, backup.requestCount)
        val primaryRequest = primary.takeRequest(2, TimeUnit.SECONDS)
        val backupRequest = backup.takeRequest(2, TimeUnit.SECONDS)
        assertEquals(primaryRequest?.path, backupRequest?.path)
        assertTrue(backupRequest?.path?.startsWith("/v4/plan?") == true)
        assertTrue(ApiState.primaryServerAvailable)
    }

    @Test
    fun unknownTimetableLocationOnBackupRetriesOnPrimary() = runBlocking {
        ApiState.markPrimaryDown()
        backup.enqueue(MockResponse().setResponseCode(400).setBody(unknownLocationBody))
        primary.enqueue(MockResponse().setBody(emptyTripJson))

        val trip = getRoute(options())

        assertEquals("prev", trip.previousPageCursor)
        assertEquals(1, backup.requestCount)
        assertEquals(1, primary.requestCount)
    }

    @Test
    fun otherRequestFailuresAreNotRetried() = runBlocking {
        primary.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"bad request"}"""))

        val error = assertFailsWith<ApiError.RequestFailed> { getRoute(options()) }

        assertEquals(400, error.statusCode)
        assertEquals(1, primary.requestCount)
        assertEquals(0, backup.requestCount)
    }

    @Test
    fun failedRetryRethrowsOriginalError() = runBlocking {
        primary.enqueue(MockResponse().setResponseCode(400).setBody(unknownLocationBody))
        backup.enqueue(MockResponse().setResponseCode(500).setBody("boom"))

        val error = assertFailsWith<ApiError.RequestFailed> { getRoute(options()) }

        assertEquals(400, error.statusCode)
        assertTrue(error.description.contains("Could not find timetable location"))
        assertEquals(1, primary.requestCount)
        assertEquals(1, backup.requestCount)
    }
}

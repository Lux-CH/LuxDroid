package ch.cclerc.luxcom.net

import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

class ApiClientFailoverTest {

    @Serializable
    data class Payload(val ok: Boolean)

    private lateinit var primary: MockWebServer
    private lateinit var backup: MockWebServer
    private lateinit var deadUrl: String
    private lateinit var originalPrimary: String
    private lateinit var originalBackup: String

    private var fakeNow: Instant = Instant.parse("2026-08-22T12:00:00Z")

    private fun MockWebServer.baseUrl(): String = url("/").toString().trimEnd('/')

    private fun ok() = MockResponse().setBody("""{"ok":true}""")

    private fun gateway(code: Int = 502) =
        MockResponse().setResponseCode(code).setBody("tunnel down")

    /** Drives a successful request through primary so it counts as proven. */
    private suspend fun provePrimary() {
        primary.enqueue(ok())
        val result: Payload = ApiClient.fetch(endpoint = "/ping")
        assertTrue(result.ok)
    }

    @BeforeTest
    fun setUp() {
        originalPrimary = ApiClient.primaryBaseUrl
        originalBackup = ApiClient.backupBaseUrl
        primary = MockWebServer()
        primary.start()
        backup = MockWebServer()
        backup.start()
        val dead = MockWebServer()
        dead.start()
        deadUrl = dead.baseUrl()
        dead.shutdown()
        ApiClient.primaryBaseUrl = primary.baseUrl()
        ApiClient.backupBaseUrl = backup.baseUrl()
        runBlocking { ApiState.reset() }
    }

    @AfterTest
    fun tearDown() {
        ApiClient.primaryBaseUrl = originalPrimary
        ApiClient.backupBaseUrl = originalBackup
        ApiClient.isDeviceOffline = null
        primary.shutdown()
        backup.shutdown()
        runBlocking { ApiState.reset() }
    }

    @Test
    fun unprovenPrimaryFailsOverOnFirstFailure() = runBlocking {
        ApiClient.primaryBaseUrl = deadUrl
        backup.enqueue(ok())

        val result: Payload = ApiClient.fetch(endpoint = "/ping")

        assertTrue(result.ok)
        assertEquals(1, backup.requestCount)
        val recorded = backup.takeRequest(2, TimeUnit.SECONDS)
        assertEquals("/v1/ping", recorded?.path)
        // Primary never answered, so one failure is enough to move over.
        assertTrue(ApiState.isUsingBackup)
    }

    @Test
    fun singleBlipOnProvenPrimaryDoesNotStickToBackup() = runBlocking {
        provePrimary()
        primary.enqueue(gateway())
        primary.enqueue(gateway())
        backup.enqueue(ok())

        val result: Payload = ApiClient.fetch(endpoint = "/ping")

        assertTrue(result.ok)
        // One immediate re-probe of primary, then the answer comes from backup.
        assertEquals(3, primary.requestCount)
        assertEquals(1, backup.requestCount)
        // The blip is served, but we have not abandoned primary.
        assertFalse(ApiState.isUsingBackup)
    }

    @Test
    fun unprovenPrimaryIsNotReprobed() = runBlocking {
        primary.enqueue(gateway())
        backup.enqueue(ok())

        val result: Payload = ApiClient.fetch(endpoint = "/ping")

        assertTrue(result.ok)
        assertEquals(1, primary.requestCount)
        assertEquals(1, backup.requestCount)
    }

    @Test
    fun sustainedFailuresSwitchToBackupAndSkipPrimary() = runBlocking {
        ApiState.clock = { fakeNow }
        provePrimary()

        // Three failures spanning more than the 8s sustained window.
        for (offset in listOf(0L, 5L, 10L)) {
            fakeNow = Instant.parse("2026-08-22T12:00:00Z").plusSeconds(offset)
            primary.enqueue(gateway())
            primary.enqueue(gateway())
            backup.enqueue(ok())
            val served: Payload = ApiClient.fetch(endpoint = "/ping")
            assertTrue(served.ok)
        }

        assertTrue(ApiState.isUsingBackup)
        val primaryCountAtSwitch = primary.requestCount

        fakeNow = fakeNow.plusSeconds(2)
        backup.enqueue(ok())
        val result: Payload = ApiClient.fetch(endpoint = "/ping")

        assertTrue(result.ok)
        // Primary is skipped entirely while the backup window is open.
        assertEquals(primaryCountAtSwitch, primary.requestCount)
        assertEquals(4, backup.requestCount)
    }

    @Test
    fun backupWindowExpiresBackToPrimary() = runBlocking {
        ApiState.clock = { fakeNow }
        ApiState.forceBackup()
        assertTrue(ApiState.isUsingBackup)

        fakeNow = fakeNow.plusSeconds(31)

        primary.enqueue(ok())
        val result: Payload = ApiClient.fetch(endpoint = "/ping")

        assertTrue(result.ok)
        assertEquals(1, primary.requestCount)
        assertEquals(0, backup.requestCount)
        assertFalse(ApiState.isUsingBackup)
    }

    @Test
    fun cloudflareTunnelStatusesCountAsUnreachable() = runBlocking {
        for (code in listOf(502, 503, 504, 520, 523, 527, 530)) {
            ApiState.reset()
            primary.enqueue(gateway(code))
            backup.enqueue(ok())

            val result: Payload = ApiClient.fetch(endpoint = "/ping")

            assertTrue(result.ok, "status $code should fail over")
        }
        assertEquals(7, backup.requestCount)
    }

    @Test
    fun gatewayStatusAndBodySurviveToTheCaller() = runBlocking {
        primary.enqueue(gateway(521))
        backup.enqueue(MockResponse().setResponseCode(503).setBody("backup down too"))

        val error = assertFailsWith<ApiError.RequestFailed> {
            ApiClient.fetch<Payload>(endpoint = "/ping")
        }

        // Gateway statuses drive failover but are never wrapped or rewritten:
        // whichever backend answered last reaches the caller with code and body
        // intact, exactly as the iOS client behaves.
        assertEquals(503, error.statusCode)
        assertEquals("backup down too", error.description)
        assertEquals(1, primary.requestCount)
        assertEquals(1, backup.requestCount)
    }

    @Test
    fun http500FailsWithoutFailover() = runBlocking {
        primary.enqueue(MockResponse().setResponseCode(500).setBody("boom"))

        val error = assertFailsWith<ApiError.RequestFailed> {
            ApiClient.fetch<Payload>(endpoint = "/ping")
        }

        assertEquals(500, error.statusCode)
        assertEquals("boom", error.description)
        assertEquals(1, primary.requestCount)
        assertEquals(0, backup.requestCount)
        // The server answered, so primary counts as reachable.
        assertFalse(ApiState.isUsingBackup)
    }

    @Test
    fun deviceOfflineIsNotBlamedOnPrimary() = runBlocking {
        ApiClient.isDeviceOffline = { true }
        primary.enqueue(gateway())

        assertFailsWith<ApiError.RequestFailed> {
            ApiClient.fetch<Payload>(endpoint = "/ping")
        }

        assertEquals(1, primary.requestCount)
        assertEquals(0, backup.requestCount)
        assertFalse(ApiState.isUsingBackup)
    }

    @Test
    fun explicitBaseUrlBypassesResolution() = runBlocking {
        ApiClient.primaryBaseUrl = deadUrl
        primary.enqueue(ok())

        val result: Payload = ApiClient.fetch(endpoint = "/ping", baseUrl = primary.baseUrl())

        assertTrue(result.ok)
        assertEquals(1, primary.requestCount)
        assertEquals(0, backup.requestCount)
        assertFalse(ApiState.isUsingBackup)
    }

    @Test
    fun explicitBaseUrlConnectionFailureDoesNotFailOver() = runBlocking {
        assertFailsWith<IOException> {
            ApiClient.fetch<Payload>(endpoint = "/ping", baseUrl = deadUrl)
        }

        assertEquals(0, primary.requestCount)
        assertEquals(0, backup.requestCount)
        assertFalse(ApiState.isUsingBackup)
    }

    @Test
    fun explicitBaseUrlGatewayFailureSurfacesUnchanged() = runBlocking {
        primary.enqueue(gateway(521))

        val error = assertFailsWith<ApiError.RequestFailed> {
            ApiClient.fetch<Payload>(endpoint = "/ping", baseUrl = primary.baseUrl())
        }

        assertEquals(521, error.statusCode)
        assertEquals(0, backup.requestCount)
    }
}

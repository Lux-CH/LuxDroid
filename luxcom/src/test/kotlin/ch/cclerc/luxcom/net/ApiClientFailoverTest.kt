package ch.cclerc.luxcom.net

import java.io.IOException
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

    private fun MockWebServer.baseUrl(): String = url("/").toString().trimEnd('/')

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
    fun primaryConnectionFailureFailsOverToBackup() = runBlocking {
        ApiClient.primaryBaseUrl = deadUrl
        ApiClient.backupBaseUrl = backup.baseUrl()
        backup.enqueue(MockResponse().setBody("""{"ok":true}"""))

        val result: Payload = ApiClient.fetch(endpoint = "/ping")

        assertTrue(result.ok)
        assertEquals(1, backup.requestCount)
        val recorded = backup.takeRequest(2, TimeUnit.SECONDS)
        assertEquals("/v1/ping", recorded?.path)
        assertFalse(ApiState.primaryServerAvailable)
    }

    @Test
    fun markedDownPrimaryIsSkippedOnNextRequest() = runBlocking {
        ApiClient.primaryBaseUrl = deadUrl
        ApiClient.backupBaseUrl = backup.baseUrl()
        ApiState.markPrimaryDown()
        backup.enqueue(MockResponse().setBody("""{"ok":true}"""))

        val result: Payload = ApiClient.fetch(endpoint = "/ping")

        assertTrue(result.ok)
        assertEquals(1, backup.requestCount)
    }

    @Test
    fun http500FailsWithoutFailover() = runBlocking {
        ApiClient.primaryBaseUrl = primary.baseUrl()
        ApiClient.backupBaseUrl = backup.baseUrl()
        primary.enqueue(MockResponse().setResponseCode(500).setBody("boom"))

        val error = assertFailsWith<ApiError.RequestFailed> {
            ApiClient.fetch<Payload>(endpoint = "/ping")
        }

        assertEquals(500, error.statusCode)
        assertEquals("boom", error.description)
        assertEquals(1, primary.requestCount)
        assertEquals(0, backup.requestCount)
        assertTrue(ApiState.primaryServerAvailable)
    }

    @Test
    fun explicitBaseUrlBypassesResolution() = runBlocking {
        ApiClient.primaryBaseUrl = deadUrl
        ApiClient.backupBaseUrl = backup.baseUrl()
        primary.enqueue(MockResponse().setBody("""{"ok":true}"""))

        val result: Payload = ApiClient.fetch(endpoint = "/ping", baseUrl = primary.baseUrl())

        assertTrue(result.ok)
        assertEquals(1, primary.requestCount)
        assertEquals(0, backup.requestCount)
        assertTrue(ApiState.primaryServerAvailable)
    }

    @Test
    fun explicitBaseUrlConnectionFailureDoesNotFailOver() = runBlocking {
        ApiClient.primaryBaseUrl = primary.baseUrl()
        ApiClient.backupBaseUrl = backup.baseUrl()

        assertFailsWith<IOException> {
            ApiClient.fetch<Payload>(endpoint = "/ping", baseUrl = deadUrl)
        }

        assertEquals(0, primary.requestCount)
        assertEquals(0, backup.requestCount)
        assertTrue(ApiState.primaryServerAvailable)
    }
}

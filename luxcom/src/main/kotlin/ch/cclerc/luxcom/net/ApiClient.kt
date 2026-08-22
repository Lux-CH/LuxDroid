package ch.cclerc.luxcom.net

import ch.cclerc.luxcom.apiUrl
import ch.cclerc.luxcom.bckpApiUrl
import ch.cclerc.luxcom.serialization.LuxJson
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.coroutines.executeAsync

object ApiClient {
    internal var primaryBaseUrl: String = apiUrl
    internal var backupBaseUrl: String = bckpApiUrl

    private const val PRIMARY_RETRY_TIMEOUT_MS = 2_500L

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .fastFallback(true)
        .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
        .build()

    /** Shares the connection pool and dispatcher; only the call timeout differs. */
    private val retryClient: OkHttpClient = client.newBuilder()
        .callTimeout(PRIMARY_RETRY_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .build()

    /**
     * Optional hook reporting that the *device* has no usable network. luxcom has
     * no Context, so the app layer wires this to ConnectivityManager. When it says
     * we are offline there is nothing to fail over to, so primary is not blamed.
     */
    var isDeviceOffline: (() -> Boolean)? = null

    fun warmUp() {
        val request = Request.Builder()
            .url("$primaryBaseUrl/v1/geocode?text=a&language=fr")
            .head()
            .build()
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {}
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.close()
            }
        })
    }

    @PublishedApi
    internal val jsonMediaType = "application/json".toMediaType()

    suspend inline fun <reified T> fetch(
        endpoint: String,
        apiVersion: String = "v1",
        queryItems: List<Pair<String, String>> = emptyList(),
        baseUrl: String? = null,
        method: String = "GET",
        jsonBody: String? = null
    ): T {
        val body = performRequest(endpoint, apiVersion, queryItems, baseUrl, method, jsonBody)
        return withContext(Dispatchers.Default) {
            try {
                LuxJson.decodeFromString(body)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw ApiError.DecodingFailed(e)
            }
        }
    }

    internal fun crossBackendRetryUrl(): String =
        if (ApiState.isUsingBackup) primaryBaseUrl else backupBaseUrl

    /**
     * Cloudflare reports a dead tunnel as a perfectly valid HTTP response, so a
     * gateway status counts as the backend being unreachable rather than as an
     * answer from it. The error itself is still surfaced unchanged to callers.
     */
    private fun isGatewayFailure(statusCode: Int): Boolean =
        statusCode in 520..527 || statusCode == 502 || statusCode == 503 ||
            statusCode == 504 || statusCode == 530

    private fun isConnectionFailure(error: Throwable): Boolean = when (error) {
        is IOException -> true
        is ApiError.RequestFailed -> isGatewayFailure(error.statusCode)
        else -> false
    }

    @PublishedApi
    internal suspend fun performRequest(
        endpoint: String,
        apiVersion: String,
        queryItems: List<Pair<String, String>>,
        baseUrl: String?,
        method: String,
        jsonBody: String?
    ): String {
        if (baseUrl != null) {
            return executeRequest(endpoint, apiVersion, queryItems, baseUrl, method, jsonBody)
        }

        suspend fun requestBackup(): String =
            executeRequest(endpoint, apiVersion, queryItems, backupBaseUrl, method, jsonBody)

        suspend fun requestPrimary(useRetryTimeout: Boolean): String {
            try {
                val body = executeRequest(
                    endpoint, apiVersion, queryItems, primaryBaseUrl, method, jsonBody,
                    if (useRetryTimeout) retryClient else client
                )
                ApiState.markPrimaryReachable()
                return body
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // A non-connection error means the server answered: primary is up.
                if (!isConnectionFailure(e)) ApiState.markPrimaryReachable()
                throw e
            }
        }

        if (!ApiState.shouldUsePrimary()) return requestBackup()

        try {
            return requestPrimary(useRetryTimeout = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (!isConnectionFailure(e)) throw e
            if (isDeviceOffline?.invoke() == true) throw e

            if (ApiState.shouldRetryPrimary()) {
                try {
                    return requestPrimary(useRetryTimeout = true)
                } catch (retry: CancellationException) {
                    throw retry
                } catch (retry: Throwable) {
                    if (!isConnectionFailure(retry)) throw retry
                    if (isDeviceOffline?.invoke() == true) throw retry
                }
            }

            ApiState.recordPrimaryFailure()
            return requestBackup()
        }
    }

    private suspend fun executeRequest(
        endpoint: String,
        apiVersion: String,
        queryItems: List<Pair<String, String>>,
        baseUrl: String,
        method: String,
        jsonBody: String?,
        httpClient: OkHttpClient = client
    ): String {
        val url = "$baseUrl/$apiVersion$endpoint".toHttpUrlOrNull() ?: throw ApiError.InvalidUrl()
        val urlBuilder = url.newBuilder()
        for ((name, value) in queryItems) {
            urlBuilder.addQueryParameter(name, value)
        }
        val request = Request.Builder()
            .url(urlBuilder.build())
            .method(method, jsonBody?.toRequestBody(jsonMediaType))
            .build()
        httpClient.newCall(request).executeAsync().use { response ->
            val body = withContext(Dispatchers.IO) { response.body.string() }
            if (response.code !in 200..299) {
                throw ApiError.RequestFailed(response.code, body)
            }
            return body
        }
    }
}

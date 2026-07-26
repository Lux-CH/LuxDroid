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

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
        .build()

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
        if (ApiState.primaryServerAvailable) backupBaseUrl else primaryBaseUrl

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
        val usePrimary = ApiState.shouldUsePrimary()
        val resolvedUrl = if (usePrimary) primaryBaseUrl else backupBaseUrl
        try {
            return executeRequest(endpoint, apiVersion, queryItems, resolvedUrl, method, jsonBody)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            if (usePrimary) {
                ApiState.markPrimaryDown()
                return executeRequest(endpoint, apiVersion, queryItems, backupBaseUrl, method, jsonBody)
            }
            throw e
        }
    }

    private suspend fun executeRequest(
        endpoint: String,
        apiVersion: String,
        queryItems: List<Pair<String, String>>,
        baseUrl: String,
        method: String,
        jsonBody: String?
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
        client.newCall(request).executeAsync().use { response ->
            val body = withContext(Dispatchers.IO) { response.body.string() }
            if (response.code !in 200..299) {
                throw ApiError.RequestFailed(response.code, body)
            }
            return body
        }
    }
}

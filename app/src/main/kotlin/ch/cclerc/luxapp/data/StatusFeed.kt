package ch.cclerc.luxapp.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

@Serializable
data class LuxStatus(
    val dateOfUpdate: String,
    val message: String,
    val estimatedDateOfResolution: String? = null,
    val isMaintenance: Boolean
)

@Serializable
data class WarningMessage(
    val message: String,
    val icon: String,
    val show: Boolean
)

object StatusFeed {
    private const val BASE = "https://cclerc.ch/lux-status"
    const val SERVER_STATUS_URL = "https://lux.cronitorstatus.com"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun fetchStatus(): LuxStatus? = fetch("$BASE/status.json?t=${epochSeconds()}")

    suspend fun fetchWarningMessage(): WarningMessage? = fetch("$BASE/message.json?t=${epochSeconds()}")

    private fun epochSeconds(): Long = System.currentTimeMillis() / 1000

    private suspend inline fun <reified T> fetch(url: String): T? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                json.decodeFromString<T>(response.body.string())
            }
        }.getOrNull()
    }
}

package ch.cclerc.luxapp.domain.intelligence

import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

data class WeatherSnapshot(
    val temperature: Double,
    val precipitation: Double,
    val code: Int
) {
    val isWet: Boolean
        get() = precipitation >= 0.2 || code in wetCodes

    val isCold: Boolean get() = temperature <= 2
    val isHot: Boolean get() = temperature >= 30
    val isHarsh: Boolean get() = isWet || isCold || isHot

    val symbol: String
        get() = when (code) {
            0 -> "sun.max.fill"
            1, 2 -> "cloud.sun.fill"
            3 -> "cloud.fill"
            45, 48 -> "cloud.fog.fill"
            51, 53, 55, 56, 57 -> "cloud.drizzle.fill"
            65, 67, 82 -> "cloud.heavyrain.fill"
            61, 63, 66, 80, 81 -> "cloud.rain.fill"
            71, 73, 75, 77, 85, 86 -> "cloud.snow.fill"
            95, 96, 99 -> "cloud.bolt.rain.fill"
            else -> if (isWet) "cloud.rain.fill" else "cloud.fill"
        }

    val temperatureText: String get() = "${temperature.roundToInt()}°"

    private companion object {
        val wetCodes = setOf(51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 71, 73, 75, 77, 80, 81, 82, 85, 86, 95, 96, 99)
    }
}

object WeatherService {
    private class Forecast(
        val fetchedAt: Instant,
        val times: List<Instant>,
        val temperatures: List<Double>,
        val precipitation: List<Double>,
        val codes: List<Int>
    )

    @Serializable
    private class Response(val hourly: Hourly) {
        @Serializable
        class Hourly(
            val time: List<Long>,
            @SerialName("temperature_2m") val temperature: List<Double?>,
            val precipitation: List<Double?>,
            @SerialName("weather_code") val weatherCode: List<Int?>
        )
    }

    private const val LIFETIME_S = 20 * 60L

    private val cache = mutableMapOf<String, Forecast>()
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .callTimeout(4, TimeUnit.SECONDS)
            .build()
    }

    suspend fun snapshot(latitude: Double, longitude: Double, at: Instant): WeatherSnapshot? {
        if (abs(at.epochSecond - Instant.now().epochSecond) >= 2 * 24 * 3600) return null
        val lat = Math.round(latitude * 50) / 50.0
        val lon = Math.round(longitude * 50) / 50.0
        val key = "$lat,$lon"

        val forecast = mutex.withLock {
            val cached = cache[key]
            if (cached != null && Instant.now().epochSecond - cached.fetchedAt.epochSecond < LIFETIME_S) {
                cached
            } else {
                fetch(lat, lon)?.also { cache[key] = it }
            }
        } ?: return null

        val index = forecast.times.indexOfLast { !it.isAfter(at) }.takeIf { it >= 0 }
            ?: forecast.times.indices.firstOrNull()
            ?: return null
        return WeatherSnapshot(
            temperature = forecast.temperatures[index],
            precipitation = forecast.precipitation[index],
            code = forecast.codes[index]
        )
    }

    private suspend fun fetch(latitude: Double, longitude: Double): Forecast? = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://api.open-meteo.com/v1/forecast".toHttpUrl().newBuilder()
                .addQueryParameter("latitude", latitude.toString())
                .addQueryParameter("longitude", longitude.toString())
                .addQueryParameter("hourly", "temperature_2m,precipitation,weather_code")
                .addQueryParameter("past_days", "1")
                .addQueryParameter("forecast_days", "3")
                .addQueryParameter("timeformat", "unixtime")
                .build()
            client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                if (response.code != 200) return@use null
                val hourly = json.decodeFromString(Response.serializer(), response.body.string()).hourly
                val count = minOf(hourly.time.size, hourly.temperature.size, hourly.precipitation.size, hourly.weatherCode.size)
                val times = mutableListOf<Instant>()
                val temperatures = mutableListOf<Double>()
                val precipitation = mutableListOf<Double>()
                val codes = mutableListOf<Int>()
                for (index in 0 until count) {
                    val temperature = hourly.temperature[index] ?: continue
                    times.add(Instant.ofEpochSecond(hourly.time[index]))
                    temperatures.add(temperature)
                    precipitation.add(hourly.precipitation[index] ?: 0.0)
                    codes.add(hourly.weatherCode[index] ?: 0)
                }
                if (times.isEmpty()) null else Forecast(Instant.now(), times, temperatures, precipitation, codes)
            }
        }.getOrNull()
    }
}

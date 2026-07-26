package ch.cclerc.luxcom.luxtrip

import ch.cclerc.luxcom.model.Place
import ch.cclerc.luxcom.model.trip.Itinerary
import ch.cclerc.luxcom.model.trip.Leg
import ch.cclerc.luxcom.net.ApiClient
import java.io.IOException
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.coroutines.executeAsync

class ExpiredLinkException : IOException("Expired link")

class LuxTripException(message: String) : IOException(message)

object ItinerarySharer {
    private const val baseUrl = "https://lux-share.cclerc.ch"
    private const val userAgent = "Lux/1.0 (Android)"
    private const val maxFileSize = 51200
    private val octetStream = "application/octet-stream".toMediaType()

    suspend fun uploadItinerary(itinerary: Itinerary, expiresHours: Int): String {
        val data = LuxTripCodec.encode(itinerary)
        val body = MultipartBody.Builder(UUID.randomUUID().toString())
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", "Itineraire.luxtrip", data.toRequestBody(octetStream))
            .addFormDataPart("expires", expiresHours.toString())
            .build()
        val request = Request.Builder()
            .url(baseUrl)
            .header("User-Agent", userAgent)
            .post(body)
            .build()
        ApiClient.client.newCall(request).executeAsync().use { response ->
            if (response.code != 200) {
                throw LuxTripException("Upload failed with status ${response.code}")
            }
            val text = withContext(Dispatchers.IO) { response.body.string() }.trim()
            if (!text.startsWith(baseUrl) || !text.endsWith(".luxtrip")) {
                throw LuxTripException("Unexpected upload response")
            }
            return text.substringAfterLast('/').substringBefore('.')
        }
    }

    suspend fun downloadItinerary(id: String): Itinerary {
        val request = Request.Builder()
            .url("$baseUrl/$id.luxtrip")
            .header("User-Agent", userAgent)
            .build()
        ApiClient.client.newCall(request).executeAsync().use { response ->
            if (response.code == 404) {
                throw ExpiredLinkException()
            }
            if (response.code != 200) {
                throw LuxTripException("Download failed with status ${response.code}")
            }
            val data = withContext(Dispatchers.IO) { response.body.bytes() }
            if (data.size > maxFileSize) {
                throw LuxTripException("File too large")
            }
            val itinerary = LuxTripCodec.decode(data)
            if (!validateItinerary(itinerary)) {
                throw LuxTripException("Invalid itinerary")
            }
            return itinerary
        }
    }

    fun validateItinerary(itinerary: Itinerary): Boolean {
        val now = Instant.now()
        val oneYearFromNow = now.plusSeconds(365L * 24 * 60 * 60)
        val oneYearAgo = now.minusSeconds(365L * 24 * 60 * 60)
        if (itinerary.startTime < oneYearAgo || itinerary.startTime > oneYearFromNow) {
            return false
        }
        if (itinerary.endTime < oneYearAgo || itinerary.endTime > oneYearFromNow) {
            return false
        }
        if (itinerary.startTime > itinerary.endTime) {
            return false
        }
        if (itinerary.duration <= 0 || itinerary.duration > 86400) {
            return false
        }
        if (itinerary.legs.isEmpty() || itinerary.legs.size > 20) {
            return false
        }
        return itinerary.legs.all { validateLeg(it) }
    }

    private fun validateLeg(leg: Leg): Boolean {
        if (!validatePlace(leg.from) || !validatePlace(leg.to)) {
            return false
        }
        if (leg.duration < 0 || leg.duration > 86400) {
            return false
        }
        val stops = leg.intermediateStops
        if (stops != null && stops.size > 100) {
            return false
        }
        val headsign = leg.headsign
        if (headsign != null && headsign.length > 200) {
            return false
        }
        val routeShortName = leg.routeShortName
        if (routeShortName != null && routeShortName.length > 50) {
            return false
        }
        return true
    }

    private fun validatePlace(place: Place): Boolean {
        if (place.lat < -90.0 || place.lat > 90.0) {
            return false
        }
        if (place.lon < -180.0 || place.lon > 180.0) {
            return false
        }
        return place.name.length <= 300
    }
}

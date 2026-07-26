package ch.cclerc.luxapp.domain.search

import ch.cclerc.luxcom.model.LocationType
import ch.cclerc.luxcom.model.SearchResult
import ch.cclerc.luxcom.net.ApiClient
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

class PhotonPoiSearchProvider(
    private val client: OkHttpClient = ApiClient.client,
    private val debounceMillis: Long = DEBOUNCE_MILLIS
) : PoiSearchProvider {

    private val json = Json { ignoreUnknownKeys = true }
    private val cache = LruCache<String, PoiSearchOutcome>(CACHE_CAPACITY)

    override suspend fun search(query: String, userLat: Double?, userLon: Double?): PoiSearchOutcome {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return PoiSearchOutcome()

        val cacheKey = cacheKey(trimmed, userLat, userLon)
        cache.get(cacheKey)?.let { return it }

        if (PhotonRateLimitState.isRateLimited()) {
            return PoiSearchOutcome(wasRateLimited = true)
        }

        delay(debounceMillis)

        val body = fetch(trimmed, userLat, userLon) ?: return PoiSearchOutcome(wasRateLimited = true)

        val outcome = runCatching { parse(body) }.getOrElse { return PoiSearchOutcome(wasRateLimited = true) }
        cache.put(cacheKey, outcome)
        return outcome
    }

    private suspend fun fetch(query: String, userLat: Double?, userLon: Double?): String? {
        val url = ENDPOINT.toHttpUrl().newBuilder().apply {
            addQueryParameter("q", query)
            addQueryParameter("lang", LANGUAGE)
            addQueryParameter("limit", LIMIT.toString())
            if (userLat != null && userLon != null) {
                addQueryParameter("lat", userLat.toString())
                addQueryParameter("lon", userLon.toString())
            }
        }.build()

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .get()
            .build()

        return try {
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (response.code == 429 || response.code == 503) {
                        PhotonRateLimitState.markRateLimited(retryAfterSeconds(response.header("Retry-After")))
                        return@use null
                    }
                    if (!response.isSuccessful) return@use null
                    response.body.string()
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: IOException) {
            null
        }
    }

    private fun parse(body: String): PoiSearchOutcome {
        val root = json.parseToJsonElement(body).jsonObject
        val features = root["features"] as? JsonArray ?: return PoiSearchOutcome()

        val results = mutableListOf<SearchResult>()
        val styles = mutableMapOf<String, SearchResultVisualStyle>()
        val seenIds = mutableSetOf<String>()

        for (element in features) {
            val feature = element as? JsonObject ?: continue
            val properties = feature["properties"]?.jsonObject ?: continue

            val osmKey = properties.string("osm_key")
            val osmValue = properties.string("osm_value")
            if (isTransitFeature(osmKey, osmValue)) continue

            val coordinates = feature["geometry"]?.jsonObject?.get("coordinates") as? JsonArray ?: continue
            if (coordinates.size < 2) continue
            val lon = coordinates[0].jsonPrimitive.content.toDoubleOrNull() ?: continue
            val lat = coordinates[1].jsonPrimitive.content.toDoubleOrNull() ?: continue
            if (!SearchResultScorer.isValidCoordinate(lat, lon)) continue

            val street = properties.string("street")
            val houseNumber = properties.string("housenumber")
            val zip = properties.string("postcode")
            val name = displayName(properties, street, houseNumber)
            if (name.isEmpty()) continue

            val identifier = identifier(properties, lat, lon, name)
            if (!seenIds.add(identifier)) continue

            val index = results.size
            results.add(
                SearchResult(
                    type = locationType(osmKey, street, houseNumber, zip),
                    tokens = emptyList(),
                    name = name,
                    id = identifier,
                    lat = lat,
                    lon = lon,
                    street = street,
                    houseNumber = houseNumber,
                    zip = zip,
                    areas = areas(properties),
                    score = maxOf(0.01, 1.0 - index * 0.01)
                )
            )
            styles[identifier] = OsmCategoryStyleMapper.style(osmKey, osmValue)
        }

        return PoiSearchOutcome(results = results, styles = styles, wasRateLimited = false)
    }

    private fun isTransitFeature(osmKey: String?, osmValue: String?): Boolean {
        val key = osmKey?.lowercase()
        if (key == "public_transport" || key == "railway" || key == "aerialway") return true
        val value = osmValue?.lowercase()
        return value == "bus_stop" || value == "bus_station" || value == "ferry_terminal"
    }

    private fun locationType(
        osmKey: String?,
        street: String?,
        houseNumber: String?,
        zip: String?
    ): LocationType {
        val key = osmKey?.lowercase()
        if (key != null && key in POI_KEYS) return LocationType.PLACE
        if (street != null || houseNumber != null || zip != null) return LocationType.ADDRESS
        return LocationType.PLACE
    }

    private fun displayName(properties: JsonObject, street: String?, houseNumber: String?): String {
        properties.string("name")?.let { return it }

        if (street != null) {
            val prefix = "$street ${houseNumber.orEmpty()}".trim()
            if (prefix.isNotEmpty()) return prefix
        }

        return properties.string("city")
            ?: properties.string("district")
            ?: properties.string("state")
            ?: ""
    }

    private fun identifier(properties: JsonObject, lat: Double, lon: Double, name: String): String {
        val osmType = properties.string("osm_type")
        val osmId = properties["osm_id"]?.jsonPrimitive?.content
        if (osmType != null && osmId != null) return "photon:$osmType:$osmId"
        return "photon:%.6f,%.6f:%s".format(lat, lon, name.lowercase())
    }

    private fun areas(properties: JsonObject): List<SearchResult.Area> {
        val seen = mutableSetOf<String>()
        val items = mutableListOf<Pair<String, Int>>()

        fun append(value: String?, level: Int) {
            val cleaned = value?.trim()?.takeIf { it.isNotEmpty() } ?: return
            if (!seen.add(cleaned.lowercase())) return
            items.add(cleaned to level)
        }

        append(properties.string("city"), 8)
        append(properties.string("district"), 9)
        append(properties.string("county"), 6)
        append(properties.string("state"), 4)
        append(properties.string("country"), 2)

        return items.mapIndexed { index, item ->
            SearchResult.Area(
                name = item.first,
                adminLevel = item.second,
                matched = index == 0,
                default = if (index == 0) true else null
            )
        }
    }

    private fun cacheKey(query: String, userLat: Double?, userLon: Double?): String {
        val lat = userLat?.let { "%.2f".format(it) } ?: "-"
        val lon = userLon?.let { "%.2f".format(it) } ?: "-"
        return "${query.lowercase()}|$lat|$lon"
    }

    private fun retryAfterSeconds(header: String?): Double? = header?.trim()?.toDoubleOrNull()

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotEmpty() }

    private class LruCache<K, V>(private val capacity: Int) {
        private val map = object : LinkedHashMap<K, V>(capacity, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean = size > capacity
        }

        @Synchronized
        fun get(key: K): V? = map[key]

        @Synchronized
        fun put(key: K, value: V) {
            map[key] = value
        }
    }

    companion object {
        private const val ENDPOINT = "https://photon.komoot.io/api"
        private const val LANGUAGE = "fr"
        private const val LIMIT = 10
        private const val DEBOUNCE_MILLIS = 300L
        private const val CACHE_CAPACITY = 50
        private const val USER_AGENT = "Lux-Android/0.8.2"

        private val POI_KEYS = setOf(
            "amenity", "shop", "tourism", "leisure", "aeroway", "historic", "sport",
            "office", "craft", "healthcare", "natural", "man_made", "emergency",
            "military", "club", "place"
        )
    }
}

object PhotonRateLimitState {
    private var rateLimitedUntilMillis: Long? = null

    @Synchronized
    fun isRateLimited(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val until = rateLimitedUntilMillis ?: return false
        return nowMillis < until
    }

    @Synchronized
    fun markRateLimited(resetAfterSeconds: Double?) {
        val resetSeconds = maxOf(1.0, resetAfterSeconds ?: 4.0)
        val newLimit = System.currentTimeMillis() + (resetSeconds * 1000).toLong()
        val current = rateLimitedUntilMillis
        if (current == null || newLimit > current) {
            rateLimitedUntilMillis = newLimit
        }
    }
}

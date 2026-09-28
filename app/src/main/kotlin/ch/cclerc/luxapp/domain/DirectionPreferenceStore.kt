package ch.cclerc.luxapp.domain

import ch.cclerc.luxapp.data.AppDirectories
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.pow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

class DirectionPreferenceStore private constructor() {
    enum class TimeBucket(val raw: String) {
        WEEKDAY_MORNING("weekdayMorning"),
        WEEKDAY_EVENING("weekdayEvening"),
        WEEKEND("weekend");

        companion object {
            fun of(date: Instant): TimeBucket {
                val local = date.atZone(ZoneId.systemDefault())
                if (local.dayOfWeek == DayOfWeek.SATURDAY || local.dayOfWeek == DayOfWeek.SUNDAY) return WEEKEND
                return if (local.hour in 4 until 12) WEEKDAY_MORNING else WEEKDAY_EVENING
            }
        }
    }

    @Serializable
    private data class Entry(val score: Double, val updatedAt: Long)

    private val lock = Any()
    private val writer = Executors.newSingleThreadExecutor()
    private val entries: MutableMap<String, Entry> = load()

    fun record(stopId: String, route: String, headsignKey: String, date: Instant, points: Double) {
        val key = key(stopId, route, headsignKey, TimeBucket.of(date))
        synchronized(lock) {
            val current = entries[key]?.let { decayed(it, date) } ?: 0.0
            entries[key] = Entry(current + points, date.toEpochMilli())
        }
        persist()
    }

    fun score(stopId: String, route: String, headsignKey: String, date: Instant): Double {
        val key = key(stopId, route, headsignKey, TimeBucket.of(date))
        return synchronized(lock) { entries[key]?.let { decayed(it, date) } ?: 0.0 }
    }

    private fun persist() {
        writer.execute {
            val now = Instant.now()
            val snapshot = synchronized(lock) {
                entries.entries.removeAll { decayed(it.value, now) < PRUNE_THRESHOLD }
                entries.toMap()
            }
            runCatching { file().writeText(json.encodeToString(serializer, snapshot)) }
        }
    }

    private fun load(): MutableMap<String, Entry> =
        runCatching { json.decodeFromString(serializer, file().readText()).toMutableMap() }.getOrElse { mutableMapOf() }

    private fun file(): File = File(AppDirectories.base(), "directionPreferences.json")

    companion object {
        val shared: DirectionPreferenceStore by lazy { DirectionPreferenceStore() }

        private const val HALF_LIFE_MILLIS = 30.0 * 24 * 3600 * 1000
        private const val PRUNE_THRESHOLD = 0.05
        private val json = Json { ignoreUnknownKeys = true }
        private val serializer = MapSerializer(String.serializer(), Entry.serializer())

        private fun decayed(entry: Entry, date: Instant): Double {
            val elapsed = max(0L, date.toEpochMilli() - entry.updatedAt).toDouble()
            return entry.score * 0.5.pow(elapsed / HALF_LIFE_MILLIS)
        }

        private fun key(stopId: String, route: String, headsignKey: String, bucket: TimeBucket): String =
            "$stopId|${route.uppercase()}|$headsignKey|${bucket.raw}"
    }
}

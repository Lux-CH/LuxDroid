package ch.cclerc.luxcom.station

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StationLayout(
    val uic: Int,
    val name: String? = null,
    val lat: Double,
    val lon: Double,
    val levels: List<Double> = emptyList(),
    val tracks: List<Track> = emptyList(),
    val platforms: List<Platform>? = null,
    val rails: List<Rail>? = null,
    val access: List<Access>? = null,
    val empty: Boolean = false
) {
    @Serializable
    data class Access(
        val kind: Kind,
        val lat: Double,
        val lon: Double,
        val line: List<List<Double>>? = null,
        val ring: List<List<Double>>? = null,
        val width: Double? = null
    ) {
        @Serializable
        enum class Kind {
            @SerialName("stairs") STAIRS,
            @SerialName("escalator") ESCALATOR,
            @SerialName("elevator") ELEVATOR
        }
    }

    @Serializable
    data class Rail(
        val track: String? = null,
        val siding: Boolean? = null,
        val line: List<List<Double>>
    )

    @Serializable
    data class Track(
        val track: String,
        val edges: List<List<List<Double>>> = emptyList(),
        val stopIds: List<String> = emptyList(),
        val sectors: List<Sector> = emptyList(),
        val platform: Platform? = null,
        val lat: Double,
        val lon: Double
    )

    @Serializable
    data class Sector(
        val s: String,
        val lat: Double,
        val lon: Double
    )

    @Serializable
    data class Platform(
        val name: String? = null,
        val type: String? = null,
        val length: Double? = null,
        val tracks: List<String>? = null,
        val ring: List<List<Double>>? = null
    )

    fun track(named: String?, stopId: String?): Track? {
        val name = named?.let(::normalizedTrack)
        if (!name.isNullOrEmpty()) {
            tracks.firstOrNull { it.track == name }?.let { return it }
        }
        if (stopId == null) return null
        return tracks.firstOrNull { stopId in it.stopIds }
    }

    companion object {
        fun normalizedTrack(track: String): String {
            val trimmed = track.trim()
            val stripped = trimmed.dropWhile { it == '0' }
            return stripped.ifEmpty { trimmed }
        }

        fun uic(fromStopId: String?): Int? {
            if (fromStopId == null) return null
            val index = fromStopId.indexOf("sloid:")
            if (index < 0) return null
            val digits = fromStopId.substring(index + 6).takeWhile { it.isDigit() }
            if (digits.length !in 1..6) return null
            val number = digits.toIntOrNull() ?: return null
            return 8_500_000 + number
        }
    }
}

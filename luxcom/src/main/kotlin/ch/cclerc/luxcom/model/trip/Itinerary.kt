package ch.cclerc.luxcom.model.trip

import ch.cclerc.luxcom.serialization.InstantIso8601Serializer
import java.time.Instant
import kotlinx.serialization.Serializable

@Serializable
data class Itinerary(
    val duration: Int,
    @Serializable(with = InstantIso8601Serializer::class) val startTime: Instant,
    @Serializable(with = InstantIso8601Serializer::class) val endTime: Instant,
    val transfers: Int,
    val legs: List<Leg>
) {
    fun equalsByContent(other: Itinerary): Boolean {
        return startTime == other.startTime &&
            endTime == other.endTime &&
            duration == other.duration &&
            transfers == other.transfers &&
            legs.size == other.legs.size
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Itinerary) return false
        return equalsByContent(other)
    }

    override fun hashCode(): Int {
        var result = duration
        result = 31 * result + startTime.hashCode()
        result = 31 * result + endTime.hashCode()
        result = 31 * result + transfers
        result = 31 * result + legs.size
        return result
    }
}

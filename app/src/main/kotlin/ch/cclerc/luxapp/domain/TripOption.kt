package ch.cclerc.luxapp.domain

import java.time.Instant

data class TripOption(
    val id: String,
    val startTime: Instant
) {
    override fun equals(other: Any?): Boolean = other is TripOption && other.id == id

    override fun hashCode(): Int = id.hashCode()
}

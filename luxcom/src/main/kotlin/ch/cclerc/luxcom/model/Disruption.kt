@file:UseSerializers(InstantIso8601Serializer::class)

package ch.cclerc.luxcom.model

import ch.cclerc.luxcom.serialization.InstantIso8601Serializer
import java.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

@Serializable
data class Disruption(
    val line: String,
    val lineBackground: String,
    val lineDisruption: String,
    val agencyId: String? = null,
    val tripIds: List<String>? = null,
    val stopIds: List<String>? = null,
    val sourceId: String? = null,
    val title: String? = null,
    val text: String? = null,
    val periods: List<Period>? = null
) {
    val id: String
        get() = sourceId ?: lineDisruption

    @Serializable
    data class Period(
        val from: Instant? = null,
        val until: Instant? = null
    )

    fun isActive(from: Instant, to: Instant): Boolean {
        val periods = periods
        if (periods.isNullOrEmpty()) return true
        return periods.any { (it.from ?: Instant.MIN) <= to && from <= (it.until ?: Instant.MAX) }
    }
}

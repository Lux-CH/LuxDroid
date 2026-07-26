@file:UseSerializers(InstantIso8601Serializer::class)

package ch.cclerc.luxcom.model

import ch.cclerc.luxcom.serialization.InstantIso8601Serializer
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

@Serializable
data class Place(
    val name: String,
    val stopId: String? = null,
    val parentId: String? = null,
    val lat: Double,
    val lon: Double,
    val level: Double = 0.0,
    val arrival: Instant? = null,
    val departure: Instant? = null,
    val scheduledArrival: Instant? = null,
    val scheduledDeparture: Instant? = null,
    val scheduledTrack: String? = null,
    val track: String? = null,
    val vertexType: VertexType,
    val modes: List<TransportationMode> = emptyList(),
    val importance: Double? = null
) {
    val id: String
        get() = stopId ?: UUID.randomUUID().toString()

    @Serializable
    enum class VertexType {
        NORMAL,
        BIKESHARE,
        TRANSIT
    }
}

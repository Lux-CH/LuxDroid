package ch.cclerc.luxcom.model.feedback

import kotlinx.serialization.Serializable

@Serializable
data class InfoResponse(
    val average: Map<String, AttributeInfo>,
    val rt: Map<String, RealtimeInfo>? = null
) {
    @Serializable
    data class AttributeInfo(
        val level: Double,
        val trustLevel: Double,
        val reportCount: Int,
        val totalWeight: Double,
        val projection: Double? = null,
        val source: String? = null
    )

    @Serializable
    data class RealtimeInfo(
        val level: Int,
        val trustLevel: Int,
        val timestamp: Long,
        val distance: Double
    )
}

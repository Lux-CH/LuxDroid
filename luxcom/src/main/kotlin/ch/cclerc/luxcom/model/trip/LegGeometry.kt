package ch.cclerc.luxcom.model.trip

import kotlinx.serialization.Serializable

@Serializable
data class LegGeometry(
    val points: String,
    val length: Int
)

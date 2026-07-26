package ch.cclerc.luxcom.model

import kotlinx.serialization.Serializable

@Serializable
data class Disruption(
    val line: String,
    val lineBackground: String,
    val lineDisruption: String
) {
    val id: String
        get() = lineDisruption
}

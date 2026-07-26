package ch.cclerc.luxcom.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class PedestrianProfile {
    @SerialName("FOOT")
    FOOT,
    @SerialName("WHEELCHAIR")
    WHEELCHAIR
}

package ch.cclerc.luxcom.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class LocationType {
    @SerialName("ADDRESS")
    ADDRESS,
    @SerialName("PLACE")
    PLACE,
    @SerialName("STOP")
    STOP
}

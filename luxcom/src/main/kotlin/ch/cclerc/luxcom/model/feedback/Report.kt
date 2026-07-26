package ch.cclerc.luxcom.model.feedback

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ReportAttribute {
    @SerialName("crowd")
    CROWD,
    @SerialName("smell")
    SMELL,
    @SerialName("clean")
    CLEAN,
    @SerialName("heat")
    HEAT,
    @SerialName("noise")
    NOISE
}

@Serializable
data class Report(
    val tripId: String,
    val routeShortName: String,
    val latitude: Double,
    val longitude: Double,
    val attribute: ReportAttribute,
    val level: Int
)

@Serializable
data class ReportResponse(
    val success: Boolean,
    val message: String
)

package ch.cclerc.luxcom.model.trip

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Direction {
    @SerialName("DEPART") depart,
    @SerialName("HARD_LEFT") hardLeft,
    @SerialName("LEFT") left,
    @SerialName("SLIGHTLY_LEFT") slightlyLeft,
    @SerialName("CONTINUE") continueStraight,
    @SerialName("SLIGHTLY_RIGHT") slightlyRight,
    @SerialName("RIGHT") right,
    @SerialName("HARD_RIGHT") hardRight,
    @SerialName("CIRCLE_CLOCKWISE") circleClockwise,
    @SerialName("CIRCLE_COUNTERCLOCKWISE") circleCounterClockwise,
    @SerialName("STAIRS") stairs,
    @SerialName("ELEVATOR") elevator,
    @SerialName("UTURN_LEFT") uturnLeft,
    @SerialName("UTURN_RIGHT") uturnRight
}

@Serializable
data class StepInstruction(
    val relativeDirection: Direction,
    val distance: Double,
    val fromLevel: Double,
    val toLevel: Double,
    val osmWay: Int? = null,
    val streetName: String,
    val exit: String,
    val stayOn: Boolean,
    val area: Boolean,
    val polyline: StepPolyline
)

@Serializable
data class StepPolyline(
    val points: String,
    val precision: Int,
    val length: Int
)

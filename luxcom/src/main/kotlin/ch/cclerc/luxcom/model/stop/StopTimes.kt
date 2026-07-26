package ch.cclerc.luxcom.model.stop

import kotlinx.serialization.Serializable

@Serializable
data class StopTimes(
    val stopTimes: List<StopTime>,
    val previousPageCursor: String,
    val nextPageCursor: String
)

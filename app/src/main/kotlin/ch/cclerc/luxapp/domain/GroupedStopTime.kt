package ch.cclerc.luxapp.domain

import ch.cclerc.luxcom.model.stop.StopTime

data class GroupedStopTime(
    val routeShortName: String,
    val headsign: String,
    val stopTimes: List<StopTime>
) {
    val id: String
        get() = "$routeShortName|$headsign"
}

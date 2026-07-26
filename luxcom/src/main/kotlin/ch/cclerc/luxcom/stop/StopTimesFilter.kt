package ch.cclerc.luxcom.stop

import ch.cclerc.luxcom.geo.StopGrouping
import ch.cclerc.luxcom.model.Place
import ch.cclerc.luxcom.model.stop.StopTime
import ch.cclerc.luxcom.model.stop.StopTimes
import kotlin.math.PI
import kotlin.math.cos

fun StopTimes.filteredToStation(
    stopId: String,
    name: String? = null,
    lat: Double? = null,
    lon: Double? = null,
    servesRail: Boolean = false,
    groupedStopIds: List<String> = emptyList()
): StopTimes {
    val targetName = name?.let { StopGrouping.normalizedName(it) }
    val memberIds = groupedStopIds.toSet()

    fun isHome(st: StopTime): Boolean {
        if (st.place.parentId == stopId || st.place.stopId == stopId) {
            return true
        }

        if (memberIds.isNotEmpty()) {
            val id = st.place.parentId ?: st.place.stopId
            if (id != null && memberIds.contains(id)) {
                return true
            }
        }

        if (targetName == null || lat == null || lon == null) return false
        return StopGrouping.isSameStopNormalized(
            st.place.name, st.place.lat, st.place.lon,
            targetName, lat, lon
        )
    }

    val homeFlags = stopTimes.map { isHome(it) }
    val home = mutableListOf<StopTime>()
    val extras = mutableListOf<StopTime>()
    for ((index, stopTime) in stopTimes.withIndex()) {
        if (homeFlags[index]) home.add(stopTime) else extras.add(stopTime)
    }
    if (extras.isEmpty() || home.isEmpty()) return this

    val homeServesRail = servesRail || home.any { it.mode.isRail }
    val keepExtra: (StopTime) -> Boolean

    if (!homeServesRail) {
        val servesStation = name?.let { StopGrouping.isStationForecourt(it) } ?: true
        keepExtra = if (servesStation) { st -> st.mode.isRail } else { _ -> false }
    } else {
        val localExtras = extras.filter { !it.mode.isRail }
        val forecourtIds = localExtras
            .filter { StopGrouping.isStationForecourt(it.place.name) }
            .mapNotNull { it.place.parentId ?: it.place.stopId }
            .toSet()
        if (forecourtIds.isNotEmpty()) {
            keepExtra = { stopTime ->
                if (stopTime.mode.isRail) {
                    false
                } else {
                    val id = stopTime.place.parentId ?: stopTime.place.stopId
                    id != null && forecourtIds.contains(id)
                }
            }
        } else {
            val nearestStation = if (lat != null && lon != null) {
                localExtras
                    .minByOrNull { squaredDistance(it.place, lat, lon) }
                    ?.let { it.place.parentId ?: it.place.stopId }
            } else {
                null
            }
            keepExtra = if (nearestStation != null) {
                { stopTime ->
                    if (stopTime.mode.isRail) {
                        false
                    } else {
                        val id = stopTime.place.parentId ?: stopTime.place.stopId
                        id != null && id == nearestStation
                    }
                }
            } else {
                { stopTime -> !stopTime.mode.isRail }
            }
        }
    }

    return StopTimes(
        stopTimes = stopTimes.filterIndexed { index, stopTime ->
            homeFlags[index] || keepExtra(stopTime)
        },
        previousPageCursor = previousPageCursor,
        nextPageCursor = nextPageCursor
    )
}

private fun squaredDistance(place: Place, lat: Double, lon: Double): Double {
    val dLat = place.lat - lat
    val dLon = (place.lon - lon) * cos(lat * PI / 180)
    return dLat * dLat + dLon * dLon
}

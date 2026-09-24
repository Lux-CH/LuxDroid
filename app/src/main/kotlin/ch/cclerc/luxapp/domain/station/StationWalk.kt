package ch.cclerc.luxapp.domain.station

import ch.cclerc.luxapp.domain.onboard.isTransit
import ch.cclerc.luxcom.model.Place
import ch.cclerc.luxcom.model.trip.Leg
import ch.cclerc.luxcom.station.StationLayout

data class StationWalk(
    val kind: Kind,
    val uic: Int,
    val fromTrack: String?,
    val toTrack: String?
) {
    enum class Kind { TRANSFER, ENTERING, LEAVING }

    companion object {
        fun of(legs: List<Leg>, index: Int): StationWalk? {
            if (index !in legs.indices || legs[index].isTransit) return null
            val arrival = if (index > 0 && legs[index - 1].mode.isMainlineRail) legs[index - 1].to else null
            val departure = if (index + 1 < legs.size && legs[index + 1].mode.isMainlineRail) legs[index + 1].from else null
            val arrivalStation = StationLayout.uic(arrival?.stopId)
            val departureStation = StationLayout.uic(departure?.stopId)

            val kind: Kind
            val uic: Int
            if (arrivalStation != null && arrivalStation == departureStation) {
                kind = Kind.TRANSFER
                uic = arrivalStation
            } else if (departureStation != null) {
                kind = Kind.ENTERING
                uic = departureStation
            } else if (arrivalStation != null) {
                kind = Kind.LEAVING
                uic = arrivalStation
            } else {
                return null
            }
            return StationWalk(
                kind = kind,
                uic = uic,
                fromTrack = if (kind == Kind.ENTERING) null else arrival?.let(::track),
                toTrack = if (kind == Kind.LEAVING) null else departure?.let(::track)
            )
        }

        private fun track(place: Place): String? =
            (place.track ?: place.scheduledTrack)?.trim()?.takeIf { it.isNotEmpty() }

        fun trackPhrase(track: String): String =
            if (track.toIntOrNull() != null) "la voie $track" else "le quai $track"
    }
}

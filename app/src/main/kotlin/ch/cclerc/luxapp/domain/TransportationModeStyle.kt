package ch.cclerc.luxapp.domain

import ch.cclerc.luxcom.model.TransportationMode
import ch.cclerc.luxcom.model.stop.StopTime

val TransportationMode.symbolName: String
    get() = when (this) {
        TransportationMode.TRAM -> "tram"
        TransportationMode.FERRY -> "ferry"
        else -> if (isRail) "tram.tunnel.fill" else "bus"
    }

val TransportationMode.dwellTime: Double
    get() = when (this) {
        TransportationMode.BUS, TransportationMode.TRAM -> 25.0
        TransportationMode.FERRY -> 50.0
        else -> if (isRail) 50.0 else 30.0
    }

val StopTime.displayBufferTime: Double
    get() {
        if (!mode.isRail && mode != TransportationMode.FERRY) return 50.0

        val arrival = place.arrival
        val departure = place.departure
        if (arrival != null && departure != null && arrival != departure) {
            val dwell = (departure.toEpochMilli() - arrival.toEpochMilli()) / 1000.0
            if (dwell > 0) return dwell
        }

        return 60.0
    }

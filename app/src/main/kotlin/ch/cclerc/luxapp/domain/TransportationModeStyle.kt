package ch.cclerc.luxapp.domain

import androidx.compose.ui.graphics.Color
import ch.cclerc.luxapp.ui.theme.LuxColors
import ch.cclerc.luxcom.model.Place
import ch.cclerc.luxcom.model.TransportationMode
import ch.cclerc.luxcom.model.stop.StopTime
import java.time.Duration

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

enum class Punctuality {
    CANCELLED,
    SCHEDULED,
    ON_TIME,
    EARLY,
    LATE;

    fun color(colors: LuxColors): Color = when (this) {
        CANCELLED -> colors.systemRed
        SCHEDULED -> colors.label
        ON_TIME -> colors.systemGreen
        EARLY -> colors.systemCyan
        LATE -> colors.systemYellow
    }

    fun borderColor(colors: LuxColors): Color = when (this) {
        CANCELLED -> colors.systemRed
        SCHEDULED -> colors.separator
        ON_TIME, EARLY, LATE -> color(colors).copy(alpha = 0.5f)
    }

    fun highlightColor(colors: LuxColors): Color? = when (this) {
        CANCELLED, EARLY, LATE -> color(colors)
        SCHEDULED, ON_TIME -> null
    }
}

fun Place.scheduledDifference(): Int {
    val scheduled = scheduledDeparture ?: scheduledArrival ?: return 0
    val effective = departure ?: arrival ?: return 0
    return Duration.between(scheduled, effective).toMinutes().toInt()
}

fun Place.punctuality(realTime: Boolean, cancelled: Boolean = false): Punctuality {
    if (cancelled) return Punctuality.CANCELLED
    if (!realTime) return Punctuality.SCHEDULED

    val difference = scheduledDifference()
    if (difference < 0) return Punctuality.EARLY
    if (difference >= 2) return Punctuality.LATE
    return Punctuality.ON_TIME
}

fun StopTime.punctuality(): Punctuality = place.punctuality(realTime, cancelled)

package ch.cclerc.luxapp.ui.stop.expanded

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ch.cclerc.luxapp.data.Settings
import ch.cclerc.luxapp.domain.GroupedStopTime
import ch.cclerc.luxapp.domain.TripOption
import ch.cclerc.luxapp.ui.anim.NumericText
import ch.cclerc.luxapp.ui.anim.scaleClickable
import ch.cclerc.luxapp.ui.anim.staggeredEntrance
import ch.cclerc.luxapp.ui.theme.LuxShapes
import ch.cclerc.luxapp.ui.theme.LuxSprings
import ch.cclerc.luxapp.ui.theme.LuxTheme
import ch.cclerc.luxcom.model.stop.StopTime
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlinx.coroutines.delay

private val departureTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

internal fun relativeTimeLabel(date: Instant?, now: Instant): String {
    if (date == null) return "N/A"
    val duration = Duration.between(now, date)
    val minutes = duration.toMinutes()
    val seconds = duration.seconds

    return when {
        minutes < 0 -> "passé"
        minutes == 0L -> "maintenant"
        minutes < 60 -> "dans ${ceil(seconds.toDouble() / 60.0).toInt()} min"
        else -> {
            val hours = minutes / 60
            val remainingMinutes = minutes % 60
            if (hours < 24) {
                if (remainingMinutes == 0L) "dans ${hours}h" else "dans ${hours}h ${remainingMinutes}min"
            } else {
                val days = hours / 24
                val remainingHours = hours % 24
                val plural = if (days > 1) "s" else ""
                var result = "dans $days jour$plural"
                if (remainingHours > 0) result += " ${remainingHours}h"
                result
            }
        }
    }
}

@Composable
fun DepartureTimeRow(
    stopTime: StopTime,
    group: GroupedStopTime,
    index: Int,
    animateIn: Boolean,
    playEntrance: Boolean,
    onOpenTrip: (String, List<TripOption>) -> Unit,
    onSelectLine: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LuxTheme.colors
    val zone = remember { ZoneId.systemDefault() }
    var now by remember { mutableStateOf(Instant.now()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = Instant.now()
            delay(5000)
        }
    }

    val departure = stopTime.place.departure ?: stopTime.place.arrival
    val scheduledDeparture = stopTime.place.scheduledDeparture ?: stopTime.place.scheduledArrival

    val latenessReference = stopTime.place.scheduledDeparture
        ?: stopTime.place.scheduledArrival ?: now
    val latenessActual = stopTime.place.departure ?: stopTime.place.arrival ?: now
    val latenessDifference = Duration.between(latenessReference, latenessActual).toMinutes().toInt()

    val latenessColor: Color = when {
        stopTime.cancelled -> colors.systemRed
        !stopTime.realTime -> colors.label
        latenessDifference < 2 && latenessDifference >= -1 -> colors.systemGreen
        else -> colors.systemYellow
    }

    val borderColor: Color = when {
        stopTime.cancelled -> colors.systemRed
        !stopTime.realTime -> colors.separator
        latenessDifference < 2 && latenessDifference >= -1 -> colors.systemGreen.copy(alpha = 0.5f)
        else -> colors.systemYellow.copy(alpha = 0.5f)
    }

    val showDelay = Settings.showDelayInsteadOfDirectTime
    val primaryColor = if (showDelay) colors.label else latenessColor

    Column(
        modifier = modifier
            .then(
                if (playEntrance) {
                    Modifier.staggeredEntrance(
                        index = index,
                        visible = animateIn,
                        delayPerItemMs = 50,
                        baseDelayMs = 100,
                        fromOffsetY = 0.dp,
                        fromScale = 0.9f,
                        spec = LuxSprings.Emphatic
                    )
                } else {
                    Modifier
                }
            )
            .background(colors.secondarySystemBackground, RoundedCornerShape(LuxShapes.r8))
            .border(
                width = if (stopTime.cancelled) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(LuxShapes.r8)
            )
            .scaleClickable {
                onSelectLine(group.routeShortName)
                onOpenTrip(stopTime.tripId, otherTripOptions(group))
            }
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (departure != null && scheduledDeparture != null) {
                val isNextDay = departure.atZone(zone).toLocalDate() != now.atZone(zone).toLocalDate()
                val shown = if (showDelay) scheduledDeparture else departure

                NumericText(
                    text = departureTimeFormatter.format(shown.atZone(zone)),
                    style = LuxTheme.type.timeVariant(LuxTheme.type.headline).copy(
                        fontWeight = FontWeight.Bold,
                        textDecoration = if (stopTime.cancelled) TextDecoration.LineThrough else null
                    ),
                    color = primaryColor
                )

                if (isNextDay) {
                    Text(
                        text = "*",
                        style = LuxTheme.type.caption2,
                        color = primaryColor
                    )
                }

                if (showDelay && !stopTime.cancelled && stopTime.realTime) {
                    val scheduledDifference =
                        Duration.between(scheduledDeparture, departure).toMinutes().toInt()
                    if (scheduledDifference != 0) {
                        val sign = if (scheduledDifference >= 0) "+" else ""
                        Text(
                            text = "$sign$scheduledDifference'",
                            style = LuxTheme.type.timeVariant(LuxTheme.type.footnote),
                            fontWeight = FontWeight.Bold,
                            color = latenessColor
                        )
                    }
                }
            }
        }

        Text(
            text = relativeTimeLabel(departure, now),
            style = LuxTheme.type.caption2,
            color = colors.secondaryLabel,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

internal fun otherTripOptions(group: GroupedStopTime): List<TripOption> =
    group.stopTimes.take(10).map { stopTime ->
        TripOption(
            id = stopTime.tripId,
            startTime = stopTime.place.departure
                ?: stopTime.place.scheduledDeparture
                ?: stopTime.place.arrival
                ?: stopTime.place.scheduledArrival
                ?: Instant.now()
        )
    }

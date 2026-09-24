package ch.cclerc.luxapp.ui.itinerary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextDecoration
import ch.cclerc.luxapp.data.Settings
import ch.cclerc.luxapp.domain.punctuality
import ch.cclerc.luxapp.domain.scheduledDifference
import ch.cclerc.luxapp.ui.theme.LuxTheme
import ch.cclerc.luxcom.model.Place
import java.time.Instant

private const val TIME_UNTIL_HORIZON_SECONDS = 10800L

@Composable
fun ItineraryStopTimeView(
    stop: Place,
    stopStatus: StopStatus,
    legColor: Color,
    accentColor: Color,
    isDepartureStop: Boolean,
    isArrivalStop: Boolean,
    isRealTime: Boolean,
    isCancelled: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = LuxTheme.colors
    val time = stop.departure ?: stop.arrival
    val showDelaySetting = Settings.showDelayInsteadOfDirectTime
    val punctuality = stop.punctuality(isRealTime, isCancelled)
    val scheduledDifference = stop.scheduledDifference()
    val showsDelay = showDelaySetting && isRealTime && !isCancelled && scheduledDifference != 0

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (time != null) {
            val scheduled = stop.scheduledDeparture ?: stop.scheduledArrival
            val displayedTime = if (showDelaySetting) scheduled ?: time else time

            Text(
                text = formatTime(displayedTime),
                style = LuxTheme.type.subheadline.copy(
                    textDecoration = if (isCancelled) TextDecoration.LineThrough else null
                ),
                color = if (showDelaySetting) {
                    colors.secondaryLabel
                } else {
                    punctuality.highlightColor(colors) ?: colors.secondaryLabel
                }
            )

            if (showsDelay) {
                Text(
                    text = "${if (scheduledDifference >= 0) "+" else ""}$scheduledDifference'",
                    style = LuxTheme.type.subheadline.copy(fontWeight = FontWeight.SemiBold),
                    color = punctuality.color(colors)
                )
            }

            val withinHorizon = time < Instant.now().plusSeconds(TIME_UNTIL_HORIZON_SECONDS)
            if (isCancelled) {
                Text(
                    text = "Supprimé",
                    style = LuxTheme.type.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.systemRed
                )
            } else if (stopStatus.timeUntil.isNotEmpty() && withinHorizon) {
                Text(
                    text = "•",
                    style = LuxTheme.type.body,
                    color = colors.secondaryLabel.copy(alpha = colors.secondaryLabel.alpha * 0.5f)
                )
                Text(
                    text = stopStatus.timeUntil,
                    style = LuxTheme.type.subheadline.copy(
                        fontWeight = if (stopStatus.isCurrentStop) FontWeight.SemiBold else FontWeight.Normal
                    ),
                    color = if (stopStatus.isCurrentStop) accentColor else legColor
                )
            }
        }

        StopTypeLabel(
            isDepartureStop = isDepartureStop,
            isArrivalStop = isArrivalStop
        )
    }
}

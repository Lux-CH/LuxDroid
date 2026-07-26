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
    modifier: Modifier = Modifier
) {
    val colors = LuxTheme.colors
    val time = stop.departure ?: stop.arrival

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (time != null) {
            Text(
                text = formatTime(time),
                style = LuxTheme.type.subheadline,
                color = colors.secondaryLabel
            )

            val withinHorizon = time < Instant.now().plusSeconds(TIME_UNTIL_HORIZON_SECONDS)
            if (stopStatus.timeUntil.isNotEmpty() && withinHorizon) {
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

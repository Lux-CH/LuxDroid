package ch.cclerc.luxapp.ui.itinerary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.cclerc.luxapp.core.SFSymbol
import ch.cclerc.luxapp.ui.theme.LuxTheme
import ch.cclerc.luxapp.viewmodel.ItineraryViewModel
import ch.cclerc.luxcom.model.Place
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.ceil

@Composable
fun ItineraryDetailSheet(
    viewModel: ItineraryViewModel,
    isSingle: Boolean,
    modifier: Modifier = Modifier,
    onOpenSubLeg: (String) -> Unit = {}
) {
    val itinerary = viewModel.itinerary
    if (itinerary == null) {
        ItineraryContentUnavailable(
            symbol = "map.fill",
            title = "Itinéraire indisponible",
            description = "Les informations de l'itinéraire ne sont pas disponibles pour le moment.",
            modifier = modifier
        )
        return
    }

    if (isSingle) {
        IndividualItineraryDetailView(
            viewModel = viewModel,
            itinerary = itinerary,
            isMultipleLeg = false,
            modifier = modifier,
            onOpenSubLeg = onOpenSubLeg
        )
    } else {
        MultipleItineraryDetailView(
            itinerary = itinerary,
            viewModel = viewModel,
            modifier = modifier,
            onOpenSubLeg = onOpenSubLeg
        )
    }
}

@Composable
internal fun ItineraryContentUnavailable(
    symbol: String,
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    val colors = LuxTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SFSymbol(name = symbol, size = 42.sp, color = colors.systemGray2)
        Text(
            text = title,
            style = LuxTheme.type.headline,
            color = colors.secondaryLabel,
            textAlign = TextAlign.Center
        )
        Text(
            text = description,
            style = LuxTheme.type.subheadline,
            color = colors.tertiaryLabel,
            textAlign = TextAlign.Center
        )
    }
}

@Immutable
data class StopStatus(
    val isCurrentStop: Boolean,
    val timeUntil: String
)

private const val PRE_ARRIVAL_WINDOW_SECONDS = 60L
private const val POST_ARRIVAL_WINDOW_SECONDS = 60L
private const val PRE_DEPARTURE_WINDOW_SECONDS = 60L
private const val POST_DEPARTURE_WINDOW_SECONDS = 60L

fun calculateStopStatus(stop: Place, currentDate: Instant): StopStatus {
    val now = currentDate
    val arrival = stop.arrival
    val departure = stop.departure

    val isCurrentStop = when {
        arrival != null && departure != null -> {
            if (now >= arrival && now <= departure) {
                true
            } else {
                now >= arrival.minusSeconds(PRE_ARRIVAL_WINDOW_SECONDS) &&
                    now <= departure.plusSeconds(POST_DEPARTURE_WINDOW_SECONDS)
            }
        }
        departure != null ->
            now >= departure.minusSeconds(PRE_DEPARTURE_WINDOW_SECONDS) &&
                now <= departure.plusSeconds(POST_DEPARTURE_WINDOW_SECONDS)
        arrival != null ->
            now >= arrival.minusSeconds(PRE_ARRIVAL_WINDOW_SECONDS) &&
                now <= arrival.plusSeconds(POST_ARRIVAL_WINDOW_SECONDS)
        else -> false
    }

    return StopStatus(
        isCurrentStop = isCurrentStop,
        timeUntil = calculateTimeUntilReachingStop(stop, now, isCurrentStop)
    )
}

fun calculateTimeUntilReachingStop(stop: Place, now: Instant, isCurrentStop: Boolean): String {
    val relevantTime = stop.departure ?: stop.arrival ?: return ""

    val secondsDifference = (relevantTime.toEpochMilli() - now.toEpochMilli()) / 1000L

    if (secondsDifference <= 0) {
        return if (isCurrentStop) "Maintenant" else ""
    }

    if (secondsDifference > 86400) return ""

    if (secondsDifference < 60) return "<1 min"

    val minutes = ceil(secondsDifference / 60.0).toInt()

    if (minutes < 100) return "${minutes}min"

    val hours = minutes / 60
    val remainingMinutes = minutes % 60

    return if (remainingMinutes == 0) "${hours}h" else "${hours}h${remainingMinutes}min"
}

private val shortTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault())

fun formatTime(instant: Instant): String = shortTimeFormatter.format(instant)

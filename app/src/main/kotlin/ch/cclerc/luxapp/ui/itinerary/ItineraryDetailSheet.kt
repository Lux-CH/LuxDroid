package ch.cclerc.luxapp.ui.itinerary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.cclerc.luxapp.core.SFSymbol
import ch.cclerc.luxapp.ui.navigation.DetentSheetState
import ch.cclerc.luxapp.ui.navigation.SheetDetent
import ch.cclerc.luxapp.ui.navigation.SheetPushHost
import ch.cclerc.luxapp.ui.theme.LuxTheme
import ch.cclerc.luxapp.viewmodel.ItineraryViewModel
import ch.cclerc.luxcom.model.Place
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.ceil
import kotlinx.coroutines.launch

@Composable
fun ItineraryDetailSheet(
    viewModel: ItineraryViewModel,
    isSingle: Boolean,
    modifier: Modifier = Modifier,
    sheetState: DetentSheetState? = null,
    onOpenSubLeg: (String) -> Unit = {}
) {
    val itinerary = viewModel.itinerary
    val scope = rememberCoroutineScope()
    var disruptionGroups by remember { mutableStateOf<List<DisruptionGroup>?>(null) }

    val openDisruptions: (List<DisruptionGroup>) -> Unit = { groups ->
        scope.launch {
            val compact = sheetState?.detents?.firstOrNull()
            if (sheetState != null && compact != null && sheetState.currentDetent == compact) {
                sheetState.animateTo(SheetDetent.Medium)
            }
            disruptionGroups = groups
        }
    }

    if (itinerary == null) {
        ItineraryContentUnavailable(
            symbol = "map.fill",
            title = "Itinéraire indisponible",
            description = "Les informations de l'itinéraire ne sont pas disponibles pour le moment.",
            modifier = modifier
        )
        return
    }

    CompositionLocalProvider(LocalOpenDisruptions provides openDisruptions) {
        SheetPushHost(
            pushed = disruptionGroups,
            onPop = { disruptionGroups = null },
            modifier = modifier,
            destination = { groups ->
                DisruptionsListView(groups = groups, onBack = { disruptionGroups = null })
            }
        ) {
            if (isSingle) {
                IndividualItineraryDetailView(
                    viewModel = viewModel,
                    itinerary = itinerary,
                    isMultipleLeg = false,
                    onOpenSubLeg = onOpenSubLeg
                )
            } else {
                MultipleItineraryDetailView(
                    itinerary = itinerary,
                    viewModel = viewModel,
                    onOpenSubLeg = onOpenSubLeg
                )
            }
        }
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

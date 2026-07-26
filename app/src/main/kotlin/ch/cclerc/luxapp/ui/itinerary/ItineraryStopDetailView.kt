package ch.cclerc.luxapp.ui.itinerary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.cclerc.luxapp.core.SFSymbol
import ch.cclerc.luxapp.domain.TripOption
import ch.cclerc.luxapp.ui.anim.scaleClickable
import ch.cclerc.luxapp.ui.stop.ExpandedStopView
import ch.cclerc.luxapp.ui.theme.LuxTheme
import ch.cclerc.luxcom.model.LocationType
import ch.cclerc.luxcom.model.Place
import ch.cclerc.luxcom.model.SearchResult
import java.time.Instant

fun searchResultForPlace(stop: Place): SearchResult = SearchResult(
    type = LocationType.STOP,
    tokens = listOf(emptyList()),
    name = stop.name,
    id = stop.parentId ?: stop.stopId ?: "",
    lat = stop.lat,
    lon = stop.lon,
    level = stop.level,
    street = null,
    houseNumber = null,
    zip = null,
    areas = emptyList(),
    score = 1.0
)

@Composable
fun ItineraryStopDetailView(
    stop: Place,
    onDismiss: () -> Unit,
    onPlanTrip: (SearchResult) -> Unit,
    onOpenTrip: (String, List<TripOption>) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LuxTheme.colors
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val searchResult = remember(stop) { searchResultForPlace(stop) }
    val selectedTime = remember(stop) { stop.departure ?: stop.arrival ?: Instant.now() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.secondarySystemBackground)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = topInset)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stop.name,
                style = LuxTheme.type.headline,
                color = colors.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.weight(1f))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SFSymbol(
                    name = "arrow.triangle.turn.up.right.circle.fill",
                    size = 22.sp,
                    color = colors.secondaryLabel,
                    modifier = Modifier.scaleClickable { onPlanTrip(searchResult) }
                )
                SFSymbol(
                    name = "xmark.circle.fill",
                    size = 22.sp,
                    color = colors.secondaryLabel,
                    modifier = Modifier.scaleClickable { onDismiss() }
                )
            }
        }

        ExpandedStopView(
            stop = searchResult,
            fromStops = true,
            maxGroupsToShow = 50,
            onOpenTrip = onOpenTrip,
            time = selectedTime,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
    }
}

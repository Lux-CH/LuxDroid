package ch.cclerc.luxapp.ui.stop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ch.cclerc.luxapp.domain.TripOption
import ch.cclerc.luxapp.ui.theme.LuxTheme
import ch.cclerc.luxcom.model.SearchResult

@Composable
fun IndividualStopView(
    stop: SearchResult,
    onPlanTrip: (SearchResult) -> Unit,
    onOpenTrip: (String, List<TripOption>) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LuxTheme.colors
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.secondarySystemBackground.copy(alpha = 0.8f)),
        contentAlignment = Alignment.TopStart
    ) {
        Column(Modifier.fillMaxSize()) {
            StopHeaderView(
                stop = stop,
                onPlanTrip = onPlanTrip,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = topInset + 52.dp)
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 20.dp)
            )

            HorizontalDivider(thickness = 0.5.dp, color = colors.separator)

            ExpandedStopView(
                stop = stop,
                fromStops = true,
                maxGroupsToShow = 50,
                onOpenTrip = onOpenTrip,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .offset(y = (-5).dp)
            )
        }
    }
}

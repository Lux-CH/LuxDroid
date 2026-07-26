package ch.cclerc.luxapp.ui.itinerary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ch.cclerc.luxapp.ui.theme.LuxTheme
import ch.cclerc.luxapp.ui.theme.legColor
import ch.cclerc.luxapp.viewmodel.ItineraryViewModel
import ch.cclerc.luxcom.model.Place
import ch.cclerc.luxcom.model.trip.Itinerary
import ch.cclerc.luxcom.model.trip.Leg
import java.time.Instant

private val genevaAgencies = setOf("881", "Transports Publics Genevois")

internal fun calculateUpcomingStopsForSingleLeg(leg: Leg): List<Place> {
    val intermediateStops = leg.intermediateStops ?: return emptyList()
    val cutoff = Instant.now().minusSeconds(60)
    val allStops = buildList {
        add(leg.from)
        addAll(intermediateStops)
        add(leg.to)
    }
    return allStops.filter { stop ->
        val relevantTime = stop.departure ?: stop.arrival
        relevantTime == null || relevantTime >= cutoff
    }
}

internal fun calculateNextStop(leg: Leg): Place? {
    val now = Instant.now()

    val departureTime = leg.from.departure
    if (departureTime != null && departureTime > now) return leg.from

    leg.intermediateStops?.forEach { stop ->
        val relevantTime = stop.departure ?: stop.arrival
        if (relevantTime != null && relevantTime > now) return stop
    }

    return leg.to
}

@Composable
fun IndividualItineraryDetailView(
    viewModel: ItineraryViewModel,
    itinerary: Itinerary,
    isMultipleLeg: Boolean,
    modifier: Modifier = Modifier,
    onOpenSubLeg: (String) -> Unit = {}
) {
    val colors = LuxTheme.colors
    val mainLeg = itinerary.legs.firstOrNull()
    val color = mainLeg?.let { legColor(it) } ?: Color.Black

    val upcomingStops = remember(mainLeg) { mainLeg?.let { calculateUpcomingStopsForSingleLeg(it) } ?: emptyList() }
    val nextStop = remember(mainLeg) { mainLeg?.let { calculateNextStop(it) } }

    val backgroundColor = if (colors.isDark) colors.systemBackground else Color.White

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        if (mainLeg == null) return@Column

        LegHeaderView(
            leg = mainLeg,
            legColor = color,
            isSingle = !isMultipleLeg,
            nextStop = nextStop,
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .padding(top = 25.dp, bottom = 17.5.dp),
            onOpenSubLeg = onOpenSubLeg
        )

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 20.dp),
            thickness = 1.dp,
            color = colors.separator
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            val routeShortName = mainLeg.routeShortName
            if (routeShortName != null && mainLeg.agencyId in genevaAgencies) {
                DisruptionSectionView(
                    disruptions = rememberDisruptions(routeShortName),
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .padding(top = 15.dp)
                        .negativeBottomPadding(10.dp)
                )
            }

            ItinerarySheetDetailStopsContentView(
                stops = upcomingStops,
                legColor = color,
                fromStop = mainLeg.from,
                toStop = mainLeg.to,
                duration = mainLeg.duration,
                isMultipleLeg = isMultipleLeg,
                onSelectStop = { viewModel.selectedStop = it },
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .padding(top = 16.dp)
            )

            HorizontalDivider(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 10.dp),
                thickness = 1.dp,
                color = colors.separator
            )
        }
    }
}

internal fun Modifier.negativeBottomPadding(amount: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val reduced = (placeable.height - amount.roundToPx()).coerceAtLeast(0)
    layout(placeable.width, reduced) { placeable.place(0, 0) }
}

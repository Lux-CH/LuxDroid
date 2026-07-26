package ch.cclerc.luxapp.ui.itinerary

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.cclerc.luxapp.core.SFSymbol
import ch.cclerc.luxapp.ui.anim.PlainIndication
import ch.cclerc.luxapp.ui.home.parseStopName
import ch.cclerc.luxapp.ui.home.shortnameCapitalize
import ch.cclerc.luxapp.ui.stop.expanded.getTrackType
import ch.cclerc.luxapp.ui.theme.LuxTheme
import ch.cclerc.luxcom.geo.StopGrouping
import ch.cclerc.luxcom.model.Place
import androidx.compose.material3.Text
import java.time.Instant

private val TimelineColumnWidth = 60.dp

@Composable
fun ItineraryStopTimelineView(
    stops: List<Place>,
    legColor: Color,
    accentColor: Color,
    fromStop: Place,
    toStop: Place,
    isMultipleLeg: Boolean,
    onSelectStop: (Place) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAllStops by remember(stops) { mutableStateOf(false) }

    val displayedStops = if (!isMultipleLeg || showAllStops || stops.size <= 2) {
        stops
    } else {
        listOf(stops.first(), stops.last())
    }

    val intermediateStopsCount = maxOf(0, stops.size - 2)

    fun actualIndex(displayIndex: Int): Int = when {
        showAllStops || !isMultipleLeg -> displayIndex
        displayIndex == 0 -> 0
        else -> stops.size - 1
    }

    Column(modifier = modifier.fillMaxWidth()) {
        displayedStops.forEachIndexed { index, stop ->
            key(stop.stopId ?: "${stop.name}-$index") {
                val resolvedIndex = actualIndex(index)
                ItineraryStopTimelineRowView(
                    stop = stop,
                    legColor = legColor,
                    accentColor = accentColor,
                    isFirstStop = resolvedIndex == 0,
                    isLastStop = resolvedIndex == stops.size - 1,
                    isDepartureStop = stop.name == fromStop.name,
                    isArrivalStop = stop.name == toStop.name,
                    currentDate = Instant.now(),
                    onSelect = { onSelectStop(stop) }
                )
            }

            if (index == 0 && isMultipleLeg && stops.size > 2) {
                IntermediateStopsButton(
                    count = intermediateStopsCount,
                    legColor = legColor,
                    isExpanded = showAllStops,
                    onClick = { showAllStops = !showAllStops }
                )
            }
        }
    }
}

@Composable
fun IntermediateStopsButton(
    count: Int,
    legColor: Color,
    isExpanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 90f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "intermediate-stops-chevron"
    )
    val plural = if (count == 1) "" else "s"

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(interactionSource = null, indication = PlainIndication, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.width(TimelineColumnWidth),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.size(3.dp, 20.dp).background(legColor))
            Box(Modifier.size(3.dp, 20.dp).background(legColor))
        }

        Row(
            modifier = Modifier.padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$count arrêt$plural intermédiaire$plural",
                style = LuxTheme.type.subheadline.copy(fontWeight = FontWeight.Medium),
                color = legColor
            )
            SFSymbol(
                name = "chevron.right",
                size = 12.sp,
                color = legColor,
                weight = 500,
                modifier = Modifier.rotate(rotation)
            )
        }

        Spacer(Modifier.weight(1f))
    }
}

@Composable
fun ItineraryStopTimelineRowView(
    stop: Place,
    legColor: Color,
    accentColor: Color,
    isFirstStop: Boolean,
    isLastStop: Boolean,
    isDepartureStop: Boolean,
    isArrivalStop: Boolean,
    currentDate: Instant,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LuxTheme.colors
    val stopStatus = calculateStopStatus(stop, currentDate)
    val parsed = parseStopName(stop.name)
    val shouldUseNormalDisplay = parsed.location.isEmpty()
    val isCityRelevant = StopGrouping.isGenericLocationTerm(parsed.location)
    val fontWeight = if (stopStatus.isCurrentStop) FontWeight.Bold else FontWeight.Medium

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(interactionSource = null, indication = PlainIndication, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TimelineIndicatorView(
            legColor = legColor,
            accentColor = accentColor,
            isFirstStop = isFirstStop,
            isLastStop = isLastStop,
            isDepartureStop = isDepartureStop,
            isArrivalStop = isArrivalStop,
            isCurrentStop = stopStatus.isCurrentStop,
            modifier = Modifier.width(TimelineColumnWidth)
        )

        Column(
            modifier = Modifier.padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (shouldUseNormalDisplay) {
                Text(
                    text = stop.name,
                    style = LuxTheme.type.body.copy(fontSize = 17.sp, fontWeight = fontWeight),
                    color = colors.label
                )
            } else {
                Text(
                    text = parsed.city,
                    style = LuxTheme.type.caption2.copy(
                        fontSize = 11.sp,
                        fontWeight = if (isCityRelevant) FontWeight.Black else fontWeight
                    ),
                    color = colors.secondaryLabel
                )
                Text(
                    text = parsed.location.shortnameCapitalize,
                    style = LuxTheme.type.body.copy(fontSize = 17.sp, fontWeight = fontWeight),
                    color = colors.label
                )
            }

            ItineraryStopTimeView(
                stop = stop,
                stopStatus = stopStatus,
                legColor = legColor,
                accentColor = accentColor,
                isDepartureStop = isDepartureStop,
                isArrivalStop = isArrivalStop
            )
        }

        Spacer(Modifier.weight(1f))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val track = stop.track
            if (track != null) {
                Text(
                    text = getTrackType(track),
                    style = LuxTheme.type.caption,
                    color = colors.secondaryLabel.copy(alpha = colors.secondaryLabel.alpha * 0.7f)
                )
            }
            SFSymbol(
                name = "chevron.right",
                size = 14.sp,
                color = colors.secondaryLabel.copy(alpha = colors.secondaryLabel.alpha * 0.5f),
                weight = 600
            )
        }
    }
}

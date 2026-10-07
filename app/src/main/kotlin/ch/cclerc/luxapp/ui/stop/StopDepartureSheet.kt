package ch.cclerc.luxapp.ui.stop

import androidx.compose.foundation.layout.Spacer
import ch.cclerc.luxcom.model.TransportationMode
import ch.cclerc.luxapp.ui.components.LinePill
import ch.cclerc.luxapp.domain.StopConnection
import ch.cclerc.luxapp.domain.ConnectionService
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.cclerc.luxapp.core.SFSymbol
import ch.cclerc.luxapp.domain.TripOption
import ch.cclerc.luxapp.ui.anim.scaleClickable
import ch.cclerc.luxapp.ui.itinerary.formatTime
import ch.cclerc.luxapp.ui.itinerary.searchResultForPlace
import ch.cclerc.luxapp.ui.stop.expanded.getTrackType
import ch.cclerc.luxapp.ui.theme.LuxTheme
import ch.cclerc.luxcom.model.Place
import ch.cclerc.luxcom.model.SearchResult
import java.time.Instant

data class StopSheetDetail(val symbol: String, val text: String)

@Composable
fun StopDepartureSheet(
    stop: SearchResult,
    onGo: (() -> Unit)?,
    onOpenTrip: (String, List<TripOption>) -> Unit,
    modifier: Modifier = Modifier,
    track: String? = null,
    time: Instant? = null,
    details: List<StopSheetDetail> = emptyList(),
    connections: List<StopConnection>? = null,
    showsDepartures: Boolean = true,
    onHeaderHeight: (Dp) -> Unit = {}
) {
    val colors = LuxTheme.colors
    val accent = LuxTheme.accent
    val density = LocalDensity.current
    val shownDetails = if (track != null) listOf(StopSheetDetail("signpost.right", getTrackType(track))) + details else details
    var loadedConnections by remember { mutableStateOf<Pair<String, List<StopConnection>>?>(null) }
    LaunchedEffect(stop.id, connections == null) {
        if (connections != null || !showsDepartures) return@LaunchedEffect
        val stopId = stop.id
        loadedConnections = stopId to ConnectionService.connections(stopId)
    }
    val shownConnections = connections ?: loadedConnections?.takeIf { it.first == stop.id }?.second.orEmpty()

    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { onHeaderHeight(with(density) { it.height.toDp() }) }
                .padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stop.name,
                    style = LuxTheme.type.title3.copy(fontWeight = FontWeight.Bold),
                    color = colors.label,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                AnimatedVisibility(visible = shownConnections.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                    ConnectionPillsRow(shownConnections)
                }
                shownDetails.forEach { detail ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        SFSymbol(name = detail.symbol, size = 13.sp, color = colors.secondaryLabel, weight = 600)
                        Text(
                            detail.text,
                            style = LuxTheme.type.subheadline.copy(fontWeight = FontWeight.Medium),
                            color = colors.secondaryLabel
                        )
                    }
                }
            }
            if (onGo != null) GoButton(accent, onGo)
        }
        if (!showsDepartures) {
            Spacer(Modifier.weight(1f))
            return@Column
        }
        HorizontalDivider(thickness = 0.5.dp, color = colors.separator)
        key(stop.id, track, time) {
            ExpandedStopView(
                stop = stop,
                fromStops = true,
                maxGroupsToShow = 50,
                onOpenTrip = onOpenTrip,
                time = time,
                track = track,
                animatesIn = false,
                onGlassSheet = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }
}

val StopSheetConnectionRowHeight = 26.dp

@Composable
private fun ConnectionPillsRow(connections: List<StopConnection>) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(StopSheetConnectionRowHeight)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val fade = 30.dp.toPx()
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Black, Color.Transparent),
                        startX = size.width - fade,
                        endX = size.width
                    ),
                    blendMode = BlendMode.DstIn
                )
            }
            .horizontalScroll(rememberScrollState())
            .padding(end = 30.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        connections.forEach { connection ->
            LinePill(line = connection.line, agencyId = connection.agency, mode = TransportationMode.BUS)
        }
    }
}

@Composable
private fun GoButton(accent: Color, onGo: () -> Unit) {
    Row(
        modifier = Modifier
            .height(38.dp)
            .scaleClickable { onGo() }
            .background(accent, CircleShape)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SFSymbol(name = "arrow.triangle.turn.up.right.diamond.fill", size = 14.sp, color = Color.White, weight = 600)
        Text("Y aller", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}

@Composable
fun ItineraryStopSheet(
    place: Place,
    onGo: (SearchResult) -> Unit,
    onOpenTrip: (String, List<TripOption>) -> Unit,
    onHeaderHeight: (Dp) -> Unit,
    modifier: Modifier = Modifier,
    connections: List<StopConnection> = emptyList(),
    isEndpoint: Boolean = false
) {
    val stop = searchResultForPlace(place)
    val details = itineraryStopDetails(place)
    StopDepartureSheet(
        stop = stop,
        onGo = if (isEndpoint) null else ({ onGo(stop) }),
        showsDepartures = !isEndpoint,
        onOpenTrip = onOpenTrip,
        modifier = modifier,
        time = place.departure ?: place.arrival,
        details = details,
        connections = connections,
        onHeaderHeight = onHeaderHeight
    )
}

fun itineraryStopDetails(place: Place): List<StopSheetDetail> =
    buildList {
        val arrival = place.arrival
        val departure = place.departure
        if (arrival != null && departure != null && arrival != departure) {
            add(StopSheetDetail("arrow.down.circle.fill", "Arrivée prévue : ${formatTime(arrival)}"))
            add(StopSheetDetail("arrow.up.circle.fill", "Départ à : ${formatTime(departure)}"))
        } else if (departure != null) {
            add(StopSheetDetail("clock", "Départ à : ${formatTime(departure)}"))
        } else if (arrival != null) {
            add(StopSheetDetail("clock", "Arrivée prévue : ${formatTime(arrival)}"))
        }
        place.track?.let { add(StopSheetDetail("train.side.front.car", getTrackType(it))) }
    }

@Composable
fun rememberStopSheetHeightEstimator(): (Place, Dp, Boolean, Boolean) -> Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val titleStyle = LuxTheme.type.title3.copy(fontWeight = FontWeight.Bold)
    val detailStyle = LuxTheme.type.subheadline.copy(fontWeight = FontWeight.Medium)
    val buttonStyle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    return remember(measurer, density, titleStyle, detailStyle) {
        { place, width, hasConnections, isEndpoint ->
            with(density) {
                val buttonWidth = measurer.measure("Y aller", buttonStyle).size.width.toDp() + 54.dp
                val textWidth = maxOf(80.dp, if (isEndpoint) width - 40.dp else width - 40.dp - 12.dp - buttonWidth)
                val title = measurer.measure(
                    place.name,
                    titleStyle,
                    maxLines = 2,
                    constraints = Constraints(maxWidth = textWidth.roundToPx())
                ).size.height.toDp()
                val detailLine = measurer.measure("Départ", detailStyle).size.height.toDp()
                val connectionRow = if (hasConnections) StopSheetConnectionRowHeight + 4.dp else 0.dp
                val column = title + connectionRow + (detailLine + 4.dp) * itineraryStopDetails(place).size
                if (isEndpoint) {
                    26.dp + 14.dp + column + ItineraryStopSheetEndpointBottomInset
                } else {
                    val header = 26.dp + 14.dp + maxOf(column, 38.dp)
                    header + ItineraryStopSheetFirstGroupHeight
                }
            }
        }
    }
}

val ItineraryStopSheetFirstGroupHeight = 330.dp
val ItineraryStopSheetEndpointBottomInset = 24.dp

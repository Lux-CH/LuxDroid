package ch.cclerc.luxapp.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import ch.cclerc.luxapp.core.SFSymbol
import ch.cclerc.luxapp.domain.ConnectionService
import ch.cclerc.luxapp.domain.rememberConnections
import ch.cclerc.luxapp.ui.components.LinePill
import ch.cclerc.luxapp.ui.stop.expanded.getTrackType
import ch.cclerc.luxapp.ui.theme.LuxShapes
import ch.cclerc.luxapp.ui.theme.LuxTheme
import ch.cclerc.luxapp.ui.theme.iosShadow
import ch.cclerc.luxcom.model.Place
import ch.cclerc.luxcom.model.TransportationMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

val StopPopoverMinWidth = 250.dp

private const val STOP_POPOVER_NAVIGATION_DELAY_MS = 150L
private val StopPopoverGap = 12.dp

private class StopPopoverPositionProvider(private val gapPx: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val centerX = anchorBounds.left + anchorBounds.width / 2
        val maxX = max(0, windowSize.width - popupContentSize.width)
        val x = (centerX - popupContentSize.width / 2).coerceIn(0, maxX)

        val above = anchorBounds.top - gapPx - popupContentSize.height
        val below = anchorBounds.bottom + gapPx
        val maxY = max(0, windowSize.height - popupContentSize.height)
        val y = if (above >= 0) above else min(below, maxY)

        return IntOffset(x, y)
    }
}

@Composable
fun StopPopover(
    place: Place,
    color: Color,
    onOtherDepartures: () -> Unit,
    onDismiss: () -> Unit
) {
    val gapPx = with(androidx.compose.ui.platform.LocalDensity.current) { StopPopoverGap.roundToPx() }
    val positionProvider = remember(gapPx) { StopPopoverPositionProvider(gapPx) }

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        StopPopoverCard(place = place, color = color, onOtherDepartures = onOtherDepartures)
    }
}

@Composable
fun StopPopoverCard(
    place: Place,
    color: Color,
    onOtherDepartures: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LuxTheme.colors
    val isDark = LuxTheme.isDark
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(LuxShapes.r16)

    val connectionStopId = remember(place.stopId) {
        ConnectionService.sanitizedStopId(place.stopId.orEmpty()).orEmpty()
    }
    val connections by rememberConnections(connectionStopId)

    val buttonTextColor = if (isDark && color == Color.White) Color.Black else Color.White

    Column(
        modifier = modifier
            .widthIn(min = StopPopoverMinWidth)
            .iosShadow(Color.Black.copy(alpha = 0.25f), 16.dp, 4.dp, shape)
            .background(colors.secondarySystemBackgroundElevated, shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(Modifier.size(8.dp).background(color, CircleShape))
            Text(
                text = place.name,
                style = LuxTheme.type.headline,
                color = colors.label,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TimingRows(place = place, color = color)

            place.track?.let { track ->
                PopoverRow(icon = "train.side.front.car", color = color) {
                    Text(
                        text = getTrackType(track),
                        style = LuxTheme.type.subheadline,
                        color = colors.label
                    )
                }
            }

            if (connections.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(top = 2.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SFSymbol(name = "arrow.triangle.swap", size = 15.sp, color = color)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        connections.forEach { routeName ->
                            LinePill(
                                line = routeName,
                                agencyId = null,
                                mode = TransportationMode.BUS
                            )
                        }
                    }
                }
            }
        }

        Button(
            onClick = {
                scope.launch {
                    delay(STOP_POPOVER_NAVIGATION_DELAY_MS)
                    onOtherDepartures()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            shape = RoundedCornerShape(LuxShapes.r12),
            colors = ButtonDefaults.buttonColors(
                containerColor = color,
                contentColor = buttonTextColor
            )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Autres départs",
                    style = LuxTheme.type.subheadline,
                    color = buttonTextColor
                )
                SFSymbol(name = "chevron.right", size = 13.sp, color = buttonTextColor)
            }
        }
    }
}

@Composable
private fun TimingRows(place: Place, color: Color) {
    val arrival = place.arrival
    val departure = place.departure

    when {
        arrival != null && departure != null && arrival != departure -> {
            TimingRow(
                icon = "arrow.down.circle.fill",
                color = color,
                text = "Arrivée prévue : ${formatPopoverTime(arrival)}"
            )
            TimingRow(
                icon = "arrow.up.circle.fill",
                color = color,
                text = "Départ à : ${formatPopoverTime(departure)}"
            )
        }
        arrival != null -> TimingRow(
            icon = "clock",
            color = color,
            text = "Arrivée prévue : ${formatPopoverTime(arrival)}"
        )
        departure != null -> TimingRow(
            icon = "clock",
            color = color,
            text = "Départ à : ${formatPopoverTime(departure)}"
        )
    }
}

@Composable
private fun TimingRow(icon: String, color: Color, text: String) {
    PopoverRow(icon = icon, color = color) {
        Text(text = text, style = LuxTheme.type.subheadline, color = LuxTheme.colors.label)
    }
}

@Composable
private fun PopoverRow(icon: String, color: Color, content: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SFSymbol(name = icon, size = 15.sp, color = color)
        content()
    }
}

private val popoverTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

fun formatPopoverTime(instant: Instant): String = popoverTimeFormatter.format(instant)

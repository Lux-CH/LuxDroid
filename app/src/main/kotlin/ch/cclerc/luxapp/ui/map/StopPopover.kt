package ch.cclerc.luxapp.ui.map

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

val StopPopoverMinWidth = 250.dp

private const val STOP_POPOVER_NAVIGATION_DELAY_MS = 150L
private val StopPopoverMaxWidth = 280.dp
private val StopCalloutTailHeight = 12.dp
private val StopCalloutTailWidth = 26.dp
private val StopCalloutTailOverlap = 2.dp
private val StopCalloutScreenMargin = 12.dp
private val StopCalloutDotInset = 10.dp
private val StopCalloutGap = 4.dp

@Composable
fun StopCallout(
    place: Place,
    color: Color,
    latitude: Double,
    longitude: Double,
    projection: MapProjector,
    onOtherDepartures: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onDismiss)

    val tailCenterX = remember { mutableFloatStateOf(0f) }
    val tailPointsDown = remember { mutableStateOf(true) }
    val entrance = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) {
        entrance.animateTo(1f, ch.cclerc.luxapp.ui.theme.LuxSprings.springFor(0.35, 0.75))
    }

    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .matchParentSize()
                .pointerInput(Unit) { detectTapGestures { onDismiss() } }
        )

        Layout(
            modifier = Modifier.matchParentSize(),
            content = {
                Box(
                    Modifier.graphicsLayer {
                        val p = entrance.value
                        scaleX = 0.6f + 0.4f * p
                        scaleY = 0.6f + 0.4f * p
                        alpha = p.coerceIn(0f, 1f)
                        transformOrigin = TransformOrigin(0.5f, if (tailPointsDown.value) 1f else 0f)
                    }
                ) {
                    StopCalloutCard(
                        place = place,
                        color = color,
                        onOtherDepartures = onOtherDepartures,
                        tailCenterX = { tailCenterX.floatValue },
                        tailPointsDown = { tailPointsDown.value }
                    )
                }
            }
        ) { measurables, constraints ->
            val margin = StopCalloutScreenMargin.roundToPx()
            val available = (constraints.maxWidth - margin * 2)
                .coerceIn(0, StopPopoverMaxWidth.roundToPx())
            val placeable = measurables.first().measure(Constraints(maxWidth = available))
            val width = constraints.maxWidth
            val height = constraints.maxHeight
            val gapPx = (StopCalloutDotInset + StopCalloutGap).toPx()
            val cornerPx = LuxShapes.r16.toPx() + StopCalloutTailWidth.toPx() / 4f

            layout(width, height) {
                val screen = projection(latitude, longitude) ?: return@layout

                val minX = margin.toFloat()
                val maxX = max(minX, (width - placeable.width - margin).toFloat())
                val x = (screen.x - placeable.width / 2f).coerceIn(minX, maxX)

                val above = screen.y - gapPx - placeable.height
                val pointsDown = above >= margin
                val y = if (pointsDown) above else screen.y + gapPx

                tailPointsDown.value = pointsDown
                tailCenterX.floatValue = (screen.x - x).coerceIn(
                    cornerPx.coerceAtMost(placeable.width / 2f),
                    (placeable.width - cornerPx).coerceAtLeast(placeable.width / 2f)
                )

                placeable.place(x.roundToInt(), y.roundToInt())
            }
        }
    }
}

@Composable
private fun StopCalloutCard(
    place: Place,
    color: Color,
    onOtherDepartures: () -> Unit,
    tailCenterX: () -> Float,
    tailPointsDown: () -> Boolean
) {
    val surface = LuxTheme.colors.secondarySystemBackgroundElevated

    Column(
        modifier = Modifier.drawBehind {
            val tailHeight = StopCalloutTailHeight.toPx()
            val overlap = StopCalloutTailOverlap.toPx()
            val halfWidth = StopCalloutTailWidth.toPx() / 2f
            val centerX = tailCenterX()
            val down = tailPointsDown()
            val baseY = if (down) size.height - tailHeight - overlap else tailHeight + overlap
            val tipY = if (down) size.height else 0f
            val path = Path().apply {
                moveTo(centerX - halfWidth, baseY)
                lineTo(centerX + halfWidth, baseY)
                lineTo(centerX, tipY)
                close()
            }
            drawPath(path, surface)
        }
    ) {
        Spacer(Modifier.height(StopCalloutTailHeight))
        StopPopoverCard(place = place, color = color, onOtherDepartures = onOtherDepartures)
        Spacer(Modifier.height(StopCalloutTailHeight))
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

    val connections by rememberConnections(place.stopId.orEmpty())

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
            shape = CircleShape,
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

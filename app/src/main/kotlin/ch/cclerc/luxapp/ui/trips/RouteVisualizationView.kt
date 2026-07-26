package ch.cclerc.luxapp.ui.trips

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.cclerc.luxapp.core.SFSymbol
import ch.cclerc.luxapp.ui.anim.pulseScale
import ch.cclerc.luxapp.ui.components.isDarkColor
import ch.cclerc.luxapp.ui.components.lightenColor
import ch.cclerc.luxapp.ui.theme.InterFontFamily
import ch.cclerc.luxapp.ui.theme.LuxTheme
import ch.cclerc.luxapp.ui.theme.TpgFontFamily
import ch.cclerc.luxcom.colors.LineColors
import ch.cclerc.luxcom.model.TransportationMode
import ch.cclerc.luxcom.model.trip.Leg
import java.util.Locale

private val lausanneAgencies = setOf("151", "55", "764", "7256", "344", "29")

private const val WAITING_THRESHOLD_SECONDS = 120
private const val MIN_SEGMENT_WIDTH = 10f
private val SEGMENT_CORNER_RADIUS = 12.dp

internal sealed interface RouteSegment {
    val weight: Int

    data class LegPart(
        val leg: Leg,
        val isFirst: Boolean,
        val isLast: Boolean
    ) : RouteSegment {
        override val weight: Int get() = leg.duration
    }

    data class Wait(val seconds: Int) : RouteSegment {
        override val weight: Int get() = seconds
    }
}

internal fun visualizationLegs(legs: List<Leg>): List<Leg> = legs.filter { leg ->
    !(leg.mode == TransportationMode.WALK &&
        leg.from.name == leg.to.name &&
        leg.from.track == leg.to.track)
}

internal fun routeSegments(legs: List<Leg>): List<RouteSegment> {
    val filtered = visualizationLegs(legs)
    val segments = mutableListOf<RouteSegment>()
    filtered.forEachIndexed { index, leg ->
        segments.add(
            RouteSegment.LegPart(
                leg = leg,
                isFirst = index == 0,
                isLast = index == filtered.size - 1
            )
        )
        val next = filtered.getOrNull(index + 1)
        if (next != null) {
            val waiting = ((next.startTime.toEpochMilli() - leg.endTime.toEpochMilli()) / 1000L)
                .coerceAtLeast(0L)
                .toInt()
            if (waiting > WAITING_THRESHOLD_SECONDS) segments.add(RouteSegment.Wait(waiting))
        }
    }
    return segments
}

internal fun segmentWidths(segments: List<RouteSegment>, availableWidth: Float): List<Float> {
    if (segments.isEmpty() || availableWidth <= 0f) return segments.map { 0f }
    if (availableWidth < MIN_SEGMENT_WIDTH * segments.size) {
        return segments.map { availableWidth / segments.size }
    }

    val widths = FloatArray(segments.size)
    val pinned = BooleanArray(segments.size)

    while (true) {
        var pinnedWidth = 0f
        var flexibleWeight = 0
        segments.indices.forEach { i ->
            if (pinned[i]) pinnedWidth += widths[i] else flexibleWeight += segments[i].weight
        }
        val remaining = availableWidth - pinnedWidth
        if (flexibleWeight <= 0) break

        var changed = false
        segments.indices.forEach { i ->
            if (!pinned[i]) {
                val width = remaining * segments[i].weight / flexibleWeight
                if (width < MIN_SEGMENT_WIDTH) {
                    widths[i] = MIN_SEGMENT_WIDTH
                    pinned[i] = true
                    changed = true
                } else {
                    widths[i] = width
                }
            }
        }
        if (!changed) break
    }

    return widths.toList()
}

@Composable
fun legColor(leg: Leg, brightIt: Boolean = false): Color {
    val colors = LuxTheme.colors
    val accent = LuxTheme.accent

    if (leg.mode == TransportationMode.WALK) return colors.systemBlue
    if (leg.mode == TransportationMode.BIKE || leg.mode == TransportationMode.CAR) {
        return colors.systemGray
    }

    val isLausanne = leg.agencyId in lausanneAgencies
    val isTAC = leg.agencyId == "1"
    val routeName = leg.routeShortName

    val baseColor: Color = if (routeName != null) {
        val tac = if (isTAC) LineColors.tacColors(routeName) else null
        val standard = if (isLausanne) LineColors.tlColor(routeName) else LineColors.color(routeName)
        when {
            tac != null -> Color(tac)
            standard != null -> Color(standard)
            else -> {
                val isTrainDetected = routeName.startsWith("RL") || routeName.startsWith("IR") ||
                    routeName.startsWith("RE") || routeName.startsWith("IC") || routeName == "R"
                if (leg.mode.usesSquaredPill || isTrainDetected) Color(0xFFEA0706) else accent
            }
        }
    } else {
        if (leg.mode.usesSquaredPill) Color(0xFFEA0706) else accent
    }

    return if (brightIt && isDarkColor(baseColor)) lightenColor(baseColor) else baseColor
}

private fun transportSymbol(mode: TransportationMode): String = when (mode) {
    TransportationMode.WALK -> "figure.walk"
    TransportationMode.BUS -> "bus.fill"
    TransportationMode.SUBWAY -> "tram.tunnel.fill"
    TransportationMode.RAIL -> "train.side.front.car"
    TransportationMode.TRAM -> "tram.fill"
    TransportationMode.FERRY -> "ferry.fill"
    else -> "car.fill"
}

@Composable
private fun GlossyFill(fill: Color, shape: Shape, highlightAlpha: Float) {
    val gradient = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = highlightAlpha),
            Color.Black.copy(alpha = 0.1f)
        )
    )
    Box(
        Modifier
            .fillMaxSize()
            .clip(shape)
            .background(fill)
            .drawWithContent {
                drawContent()
                drawRect(brush = gradient, blendMode = BlendMode.Overlay)
            }
    )
}

@Composable
fun WaitingTimeView(seconds: Int, modifier: Modifier = Modifier) {
    val colors = LuxTheme.colors
    val gradient = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = if (colors.isDark) 0.05f else 0.08f),
            Color.Black.copy(alpha = 0.05f)
        )
    )
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .matchParentSize()
                .clip(RectangleShape)
                .background(Color(0xFF808080).copy(alpha = 0.15f))
                .drawWithContent {
                    drawContent()
                    drawRect(brush = gradient, blendMode = BlendMode.Overlay)
                }
        )
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            SFSymbol(name = "clock", size = 10.sp, color = colors.secondaryLabel, weight = 500)
            BasicText(
                text = "${seconds / 60}m",
                style = TextStyle(
                    fontFamily = InterFontFamily,
                    fontSize = 8.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    color = colors.secondaryLabel
                ),
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
fun LegSegmentView(
    leg: Leg,
    isFirst: Boolean,
    isLast: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = LuxTheme.colors
    val shape = RoundedCornerShape(
        topStart = if (isFirst) SEGMENT_CORNER_RADIUS else 0.dp,
        bottomStart = if (isFirst) SEGMENT_CORNER_RADIUS else 0.dp,
        topEnd = if (isLast) SEGMENT_CORNER_RADIUS else 0.dp,
        bottomEnd = if (isLast) SEGMENT_CORNER_RADIUS else 0.dp
    )
    val base = legColor(leg)
    val bright = legColor(leg, brightIt = true)
    val shadowPx = with(LocalDensity.current) { 1.dp.toPx() }
    val textShadow = Shadow(
        color = Color.Black.copy(alpha = 0.3f),
        offset = Offset(0f, shadowPx),
        blurRadius = shadowPx
    )

    Box(modifier, contentAlignment = Alignment.Center) {
        Box(Modifier.matchParentSize()) {
            GlossyFill(
                fill = base.copy(alpha = 0.2f),
                shape = shape,
                highlightAlpha = if (colors.isDark) 0.08f else 0.15f
            )
        }

        Box(Modifier.padding(vertical = 2.dp), contentAlignment = Alignment.Center) {
            when {
                leg.mode == TransportationMode.WALK -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        SFSymbol(
                            name = "figure.walk",
                            size = 14.sp,
                            color = colors.systemBlue,
                            weight = 600,
                            modifier = Modifier.pulseScale(
                                from = 1f,
                                to = 1.05f,
                                durationMs = 1200
                            )
                        )
                        if (isFirst && isLast) {
                            val meters = leg.distance ?: 0.0
                            val km = meters / 1000.0
                            val text = if (km >= 1.0) {
                                String.format(Locale.ROOT, "%.1f km", km)
                            } else {
                                String.format(Locale.ROOT, "%d m", meters.toInt())
                            }
                            BasicText(
                                text = text,
                                style = TextStyle(
                                    fontFamily = InterFontFamily,
                                    fontSize = 10.sp,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                                    color = colors.systemBlue,
                                    textAlign = TextAlign.Center
                                ),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Clip
                            )
                        }
                    }
                }

                leg.routeShortName != null -> {
                    BasicText(
                        text = leg.routeShortName!!,
                        modifier = Modifier.padding(horizontal = 4.dp),
                        style = TextStyle(
                            fontFamily = TpgFontFamily,
                            fontSize = 14.sp,
                            color = bright,
                            textAlign = TextAlign.Center,
                            shadow = textShadow
                        ),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip
                    )
                }

                else -> {
                    SFSymbol(
                        name = transportSymbol(leg.mode),
                        size = 14.sp,
                        color = Color.White,
                        weight = 600
                    )
                }
            }
        }
    }
}

@Composable
fun RouteVisualizationView(legs: List<Leg>, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        val segments = routeSegments(legs)
        val widths = segmentWidths(segments, maxWidth.value)
        Row(Modifier.fillMaxSize()) {
            segments.forEachIndexed { index, segment ->
                val itemModifier = Modifier
                    .width(widths[index].dp)
                    .fillMaxHeight()
                when (segment) {
                    is RouteSegment.LegPart -> LegSegmentView(
                        leg = segment.leg,
                        isFirst = segment.isFirst,
                        isLast = segment.isLast,
                        modifier = itemModifier
                    )

                    is RouteSegment.Wait -> WaitingTimeView(
                        seconds = segment.seconds,
                        modifier = itemModifier
                    )
                }
            }
        }
    }
}

package ch.cclerc.luxapp.ui.components.settings

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.cclerc.luxapp.core.HapticFeedback
import ch.cclerc.luxapp.core.SFSymbol
import ch.cclerc.luxapp.data.Settings
import ch.cclerc.luxapp.ui.anim.scaleClickable
import ch.cclerc.luxapp.ui.theme.LuxSprings
import ch.cclerc.luxapp.ui.theme.LuxTheme
import ch.cclerc.luxapp.ui.theme.TpgFontFamily
import ch.cclerc.luxcom.colors.LineColors
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private val sampleLines = listOf("18", "80", "10", "14", "29", "17", "2", "3", "RL4", "60")

private fun applyDisplayMode(mode: Int) {
    when (mode) {
        1 -> {
            Settings.highContrastButAccurateLinePill = true
            Settings.easyOnTheEyes = false
        }
        2 -> {
            Settings.highContrastButAccurateLinePill = false
            Settings.easyOnTheEyes = true
        }
        else -> {
            Settings.highContrastButAccurateLinePill = false
            Settings.easyOnTheEyes = false
        }
    }
}

private fun isDarkColor(color: Color): Boolean {
    val luminance = 0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue
    return luminance < 0.4f
}

private fun lightenColor(color: Color, factor: Float = 0.25f): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        (color.red * 255f).roundToInt(),
        (color.green * 255f).roundToInt(),
        (color.blue * 255f).roundToInt(),
        hsv
    )
    val value = min(1f, hsv[2] + factor)
    val saturation = max(0.3f, hsv[1] * 0.8f)
    return Color.hsv(hsv[0], saturation, value)
}

@Composable
fun LineStylePicker(
    icon: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    val colors = LuxTheme.colors
    val accent = LuxTheme.accent
    val density = LocalDensity.current
    val options = listOf(0 to "Standard", 2 to "Confort", 1 to "Réaliste")
    val displayMode = when {
        Settings.highContrastButAccurateLinePill -> 1
        Settings.easyOnTheEyes -> 2
        else -> 0
    }
    var sampleLine by remember { mutableStateOf(sampleLines.random()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)
            sampleLine = sampleLines.filter { it != sampleLine }.random()
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                SFSymbol(name = icon, size = 20.sp, color = accent)
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = LuxTheme.type.body.copy(fontWeight = FontWeight.Medium),
                    color = colors.label
                )
                Text(
                    text = subtitle,
                    style = LuxTheme.type.caption,
                    color = colors.secondaryLabel
                )
            }
        }

        val selectedIndex = options.indexOfFirst { it.first == displayMode }.coerceAtLeast(0)
        val animatedIndex by animateFloatAsState(
            targetValue = selectedIndex.toFloat(),
            animationSpec = LuxSprings.springFor(0.35, 0.8),
            label = "lineStyleSelection"
        )
        var rowSize by remember { mutableStateOf(IntSize.Zero) }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(4.dp)
                .onSizeChanged { rowSize = it }
        ) {
            if (rowSize.width > 0) {
                val cellWidthPx = rowSize.width.toFloat() / options.size
                Box(
                    Modifier
                        .offset { IntOffset((animatedIndex * cellWidthPx).roundToInt(), 0) }
                        .size(
                            with(density) { cellWidthPx.toDp() },
                            with(density) { rowSize.height.toDp() }
                        )
                        .background(accent.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                )
            }
            Row(Modifier.fillMaxWidth()) {
                options.forEach { (value, label) ->
                    val selected = value == displayMode
                    val pillScale by animateFloatAsState(
                        targetValue = if (selected) 1.05f else 1f,
                        animationSpec = LuxSprings.springFor(0.35, 0.8),
                        label = "samplePillScale"
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .scaleClickable(haptic = false) {
                                HapticFeedback.softImpact()
                                applyDisplayMode(value)
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp)
                    ) {
                        Crossfade(
                            targetState = sampleLine,
                            animationSpec = tween(durationMillis = 300, easing = LinearEasing),
                            label = "sampleLineSwap",
                            modifier = Modifier.graphicsLayer {
                                scaleX = pillScale
                                scaleY = pillScale
                            }
                        ) { line ->
                            SamplePill(
                                line = line,
                                isEasyOnTheEyes = value == 2,
                                isRealistic = value == 1
                            )
                        }
                        Text(
                            text = label,
                            style = LuxTheme.type.caption.copy(
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                            ),
                            color = if (selected) accent else colors.secondaryLabel
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SamplePill(
    line: String,
    isEasyOnTheEyes: Boolean,
    isRealistic: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = LuxTheme.colors
    val accent = LuxTheme.accent
    val density = LocalDensity.current
    val base = LineColors.color(line)?.let { Color(it) } ?: accent
    val lineColor = if (isDarkColor(base) && !isRealistic) lightenColor(base) else base
    val fill = when {
        isEasyOnTheEyes -> Color.Transparent
        isRealistic -> lineColor
        else -> base.copy(alpha = 0.25f)
    }
    val stroke = if (isEasyOnTheEyes) lineColor else colors.hairline
    val shape = if (line == "RL4") RoundedCornerShape(2.dp) else RoundedCornerShape(percent = 50)
    val textColor = when {
        isRealistic -> LineColors.textColor(line)?.let { Color(it) } ?: Color.White
        base == Color.Black -> Color.White
        else -> lineColor
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(30.dp, 20.dp)
            .background(fill, shape)
            .border(0.5.dp, stroke, shape)
    ) {
        Text(
            text = line.replace("RL4", "L4"),
            style = TextStyle(
                fontFamily = TpgFontFamily,
                fontSize = 11.sp,
                color = textColor,
                shadow = Shadow(
                    color = Color.Black.copy(alpha = 0.3f),
                    offset = Offset(0f, with(density) { 1.dp.toPx() }),
                    blurRadius = with(density) { 1.dp.toPx() }
                ),
                platformStyle = PlatformTextStyle(includeFontPadding = false)
            ),
            maxLines = 1
        )
    }
}

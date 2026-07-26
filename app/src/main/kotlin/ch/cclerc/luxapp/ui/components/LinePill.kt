package ch.cclerc.luxapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.cclerc.luxapp.core.SFSymbol
import ch.cclerc.luxapp.data.Settings
import ch.cclerc.luxapp.ui.theme.LuxTheme
import ch.cclerc.luxapp.ui.theme.TpgFontFamily
import ch.cclerc.luxcom.colors.LineColors
import ch.cclerc.luxcom.model.TransportationMode
import kotlin.math.max
import kotlin.math.min

enum class LinePillStyle { Standard, Realiste, Confort }

private val lausanneAgencies = setOf("151", "55", "764", "7256", "344", "29")

fun isDarkColor(color: Color): Boolean {
    val luminance = 0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue
    return luminance < 0.4f
}

fun lightenColor(color: Color, factor: Float = 0.25f): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    val newBrightness = min(1f, hsv[2] + factor)
    val newSaturation = max(0.3f, hsv[1] * 0.8f)
    return Color.hsv(hsv[0], newSaturation, newBrightness)
}

@Composable
private fun PillBody(
    text: String,
    fillColor: Color,
    strokeColor: Color,
    textColor: Color,
    isSquared: Boolean,
    width: Dp,
    height: Dp,
    fontSize: TextUnit
) {
    val shape = if (isSquared) RoundedCornerShape(2.dp) else RoundedCornerShape(50)
    val density = LocalDensity.current
    val shadowPx = with(density) { 1.dp.toPx() }
    Box(contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(width, height)
                .background(fillColor, shape)
                .border(0.5.dp, strokeColor, shape)
        )
        BasicText(
            text = text,
            style = TextStyle(
                fontFamily = TpgFontFamily,
                fontSize = fontSize,
                color = textColor,
                textAlign = TextAlign.Center,
                shadow = Shadow(
                    color = Color.Black.copy(alpha = 0.3f),
                    offset = Offset(0f, shadowPx),
                    blurRadius = shadowPx
                )
            )
        )
    }
}

@Composable
fun LinePill(
    line: String,
    agencyId: String?,
    mode: TransportationMode,
    width: Dp = 30.dp,
    height: Dp = 20.dp,
    fontSize: TextUnit = 11.sp
) {
    val accent = LuxTheme.accent
    val colors = LuxTheme.colors
    val isLausanne = agencyId in lausanneAgencies
    val isTAC = agencyId == "1"
    val isTrainDetected = line.startsWith("RL") || line.startsWith("IR") ||
        line.startsWith("RE") || line.startsWith("IC") || line == "R"
    val isSquared = mode.usesSquaredPill || isTrainDetected
    val formattedLine = if (line.startsWith("RL")) line.drop(1) else line

    val baseLineColor = when {
        isSquared && LineColors.color(line) == null -> Color(0xFFEA0706)
        isTAC -> LineColors.tacColors(line)?.let { Color(it) } ?: accent
        else -> (if (isLausanne) LineColors.tlColor(line) else LineColors.color(line))
            ?.let { Color(it) } ?: accent
    }

    val highContrast = Settings.highContrastButAccurateLinePill
    val easyOnTheEyes = Settings.easyOnTheEyes
    val lineColor =
        if (isDarkColor(baseLineColor) && !highContrast) lightenColor(baseLineColor) else baseLineColor

    val fillColor = when {
        easyOnTheEyes -> Color.Transparent
        highContrast -> lineColor
        else -> baseLineColor.copy(alpha = 0.25f)
    }
    val strokeColor = if (easyOnTheEyes) lineColor else colors.hairline
    val textColor = if (highContrast && !isLausanne) {
        Color((if (isTAC) LineColors.tacTextColor(line) else LineColors.textColor(line)) ?: 0xFFFFFFFF)
    } else {
        if (baseLineColor == Color.Black) Color.White else lineColor
    }

    PillBody(
        text = formattedLine,
        fillColor = fillColor,
        strokeColor = strokeColor,
        textColor = textColor,
        isSquared = isSquared,
        width = width,
        height = height,
        fontSize = fontSize
    )
}

@Composable
fun MorePill() {
    val accent = LuxTheme.accent
    val colors = LuxTheme.colors
    val shape = RoundedCornerShape(50)
    val fillColor = when {
        Settings.easyOnTheEyes -> Color.Transparent
        Settings.highContrastButAccurateLinePill -> colors.secondarySystemFill
        else -> accent.copy(alpha = 0.25f)
    }
    val strokeColor = if (Settings.easyOnTheEyes) accent.copy(alpha = 0.1f) else colors.hairline
    Box(contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(30.dp, 20.dp)
                .background(fillColor, shape)
                .border(0.5.dp, strokeColor, shape)
        )
        SFSymbol(name = "ellipsis", size = 11.sp, color = accent)
    }
}

@Composable
fun SamplePill(styleOverride: LinePillStyle, line: String = "18") {
    val isEasyOnTheEyes = styleOverride == LinePillStyle.Confort
    val isRealistic = styleOverride == LinePillStyle.Realiste
    val accent = LuxTheme.accent
    val colors = LuxTheme.colors
    val baseLineColor = LineColors.color(line)?.let { Color(it) } ?: accent
    val lineColor =
        if (isDarkColor(baseLineColor) && !isRealistic) lightenColor(baseLineColor) else baseLineColor

    val fillColor = when {
        isEasyOnTheEyes -> Color.Transparent
        isRealistic -> lineColor
        else -> baseLineColor.copy(alpha = 0.25f)
    }
    val strokeColor = if (isEasyOnTheEyes) lineColor else colors.hairline
    val textColor = if (isRealistic) {
        Color(LineColors.textColor(line) ?: 0xFFFFFFFF)
    } else {
        if (baseLineColor == Color.Black) Color.White else lineColor
    }

    PillBody(
        text = line.replace("RL4", "L4"),
        fillColor = fillColor,
        strokeColor = strokeColor,
        textColor = textColor,
        isSquared = line == "RL4",
        width = 30.dp,
        height = 20.dp,
        fontSize = 11.sp
    )
}

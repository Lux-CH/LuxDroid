package ch.cclerc.luxapp.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.cclerc.luxapp.data.Settings
import ch.cclerc.luxapp.ui.components.isDarkColor
import ch.cclerc.luxapp.ui.components.lightenColor
import ch.cclerc.luxapp.ui.theme.LuxTheme
import ch.cclerc.luxapp.ui.theme.TpgFontFamily
import ch.cclerc.luxcom.colors.LineColors
import ch.cclerc.luxcom.model.TransportationMode
import java.util.Locale

@Composable
fun EditableLinePill(
    lineNumber: String,
    onLineNumberChange: (String) -> Unit,
    mode: TransportationMode,
    modifier: Modifier = Modifier
) {
    val colors = LuxTheme.colors
    val accent = LuxTheme.accent
    val density = LocalDensity.current

    val isTrainDetected = lineNumber.startsWith("RL") || lineNumber.startsWith("IR") ||
        lineNumber.startsWith("RE") || lineNumber.startsWith("IC") || lineNumber == "R"
    val isSquared = mode.usesSquaredPill || isTrainDetected

    val rawColor = LineColors.color(lineNumber)?.let { Color(it) }
    val baseLineColor = when {
        isSquared && rawColor == null -> Color(0xFFEA0706)
        rawColor == null -> colors.systemGray
        else -> rawColor
    }
    val highContrast = Settings.highContrastButAccurateLinePill
    val lineColor = if (isDarkColor(baseLineColor) && !highContrast) {
        lightenColor(baseLineColor)
    } else {
        baseLineColor
    }
    val fillColor = if (highContrast) lineColor else baseLineColor.copy(alpha = 0.25f)
    val textColor = if (highContrast) {
        Color(LineColors.textColor(lineNumber) ?: 0xFFFFFFFF)
    } else {
        if (rawColor == null) colors.label else lineColor
    }
    val shape = RoundedCornerShape(if (isSquared) 4.dp else 50.dp)
    val shadowPx = with(density) { 1.dp.toPx() }

    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        Box(
            Modifier
                .size(80.dp, 50.dp)
                .background(fillColor, shape)
                .border(0.5.dp, colors.hairline, shape)
        )
        BasicTextField(
            value = lineNumber,
            onValueChange = { onLineNumberChange(it.uppercase(Locale.ROOT)) },
            singleLine = true,
            textStyle = TextStyle(
                fontFamily = TpgFontFamily,
                fontSize = 18.sp,
                color = textColor,
                textAlign = TextAlign.Center,
                shadow = Shadow(
                    color = Color.Black.copy(alpha = 0.3f),
                    offset = Offset(0f, shadowPx),
                    blurRadius = shadowPx
                )
            ),
            cursorBrush = SolidColor(accent),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                imeAction = ImeAction.Done
            ),
            modifier = Modifier.width(70.dp)
        )
    }
}

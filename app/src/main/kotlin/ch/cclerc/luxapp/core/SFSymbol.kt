package ch.cclerc.luxapp.core

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import ch.cclerc.luxapp.R

@OptIn(ExperimentalTextApi::class)
@Composable
fun SFSymbol(
    name: String,
    size: TextUnit,
    color: Color,
    weight: Int = 500,
    modifier: Modifier = Modifier
) {
    val symbol = SymbolMap.glyphFor(name)
    val opticalSize = size.value.coerceIn(20f, 48f)
    val fontFamily = remember(symbol.fill, weight, opticalSize) {
        FontFamily(
            Font(
                R.font.material_symbols_rounded,
                variationSettings = FontVariation.Settings(
                    FontVariation.Setting("FILL", symbol.fill),
                    FontVariation.Setting("wght", weight.toFloat()),
                    FontVariation.Setting("opsz", opticalSize)
                )
            )
        )
    }
    BasicText(
        text = symbol.glyph,
        style = TextStyle(
            fontFamily = fontFamily,
            fontSize = size,
            color = color,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.Both
            )
        ),
        maxLines = 1,
        overflow = TextOverflow.Visible,
        softWrap = false,
        modifier = modifier
    )
}

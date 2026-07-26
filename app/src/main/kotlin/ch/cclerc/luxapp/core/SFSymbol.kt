package ch.cclerc.luxapp.core

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import ch.cclerc.luxapp.R

private val Framework7FontFamily = FontFamily(Font(R.font.framework7_icons))
private val RemixFontFamily = FontFamily(Font(R.font.remixicon))

@Composable
fun SFSymbol(
    name: String,
    size: TextUnit,
    color: Color,
    weight: Int = 500,
    modifier: Modifier = Modifier,
    lineHeight: TextUnit = TextUnit.Unspecified
) {
    val symbol = SymbolMap.glyphFor(name)
    val fontFamily = when (symbol.font) {
        IconFont.F7 -> Framework7FontFamily
        IconFont.REMIX -> RemixFontFamily
    }
    BasicText(
        text = symbol.text,
        style = TextStyle(
            fontFamily = fontFamily,
            fontSize = size,
            lineHeight = lineHeight,
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

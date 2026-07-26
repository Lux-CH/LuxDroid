package ch.cclerc.luxapp.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import ch.cclerc.luxapp.R

@OptIn(ExperimentalTextApi::class)
private fun interFont(weight: Int): Font = Font(
    resId = R.font.inter_variable,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight))
)

val InterFontFamily: FontFamily = FontFamily(
    interFont(400),
    interFont(500),
    interFont(600),
    interFont(700),
    interFont(900)
)

val TpgFontFamily: FontFamily = FontFamily(Font(R.font.tpg_font))

object LuxTypography {
    private val centeredLineHeight = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None
    )
    private val noFontPadding = PlatformTextStyle(includeFontPadding = false)

    private fun style(
        size: TextUnit,
        lineHeight: TextUnit,
        weight: FontWeight = FontWeight.Normal
    ): TextStyle = TextStyle(
        fontFamily = InterFontFamily,
        fontSize = size,
        lineHeight = lineHeight,
        fontWeight = weight,
        platformStyle = noFontPadding,
        lineHeightStyle = centeredLineHeight
    )

    val caption2: TextStyle = style(11.sp, 13.sp)
    val caption: TextStyle = style(12.sp, 16.sp)
    val footnote: TextStyle = style(13.sp, 18.sp)
    val subheadline: TextStyle = style(15.sp, 20.sp)
    val callout: TextStyle = style(16.sp, 21.sp)
    val body: TextStyle = style(17.sp, 22.sp)
    val headline: TextStyle = style(17.sp, 22.sp, FontWeight.SemiBold)
    val title3: TextStyle = style(20.sp, 25.sp)
    val title2: TextStyle = style(22.sp, 28.sp, FontWeight.SemiBold)
    val title: TextStyle = style(28.sp, 34.sp, FontWeight.Bold)
    val largeTitle: TextStyle = style(34.sp, 41.sp, FontWeight.Bold)

    fun timeVariant(style: TextStyle): TextStyle = style.copy(fontFeatureSettings = "tnum")
}

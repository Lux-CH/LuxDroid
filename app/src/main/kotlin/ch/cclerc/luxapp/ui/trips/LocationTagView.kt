package ch.cclerc.luxapp.ui.trips

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.cclerc.luxapp.core.SFSymbol
import ch.cclerc.luxapp.domain.search.SearchResultVisualStyleStore
import ch.cclerc.luxapp.ui.anim.PlainIndication
import ch.cclerc.luxapp.ui.theme.LuxColors
import ch.cclerc.luxapp.ui.theme.LuxTheme
import ch.cclerc.luxapp.ui.theme.iosShadow
import ch.cclerc.luxapp.viewmodel.SelectedLocation
import ch.cclerc.luxcom.model.LocationType
import ch.cclerc.luxcom.model.SearchResult

data class LocationTagIconStyle(val symbolName: String, val color: Color)

@Composable
fun LocationTagView(
    location: SelectedLocation,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    shortcutSymbol: (SearchResult) -> String? = { null }
) {
    val colors = LuxTheme.colors
    val accent = LuxTheme.accent
    val shape = RoundedCornerShape(75.dp)
    val fillBase = colors.secondarySystemFill
    val storedStyles by SearchResultVisualStyleStore.styles.collectAsState()

    val iconStyle = tagIconStyle(location, storedStyles.keys.size, colors, accent, shortcutSymbol)

    Row(
        modifier = modifier
            .iosShadow(
                color = Color.Black.copy(alpha = 0.1f),
                blurRadius = 4.dp,
                offsetY = 2.dp,
                shape = shape
            )
            .background(fillBase.copy(alpha = fillBase.alpha * 0.4f), shape)
            .border(0.75.dp, colors.label.copy(alpha = 0.15f), shape)
            .padding(horizontal = 15.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (iconStyle != null) {
            Box(
                modifier = Modifier
                    .size(width = 28.dp, height = 18.dp)
                    .background(iconStyle.color.copy(alpha = 0.12f), RoundedCornerShape(50))
                    .border(0.5.dp, iconStyle.color.copy(alpha = 0.35f), RoundedCornerShape(50)),
                contentAlignment = Alignment.Center
            ) {
                SFSymbol(name = iconStyle.symbolName, size = 10.sp, color = iconStyle.color)
            }
        }

        Text(
            text = location.displayName,
            fontSize = 15.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.Medium,
            color = colors.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = LuxTheme.type.subheadline.copy(
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.Both
                )
            ),
            modifier = Modifier.weight(1f, fill = false)
        )

        Box(
            modifier = Modifier
                .clip(CircleShape)
                .clickable(interactionSource = null, indication = PlainIndication) { onRemove() }
                .padding(4.dp)
                .size(15.dp),
            contentAlignment = Alignment.Center
        ) {
            SFSymbol(name = "xmark.circle.fill", size = 15.sp, color = colors.systemGray)
        }
    }
}

private fun tagIconStyle(
    location: SelectedLocation,
    @Suppress("UNUSED_PARAMETER") styleRevision: Int,
    colors: LuxColors,
    accent: Color,
    shortcutSymbol: (SearchResult) -> String?
): LocationTagIconStyle? = when (location) {
    SelectedLocation.CurrentPosition -> LocationTagIconStyle("location.fill", accent)
    is SelectedLocation.SearchResultLocation -> {
        val result = location.result
        val shortcut = shortcutSymbol(result)
        val stored = if (result.type != LocationType.STOP) {
            SearchResultVisualStyleStore.style(result.id)
        } else {
            null
        }
        when {
            shortcut != null -> LocationTagIconStyle(shortcut, accent)
            stored != null -> LocationTagIconStyle(stored.symbolName, stored.colorKey.resolve(colors, accent))
            else -> defaultTagIcon(result.type, colors, accent)
        }
    }
}

private fun defaultTagIcon(
    type: LocationType,
    colors: LuxColors,
    accent: Color
): LocationTagIconStyle = when (type) {
    LocationType.ADDRESS -> LocationTagIconStyle("mappin", colors.systemRed)
    LocationType.PLACE -> LocationTagIconStyle("building.fill", colors.systemBlue)
    LocationType.STOP -> LocationTagIconStyle("signpost.right.fill", accent)
}

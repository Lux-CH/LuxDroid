package ch.cclerc.luxapp.ui.trips

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import ch.cclerc.luxapp.core.LocationService
import ch.cclerc.luxapp.core.SFSymbol
import ch.cclerc.luxapp.domain.search.SearchResultVisualColor
import ch.cclerc.luxapp.domain.search.SearchResultVisualStyleStore
import ch.cclerc.luxapp.ui.stops.formatDistance
import ch.cclerc.luxapp.ui.theme.LuxColors
import ch.cclerc.luxapp.ui.theme.LuxTheme
import ch.cclerc.luxcom.geo.calculateDistance
import ch.cclerc.luxcom.model.LocationType
import ch.cclerc.luxcom.model.SearchResult

internal fun defaultIconForType(
    type: LocationType,
    colors: LuxColors,
    accent: Color
): Pair<String, Color> = when (type) {
    LocationType.ADDRESS -> "mappin" to colors.systemRed
    LocationType.PLACE -> "building.fill" to colors.systemBlue
    LocationType.STOP -> "signpost.right.fill" to accent
}

internal fun relevantAreaName(result: SearchResult): String? {
    result.areas.firstOrNull { it.matched }?.let { return it.name }
    result.areas.firstOrNull { it.default == true }?.let { return it.name }
    return result.areas.minByOrNull { it.adminLevel }?.name
}

internal fun formattedAddress(result: SearchResult): String? {
    val components = mutableListOf<String>()

    result.street?.let { street ->
        val houseNumber = result.houseNumber
        components.add(if (houseNumber != null) "$street $houseNumber" else street)
    }

    val city = result.areas.firstOrNull { it.matched }?.name
        ?: result.areas.firstOrNull { it.default == true }?.name
    if (city != null) components.add(city)

    return if (components.isEmpty()) null else components.joinToString(", ")
}

@Composable
internal fun storedIconStyle(resultId: String): Pair<String, SearchResultVisualColor>? {
    val styles by SearchResultVisualStyleStore.styles.collectAsState()
    return remember(resultId, styles) {
        (styles[resultId] ?: SearchResultVisualStyleStore.style(resultId))
            ?.let { it.symbolName to it.colorKey }
    }
}

@Composable
internal fun SearchResultIcon(
    symbolName: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(38.dp)
            .background(color.copy(alpha = 0.14f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        SFSymbol(name = symbolName, size = 16.sp, color = color, weight = 500)
    }
}

@Composable
fun SearchResultRow(
    result: SearchResult,
    modifier: Modifier = Modifier
) {
    val colors = LuxTheme.colors
    val accent = LuxTheme.accent
    val location by LocationService.location.collectAsState()

    val stored = storedIconStyle(result.id)
    val (symbolName, iconColor) = if (stored != null && result.type != LocationType.STOP) {
        stored.first to stored.second.resolve(colors, accent)
    } else {
        defaultIconForType(result.type, colors, accent)
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SearchResultIcon(symbolName = symbolName, color = iconColor)

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = result.name,
                style = LuxTheme.type.subheadline,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = colors.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            val address = if (result.type != LocationType.ADDRESS) formattedAddress(result) else null
            val area = if (result.type != LocationType.STOP) relevantAreaName(result) else null
            val secondary = address ?: area
            if (secondary != null) {
                Text(
                    text = secondary,
                    style = LuxTheme.type.caption,
                    fontSize = 12.sp,
                    color = colors.secondaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.weight(1f))

        val current = location
        if (current != null && !(result.lat == 0.0 && result.lon == 0.0)) {
            val distance = calculateDistance(current.latitude, current.longitude, result.lat, result.lon)
            Text(
                text = formatDistance(distance),
                style = LuxTheme.type.caption,
                fontSize = 12.sp,
                color = colors.tertiaryLabel,
                maxLines = 1
            )
        }
    }
}

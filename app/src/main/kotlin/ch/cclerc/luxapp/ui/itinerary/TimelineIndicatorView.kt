package ch.cclerc.luxapp.ui.itinerary

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.cclerc.luxapp.core.SFSymbol
import ch.cclerc.luxapp.ui.theme.LuxTheme

internal val TimelineRailWidth = 3.dp

@Composable
internal fun timelineSurfaceColor(): Color {
    val colors = LuxTheme.colors
    return if (colors.isDark) colors.systemBackgroundElevated else Color.White
}

@Composable
fun TimelineIndicatorView(
    legColor: Color,
    accentColor: Color,
    isFirstStop: Boolean,
    isLastStop: Boolean,
    isDepartureStop: Boolean,
    isArrivalStop: Boolean,
    isCurrentStop: Boolean,
    modifier: Modifier = Modifier
) {
    val surface = timelineSurfaceColor()
    val isSpecialStop = isDepartureStop || isArrivalStop
    val lineColor = if (isCurrentStop) accentColor else legColor

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(TimelineRailWidth)
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .width(TimelineRailWidth)
                    .background(if (isFirstStop) Color.Transparent else lineColor)
            )
            Box(
                Modifier
                    .weight(1f)
                    .width(TimelineRailWidth)
                    .background(if (isLastStop) Color.Transparent else lineColor)
            )
        }

        when {
            isSpecialStop -> {
                Box(
                    Modifier
                        .size(28.dp)
                        .background(surface, CircleShape)
                        .border(1.5.dp, legColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    SFSymbol(
                        name = if (isDepartureStop) "arrow.down.circle.fill" else "flag.circle.fill",
                        size = 18.sp,
                        color = legColor,
                        weight = 500
                    )
                }
            }
            isCurrentStop -> {
                Box(
                    Modifier
                        .size(18.dp)
                        .background(legColor, CircleShape)
                        .border(2.dp, accentColor, CircleShape)
                )
            }
            else -> {
                Box(
                    Modifier
                        .size(16.dp)
                        .background(legColor, CircleShape)
                        .border(2.dp, surface, CircleShape)
                )
            }
        }
    }
}

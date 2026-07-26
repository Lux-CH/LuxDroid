package ch.cclerc.luxapp.ui.itinerary

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.cclerc.luxapp.core.SFSymbol
import ch.cclerc.luxapp.ui.theme.LuxTheme

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
    val colors = LuxTheme.colors
    val isSpecialStop = isDepartureStop || isArrivalStop
    val indicatorSize: Dp = when {
        isSpecialStop -> 28.dp
        isCurrentStop -> 18.dp
        else -> 16.dp
    }
    val lineColor = if (isCurrentStop) accentColor else legColor

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (!isFirstStop) {
            Box(
                Modifier
                    .offset(y = (-18).dp)
                    .size(3.dp, indicatorSize)
                    .background(lineColor)
            )
        }

        if (!isLastStop) {
            Box(
                Modifier
                    .offset(y = 18.dp)
                    .size(3.dp, indicatorSize)
                    .background(lineColor)
            )
        }

        when {
            isSpecialStop -> {
                Box(
                    Modifier
                        .size(28.dp)
                        .background(colors.systemBackground, CircleShape)
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
                        .border(2.dp, colors.systemBackground, CircleShape)
                )
            }
        }
    }
}

package ch.cclerc.luxapp.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import ch.cclerc.luxapp.ui.theme.LuxSprings
import ch.cclerc.luxapp.ui.theme.LuxTheme
import kotlin.math.min

@Composable
fun PaginationDots(
    groupsCount: Int,
    currentPage: Int,
    animateIn: Boolean,
    activeDotColor: Color = LuxTheme.colors.label.copy(alpha = 0.5f),
    inactiveDotColor: Color = LuxTheme.colors.secondaryLabel.copy(alpha = 0.3f),
    modifier: Modifier = Modifier
) {
    if (groupsCount <= 1) return
    val entranceAlpha by animateFloatAsState(
        if (animateIn) 1f else 0f,
        tween(durationMillis = 400, delayMillis = 300, easing = EaseInOut)
    )
    Row(
        modifier
            .graphicsLayer { alpha = entranceAlpha }
            .padding(bottom = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        repeat(min(groupsCount, 10)) { index ->
            val active = index == currentPage
            val scale by animateFloatAsState(if (active) 1f else 0.8f, LuxSprings.Snappy)
            val color by animateColorAsState(
                if (active) activeDotColor else inactiveDotColor,
                LuxSprings.springFor(0.3, 0.7)
            )
            Box(
                Modifier
                    .size(5.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .background(color, CircleShape)
            )
        }
    }
}

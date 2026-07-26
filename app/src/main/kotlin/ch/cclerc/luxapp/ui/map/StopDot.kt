package ch.cclerc.luxapp.ui.map

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import ch.cclerc.luxapp.ui.theme.LuxSprings
import ch.cclerc.luxapp.ui.theme.iosShadow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

val StopDotHitSize = 44.dp

val StopDotTerminalSize = 20.dp
val StopDotIntermediateSize = 10.dp
val StopDotDefaultSize = 16.dp
val StopDotTerminalHaloSize = 30.dp

private const val STOP_DOT_BOUNCE_SCALE = 1.2f
private const val STOP_DOT_BOUNCE_HOLD_MS = 200L

fun stopDotDiameter(isTerminal: Boolean, isIntermediate: Boolean) = when {
    isTerminal -> StopDotTerminalSize
    isIntermediate -> StopDotIntermediateSize
    else -> StopDotDefaultSize
}

@Composable
fun StopDot(
    color: Color,
    isTerminal: Boolean,
    isIntermediate: Boolean,
    modifier: Modifier = Modifier,
    onTapDown: () -> Unit = {},
    onTap: () -> Unit = {}
) {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }
    val diameter = stopDotDiameter(isTerminal, isIntermediate)
    val strokeWidth = if (isTerminal) 3.dp else 1.dp

    Box(
        modifier = modifier
            .size(StopDotHitSize)
            .clip(CircleShape)
            .clickable(interactionSource = interactionSource, indication = null) {
                onTapDown()
                scope.launch {
                    launch { scale.animateTo(STOP_DOT_BOUNCE_SCALE, LuxSprings.StopBounce) }
                    delay(STOP_DOT_BOUNCE_HOLD_MS)
                    scale.snapTo(1f)
                    onTap()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (isTerminal) {
            Box(
                Modifier
                    .size(StopDotTerminalHaloSize)
                    .background(color.copy(alpha = 0.15f), CircleShape)
            )
        }

        Box(
            Modifier
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                }
                .size(diameter)
                .iosShadow(
                    color = Color.Black.copy(alpha = 0.2f),
                    blurRadius = 2.dp,
                    offsetY = 1.dp,
                    shape = CircleShape
                )
                .background(Color.White, CircleShape)
                .border(strokeWidth, color, CircleShape)
        )
    }
}

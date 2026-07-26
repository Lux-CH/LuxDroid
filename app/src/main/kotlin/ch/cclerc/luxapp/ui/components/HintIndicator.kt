package ch.cclerc.luxapp.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.cclerc.luxapp.core.SFSymbol
import ch.cclerc.luxapp.ui.theme.LuxSprings
import ch.cclerc.luxapp.ui.theme.LuxTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private suspend fun runBounce(offset: Animatable<Float, AnimationVector1D>) = coroutineScope {
    launch { offset.animateTo(-4f, LuxSprings.Snappy) }
    delay(300)
    offset.animateTo(0f, LuxSprings.Snappy)
}

@Composable
fun HintIndicator(
    icon: String,
    message: String,
    delayMs: Long,
    durationMs: Long,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null,
    onTap: (() -> Unit)? = null
) {
    val colors = LuxTheme.colors
    var visible by remember { mutableStateOf(false) }
    val visibleFraction by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 750, easing = EaseOut),
        label = "hintVisible"
    )
    val pulseTransition = rememberInfiniteTransition(label = "hintPulse")
    val pulseOpacity by pulseTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hintPulseAlpha"
    )
    val bounceOffset = remember { Animatable(0f) }
    val scope: CoroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        launch {
            delay(delayMs)
            visible = true
        }
        launch {
            delay(delayMs + 2000)
            runBounce(bounceOffset)
        }
        launch {
            delay(delayMs + durationMs)
            visible = false
            onDismiss?.invoke()
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.clickable(interactionSource = null, indication = null) {
            if (onTap != null) onTap() else scope.launch { runBounce(bounceOffset) }
        }
    ) {
        SFSymbol(
            name = icon,
            size = 20.sp,
            color = colors.secondaryLabel,
            weight = 700,
            modifier = Modifier
                .offset { IntOffset(0, bounceOffset.value.dp.roundToPx()) }
                .graphicsLayer { alpha = (visibleFraction * (pulseOpacity - 0.1f)).coerceAtLeast(0f) }
        )
        Text(
            text = message,
            style = LuxTheme.type.caption,
            color = colors.secondaryLabel,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .graphicsLayer { alpha = visibleFraction * pulseOpacity }
        )
    }
}

package ch.cclerc.luxapp.ui.anim

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

fun Modifier.pulse(
    fromAlpha: Float = 1f,
    toAlpha: Float = 0.4f,
    durationMs: Int = 800,
    autoreverse: Boolean = true
): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "pulse")
    val value by transition.animateFloat(
        initialValue = fromAlpha,
        targetValue = toAlpha,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMs, easing = EaseInOut),
            repeatMode = if (autoreverse) RepeatMode.Reverse else RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )
    graphicsLayer { alpha = value }
}

fun Modifier.pulseScale(
    from: Float = 1f,
    to: Float = 0.88f,
    durationMs: Int = 350,
    autoreverse: Boolean = true
): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "pulseScale")
    val value by transition.animateFloat(
        initialValue = from,
        targetValue = to,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMs, easing = EaseInOut),
            repeatMode = if (autoreverse) RepeatMode.Reverse else RepeatMode.Restart
        ),
        label = "pulseScaleValue"
    )
    graphicsLayer {
        scaleX = value
        scaleY = value
    }
}

fun Modifier.breathe(durationMs: Int = 350): Modifier =
    pulse(fromAlpha = 1f, toAlpha = 0f, durationMs = durationMs)
        .pulseScale(from = 1f, to = 0.88f, durationMs = durationMs)

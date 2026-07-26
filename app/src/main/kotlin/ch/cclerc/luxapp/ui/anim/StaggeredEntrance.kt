package ch.cclerc.luxapp.ui.anim

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ch.cclerc.luxapp.ui.theme.LuxSprings
import kotlinx.coroutines.delay

fun Modifier.staggeredEntrance(
    index: Int,
    visible: Boolean,
    delayPerItemMs: Long = 40,
    baseDelayMs: Long = 0,
    fromOffsetY: Dp = 8.dp,
    fromScale: Float = 1f,
    spec: AnimationSpec<Float> = LuxSprings.ListEntrance
): Modifier = composed {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        if (visible) {
            val wait = baseDelayMs + index.coerceAtLeast(0) * delayPerItemMs
            if (progress.value < 1f && wait > 0) delay(wait)
            progress.animateTo(1f, spec)
        } else {
            progress.animateTo(0f, spec)
        }
    }
    graphicsLayer {
        val p = progress.value
        alpha = p
        translationY = fromOffsetY.toPx() * (1f - p)
        val s = fromScale + (1f - fromScale) * p
        scaleX = s
        scaleY = s
    }
}

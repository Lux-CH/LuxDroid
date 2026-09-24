package ch.cclerc.luxapp.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

private const val PUSH_DURATION_MS = 350

@Composable
fun <T : Any> SheetPushHost(
    pushed: T?,
    onPop: () -> Unit,
    modifier: Modifier = Modifier,
    destination: @Composable (T) -> Unit,
    root: @Composable () -> Unit
) {
    BackHandler(enabled = pushed != null, onBack = onPop)

    AnimatedContent(
        targetState = pushed,
        modifier = modifier,
        transitionSpec = {
            val spec = tween<androidx.compose.ui.unit.IntOffset>(PUSH_DURATION_MS, easing = FastOutSlowInEasing)
            val fade = tween<Float>(PUSH_DURATION_MS, easing = FastOutSlowInEasing)
            if (targetState != null) {
                (slideInHorizontally(spec) { it } + fadeIn(fade, initialAlpha = 0.6f)) togetherWith
                    (slideOutHorizontally(spec) { -it / 3 } + fadeOut(fade, targetAlpha = 0.4f))
            } else {
                (slideInHorizontally(spec) { -it / 3 } + fadeIn(fade, initialAlpha = 0.4f)) togetherWith
                    (slideOutHorizontally(spec) { it } + fadeOut(fade, targetAlpha = 0.6f))
            }
        },
        label = "sheet-push"
    ) { target ->
        Box(Modifier.fillMaxSize()) {
            if (target != null) destination(target) else root()
        }
    }
}

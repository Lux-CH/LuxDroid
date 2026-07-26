package ch.cclerc.luxapp.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import ch.cclerc.luxapp.ui.theme.LuxTheme
import kotlinx.coroutines.flow.first

class LuxCoverRequest(
    val dismissOnBack: Boolean = true,
    val content: @Composable () -> Unit
)

class CoverController {
    internal val requests: SnapshotStateList<LuxCoverRequest> = mutableStateListOf()

    val covers: List<LuxCoverRequest> get() = requests

    fun present(cover: LuxCoverRequest) {
        requests.add(cover)
    }

    fun dismiss() {
        if (requests.isNotEmpty()) {
            requests.removeAt(requests.lastIndex)
        }
    }
}

@Composable
fun FullScreenCoverHost(controller: CoverController, modifier: Modifier = Modifier) {
    val displayed = remember { mutableStateListOf<LuxCoverRequest>() }

    LaunchedEffect(controller) {
        snapshotFlow { controller.requests.toList() }.collect { target ->
            target.forEach { request ->
                if (!displayed.contains(request)) {
                    displayed.add(request)
                }
            }
        }
    }

    BackHandler(enabled = controller.requests.isNotEmpty()) {
        val top = controller.requests.lastOrNull() ?: return@BackHandler
        if (top.dismissOnBack) {
            controller.dismiss()
        }
    }

    Box(modifier.fillMaxSize()) {
        displayed.forEach { request ->
            key(request) {
                CoverLayer(
                    request = request,
                    presented = controller.requests.contains(request),
                    onExited = { displayed.remove(request) }
                )
            }
        }
    }
}

@Composable
private fun CoverLayer(
    request: LuxCoverRequest,
    presented: Boolean,
    onExited: () -> Unit
) {
    val visibleState = remember { MutableTransitionState(false) }
    visibleState.targetState = presented

    LaunchedEffect(presented) {
        if (!presented) {
            snapshotFlow { visibleState.isIdle && !visibleState.currentState }.first { it }
            onExited()
        }
    }

    AnimatedVisibility(
        visibleState = visibleState,
        enter = slideInVertically(animationSpec = tween(350, easing = EaseOut)) { it },
        exit = slideOutVertically(animationSpec = tween(350, easing = EaseOut)) { it }
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(LuxTheme.colors.systemBackground)
                .pointerInput(Unit) {}
        ) {
            request.content()
        }
    }
}

package ch.cclerc.luxapp.ui.navigation

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import ch.cclerc.luxapp.ui.theme.LuxTheme
import ch.cclerc.luxcom.model.SearchResult
import kotlinx.coroutines.CancellationException

sealed interface LuxDestination {
    data class Placeholder(val label: String) : LuxDestination
    data class IndividualStop(val stop: SearchResult) : LuxDestination
    data class TripsSearch(val initial: SearchResult?, val toField: Boolean) : LuxDestination
}

class LuxBackStack {
    val entries: SnapshotStateList<LuxDestination> = mutableStateListOf()

    fun push(destination: LuxDestination) {
        entries.add(destination)
    }

    fun pop(): LuxDestination? =
        if (entries.isEmpty()) null else entries.removeAt(entries.lastIndex)

    fun popTo(destination: LuxDestination) {
        val index = entries.lastIndexOf(destination)
        if (index < 0) return
        while (entries.lastIndex > index) {
            entries.removeAt(entries.lastIndex)
        }
    }
}

private val PushSpring: SpringSpec<Float> = spring(dampingRatio = 1f, stiffness = 322f)

@Composable
fun LuxBackStackHost(
    stack: LuxBackStack,
    modifier: Modifier = Modifier,
    rootContent: @Composable () -> Unit,
    destinationContent: @Composable (LuxDestination) -> Unit
) {
    val displayed = remember { mutableStateListOf<LuxDestination>() }
    val progress = remember { Animatable(1f) }

    LaunchedEffect(stack) {
        snapshotFlow { stack.entries.toList() }.collect { target ->
            val current = displayed.toList()
            when {
                target == current -> Unit
                target.size > current.size && target.subList(0, current.size) == current -> {
                    displayed.clear()
                    displayed.addAll(target)
                    progress.snapTo(0f)
                    progress.animateTo(1f, PushSpring)
                }
                target.size < current.size && current.subList(0, target.size) == target -> {
                    progress.animateTo(0f, PushSpring)
                    displayed.clear()
                    displayed.addAll(target)
                    progress.snapTo(1f)
                }
                else -> {
                    displayed.clear()
                    displayed.addAll(target)
                    progress.snapTo(1f)
                }
            }
        }
    }

    PredictiveBackHandler(enabled = displayed.isNotEmpty()) { events ->
        try {
            events.collect { event ->
                progress.snapTo(1f - event.progress)
            }
            progress.animateTo(0f, PushSpring)
            val target = displayed.toList().dropLast(1)
            displayed.clear()
            displayed.addAll(target)
            stack.pop()
            progress.snapTo(1f)
        } catch (_: CancellationException) {
            progress.animateTo(1f, PushSpring)
        }
    }

    Box(modifier.fillMaxSize()) {
        val layerCount = displayed.size + 1
        PushLayer(
            position = 0,
            layerCount = layerCount,
            progress = { progress.value },
            content = rootContent
        )
        displayed.forEachIndexed { index, destination ->
            key(index, destination) {
                PushLayer(
                    position = index + 1,
                    layerCount = layerCount,
                    progress = { progress.value }
                ) {
                    destinationContent(destination)
                }
            }
        }
    }
}

@Composable
private fun PushLayer(
    position: Int,
    layerCount: Int,
    progress: () -> Float,
    content: @Composable () -> Unit
) {
    val isTop = position == layerCount - 1
    val isBelowTop = position == layerCount - 2
    val baseFill = LuxTheme.colors.systemBackground
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                if (layerCount > 1) {
                    translationX = when {
                        isTop -> size.width * (1f - progress())
                        isBelowTop -> -size.width * 0.25f * progress()
                        else -> -size.width * 0.25f
                    }
                }
            }
            .background(if (position > 0) baseFill else Color.Transparent)
    ) {
        content()
        if (isBelowTop && layerCount > 1) {
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawRect(Color.Black.copy(alpha = 0.10f * progress()))
                    }
            )
        }
    }
}

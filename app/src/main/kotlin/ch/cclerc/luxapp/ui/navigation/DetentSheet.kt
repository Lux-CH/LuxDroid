package ch.cclerc.luxapp.ui.navigation

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import ch.cclerc.luxapp.ui.theme.LuxShapes
import ch.cclerc.luxapp.ui.theme.LuxTheme
import kotlin.math.roundToInt

private val DetentSpring: SpringSpec<Float> = spring(dampingRatio = 1f, stiffness = 322f)

data class DetentSheetState(val detents: List<SheetDetent>) {
    internal val ordered: List<SheetDetent> = detents.distinct().sortedBy { it.sortFraction() }
    internal val anchored = AnchoredDraggableState(initialValue = 0)
    internal val peekHeightState = mutableStateOf(0.dp)

    val peekHeightDp: Dp get() = peekHeightState.value

    val currentDetent: SheetDetent
        get() = ordered[anchored.currentValue.coerceIn(0, ordered.lastIndex)]
}

@Composable
fun DetentSheet(
    state: DetentSheetState,
    cornerRadius: Dp = 38.dp,
    showDragIndicator: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val colors = LuxTheme.colors
    val density = LocalDensity.current
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    BoxWithConstraints(modifier.fillMaxSize()) {
        val containerHeightPx = constraints.maxHeight.toFloat()
        val largeHeightPx = containerHeightPx - with(density) { (topInset + 10.dp).toPx() }
        val bottomInsetPx = with(density) { bottomInset.toPx() }
        val heights = remember(state, containerHeightPx, largeHeightPx, bottomInsetPx) {
            val computed = state.ordered.map { detent ->
                val height = detentHeightPx(detent, containerHeightPx, largeHeightPx)
                if (detent == SheetDetent.Large) height else height + bottomInsetPx
            }
            state.anchored.updateAnchors(
                DraggableAnchors {
                    computed.forEachIndexed { index, height ->
                        index at (containerHeightPx - height)
                    }
                }
            )
            state.peekHeightState.value = with(density) { computed.first().toDp() }
            computed
        }
        val flingThresholdPx = with(density) { 125.dp.toPx() }
        val connection = remember(state, flingThresholdPx) {
            DetentNestedScrollConnection(state.anchored, flingThresholdPx)
        }
        val fling = AnchoredDraggableDefaults.flingBehavior(
            state = state.anchored,
            animationSpec = DetentSpring
        )

        Box(
            Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .height(with(density) { heights.last().toDp() })
                .offset {
                    val y = state.anchored.offset
                    val resolved = if (y.isNaN()) containerHeightPx - heights.first() else y
                    IntOffset(0, resolved.roundToInt())
                }
                .nestedScroll(connection)
                .anchoredDraggable(
                    state = state.anchored,
                    orientation = Orientation.Vertical,
                    flingBehavior = fling
                )
                .clip(LuxShapes.topCorners(cornerRadius))
                .background(colors.systemBackground)
        ) {
            content()
            if (showDragIndicator) {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 5.dp)
                        .size(width = 36.dp, height = 5.dp)
                        .background(colors.systemGray3, RoundedCornerShape(2.5.dp))
                )
            }
        }
    }
}

private class DetentNestedScrollConnection(
    private val state: AnchoredDraggableState<Int>,
    private val flingThresholdPx: Float
) : NestedScrollConnection {

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        val delta = available.y
        return if (delta < 0 && source == NestedScrollSource.UserInput &&
            state.offset > state.anchors.minPosition()
        ) {
            Offset(0f, state.dispatchRawDelta(delta))
        } else {
            Offset.Zero
        }
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource
    ): Offset {
        val delta = available.y
        return if (delta > 0 && source == NestedScrollSource.UserInput) {
            Offset(0f, state.dispatchRawDelta(delta))
        } else {
            Offset.Zero
        }
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        return if (available.y < 0 && state.offset > state.anchors.minPosition()) {
            settle(available.y)
            available
        } else {
            Velocity.Zero
        }
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        settle(available.y)
        return available
    }

    private suspend fun settle(velocity: Float) {
        val anchors = state.anchors
        if (anchors.size == 0) return
        val currentOffset = state.offset
        if (currentOffset.isNaN()) return
        val target = when {
            velocity < -flingThresholdPx -> anchors.closestAnchor(currentOffset, false)
            velocity > flingThresholdPx -> anchors.closestAnchor(currentOffset, true)
            else -> anchors.closestAnchor(currentOffset)
        } ?: return
        state.animateTo(target, DetentSpring)
    }
}

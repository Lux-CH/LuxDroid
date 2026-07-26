package ch.cclerc.luxapp.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.SpringSpec
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import ch.cclerc.luxapp.ui.theme.LuxShapes
import ch.cclerc.luxapp.ui.theme.LuxSprings
import ch.cclerc.luxapp.ui.theme.LuxTheme
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val SheetSpring: SpringSpec<Float> = LuxSprings.springFor(0.5, 1.0)

private fun Modifier.consumeAllPointerInput(key: Any?): Modifier = pointerInput(key) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Main)
            event.changes.forEach { change ->
                if (change.pressed || change.previousPressed) change.consume()
            }
        }
    }
}

private fun Modifier.scrimGestures(key: Any?, onTap: () -> Unit): Modifier = pointerInput(key) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = true).consume()
        var travel = 0f
        var released = false
        while (!released) {
            val event = awaitPointerEvent(PointerEventPass.Main)
            event.changes.forEach { change ->
                travel += (change.position - change.previousPosition).getDistance()
                change.consume()
            }
            released = event.changes.all { !it.pressed }
        }
        if (travel <= viewConfiguration.touchSlop) onTap()
    }
}

@Composable
fun SheetHost(controller: SheetController, modifier: Modifier = Modifier) {
    val displayed = remember { mutableStateListOf<LuxSheetRequest>() }

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
        controller.dismiss()
    }

    Box(modifier.fillMaxSize()) {
        displayed.forEach { request ->
            key(request) {
                SheetLayer(
                    request = request,
                    presented = controller.requests.contains(request),
                    onDismissRequest = { controller.requests.remove(request) },
                    onExited = { displayed.remove(request) }
                )
            }
        }
    }
}

@Composable
private fun SheetLayer(
    request: LuxSheetRequest,
    presented: Boolean,
    onDismissRequest: () -> Unit,
    onExited: () -> Unit
) {
    val colors = LuxTheme.colors
    val density = LocalDensity.current
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val containerHeightPx = constraints.maxHeight.toFloat()
        val largeHeightPx = containerHeightPx - with(density) { (topInset + 10.dp).toPx() }
        val detentHeights = remember(request, containerHeightPx, largeHeightPx) {
            request.detents
                .distinct()
                .map { detentHeightPx(it, containerHeightPx, largeHeightPx) }
                .sorted()
        }
        val smallestHeight = detentHeights.first()
        val largestHeight = detentHeights.last()
        val restingOffset = containerHeightPx - smallestHeight
        val topOffset = containerHeightPx - largestHeight
        val offset = remember { Animatable(containerHeightPx) }
        val scope = rememberCoroutineScope()
        val flingThresholdPx = with(density) { 125.dp.toPx() }

        LaunchedEffect(presented) {
            if (presented) {
                offset.animateTo(restingOffset, SheetSpring)
            } else {
                offset.animateTo(containerHeightPx, SheetSpring)
                onExited()
            }
        }

        val dismissLimit = if (request.interactiveDismiss) containerHeightPx else restingOffset

        fun dispatchRawDelta(delta: Float, max: Float = dismissLimit): Float {
            val current = offset.value
            val next = (current + delta).coerceIn(topOffset, max)
            val consumed = next - current
            if (consumed != 0f) {
                scope.launch { offset.snapTo(next) }
            }
            return consumed
        }

        suspend fun settle(velocity: Float, maxTarget: Float = dismissLimit) {
            val anchorOffsets = buildList {
                detentHeights.forEach { add(containerHeightPx - it) }
                if (request.interactiveDismiss) add(containerHeightPx)
            }.sorted().filter { it <= maxTarget + 0.5f }
            if (anchorOffsets.isEmpty()) return
            val current = offset.value
            val target = when {
                velocity > flingThresholdPx ->
                    anchorOffsets.firstOrNull { it > current + 1f } ?: anchorOffsets.last()
                velocity < -flingThresholdPx ->
                    anchorOffsets.lastOrNull { it < current - 1f } ?: anchorOffsets.first()
                else -> anchorOffsets.minByOrNull { abs(it - current) } ?: restingOffset
            }
            if (request.interactiveDismiss && target >= containerHeightPx - 0.5f) {
                onDismissRequest()
            } else {
                offset.animateTo(target, SheetSpring, initialVelocity = velocity)
            }
        }

        val connection = remember(request, containerHeightPx, topOffset, restingOffset, dismissLimit) {
            object : NestedScrollConnection {
                private var gestureStartOffset = Float.NaN

                private fun beginGesture() {
                    if (gestureStartOffset.isNaN()) {
                        gestureStartOffset = offset.value
                    }
                }

                private fun collapseLimit(): Float =
                    if (gestureStartOffset >= restingOffset - 1f) dismissLimit else restingOffset

                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (source != NestedScrollSource.UserInput) return Offset.Zero
                    beginGesture()
                    val delta = available.y
                    return if (delta < 0 && offset.value > topOffset) {
                        Offset(0f, dispatchRawDelta(delta))
                    } else {
                        Offset.Zero
                    }
                }

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource
                ): Offset {
                    if (source != NestedScrollSource.UserInput) return Offset.Zero
                    beginGesture()
                    val delta = available.y
                    return if (delta > 0) {
                        Offset(0f, dispatchRawDelta(delta, collapseLimit()))
                    } else {
                        Offset.Zero
                    }
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    return if (available.y < 0 && offset.value > topOffset) {
                        settle(available.y)
                        available
                    } else {
                        Velocity.Zero
                    }
                }

                override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                    val limit = collapseLimit()
                    gestureStartOffset = Float.NaN
                    settle(available.y, limit)
                    return available
                }
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val reveal = ((containerHeightPx - offset.value) / smallestHeight)
                        .coerceIn(0f, 1f)
                    drawRect(Color.Black.copy(alpha = 0.20f * reveal))
                }
                .scrimGestures(request) {
                    if (request.interactiveDismiss) {
                        onDismissRequest()
                    }
                }
        )

        Box(
            Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .height(with(density) { largestHeight.toDp() })
                .offset { IntOffset(0, offset.value.roundToInt()) }
                .clip(LuxShapes.topCorners(request.cornerRadius))
                .background(colors.systemBackground)
                .nestedScroll(connection)
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta -> dispatchRawDelta(delta) },
                    onDragStopped = { velocity -> settle(velocity) }
                )
        ) {
            request.content()
            if (request.showDragIndicator) {
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

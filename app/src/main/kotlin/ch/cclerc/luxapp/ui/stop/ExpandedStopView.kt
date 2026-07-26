package ch.cclerc.luxapp.ui.stop

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import ch.cclerc.luxapp.domain.TripOption
import ch.cclerc.luxapp.ui.anim.IosTransitions
import ch.cclerc.luxapp.ui.stop.expanded.BoardMode
import ch.cclerc.luxapp.ui.stop.expanded.ExpandedStopHeaderView
import ch.cclerc.luxapp.ui.stop.expanded.RouteGroupsView
import ch.cclerc.luxapp.ui.stop.expanded.StopContentEmptyView
import ch.cclerc.luxapp.ui.stop.expanded.StopContentLoadingView
import ch.cclerc.luxapp.ui.theme.LuxSprings
import ch.cclerc.luxapp.viewmodel.StopViewModel
import ch.cclerc.luxcom.model.SearchResult
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class StopContentState { Loading, Empty, Content }

@Composable
fun ExpandedStopView(
    stop: SearchResult,
    fromStops: Boolean,
    maxGroupsToShow: Int,
    onOpenTrip: (String, List<TripOption>) -> Unit,
    modifier: Modifier = Modifier,
    time: Instant? = null
) {
    val scope = rememberCoroutineScope()

    val viewModel = remember(stop.id, fromStops, maxGroupsToShow, time) {
        StopViewModel(
            stop = stop,
            fromStops = fromStops,
            maxGroupsToShow = maxGroupsToShow,
            initialTime = time
        )
    }

    var selectedDate by remember(stop.id) { mutableStateOf(time ?: Instant.now()) }
    var boardMode by remember(stop.id) { mutableStateOf(BoardMode.Grouped) }
    var contentTransitionId by remember(stop.id) { mutableStateOf(0) }
    var animateIn by remember(stop.id) { mutableStateOf(false) }
    var isChangingContent by remember(stop.id) { mutableStateOf(false) }
    var showContent by remember(stop.id) { mutableStateOf(true) }
    var isRefreshing by remember(stop.id) { mutableStateOf(false) }

    DisposableEffect(viewModel) {
        viewModel.startMonitoring()
        onDispose { viewModel.stopMonitoring() }
    }

    LaunchedEffect(viewModel) {
        delay(50)
        animateIn = true
    }

    val contentAlpha by animateFloatAsState(
        targetValue = if (showContent) 1f else 0f,
        animationSpec = LuxSprings.springFor(0.5, 0.8),
        label = "stop-content-alpha"
    )

    fun contentTransition(task: suspend () -> Unit) {
        if (isChangingContent) return
        isChangingContent = true
        showContent = false

        scope.launch {
            delay(400)
            contentTransitionId += 1
            task()
            showContent = true
            delay(500)
            isChangingContent = false
        }
    }

    Column(modifier.fillMaxSize()) {
        ExpandedStopHeaderView(
            animateIn = animateIn,
            selectedDate = selectedDate,
            boardMode = boardMode,
            onDateSelected = { instant ->
                selectedDate = instant
                contentTransition {
                    viewModel.refreshDepartures(forTime = instant, showLoading = true)
                }
            },
            onModeChanged = { mode -> boardMode = mode }
        )

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .graphicsLayer { alpha = contentAlpha }
        ) {
            key(contentTransitionId) {
                val contentState = when {
                    viewModel.isLoading -> StopContentState.Loading
                    viewModel.routeGroups.isEmpty() -> StopContentState.Empty
                    else -> StopContentState.Content
                }

                AnimatedContent(
                    targetState = contentState,
                    transitionSpec = {
                        IosTransitions.moveEdgeBottomEnter(LuxSprings.springFor(0.5, 0.8)) togetherWith
                            IosTransitions.moveEdgeBottomExit(LuxSprings.springFor(0.5, 0.8))
                    },
                    label = "stop-content"
                ) { state ->
                    when (state) {
                        StopContentState.Loading -> StopContentLoadingView(
                            animateIn = animateIn,
                            errorMessage = viewModel.errorMessage,
                            modifier = Modifier.fillMaxSize()
                        )

                        StopContentState.Empty -> StopContentEmptyView(
                            animateIn = animateIn,
                            errorMessage = viewModel.errorMessage,
                            modifier = Modifier.fillMaxSize()
                        )

                        StopContentState.Content -> RouteGroupsView(
                            viewModel = viewModel,
                            animateIn = animateIn,
                            maxGroupsToShow = maxGroupsToShow,
                            boardMode = boardMode,
                            isRefreshing = isRefreshing,
                            onRefresh = {
                                if (!isRefreshing) {
                                    isRefreshing = true
                                    scope.launch {
                                        val now = Instant.now()
                                        selectedDate = now
                                        viewModel.refreshDepartures(forTime = now, showLoading = false)
                                        isRefreshing = false
                                    }
                                }
                            },
                            onOpenTrip = onOpenTrip,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

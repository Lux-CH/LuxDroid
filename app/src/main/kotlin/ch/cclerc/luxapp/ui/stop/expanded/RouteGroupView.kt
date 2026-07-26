package ch.cclerc.luxapp.ui.stop.expanded

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import ch.cclerc.luxapp.domain.GroupedStopTime
import ch.cclerc.luxapp.domain.TripOption
import ch.cclerc.luxapp.ui.components.PaginationDots
import ch.cclerc.luxapp.ui.theme.LuxSprings
import ch.cclerc.luxapp.ui.theme.LuxTheme
import kotlin.math.max

private val ROUTE_PAGE_HEIGHT = 175.dp

@Composable
fun RouteGroupView(
    routeName: String,
    groups: List<GroupedStopTime>,
    currentPage: Int,
    onPageChanged: (String, Int) -> Unit,
    animateIn: Boolean,
    playEntrance: Boolean,
    entranceTracker: EntranceTracker,
    isLastRoute: Boolean,
    onOpenTrip: (String, List<TripOption>) -> Unit,
    onSelectLine: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LuxTheme.colors
    val pagerState = rememberPagerState(
        initialPage = currentPage.coerceIn(0, max(0, groups.size - 1)),
        pageCount = { groups.size }
    )
    val flingBehavior = PagerDefaults.flingBehavior(
        state = pagerState,
        snapAnimationSpec = LuxSprings.springFor(0.5, 0.75)
    )

    LaunchedEffect(pagerState, groups.size) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            onPageChanged(routeName, page)
        }
    }

    val dividerProgress by animateFloatAsState(
        targetValue = if (animateIn || !playEntrance) 1f else 0f,
        animationSpec = tween(durationMillis = 500, delayMillis = 300),
        label = "route-divider"
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(ROUTE_PAGE_HEIGHT),
            contentAlignment = Alignment.BottomCenter
        ) {
            HorizontalPager(
                state = pagerState,
                flingBehavior = flingBehavior,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ROUTE_PAGE_HEIGHT)
            ) { page ->
                val group = groups.getOrNull(page)
                if (group != null) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        ExpandedGroupView(
                            group = group,
                            animateIn = animateIn,
                            entranceTracker = entranceTracker,
                            onOpenTrip = onOpenTrip,
                            onSelectLine = onSelectLine
                        )
                    }
                }
            }

            PaginationDots(
                groupsCount = groups.size,
                currentPage = pagerState.currentPage,
                animateIn = animateIn || !playEntrance
            )
        }

        if (!isLastRoute) {
            HorizontalDivider(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0f, 0.5f)
                        scaleX = dividerProgress
                    },
                thickness = 0.5.dp,
                color = colors.separator
            )
        }
    }
}

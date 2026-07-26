package ch.cclerc.luxapp.ui.stop.expanded

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ch.cclerc.luxapp.domain.TripOption
import ch.cclerc.luxapp.ui.anim.staggeredEntrance
import ch.cclerc.luxapp.ui.theme.LuxSprings
import ch.cclerc.luxapp.viewmodel.StopViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteGroupsView(
    viewModel: StopViewModel,
    animateIn: Boolean,
    maxGroupsToShow: Int,
    boardMode: BoardMode,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onOpenTrip: (String, List<TripOption>) -> Unit,
    modifier: Modifier = Modifier
) {
    val pullState = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = pullState,
        modifier = modifier.fillMaxSize()
    ) {
        if (boardMode == BoardMode.Grouped) {
            val shownRoutes = viewModel.routeNames.take(maxGroupsToShow)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 10.dp)
            ) {
                itemsIndexed(shownRoutes) { index, routeName ->
                    val groups = viewModel.routeGroups[routeName]
                    if (!groups.isNullOrEmpty()) {
                        RouteGroupView(
                            routeName = routeName,
                            groups = groups,
                            currentPage = viewModel.currentPages[routeName] ?: 0,
                            onPageChanged = { name, page -> viewModel.currentPages[name] = page },
                            animateIn = animateIn,
                            isLastRoute = routeName == shownRoutes.lastOrNull(),
                            onOpenTrip = onOpenTrip,
                            onSelectLine = { name -> viewModel.userSelectedLine(name) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .staggeredEntrance(
                                    index = index,
                                    visible = animateIn,
                                    delayPerItemMs = 100,
                                    baseDelayMs = 200,
                                    fromOffsetY = 20.dp,
                                    spec = LuxSprings.springFor(0.6, 0.75)
                                )
                        )
                    }
                }
            }
        } else {
            val departures = viewModel.sortStopTimes(viewModel.stopTimes?.stopTimes ?: emptyList())

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 10.dp)
            ) {
                items(departures, key = { "${it.tripId}-${it.place.scheduledDeparture ?: it.place.departure ?: it.place.arrival}" }) { stopTime ->
                    ExpandedDepartureRowView(
                        stopTime = stopTime,
                        onOpenTrip = { tripId -> onOpenTrip(tripId, emptyList()) },
                        onSelectLine = { name -> viewModel.userSelectedLine(name) }
                    )
                }
            }
        }
    }
}

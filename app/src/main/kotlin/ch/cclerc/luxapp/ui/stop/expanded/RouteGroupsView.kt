package ch.cclerc.luxapp.ui.stop.expanded

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ch.cclerc.luxapp.domain.TripOption
import ch.cclerc.luxapp.ui.anim.staggeredEntrance
import ch.cclerc.luxapp.ui.theme.LuxSprings
import ch.cclerc.luxapp.ui.theme.LuxTheme
import ch.cclerc.luxapp.viewmodel.StopViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteGroupsView(
    viewModel: StopViewModel,
    animateIn: Boolean,
    entranceTracker: EntranceTracker,
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
                        val playEntrance = rememberEntrancePlayback(entranceTracker, "route|$routeName")
                        RouteGroupView(
                            routeName = routeName,
                            groups = groups,
                            currentPage = viewModel.currentPages[routeName] ?: 0,
                            onPageChanged = { name, page -> viewModel.currentPages[name] = page },
                            animateIn = animateIn,
                            playEntrance = playEntrance,
                            entranceTracker = entranceTracker,
                            isLastRoute = routeName == shownRoutes.lastOrNull(),
                            onOpenTrip = onOpenTrip,
                            onSelectLine = { name -> viewModel.userSelectedLine(name) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (playEntrance) {
                                        Modifier.staggeredEntrance(
                                            index = index,
                                            visible = animateIn,
                                            delayPerItemMs = 100,
                                            baseDelayMs = 200,
                                            fromOffsetY = 20.dp,
                                            spec = LuxSprings.springFor(0.6, 0.75)
                                        )
                                    } else {
                                        Modifier
                                    }
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
                itemsIndexed(
                    departures,
                    key = { _, it -> "${it.tripId}-${it.place.scheduledDeparture ?: it.place.departure ?: it.place.arrival}" }
                ) { index, stopTime ->
                    ExpandedDepartureRowView(
                        stopTime = stopTime,
                        onOpenTrip = { tripId -> onOpenTrip(tripId, emptyList()) },
                        onSelectLine = { name -> viewModel.userSelectedLine(name) }
                    )

                    if (index < departures.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 16.dp),
                            thickness = 0.5.dp,
                            color = LuxTheme.colors.separator
                        )
                    }
                }
            }
        }
    }
}

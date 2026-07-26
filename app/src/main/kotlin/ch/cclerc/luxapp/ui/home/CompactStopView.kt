package ch.cclerc.luxapp.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.max
import androidx.compose.ui.unit.sp
import ch.cclerc.luxapp.core.SFSymbol
import ch.cclerc.luxapp.data.Settings
import ch.cclerc.luxapp.domain.TripOption
import ch.cclerc.luxapp.ui.anim.scaleClickable
import ch.cclerc.luxapp.ui.components.MaskedImage
import ch.cclerc.luxapp.ui.components.PaginationDots
import ch.cclerc.luxapp.ui.components.isDarkColor
import ch.cclerc.luxapp.ui.components.lightenColor
import ch.cclerc.luxapp.ui.theme.InterFontFamily
import ch.cclerc.luxapp.ui.theme.LuxTheme
import ch.cclerc.luxapp.viewmodel.StopViewModel
import ch.cclerc.luxcom.colors.LineColors
import ch.cclerc.luxcom.model.SearchResult
import java.time.Instant

private val DISTANT_FUTURE: Instant = Instant.ofEpochSecond(64_092_211_200L)

@Composable
fun CompactStopView(
    stop: SearchResult,
    maxGroupsToShow: Int,
    dontShowLastDivider: Boolean,
    isLastStopOverall: Boolean,
    onOpenStop: (SearchResult) -> Unit,
    onOpenTrip: (String, List<TripOption>) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LuxTheme.colors
    val viewModel = remember(stop.id) {
        StopViewModel(stop = stop, fromStops = false, maxGroupsToShow = maxGroupsToShow)
    }

    DisposableEffect(viewModel) {
        viewModel.startMonitoring()
        onDispose { viewModel.stopMonitoring() }
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .scaleClickable { onOpenStop(stop) }
        ) {
            MaskedImage(stopIdentifier = stop.id, modifier = Modifier.matchParentSize())
            Column(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 25.dp)
                        .padding(top = 20.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SFSymbol(
                        name = if (stop.servesRail) "train.side.front.car" else "signpost.right",
                        size = 17.sp,
                        color = colors.secondaryLabel,
                        modifier = if (stop.servesRail) {
                            Modifier.graphicsLayer { scaleX = -1f }
                        } else {
                            Modifier
                        }
                    )

                    Text(
                        text = stop.name,
                        fontFamily = InterFontFamily,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.label,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.weight(1f)
                    )

                    SFSymbol(
                        name = "chevron.forward",
                        size = 12.sp,
                        color = colors.tertiaryLabel
                    )
                }

                HorizontalDivider(thickness = 0.5.dp, color = colors.separator)
            }
        }

        val isLoading = viewModel.isLoading
        val routeGroups = viewModel.routeGroups
        val errorMessage = viewModel.errorMessage

        if (isLoading && routeGroups.isEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.height(18.dp),
                    strokeWidth = 2.dp,
                    color = colors.secondaryLabel
                )
                Text(
                    text = "Chargement des départs...",
                    style = LuxTheme.type.footnote,
                    color = colors.secondaryLabel
                )
            }
        } else if (routeGroups.isEmpty()) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Aucun départ à venir.",
                    style = LuxTheme.type.body,
                    color = colors.systemGray,
                    modifier = Modifier.padding(16.dp)
                )
                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        style = LuxTheme.type.body,
                        color = colors.systemRed,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            val ordered = orderedRouteNames(viewModel)
            val shownRouteNames = ordered.take(maxGroupsToShow)

            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                shownRouteNames.forEach { routeName ->
                    val groups = routeGroups[routeName]
                    if (groups != null && groups.isNotEmpty()) {
                        RouteGroupView(
                            viewModel = viewModel,
                            routeName = routeName,
                            groups = groups,
                            isLastRoute = dontShowLastDivider && routeName == shownRouteNames.lastOrNull(),
                            isLastStopOverall = isLastStopOverall,
                            onOpenTrip = onOpenTrip
                        )
                    }
                }
            }
        }

        if (errorMessage != null && routeGroups.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SFSymbol("wifi.exclamationmark", 11.sp, colors.secondaryLabel)
                Text(
                    text = "Horaires possiblement obsolètes",
                    style = LuxTheme.type.caption2,
                    color = colors.secondaryLabel
                )
            }
        }
    }
}

private fun orderedRouteNames(viewModel: StopViewModel): List<String> {
    val names = viewModel.routeNames
    if (!viewModel.stop.servesRail) return names

    val rail = names.filter { name ->
        viewModel.routeGroups[name]?.firstOrNull()?.stopTimes?.firstOrNull()?.mode?.isRail == true
    }
    val topRail = rail.minByOrNull { name ->
        viewModel.routeGroups[name]
            ?.mapNotNull { it.stopTimes.firstOrNull() }
            ?.mapNotNull { it.place.departure ?: it.place.arrival }
            ?.minOrNull() ?: DISTANT_FUTURE
    } ?: return names

    return listOf(topRail) + names.filter { it != topRail }
}

@Composable
private fun RouteGroupView(
    viewModel: StopViewModel,
    routeName: String,
    groups: List<ch.cclerc.luxapp.domain.GroupedStopTime>,
    isLastRoute: Boolean,
    isLastStopOverall: Boolean,
    onOpenTrip: (String, List<TripOption>) -> Unit
) {
    val colors = LuxTheme.colors
    val baseColor = LineColors.color(groups.firstOrNull()?.routeShortName ?: "")
        ?.let { Color(it) } ?: Color(0xFFEA0706)
    val lineColor = if (isDarkColor(baseColor)) lightenColor(baseColor) else baseColor

    val overhangDp = if (isLastRoute) (if (isLastStopOverall) 4.dp else 55.dp) else 0.dp
    val overhangPx = with(LocalDensity.current) { overhangDp.toPx() }

    val pagerState = rememberPagerState(
        initialPage = (viewModel.currentPages[routeName] ?: 0).coerceIn(0, max(0, groups.size - 1)),
        pageCount = { groups.size }
    )

    LaunchedEffect(pagerState, groups.size) {
        androidx.compose.runtime.snapshotFlow { pagerState.currentPage }.collect { page ->
            viewModel.currentPages[routeName] = page
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            lineColor.copy(alpha = 0.05f),
                            lineColor.copy(alpha = 0.01f)
                        ),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height + overhangPx)
                    ),
                    topLeft = Offset.Zero,
                    size = Size(size.width, size.height + overhangPx)
                )
            },
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(70.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(70.dp)
            ) { page ->
                val group = groups.getOrNull(page)
                if (group != null && group.stopTimes.isNotEmpty()) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(70.dp)
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        IncomingBusView(
                            group = group,
                            onOpenTrip = onOpenTrip,
                            onSelectLine = { name -> viewModel.userSelectedLine(name) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            PaginationDots(
                groupsCount = groups.size,
                currentPage = pagerState.currentPage,
                animateIn = true
            )
        }

        if (!isLastRoute) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                thickness = 0.5.dp,
                color = colors.separator
            )
        }
    }
}

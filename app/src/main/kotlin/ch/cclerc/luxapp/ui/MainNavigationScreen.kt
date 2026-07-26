package ch.cclerc.luxapp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import ch.cclerc.luxapp.core.HapticFeedback
import ch.cclerc.luxapp.core.LocationService
import ch.cclerc.luxapp.core.SFSymbol
import ch.cclerc.luxapp.data.Progress
import ch.cclerc.luxapp.data.SavedItineraryRecord
import ch.cclerc.luxapp.data.SavedItineraryStorage
import ch.cclerc.luxapp.data.Settings
import ch.cclerc.luxapp.domain.shortcut.ShortcutManager
import ch.cclerc.luxapp.domain.shortcut.UserShortcut
import ch.cclerc.luxapp.ui.anim.IosTransitions
import ch.cclerc.luxapp.ui.anim.scaleClickable
import ch.cclerc.luxapp.ui.components.AddShortcutsButton
import ch.cclerc.luxapp.ui.components.AnimatedSearchBar
import ch.cclerc.luxapp.ui.components.CustomTabBar
import ch.cclerc.luxapp.ui.components.LuxTab
import ch.cclerc.luxapp.ui.components.SavedItineraryShortcutButton
import ch.cclerc.luxapp.ui.components.ShortcutButton
import ch.cclerc.luxapp.ui.navigation.LocalSheetController
import ch.cclerc.luxapp.ui.navigation.LuxSheetRequest
import ch.cclerc.luxapp.ui.navigation.ViewMode
import ch.cclerc.luxapp.ui.theme.LuxShapes
import ch.cclerc.luxapp.ui.theme.LuxSprings
import ch.cclerc.luxapp.ui.theme.LuxTheme
import ch.cclerc.luxapp.ui.theme.iosShadow
import ch.cclerc.luxcom.model.SearchResult
import ch.cclerc.luxcom.model.trip.Itinerary
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal const val SEARCH_DRAG_RESISTANCE = 0.4f

internal fun Modifier.stretchedShift(shiftPx: Float): Modifier = layout { measurable, constraints ->
    val extra = (-shiftPx).roundToInt().coerceAtLeast(0)
    if (extra == 0 || !constraints.hasBoundedHeight) {
        val placeable = measurable.measure(constraints)
        return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
    val stretched = constraints.maxHeight + extra
    val placeable = measurable.measure(
        constraints.copy(minHeight = stretched, maxHeight = stretched)
    )
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.place(0, -extra)
    }
}

@Composable
fun MainNavigationScreen(
    homeContent: @Composable () -> Unit = { HomeContentPlaceholder() },
    stopsContent: @Composable (String) -> Unit = { StopsContentPlaceholder() },
    searchHeader: @Composable (onBack: () -> Unit) -> Unit = { SearchHeaderPlaceholder() },
    searchContent: @Composable () -> Unit = { SearchContentPlaceholder() },
    settingsSheet: @Composable () -> Unit = { SettingsSheetPlaceholder() },
    shortcutsSheet: @Composable () -> Unit = { ShortcutsSheetPlaceholder() },
    onSearchEnter: () -> Unit = {},
    onSearchReset: () -> Unit = {},
    onSearchPrefillDestination: (SearchResult) -> Unit = {},
    onOpenSavedItinerary: (Itinerary) -> Unit = {}
) {
    val colors = LuxTheme.colors
    val isDark = LuxTheme.isDark
    val scope = rememberCoroutineScope()
    val sheets = LocalSheetController.current
    val statusInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    var viewMode by remember { mutableStateOf(ViewMode.Home) }
    var searchBarOffset by remember { mutableStateOf(0.dp) }
    var searchDragOffset by remember { mutableStateOf(0f) }
    var isSearchTransitioning by remember { mutableStateOf(false) }
    var isAnimatingToSearch by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var stopsQuery by remember { mutableStateOf("") }
    var searchGeneration by remember { mutableStateOf(0) }

    val shortcutManager = ShortcutManager.shared
    val visibleShortcuts by shortcutManager.visibleShortcuts.collectAsState()
    var upcomingSavedItinerary by remember { mutableStateOf<SavedItineraryRecord?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current

    suspend fun refreshUpcomingSavedItinerary() {
        upcomingSavedItinerary = SavedItineraryStorage.loadUpcomingItineraryAsync()
    }

    LaunchedEffect(Unit) { refreshUpcomingSavedItinerary() }

    LaunchedEffect(Unit) {
        SavedItineraryStorage.changes.collect { refreshUpcomingSavedItinerary() }
    }

    LaunchedEffect(viewMode) {
        if (viewMode != ViewMode.Home) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            refreshUpcomingSavedItinerary()
            while (true) {
                delay(30_000)
                refreshUpcomingSavedItinerary()
            }
        }
    }

    LaunchedEffect(Unit) {
        var lastLocationUpdate = 0L
        LocationService.location.collect { location ->
            val now = System.currentTimeMillis()
            if (now - lastLocationUpdate < 2_500L) return@collect
            lastLocationUpdate = now
            if (Settings.useTimeBasedRelevance) shortcutManager.refreshFor(location)
        }
    }

    LaunchedEffect(Settings.useTimeBasedRelevance) {
        shortcutManager.refreshFor(LocationService.location.value)
    }

    val compactStops = viewMode == ViewMode.Stops && Settings.reduceSpacerBtwnStopContent
    val targetHeaderHeight = when (viewMode) {
        ViewMode.Home -> 215.dp
        ViewMode.Search -> 225.dp
        ViewMode.Stops -> max(135.dp, statusInset + 75.dp)
    }
    val headerHeight by animateDpAsState(targetHeaderHeight, LuxSprings.springFor<Dp>(0.4, 1.0), label = "headerHeight")
    val headerCornerRaw by animateDpAsState(if (compactStops) 0.dp else LuxShapes.r40, LuxSprings.springFor<Dp>(0.4, 1.0), label = "headerCorner")
    val headerCorner = headerCornerRaw.coerceIn(0.dp, LuxShapes.r40)
    val animatedSearchOffset by animateDpAsState(searchBarOffset, LuxSprings.springFor<Dp>(0.4, 0.85), label = "searchBarOffset")

    val headerFill by animateColorAsState(
        if (isDark) colors.secondarySystemBackground.copy(alpha = 0.7f) else Color.White,
        label = "headerFill"
    )
    val bgBrush = Brush.verticalGradient(
        if (isDark) listOf(colors.systemBackground, colors.systemBackground.copy(alpha = 0.8f))
        else listOf(colors.secondarySystemBackground, Color.White)
    )

    fun switchToStopsMode() {
        HapticFeedback.softImpact()
        Progress.numOfTimesStopViewWasOpened += 1
        viewMode = ViewMode.Stops
    }

    fun switchToHomeMode() {
        HapticFeedback.softImpact()
        viewMode = ViewMode.Home
    }

    fun transitionToSearchMode() {
        if (isSearchTransitioning || viewMode == ViewMode.Search) return
        isSearchTransitioning = true
        Progress.numOfTimesTripViewWasOpened += 1
        scope.launch {
            isAnimatingToSearch = true
            searchBarOffset = (-20).dp
            delay(25)
            viewMode = ViewMode.Search
            delay(75)
            HapticFeedback.softImpact()
            onSearchEnter()
            delay(50)
            searchBarOffset = 0.dp
            isAnimatingToSearch = false
            isSearchTransitioning = false
            HapticFeedback.lightImpact()
        }
    }

    fun transitionToSearchModeWithShortcut(shortcut: UserShortcut) {
        if (isSearchTransitioning || viewMode == ViewMode.Search) return
        isSearchTransitioning = true
        val result = shortcut.toSearchResult()
        HapticFeedback.lightImpact()
        scope.launch {
            isAnimatingToSearch = true
            searchBarOffset = (-15).dp
            delay(25)
            viewMode = ViewMode.Search
            delay(75)
            HapticFeedback.softImpact()
            onSearchEnter()
            delay(50)
            onSearchPrefillDestination(result)
            searchBarOffset = 0.dp
            isAnimatingToSearch = false
            isSearchTransitioning = false
        }
    }

    fun exitSearchMode() {
        if (isSearchTransitioning || viewMode != ViewMode.Search) return
        isSearchTransitioning = true
        scope.launch {
            searchBarOffset = 15.dp
            delay(100)
            viewMode = ViewMode.Home
            searchDragOffset = 0f
            delay(150)
            searchBarOffset = 0.dp
            delay(150)
            searchGeneration += 1
            onSearchReset()
            searchText = ""
            isSearchTransitioning = false
        }
    }

    BackHandler(enabled = viewMode != ViewMode.Home) {
        when (viewMode) {
            ViewMode.Search -> exitSearchMode()
            ViewMode.Stops -> switchToHomeMode()
            else -> Unit
        }
    }

    val density = LocalDensity.current
    val overdragThresholdPx = with(density) { 150.dp.toPx() }
    val overdragHorizontalGuardPx = with(density) { 100.dp.toPx() }
    val onStopsOverdrag = rememberUpdatedState<() -> Unit> { switchToHomeMode() }
    val stopsOverdragConnection = remember(overdragThresholdPx, overdragHorizontalGuardPx) {
        object : NestedScrollConnection {
            private var accumY = 0f
            private var accumX = 0f
            private var fired = false

            private fun reset() {
                accumY = 0f
                accumX = 0f
                fired = false
            }

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y < 0f) reset()
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                if (consumed.y != 0f || available.y <= 0f) {
                    reset()
                    return Offset.Zero
                }
                accumX += consumed.x + available.x
                accumY += available.y
                if (!fired &&
                    accumY > overdragThresholdPx &&
                    abs(accumX) < overdragHorizontalGuardPx
                ) {
                    fired = true
                    onStopsOverdrag.value()
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                reset()
                return Velocity.Zero
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(bgBrush)
            .pointerInput(viewMode) {
                var dx = 0f
                var dy = 0f
                detectDragGestures(
                    onDragStart = { dx = 0f; dy = 0f },
                    onDrag = { change, amount ->
                        change.consume()
                        dx += amount.x
                        dy += amount.y
                        if (viewMode == ViewMode.Search && !isSearchTransitioning) {
                            searchDragOffset = (searchDragOffset + amount.y).coerceAtMost(0f)
                        }
                    },
                    onDragEnd = {
                        val dxDp = with(density) { dx.toDp().value }
                        val dyDp = with(density) { dy.toDp().value }
                        if (viewMode == ViewMode.Search) {
                            if (dyDp < -50) exitSearchMode() else searchDragOffset = 0f
                        } else if (abs(dxDp) < 100) {
                            when {
                                viewMode == ViewMode.Home && dyDp < -50 -> switchToStopsMode()
                                viewMode == ViewMode.Home && dyDp > 100 -> transitionToSearchMode()
                                viewMode == ViewMode.Stops && dyDp > 150 -> switchToHomeMode()
                            }
                        }
                    }
                )
            }
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(headerHeight)
                    .let { if (compactStops) it else it.iosShadow(Color.Black.copy(alpha = 0.05f), 10.dp, 5.dp, LuxShapes.bottomCorners(headerCorner)) }
                    .clip(LuxShapes.bottomCorners(headerCorner))
                    .background(headerFill)
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(top = statusInset)
                ) {
                    AnimatedContent(
                        targetState = viewMode == ViewMode.Search,
                        transitionSpec = {
                            IosTransitions.searchModeEnter(LuxSprings.springFor(0.45, 0.82)) togetherWith
                                IosTransitions.searchModeExit(LuxSprings.springFor(0.45, 0.82))
                        },
                        label = "headerContent"
                    ) { inSearch ->
                        if (inSearch) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .padding(top = (max(statusInset - 8.dp, 48.dp) - statusInset).coerceAtLeast(0.dp))
                                    .offset(y = animatedSearchOffset)
                            ) {
                                searchHeader { exitSearchMode() }
                            }
                        } else {
                            Column(
                                Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 20.dp),
                                verticalArrangement = Arrangement.Bottom
                            ) {
                                if (viewMode == ViewMode.Home) {
                                    val openShortcutsSheet = {
                                        sheets.present(
                                            LuxSheetRequest(cornerRadius = LuxShapes.r36) { shortcutsSheet() }
                                        )
                                    }
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val savedItinerary = upcomingSavedItinerary
                                        if (savedItinerary == null && visibleShortcuts.isEmpty()) {
                                            AddShortcutsButton(modifier = Modifier.weight(1f)) {
                                                openShortcutsSheet()
                                            }
                                        } else {
                                            if (savedItinerary != null) {
                                                SavedItineraryShortcutButton(
                                                    timeLabel = itineraryTimeLabel(savedItinerary.itinerary),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    onOpenSavedItinerary(savedItinerary.itinerary)
                                                }
                                            } else {
                                                val first = visibleShortcuts.first()
                                                ShortcutButton(
                                                    symbol = first.symbol,
                                                    name = first.name,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    transitionToSearchModeWithShortcut(first)
                                                }
                                            }

                                            val secondIndex = if (savedItinerary == null) 1 else 0
                                            val second = visibleShortcuts.getOrNull(secondIndex)
                                            if (second != null) {
                                                ShortcutButton(
                                                    symbol = second.symbol,
                                                    name = second.name,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    transitionToSearchModeWithShortcut(second)
                                                }
                                            } else {
                                                ShortcutButton(
                                                    symbol = "plus",
                                                    name = "Ajouter",
                                                    isPlaceholder = true,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    openShortcutsSheet()
                                                }
                                            }
                                        }
                                        GearButton {
                                            HapticFeedback.softImpact()
                                            sheets.present(
                                                LuxSheetRequest(cornerRadius = LuxShapes.r36) { settingsSheet() }
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(12.dp))
                                }
                                AnimatedSearchBar(
                                    searchText = if (viewMode == ViewMode.Stops) stopsQuery else searchText,
                                    onSearchTextChange = { if (viewMode == ViewMode.Stops) stopsQuery = it },
                                    placeholderText = if (viewMode == ViewMode.Stops) "Rechercher un arrêt..." else "Où allez-vous ?",
                                    onSearch = {},
                                    onClear = { stopsQuery = "" },
                                    isTextFieldDisabled = viewMode == ViewMode.Home,
                                    onTapWhenDisabled = { transitionToSearchMode() },
                                    modifier = Modifier
                                        .offset(y = animatedSearchOffset)
                                        .padding(bottom = 15.dp)
                                )
                            }
                        }
                    }
                }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                AnimatedContent(
                    targetState = viewMode,
                    transitionSpec = {
                        if (initialState == ViewMode.Home && targetState == ViewMode.Stops) {
                            androidx.compose.animation.fadeIn(LuxSprings.springFor(0.5, 1.0)) togetherWith
                                IosTransitions.contentSwapExit(LuxSprings.springFor(0.5, 1.0))
                        } else {
                            IosTransitions.contentSwapEnter(LuxSprings.springFor(0.5, 0.85)) togetherWith
                                IosTransitions.contentSwapExit(LuxSprings.springFor(0.5, 0.85))
                        }
                    },
                    label = "modeContent"
                ) { mode ->
                    val contentFill = when {
                        mode == ViewMode.Search -> Color.Transparent
                        isDark -> colors.secondarySystemBackground.copy(alpha = 0.7f)
                        else -> Color.White
                    }
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(
                                top = when {
                                    compactStops -> 0.dp
                                    mode == ViewMode.Search -> 24.dp
                                    mode == ViewMode.Home -> 22.dp
                                    else -> 18.dp
                                }
                            )
                            .let {
                                if (mode == ViewMode.Search) it
                                else it.iosShadow(Color.Black.copy(alpha = 0.05f), 8.dp, (-4).dp, LuxShapes.topCorners(LuxShapes.r38))
                            }
                            .clip(LuxShapes.topCorners(if (compactStops) 0.dp else LuxShapes.r38))
                            .background(contentFill)
                    ) {
                        when (mode) {
                            ViewMode.Home -> homeContent()
                            ViewMode.Stops -> Box(
                                Modifier
                                    .fillMaxSize()
                                    .nestedScroll(stopsOverdragConnection)
                            ) {
                                stopsContent(stopsQuery)
                            }

                            ViewMode.Search -> Box(
                                Modifier
                                    .fillMaxSize()
                                    .stretchedShift(searchDragOffset * SEARCH_DRAG_RESISTANCE)
                            ) {
                                androidx.compose.runtime.key(searchGeneration) { searchContent() }
                            }
                        }
                    }
                }
            }
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = viewMode != ViewMode.Search,
            enter = IosTransitions.moveEdgeBottomEnter(LuxSprings.springFor(0.4, 0.85)),
            exit = IosTransitions.moveEdgeBottomExit(LuxSprings.springFor(0.4, 0.85)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            CustomTabBar(
                selectedTab = if (viewMode == ViewMode.Stops) LuxTab.Stops else LuxTab.Home,
                onModeChange = { tab ->
                    when (tab) {
                        LuxTab.Home -> if (viewMode != ViewMode.Home) switchToHomeMode()
                        LuxTab.Stops -> if (viewMode != ViewMode.Stops) switchToStopsMode()
                    }
                }
            )
        }
    }
}

private val itineraryTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

private fun itineraryTimeLabel(itinerary: Itinerary): String {
    val departure = itineraryTimeFormatter.format(itinerary.startTime)
    val arrival = itineraryTimeFormatter.format(itinerary.endTime)
    return "$departure → $arrival"
}

@Composable
private fun GearButton(onClick: () -> Unit) {
    val colors = LuxTheme.colors
    Box(
        Modifier
            .size(52.5.dp)
            .clip(CircleShape)
            .background(colors.secondarySystemFill.copy(alpha = colors.secondarySystemFill.alpha * 0.5f))
            .scaleClickable(haptic = false, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        SFSymbol("gearshape", 20.sp, LuxTheme.accent)
    }
}

@Composable
fun HomeContentPlaceholder() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Arrêts à proximité", color = LuxTheme.colors.secondaryLabel, fontSize = 15.sp)
    }
}

@Composable
fun StopsContentPlaceholder() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Liste des arrêts", color = LuxTheme.colors.secondaryLabel, fontSize = 15.sp)
    }
}

@Composable
fun SearchHeaderPlaceholder() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Recherche d'itinéraire", color = LuxTheme.colors.label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SearchContentPlaceholder() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Résultats", color = LuxTheme.colors.secondaryLabel, fontSize = 15.sp)
    }
}

@Composable
fun SettingsSheetPlaceholder() {
    Box(Modifier.fillMaxSize().padding(top = 40.dp), contentAlignment = Alignment.TopCenter) {
        Text("Paramètres", color = LuxTheme.colors.label, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun ShortcutsSheetPlaceholder() {
    Box(Modifier.fillMaxSize().padding(top = 40.dp), contentAlignment = Alignment.TopCenter) {
        Text("Raccourcis", color = LuxTheme.colors.label, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
    }
}

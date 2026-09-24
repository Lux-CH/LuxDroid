package ch.cclerc.luxapp.ui.trips

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import ch.cclerc.luxapp.ui.theme.LuxSprings
import ch.cclerc.luxapp.viewmodel.RoutePreset
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.cclerc.luxapp.core.HapticFeedback
import ch.cclerc.luxapp.core.LocationService
import ch.cclerc.luxapp.core.SFSymbol
import ch.cclerc.luxapp.data.Progress
import ch.cclerc.luxapp.data.Settings
import ch.cclerc.luxapp.domain.shortcut.ShortcutManager
import ch.cclerc.luxapp.ui.anim.pulse
import ch.cclerc.luxapp.ui.anim.scaleClickable
import ch.cclerc.luxapp.ui.components.HintIndicator
import ch.cclerc.luxapp.ui.components.KeyboardToolbarHeight
import ch.cclerc.luxapp.ui.components.PaginationControls
import ch.cclerc.luxapp.ui.components.TripResultsSkeletonList
import ch.cclerc.luxapp.ui.theme.LuxShapes
import ch.cclerc.luxapp.ui.theme.LuxTheme
import ch.cclerc.luxapp.ui.theme.iosShadow
import ch.cclerc.luxapp.viewmodel.SearchField
import ch.cclerc.luxapp.viewmodel.TripsSearchViewModel
import ch.cclerc.luxcom.model.SearchResult
import ch.cclerc.luxcom.model.trip.Itinerary

private const val MIN_SEARCH_CHARACTERS = 3

private enum class TripsSearchSurface {
    TripResults,
    SearchResults,
    History,
    Empty
}

@Composable
fun TripsSearchContentView(
    viewModel: TripsSearchViewModel,
    modifier: Modifier = Modifier,
    onOpenItinerary: ((Itinerary, String?) -> Unit)? = null,
    shortcutSymbol: ((SearchResult) -> String?)? = null
) {
    val colors = LuxTheme.colors
    val shape = LuxShapes.topCorners(LuxShapes.r38)

    val showTripResults by viewModel.showTripResults.collectAsState()
    val activeField by viewModel.activeField.collectAsState()
    val fromQuery by viewModel.fromQuery.collectAsState()
    val toQuery by viewModel.toQuery.collectAsState()
    val viaQuery by viewModel.viaQuery.collectAsState()
    val results by viewModel.searchResults.collectAsState()
    val showMinCharacters by viewModel.showMinCharactersMessage.collectAsState()

    val searchActive = activeField != SearchField.NONE &&
        (fromQuery.length >= MIN_SEARCH_CHARACTERS ||
            toQuery.length >= MIN_SEARCH_CHARACTERS ||
            viaQuery.length >= MIN_SEARCH_CHARACTERS ||
            results.isNotEmpty() ||
            showMinCharacters)

    val surface = when {
        showTripResults -> TripsSearchSurface.TripResults
        searchActive -> TripsSearchSurface.SearchResults
        activeField != SearchField.NONE &&
            fromQuery.isEmpty() && toQuery.isEmpty() && viaQuery.isEmpty() &&
            results.isEmpty() && !showMinCharacters && Settings.showHistory -> TripsSearchSurface.History
        else -> TripsSearchSurface.Empty
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .iosShadow(
                color = Color.Black.copy(alpha = if (colors.isDark) 0.15f else 0.05f),
                blurRadius = 12.dp,
                offsetY = (-4).dp,
                shape = shape
            )
            .background(
                if (colors.isDark) colors.secondarySystemBackground.copy(alpha = 0.7f) else Color.White,
                shape
            )
            .clip(shape)
    ) {
        Crossfade(
            targetState = surface,
            animationSpec = tween(durationMillis = 300),
            label = "tripsSearchSurface"
        ) { target ->
            when (target) {
                TripsSearchSurface.TripResults -> TripResultsContent(
                    viewModel = viewModel,
                    onOpenItinerary = onOpenItinerary
                )
                TripsSearchSurface.SearchResults -> SearchResultsContent(viewModel)
                TripsSearchSurface.History -> SearchHistoryContent(
                    viewModel = viewModel,
                    shortcutSymbol = shortcutSymbol
                )
                TripsSearchSurface.Empty -> EmptyStateContent(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun TripResultsContent(
    viewModel: TripsSearchViewModel,
    modifier: Modifier = Modifier,
    onOpenItinerary: ((Itinerary, String?) -> Unit)? = null
) {
    val showSkeleton by viewModel.showSkeleton.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val trips by viewModel.trips.collectAsState()
    val directs by viewModel.directs.collectAsState()
    val vias by viewModel.vias.collectAsState()
    val isPresetFallback by viewModel.isPresetFallback.collectAsState()
    val isSearchingTrips by viewModel.isSearchingTrips.collectAsState()
    val colors = LuxTheme.colors

    Column(modifier = modifier.fillMaxSize()) {
        RoutePresetBar(viewModel = viewModel, modifier = Modifier.padding(top = 14.dp))

        AnimatedVisibility(
            visible = isPresetFallback && !isSearchingTrips,
            enter = fadeIn(tween(250)) + expandVertically(tween(250)),
            exit = fadeOut(tween(250)) + shrinkVertically(tween(250))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SFSymbol(name = "info.circle", size = 12.sp, color = colors.secondaryLabel, weight = 500)
                Text(
                    text = "Aucun itinéraire avec ce profil, itinéraires standards affichés",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.secondaryLabel
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .topFadeMask(14.dp)
        ) {
            when {
                showSkeleton -> TripResultsSkeletonList()
                errorMessage != null -> ErrorView(
                    message = errorMessage.orEmpty(),
                    onRetry = { viewModel.searchTrips() }
                )
                trips.isEmpty() && directs.isEmpty() -> NoResultsView(hasVias = vias.isNotEmpty())
                else -> TripResultsList(
                    viewModel = viewModel,
                    trips = trips,
                    directs = directs,
                    onOpenItinerary = onOpenItinerary
                )
            }
        }
    }
}

@Composable
fun RoutePresetBar(
    viewModel: TripsSearchViewModel,
    modifier: Modifier = Modifier
) {
    val colors = LuxTheme.colors
    val accent = LuxTheme.accent
    val selected by viewModel.routePreset.collectAsState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RoutePreset.entries.forEach { preset ->
            val isSelected = selected == preset
            val shape = RoundedCornerShape(50)
            val fill by animateColorAsState(
                targetValue = if (isSelected) accent.copy(alpha = 0.14f) else colors.secondarySystemFill.copy(alpha = colors.secondarySystemFill.alpha * 0.5f),
                animationSpec = LuxSprings.springFor(0.35, 0.8),
                label = "presetFill"
            )
            val stroke by animateColorAsState(
                targetValue = if (isSelected) accent.copy(alpha = 0.35f) else Color.Transparent,
                animationSpec = LuxSprings.springFor(0.35, 0.8),
                label = "presetStroke"
            )
            val content = if (isSelected) accent else colors.secondaryLabel
            Row(
                modifier = Modifier
                    .clip(shape)
                    .scaleClickable { viewModel.setRoutePreset(preset) }
                    .background(fill, shape)
                    .border(0.5.dp, stroke, shape)
                    .padding(vertical = 8.dp, horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SFSymbol(name = preset.symbol, size = 12.sp, color = content, weight = 600)
                Text(
                    text = preset.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = content,
                    maxLines = 1
                )
            }
        }
    }
}

private fun Modifier.topFadeMask(height: androidx.compose.ui.unit.Dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.Black),
                startY = 0f,
                endY = height.toPx()
            ),
            blendMode = BlendMode.DstIn
        )
    }

@Composable
private fun TripResultsList(
    viewModel: TripsSearchViewModel,
    trips: List<Itinerary>,
    directs: List<Itinerary>,
    onOpenItinerary: ((Itinerary, String?) -> Unit)?
) {
    val listState = rememberLazyListState()
    val toLocation by viewModel.toLocation.collectAsState()
    val animateIn by viewModel.animateIn.collectAsState()
    val isLoadingEarlier by viewModel.isLoadingEarlier.collectAsState()
    val isLoadingLater by viewModel.isLoadingLater.collectAsState()
    val isChangingContent by viewModel.isChangingContent.collectAsState()
    val isSearchingTrips by viewModel.isSearchingTrips.collectAsState()

    val hasDivider = directs.isNotEmpty() && trips.isNotEmpty()
    val firstTripIndex = directs.size + if (hasDivider) 1 else 0

    LaunchedEffect(trips, animateIn) {
        if (trips.isNotEmpty() && animateIn && directs.isEmpty()) {
            listState.animateScrollToItem(firstTripIndex)
        }
    }

    // Edge-to-edge means adjustResize never shrinks us; reserve room for the IME and the
    // KeyboardToolbar that floats on top of it.
    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val listBottomPadding = if (imeBottom > 0.dp) imeBottom + KeyboardToolbarHeight + 16.dp else 100.dp

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(top = 12.dp, bottom = listBottomPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            itemsIndexedItineraries(directs, "direct", toLocation?.displayName, onOpenItinerary)

            if (hasDivider) {
                item(key = "directsDivider") {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 32.dp),
                        thickness = 0.5.dp,
                        color = LuxTheme.colors.separator
                    )
                }
            }

            itemsIndexedItineraries(trips, "trip", toLocation?.displayName, onOpenItinerary)
        }

        PaginationControls(
            isLoadingEarlier = isLoadingEarlier,
            isLoadingLater = isLoadingLater,
            isChangingContent = isChangingContent,
            isLoading = isSearchingTrips,
            animateIn = animateIn,
            loadEarlier = { viewModel.loadEarlier() },
            loadLater = { viewModel.loadLater() },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

private fun LazyListScope.itemsIndexedItineraries(
    itineraries: List<Itinerary>,
    prefix: String,
    destinationName: String?,
    onOpenItinerary: ((Itinerary, String?) -> Unit)?
) {
    itineraries.forEachIndexed { index, itinerary ->
        item(key = "$prefix-$index") {
            TripResultView(
                itinerary = itinerary,
                destinationName = destinationName,
                onClick = onOpenItinerary?.let { callback ->
                    { clicked: Itinerary -> callback(clicked, destinationName) }
                }
            )
        }
    }
}

@Composable
fun EmptyStateContent(
    viewModel: TripsSearchViewModel,
    modifier: Modifier = Modifier
) {
    val colors = LuxTheme.colors
    val accent = LuxTheme.accent
    val location by LocationService.location.collectAsState()
    val shortcuts by ShortcutManager.shared.shortcuts.collectAsState()
    val showsHint = remember { Progress.numOfTimesTripViewWasOpened == 1 }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp)
            .padding(bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        if (showsHint) {
            HintIndicator(
                icon = "chevron.compact.up",
                message = "Glissez vers le haut pour retourner sur l'écran d'accueil",
                delayMs = 1000,
                durationMs = 15000,
                modifier = Modifier.padding(top = 20.dp)
            )
            Spacer(Modifier.weight(1f))
        }

        SFSymbol(
            name = "map",
            size = 40.sp,
            color = colors.secondaryLabel.copy(alpha = 0.6f),
            weight = 300,
            modifier = Modifier
                .padding(top = if (showsHint) 5.dp else 60.dp)
                .pulse(fromAlpha = 1f, toAlpha = 0.4f, durationMs = 800)
        )

        Text(
            text = "Entrez un point de départ et une destination",
            style = LuxTheme.type.headline,
            color = colors.secondaryLabel,
            textAlign = TextAlign.Center
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (location != null) {
                val shape = RoundedCornerShape(LuxShapes.r16)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = if (shortcuts.isEmpty()) 5.dp else 0.dp)
                        .clip(shape)
                        .background(accent.copy(alpha = 0.15f), shape)
                        .scaleClickable { viewModel.selectCurrentPosition() }
                        .padding(horizontal = 10.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SFSymbol(name = "location.fill", size = 16.sp, color = accent, weight = 500)
                    Text(
                        text = "Utiliser ma position actuelle",
                        style = LuxTheme.type.body,
                        fontWeight = FontWeight.Medium,
                        color = accent
                    )
                }
            }

            if (shortcuts.isNotEmpty()) {
                Text(
                    text = "Raccourcis",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.secondaryLabel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .padding(top = 8.dp)
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .bottomFadeMask(20.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    shortcuts.forEach { shortcut ->
                        ShortcutSuggestionRow(
                            symbol = shortcut.symbol,
                            name = shortcut.name,
                            locationName = shortcut.coordinates.locationName,
                            onClick = {
                                HapticFeedback.lightImpact()
                                viewModel.handleInitialSearchResult(
                                    shortcut.toSearchResult(),
                                    viewModel.activeField.value
                                )
                            }
                        )
                    }
                    Spacer(Modifier.height(15.dp))
                }
            } else {
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ShortcutSuggestionRow(
    symbol: String,
    name: String,
    locationName: String,
    onClick: () -> Unit
) {
    val colors = LuxTheme.colors
    val accent = LuxTheme.accent
    val shape = RoundedCornerShape(LuxShapes.r16)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.secondaryLabel.copy(alpha = 0.1f), shape)
            .border(0.5.dp, colors.hairline, shape)
            .scaleClickable(haptic = false) { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            SFSymbol(name = symbol, size = 16.sp, color = accent, weight = 500)
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = name,
                style = LuxTheme.type.body,
                fontWeight = FontWeight.Medium,
                color = colors.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = locationName,
                style = LuxTheme.type.caption,
                color = colors.secondaryLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

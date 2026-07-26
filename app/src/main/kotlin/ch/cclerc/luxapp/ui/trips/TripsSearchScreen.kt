package ch.cclerc.luxapp.ui.trips

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.cclerc.luxapp.core.HapticFeedback
import ch.cclerc.luxapp.domain.shortcut.ShortcutManager
import ch.cclerc.luxapp.domain.search.PhotonPoiSearchProvider
import ch.cclerc.luxapp.domain.search.PoiSearchProvider
import ch.cclerc.luxapp.domain.search.SearchResultVisualStyleStore
import ch.cclerc.luxapp.ui.components.KeyboardToolbar
import ch.cclerc.luxapp.ui.components.KeyboardToolbarShortcut
import ch.cclerc.luxapp.viewmodel.SearchField
import ch.cclerc.luxapp.viewmodel.TripsSearchViewModel
import ch.cclerc.luxcom.model.SearchResult
import ch.cclerc.luxcom.model.trip.Itinerary

private val sharedPoiProvider: PoiSearchProvider by lazy { PhotonPoiSearchProvider() }

internal fun TripsSearchViewModel.installPoiHooks(provider: PoiSearchProvider = sharedPoiProvider) {
    placeSearch = { query, lat, lon ->
        val outcome = provider.search(query, lat, lon)
        if (outcome.styles.isNotEmpty()) {
            SearchResultVisualStyleStore.setStyles(outcome.styles)
        }
        outcome.results
    }
    resolvePlace = { result -> provider.resolve(result) }
}

@Stable
class TripsSearchScreenState internal constructor(val viewModel: TripsSearchViewModel) {

    fun enterSearchMode() {
        if (viewModel.isCurrentPositionAvailable()) {
            viewModel.selectCurrentPosition()
        }
        viewModel.setActiveSearchField(SearchField.TO)
    }

    fun reset() {
        viewModel.reset()
    }

    fun handleShortcut(result: SearchResult) {
        val field = if (viewModel.activeField.value == SearchField.FROM) SearchField.FROM else SearchField.TO
        viewModel.handleInitialSearchResult(result, field)
    }

    fun prefillDestination(result: SearchResult) {
        viewModel.handleInitialSearchResult(result, SearchField.TO)
    }
}

@Composable
fun rememberTripsSearchScreenState(
    viewModel: TripsSearchViewModel = viewModel(key = "tripsSearchShell")
): TripsSearchScreenState {
    LaunchedEffect(viewModel) { viewModel.installPoiHooks() }
    return remember(viewModel) { TripsSearchScreenState(viewModel) }
}

@Composable
fun TripsSearchHeaderSlot(
    state: TripsSearchScreenState,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    shortcutSymbol: (SearchResult) -> String? = { null }
) {
    TripsSearchHeaderView(
        viewModel = state.viewModel,
        modifier = modifier,
        onBack = onBack,
        shortcutSymbol = shortcutSymbol
    )
}

@Composable
fun TripsSearchContentSlot(
    state: TripsSearchScreenState,
    modifier: Modifier = Modifier,
    onOpenItinerary: ((Itinerary, String?) -> Unit)? = null
) {
    val shortcuts by ShortcutManager.shared.shortcuts.collectAsState()

    Box(modifier.fillMaxSize()) {
        TripsSearchContentView(
            viewModel = state.viewModel,
            onOpenItinerary = onOpenItinerary
        )

        KeyboardToolbar(
            shortcuts = shortcuts.map { KeyboardToolbarShortcut(it.symbol, it.name) },
            onCurrentPositionSelected = {
                state.viewModel.selectCurrentPosition()
                HapticFeedback.lightImpact()
            },
            onShortcutSelected = { index ->
                shortcuts.getOrNull(index)?.let { shortcut ->
                    HapticFeedback.lightImpact()
                    state.handleShortcut(shortcut.toSearchResult())
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

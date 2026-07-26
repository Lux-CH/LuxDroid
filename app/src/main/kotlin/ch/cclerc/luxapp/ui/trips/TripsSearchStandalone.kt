package ch.cclerc.luxapp.ui.trips

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.cclerc.luxapp.core.HapticFeedback
import ch.cclerc.luxapp.domain.shortcut.ShortcutManager
import ch.cclerc.luxapp.ui.components.KeyboardToolbar
import ch.cclerc.luxapp.ui.components.KeyboardToolbarShortcut
import ch.cclerc.luxapp.ui.theme.LuxTheme
import ch.cclerc.luxapp.viewmodel.SearchField
import ch.cclerc.luxapp.viewmodel.TripsSearchViewModel
import ch.cclerc.luxcom.model.SearchResult
import ch.cclerc.luxcom.model.trip.Itinerary

@Composable
fun TripsSearchStandalone(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    initialSearchResult: SearchResult? = null,
    initialTargetField: SearchField = SearchField.TO,
    onOpenItinerary: ((Itinerary, String?) -> Unit)? = null,
    shortcutSymbol: (SearchResult) -> String? = { null }
) {
    val colors = LuxTheme.colors
    val isDark = LuxTheme.isDark
    val density = LocalDensity.current
    val statusInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    val viewModel: TripsSearchViewModel = viewModel(key = "tripsSearchStandalone")
    val shortcuts by ShortcutManager.shared.shortcuts.collectAsState()
    var dragOffset by remember { mutableStateOf(0f) }

    LaunchedEffect(viewModel) { viewModel.installPoiHooks() }

    LaunchedEffect(viewModel, initialSearchResult) {
        val result = initialSearchResult ?: return@LaunchedEffect
        viewModel.handleInitialSearchResult(result, initialTargetField)
    }

    DisposableEffect(viewModel) {
        onDispose { viewModel.reset() }
    }

    BackHandler { onDismiss() }

    val bgBrush = Brush.verticalGradient(
        if (isDark) {
            listOf(colors.systemBackground, colors.systemBackground.copy(alpha = 0.92f))
        } else {
            listOf(colors.secondarySystemBackground.copy(alpha = 0.7f), androidx.compose.ui.graphics.Color.White)
        }
    )

    Box(
        modifier
            .fillMaxSize()
            .background(bgBrush)
            .pointerInput(Unit) {
                var dy = 0f
                detectDragGestures(
                    onDragStart = { dy = 0f },
                    onDrag = { change, amount ->
                        change.consume()
                        dy += amount.y
                        dragOffset = (dragOffset + amount.y).coerceAtMost(0f)
                    },
                    onDragEnd = {
                        val dyDp = with(density) { dy.toDp().value }
                        if (dyDp < -50) {
                            HapticFeedback.lightImpact()
                            onDismiss()
                        } else {
                            dragOffset = 0f
                        }
                    }
                )
            }
    ) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy((-15).dp)
        ) {
            TripsSearchHeaderSlot(
                state = remember(viewModel) { TripsSearchScreenState(viewModel) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = statusInset + 12.dp),
                onBack = onDismiss,
                shortcutSymbol = shortcutSymbol
            )

            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .offset(y = with(density) { dragOffset.toDp() })
            ) {
                TripsSearchContentView(
                    viewModel = viewModel,
                    onOpenItinerary = onOpenItinerary
                )
            }
        }

        KeyboardToolbar(
            shortcuts = shortcuts.map { KeyboardToolbarShortcut(it.symbol, it.name) },
            onCurrentPositionSelected = {
                viewModel.selectCurrentPosition()
                HapticFeedback.lightImpact()
            },
            onShortcutSelected = { index ->
                shortcuts.getOrNull(index)?.let { shortcut ->
                    HapticFeedback.lightImpact()
                    val field = if (viewModel.activeField.value == SearchField.FROM) {
                        SearchField.FROM
                    } else {
                        SearchField.TO
                    }
                    viewModel.handleInitialSearchResult(shortcut.toSearchResult(), field)
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

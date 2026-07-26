package ch.cclerc.luxapp.ui.trips

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.cclerc.luxapp.core.HapticFeedback
import ch.cclerc.luxapp.core.LocationService
import ch.cclerc.luxapp.core.SFSymbol
import ch.cclerc.luxapp.ui.anim.scaleClickable
import ch.cclerc.luxapp.ui.anim.staggeredEntrance
import ch.cclerc.luxapp.ui.theme.LuxShapes
import ch.cclerc.luxapp.ui.theme.LuxTheme
import ch.cclerc.luxapp.ui.theme.iosShadow
import ch.cclerc.luxcom.model.SearchResult
import ch.cclerc.luxapp.viewmodel.TripsSearchViewModel
import kotlinx.coroutines.delay

@Composable
fun SearchHistoryContent(
    viewModel: TripsSearchViewModel,
    modifier: Modifier = Modifier,
    shortcutSymbol: ((SearchResult) -> String?)? = null
) {
    val history by viewModel.searchHistory.collectAsState()
    var appear by remember { mutableStateOf(false) }
    var showClearAlert by remember { mutableStateOf(false) }

    DisposableEffect(Unit) { onDispose { appear = false } }
    LaunchedEffect(Unit) {
        delay(50)
        appear = true
    }

    if (history.isEmpty()) {
        EmptyStateContent(
            viewModel = viewModel,
            modifier = modifier
        )
        return
    }

    Column(modifier = modifier.fillMaxWidth()) {
        HistoryHeader(
            viewModel = viewModel,
            onRequestClear = { showClearAlert = true },
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .padding(top = 18.dp, bottom = 11.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .bottomFadeMask(28.dp)
                .verticalScroll(rememberScrollState())
        ) {
            HistoryList(
                history = history,
                appear = appear,
                shortcutSymbol = shortcutSymbol,
                onSelect = { viewModel.selectLocation(it) },
                onRemove = { viewModel.removeFromHistory(it) },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(40.dp))
        }
    }

    if (showClearAlert) {
        val colors = LuxTheme.colors
        AlertDialog(
            onDismissRequest = { showClearAlert = false },
            title = {
                Text("Effacer l'historique ?", style = LuxTheme.type.headline, color = colors.label)
            },
            text = {
                Text(
                    text = "Cette action supprimera tous les éléments de l'historique.",
                    style = LuxTheme.type.footnote,
                    color = colors.secondaryLabel
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    HapticFeedback.mediumImpact()
                    viewModel.clearHistory()
                    showClearAlert = false
                }) {
                    Text("Effacer", color = colors.systemRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAlert = false }) {
                    Text("Annuler", color = LuxTheme.accent)
                }
            },
            containerColor = colors.secondarySystemBackgroundElevated
        )
    }
}

@Composable
private fun HistoryHeader(
    viewModel: TripsSearchViewModel,
    onRequestClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LuxTheme.colors
    val accent = LuxTheme.accent
    val location by LocationService.location.collectAsState()
    val capsule = RoundedCornerShape(LuxShapes.r50)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Historique",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.secondaryLabel
        )

        Spacer(Modifier.weight(1f))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (location != null) {
                Box(
                    modifier = Modifier
                        .clip(capsule)
                        .background(accent.copy(alpha = 0.12f), capsule)
                        .border(0.5.dp, accent.copy(alpha = 0.35f), capsule)
                        .scaleClickable { viewModel.selectCurrentPosition() }
                        .padding(horizontal = 24.dp, vertical = 6.dp)
                ) {
                    SFSymbol(name = "location.fill", size = 13.sp, color = accent, weight = 600)
                }
            }

            Box(
                modifier = Modifier
                    .clip(capsule)
                    .background(colors.secondarySystemFill.copy(alpha = 0.5f), capsule)
                    .border(0.5.dp, colors.hairline, capsule)
                    .scaleClickable { onRequestClear() }
                    .padding(horizontal = 24.dp, vertical = 5.5.dp)
            ) {
                SFSymbol(name = "trash", size = 13.sp, color = colors.systemRed, weight = 600)
            }
        }
    }
}

@Composable
private fun HistoryList(
    history: List<SearchResult>,
    appear: Boolean,
    shortcutSymbol: ((SearchResult) -> String?)?,
    onSelect: (SearchResult) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LuxTheme.colors
    val shape = RoundedCornerShape(LuxShapes.r20)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .iosShadow(
                color = Color.Black.copy(alpha = if (colors.isDark) 0.25f else 0.1f),
                blurRadius = 12.dp,
                offsetY = 4.dp,
                shape = shape
            )
            .background(
                if (colors.isDark) colors.tertiarySystemBackground else colors.secondarySystemBackground,
                shape
            )
            .border(0.5.dp, colors.label.copy(alpha = if (colors.isDark) 0.08f else 0.06f), shape)
            .clip(shape)
    ) {
        history.forEachIndexed { index, result ->
            HistoryRow(
                result = result,
                index = index,
                appear = appear,
                shortcutSymbol = shortcutSymbol?.invoke(result),
                onSelect = { onSelect(result) },
                onRemove = { onRemove(result.id) }
            )

            if (index < history.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 68.dp),
                    thickness = 0.5.dp,
                    color = colors.separator
                )
            }
        }
    }
}

@Composable
private fun HistoryRow(
    result: SearchResult,
    index: Int,
    appear: Boolean,
    shortcutSymbol: String?,
    onSelect: () -> Unit,
    onRemove: () -> Unit
) {
    val colors = LuxTheme.colors
    val accent = LuxTheme.accent

    val stored = storedIconStyle(result.id)
    val fallback = stored?.let { it.first to it.second.resolve(colors, accent) }
        ?: defaultIconForType(result.type, colors, accent)
    val (symbolName, iconColor) = if (shortcutSymbol != null) shortcutSymbol to accent else fallback

    Row(
        modifier = Modifier
            .staggeredEntrance(index = index, visible = appear)
            .fillMaxWidth()
            .scaleClickable { onSelect() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SearchResultIcon(symbolName = symbolName, color = iconColor)

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = result.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = colors.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val area = relevantAreaName(result)
            if (area != null) {
                Text(
                    text = area,
                    fontSize = 12.sp,
                    color = colors.secondaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(colors.quaternarySystemFill, CircleShape)
                .scaleClickable(haptic = false) {
                    HapticFeedback.mediumImpact()
                    onRemove()
                },
            contentAlignment = Alignment.Center
        ) {
            SFSymbol(name = "xmark", size = 10.sp, color = colors.tertiaryLabel, weight = 700)
        }
    }
}

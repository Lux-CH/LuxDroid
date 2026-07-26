package ch.cclerc.luxapp.ui.trips

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import ch.cclerc.luxapp.core.HapticFeedback
import ch.cclerc.luxapp.core.SFSymbol
import ch.cclerc.luxapp.ui.anim.scaleClickable
import ch.cclerc.luxapp.ui.navigation.LocalSheetController
import ch.cclerc.luxapp.ui.navigation.LuxSheetRequest
import ch.cclerc.luxapp.ui.navigation.SheetDetent
import ch.cclerc.luxapp.ui.theme.LuxMaterials
import ch.cclerc.luxapp.ui.theme.LuxShapes
import ch.cclerc.luxapp.ui.theme.LuxSprings
import ch.cclerc.luxapp.ui.theme.LuxTheme
import ch.cclerc.luxapp.ui.theme.iosShadow
import ch.cclerc.luxapp.viewmodel.DepartureType
import ch.cclerc.luxapp.viewmodel.SearchField
import ch.cclerc.luxapp.viewmodel.TripsSearchViewModel
import ch.cclerc.luxcom.model.SearchResult
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val timeOnlyFormatter: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault())

private val dateAndTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT, FormatStyle.SHORT)
        .withLocale(Locale.getDefault())

@Composable
fun TripsSearchHeaderView(
    viewModel: TripsSearchViewModel,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    shortcutSymbol: (SearchResult) -> String? = { null }
) {
    val colors = LuxTheme.colors
    val accent = LuxTheme.accent
    val scope = rememberCoroutineScope()
    val sheets = LocalSheetController.current

    val fromQuery by viewModel.fromQuery.collectAsState()
    val toQuery by viewModel.toQuery.collectAsState()
    val fromLocation by viewModel.fromLocation.collectAsState()
    val toLocation by viewModel.toLocation.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val departureType by viewModel.departureType.collectAsState()
    val showPastDateWarning by viewModel.showPastDateWarning.collectAsState()
    val hasCustomSettings by viewModel.hasCustomSettings.collectAsState()
    val showSettings by viewModel.showSettings.collectAsState()

    val headerOffset = remember { Animatable(-100f) }
    val contentOpacity = remember { Animatable(0f) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showWarningOvertimeAlert by remember { mutableStateOf(false) }
    var isSwapping by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        launch { headerOffset.animateTo(0f, LuxSprings.springFor(0.6, 0.8)) }
        launch { contentOpacity.animateTo(1f, LuxSprings.springFor(0.6, 0.8)) }
    }

    LaunchedEffect(showSettings) {
        if (!showSettings) return@LaunchedEffect
        sheets.present(
            LuxSheetRequest(
                cornerRadius = LuxShapes.r36,
                detents = listOf(SheetDetent.Medium, SheetDetent.Large)
            ) {
                val currentOptions by viewModel.routeOptions.collectAsState()
                RouteOptionsView(
                    routeOptions = currentOptions,
                    onDismiss = { sheets.dismiss() },
                    onSave = { viewModel.updateRouteOptions(it) }
                )
            }
        )
        viewModel.setShowSettings(false)
    }

    fun dismissHeaderAndGoBack() {
        val back = onBack ?: return
        scope.launch {
            launch { headerOffset.animateTo(-80f, LuxSprings.springFor(0.4, 0.8)) }
            launch { contentOpacity.animateTo(0f, LuxSprings.springFor(0.4, 0.8)) }
            delay(100)
            back()
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { translationY = headerOffset.value * density }
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(22.5.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(contentOpacity.value)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                HeaderChip(
                    fill = LuxMaterials.capsuleFill(),
                    stroke = colors.hairline,
                    horizontalPadding = 20.dp,
                    onClick = { dismissHeaderAndGoBack() }
                ) {
                    SFSymbol(name = "chevron.backward", size = 16.sp, color = accent, weight = 600)
                }
            }

            Spacer(Modifier.weight(1f))

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AnimatedVisibility(
                    visible = showPastDateWarning,
                    enter = fadeIn(LuxSprings.Snappy) +
                        slideInVertically(LuxSprings.springFor(0.3, 0.7)) { -it } +
                        scaleIn(LuxSprings.Snappy, initialScale = 0.8f),
                    exit = fadeOut(LuxSprings.Snappy) +
                        slideOutVertically(LuxSprings.springFor(0.3, 0.7)) { -it } +
                        scaleOut(LuxSprings.Snappy, targetScale = 0.8f)
                ) {
                    HeaderChip(
                        fill = LuxMaterials.tintedChipFill(colors.systemYellow),
                        stroke = LuxMaterials.tintedChipStroke(colors.systemYellow),
                        horizontalPadding = 14.dp,
                        onClick = {
                            HapticFeedback.lightImpact()
                            showWarningOvertimeAlert = true
                        }
                    ) {
                        SFSymbol(
                            name = "exclamationmark.triangle",
                            size = 14.sp,
                            color = colors.systemYellow,
                            weight = 600
                        )
                    }
                }

                TimeChip(
                    summary = timeSummaryText(selectedDate, departureType),
                    hasDate = selectedDate != null,
                    expanded = showTimePicker,
                    onExpandedChange = { showTimePicker = it },
                    picker = {
                        TripsSearchTimePickerView(
                            selectedDate = selectedDate,
                            departureType = departureType,
                            onDepartureTypeChange = { viewModel.setDepartureType(it) },
                            onDismiss = { showTimePicker = false },
                            onApply = { date ->
                                viewModel.setSelectedDate(date)
                                showTimePicker = false
                                if (viewModel.fromLocation.value != null && viewModel.toLocation.value != null) {
                                    viewModel.searchTrips()
                                }
                            }
                        )
                    }
                )

                HeaderChip(
                    fill = LuxMaterials.tintedChipFill(accent),
                    stroke = LuxMaterials.tintedChipStroke(accent),
                    horizontalPadding = 20.dp,
                    onClick = {
                        HapticFeedback.lightImpact()
                        viewModel.setShowSettings(true)
                    }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        SFSymbol(name = "slider.horizontal.3", size = 14.sp, color = accent, weight = 600)
                        if (hasCustomSettings) {
                            Box(
                                Modifier
                                    .size(7.dp)
                                    .offset(x = 8.dp, y = 5.dp)
                                    .background(colors.systemGreen, CircleShape)
                            )
                        }
                    }
                }
            }
        }

        InputCard(
            fromSelected = fromLocation != null,
            toSelected = toLocation != null,
            contentOpacity = contentOpacity.value,
            isSwapping = isSwapping,
            onSwap = {
                if (fromLocation == null && toLocation == null) return@InputCard
                HapticFeedback.lightImpact()
                isSwapping = true
                viewModel.swap()
                scope.launch {
                    delay(500)
                    isSwapping = false
                }
            },
            fromBar = {
                TripSearchBar(
                    searchText = fromQuery,
                    onSearchTextChange = { viewModel.onQueryChange(it) },
                    placeholderText = "Depuis",
                    selectedLocation = fromLocation,
                    onSearch = { viewModel.performSearch(viewModel.fromQuery.value) },
                    onClear = { viewModel.resetSearch() },
                    onRemoveTag = { viewModel.removeFromLocation() },
                    onFocused = { viewModel.setActiveSearchField(SearchField.FROM) },
                    shortcutSymbol = shortcutSymbol,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            },
            toBar = {
                TripSearchBar(
                    searchText = toQuery,
                    onSearchTextChange = { viewModel.onQueryChange(it) },
                    placeholderText = "À",
                    selectedLocation = toLocation,
                    onSearch = { viewModel.performSearch(viewModel.toQuery.value) },
                    onClear = { viewModel.resetSearch() },
                    onRemoveTag = { viewModel.removeToLocation() },
                    onFocused = { viewModel.setActiveSearchField(SearchField.TO) },
                    shortcutSymbol = shortcutSymbol,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        )
    }

    if (showWarningOvertimeAlert) {
        AlertDialog(
            onDismissRequest = { showWarningOvertimeAlert = false },
            title = {
                Text(
                    text = "Date potentiellement incorrecte",
                    style = LuxTheme.type.headline,
                    color = colors.label
                )
            },
            text = {
                Text(
                    text = "La date séléctionnée est antérieure à l'heure actuelle",
                    style = LuxTheme.type.footnote,
                    color = colors.secondaryLabel
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setSelectedDate(null)
                    showWarningOvertimeAlert = false
                }) {
                    Text("Réinitialiser", color = colors.systemRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showWarningOvertimeAlert = false }) {
                    Text("OK", color = colors.secondaryLabel)
                }
            },
            containerColor = colors.secondarySystemBackgroundElevated
        )
    }
}

@Composable
private fun TimeChip(
    summary: String,
    hasDate: Boolean,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    picker: @Composable () -> Unit
) {
    val colors = LuxTheme.colors
    val accent = LuxTheme.accent
    var anchorHeight by remember { mutableStateOf(0) }

    Box(Modifier.onSizeChanged { anchorHeight = it.height }) {
        HeaderChip(
            fill = LuxMaterials.tintedChipFill(accent),
            stroke = LuxMaterials.tintedChipStroke(accent),
            horizontalPadding = 20.dp,
            onClick = {
                HapticFeedback.lightImpact()
                onExpandedChange(true)
            }
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SFSymbol(name = "clock", size = 14.sp, color = accent, weight = 600)
                if (hasDate) {
                    Text(
                        text = summary,
                        style = LuxTheme.type.footnote,
                        fontWeight = FontWeight.Medium,
                        color = accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (expanded) {
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(0, anchorHeight + 8),
                onDismissRequest = { onExpandedChange(false) },
                properties = PopupProperties(focusable = true)
            ) {
                picker()
            }
        }
    }
}

@Composable
private fun HeaderChip(
    fill: Color,
    stroke: Color,
    horizontalPadding: Dp,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .clip(shape)
            .scaleClickable(haptic = false) { onClick() }
            .background(fill, shape)
            .border(LuxMaterials.capsuleStrokeWidth, stroke, shape)
            .padding(horizontal = horizontalPadding, vertical = 8.dp)
            .heightIn(min = 15.dp),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun InputCard(
    fromSelected: Boolean,
    toSelected: Boolean,
    contentOpacity: Float,
    isSwapping: Boolean,
    onSwap: () -> Unit,
    fromBar: @Composable () -> Unit,
    toBar: @Composable () -> Unit
) {
    val colors = LuxTheme.colors
    val accent = LuxTheme.accent
    val shape = RoundedCornerShape(LuxShapes.r28)
    val swapEnabled = fromSelected || toSelected

    val swapRotation by animateFloatAsState(
        targetValue = if (isSwapping) 180f else 0f,
        animationSpec = LuxSprings.springFor(0.5, 0.6),
        label = "swapRotation"
    )
    val swapScale by animateFloatAsState(
        targetValue = if (isSwapping) 0.95f else 1f,
        animationSpec = LuxSprings.springFor(0.5, 0.6),
        label = "swapScale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(98.dp)
            .alpha(contentOpacity)
            .iosShadow(
                color = Color.Black.copy(alpha = 0.05f),
                blurRadius = 8.dp,
                offsetY = 2.dp,
                shape = shape
            )
            .background(LuxMaterials.capsuleFill(), shape)
            .border(0.5.dp, colors.hairline, shape)
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = 20.dp)
                .size(width = 2.dp, height = 30.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            if (fromSelected) accent.copy(alpha = 0.4f) else colors.secondaryLabel.copy(alpha = 0.3f),
                            if (toSelected) accent.copy(alpha = 0.4f) else colors.secondaryLabel.copy(alpha = 0.3f)
                        )
                    ),
                    RoundedCornerShape(1.dp)
                )
        )

        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.width(20.dp), contentAlignment = Alignment.Center) {
                    SFSymbol(
                        name = "location",
                        size = 16.sp,
                        color = if (fromSelected) accent else colors.secondaryLabel
                    )
                }
                Box(Modifier.weight(1f)) { fromBar() }
            }

            HorizontalDivider(thickness = 0.5.dp, color = colors.separator)

            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.width(20.dp), contentAlignment = Alignment.Center) {
                    SFSymbol(
                        name = "flag.checkered",
                        size = 16.sp,
                        color = if (toSelected) accent else colors.secondaryLabel
                    )
                }
                Box(Modifier.weight(1f)) { toBar() }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 6.dp)
                .size(38.dp)
                .graphicsLayer {
                    rotationZ = swapRotation
                    scaleX = swapScale
                    scaleY = swapScale
                }
                .iosShadow(
                    color = Color.Black.copy(alpha = 0.08f),
                    blurRadius = 6.dp,
                    offsetY = 2.dp,
                    shape = CircleShape
                )
                .background(colors.secondarySystemBackground, CircleShape)
                .background(LuxMaterials.capsuleFill(), CircleShape)
                .border(0.5.dp, colors.hairline, CircleShape)
                .clip(CircleShape)
                .scaleClickable(enabled = swapEnabled, haptic = false) { onSwap() },
            contentAlignment = Alignment.Center
        ) {
            SFSymbol(
                name = "arrow.up.arrow.down",
                size = 16.sp,
                weight = 600,
                color = if (swapEnabled) accent else colors.tertiaryLabel
            )
        }
    }
}

private fun timeSummaryText(selectedDate: Instant?, departureType: DepartureType): String {
    val typeText = if (departureType == DepartureType.ARRIVE_BY) "Arrivée" else "Départ"
    val date = selectedDate ?: return "$typeText Maintenant"
    return "$typeText • ${formatDate(date)}"
}

private fun formatDate(date: Instant): String {
    val zone = ZoneId.systemDefault()
    val local = date.atZone(zone)
    val today = LocalDate.now(zone)
    return when (local.toLocalDate()) {
        today -> timeOnlyFormatter.format(local)
        today.plusDays(1) -> "${timeOnlyFormatter.format(local)}*"
        else -> dateAndTimeFormatter.format(local)
    }
}

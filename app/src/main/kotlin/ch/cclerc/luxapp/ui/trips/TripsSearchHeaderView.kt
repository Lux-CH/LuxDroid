package ch.cclerc.luxapp.ui.trips

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
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
import ch.cclerc.luxapp.ui.theme.LuxTypography
import ch.cclerc.luxapp.ui.theme.iosShadow
import ch.cclerc.luxapp.viewmodel.DepartureType
import ch.cclerc.luxapp.viewmodel.SearchField
import ch.cclerc.luxapp.viewmodel.SelectedLocation
import ch.cclerc.luxapp.viewmodel.ViaStop
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
    shortcutSymbol: (SearchResult) -> String? = { null },
    toFocusRequester: androidx.compose.ui.focus.FocusRequester? = null
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
    val viaQuery by viewModel.viaQuery.collectAsState()
    val vias by viewModel.vias.collectAsState()
    val activeField by viewModel.activeField.collectAsState()
    val canAddVia = vias.size < viewModel.maxVias && vias.none { it.location == null }
    val viaFocusRequester = remember { FocusRequester() }

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
            viaCount = vias.size,
            canAddVia = canAddVia,
            onAddVia = {
                viewModel.addVia()
                scope.launch {
                    delay(150)
                    runCatching { viaFocusRequester.requestFocus() }
                }
            },
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
                    clearButtonInset = 0.dp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            },
            viaRow = { index ->
                val via = vias[index]
                ViaRowContent(
                    via = via,
                    viaQuery = viaQuery,
                    isActive = activeField == SearchField.VIA(via.id),
                    focusRequester = viaFocusRequester,
                    shortcutSymbol = shortcutSymbol,
                    onQueryChange = { viewModel.onQueryChange(it) },
                    onSearch = { viewModel.performSearch(viewModel.viaQuery.value) },
                    onClear = { viewModel.resetSearch() },
                    onFocused = { viewModel.setActiveSearchField(SearchField.VIA(via.id)) },
                    onRemove = { viewModel.removeVia(via.id) },
                    onStayChange = { minutes -> viewModel.setViaMinimumStay(minutes, via.id) }
                )
            },
            viaKey = { index -> vias[index].id },
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
                    focusRequester = toFocusRequester,
                    clearButtonInset = 0.dp,
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

fun viaRowsHeight(count: Int): Dp = (count * VIA_ROW_HEIGHT).dp

private const val VIA_ROW_HEIGHT = 49
private val SwapButtonSize = 38.dp
private val ViaButtonSize = 32.dp
private val TrailingControlsSpacing = 6.dp
private val TrailingControlsEdge = 6.dp
private val viaStayOptions = listOf(5, 10, 15, 30, 45, 60, 90, 120)

private fun trailingControlsInset(canAddVia: Boolean): Dp {
    val controls = if (canAddVia) {
        ViaButtonSize + TrailingControlsSpacing + SwapButtonSize
    } else {
        SwapButtonSize
    }
    return controls + TrailingControlsEdge + TrailingControlsSpacing
}

@Composable
private fun InputCard(
    fromSelected: Boolean,
    toSelected: Boolean,
    contentOpacity: Float,
    isSwapping: Boolean,
    viaCount: Int,
    canAddVia: Boolean,
    onAddVia: () -> Unit,
    onSwap: () -> Unit,
    fromBar: @Composable () -> Unit,
    viaRow: @Composable RowScope.(Int) -> Unit,
    viaKey: (Int) -> String,
    toBar: @Composable () -> Unit
) {
    val colors = LuxTheme.colors
    val accent = LuxTheme.accent
    val shape = RoundedCornerShape(LuxShapes.r28)
    val swapEnabled = fromSelected || toSelected
    val viaSpring = LuxSprings.springFor<Dp>(0.4, 0.82)
    val viaHeight by animateDpAsState(viaRowsHeight(viaCount), viaSpring, label = "viaHeight")
    val trailingInset by animateDpAsState(
        trailingControlsInset(canAddVia),
        LuxSprings.springFor(0.4, 0.8),
        label = "trailingInset"
    )

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
            .height(98.dp + viaHeight)
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
                .size(width = 2.dp, height = 30.dp + viaHeight)
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
                    .padding(start = 12.dp, end = trailingInset),
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

            for (index in 0 until viaCount) {
                key(viaKey(index)) {
                    val appear = remember { Animatable(0f) }
                    LaunchedEffect(Unit) { appear.animateTo(1f, LuxSprings.springFor(0.4, 0.82)) }
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .graphicsLayer {
                                alpha = appear.value
                                translationY = (1f - appear.value) * -12.dp.toPx()
                            }
                            .padding(start = 12.dp, end = trailingInset),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        viaRow(index)
                    }
                    HorizontalDivider(thickness = 0.5.dp, color = colors.separator)
                }
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = trailingInset),
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

        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = TrailingControlsEdge),
            horizontalArrangement = Arrangement.spacedBy(TrailingControlsSpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedVisibility(
                visible = canAddVia,
                enter = scaleIn(LuxSprings.springFor(0.4, 0.8), initialScale = 0.6f) + fadeIn(LuxSprings.springFor(0.4, 0.8)),
                exit = scaleOut(LuxSprings.springFor(0.4, 0.8), targetScale = 0.6f) + fadeOut(LuxSprings.springFor(0.4, 0.8))
            ) {
                Box(
                    modifier = Modifier
                        .size(ViaButtonSize)
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
                        .scaleClickable { onAddVia() },
                    contentAlignment = Alignment.Center
                ) {
                    SFSymbol(
                        name = "point.bottomleft.forward.to.point.topright.scurvepath",
                        size = 13.sp,
                        weight = 600,
                        color = accent
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(SwapButtonSize)
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
}

@Composable
private fun RowScope.ViaRowContent(
    via: ViaStop,
    viaQuery: String,
    isActive: Boolean,
    focusRequester: FocusRequester,
    shortcutSymbol: (SearchResult) -> String?,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    onFocused: () -> Unit,
    onRemove: () -> Unit,
    onStayChange: (Int) -> Unit
) {
    val colors = LuxTheme.colors
    val accent = LuxTheme.accent
    val location = via.location
    val focusManager = LocalFocusManager.current

    Box(Modifier.width(20.dp), contentAlignment = Alignment.Center) {
        SFSymbol(
            name = "smallcircle.filled.circle",
            size = 16.sp,
            color = if (location == null) colors.secondaryLabel else accent
        )
    }
    Box(Modifier.weight(1f)) {
        TripSearchBar(
            searchText = viaQuery,
            onSearchTextChange = onQueryChange,
            placeholderText = "Via",
            selectedLocation = location?.let { SelectedLocation.SearchResultLocation(it) },
            onSearch = onSearch,
            onClear = onClear,
            onRemoveTag = onRemove,
            onFocused = { if (location == null && !isActive) onFocused() },
            focusRequester = if (location == null) focusRequester else null,
            shortcutSymbol = shortcutSymbol,
            clearButtonInset = 0.dp,
            modifier = Modifier.padding(vertical = 8.dp)
        )
    }

    if (location != null) {
        ViaStayMenu(minimumStay = via.minimumStay, onStayChange = onStayChange)
    } else {
        Box(
            modifier = Modifier.scaleClickable {
                focusManager.clearFocus()
                onRemove()
            }
        ) {
            SFSymbol(name = "minus.circle.fill", size = 17.sp, color = colors.secondaryLabel)
        }
    }
}

@Composable
private fun ViaStayMenu(minimumStay: Int, onStayChange: (Int) -> Unit) {
    val colors = LuxTheme.colors
    val accent = LuxTheme.accent
    var expanded by remember { mutableStateOf(false) }
    val hasStay = minimumStay > 0
    val tint = if (hasStay) accent else colors.secondaryLabel
    val shape = RoundedCornerShape(50)

    Box {
        Row(
            modifier = Modifier
                .clip(shape)
                .background(if (hasStay) accent.copy(alpha = 0.12f) else colors.tertiarySystemFill, shape)
                .scaleClickable(haptic = false) { expanded = true }
                .padding(vertical = 5.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SFSymbol(
                name = if (hasStay) "hourglass" else "hourglass.badge.plus",
                size = 12.sp,
                color = tint,
                weight = 600
            )
            if (hasStay) {
                Text(
                    text = "$minimumStay min",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    style = LuxTypography.timeVariant(LuxTheme.type.caption),
                    color = tint
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = colors.secondarySystemBackgroundElevated
        ) {
            (listOf(0) + viaStayOptions).forEach { minutes ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = if (minutes == 0) "Simple passage" else "Rester $minutes min",
                            style = LuxTheme.type.body,
                            color = colors.label
                        )
                    },
                    trailingIcon = if (minutes == minimumStay) {
                        { SFSymbol(name = "checkmark", size = 15.sp, color = accent) }
                    } else {
                        null
                    },
                    onClick = {
                        expanded = false
                        HapticFeedback.lightImpact()
                        onStayChange(minutes)
                    }
                )
            }
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

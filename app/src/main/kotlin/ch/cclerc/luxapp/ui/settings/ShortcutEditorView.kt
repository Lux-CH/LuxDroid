package ch.cclerc.luxapp.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.cclerc.luxapp.core.HapticFeedback
import ch.cclerc.luxapp.core.LocationService
import ch.cclerc.luxapp.core.SFSymbol
import ch.cclerc.luxapp.domain.search.LocationSearchViewModel
import ch.cclerc.luxapp.domain.shortcut.UserShortcut
import ch.cclerc.luxapp.ui.anim.scaleClickable
import ch.cclerc.luxapp.ui.anim.staggeredEntrance
import ch.cclerc.luxapp.ui.components.ModernCard
import ch.cclerc.luxapp.ui.components.ModernCardStyle
import ch.cclerc.luxapp.ui.components.ModernToggle
import ch.cclerc.luxapp.ui.components.luxTextField
import ch.cclerc.luxapp.ui.navigation.LocalSheetController
import ch.cclerc.luxapp.ui.navigation.LuxSheetRequest
import ch.cclerc.luxapp.ui.stop.expanded.LuxWheelPicker
import ch.cclerc.luxapp.ui.stop.expanded.WHEEL_ITEM_HEIGHT
import ch.cclerc.luxapp.ui.stop.expanded.WHEEL_VISIBLE_ITEMS
import ch.cclerc.luxapp.ui.stop.expanded.wheelHourLabels
import ch.cclerc.luxapp.ui.stop.expanded.wheelMinuteLabels
import ch.cclerc.luxapp.ui.theme.LuxTheme
import ch.cclerc.luxapp.ui.trips.SearchResultIcon
import ch.cclerc.luxapp.ui.trips.SearchResultRow
import ch.cclerc.luxapp.viewmodel.ShortcutEditorViewModel
import ch.cclerc.luxcom.model.SearchResult
import kotlinx.coroutines.launch

@Composable
fun ShortcutEditorView(
    shortcutToEdit: UserShortcut?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LuxTheme.colors
    val accent = LuxTheme.accent
    val sheets = LocalSheetController.current
    val viewModel = remember { ShortcutEditorViewModel() }

    val name by viewModel.name.collectAsState()
    val selectedSymbol by viewModel.selectedSymbol.collectAsState()
    val selectedLocation by viewModel.selectedLocation.collectAsState()
    val hasTimeSchedule by viewModel.hasTimeSchedule.collectAsState()
    val selectedDays by viewModel.selectedDays.collectAsState()
    val selectedHour by viewModel.selectedHour.collectAsState()
    val selectedMinute by viewModel.selectedMinute.collectAsState()
    val isEditing by viewModel.isEditing.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    var showContent by remember { mutableStateOf(false) }

    LaunchedEffect(shortcutToEdit) {
        viewModel.setupForEditing(shortcutToEdit)
        showContent = true
    }

    val canSave = name.trim().isNotEmpty() && selectedLocation != null

    fun save() {
        if (!canSave) return
        HapticFeedback.mediumImpact()
        if (viewModel.save()) onDismiss()
    }

    val background = Brush.verticalGradient(
        listOf(
            colors.systemBackground,
            colors.systemGroupedBackground.copy(alpha = 0.3f),
            colors.systemGroupedBackground
        )
    )

    Column(
        modifier
            .fillMaxSize()
            .background(background)
            .imePadding()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 26.dp, bottom = 8.dp)
        ) {
            Text(
                text = "Annuler",
                style = LuxTheme.type.body,
                color = colors.secondaryLabel,
                modifier = Modifier
                    .scaleClickable(haptic = false) { onDismiss() }
                    .padding(vertical = 4.dp)
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = if (isEditing) "Enregistrer" else "Ajouter",
                style = LuxTheme.type.body.copy(fontWeight = FontWeight.Bold),
                color = if (canSave) accent else colors.secondaryLabel,
                modifier = Modifier
                    .scaleClickable(haptic = false) { save() }
                    .padding(vertical = 4.dp)
            )
        }

        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 100.dp)
        ) {
            if (!isEditing) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .staggeredEntrance(index = 0, visible = showContent, baseDelayMs = 100, fromOffsetY = 20.dp)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Créer un raccourci",
                            style = LuxTheme.type.title2.copy(fontWeight = FontWeight.Bold),
                            color = colors.label
                        )
                        Text(
                            text = "Accédez rapidement à vos destinations préférées",
                            style = LuxTheme.type.subheadline,
                            color = colors.secondaryLabel
                        )
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(60.dp)
                            .background(colors.secondarySystemFill, CircleShape)
                            .scaleClickable(haptic = false) { save() }
                    ) {
                        SFSymbol(name = "plus.circle.fill", size = 24.sp, color = accent)
                    }
                }
                Spacer(Modifier.height(22.dp))
            }

            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.staggeredEntrance(
                        index = 0,
                        visible = showContent,
                        baseDelayMs = 200,
                        fromOffsetY = 30.dp
                    )
                ) {
                    EditorSectionHeader(title = "Nom du raccourci", icon = "textformat")
                    Box(Modifier.fillMaxWidth().luxTextField()) {
                        BasicTextField(
                            value = name,
                            onValueChange = { viewModel.updateName(it) },
                            singleLine = true,
                            textStyle = LuxTheme.type.body.copy(color = colors.label),
                            cursorBrush = SolidColor(accent),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                                imeAction = ImeAction.Done
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { inner ->
                                if (name.isEmpty()) {
                                    Text(
                                        text = "Ex: Maison, Travail, École...",
                                        style = LuxTheme.type.body,
                                        color = colors.tertiaryLabel
                                    )
                                }
                                inner()
                            }
                        )
                    }
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.staggeredEntrance(
                        index = 0,
                        visible = showContent,
                        baseDelayMs = 300,
                        fromOffsetY = 40.dp
                    )
                ) {
                    EditorSectionHeader(title = "Icône", icon = "heart.circle")
                    ModernCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .scaleClickable {
                                sheets.present(
                                    LuxSheetRequest(cornerRadius = 38.dp) {
                                        SymbolPickerGrid(
                                            selected = selectedSymbol,
                                            onSelect = {
                                                viewModel.updateSymbol(it)
                                                sheets.dismiss()
                                            },
                                            onDismiss = { sheets.dismiss() }
                                        )
                                    }
                                )
                            }
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(accent.copy(alpha = 0.1f), CircleShape)
                            ) {
                                SFSymbol(name = selectedSymbol, size = 22.sp, color = accent)
                            }
                            Column(
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "Symbole sélectionné",
                                    style = LuxTheme.type.subheadline.copy(fontWeight = FontWeight.Medium),
                                    color = colors.label
                                )
                                Text(
                                    text = selectedSymbol,
                                    style = LuxTheme.type.caption,
                                    color = colors.secondaryLabel
                                )
                            }
                            SFSymbol(name = "chevron.right", size = 12.sp, color = colors.tertiaryLabel)
                        }
                    }
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.staggeredEntrance(
                        index = 0,
                        visible = showContent,
                        baseDelayMs = 400,
                        fromOffsetY = 50.dp
                    )
                ) {
                    EditorSectionHeader(title = "Destination", icon = "location.circle")
                    ModernCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .scaleClickable {
                                sheets.present(
                                    LuxSheetRequest(cornerRadius = 38.dp) {
                                        LocationSearchSheet(
                                            onSelected = {
                                                viewModel.updateLocation(it)
                                                sheets.dismiss()
                                            },
                                            onDismiss = { sheets.dismiss() }
                                        )
                                    }
                                )
                            }
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(
                                        if (selectedLocation != null) {
                                            colors.systemRed.copy(alpha = 0.1f)
                                        } else {
                                            colors.systemGray5
                                        },
                                        CircleShape
                                    )
                            ) {
                                SFSymbol(
                                    name = if (selectedLocation != null) "mappin.circle.fill" else "magnifyingglass",
                                    size = 20.sp,
                                    color = if (selectedLocation != null) colors.systemRed else colors.secondaryLabel
                                )
                            }
                            Column(
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = selectedLocation?.name ?: "Rechercher une destination",
                                    style = LuxTheme.type.subheadline.copy(fontWeight = FontWeight.Medium),
                                    color = if (selectedLocation != null) colors.label else colors.secondaryLabel,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (selectedLocation != null) {
                                    Text(
                                        text = "Destination sélectionnée",
                                        style = LuxTheme.type.caption,
                                        color = colors.secondaryLabel
                                    )
                                }
                            }
                            SFSymbol(name = "chevron.right", size = 12.sp, color = colors.tertiaryLabel)
                        }
                    }

                    AnimatedVisibility(
                        visible = selectedLocation == null,
                        enter = scaleIn() + fadeIn(),
                        exit = scaleOut() + fadeOut()
                    ) {
                        ModernCard(
                            style = ModernCardStyle.Accent,
                            optionalColor = accent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .scaleClickable { viewModel.useCurrentLocation() }
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                SFSymbol(name = "location.fill", size = 17.sp, color = accent)
                                Text(
                                    text = "Utiliser ma position actuelle",
                                    style = LuxTheme.type.subheadline.copy(fontWeight = FontWeight.Medium),
                                    color = accent
                                )
                            }
                        }
                    }

                    val error = errorMessage
                    if (error != null) {
                        Text(
                            text = error,
                            style = LuxTheme.type.caption,
                            color = colors.systemRed,
                            modifier = Modifier.scaleClickable(haptic = false) { viewModel.clearError() }
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.staggeredEntrance(
                        index = 0,
                        visible = showContent,
                        baseDelayMs = 500,
                        fromOffsetY = 60.dp
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        EditorSectionHeader(title = "Programmation", icon = "clock.circle")
                        Spacer(Modifier.weight(1f))
                        ModernToggle(
                            checked = hasTimeSchedule,
                            onCheckedChange = {
                                HapticFeedback.lightImpact()
                                viewModel.setHasTimeSchedule(it)
                            }
                        )
                    }

                    if (hasTimeSchedule) {
                        ModernCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Jours de la semaine",
                                    style = LuxTheme.type.subheadline.copy(fontWeight = FontWeight.Medium),
                                    color = colors.label
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    UserShortcut.TimeSchedule.Weekday.entries.forEach { day ->
                                        DayPickerButton(
                                            day = day,
                                            isSelected = selectedDays.contains(day),
                                            onTap = {
                                                HapticFeedback.lightImpact()
                                                viewModel.toggleDay(day)
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }

                                HorizontalDivider(
                                    thickness = 0.5.dp,
                                    color = colors.separator.copy(alpha = 0.5f)
                                )

                                Text(
                                    text = "Heure habituelle",
                                    style = LuxTheme.type.subheadline.copy(fontWeight = FontWeight.Medium),
                                    color = colors.label
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(WHEEL_ITEM_HEIGHT * WHEEL_VISIBLE_ITEMS)
                                ) {
                                    LuxWheelPicker(
                                        items = wheelHourLabels(),
                                        selectedIndex = selectedHour,
                                        onSelectedChange = { viewModel.updateTime(it, selectedMinute) },
                                        modifier = Modifier.weight(1f)
                                    )
                                    LuxWheelPicker(
                                        items = wheelMinuteLabels(),
                                        selectedIndex = selectedMinute,
                                        onSelectedChange = { viewModel.updateTime(selectedHour, it) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Text(
                                    text = "Lux vous suggérera ce raccourci à cette heure",
                                    style = LuxTheme.type.caption2,
                                    color = colors.secondaryLabel,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 12.dp)
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "En définissant une programmation, Lux vous suggérera le raccourci sur " +
                                "l'écran d'accueil, en fonction du moment de la journée.\nPar exemple, si " +
                                "vous utilisez un raccourci tous les jours à 8h, Lux le mettra en avant " +
                                "autour de cette heure.",
                            style = LuxTheme.type.footnote,
                            color = colors.secondaryLabel
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun EditorSectionHeader(title: String, icon: String, modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        SFSymbol(name = icon, size = 12.sp, color = LuxTheme.accent)
        Text(
            text = title,
            style = LuxTheme.type.subheadline.copy(fontWeight = FontWeight.SemiBold),
            color = LuxTheme.colors.label
        )
    }
}

@Composable
internal fun LocationSearchSheet(
    onSelected: (SearchResult) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LuxTheme.colors
    val accent = LuxTheme.accent
    val viewModel = remember { LocationSearchViewModel() }
    val results by viewModel.searchResults.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val showMinCharacters by viewModel.showMinCharactersMessage.collectAsState()
    val location by LocationService.location.collectAsState()
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(location) {
        viewModel.userLocation = location?.let { it.latitude to it.longitude }
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(
        modifier
            .fillMaxSize()
            .background(colors.systemBackground)
            .imePadding()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 26.dp, bottom = 12.dp)
        ) {
            Text(
                text = "Destination",
                style = LuxTheme.type.headline,
                color = colors.label
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "Fermer",
                style = LuxTheme.type.body,
                color = colors.secondaryLabel,
                modifier = Modifier.scaleClickable(haptic = false) { onDismiss() }
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .background(colors.secondarySystemBackground, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            SFSymbol(name = "magnifyingglass", size = 15.sp, color = colors.secondaryLabel)
            Box(Modifier.weight(1f)) {
                BasicTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        viewModel.performSearch(it)
                    },
                    singleLine = true,
                    textStyle = LuxTheme.type.body.copy(color = colors.label),
                    cursorBrush = SolidColor(accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
                if (query.isEmpty()) {
                    Text(
                        text = "Rechercher une destination",
                        style = LuxTheme.type.body,
                        color = colors.tertiaryLabel
                    )
                }
            }
            if (query.isNotEmpty()) {
                Box(
                    Modifier.scaleClickable(haptic = false) {
                        query = ""
                        viewModel.clear()
                    }
                ) {
                    SFSymbol(name = "xmark.circle.fill", size = 15.sp, color = colors.tertiaryLabel)
                }
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = 12.dp, bottom = 40.dp)
        ) {
            if (location != null && query.isEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .scaleClickable {
                            viewModel.useCurrentLocation { result ->
                                if (result != null) onSelected(result)
                            }
                        }
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    SearchResultIcon(symbolName = "location.fill", color = accent)
                    Text(
                        text = "Position Actuelle",
                        style = LuxTheme.type.subheadline.copy(fontWeight = FontWeight.Medium),
                        color = colors.label
                    )
                }
            }

            if (showMinCharacters) {
                Text(
                    text = "Entrez au moins 3 caractères",
                    style = LuxTheme.type.footnote,
                    color = colors.secondaryLabel,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
            }

            results.forEachIndexed { index, result ->
                SearchResultRow(
                    result = result,
                    modifier = Modifier
                        .fillMaxWidth()
                        .scaleClickable {
                            scope.launch {
                                val resolved = runCatching { viewModel.resolve(result) }.getOrDefault(result)
                                onSelected(resolved)
                            }
                        }
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                )
                if (index < results.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 74.dp),
                        thickness = 0.5.dp,
                        color = colors.separator
                    )
                }
            }

            if (isLoading && results.isEmpty()) {
                Text(
                    text = "Recherche...",
                    style = LuxTheme.type.footnote,
                    color = colors.secondaryLabel,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
            }
        }
    }
}

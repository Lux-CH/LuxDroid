package ch.cclerc.luxapp.ui.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import ch.cclerc.luxapp.core.HapticFeedback
import ch.cclerc.luxapp.core.SFSymbol
import ch.cclerc.luxapp.data.Settings
import ch.cclerc.luxapp.domain.shortcut.ShortcutManager
import ch.cclerc.luxapp.domain.shortcut.UserShortcut
import ch.cclerc.luxapp.ui.anim.scaleClickable
import ch.cclerc.luxapp.ui.components.settings.SectionHeader
import ch.cclerc.luxapp.ui.components.settings.SettingsCard
import ch.cclerc.luxapp.ui.navigation.LocalSheetController
import ch.cclerc.luxapp.ui.navigation.LuxSheetRequest
import ch.cclerc.luxapp.ui.theme.LuxTheme
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val PLAIN_ROW_HEIGHT = 64.2.dp
private val SCHEDULED_ROW_HEIGHT = 85.5.dp

private fun rowHeight(shortcut: UserShortcut) =
    if (shortcut.timeSchedule == null) PLAIN_ROW_HEIGHT else SCHEDULED_ROW_HEIGHT

@Composable
fun ShortcutsListView(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LuxTheme.colors
    val accent = LuxTheme.accent
    val sheets = LocalSheetController.current
    val manager = ShortcutManager.shared
    val shortcuts by manager.shortcuts.collectAsState()
    val visibleShortcuts by manager.visibleShortcuts.collectAsState()
    val density = LocalDensity.current

    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    fun presentEditor(shortcut: UserShortcut?) {
        sheets.present(
            LuxSheetRequest(cornerRadius = 38.dp) {
                ShortcutEditorView(
                    shortcutToEdit = shortcut,
                    onDismiss = { sheets.dismiss() }
                )
            }
        )
    }

    SettingsSubScreen(title = "Raccourcis", onBack = onBack, modifier = modifier) {
        SettingsHeaderCard(
            icon = "link",
            title = "Raccourcis",
            subtitle = "Accès rapide à vos destinations",
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        SettingsCard(Modifier.padding(horizontal = 16.dp)) {
            SectionHeader(
                icon = "list.star",
                iconColor = colors.systemBlue,
                title = "Raccourcis",
                subtitle = if (shortcuts.isEmpty()) {
                    "Aucun raccourci configuré"
                } else {
                    "${visibleShortcuts.size}/${shortcuts.size} affichés"
                }
            )

            Column(Modifier.fillMaxWidth()) {
                shortcuts.forEachIndexed { index, shortcut ->
                    androidx.compose.runtime.key(shortcut.id) {
                        val isDragging = draggingIndex == index
                        Column(
                            Modifier
                                .zIndex(if (isDragging) 1f else 0f)
                                .graphicsLayer {
                                    if (isDragging) {
                                        translationY = dragOffset
                                        scaleX = 1.02f
                                        scaleY = 1.02f
                                    }
                                }
                        ) {
                            ShortcutRow(
                                shortcut = shortcut,
                                isDragging = isDragging,
                                onEdit = { presentEditor(shortcut) },
                                onDelete = { manager.deleteShortcut(shortcut.id) },
                                onDragStart = {
                                    draggingIndex = index
                                    dragOffset = 0f
                                    HapticFeedback.mediumImpact()
                                },
                                onDrag = { delta -> dragOffset += delta },
                                onDragEnd = {
                                    val stepPx = with(density) { rowHeight(shortcut).toPx() }
                                    val steps = (dragOffset / stepPx).roundToInt()
                                    val target = (index + steps).coerceIn(0, shortcuts.lastIndex)
                                    draggingIndex = -1
                                    dragOffset = 0f
                                    if (target != index) {
                                        HapticFeedback.lightImpact()
                                        manager.moveShortcut(
                                            fromIndex = index,
                                            toIndex = if (target > index) target + 1 else target
                                        )
                                    }
                                },
                                onDragCancel = {
                                    draggingIndex = -1
                                    dragOffset = 0f
                                }
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 25.dp),
                                thickness = 0.5.dp,
                                color = colors.separator
                            )
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .scaleClickable { presentEditor(null) }
                        .padding(horizontal = 25.dp, vertical = 14.dp)
                ) {
                    SFSymbol(name = "plus.circle.fill", size = 17.sp, color = accent)
                    Text(
                        text = "Ajouter un raccourci",
                        style = LuxTheme.type.body,
                        color = accent
                    )
                }
            }

            if (shortcuts.isNotEmpty()) {
                Text(
                    text = if (Settings.useTimeBasedRelevance) {
                        "Les raccourcis seront triés par pertinence temporelle. Les plus proches en " +
                            "termes d'horaire et de jour seront affichés en premier, sauf si vous êtes " +
                            "très proche de la destination."
                    } else {
                        "Les deux premiers raccourcis seront affichés sur l'écran d'accueil.\n" +
                            "Réalisez un appui prolongé sur un raccourci puis déplacez le pour le " +
                            "réordonner dans la liste."
                    },
                    style = LuxTheme.type.footnote,
                    color = colors.secondaryLabel,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun ShortcutRow(
    shortcut: UserShortcut,
    isDragging: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit
) {
    val colors = LuxTheme.colors
    val accent = LuxTheme.accent
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val deleteThreshold = with(density) { 110.dp.toPx() }
    var deleted by remember { mutableStateOf(false) }

    LaunchedEffect(isDragging) {
        if (isDragging && offsetX.value != 0f) offsetX.animateTo(0f)
    }

    Box(Modifier.fillMaxWidth().height(rowHeight(shortcut))) {
        Box(
            Modifier
                .fillMaxSize()
                .background(colors.systemRed.copy(alpha = 0.9f)),
            contentAlignment = Alignment.CenterEnd
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(end = 24.dp)
            ) {
                SFSymbol(name = "trash.fill", size = 16.sp, color = androidx.compose.ui.graphics.Color.White)
                Text(
                    text = "Supprimer",
                    style = LuxTheme.type.footnote.copy(fontWeight = FontWeight.SemiBold),
                    color = androidx.compose.ui.graphics.Color.White
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationX = offsetX.value }
                .background(colors.secondarySystemGroupedBackground)
                .pointerInput(shortcut.id) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { onDragStart() },
                        onDrag = { change, amount ->
                            change.consume()
                            onDrag(amount.y)
                        },
                        onDragEnd = { onDragEnd() },
                        onDragCancel = { onDragCancel() }
                    )
                }
                .pointerInput(shortcut.id) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            scope.launch {
                                offsetX.snapTo((offsetX.value + amount).coerceIn(-size.width.toFloat(), 0f))
                            }
                        },
                        onDragEnd = {
                            scope.launch {
                                if (abs(offsetX.value) > deleteThreshold && !deleted) {
                                    deleted = true
                                    HapticFeedback.mediumImpact()
                                    offsetX.animateTo(-size.width.toFloat())
                                    onDelete()
                                } else {
                                    offsetX.animateTo(0f)
                                }
                            }
                        },
                        onDragCancel = { scope.launch { offsetX.animateTo(0f) } }
                    )
                }
                .scaleClickable(haptic = false) { onEdit() }
                .padding(horizontal = 20.dp)
        ) {
            Box(Modifier.width(40.dp), contentAlignment = Alignment.CenterStart) {
                SFSymbol(name = shortcut.symbol, size = 20.sp, color = accent)
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = shortcut.name,
                    style = LuxTheme.type.body.copy(fontWeight = FontWeight.Medium),
                    color = colors.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = shortcut.coordinates.locationName,
                    style = LuxTheme.type.footnote,
                    color = colors.secondaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val schedule = shortcut.timeSchedule
                if (schedule != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        schedule.daysOfWeek.sortedBy { it.rawValue }.forEach { day ->
                            Text(
                                text = day.displayName,
                                style = LuxTheme.type.caption2,
                                color = accent,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(accent.copy(alpha = 0.2f))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = schedule.time.displayString,
                            style = LuxTheme.type.caption2,
                            color = colors.secondaryLabel
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                SFSymbol(name = "pencil", size = 16.sp, color = accent)
            }
        }
    }
}

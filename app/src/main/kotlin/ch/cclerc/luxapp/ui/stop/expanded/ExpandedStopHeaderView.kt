package ch.cclerc.luxapp.ui.stop.expanded

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import ch.cclerc.luxapp.core.HapticFeedback
import ch.cclerc.luxapp.core.SFSymbol
import ch.cclerc.luxapp.ui.anim.PlainIndication
import ch.cclerc.luxapp.ui.theme.LuxSprings
import ch.cclerc.luxapp.ui.theme.LuxTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

enum class BoardMode(val label: String) {
    Grouped("Groupé"),
    Chronological("Chronologique")
}

private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.FRENCH)
private val dateTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM HH:mm", Locale.FRENCH)

internal fun formattedBoardDate(date: Instant, zone: ZoneId = ZoneId.systemDefault()): String {
    val zoned = date.atZone(zone)
    val today = LocalDate.now(zone)
    return when (zoned.toLocalDate()) {
        today -> "Aujourd'hui " + timeFormatter.format(zoned)
        today.plusDays(1) -> "Demain, " + timeFormatter.format(zoned)
        else -> dateTimeFormatter.format(zoned)
    }
}

@Composable
private fun springProgress(
    visible: Boolean,
    delayMs: Long,
    spec: AnimationSpec<Float>
): State<Float> {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        if (visible) {
            if (progress.value < 1f && delayMs > 0) delay(delayMs)
            progress.animateTo(1f, spec)
        } else {
            progress.snapTo(0f)
        }
    }
    return progress.asState()
}

@Composable
fun ExpandedStopHeaderView(
    animateIn: Boolean,
    selectedDate: Instant,
    boardMode: BoardMode,
    onDateSelected: (Instant) -> Unit,
    onModeChanged: (BoardMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LuxTheme.colors
    var showDatePicker by remember { mutableStateOf(false) }

    val clockProgress by springProgress(animateIn, 100, LuxSprings.Emphatic)
    val titleProgress by animateFloatAsState(
        targetValue = if (animateIn) 1f else 0f,
        animationSpec = tween(durationMillis = 400, delayMillis = 200, easing = EaseOut),
        label = "header-title"
    )
    val buttonProgress by animateFloatAsState(
        targetValue = if (animateIn) 1f else 0f,
        animationSpec = tween(durationMillis = 400, delayMillis = 300, easing = EaseOut),
        label = "header-button"
    )
    val pickerProgress by animateFloatAsState(
        targetValue = if (animateIn) 1f else 0f,
        animationSpec = tween(durationMillis = 400, delayMillis = 350, easing = EaseOut),
        label = "header-picker"
    )
    val dividerProgress by animateFloatAsState(
        targetValue = if (animateIn) 1f else 0f,
        animationSpec = tween(durationMillis = 500, delayMillis = 400, easing = EaseOut),
        label = "header-divider"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 17.5.dp)
            .padding(horizontal = 25.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SFSymbol(
                name = "clock",
                size = 17.sp,
                color = colors.label,
                modifier = Modifier.graphicsLayer {
                    rotationZ = -45f * (1f - clockProgress)
                }
            )

            Text(
                text = "Horaires",
                style = LuxTheme.type.body,
                fontWeight = FontWeight.Bold,
                color = colors.label,
                modifier = Modifier.graphicsLayer {
                    alpha = titleProgress
                    translationX = -20.dp.toPx() * (1f - titleProgress)
                }
            )

            Box(Modifier.weight(1f))

            DatePickerButton(
                selectedDate = selectedDate,
                expanded = showDatePicker,
                onExpandedChange = { showDatePicker = it },
                onApply = { instant ->
                    showDatePicker = false
                    onDateSelected(instant)
                },
                modifier = Modifier.graphicsLayer {
                    alpha = buttonProgress
                    translationX = 20.dp.toPx() * (1f - buttonProgress)
                }
            )
        }

        CustomSegmentedPicker(
            selection = boardMode,
            onSelect = onModeChanged,
            modifier = Modifier.graphicsLayer {
                alpha = pickerProgress
                translationY = 10.dp.toPx() * (1f - pickerProgress)
            }
        )

        HorizontalDivider(
            modifier = Modifier.graphicsLayer {
                transformOrigin = TransformOrigin(0f, 0.5f)
                scaleX = dividerProgress
            },
            thickness = 0.5.dp,
            color = colors.separator
        )
    }
}

@Composable
private fun DatePickerButton(
    selectedDate: Instant,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onApply: (Instant) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LuxTheme.colors
    val accent = LuxTheme.accent
    var anchorHeight by remember { mutableStateOf(0) }

    Box(modifier) {
        Row(
            modifier = Modifier
                .onSizeChanged { anchorHeight = it.height }
                .background(accent.copy(alpha = 0.1f), RoundedCornerShape(50))
                .clickable(
                    interactionSource = null,
                    indication = PlainIndication
                ) {
                    HapticFeedback.lightImpact()
                    onExpandedChange(true)
                }
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formattedBoardDate(selectedDate),
                style = LuxTheme.type.footnote,
                color = colors.label
            )
            SFSymbol(name = "calendar", size = 13.sp, color = colors.label)
        }

        if (expanded) {
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(0, anchorHeight + 8),
                onDismissRequest = { onExpandedChange(false) },
                properties = PopupProperties(focusable = true)
            ) {
                DateTimePickerView(
                    initialDate = selectedDate,
                    onCancel = { onExpandedChange(false) },
                    onApply = onApply
                )
            }
        }
    }
}

@Composable
fun CustomSegmentedPicker(
    selection: BoardMode,
    onSelect: (BoardMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LuxTheme.colors
    val accent = LuxTheme.accent
    val density = LocalDensity.current
    val options = remember { BoardMode.entries.toList() }
    var rects by remember { mutableStateOf(mapOf<BoardMode, Pair<Float, Float>>()) }

    val target = rects[selection]
    val pillX by animateFloatAsState(
        targetValue = target?.first ?: 0f,
        animationSpec = LuxSprings.Snappy,
        label = "segment-x"
    )
    val pillWidth by animateFloatAsState(
        targetValue = target?.second ?: 0f,
        animationSpec = LuxSprings.Snappy,
        label = "segment-width"
    )

    Box(
        modifier
            .fillMaxWidth()
            .height(28.dp)
            .border(
                width = if (LuxTheme.isDark) 0.5.dp else 0.75.dp,
                color = accent.copy(alpha = 0.2f),
                shape = RoundedCornerShape(50)
            )
            .padding(2.dp)
    ) {
        if (target != null && pillWidth > 0f) {
            Box(
                Modifier
                    .offset { IntOffset(pillX.roundToInt(), 0) }
                    .width(with(density) { pillWidth.toDp() })
                    .fillMaxHeight()
                    .background(accent.copy(alpha = 0.12f), RoundedCornerShape(50))
            )
        }

        Row(Modifier.fillMaxSize()) {
            options.forEach { option ->
                val selected = option == selection
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .onGloballyPositioned { coordinates ->
                            val position = coordinates.positionInParent().x
                            val width = coordinates.size.width.toFloat()
                            val existing = rects[option]
                            if (existing?.first != position || existing.second != width) {
                                rects = rects + (option to (position to width))
                            }
                        }
                        .clickable(
                            interactionSource = null,
                            indication = PlainIndication
                        ) {
                            HapticFeedback.softImpact()
                            onSelect(option)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = option.label,
                        style = LuxTheme.type.footnote,
                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                        color = if (selected) accent else colors.secondaryLabel
                    )
                }
            }
        }
    }
}

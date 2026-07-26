package ch.cclerc.luxapp.ui.stop.expanded

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.cclerc.luxapp.core.HapticFeedback
import ch.cclerc.luxapp.core.SFSymbol
import ch.cclerc.luxapp.ui.anim.PlainIndication
import ch.cclerc.luxapp.ui.theme.LuxShapes
import ch.cclerc.luxapp.ui.theme.LuxTheme
import ch.cclerc.luxapp.ui.theme.iosShadow
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.flow.distinctUntilChanged

internal val WHEEL_ITEM_HEIGHT = 34.dp
internal const val WHEEL_VISIBLE_ITEMS = 5
internal const val DAYS_BEFORE = 7
internal const val DAYS_AFTER = 365

private val wheelDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE d MMM", Locale.FRENCH)

internal fun wheelDates(zone: ZoneId): List<LocalDate> {
    val base = LocalDate.now(zone).minusDays(DAYS_BEFORE.toLong())
    return List(DAYS_BEFORE + DAYS_AFTER + 1) { base.plusDays(it.toLong()) }
}

internal fun wheelDateLabels(dates: List<LocalDate>, zone: ZoneId): List<String> {
    val today = LocalDate.now(zone)
    return dates.map { date ->
        when (date) {
            today -> "Aujourd'hui"
            today.plusDays(1) -> "Demain"
            else -> wheelDateFormatter.format(date)
        }
    }
}

internal fun wheelHourLabels(): List<String> = List(24) { String.format(Locale.ROOT, "%02d", it) }

internal fun wheelMinuteLabels(): List<String> = List(60) { String.format(Locale.ROOT, "%02d", it) }

@Composable
fun DateTimePickerView(
    initialDate: Instant,
    onCancel: () -> Unit,
    onApply: (Instant) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LuxTheme.colors
    val accent = LuxTheme.accent
    val zone = remember { ZoneId.systemDefault() }

    val dates = remember(zone) { wheelDates(zone) }
    val dateLabels = remember(dates) { wheelDateLabels(dates, zone) }
    val hourLabels = remember { wheelHourLabels() }
    val minuteLabels = remember { wheelMinuteLabels() }

    val initialLocal = remember(initialDate) { initialDate.atZone(zone).toLocalDateTime() }

    var dateIndex by remember {
        mutableIntStateOf(
            dates.indexOf(initialLocal.toLocalDate()).coerceAtLeast(DAYS_BEFORE)
        )
    }
    var hourIndex by remember { mutableIntStateOf(initialLocal.hour) }
    var minuteIndex by remember { mutableIntStateOf(initialLocal.minute) }

    fun composed(): Instant = LocalDateTime.of(
        dates[dateIndex.coerceIn(dates.indices)],
        LocalTime.of(hourIndex.coerceIn(0, 23), minuteIndex.coerceIn(0, 59))
    ).atZone(zone).toInstant()

    Column(
        modifier = modifier
            .size(width = 350.dp, height = 332.5.dp)
            .iosShadow(
                color = Color.Black.copy(alpha = 0.1f),
                blurRadius = 10.dp,
                offsetY = 5.dp,
                shape = RoundedCornerShape(LuxShapes.r12)
            )
            .background(colors.secondarySystemBackgroundElevated, RoundedCornerShape(LuxShapes.r12)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .padding(horizontal = 16.dp)
                .height(WHEEL_ITEM_HEIGHT * WHEEL_VISIBLE_ITEMS)
        ) {
            LuxWheelPicker(
                items = dateLabels,
                selectedIndex = dateIndex,
                onSelectedChange = { dateIndex = it },
                modifier = Modifier.weight(2f)
            )
            LuxWheelPicker(
                items = hourLabels,
                selectedIndex = hourIndex,
                onSelectedChange = { hourIndex = it },
                modifier = Modifier.weight(1f)
            )
            LuxWheelPicker(
                items = minuteLabels,
                selectedIndex = minuteIndex,
                onSelectedChange = { minuteIndex = it },
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier
                .padding(top = 4.dp)
                .background(accent.copy(alpha = 0.1f), RoundedCornerShape(LuxShapes.r8))
                .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(LuxShapes.r8))
                .clickable(interactionSource = null, indication = PlainIndication) {
                    HapticFeedback.lightImpact()
                    val now = LocalDateTime.now(zone)
                    dateIndex = dates.indexOf(now.toLocalDate()).coerceAtLeast(0)
                    hourIndex = now.hour
                    minuteIndex = now.minute
                }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SFSymbol(name = "clock.arrow.circlepath", size = 13.sp, color = accent)
            Text(text = "Maintenant", style = LuxTheme.type.footnote, color = accent)
        }

        Spacer(Modifier.weight(1f))

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            thickness = 0.5.dp,
            color = colors.tertiaryLabel
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Annuler",
                style = LuxTheme.type.body,
                color = colors.tertiaryLabel,
                modifier = Modifier
                    .clickable(interactionSource = null, indication = PlainIndication) {
                        onCancel()
                    }
                    .padding(vertical = 4.dp)
            )

            Spacer(Modifier.weight(1f))

            Text(
                text = "Appliquer",
                style = LuxTheme.type.body,
                fontWeight = FontWeight.Bold,
                color = accent,
                modifier = Modifier
                    .clickable(interactionSource = null, indication = PlainIndication) {
                        HapticFeedback.mediumImpact()
                        onApply(composed())
                    }
                    .padding(vertical = 4.dp)
            )
        }
    }
}

@Composable
internal fun LuxWheelPicker(
    items: List<String>,
    selectedIndex: Int,
    onSelectedChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return
    val colors = LuxTheme.colors
    val density = LocalDensity.current
    val itemHeightPx = with(density) { WHEEL_ITEM_HEIGHT.toPx() }
    val padCount = (WHEEL_VISIBLE_ITEMS - 1) / 2

    val state: LazyListState = rememberLazyListState(
        initialFirstVisibleItemIndex = selectedIndex.coerceIn(items.indices)
    )
    val fling = rememberSnapFlingBehavior(lazyListState = state)

    val centerIndex by remember {
        derivedStateOf {
            val offsetSteps = if (state.firstVisibleItemScrollOffset > itemHeightPx / 2f) 1 else 0
            (state.firstVisibleItemIndex + offsetSteps).coerceIn(0, items.size - 1)
        }
    }

    LaunchedEffect(state, items.size) {
        snapshotFlow { state.isScrollInProgress to centerIndex }
            .distinctUntilChanged()
            .collect { (scrolling, index) ->
                if (!scrolling && index != selectedIndex) onSelectedChange(index)
            }
    }

    LaunchedEffect(state) {
        var last = centerIndex
        snapshotFlow { centerIndex }.collect { index ->
            if (index != last && state.isScrollInProgress) {
                HapticFeedback.selectionChanged()
            }
            last = index
        }
    }

    LaunchedEffect(selectedIndex) {
        if (!state.isScrollInProgress && centerIndex != selectedIndex) {
            state.animateScrollToItem(selectedIndex.coerceIn(items.indices))
        }
    }

    Box(modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(WHEEL_ITEM_HEIGHT)
                .background(colors.tertiarySystemFill, RoundedCornerShape(LuxShapes.r8))
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = state,
            flingBehavior = fling
        ) {
            items(padCount) { Spacer(Modifier.height(WHEEL_ITEM_HEIGHT)) }
            items(items.size) { index ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(WHEEL_ITEM_HEIGHT)
                        .graphicsLayer {
                            val f = (index - state.firstVisibleItemIndex) -
                                state.firstVisibleItemScrollOffset / itemHeightPx
                            val half = (WHEEL_VISIBLE_ITEMS - 1) / 2f
                            val t = (f / (half + 0.5f)).coerceIn(-1f, 1f)
                            rotationX = -t * 52f
                            cameraDistance = 14f * density.density
                            translationY = -t * t * t * itemHeightPx * 0.28f
                            val fade = kotlin.math.cos(t * 1.35f).coerceAtLeast(0f)
                            alpha = 0.18f + 0.82f * fade * fade
                            scaleX = 0.88f + 0.12f * fade
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val distance = abs(index - centerIndex)
                    Text(
                        text = items[index],
                        style = LuxTheme.type.body,
                        fontWeight = if (distance == 0) FontWeight.SemiBold else FontWeight.Normal,
                        color = colors.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            items(padCount) { Spacer(Modifier.height(WHEEL_ITEM_HEIGHT)) }
        }
    }
}

package ch.cclerc.luxapp.ui.trips

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.cclerc.luxapp.core.HapticFeedback
import ch.cclerc.luxapp.core.SFSymbol
import ch.cclerc.luxapp.ui.anim.PlainIndication
import ch.cclerc.luxapp.ui.stop.expanded.LuxWheelPicker
import ch.cclerc.luxapp.ui.stop.expanded.WHEEL_ITEM_HEIGHT
import ch.cclerc.luxapp.ui.stop.expanded.WHEEL_VISIBLE_ITEMS
import ch.cclerc.luxapp.ui.stop.expanded.wheelDateLabels
import ch.cclerc.luxapp.ui.stop.expanded.wheelDates
import ch.cclerc.luxapp.ui.stop.expanded.wheelHourLabels
import ch.cclerc.luxapp.ui.stop.expanded.wheelMinuteLabels
import ch.cclerc.luxapp.ui.theme.LuxShapes
import ch.cclerc.luxapp.ui.theme.LuxSprings
import ch.cclerc.luxapp.ui.theme.LuxTheme
import ch.cclerc.luxapp.ui.theme.iosShadow
import ch.cclerc.luxapp.viewmodel.DepartureType
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

@Composable
fun TripsSearchTimePickerView(
    selectedDate: Instant?,
    departureType: DepartureType,
    onDepartureTypeChange: (DepartureType) -> Unit,
    onDismiss: () -> Unit,
    onApply: (Instant?) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LuxTheme.colors
    val accent = LuxTheme.accent
    val shape = RoundedCornerShape(LuxShapes.r16)
    val zone = remember { ZoneId.systemDefault() }

    val dates = remember(zone) { wheelDates(zone) }
    val dateLabels = remember(dates) { wheelDateLabels(dates, zone) }
    val hourLabels = remember { wheelHourLabels() }
    val minuteLabels = remember { wheelMinuteLabels() }

    val initialLocal = remember { (selectedDate ?: Instant.now()).atZone(zone).toLocalDateTime() }

    var dateIndex by remember {
        mutableIntStateOf(dates.indexOf(initialLocal.toLocalDate()).coerceAtLeast(0))
    }
    var hourIndex by remember { mutableIntStateOf(initialLocal.hour) }
    var minuteIndex by remember { mutableIntStateOf(initialLocal.minute) }

    var hapticArmed by remember { mutableStateOf(false) }
    LaunchedEffect(dateIndex, hourIndex, minuteIndex) {
        if (hapticArmed) HapticFeedback.selectionChanged() else hapticArmed = true
    }

    fun composed(): Instant = LocalDateTime.of(
        dates[dateIndex.coerceIn(dates.indices)],
        LocalTime.of(hourIndex.coerceIn(0, 23), minuteIndex.coerceIn(0, 59))
    ).atZone(zone).toInstant()

    Column(
        modifier = modifier
            .width(350.dp)
            .iosShadow(
                color = Color.Black.copy(alpha = 0.15f),
                blurRadius = 10.dp,
                offsetY = 5.dp,
                shape = shape
            )
            .background(colors.secondarySystemBackgroundElevated, shape),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        DepartureTypeSegmentedPicker(
            selection = departureType,
            onSelect = onDepartureTypeChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .padding(horizontal = 16.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
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

        if (departureType == DepartureType.LEAVE_AT) {
            Row(
                modifier = Modifier
                    .offset(y = (-8).dp)
                    .background(accent.copy(alpha = 0.1f), RoundedCornerShape(LuxShapes.r8))
                    .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(LuxShapes.r8))
                    .clickable(interactionSource = null, indication = PlainIndication) {
                        HapticFeedback.lightImpact()
                        onApply(null)
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SFSymbol(name = "clock.arrow.circlepath", size = 13.sp, color = accent)
                Text(text = "Maintenant", style = LuxTheme.type.footnote, color = accent)
            }
        }

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
                        HapticFeedback.lightImpact()
                        onDismiss()
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
private fun DepartureTypeSegmentedPicker(
    selection: DepartureType,
    onSelect: (DepartureType) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LuxTheme.colors
    val accent = LuxTheme.accent
    val trackShape = RoundedCornerShape(LuxShapes.r8)
    val thumbShape = RoundedCornerShape(7.dp)

    Row(
        modifier = modifier
            .height(32.dp)
            .background(colors.tertiarySystemFill, trackShape)
            .padding(2.dp)
    ) {
        DepartureType.entries.forEach { option ->
            val selected = option == selection
            val thumbColor by animateColorAsState(
                targetValue = if (selected) {
                    if (colors.isDark) colors.systemGray3 else Color.White
                } else {
                    Color.Transparent
                },
                animationSpec = LuxSprings.springFor(0.3, 0.7),
                label = "segmentThumb"
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(thumbColor, thumbShape)
                    .clickable(interactionSource = null, indication = PlainIndication) {
                        HapticFeedback.selectionChanged()
                        onSelect(option)
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = option.label,
                    style = LuxTheme.type.footnote,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) accent else colors.secondaryLabel
                )
            }
        }
    }
}

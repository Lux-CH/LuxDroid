package ch.cclerc.luxapp.ui.components.settings

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.cclerc.luxapp.core.SFSymbol
import ch.cclerc.luxapp.ui.anim.scaleClickable
import ch.cclerc.luxapp.ui.theme.LuxSprings
import ch.cclerc.luxapp.ui.theme.LuxTheme
import ch.cclerc.luxapp.ui.theme.iosShadow

@Composable
fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LuxTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(colors.secondarySystemGroupedBackgroundElevated, shape)
            .border(0.5.dp, colors.hairline, shape)
    ) {
        content()
    }
}

@Composable
fun SettingsRow(
    icon: String,
    title: String,
    subtitle: String,
    showChevron: Boolean = true,
    external: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val colors = LuxTheme.colors
    val accent = LuxTheme.accent
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.scaleClickable { onClick() } else Modifier)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            SFSymbol(name = icon, size = 20.sp, color = accent)
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = LuxTheme.type.body.copy(fontWeight = FontWeight.Medium),
                color = colors.label
            )
            Text(
                text = subtitle,
                style = LuxTheme.type.caption,
                color = colors.secondaryLabel
            )
        }
        if (showChevron) {
            SFSymbol(
                name = if (external) "arrow.up.right.square" else "chevron.right",
                size = if (external) 13.sp else 12.sp,
                color = colors.secondaryLabel
            )
        }
    }
}

@Composable
fun ModernToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LuxTheme.colors
    val accent = LuxTheme.accent
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 10.dp else (-10).dp,
        animationSpec = LuxSprings.springFor<Dp>(0.3, 0.7),
        label = "toggleThumb"
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(50.dp, 30.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(interactionSource = null, indication = null) { onCheckedChange(!checked) }
            .background(if (checked) accent else colors.systemGray4)
    ) {
        Box(
            Modifier
                .offset(x = thumbOffset)
                .size(26.dp)
                .iosShadow(Color.Black.copy(alpha = 0.1f), 2.dp, 1.dp, CircleShape)
                .background(Color.White, CircleShape)
        )
    }
}

@Composable
fun SettingsToggle(
    icon: String,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LuxTheme.colors
    val accent = LuxTheme.accent
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            SFSymbol(name = icon, size = 20.sp, color = accent)
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = LuxTheme.type.body.copy(fontWeight = FontWeight.Medium),
                color = colors.label
            )
            Text(
                text = subtitle,
                style = LuxTheme.type.caption,
                color = colors.secondaryLabel
            )
        }
        ModernToggle(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun <T> SettingsPicker(
    icon: String,
    title: String,
    subtitle: String,
    selection: T,
    options: List<Pair<T, String>>,
    modifier: Modifier = Modifier,
    onSelectionChange: (T) -> Unit
) {
    val colors = LuxTheme.colors
    val accent = LuxTheme.accent
    var expanded by remember { mutableStateOf(false) }
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            SFSymbol(name = icon, size = 20.sp, color = accent)
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = LuxTheme.type.body.copy(fontWeight = FontWeight.Medium),
                color = colors.label
            )
            Text(
                text = subtitle,
                style = LuxTheme.type.caption,
                color = colors.secondaryLabel
            )
        }
        Box {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(interactionSource = null, indication = null) { expanded = true }
                    .padding(4.dp)
            ) {
                Text(
                    text = options.firstOrNull { it.first == selection }?.second ?: "",
                    style = LuxTheme.type.body,
                    color = accent
                )
                SFSymbol(name = "chevron.up.chevron.down", size = 12.sp, color = accent)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (value, label) ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = label,
                                style = LuxTheme.type.body,
                                color = colors.label
                            )
                        },
                        trailingIcon = if (value == selection) {
                            {
                                SFSymbol(name = "checkmark", size = 15.sp, color = accent)
                            }
                        } else null,
                        onClick = {
                            expanded = false
                            onSelectionChange(value)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SectionHeader(
    icon: String,
    iconColor: Color,
    title: String,
    subtitle: String,
    showInfoButton: Boolean = false,
    modifier: Modifier = Modifier,
    onInfo: (() -> Unit)? = null
) {
    val colors = LuxTheme.colors
    val accent = LuxTheme.accent
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp)
    ) {
        Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
            SFSymbol(name = icon, size = 12.sp, color = iconColor)
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(1.dp),
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title.uppercase(),
                style = LuxTheme.type.caption.copy(fontWeight = FontWeight.Black),
                color = colors.secondaryLabel
            )
            Text(
                text = subtitle,
                style = LuxTheme.type.caption2,
                color = colors.secondaryLabel
            )
        }
        if (showInfoButton) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .scaleClickable(haptic = false) { onInfo?.invoke() }
            ) {
                SFSymbol(name = "info.circle", size = 16.sp, color = accent)
            }
        }
    }
}

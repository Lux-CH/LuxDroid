package ch.cclerc.luxapp.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.cclerc.luxapp.core.SFSymbol
import ch.cclerc.luxapp.ui.anim.scaleClickable
import ch.cclerc.luxapp.ui.theme.LuxTheme

data class KeyboardToolbarShortcut(val symbol: String, val name: String)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KeyboardToolbar(
    shortcuts: List<KeyboardToolbarShortcut>,
    modifier: Modifier = Modifier,
    onCurrentPositionSelected: () -> Unit,
    onShortcutSelected: (Int) -> Unit
) {
    val imeVisible = WindowInsets.isImeVisible
    val density = LocalDensity.current
    val slidePx = with(density) { 100.dp.roundToPx() }
    Box(modifier.fillMaxWidth().imePadding()) {
        AnimatedVisibility(
            visible = imeVisible,
            enter = slideInVertically(
                animationSpec = tween(durationMillis = 300, easing = EaseOut),
                initialOffsetY = { slidePx }
            ) + fadeIn(tween(durationMillis = 300, easing = EaseOut)),
            exit = slideOutVertically(
                animationSpec = tween(durationMillis = 200, easing = EaseIn),
                targetOffsetY = { slidePx }
            ) + fadeOut(tween(durationMillis = 200, easing = EaseIn))
        ) {
            KeyboardToolbarBar(
                shortcuts = shortcuts,
                onCurrentPositionSelected = onCurrentPositionSelected,
                onShortcutSelected = onShortcutSelected
            )
        }
    }
}

@Composable
private fun KeyboardToolbarBar(
    shortcuts: List<KeyboardToolbarShortcut>,
    onCurrentPositionSelected: () -> Unit,
    onShortcutSelected: (Int) -> Unit
) {
    val colors = LuxTheme.colors
    val accent = LuxTheme.accent
    val dark = LuxTheme.isDark
    val background = if (dark) Color(0xFF2F2F30) else Color(0xFFCFD3D9)
    val shortcutFill = if (dark) {
        colors.systemFill.copy(alpha = colors.systemFill.alpha * 0.8f)
    } else {
        colors.secondarySystemBackground
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(background)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            ToolbarChip(
                symbol = "location.fill",
                label = "Position actuelle",
                fill = accent.copy(alpha = 0.15f),
                onClick = onCurrentPositionSelected
            )
            Box(
                Modifier
                    .width(1.dp)
                    .height(24.dp)
                    .background(colors.systemGray.copy(alpha = 0.3f))
            )
            shortcuts.forEachIndexed { index, shortcut ->
                ToolbarChip(
                    symbol = shortcut.symbol,
                    label = shortcut.name,
                    fill = shortcutFill,
                    onClick = { onShortcutSelected(index) }
                )
            }
        }
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(0.5.dp)
                .background(colors.systemGray.copy(alpha = 0.3f))
        )
    }
}

@Composable
private fun ToolbarChip(
    symbol: String,
    label: String,
    fill: Color,
    onClick: () -> Unit
) {
    val colors = LuxTheme.colors
    val accent = LuxTheme.accent
    val shape = RoundedCornerShape(10.dp)
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(shape)
            .scaleClickable { onClick() }
            .background(fill, shape)
            .border(0.5.dp, colors.hairline, shape)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .height(16.dp)
    ) {
        Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
            SFSymbol(name = symbol, size = 14.sp, color = accent)
        }
        Text(
            text = label,
            style = LuxTheme.type.footnote.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
            color = colors.label,
            maxLines = 1
        )
    }
}

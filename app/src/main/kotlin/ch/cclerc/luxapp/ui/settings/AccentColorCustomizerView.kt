package ch.cclerc.luxapp.ui.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.cclerc.luxapp.core.HapticFeedback
import ch.cclerc.luxapp.core.SFSymbol
import ch.cclerc.luxapp.ui.anim.scaleClickable
import ch.cclerc.luxapp.ui.anim.staggeredEntrance
import ch.cclerc.luxapp.ui.theme.AccentColorManager
import ch.cclerc.luxapp.ui.theme.LuxSprings
import ch.cclerc.luxapp.ui.theme.LuxTheme
import kotlinx.coroutines.delay

private val darkAccentIds = setOf("sbb-blue", "jura-brown")

@Composable
fun AccentColorCustomizerView(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LuxTheme.colors
    val accent = LuxTheme.accent
    val isDark = LuxTheme.isDark
    val selected by AccentColorManager.selectedAccent
    var headerScale by remember { mutableFloatStateOf(1f) }
    var showDarkColorAlert by remember { mutableStateOf(false) }
    var appeared by remember { mutableStateOf(false) }

    val animatedHeaderScale by animateFloatAsState(
        targetValue = headerScale,
        animationSpec = LuxSprings.springFor(0.6, 0.8),
        label = "accentHeaderScale"
    )

    LaunchedEffect(Unit) { appeared = true }
    LaunchedEffect(headerScale) {
        if (headerScale > 1f) {
            delay(100)
            headerScale = 1f
        }
    }

    SettingsSubScreen(title = "Couleur de l'app", onBack = onBack, modifier = modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 15.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = animatedHeaderScale
                        scaleY = animatedHeaderScale
                    }
                    .size(65.dp)
                    .background(accent, CircleShape)
            ) {
                SFSymbol(name = "paintpalette", size = 24.sp, color = Color.White)
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 15.dp)
            ) {
                Text(
                    text = "Couleur de l'app",
                    style = LuxTheme.type.title,
                    color = colors.label,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Séléctionnez la couleur de l'application et de l'icône",
                    style = LuxTheme.type.subheadline,
                    color = colors.secondaryLabel,
                    textAlign = TextAlign.Center
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        ) {
            Text(
                text = "Couleurs disponibles",
                style = LuxTheme.type.headline,
                color = colors.label
            )
            Spacer(Modifier.weight(1f))
            if (isDark && selected.id in darkAccentIds) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .background(colors.secondaryLabel.copy(alpha = 0.1f))
                        .scaleClickable(haptic = false) { showDarkColorAlert = true }
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    SFSymbol(name = "exclamationmark.triangle", size = 12.sp, color = colors.systemRed)
                    Text(
                        text = "Couleur Sombre",
                        style = LuxTheme.type.caption,
                        color = colors.systemRed
                    )
                }
            }
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 20.dp)
        ) {
            AccentColorManager.availableColors.chunked(3).forEachIndexed { rowIndex, rowColors ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    rowColors.forEachIndexed { columnIndex, option ->
                        val index = rowIndex * 3 + columnIndex
                        ModernColorOptionView(
                            color = option.resolved(isDark),
                            name = option.displayName,
                            isSelected = option.id == selected.id,
                            index = index,
                            visible = appeared,
                            modifier = Modifier.weight(1f)
                        ) {
                            AccentColorManager.select(option)
                            headerScale = 1.2f
                            HapticFeedback.lightImpact()
                        }
                    }
                    repeat(3 - rowColors.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }

    if (showDarkColorAlert) {
        AlertDialog(
            onDismissRequest = { showDarkColorAlert = false },
            title = { Text("Couleur Sombre") },
            text = {
                Text(
                    "La couleur séléctionnée n'est pas bien visible en mode sombre. Pensez à passer " +
                        "en mode clair dans les paramètres \"Mode d'Affichage\""
                )
            },
            confirmButton = {
                TextButton(onClick = { showDarkColorAlert = false }) { Text("OK") }
            }
        )
    }
}

@Composable
private fun ModernColorOptionView(
    color: Color,
    name: String,
    isSelected: Boolean,
    index: Int,
    visible: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = LuxTheme.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.staggeredEntrance(
            index = index,
            visible = visible,
            delayPerItemMs = 50,
            fromOffsetY = 20.dp
        )
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(84.dp)
        ) {
            if (isSelected) {
                Box(
                    Modifier
                        .size(84.dp)
                        .border(4.dp, color, CircleShape)
                        .border(1.dp, colors.hairline, CircleShape)
                )
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(70.dp)
                    .background(color, CircleShape)
                    .border(
                        width = if (isSelected) 0.dp else 0.5.dp,
                        color = colors.hairline,
                        shape = CircleShape
                    )
                    .scaleClickable(haptic = false, onClick = onClick)
            ) {
                if (isSelected) {
                    SFSymbol(name = "checkmark", size = 20.sp, color = Color.White, weight = 700)
                }
            }
        }
        Text(
            text = name,
            style = LuxTheme.type.caption.copy(
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
            ),
            color = if (isSelected) color else colors.secondaryLabel,
            textAlign = TextAlign.Center
        )
    }
}

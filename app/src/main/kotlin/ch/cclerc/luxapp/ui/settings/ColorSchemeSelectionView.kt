package ch.cclerc.luxapp.ui.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.cclerc.luxapp.core.HapticFeedback
import ch.cclerc.luxapp.core.SFSymbol
import ch.cclerc.luxapp.data.Settings
import ch.cclerc.luxapp.ui.anim.scaleClickable
import ch.cclerc.luxapp.ui.theme.LuxTheme

private data class ThemeOption(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: String,
    val color: Color,
    val isDefault: Boolean = false
)

@Composable
fun ColorSchemeSelectionView(
    onBack: () -> Unit,
    onPicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LuxTheme.colors
    val accent = LuxTheme.accent

    val themeOptions = listOf(
        ThemeOption("system", "Système", "Suit les réglages système", "iphone", accent, isDefault = true),
        ThemeOption("automatic", "Automatique", "Basé sur l'heure", "clock.arrow.2.circlepath", colors.systemPurple),
        ThemeOption("light", "Clair", "Toujours en mode clair", "sun.max", colors.systemYellow),
        ThemeOption("dark", "Sombre", "Toujours en mode sombre", "moon", colors.systemIndigo)
    )

    fun isSelected(option: ThemeOption): Boolean = when (option.id) {
        "system" -> !Settings.autoColorScheme && !Settings.customScheme
        "automatic" -> Settings.autoColorScheme
        "light" -> Settings.customScheme && Settings.customSchemeSelection == "light"
        "dark" -> Settings.customScheme && Settings.customSchemeSelection == "dark"
        else -> false
    }

    fun selectTheme(option: ThemeOption) {
        when (option.id) {
            "system" -> {
                Settings.autoColorScheme = false
                Settings.customScheme = false
                Settings.customSchemeSelection = ""
            }
            "automatic" -> {
                Settings.autoColorScheme = true
                Settings.customScheme = false
                Settings.customSchemeSelection = ""
            }
            "light" -> {
                Settings.autoColorScheme = false
                Settings.customScheme = true
                Settings.customSchemeSelection = "light"
            }
            "dark" -> {
                Settings.autoColorScheme = false
                Settings.customScheme = true
                Settings.customSchemeSelection = "dark"
            }
        }
        HapticFeedback.softImpact()
        onPicked()
    }

    SettingsSubScreen(title = "Mode d'affichage", onBack = onBack, modifier = modifier) {
        SettingsHeaderCard(
            icon = "circle.lefthalf.filled",
            title = "Mode d'affichage",
            subtitle = "Choisissez le mode d'affichage de l'app",
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
        ) {
            themeOptions.forEach { option ->
                ThemeSelectionCard(
                    option = option,
                    isSelected = isSelected(option),
                    onClick = { selectTheme(option) }
                )
            }
        }
    }
}

@Composable
private fun ThemeSelectionCard(
    option: ThemeOption,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LuxTheme.colors
    val shape = RoundedCornerShape(16.dp)
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.01f else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "themeCardScale"
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(colors.secondarySystemGroupedBackground, shape)
            .border(if (isSelected) 2.dp else 0.5.dp, if (isSelected) option.color else colors.hairline, shape)
            .scaleClickable(haptic = false, onClick = onClick)
            .padding(16.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(50.dp)
                .background(
                    Brush.linearGradient(
                        if (isSelected) {
                            listOf(option.color, option.color.copy(alpha = 0.7f))
                        } else {
                            listOf(option.color.copy(alpha = 0.3f), option.color.copy(alpha = 0.1f))
                        }
                    ),
                    CircleShape
                )
        ) {
            SFSymbol(
                name = option.icon,
                size = 20.sp,
                color = if (isSelected) Color.White else option.color
            )
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = option.title,
                    style = LuxTheme.type.headline,
                    color = colors.label
                )
                if (option.isDefault) {
                    Text(
                        text = "Par Défaut",
                        style = LuxTheme.type.caption,
                        color = colors.secondaryLabel,
                        modifier = Modifier
                            .clip(RoundedCornerShape(percent = 50))
                            .background(colors.tertiarySystemFill)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Text(
                text = option.subtitle,
                style = LuxTheme.type.subheadline,
                color = colors.secondaryLabel
            )
        }

        if (isSelected) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(24.dp)
                    .background(option.color, CircleShape)
            ) {
                SFSymbol(name = "checkmark", size = 12.sp, color = Color.White, weight = 700)
            }
        } else {
            Spacer(Modifier.size(0.dp))
        }
    }
}

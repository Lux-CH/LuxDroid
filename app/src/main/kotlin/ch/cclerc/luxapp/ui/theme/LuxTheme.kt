package ch.cclerc.luxapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import ch.cclerc.luxapp.data.Settings
import java.util.Calendar
import kotlinx.coroutines.delay

val LocalLuxColors = staticCompositionLocalOf { LuxColors.light() }
val LocalLuxTypography = staticCompositionLocalOf { LuxTypography }
val LocalLuxAccent = staticCompositionLocalOf { Color(0xFFFF9500) }
val LocalLuxDarkness = staticCompositionLocalOf { false }

object LuxTheme {
    val colors: LuxColors
        @Composable @ReadOnlyComposable get() = LocalLuxColors.current
    val type: LuxTypography
        @Composable @ReadOnlyComposable get() = LocalLuxTypography.current
    val accent: Color
        @Composable @ReadOnlyComposable get() = LocalLuxAccent.current
    val isDark: Boolean
        @Composable @ReadOnlyComposable get() = LocalLuxDarkness.current
}

@Composable
fun LuxTheme(content: @Composable () -> Unit) {
    val dark = resolveDarkness()
    val colors = remember(dark) { if (dark) LuxColors.dark() else LuxColors.light() }
    val accentOption by AccentColorManager.selectedAccent
    val accent = accentOption.resolved(dark)
    val materialScheme = remember(dark, accent) {
        if (dark) darkColorScheme(
            primary = accent,
            secondary = accent,
            tertiary = accent,
            background = colors.systemBackground,
            onBackground = colors.label,
            surface = colors.systemBackground,
            onSurface = colors.label,
            surfaceVariant = colors.secondarySystemBackground,
            onSurfaceVariant = colors.secondaryLabel,
            outline = colors.separator,
            error = colors.systemRed
        ) else lightColorScheme(
            primary = accent,
            secondary = accent,
            tertiary = accent,
            background = colors.systemBackground,
            onBackground = colors.label,
            surface = colors.systemBackground,
            onSurface = colors.label,
            surfaceVariant = colors.secondarySystemBackground,
            onSurfaceVariant = colors.secondaryLabel,
            outline = colors.separator,
            error = colors.systemRed
        )
    }
    CompositionLocalProvider(
        LocalLuxColors provides colors,
        LocalLuxAccent provides accent,
        LocalLuxDarkness provides dark
    ) {
        MaterialTheme(colorScheme = materialScheme, content = content)
    }
}

@Composable
private fun resolveDarkness(): Boolean = when {
    Settings.autoColorScheme -> {
        val hour = observedHour()
        hour >= 20 || hour < 6
    }
    Settings.customScheme -> Settings.customSchemeSelection == "dark"
    else -> isSystemInDarkTheme()
}

@Composable
private fun observedHour(): Int {
    var hour by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) {
        while (true) {
            val now = Calendar.getInstance()
            val millisIntoHour = now.get(Calendar.MINUTE) * 60_000L +
                now.get(Calendar.SECOND) * 1_000L +
                now.get(Calendar.MILLISECOND)
            delay(3_600_000L - millisIntoHour + 1_000L)
            hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        }
    }
    return hour
}

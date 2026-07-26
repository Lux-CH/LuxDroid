package ch.cclerc.luxapp.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object LuxMaterials {

    @Composable
    @ReadOnlyComposable
    fun ultraThin(): Color =
        if (LuxTheme.isDark) Color(0xFF252525).copy(alpha = 0.55f)
        else Color(0xFFF5F5F5).copy(alpha = 0.66f)

    @Composable
    @ReadOnlyComposable
    fun regular(): Color =
        if (LuxTheme.isDark) Color(0xFF1E1E1E).copy(alpha = 0.82f)
        else Color(0xFFF2F2F7).copy(alpha = 0.82f)

    @Composable
    @ReadOnlyComposable
    fun ultraThick(): Color =
        if (LuxTheme.isDark) Color(0xFF232323).copy(alpha = 0.95f)
        else Color(0xFFFAFAFA).copy(alpha = 0.95f)

    val capsuleStrokeWidth: Dp = 0.5.dp

    @Composable
    @ReadOnlyComposable
    fun capsuleFill(): Color {
        val fill = LuxTheme.colors.secondarySystemFill
        return fill.copy(alpha = fill.alpha * 0.5f)
    }

    @Composable
    @ReadOnlyComposable
    fun capsuleStroke(): Color = LuxTheme.colors.hairline

    fun tintedChipFill(accent: Color): Color = accent.copy(alpha = 0.12f)

    fun tintedChipStroke(accent: Color): Color = accent.copy(alpha = 0.35f)
}

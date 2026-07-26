package ch.cclerc.luxapp.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ch.cclerc.luxapp.ui.anim.PlainIndication
import ch.cclerc.luxapp.ui.theme.LuxSprings
import ch.cclerc.luxapp.ui.theme.LuxTheme
import ch.cclerc.luxapp.ui.theme.iosShadow

enum class ModernCardStyle { Normal, Accent, Subtle }

@Composable
fun ModernCard(
    style: ModernCardStyle = ModernCardStyle.Normal,
    optionalColor: Color? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val colors = LuxTheme.colors
    val shape = RoundedCornerShape(16.dp)
    val backgroundColor = when (style) {
        ModernCardStyle.Normal -> colors.secondarySystemGroupedBackground
        ModernCardStyle.Accent -> (optionalColor ?: LuxTheme.accent).copy(alpha = 0.05f)
        ModernCardStyle.Subtle -> colors.tertiarySystemGroupedBackground
    }
    Box(
        modifier
            .background(backgroundColor, shape)
            .border(0.5.dp, colors.hairline, shape)
            .padding(16.dp)
    ) {
        content()
    }
}

@Composable
fun ModernToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val trackColor by animateColorAsState(
        if (checked) LuxTheme.accent else LuxTheme.colors.systemGray4,
        LuxSprings.springFor(0.3, 0.7)
    )
    val knobOffset by animateDpAsState(
        if (checked) 10.dp else (-10).dp,
        LuxSprings.springFor(0.3, 0.7)
    )
    Box(
        modifier
            .size(50.dp, 30.dp)
            .clickable(interactionSource = null, indication = PlainIndication) {
                onCheckedChange(!checked)
            }
            .background(trackColor, RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .offset(x = knobOffset)
                .size(26.dp)
                .iosShadow(
                    color = Color.Black.copy(alpha = 0.1f),
                    blurRadius = 2.dp,
                    offsetY = 1.dp,
                    shape = CircleShape
                )
                .background(Color.White, CircleShape)
        )
    }
}

@Composable
fun Modifier.luxTextField(): Modifier {
    val colors = LuxTheme.colors
    val shape = RoundedCornerShape(16.dp)
    return background(colors.secondarySystemGroupedBackground, shape)
        .border(0.5.dp, colors.hairline, shape)
        .padding(16.dp)
}

package ch.cclerc.luxapp.ui.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.cclerc.luxapp.domain.shortcut.UserShortcut
import ch.cclerc.luxapp.ui.anim.PlainIndication
import ch.cclerc.luxapp.ui.theme.LuxSprings
import ch.cclerc.luxapp.ui.theme.LuxTheme
import ch.cclerc.luxapp.ui.theme.iosShadow

@Composable
fun DayPickerButton(
    day: UserShortcut.TimeSchedule.Weekday,
    isSelected: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LuxTheme.colors
    val accent = LuxTheme.accent
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.1f else 1.0f,
        animationSpec = LuxSprings.springFor(0.4, 0.8),
        label = "dayPickerScale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.clickable(interactionSource = null, indication = PlainIndication) { onTap() }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .size(40.dp)
                .iosShadow(
                    color = if (isSelected) accent.copy(alpha = 0.3f) else Color.Transparent,
                    blurRadius = if (isSelected) 1.dp else 0.dp,
                    offsetY = 0.5.dp,
                    shape = CircleShape
                )
                .background(if (isSelected) accent else colors.quaternarySystemFill, CircleShape)
                .border(
                    width = 0.5.dp,
                    color = if (isSelected) colors.hairline else colors.separator.copy(alpha = 0.3f),
                    shape = CircleShape
                )
        ) {
            Text(
                text = day.shortDisplayName,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = if (isSelected) Color.White else colors.label
            )
        }
        Text(
            text = day.displayName,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = if (isSelected) accent else colors.secondaryLabel.copy(alpha = 0.8f)
        )
    }
}

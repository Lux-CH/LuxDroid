package ch.cclerc.luxapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.cclerc.luxapp.core.HapticFeedback
import ch.cclerc.luxapp.core.SFSymbol
import ch.cclerc.luxapp.data.Settings
import ch.cclerc.luxapp.ui.anim.scaleClickable
import ch.cclerc.luxapp.ui.theme.LuxTheme

private fun Modifier.capsuleStroke(
    shape: Shape,
    color: Color,
    width: Dp,
    dashed: Boolean
): Modifier = drawBehind {
    val sw = width.toPx()
    val inset = sw / 2f
    translate(inset, inset) {
        val outline = shape.createOutline(
            Size(size.width - sw, size.height - sw),
            layoutDirection,
            this
        )
        drawOutline(
            outline = outline,
            color = color,
            style = Stroke(
                width = sw,
                pathEffect = if (dashed) {
                    PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()))
                } else null
            )
        )
    }
}

@Composable
fun ShortcutButton(
    symbol: String,
    name: String? = null,
    isPlaceholder: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = LuxTheme.colors
    val accent = LuxTheme.accent
    val shape = RoundedCornerShape(percent = 50)
    val fillBase = colors.secondarySystemFill
    val fill = fillBase.copy(alpha = fillBase.alpha * if (isPlaceholder) 0.3f else 0.5f)
    val strokeColor = if (isPlaceholder) {
        colors.secondaryLabel.copy(alpha = colors.secondaryLabel.alpha * 0.3f)
    } else {
        colors.hairline
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .height(52.5.dp)
            .clip(shape)
            .scaleClickable(haptic = false) { onClick() }
            .background(fill, shape)
            .capsuleStroke(
                shape = shape,
                color = strokeColor,
                width = if (isPlaceholder) 2.dp else 0.5.dp,
                dashed = isPlaceholder
            )
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SFSymbol(
                name = symbol,
                size = 20.sp,
                color = if (isPlaceholder) accent.copy(alpha = 0.5f) else accent
            )
            if (name != null && !isPlaceholder && Settings.showShortcutLabel) {
                Text(
                    text = name,
                    style = LuxTheme.type.subheadline,
                    color = colors.label
                )
            }
        }
    }
}

@Composable
fun AddShortcutsButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = LuxTheme.colors
    val accent = LuxTheme.accent
    val shape = RoundedCornerShape(percent = 50)
    val fillBase = colors.secondarySystemFill
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .scaleClickable(haptic = false) { onClick() }
            .background(fillBase.copy(alpha = fillBase.alpha * 0.3f), shape)
            .capsuleStroke(
                shape = shape,
                color = colors.secondaryLabel.copy(alpha = colors.secondaryLabel.alpha * 0.3f),
                width = 2.dp,
                dashed = true
            )
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SFSymbol(name = "plus", size = 20.sp, color = accent.copy(alpha = 0.5f))
            Text(
                text = "Ajouter des raccourcis",
                style = LuxTheme.type.body,
                color = accent.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun SavedItineraryShortcutButton(
    timeLabel: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = LuxTheme.colors
    val green = colors.systemGreen
    val shape = RoundedCornerShape(percent = 50)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .height(52.5.dp)
            .clip(shape)
            .scaleClickable(haptic = false) {
                HapticFeedback.softImpact()
                onClick()
            }
            .background(green.copy(alpha = 0.16f), shape)
            .capsuleStroke(
                shape = shape,
                color = green.copy(alpha = 0.35f),
                width = 0.8.dp,
                dashed = false
            )
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SFSymbol(
                name = "calendar.badge.clock",
                size = 20.sp,
                color = green.copy(alpha = 0.85f)
            )
            if (Settings.showShortcutLabel) {
                Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        text = "Itinéraire",
                        style = LuxTheme.type.subheadline.copy(fontWeight = FontWeight.SemiBold),
                        color = green.copy(alpha = 0.85f)
                    )
                    Text(
                        text = timeLabel,
                        style = LuxTheme.type.caption2.copy(fontWeight = FontWeight.Medium),
                        color = green.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

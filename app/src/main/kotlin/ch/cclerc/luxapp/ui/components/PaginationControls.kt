package ch.cclerc.luxapp.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicText
import ch.cclerc.luxapp.core.SFSymbol
import ch.cclerc.luxapp.ui.anim.PlainIndication
import ch.cclerc.luxapp.ui.theme.InterFontFamily
import ch.cclerc.luxapp.ui.theme.LuxSprings
import ch.cclerc.luxapp.ui.theme.LuxTheme
import ch.cclerc.luxapp.ui.theme.iosShadow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PaginationControls(
    isLoadingEarlier: Boolean,
    isLoadingLater: Boolean,
    isChangingContent: Boolean,
    isLoading: Boolean,
    animateIn: Boolean,
    loadEarlier: () -> Unit,
    loadLater: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LuxTheme.colors
    val isDark = LuxTheme.isDark
    val accent = LuxTheme.accent
    val capsule = RoundedCornerShape(50)
    val entranceOffset = remember { Animatable(if (animateIn) -16f else 40f) }
    val entranceAlpha = remember { Animatable(if (animateIn) 1f else 0f) }
    LaunchedEffect(animateIn) {
        delay(300)
        launch { entranceOffset.animateTo(if (animateIn) -16f else 40f, LuxSprings.SlowEntrance) }
        launch { entranceAlpha.animateTo(if (animateIn) 1f else 0f, LuxSprings.SlowEntrance) }
    }
    val chromeScale by animateFloatAsState(if (isChangingContent) 0.98f else 1f, LuxSprings.Snappy)
    val disabled = isLoadingEarlier || isLoadingLater || isLoading || isChangingContent

    Box(
        modifier
            .padding(horizontal = 24.dp)
            .fillMaxWidth()
            .height(54.dp)
            .offset(y = entranceOffset.value.dp)
            .graphicsLayer { alpha = entranceAlpha.value }
    ) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    scaleX = chromeScale
                    scaleY = chromeScale
                }
                .iosShadow(
                    color = Color.Black.copy(alpha = if (isDark) 0.3f else 0.15f),
                    blurRadius = 7.5.dp,
                    offsetY = 5.dp,
                    shape = capsule
                )
                .background(if (isDark) colors.secondarySystemBackground else Color.White, capsule)
                .border(0.75.dp, if (isDark) colors.hairline else colors.hairlineGray, capsule)
        )
        Row(
            Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PaginationButton(
                label = "Plus tôt",
                icon = "arrow.backward",
                iconLeading = true,
                showSpinner = isLoadingEarlier,
                enabled = !disabled,
                accent = accent,
                spinnerColor = colors.secondaryLabel,
                onClick = loadEarlier
            )
            Spacer(Modifier.weight(1f))
            PaginationButton(
                label = "Plus tard",
                icon = "arrow.forward",
                iconLeading = false,
                showSpinner = isLoadingLater,
                enabled = !disabled,
                accent = accent,
                spinnerColor = colors.secondaryLabel,
                onClick = loadLater
            )
        }
    }
}

@Composable
private fun PaginationButton(
    label: String,
    icon: String,
    iconLeading: Boolean,
    showSpinner: Boolean,
    enabled: Boolean,
    accent: Color,
    spinnerColor: Color,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .height(44.dp)
            .clip(RoundedCornerShape(50))
            .clickable(
                interactionSource = null,
                indication = PlainIndication,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (iconLeading) {
            PaginationButtonIcon(icon, showSpinner, accent, spinnerColor)
        }
        BasicText(
            text = label,
            style = TextStyle(
                fontFamily = InterFontFamily,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = accent
            )
        )
        if (!iconLeading) {
            PaginationButtonIcon(icon, showSpinner, accent, spinnerColor)
        }
    }
}

@Composable
private fun PaginationButtonIcon(
    icon: String,
    showSpinner: Boolean,
    accent: Color,
    spinnerColor: Color
) {
    if (showSpinner) {
        LuxSpinner(color = spinnerColor, size = 14.dp)
    } else {
        SFSymbol(name = icon, size = 16.sp, color = accent, weight = 500)
    }
}

@Composable
private fun LuxSpinner(color: Color, size: Dp) {
    val transition = rememberInfiniteTransition()
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 800, easing = LinearEasing))
    )
    Canvas(Modifier.size(size)) {
        val tickCount = 8
        val activeTick = phase.toInt() % tickCount
        val radius = this.size.minDimension / 2f
        for (i in 0 until tickCount) {
            val relative = ((i - activeTick) + tickCount) % tickCount
            val tickAlpha = 0.25f + 0.75f * (1f - relative / tickCount.toFloat())
            rotate(degrees = i * 360f / tickCount) {
                drawLine(
                    color = color.copy(alpha = tickAlpha),
                    start = Offset(center.x, center.y - radius),
                    end = Offset(center.x, center.y - radius * 0.55f),
                    strokeWidth = this.size.minDimension * 0.1f,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

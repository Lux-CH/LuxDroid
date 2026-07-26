package ch.cclerc.luxapp.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ch.cclerc.luxapp.ui.theme.LuxTheme
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val SPOKE_COUNT = 8

@Composable
fun IosActivityIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    color: Color = LuxTheme.colors.secondaryLabel
) {
    val transition = rememberInfiniteTransition(label = "iosActivityIndicator")
    val step by transition.animateFloat(
        initialValue = 0f,
        targetValue = SPOKE_COUNT.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spokeStep"
    )

    Canvas(modifier = modifier.size(size)) {
        val current = step.roundToInt() % SPOKE_COUNT
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val radius = minOf(this.size.width, this.size.height) / 2f
        val strokeWidth = radius * 0.28f
        val innerRadius = radius * 0.42f
        val outerRadius = radius - strokeWidth / 2f

        for (i in 0 until SPOKE_COUNT) {
            val trail = ((i - current) + SPOKE_COUNT) % SPOKE_COUNT
            val alpha = 1f - (trail.toFloat() / SPOKE_COUNT) * 0.85f
            val angle = (Math.PI * 2.0 * i / SPOKE_COUNT) - Math.PI / 2.0
            val cosA = cos(angle).toFloat()
            val sinA = sin(angle).toFloat()
            drawLine(
                color = color.copy(alpha = color.alpha * alpha),
                start = Offset(center.x + cosA * innerRadius, center.y + sinA * innerRadius),
                end = Offset(center.x + cosA * outerRadius, center.y + sinA * outerRadius),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

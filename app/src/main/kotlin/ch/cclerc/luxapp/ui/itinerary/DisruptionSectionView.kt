package ch.cclerc.luxapp.ui.itinerary

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.cclerc.luxapp.core.HapticFeedback
import ch.cclerc.luxapp.core.SFSymbol
import ch.cclerc.luxapp.domain.DisruptionManager
import ch.cclerc.luxapp.ui.anim.PlainIndication
import ch.cclerc.luxapp.ui.theme.LuxSprings
import ch.cclerc.luxapp.ui.theme.LuxTheme
import ch.cclerc.luxcom.model.Disruption
import androidx.compose.runtime.collectAsState
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private val DisruptionSpring = LuxSprings.springFor<Float>(0.4, 0.8)

@Composable
fun rememberDisruptions(line: String?): List<Disruption> {
    val all by DisruptionManager.shared.disruptions.collectAsState()
    if (line == null) return emptyList()
    return all.filter { it.line == line }
}

@Composable
fun DisruptionSectionView(
    disruptions: List<Disruption>,
    modifier: Modifier = Modifier
) {
    if (disruptions.isEmpty()) return

    val colors = LuxTheme.colors
    var isExpanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 90f else 0f,
        animationSpec = DisruptionSpring,
        label = "disruption-chevron"
    )
    val revealProgress by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = DisruptionSpring,
        label = "disruption-reveal"
    )
    val shape = RoundedCornerShape(20.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (colors.isDark) colors.secondarySystemBackgroundElevated else colors.systemGray6,
                shape
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(interactionSource = null, indication = PlainIndication) {
                    HapticFeedback.lightImpact()
                    isExpanded = !isExpanded
                }
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(8.dp).background(colors.systemRed, CircleShape))

            Text(
                text = "Perturbations (${disruptions.size})",
                style = LuxTheme.type.subheadline.copy(fontWeight = FontWeight.Medium),
                color = colors.systemRed
            )

            Spacer(Modifier.weight(1f))

            SFSymbol(
                name = "chevron.right",
                size = 12.sp,
                color = colors.systemRed,
                modifier = Modifier.rotate(rotation)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clipToBounds()
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    val height = (placeable.height * revealProgress).roundToInt().coerceAtLeast(0)
                    layout(placeable.width, height) { placeable.place(0, 0) }
                }
                .padding(horizontal = 20.dp)
                .padding(bottom = if (isExpanded) 12.dp else 0.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            disruptions.forEachIndexed { index, disruption ->
                DisruptionCardView(disruption = disruption, index = index, isExpanded = isExpanded)
            }
        }
    }
}

@Composable
private fun DisruptionCardView(
    disruption: Disruption,
    index: Int,
    isExpanded: Boolean
) {
    val colors = LuxTheme.colors
    val appearance = remember(disruption.id) { Animatable(if (isExpanded) 1f else 0f) }

    LaunchedEffect(isExpanded) {
        delay(index * 50L)
        appearance.animateTo(if (isExpanded) 1f else 0f, DisruptionSpring)
    }

    val (title, description) = remember(disruption.lineDisruption) {
        extractTitleAndDesc(disruption.lineDisruption)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = appearance.value
                val scale = 0.95f + 0.05f * appearance.value
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0.5f, 0f)
            }
            .background(
                if (colors.isDark) colors.tertiarySystemBackgroundElevated else Color.White,
                RoundedCornerShape(14.dp)
            )
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (title.isNotEmpty()) {
                Text(
                    text = title,
                    style = LuxTheme.type.subheadline.copy(fontWeight = FontWeight.Medium),
                    color = colors.label
                )
            }
            Text(
                text = description,
                style = LuxTheme.type.caption,
                color = colors.secondaryLabel
            )
        }

        Spacer(Modifier.weight(1f))
    }
}

private fun extractTitleAndDesc(disruption: String): Pair<String, String> {
    val unescaped = android.text.Html.fromHtml(disruption, android.text.Html.FROM_HTML_MODE_LEGACY).toString()
    val index = unescaped.indexOf(" - ")
    if (index < 0) return "" to unescaped
    return unescaped.substring(0, index).trim() to unescaped.substring(index + 3).trim()
}

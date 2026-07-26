package ch.cclerc.luxapp.ui.anim

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.unit.Constraints
import ch.cclerc.luxapp.core.HapticFeedback
import kotlinx.coroutines.launch

object ScaleIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        ScaleIndicationNode(interactionSource)

    override fun equals(other: Any?): Boolean = other === this
    override fun hashCode(): Int = 971
}

object PlainIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        object : Modifier.Node() {}

    override fun equals(other: Any?): Boolean = other === this
    override fun hashCode(): Int = 972
}

private class ScaleIndicationNode(
    private val interactionSource: InteractionSource
) : Modifier.Node(), LayoutModifierNode {

    private val scale = Animatable(1f)
    private val alpha = Animatable(1f)

    override fun onAttach() {
        coroutineScope.launch {
            var pressCount = 0
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> pressCount++
                    is PressInteraction.Release -> pressCount--
                    is PressInteraction.Cancel -> pressCount--
                }
                val pressed = pressCount > 0
                launch {
                    scale.animateTo(
                        if (pressed) 0.97f else 1f,
                        tween(durationMillis = 200, easing = FastOutSlowInEasing)
                    )
                }
                launch {
                    alpha.animateTo(
                        if (pressed) 0.9f else 1f,
                        tween(durationMillis = 200, easing = FastOutSlowInEasing)
                    )
                }
            }
        }
    }

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(0, 0) {
                scaleX = scale.value
                scaleY = scale.value
                alpha = this@ScaleIndicationNode.alpha.value
            }
        }
    }
}

fun Modifier.scaleClickable(
    enabled: Boolean = true,
    haptic: Boolean = true,
    onClick: () -> Unit
): Modifier = clickable(
    interactionSource = null,
    indication = ScaleIndication,
    enabled = enabled
) {
    if (haptic) HapticFeedback.lightImpact()
    onClick()
}

package ch.cclerc.luxapp.ui.anim

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntSize
import ch.cclerc.luxapp.ui.theme.LuxSprings

@Composable
fun NumericText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier
) {
    var previousText by remember { mutableStateOf(text) }
    var countsDown by remember { mutableStateOf(true) }
    if (text != previousText) {
        val prev = previousText.filter { it.isDigit() }.toLongOrNull()
        val next = text.filter { it.isDigit() }.toLongOrNull()
        countsDown = if (prev != null && next != null) next <= prev else true
        previousText = text
    }
    val down = countsDown
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        val count = text.length
        for (i in 0 until count) {
            val slotKey = count - i
            AnimatedContent(
                targetState = text[i],
                transitionSpec = {
                    val enter = slideInVertically(LuxSprings.springFor(0.3, 0.7)) { height ->
                        if (down) -height else height
                    } + fadeIn(LuxSprings.Snappy)
                    val exit = slideOutVertically(LuxSprings.springFor(0.3, 0.7)) { height ->
                        if (down) height else -height
                    } + fadeOut(LuxSprings.Snappy)
                    (enter togetherWith exit).using(
                        SizeTransform(clip = true) { _, _ ->
                            LuxSprings.springFor<IntSize>(0.3, 0.7)
                        }
                    )
                },
                label = "numeric-$slotKey"
            ) { character ->
                androidx.compose.material3.Text(
                    text = character.toString(),
                    style = style,
                    color = color,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

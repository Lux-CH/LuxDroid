package ch.cclerc.luxapp.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import ch.cclerc.luxapp.R
import ch.cclerc.luxapp.ui.theme.LuxShapes
import ch.cclerc.luxapp.ui.theme.LuxTheme
import kotlin.math.abs

private val stopHeaderImages = intArrayOf(
    R.drawable.stop_header_mountain1,
    R.drawable.stop_header_jet1,
    R.drawable.stop_header_rive1,
    R.drawable.stop_header_rive2,
    R.drawable.stop_header_vignes1,
    R.drawable.stop_header_vignes2,
    R.drawable.stop_header_champel1,
    R.drawable.stop_header_chambesy1,
    R.drawable.stop_header_lancy1,
    R.drawable.stop_header_rive3
)

private fun lightBoostMatrix(): ColorMatrix {
    val saturation = ColorMatrix().apply { setToSaturation(1.5f) }
    val scale = 1.2f
    val translate = (-0.5f * scale + 0.5f) * 255f
    val contrast = ColorMatrix(
        floatArrayOf(
            scale, 0f, 0f, 0f, translate,
            0f, scale, 0f, 0f, translate,
            0f, 0f, scale, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        )
    )
    contrast *= saturation
    return contrast
}

@Composable
fun MaskedImage(stopIdentifier: String, modifier: Modifier = Modifier) {
    val dark = LuxTheme.isDark
    val colors = LuxTheme.colors
    val imageRes = remember(stopIdentifier) {
        stopHeaderImages[abs(stopIdentifier.hashCode() % stopHeaderImages.size)]
    }
    val layerAlpha = if (dark) 0.14f else 0.35f
    val colorFilter = if (dark) null else remember { ColorFilter.colorMatrix(lightBoostMatrix()) }
    Box(modifier.clip(LuxShapes.topCorners(LuxShapes.r38))) {
        Image(
            painter = painterResource(imageRes),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            colorFilter = colorFilter,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = layerAlpha
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(
                            0.42f to Color.White,
                            1f to Color.White.copy(alpha = 0.53f)
                        ),
                        blendMode = BlendMode.DstIn
                    )
                }
                .blur(8.dp)
        )
        if (!dark) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(colors.separator.copy(alpha = colors.separator.alpha * 0.5f))
            )
        }
    }
}

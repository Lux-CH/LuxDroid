package ch.cclerc.luxapp.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.iosShadow(
    color: Color,
    blurRadius: Dp,
    offsetY: Dp = 0.dp,
    shape: Shape
): Modifier = drawBehind {
    val blurPx = blurRadius.toPx()
    if (blurPx <= 0f || color.alpha <= 0f) return@drawBehind
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = Path().apply { addOutline(outline) }
    drawIntoCanvas { canvas ->
        val paint = Paint()
        val frameworkPaint = paint.asFrameworkPaint()
        frameworkPaint.isAntiAlias = true
        frameworkPaint.color = android.graphics.Color.TRANSPARENT
        frameworkPaint.setShadowLayer(blurPx, 0f, offsetY.toPx(), color.toArgb())
        canvas.drawPath(path, paint)
    }
}

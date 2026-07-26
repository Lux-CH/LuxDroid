package ch.cclerc.luxapp.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object LuxShapes {
    val r2: Dp = 2.dp
    val r8: Dp = 8.dp
    val r12: Dp = 12.dp
    val r16: Dp = 16.dp
    val r20: Dp = 20.dp
    val r24: Dp = 24.dp
    val r28: Dp = 28.dp
    val r32: Dp = 32.dp
    val r36: Dp = 36.dp
    val r38: Dp = 38.dp
    val r40: Dp = 40.dp
    val r50: Dp = 50.dp

    fun topCorners(radius: Dp): RoundedCornerShape = RoundedCornerShape(
        topStart = radius,
        topEnd = radius,
        bottomStart = 0.dp,
        bottomEnd = 0.dp
    )

    fun bottomCorners(radius: Dp): RoundedCornerShape = RoundedCornerShape(
        topStart = 0.dp,
        topEnd = 0.dp,
        bottomStart = radius,
        bottomEnd = radius
    )
}

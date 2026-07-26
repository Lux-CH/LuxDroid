package ch.cclerc.luxapp.ui.theme

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import kotlin.math.PI

object LuxSprings {

    fun stiffness(response: Double): Float {
        val omega = 2.0 * PI / response
        return (omega * omega).toFloat()
    }

    fun <T> springFor(response: Double, damping: Double): SpringSpec<T> =
        spring(dampingRatio = damping.toFloat(), stiffness = stiffness(response))

    fun convert(response: Double, damping: Double): SpringSpec<Float> =
        springFor(response, damping)

    val DragTracking: SpringSpec<Float> = springFor(0.1, 1.0)
    val StopBounce: SpringSpec<Float> = springFor(0.2, 0.6)
    val Snappy: SpringSpec<Float> = springFor(0.3, 0.7)
    val ListEntrance: SpringSpec<Float> = springFor(0.35, 0.8)
    val Select: SpringSpec<Float> = springFor(0.4, 0.7)
    val Gentle: SpringSpec<Float> = springFor(0.4, 0.8)
    val UltraSmooth: SpringSpec<Float> = springFor(0.4, 0.85)
    val SearchTransition: SpringSpec<Float> = springFor(0.45, 0.82)
    val SwapBounce: SpringSpec<Float> = springFor(0.5, 0.6)
    val Emphatic: SpringSpec<Float> = springFor(0.5, 0.7)
    val ContentSpring: SpringSpec<Float> = springFor(0.5, 0.85)
    val SlowEntrance: SpringSpec<Float> = springFor(0.6, 0.7)
    val HeaderEntrance: SpringSpec<Float> = springFor(0.6, 0.8)
}

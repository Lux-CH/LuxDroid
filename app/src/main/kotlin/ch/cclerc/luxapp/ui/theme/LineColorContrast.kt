package ch.cclerc.luxapp.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

fun readableLineColor(color: Color, isDark: Boolean, tint: Double = 0.0): Color =
    LineColorContrast.cached(color, isDark, tint)

object LineColorContrast {
    const val DARK_TARGET = 3.5

    private data class Rgb(val r: Double, val g: Double, val b: Double)

    private val darkSurface = Rgb(0.282, 0.282, 0.290)

    private data class Key(val color: Color, val isDark: Boolean, val tint: Double)

    private val cache = HashMap<Key, Color>()

    fun cached(color: Color, isDark: Boolean, tint: Double): Color {
        val key = Key(color, isDark, tint)
        synchronized(cache) { cache[key]?.let { return it } }
        val result = readable(color, isDark, tint)
        synchronized(cache) {
            if (cache.size > 1024) cache.clear()
            cache[key] = result
        }
        return result
    }

    private fun legacy(color: Color): Color = if (isLegColorDark(color)) lightenLegColor(color) else color

    fun readable(color: Color, isDark: Boolean, tint: Double): Color {
        if (!isDark) return legacy(color)
        val original = Rgb(clamp(color.red), clamp(color.green), clamp(color.blue))
        val surface = darkSurface
        val background = Rgb(
            surface.r + (original.r - surface.r) * tint,
            surface.g + (original.g - surface.g) * tint,
            surface.b + (original.b - surface.b) * tint
        )
        val (hue, saturation, lightness) = hsl(original)
        fun candidate(step: Int): Rgb =
            if (step == 0) original else rgb(hue, saturation, min(1.0, lightness + step / 100.0))
        fun passes(step: Int): Boolean =
            lightness + step / 100.0 >= 1 || contrast(candidate(step), background) >= DARK_TARGET
        var low = 0
        var high = 100
        while (low < high) {
            val middle = (low + high) / 2
            if (passes(middle)) high = middle else low = middle + 1
        }
        val result = candidate(low)
        return Color(result.r.toFloat(), result.g.toFloat(), result.b.toFloat(), color.alpha)
    }

    private fun clamp(value: Float): Double = min(1.0, max(0.0, value.toDouble()))

    private fun luminance(color: Rgb): Double {
        fun linear(c: Double) = if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        return 0.2126 * linear(color.r) + 0.7152 * linear(color.g) + 0.0722 * linear(color.b)
    }

    private fun contrast(a: Rgb, b: Rgb): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private fun hsl(color: Rgb): Triple<Double, Double, Double> {
        val high = maxOf(color.r, color.g, color.b)
        val low = minOf(color.r, color.g, color.b)
        val lightness = (high + low) / 2
        if (high == low) return Triple(0.0, 0.0, lightness)
        val delta = high - low
        val saturation = if (lightness > 0.5) delta / (2 - high - low) else delta / (high + low)
        val hue = when (high) {
            color.r -> (color.g - color.b) / delta + (if (color.g < color.b) 6 else 0)
            color.g -> (color.b - color.r) / delta + 2
            else -> (color.r - color.g) / delta + 4
        }
        return Triple(hue / 6, saturation, lightness)
    }

    private fun rgb(hue: Double, saturation: Double, lightness: Double): Rgb {
        if (saturation <= 0) return Rgb(lightness, lightness, lightness)
        val q = if (lightness < 0.5) lightness * (1 + saturation) else lightness + saturation - lightness * saturation
        val p = 2 * lightness - q
        fun channel(value: Double): Double {
            var t = value
            if (t < 0) t += 1
            if (t > 1) t -= 1
            if (t < 1.0 / 6) return p + (q - p) * 6 * t
            if (t < 1.0 / 2) return q
            if (t < 2.0 / 3) return p + (q - p) * (2.0 / 3 - t) * 6
            return p
        }
        return Rgb(channel(hue + 1.0 / 3), channel(hue), channel(hue - 1.0 / 3))
    }
}

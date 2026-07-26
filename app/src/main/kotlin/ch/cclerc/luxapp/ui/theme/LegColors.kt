package ch.cclerc.luxapp.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import ch.cclerc.luxcom.colors.LineColors
import ch.cclerc.luxcom.model.TransportationMode
import ch.cclerc.luxcom.model.trip.Leg
import kotlin.math.max
import kotlin.math.min

private val lausanneAgencies = setOf("151", "55", "764", "7256", "344", "29")

val legWalkColor: Color = Color(0xFF007AFF)
val legVehicleGrayColor: Color = Color(0xFF8E8E93)
val squaredPillFallbackColor: Color = Color(0xFFEA0706)

fun defaultLegAccent(): Color = AccentColorManager.current().resolved(false)

@Composable
@ReadOnlyComposable
fun legColor(leg: Leg, brightIt: Boolean = false): Color =
    getLegColor(leg, brightIt, LuxTheme.accent)

fun getLegColor(
    leg: Leg,
    brightIt: Boolean = false,
    accent: Color = defaultLegAccent()
): Color {
    when (leg.mode) {
        TransportationMode.WALK -> return legWalkColor
        TransportationMode.BIKE, TransportationMode.CAR -> return legVehicleGrayColor
        else -> Unit
    }

    val isLausanne = (leg.agencyId ?: "") in lausanneAgencies
    val isTAC = leg.agencyId == "1"
    val routeName = leg.routeShortName

    val baseColor: Color = if (routeName != null) {
        val tac = if (isTAC) LineColors.tacColors(routeName) else null
        val mapped = if (isLausanne) LineColors.tlColor(routeName) else LineColors.color(routeName)
        when {
            tac != null -> Color(tac)
            mapped != null -> Color(mapped)
            else -> {
                val isTrainDetected = routeName.startsWith("RL") || routeName.startsWith("IR") ||
                    routeName.startsWith("RE") || routeName.startsWith("IC") || routeName == "R"
                if (leg.mode.usesSquaredPill || isTrainDetected) squaredPillFallbackColor else accent
            }
        }
    } else {
        if (leg.mode.usesSquaredPill) squaredPillFallbackColor else accent
    }

    return if (brightIt && isLegColorDark(baseColor)) lightenLegColor(baseColor) else baseColor
}

fun isLegColorDark(color: Color): Boolean {
    val luminance = 0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue
    return luminance < 0.4f
}

fun lightenLegColor(color: Color, factor: Float = 0.25f): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    val newBrightness = min(1f, hsv[2] + factor)
    val newSaturation = max(0.3f, hsv[1] * 0.8f)
    return Color.hsv(hsv[0], newSaturation, newBrightness)
}

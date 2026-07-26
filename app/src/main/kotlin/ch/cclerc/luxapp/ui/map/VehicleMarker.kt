package ch.cclerc.luxapp.ui.map

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.cclerc.luxapp.domain.map.LatLng
import ch.cclerc.luxapp.ui.theme.TpgFontFamily
import ch.cclerc.luxapp.ui.theme.iosShadow
import ch.cclerc.luxapp.viewmodel.VehicleAnnotation
import kotlinx.coroutines.launch

val VehicleMarkerBadgeSize = 32.dp
val VehicleMarkerFrameSize = 64.dp

private const val VEHICLE_PULSE_DURATION_MS = 2000
private const val VEHICLE_PULSE_MAX_SCALE = 1.5f
private const val VEHICLE_PULSE_MAX_ALPHA = 0.7f
private const val VEHICLE_MOVE_DURATION_MS = 500

val LatLngVectorConverter: TwoWayConverter<LatLng, AnimationVector2D> = TwoWayConverter(
    convertToVector = { AnimationVector2D(it.latitude.toFloat(), it.longitude.toFloat()) },
    convertFromVector = { LatLng(it.v1.toDouble(), it.v2.toDouble()) }
)

val VehicleMoveSpec: AnimationSpec<LatLng> =
    tween(durationMillis = VEHICLE_MOVE_DURATION_MS, easing = EaseInOut)

@Composable
internal fun <T> rememberAnimatedCoordinates(
    items: List<T>,
    spec: AnimationSpec<LatLng>,
    idOf: (T) -> String,
    coordinateOf: (T) -> LatLng,
    withCoordinate: (T, LatLng) -> T
): List<T> {
    val animatables = remember { mutableStateMapOf<String, Animatable<LatLng, AnimationVector2D>>() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(items) {
        val identifiers = items.map(idOf).toSet()
        animatables.keys.retainAll(identifiers)
        items.forEach { item ->
            val identifier = idOf(item)
            val target = coordinateOf(item)
            val existing = animatables[identifier]
            if (existing == null) {
                animatables[identifier] = Animatable(target, LatLngVectorConverter)
            } else {
                scope.launch { existing.animateTo(target, spec) }
            }
        }
    }

    return items.map { item ->
        val animatable = animatables[idOf(item)]
        if (animatable == null) item else withCoordinate(item, animatable.value)
    }
}

@Composable
fun rememberAnimatedVehicleAnnotations(
    vehicles: List<VehicleAnnotation>
): List<VehicleAnnotation> = rememberAnimatedCoordinates(
    items = vehicles,
    spec = VehicleMoveSpec,
    idOf = { it.id },
    coordinateOf = { it.coordinate },
    withCoordinate = { vehicle, coordinate -> vehicle.copy(coordinate = coordinate) }
)

@Composable
fun VehicleMarker(
    routeShortName: String?,
    color: Color,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "vehiclePulse")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = VEHICLE_PULSE_DURATION_MS, easing = EaseInOut),
            repeatMode = RepeatMode.Restart
        ),
        label = "vehiclePulseProgress"
    )

    Box(
        modifier = modifier.size(VehicleMarkerFrameSize),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .graphicsLayer {
                    val scale = 1f + (VEHICLE_PULSE_MAX_SCALE - 1f) * progress
                    scaleX = scale
                    scaleY = scale
                    alpha = VEHICLE_PULSE_MAX_ALPHA * (1f - progress)
                }
                .size(VehicleMarkerBadgeSize)
                .border(3.5.dp, color.copy(alpha = 0.6f), CircleShape)
        )

        Box(
            Modifier
                .size(VehicleMarkerBadgeSize)
                .iosShadow(color.copy(alpha = 0.7f), 4.dp, 0.dp, CircleShape)
                .background(color, CircleShape)
        )

        if (routeShortName != null) {
            BasicText(
                text = routeShortName,
                style = TextStyle(
                    fontFamily = TpgFontFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            )
        }
    }
}

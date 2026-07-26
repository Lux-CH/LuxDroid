package ch.cclerc.luxapp.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import ch.cclerc.luxapp.R
import ch.cclerc.luxapp.domain.map.LatLng
import ch.cclerc.luxapp.viewmodel.VehicleAnnotation
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.maplibre.compose.expressions.dsl.Feature
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.convertToColor
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.expressions.value.SymbolAnchor
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.util.MaplibreComposable

val VehicleMarkerBadgeSize = 32.dp
val VehicleMarkerShadowRadius = 4.dp
val VehicleMarkerPulseStroke = 3.5.dp

const val LUX_VEHICLE_PULSE_LAYER_ID = "lux-vehicle-pulse"
const val LUX_VEHICLE_BADGE_LAYER_ID = "lux-vehicle-badge"

private const val VEHICLE_PULSE_DURATION_NANOS = 2_000_000_000L
private const val VEHICLE_PULSE_MAX_ALPHA = 0.7f
private const val VEHICLE_PULSE_STROKE_ALPHA = 0.6f
private const val VEHICLE_SHADOW_ALPHA = 0.7f
private const val VEHICLE_MOVE_DURATION_MS = 500
private const val VEHICLE_LABEL_SIZE_SP = 11f

private val VehiclePulseStartRadius = 16.dp
private val VehiclePulseEndRadius = 24.dp

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
@MaplibreComposable
fun VehicleMarkerLayers(vehicles: List<VehicleAnnotation>) {
    VehiclePulseLayer(vehicles)

    val groups = remember(vehicles) { vehicles.groupBy(::vehicleBadgeKey) }
    groups.forEach { (badgeKey, members) ->
        key(badgeKey) {
            VehicleBadgeLayer(badgeKey = badgeKey, vehicles = members)
        }
    }
}

@Composable
@MaplibreComposable
private fun VehiclePulseLayer(vehicles: List<VehicleAnnotation>) {
    val json = remember(vehicles) { vehicleFeatureCollectionJson(vehicles) }
    val source = rememberGeoJsonSource(data = GeoJsonData.JsonString(json))
    val progress = rememberVehiclePulseProgress()

    CircleLayer(
        id = LUX_VEHICLE_PULSE_LAYER_ID,
        source = source,
        radius = const(
            VehiclePulseStartRadius +
                (VehiclePulseEndRadius - VehiclePulseStartRadius) * progress
        ),
        color = const(Color.Transparent),
        opacity = const(0f),
        strokeColor = Feature.get("pulse").convertToColor(),
        strokeWidth = const(VehicleMarkerPulseStroke),
        strokeOpacity = const(VEHICLE_PULSE_MAX_ALPHA * (1f - progress))
    )
}

@Composable
@MaplibreComposable
private fun VehicleBadgeLayer(badgeKey: VehicleBadgeKey, vehicles: List<VehicleAnnotation>) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val json = remember(vehicles) { vehicleFeatureCollectionJson(vehicles) }
    val source = rememberGeoJsonSource(data = GeoJsonData.JsonString(json))

    val bitmap = remember(badgeKey, density) {
        vehicleBadgeBitmap(
            routeShortName = badgeKey.routeShortName,
            color = Color(badgeKey.colorArgb),
            typeface = ResourcesCompat.getFont(context, R.font.tpg_font),
            density = density
        )
    }
    val icon = remember(bitmap) { image(bitmap) }

    SymbolLayer(
        id = "$LUX_VEHICLE_BADGE_LAYER_ID-${badgeKey.layerSuffix}",
        source = source,
        iconImage = icon,
        iconAnchor = const(SymbolAnchor.Center),
        iconAllowOverlap = const(true),
        iconIgnorePlacement = const(true)
    )
}

@Composable
private fun rememberVehiclePulseProgress(): Float {
    var progress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        var start = 0L
        while (true) {
            withFrameNanos { now ->
                if (start == 0L) start = now
                val elapsed = (now - start) % VEHICLE_PULSE_DURATION_NANOS
                progress = EaseInOut.transform(
                    elapsed.toFloat() / VEHICLE_PULSE_DURATION_NANOS.toFloat()
                )
            }
        }
    }

    return progress
}

data class VehicleBadgeKey(val routeShortName: String?, val colorArgb: Int) {
    val layerSuffix: String
        get() = "${routeShortName.orEmpty().ifEmpty { "none" }}-$colorArgb"
}

private fun vehicleBadgeKey(vehicle: VehicleAnnotation): VehicleBadgeKey =
    VehicleBadgeKey(vehicle.routeShortName, vehicle.color.toArgb())

private fun vehicleBadgeBitmap(
    routeShortName: String?,
    color: Color,
    typeface: Typeface?,
    density: Density
): ImageBitmap {
    val badgePx = with(density) { VehicleMarkerBadgeSize.toPx() }
    val shadowPx = with(density) { VehicleMarkerShadowRadius.toPx() }
    val sizePx = max(1, (badgePx + shadowPx * 4f).roundToInt())
    val center = sizePx / 2f
    val radius = badgePx / 2f

    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb()
        setShadowLayer(shadowPx, 0f, 0f, color.copy(alpha = VEHICLE_SHADOW_ALPHA).toArgb())
    }
    canvas.drawCircle(center, center, radius, fill)

    if (!routeShortName.isNullOrEmpty()) {
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.White.toArgb()
            this.typeface = typeface ?: Typeface.DEFAULT_BOLD
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
            textSize = with(density) { VEHICLE_LABEL_SIZE_SP.sp.toPx() }
        }
        val metrics = label.fontMetrics
        val baseline = center - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(routeShortName, center, baseline, label)
    }

    return bitmap.asImageBitmap()
}

fun vehicleFeatureCollectionJson(vehicles: List<VehicleAnnotation>): String = buildJsonObject {
    put("type", "FeatureCollection")
    putJsonArray("features") {
        vehicles.forEach { vehicle ->
            addJsonObject {
                put("type", "Feature")
                putJsonObject("properties") {
                    put("id", vehicle.id)
                    put(
                        "pulse",
                        vehicle.color.copy(alpha = VEHICLE_PULSE_STROKE_ALPHA).toCssColorString()
                    )
                }
                putJsonObject("geometry") {
                    put("type", "Point")
                    putJsonArray("coordinates") {
                        add(vehicle.coordinate.longitude)
                        add(vehicle.coordinate.latitude)
                    }
                }
            }
        }
    }
}.toString()

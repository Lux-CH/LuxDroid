package ch.cclerc.luxapp.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ch.cclerc.luxapp.viewmodel.StopAnnotation
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.maplibre.compose.expressions.dsl.Feature
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.convertToColor
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.GeoJsonSource
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.util.ClickResult
import org.maplibre.compose.util.FeaturesClickHandler
import org.maplibre.compose.util.MaplibreComposable

val StopDotHitSize = 44.dp

val StopDotTerminalSize = 20.dp
val StopDotIntermediateSize = 10.dp
val StopDotDefaultSize = 16.dp
val StopDotTerminalHaloSize = 30.dp

const val LUX_STOP_HALO_LAYER_ID = "lux-stop-halo"
const val LUX_STOP_TERMINAL_LAYER_ID = "lux-stop-terminal"
const val LUX_STOP_DEFAULT_LAYER_ID = "lux-stop-default"
const val LUX_STOP_INTERMEDIATE_LAYER_ID = "lux-stop-intermediate"
const val LUX_STOP_HIT_LAYER_ID = "lux-stop-hit"

private const val STOP_HALO_OPACITY = 0.15f
private const val STOP_HIT_OPACITY = 0.002f
private val StopDotTerminalStroke = 3.dp
private val StopDotStroke = 1.dp

private enum class StopDotKind { TERMINAL, INTERMEDIATE, DEFAULT }

@Composable
@MaplibreComposable
fun StopDotLayers(
    stops: List<StopAnnotation>,
    showingIntermediateStops: Boolean,
    onStopClick: (StopAnnotation) -> Unit
) {
    val grouped = remember(stops) { stops.groupBy(::stopDotKind) }
    val byId = remember(stops) { stops.associateBy { it.id } }

    val terminals = grouped[StopDotKind.TERMINAL].orEmpty()
    val intermediates = grouped[StopDotKind.INTERMEDIATE].orEmpty()
    val defaults = grouped[StopDotKind.DEFAULT].orEmpty()

    val terminalSource = rememberStopSource(terminals)
    val intermediateSource = rememberStopSource(intermediates)
    val defaultSource = rememberStopSource(defaults)

    val onFeaturesClick: FeaturesClickHandler = { features ->
        val annotation = features.firstNotNullOfOrNull { feature ->
            (feature.properties?.get("id") as? JsonPrimitive)?.content?.let(byId::get)
        }
        if (annotation != null) {
            onStopClick(annotation)
            ClickResult.Consume
        } else {
            ClickResult.Pass
        }
    }

    CircleLayer(
        id = LUX_STOP_HALO_LAYER_ID,
        source = terminalSource,
        radius = const(StopDotTerminalHaloSize / 2),
        color = Feature.get("color").convertToColor(),
        opacity = const(STOP_HALO_OPACITY),
        strokeWidth = const(0.dp)
    )

    StopCircleLayer(
        id = LUX_STOP_INTERMEDIATE_LAYER_ID,
        source = intermediateSource,
        radius = StopDotIntermediateSize / 2,
        strokeWidth = StopDotStroke,
        visible = showingIntermediateStops
    )

    StopCircleLayer(
        id = LUX_STOP_DEFAULT_LAYER_ID,
        source = defaultSource,
        radius = StopDotDefaultSize / 2,
        strokeWidth = StopDotStroke,
        visible = showingIntermediateStops
    )

    StopCircleLayer(
        id = LUX_STOP_TERMINAL_LAYER_ID,
        source = terminalSource,
        radius = StopDotTerminalSize / 2,
        strokeWidth = StopDotTerminalStroke
    )

    CircleLayer(
        id = "$LUX_STOP_HIT_LAYER_ID-terminal",
        source = terminalSource,
        radius = const(StopDotHitSize / 2),
        color = Feature.get("color").convertToColor(),
        opacity = const(STOP_HIT_OPACITY),
        strokeWidth = const(0.dp),
        onClick = onFeaturesClick
    )

    CircleLayer(
        id = "$LUX_STOP_HIT_LAYER_ID-default",
        source = defaultSource,
        visible = showingIntermediateStops,
        radius = const(StopDotHitSize / 2),
        color = Feature.get("color").convertToColor(),
        opacity = const(STOP_HIT_OPACITY),
        strokeWidth = const(0.dp),
        onClick = onFeaturesClick
    )

    CircleLayer(
        id = "$LUX_STOP_HIT_LAYER_ID-intermediate",
        source = intermediateSource,
        visible = showingIntermediateStops,
        radius = const(StopDotHitSize / 2),
        color = Feature.get("color").convertToColor(),
        opacity = const(STOP_HIT_OPACITY),
        strokeWidth = const(0.dp),
        onClick = onFeaturesClick
    )
}

@Composable
@MaplibreComposable
private fun StopCircleLayer(
    id: String,
    source: GeoJsonSource,
    radius: Dp,
    strokeWidth: Dp,
    visible: Boolean = true
) {
    CircleLayer(
        id = id,
        source = source,
        visible = visible,
        radius = const(radius),
        color = const(Color.White),
        strokeColor = Feature.get("color").convertToColor(),
        strokeWidth = const(strokeWidth)
    )
}

@Composable
@MaplibreComposable
private fun rememberStopSource(stops: List<StopAnnotation>): GeoJsonSource {
    val json = remember(stops) { stopFeatureCollectionJson(stops) }
    return rememberGeoJsonSource(data = GeoJsonData.JsonString(json))
}

private fun stopDotKind(annotation: StopAnnotation): StopDotKind = when {
    annotation.isTerminal -> StopDotKind.TERMINAL
    annotation.isIntermediate -> StopDotKind.INTERMEDIATE
    else -> StopDotKind.DEFAULT
}

fun stopFeatureCollectionJson(stops: List<StopAnnotation>): String = buildJsonObject {
    put("type", "FeatureCollection")
    putJsonArray("features") {
        stops.forEach { stop ->
            addJsonObject {
                put("type", "Feature")
                putJsonObject("properties") {
                    put("id", stop.id)
                    put("color", stop.color.toCssColorString())
                }
                putJsonObject("geometry") {
                    put("type", "Point")
                    putJsonArray("coordinates") {
                        add(stop.coordinate.longitude)
                        add(stop.coordinate.latitude)
                    }
                }
            }
        }
    }
}.toString()

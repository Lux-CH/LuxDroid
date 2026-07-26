package ch.cclerc.luxapp.ui.map

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import ch.cclerc.luxapp.domain.map.LatLng
import ch.cclerc.luxapp.ui.theme.legWalkColor
import ch.cclerc.luxapp.viewmodel.WalkingAnnotation
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.util.MaplibreComposable

val WalkingDotSize = 12.dp

const val LUX_WALKING_LAYER_ID = "lux-walking-dot"

private const val WALKING_MOVE_DURATION_MS = 1000

val WalkingMoveSpec: AnimationSpec<LatLng> =
    tween(durationMillis = WALKING_MOVE_DURATION_MS, easing = LinearEasing)

@Composable
fun rememberAnimatedWalkingAnnotations(
    walking: List<WalkingAnnotation>
): List<WalkingAnnotation> = rememberAnimatedCoordinates(
    items = walking,
    spec = WalkingMoveSpec,
    idOf = { it.id },
    coordinateOf = { it.coordinate },
    withCoordinate = { annotation, coordinate -> annotation.copy(coordinate = coordinate) }
)

@Composable
@MaplibreComposable
fun WalkingDotLayer(walking: List<WalkingAnnotation>) {
    val json = remember(walking) { walkingFeatureCollectionJson(walking) }
    val source = rememberGeoJsonSource(data = GeoJsonData.JsonString(json))

    CircleLayer(
        id = LUX_WALKING_LAYER_ID,
        source = source,
        radius = const(WalkingDotSize / 2),
        color = const(legWalkColor),
        strokeWidth = const(0.dp)
    )
}

fun walkingFeatureCollectionJson(walking: List<WalkingAnnotation>): String = buildJsonObject {
    put("type", "FeatureCollection")
    putJsonArray("features") {
        walking.forEach { annotation ->
            addJsonObject {
                put("type", "Feature")
                putJsonObject("properties") {
                    put("id", annotation.id)
                }
                putJsonObject("geometry") {
                    put("type", "Point")
                    putJsonArray("coordinates") {
                        add(annotation.coordinate.longitude)
                        add(annotation.coordinate.latitude)
                    }
                }
            }
        }
    }
}.toString()

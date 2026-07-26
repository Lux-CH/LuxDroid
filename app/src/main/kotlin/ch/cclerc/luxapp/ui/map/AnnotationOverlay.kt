package ch.cclerc.luxapp.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ch.cclerc.luxapp.domain.map.LatLng
import kotlin.math.roundToInt
import org.maplibre.compose.camera.CameraState
import org.maplibre.spatialk.geojson.Position

typealias MapProjector = (Double, Double) -> Offset?

val AnnotationCullingMargin = 64.dp

val AnnotationCenterAnchor = Offset(0.5f, 0.5f)
val AnnotationBottomAnchor = Offset(0.5f, 1f)

@Composable
fun rememberMapProjector(cameraState: CameraState): MapProjector {
    val density = LocalDensity.current
    return remember(cameraState, density) {
        { latitude: Double, longitude: Double ->
            projectToScreen(cameraState, density, latitude, longitude)
        }
    }
}

private fun projectToScreen(
    cameraState: CameraState,
    density: Density,
    latitude: Double,
    longitude: Double
): Offset? {
    cameraState.position
    val projection = cameraState.projection ?: return null
    val dpOffset = projection.screenLocationFromPosition(
        Position(longitude = longitude, latitude = latitude)
    )
    return with(density) { Offset(dpOffset.x.toPx(), dpOffset.y.toPx()) }
}

@Composable
fun <T> AnnotationOverlay(
    items: List<T>,
    projection: MapProjector,
    positionOf: (T) -> LatLng,
    modifier: Modifier = Modifier,
    keyOf: (T) -> Any = { it as Any },
    anchorOf: (T) -> Offset = { AnnotationCenterAnchor },
    cullingMargin: Dp = AnnotationCullingMargin,
    content: @Composable (T) -> Unit
) {
    Layout(
        modifier = modifier,
        content = {
            items.forEach { item ->
                key(keyOf(item)) { content(item) }
            }
        }
    ) { measurables, constraints ->
        val childConstraints = Constraints()
        val placeables = measurables.map { it.measure(childConstraints) }
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else 0
        val height = if (constraints.hasBoundedHeight) constraints.maxHeight else 0
        val margin = cullingMargin.toPx()

        layout(width, height) {
            placeables.forEachIndexed { index, placeable ->
                val item = items.getOrNull(index) ?: return@forEachIndexed
                val coordinate = positionOf(item)
                val screen = projection(coordinate.latitude, coordinate.longitude)
                    ?: return@forEachIndexed
                val anchor = anchorOf(item)
                val x = screen.x - placeable.width * anchor.x
                val y = screen.y - placeable.height * anchor.y

                val visible = x + placeable.width >= -margin &&
                    x <= width + margin &&
                    y + placeable.height >= -margin &&
                    y <= height + margin

                if (visible) {
                    placeable.place(x.roundToInt(), y.roundToInt())
                }
            }
        }
    }
}

@Composable
fun AnnotationOverlayItem(
    latitude: Double,
    longitude: Double,
    projection: MapProjector,
    modifier: Modifier = Modifier,
    anchor: Offset = AnnotationCenterAnchor,
    cullingMargin: Dp = AnnotationCullingMargin,
    content: @Composable () -> Unit
) {
    AnnotationOverlay(
        items = listOf(LatLng(latitude, longitude)),
        projection = projection,
        positionOf = { it },
        modifier = modifier,
        keyOf = { it },
        anchorOf = { anchor },
        cullingMargin = cullingMargin
    ) { content() }
}

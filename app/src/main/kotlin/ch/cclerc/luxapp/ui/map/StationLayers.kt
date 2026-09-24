package ch.cclerc.luxapp.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.cclerc.luxapp.core.SFSymbol
import ch.cclerc.luxapp.domain.map.LatLng
import ch.cclerc.luxapp.domain.station.StationDetail
import ch.cclerc.luxapp.domain.station.StationOverlayContent
import ch.cclerc.luxapp.ui.theme.LuxTypography
import ch.cclerc.luxcom.station.StationLayout
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.maplibre.compose.expressions.dsl.Feature
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.convertToColor
import org.maplibre.compose.expressions.value.LineCap
import org.maplibre.compose.expressions.value.LineJoin
import org.maplibre.compose.layers.FillLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.util.MaplibreComposable

object StationStyle {
    val signBlue = Color(red = 0.176f, green = 0.196f, blue = 0.490f)

    fun idleEdge(dark: Boolean): Color =
        if (dark) Color.White.copy(alpha = 0.45f) else Color.Black.copy(alpha = 0.35f)

    val idleEdgeWidth = 2.5.dp

    fun platformFill(dark: Boolean): Color =
        if (dark) Color.White.copy(alpha = 0.13f) else Color(0.96f, 0.92f, 0.91f, 0.95f)

    fun platformStroke(dark: Boolean): Color =
        if (dark) Color.White.copy(alpha = 0.25f) else Color(0.78f, 0.62f, 0.62f, 0.8f)

    const val HIGHLIGHTED_PLATFORM_OPACITY = 0.25f

    fun stairBand(dark: Boolean): Color =
        if (dark) Color.White.copy(alpha = 0.32f) else Color(0.62f, 0.62f, 0.62f, 0.9f)

    fun stairTread(dark: Boolean): Color =
        if (dark) Color.Black.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.95f)

    fun idleRail(dark: Boolean): Color =
        if (dark) Color.Black.copy(alpha = 0.55f) else Color(0.45f, 0.45f, 0.45f, 0.8f)

    fun ourRailColor(lineColor: Color): Color {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(lineColor.toArgb(), hsv)
        return Color.hsv(hsv[0], minOf(1f, hsv[1] * 1.1f), hsv[2] * 0.55f)
    }
}

@Composable
@MaplibreComposable
fun StationShapeLayers(
    content: StationOverlayContent,
    detail: StationDetail,
    dark: Boolean,
    idPrefix: String = "lux-station"
) {
    val shown = detail >= StationDetail.TRACKS
    val idleRailJson = remember(content, dark) {
        polygonsJson(content.rails.filter { it.color == null }.map { it.coordinates to StationStyle.idleRail(dark) })
    }
    val platformJson = remember(content, dark) {
        polygonsJson(content.areas.map { area ->
            area.coordinates to (area.color?.copy(alpha = StationStyle.HIGHLIGHTED_PLATFORM_OPACITY)
                ?: StationStyle.platformFill(dark))
        })
    }
    val ourRailJson = remember(content) {
        polygonsJson(content.rails.mapNotNull { rail ->
            rail.color?.let { rail.coordinates to StationStyle.ourRailColor(it) }
        })
    }
    val stairs = content.visibleStairs(detail)
    val stairBandJson = remember(stairs, dark) {
        polygonsJson(stairs.map { it.band to StationStyle.stairBand(dark) })
    }
    val stairTreadJson = remember(stairs, dark) {
        polygonsJson(stairs.filter { it.treads.size >= 3 }.map { it.treads to StationStyle.stairTread(dark) })
    }
    val edgeJson = remember(content) { linesJson(content.lines.map { it.coordinates }) }

    val idleRailSource = rememberGeoJsonSource(GeoJsonData.JsonString(idleRailJson))
    val platformSource = rememberGeoJsonSource(GeoJsonData.JsonString(platformJson))
    val ourRailSource = rememberGeoJsonSource(GeoJsonData.JsonString(ourRailJson))
    val stairBandSource = rememberGeoJsonSource(GeoJsonData.JsonString(stairBandJson))
    val stairTreadSource = rememberGeoJsonSource(GeoJsonData.JsonString(stairTreadJson))
    val edgeSource = rememberGeoJsonSource(GeoJsonData.JsonString(edgeJson))

    FillLayer(
        id = "$idPrefix-idle-rails",
        source = idleRailSource,
        visible = shown,
        color = Feature.get("color").convertToColor()
    )
    FillLayer(
        id = "$idPrefix-platforms",
        source = platformSource,
        visible = shown,
        color = Feature.get("color").convertToColor(),
        outlineColor = const(StationStyle.platformStroke(dark))
    )
    FillLayer(
        id = "$idPrefix-our-rails",
        source = ourRailSource,
        visible = shown,
        color = Feature.get("color").convertToColor()
    )
    FillLayer(
        id = "$idPrefix-stair-bands",
        source = stairBandSource,
        visible = shown,
        color = Feature.get("color").convertToColor()
    )
    FillLayer(
        id = "$idPrefix-stair-treads",
        source = stairTreadSource,
        visible = shown,
        color = Feature.get("color").convertToColor()
    )
    LineLayer(
        id = "$idPrefix-edges",
        source = edgeSource,
        visible = shown,
        color = const(StationStyle.idleEdge(dark)),
        width = const(StationStyle.idleEdgeWidth),
        cap = const(LineCap.Round),
        join = const(LineJoin.Round)
    )
}

@Composable
fun StationSignOverlay(
    content: StationOverlayContent,
    detail: StationDetail,
    projection: MapProjector,
    modifier: Modifier = Modifier
) {
    val access = content.visibleAccess(detail)
    val labels = content.visibleLabels(detail)

    AnnotationOverlay(
        items = access,
        projection = projection,
        positionOf = { it.coordinate },
        modifier = modifier,
        keyOf = { it.id }
    ) { point ->
        StationAccessView(kind = point.kind)
    }

    AnnotationOverlay(
        items = labels,
        projection = projection,
        positionOf = { it.coordinate },
        modifier = modifier,
        keyOf = { it.id },
        anchorOf = { if (it.color != null) AnnotationBottomAnchor else AnnotationCenterAnchor }
    ) { label ->
        StationLabelView(label = label)
    }
}

@Composable
fun StationAccessView(kind: StationLayout.Access.Kind, modifier: Modifier = Modifier) {
    val description = when (kind) {
        StationLayout.Access.Kind.STAIRS -> "Escaliers"
        StationLayout.Access.Kind.ESCALATOR -> "Escalier roulant"
        StationLayout.Access.Kind.ELEVATOR -> "Ascenseur"
    }
    Box(
        modifier = modifier
            .size(17.dp)
            .shadow(1.dp, RoundedCornerShape(3.dp), ambientColor = Color.Black.copy(alpha = 0.2f))
            .background(Color(0.38f, 0.38f, 0.38f), RoundedCornerShape(3.dp))
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .padding(2.dp)
                .size(13.dp)
                .border(1.dp, Color.White, RoundedCornerShape(2.dp))
        )
        when (kind) {
            StationLayout.Access.Kind.STAIRS -> SFSymbol(name = "stairs", size = 9.sp, color = Color.White, weight = 700)
            StationLayout.Access.Kind.ESCALATOR -> Box(contentAlignment = Alignment.Center) {
                SFSymbol(name = "stairs", size = 9.sp, color = Color.White, weight = 700)
                SFSymbol(
                    name = "arrow.up.right",
                    size = 5.sp,
                    color = Color.White,
                    weight = 900,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 2.dp, y = (-2).dp)
                )
            }
            StationLayout.Access.Kind.ELEVATOR ->
                SFSymbol(name = "arrow.up.arrow.down", size = 8.sp, color = Color.White, weight = 700)
        }
    }
}

private val CalloutPointerShape = GenericShape { size, _ ->
    moveTo(0f, 0f)
    lineTo(size.width, 0f)
    lineTo(size.width / 2f, size.height)
    close()
}

@Composable
fun StationLabelView(label: StationOverlayContent.Label, modifier: Modifier = Modifier) {
    val color = label.color
    val numberStyle = LuxTypography.timeVariant(
        TextStyle(fontWeight = FontWeight.Bold, color = Color.White)
    )
    Box(modifier.semantics { contentDescription = label.accessibilityText }) {
        if (color == null) {
            Box(
                modifier = Modifier
                    .shadow(1.5.dp, RoundedCornerShape(4.dp), ambientColor = Color.Black.copy(alpha = 0.2f))
                    .background(Color.White, RoundedCornerShape(4.dp))
                    .padding(1.dp)
                    .background(StationStyle.signBlue, RoundedCornerShape(3.dp))
                    .defaultMinSize(minWidth = 17.dp, minHeight = 17.dp)
                    .padding(horizontal = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = label.text, style = numberStyle.copy(fontSize = 11.sp), maxLines = 1)
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy((-1).dp)
            ) {
                Box(
                    modifier = Modifier
                        .shadow(2.dp, RoundedCornerShape(5.dp), ambientColor = Color.Black.copy(alpha = 0.25f))
                        .background(color, RoundedCornerShape(5.dp))
                        .padding(2.dp)
                        .background(StationStyle.signBlue, RoundedCornerShape(3.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = (if (label.text.toIntOrNull() != null) "Voie" else "Quai").uppercase(),
                            style = TextStyle(fontSize = 8.sp, fontWeight = FontWeight.SemiBold, color = Color.White),
                            maxLines = 1
                        )
                        Text(text = label.text, style = numberStyle.copy(fontSize = 15.sp), maxLines = 1)
                    }
                }
                Box(
                    Modifier
                        .size(width = 10.dp, height = 6.dp)
                        .background(color, CalloutPointerShape)
                )
            }
        }
    }
}

private fun polygonsJson(polygons: List<Pair<List<LatLng>, Color>>): String = buildJsonObject {
    put("type", "FeatureCollection")
    putJsonArray("features") {
        polygons.forEach { (ring, color) ->
            if (ring.size < 3) return@forEach
            addJsonObject {
                put("type", "Feature")
                putJsonObject("properties") { put("color", color.toCssColorString()) }
                putJsonObject("geometry") {
                    put("type", "Polygon")
                    putJsonArray("coordinates") {
                        addJsonArray {
                            (ring + ring.first()).forEach { point ->
                                addJsonArray {
                                    add(point.longitude)
                                    add(point.latitude)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}.toString()

internal fun linesJson(lines: List<List<LatLng>>, colors: List<Color>? = null): String = buildJsonObject {
    put("type", "FeatureCollection")
    putJsonArray("features") {
        lines.forEachIndexed { index, line ->
            if (line.size < 2) return@forEachIndexed
            addJsonObject {
                put("type", "Feature")
                putJsonObject("properties") {
                    colors?.getOrNull(index)?.let { put("color", it.toCssColorString()) }
                }
                putJsonObject("geometry") {
                    put("type", "LineString")
                    putJsonArray("coordinates") {
                        line.forEach { point ->
                            addJsonArray {
                                add(point.longitude)
                                add(point.latitude)
                            }
                        }
                    }
                }
            }
        }
    }
}.toString()

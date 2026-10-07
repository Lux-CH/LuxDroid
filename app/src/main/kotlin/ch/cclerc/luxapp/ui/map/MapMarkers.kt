package ch.cclerc.luxapp.ui.map

import android.util.DisplayMetrics
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ch.cclerc.luxapp.domain.map.LatLng
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.maplibre.compose.expressions.dsl.Feature
import org.maplibre.compose.expressions.ast.Expression
import org.maplibre.compose.expressions.dsl.asString
import org.maplibre.compose.expressions.dsl.case
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.convertToNumber
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.expressions.dsl.offset
import org.maplibre.compose.expressions.dsl.switch
import org.maplibre.compose.expressions.value.IconPitchAlignment
import org.maplibre.compose.expressions.value.IconRotationAlignment
import org.maplibre.compose.expressions.value.ImageValue
import org.maplibre.compose.expressions.value.SymbolAnchor
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.GeoJsonSource
import org.maplibre.compose.sources.Source
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.util.ClickResult
import org.maplibre.compose.util.MaplibreComposable

class MarkerImageStore {
    internal val requests: SnapshotStateMap<String, Map<String, @Composable () -> Unit>> = mutableStateMapOf()
    internal val images: SnapshotStateMap<String, ImageBitmap> = mutableStateMapOf()
    private val displayed = mutableMapOf<String, Set<String>>()

    internal fun request(layerId: String, contents: Map<String, @Composable () -> Unit>) {
        val previous = requests[layerId]
        if (previous != null && previous.keys == contents.keys) return
        requests[layerId] = contents
        prune()
    }

    internal fun release(layerId: String) {
        requests.remove(layerId)
        displayed.remove(layerId)
        prune()
    }

    internal fun show(layerId: String, names: Set<String>) {
        displayed[layerId] = names
    }

    // Unused images are kept for a while so a key flipping back (a sector becoming covered and not) never
    // waits for a recapture; only the surplus beyond MAX_UNUSED_IMAGES is dropped.
    private fun prune() {
        val wanted = HashSet<String>()
        requests.values.forEach { wanted.addAll(it.keys) }
        displayed.values.forEach { wanted.addAll(it) }
        val unused = images.keys.filter { it !in wanted }
        if (unused.size <= MAX_UNUSED_IMAGES) return
        unused.take(unused.size - MAX_UNUSED_IMAGES).forEach { images.remove(it) }
    }
}

private const val MAX_UNUSED_IMAGES = 120

@Composable
fun rememberMarkerImageStore(): MarkerImageStore = remember { MarkerImageStore() }

@Composable
fun MarkerImageHost(store: MarkerImageStore) {
    val pending = store.requests.values
        .flatMap { it.entries }
        .associate { it.key to it.value }
        .filterKeys { it !in store.images }
    Box(Modifier.size(0.dp)) {
        pending.forEach { (imageKey, content) ->
            key(imageKey) { MarkerCapture(imageKey, store, content) }
        }
    }
}

@Composable
private fun MarkerCapture(imageKey: String, store: MarkerImageStore, content: @Composable () -> Unit) {
    val layer = rememberGraphicsLayer()
    val density = LocalDensity.current.density
    val recorded = remember { Channel<Unit>(Channel.CONFLATED) }
    Box(
        Modifier
            .wrapContentSize(align = Alignment.TopStart, unbounded = true)
            .drawWithContent {
                layer.record { this@drawWithContent.drawContent() }
                recorded.trySend(Unit)
            }
    ) {
        Box(Modifier.padding(MarkerPadding)) { content() }
    }
    LaunchedEffect(imageKey) {
        recorded.receive()
        val bitmap = layer.toImageBitmap().asAndroidBitmap()
        val copy = bitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
        copy.density = (density * DisplayMetrics.DENSITY_DEFAULT).toInt()
        store.images[imageKey] = copy.asImageBitmap()
    }
}

private val MarkerPadding = 4.dp

private object MarkerLayerStyle {
    val allowOverlap = const(true)
    val sortKey = Feature.get("sort").convertToNumber()
    val icon = Feature.get("icon").asString()
    val rotate = Feature.get("rotate").convertToNumber()
}

enum class MarkerAnchor(internal val symbol: SymbolAnchor) {
    Center(SymbolAnchor.Center),
    Bottom(SymbolAnchor.Bottom),
    Top(SymbolAnchor.Top)
}

private fun markerImageName(layerId: String, imageKey: String) = "$layerId/$imageKey"

// maplibre-compose adds an image() bitmap to the style while some layer expression references it, and
// removes it when the last one goes. A marker layer's icon switch is rebuilt whenever its set of images
// changes, which dropped every image of the layer to zero references for a moment: they were removed,
// re-added under new ids and the whole layer blinked. Each shown image is therefore also held by an
// invisible layer whose expression never changes, so the style keeps it for as long as it is on screen.
@Composable
@MaplibreComposable
private fun MarkerImageKeepers(layerId: String, source: Source, icons: Map<String, Expression<ImageValue>>) {
    icons.forEach { (name, icon) ->
        key(name) {
            SymbolLayer(id = "$layerId/keep/$name", source = source, visible = false, iconImage = icon)
        }
    }
}

@Composable
private fun rememberMarkerIcons(store: MarkerImageStore, names: Collection<String>): Map<String, Expression<ImageValue>> {
    val cache = remember { HashMap<ImageBitmap, Expression<ImageValue>>() }
    return names.associateWith { name ->
        val bitmap = store.images.getValue(name)
        cache.getOrPut(bitmap) { image(bitmap) }
    }.also { icons -> cache.keys.retainAll(icons.keys.mapTo(HashSet()) { store.images.getValue(it) }) }
}

@Composable
private fun RequestMarkerImages(id: String, store: MarkerImageStore, contents: Map<String, @Composable () -> Unit>) {
    SideEffect { store.request(id, contents) }
    DisposableEffect(id) { onDispose { store.release(id) } }
}

@Composable
@MaplibreComposable
fun <T> MarkerLayer(
    id: String,
    store: MarkerImageStore,
    items: List<T>,
    positionOf: (T) -> LatLng,
    imageKeyOf: (T) -> String,
    anchor: MarkerAnchor = MarkerAnchor.Center,
    offsetY: Dp = 0.dp,
    sortKeyOf: ((T) -> Double)? = null,
    onClick: ((T) -> Unit)? = null,
    content: @Composable (T) -> Unit
) {
    val imageKeys = items.map { markerImageName(id, imageKeyOf(it)) }
    val keySignature = imageKeys.distinct().joinToString("\u0001")
    val latestItems by rememberUpdatedState(items)
    val latestContent by rememberUpdatedState(content)
    val contents = remember(keySignature) {
        imageKeys.withIndex().associate { (index, imageKey) ->
            val item = items[index]
            imageKey to @Composable { latestContent(item) }
        }
    }
    RequestMarkerImages(id, store, contents)

    // A marker whose image key changed keeps showing its previous image until the new one is captured,
    // instead of disappearing for the frames the capture takes.
    val shown = remember { HashMap<LatLng, String>() }
    val names = items.mapIndexed { index, item ->
        imageKeys[index].takeIf { it in store.images }
            ?: shown[positionOf(item)]?.takeIf { it in store.images }
    }
    val shownNames = names.filterNotNullTo(LinkedHashSet())
    SideEffect {
        shown.clear()
        items.forEachIndexed { index, item -> names[index]?.let { shown[positionOf(item)] = it } }
        store.show(id, shownNames)
    }

    val featureSignature = buildString {
        items.forEachIndexed { index, item ->
            val name = names[index] ?: return@forEachIndexed
            val position = positionOf(item)
            append(name).append('@').append(position.latitude).append(',').append(position.longitude)
            append('#').append(sortKeyOf?.invoke(item) ?: index.toDouble()).append(';')
        }
    }
    val json = remember(featureSignature) {
        buildJsonObject {
            put("type", "FeatureCollection")
            putJsonArray("features") {
                items.forEachIndexed { index, item ->
                    val name = names[index] ?: return@forEachIndexed
                    val position = positionOf(item)
                    addJsonObject {
                        put("type", "Feature")
                        putJsonObject("properties") {
                            put("index", index)
                            put("icon", name)
                            put("sort", sortKeyOf?.invoke(item) ?: index.toDouble())
                        }
                        putJsonObject("geometry") {
                            put("type", "Point")
                            putJsonArray("coordinates") {
                                add(position.longitude)
                                add(position.latitude)
                            }
                        }
                    }
                }
            }
        }.toString()
    }
    val source = rememberGeoJsonSource(GeoJsonData.JsonString(json))

    val icons = rememberMarkerIcons(store, shownNames.sorted())
    MarkerImageKeepers(id, source, icons)
    val iconImage = remember(icons) {
        if (icons.isEmpty()) {
            image(NO_IMAGE)
        } else {
            val cases = icons.map { (name, icon) -> case(name, icon) }.toTypedArray()
            switch(MarkerLayerStyle.icon, *cases, fallback = image(NO_IMAGE))
        }
    }
    val iconOffset = remember(anchor, offsetY) { markerOffset(anchor, offsetY) }
    SymbolLayer(
        id = id,
        source = source,
        iconImage = iconImage,
        iconAnchor = remember(anchor) { const(anchor.symbol) },
        iconOffset = iconOffset,
        iconAllowOverlap = MarkerLayerStyle.allowOverlap,
        iconIgnorePlacement = MarkerLayerStyle.allowOverlap,
        sortKey = MarkerLayerStyle.sortKey,
        onClick = onClick?.let { handler ->
            { features ->
                val index = features.firstNotNullOfOrNull { (it.properties?.get("index") as? JsonPrimitive)?.content?.toIntOrNull() }
                val item = index?.let { latestItems.getOrNull(it) }
                if (item != null) {
                    handler(item)
                    ClickResult.Consume
                } else {
                    ClickResult.Pass
                }
            }
        }
    )
}

private fun markerOffset(anchor: MarkerAnchor, offsetY: Dp) = offset(
    0.dp,
    offsetY + when (anchor) {
        MarkerAnchor.Bottom -> MarkerPadding
        MarkerAnchor.Top -> -MarkerPadding
        MarkerAnchor.Center -> 0.dp
    }
)

/**
 * Draws [content] at every point of a source the caller feeds itself, typically from a frame loop
 * through [GeoJsonSource.setData], so moving markers render in the same frame as the camera instead of
 * trailing it in a Compose overlay. Points may carry a "rotate" property in degrees.
 */
@Composable
@MaplibreComposable
fun MarkerSourceLayer(
    id: String,
    store: MarkerImageStore,
    source: GeoJsonSource,
    imageKey: String,
    anchor: MarkerAnchor = MarkerAnchor.Center,
    rotatesWithMap: Boolean = false,
    flat: Boolean = false,
    content: @Composable () -> Unit
) {
    val name = markerImageName(id, imageKey)
    val latestContent by rememberUpdatedState(content)
    val contents = remember(name) { mapOf<String, @Composable () -> Unit>(name to { latestContent() }) }
    RequestMarkerImages(id, store, contents)

    var shown by remember { mutableStateOf<String?>(null) }
    val current = name.takeIf { it in store.images } ?: shown?.takeIf { it in store.images }
    SideEffect {
        shown = current
        store.show(id, setOfNotNull(current))
    }
    val bitmap = current?.let { store.images.getValue(it) }

    SymbolLayer(
        id = id,
        source = source,
        visible = bitmap != null,
        iconImage = remember(bitmap) { if (bitmap != null) image(bitmap) else image(NO_IMAGE) },
        iconAnchor = remember(anchor) { const(anchor.symbol) },
        iconOffset = remember(anchor) { markerOffset(anchor, 0.dp) },
        iconRotate = MarkerLayerStyle.rotate,
        iconRotationAlignment = const(if (rotatesWithMap) IconRotationAlignment.Map else IconRotationAlignment.Viewport),
        iconPitchAlignment = const(if (flat) IconPitchAlignment.Map else IconPitchAlignment.Viewport),
        iconAllowOverlap = MarkerLayerStyle.allowOverlap,
        iconIgnorePlacement = MarkerLayerStyle.allowOverlap
    )
}

/** A point feature for [MarkerSourceLayer] sources, written by hand since it is rebuilt every frame. */
fun markerPointJson(position: LatLng?, rotate: Double = 0.0): String =
    if (position == null) {
        EMPTY_FEATURES
    } else {
        "{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"properties\":{\"rotate\":$rotate}," +
            "\"geometry\":{\"type\":\"Point\",\"coordinates\":[${position.longitude},${position.latitude}]}}]}"
    }

private const val NO_IMAGE = ""

const val EMPTY_FEATURES = "{\"type\":\"FeatureCollection\",\"features\":[]}"

val EmptyGeoJson = GeoJsonData.JsonString(EMPTY_FEATURES)

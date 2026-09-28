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
import org.maplibre.compose.expressions.dsl.asString
import org.maplibre.compose.expressions.dsl.case
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.convertToNumber
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.expressions.dsl.offset
import org.maplibre.compose.expressions.dsl.switch
import org.maplibre.compose.expressions.value.SymbolAnchor
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.util.ClickResult
import org.maplibre.compose.util.MaplibreComposable

class MarkerImageStore {
    internal val requests: SnapshotStateMap<String, Map<String, @Composable () -> Unit>> = mutableStateMapOf()
    internal val images: SnapshotStateMap<String, ImageBitmap> = mutableStateMapOf()

    internal fun request(layerId: String, contents: Map<String, @Composable () -> Unit>) {
        val previous = requests[layerId]
        if (previous != null && previous.keys == contents.keys) return
        requests[layerId] = contents
        val wanted = requests.values.flatMap { it.keys }.toSet()
        images.keys.filter { it !in wanted }.forEach { images.remove(it) }
    }

    internal fun release(layerId: String) {
        requests.remove(layerId)
        val wanted = requests.values.flatMap { it.keys }.toSet()
        images.keys.filter { it !in wanted }.forEach { images.remove(it) }
    }
}

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

private val EmptyMarkerImage: ImageBitmap = ImageBitmap(1, 1)

private object MarkerLayerStyle {
    val allowOverlap = const(true)
    val sortKey = Feature.get("sort").convertToNumber()
    val icon = Feature.get("icon").asString()
}

enum class MarkerAnchor(internal val symbol: SymbolAnchor) {
    Center(SymbolAnchor.Center),
    Bottom(SymbolAnchor.Bottom),
    Top(SymbolAnchor.Top)
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
    val imageKeys = items.map { "$id/${imageKeyOf(it)}" }
    val keySignature = imageKeys.distinct().joinToString("\u0001")
    val latestItems by rememberUpdatedState(items)
    val latestContent by rememberUpdatedState(content)
    val contents = remember(keySignature) {
        imageKeys.withIndex().associate { (index, imageKey) ->
            val item = items[index]
            imageKey to @Composable { latestContent(item) }
        }
    }
    SideEffect { store.request(id, contents) }
    DisposableEffect(id) { onDispose { store.release(id) } }

    val readyKeys = contents.keys.filter { it in store.images }
    val readySignature = readyKeys.joinToString("\u0001")
    val featureSignature = buildString {
        items.forEachIndexed { index, item ->
            val position = positionOf(item)
            append(imageKeys[index]).append('@').append(position.latitude).append(',').append(position.longitude)
            append('#').append(sortKeyOf?.invoke(item) ?: index.toDouble()).append(';')
        }
    }
    val json = remember(featureSignature, readySignature) {
        val ready = readyKeys.toSet()
        buildJsonObject {
            put("type", "FeatureCollection")
            putJsonArray("features") {
                items.forEachIndexed { index, item ->
                    val imageKey = imageKeys[index]
                    if (imageKey !in ready) return@forEachIndexed
                    val position = positionOf(item)
                    addJsonObject {
                        put("type", "Feature")
                        putJsonObject("properties") {
                            put("index", index)
                            put("icon", imageKey)
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

    val iconImage = remember(readySignature) {
        if (readyKeys.isEmpty()) {
            image(EmptyMarkerImage)
        } else {
            val cases = readyKeys.map { imageKey -> case(imageKey, image(store.images.getValue(imageKey))) }.toTypedArray()
            switch(MarkerLayerStyle.icon, *cases, fallback = image(EmptyMarkerImage))
        }
    }
    val iconOffset = remember(anchor, offsetY) {
        offset(
            0.dp,
            offsetY + when (anchor) {
                MarkerAnchor.Bottom -> MarkerPadding
                MarkerAnchor.Top -> -MarkerPadding
                MarkerAnchor.Center -> 0.dp
            }
        )
    }
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


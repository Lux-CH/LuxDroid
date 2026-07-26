package ch.cclerc.luxapp.ui.map

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import ch.cclerc.luxapp.ui.theme.LuxTheme
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

@Immutable
data class LuxMapStyle(
    val isDark: Boolean,
    val json: String,
    val firstLabelLayerId: String?
)

object MapStyleProvider {

    const val LIGHT_STYLE_ASSET = "map_styles/liberty_nopoi.json"
    const val DARK_STYLE_ASSET = "map_styles/dark_nopoi.json"

    private val parser = Json { ignoreUnknownKeys = true; isLenient = true }
    private val cache = HashMap<Boolean, LuxMapStyle>()

    fun assetPath(dark: Boolean): String = if (dark) DARK_STYLE_ASSET else LIGHT_STYLE_ASSET

    fun style(context: Context, dark: Boolean): LuxMapStyle = synchronized(cache) {
        cache.getOrPut(dark) {
            val json = context.applicationContext.assets
                .open(assetPath(dark))
                .bufferedReader()
                .use { it.readText() }
            LuxMapStyle(isDark = dark, json = json, firstLabelLayerId = firstSymbolLayerId(json))
        }
    }

    fun styleJson(context: Context, dark: Boolean): String = style(context, dark).json

    private fun firstSymbolLayerId(json: String): String? = runCatching {
        val layers = parser.parseToJsonElement(json).jsonObject["layers"] as? JsonArray
        val symbol = layers?.firstOrNull { element ->
            val layer = element as? JsonObject
            (layer?.get("type") as? JsonPrimitive)?.content == "symbol"
        } as? JsonObject
        (symbol?.get("id") as? JsonPrimitive)?.content
    }.getOrNull()
}

@Composable
fun rememberLuxMapStyle(dark: Boolean = LuxTheme.isDark): LuxMapStyle {
    val context = LocalContext.current
    return remember(context.applicationContext, dark) {
        MapStyleProvider.style(context, dark)
    }
}

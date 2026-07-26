package ch.cclerc.luxapp.domain

import android.content.Context
import android.content.res.AssetManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

object ConnectionService {
    private const val ASSET_NAME = "connections.json"
    private const val CACHE_LIMIT = 100

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = MapSerializer(String.serializer(), ListSerializer(String.serializer()))

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cacheMutex = Mutex()
    private val loadedConnections = LinkedHashMap<String, List<String>>()

    private var assets: AssetManager? = null
    private var loading: Deferred<Map<String, List<String>>>? = null

    fun init(context: Context) {
        assets = context.applicationContext.assets
    }

    fun cleanStopId(stopId: String): String = stopId
        .replace("ch-opentransportdataswiss26", "ch")
        .replace("ch_Parent", "ch_")

    fun sanitizedStopId(stopId: String): String? {
        if (stopId.isEmpty()) return null
        return stopId.substringBefore(":")
    }

    suspend fun connections(stopId: String): List<String> {
        val key = cleanStopId(stopId)

        cacheMutex.withLock { loadedConnections[key] }?.let { cached ->
            return LineScoreManager.shared.getSortedRouteNames(cached)
        }

        val all = allConnections()
        val result = all[key] ?: emptyList()

        cacheMutex.withLock {
            if (loadedConnections.size > CACHE_LIMIT) {
                loadedConnections.keys.firstOrNull()?.let { loadedConnections.remove(it) }
            }
            loadedConnections[key] = result
        }

        return LineScoreManager.shared.getSortedRouteNames(result)
    }

    fun clearCache() {
        scope.launch {
            cacheMutex.withLock { loadedConnections.clear() }
        }
    }

    private suspend fun allConnections(): Map<String, List<String>> {
        val existing = loading
        if (existing != null) return existing.await()
        val started = cacheMutex.withLock {
            loading ?: scope.async { loadFromAssets() }.also { loading = it }
        }
        return started.await()
    }

    private suspend fun loadFromAssets(): Map<String, List<String>> = withContext(Dispatchers.IO) {
        val manager = assets ?: return@withContext emptyMap()
        runCatching {
            manager.open(ASSET_NAME).bufferedReader().use { it.readText() }
        }.mapCatching { text ->
            json.decodeFromString(serializer, text)
        }.getOrDefault(emptyMap())
    }
}

@Composable
fun rememberConnections(stopId: String): State<List<String>> {
    val state = remember(stopId) { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(stopId) {
        state.value = ConnectionService.connections(stopId)
    }
    return state
}

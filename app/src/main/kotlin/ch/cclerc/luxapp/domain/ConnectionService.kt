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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class StopConnection(val line: String, val agency: String?)

object ConnectionService {
    private const val ASSET_NAME = "connections.json"
    private const val CACHE_LIMIT = 100

    private val json = Json { ignoreUnknownKeys = true }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cacheMutex = Mutex()
    private val loadedConnections = LinkedHashMap<String, List<StopConnection>>()

    private var assets: AssetManager? = null
    private var loading: Deferred<Map<String, List<StopConnection>>>? = null

    fun init(context: Context) {
        assets = context.applicationContext.assets
    }

    fun cleanStopId(stopId: String): String = stopId
        .replace("ch-opentransportdataswiss26", "ch")
        .replace("ch_Parent", "ch_")

    suspend fun connections(stopId: String): List<StopConnection> {
        val key = cleanStopId(stopId)

        cacheMutex.withLock { loadedConnections[key] }?.let { cached ->
            return sorted(cached)
        }

        val all = allConnections()
        val result = all[key] ?: emptyList()

        cacheMutex.withLock {
            if (loadedConnections.size > CACHE_LIMIT) {
                loadedConnections.keys.firstOrNull()?.let { loadedConnections.remove(it) }
            }
            loadedConnections[key] = result
        }

        return sorted(result)
    }

    private fun sorted(connections: List<StopConnection>): List<StopConnection> {
        val order = LineScoreManager.shared.getSortedRouteNames(connections.map { it.line })
        val rank = HashMap<String, Int>()
        order.forEachIndexed { index, line -> rank.putIfAbsent(line, index) }
        return connections.sortedBy { rank[it.line] ?: Int.MAX_VALUE }
    }

    fun warmUp() {
        scope.launch { allConnections() }
    }

    fun clearCache() {
        scope.launch {
            cacheMutex.withLock { loadedConnections.clear() }
        }
    }

    private suspend fun allConnections(): Map<String, List<StopConnection>> {
        val existing = loading
        if (existing != null) return existing.await()
        val started = cacheMutex.withLock {
            loading ?: scope.async { loadFromAssets() }.also { loading = it }
        }
        return started.await()
    }

    private suspend fun loadFromAssets(): Map<String, List<StopConnection>> = withContext(Dispatchers.IO) {
        val manager = assets ?: return@withContext emptyMap()
        runCatching {
            manager.open(ASSET_NAME).bufferedReader().use { it.readText() }
        }.mapCatching { text ->
            json.parseToJsonElement(text).jsonObject.mapValues { (_, entries) ->
                entries.jsonArray.mapNotNull { entry ->
                    when (entry) {
                        is JsonArray -> entry.firstOrNull()?.jsonPrimitive?.contentOrNull?.let { line ->
                            StopConnection(line, entry.getOrNull(1)?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() })
                        }
                        is JsonPrimitive -> entry.contentOrNull?.let { StopConnection(it, null) }
                        else -> null
                    }
                }
            }
        }.getOrDefault(emptyMap())
    }
}

@Composable
fun rememberConnections(stopId: String): State<List<StopConnection>> {
    val state = remember(stopId) { mutableStateOf(emptyList<StopConnection>()) }
    LaunchedEffect(stopId) {
        state.value = ConnectionService.connections(stopId)
    }
    return state
}

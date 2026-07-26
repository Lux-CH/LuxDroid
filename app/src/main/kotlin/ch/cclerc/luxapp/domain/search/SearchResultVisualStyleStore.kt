package ch.cclerc.luxapp.domain.search

import androidx.compose.ui.graphics.Color
import ch.cclerc.luxapp.data.AppDirectories
import ch.cclerc.luxapp.ui.theme.LuxColors
import java.io.File
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

@Serializable
enum class SearchResultVisualColor {
    @SerialName("accent")
    ACCENT,
    @SerialName("red")
    RED,
    @SerialName("orange")
    ORANGE,
    @SerialName("yellow")
    YELLOW,
    @SerialName("blue")
    BLUE,
    @SerialName("teal")
    TEAL,
    @SerialName("indigo")
    INDIGO,
    @SerialName("mint")
    MINT,
    @SerialName("cyan")
    CYAN,
    @SerialName("brown")
    BROWN,
    @SerialName("purple")
    PURPLE,
    @SerialName("green")
    GREEN,
    @SerialName("pink")
    PINK,
    @SerialName("gray")
    GRAY;

    fun resolve(colors: LuxColors, accent: Color): Color = when (this) {
        ACCENT -> accent
        RED -> colors.systemRed
        ORANGE -> colors.systemOrange
        YELLOW -> colors.systemYellow
        BLUE -> colors.systemBlue
        TEAL -> colors.systemTeal
        INDIGO -> colors.systemIndigo
        MINT -> if (colors.isDark) Color(0xFF63E6E2) else Color(0xFF00C7BE)
        CYAN -> colors.systemCyan
        BROWN -> if (colors.isDark) Color(0xFFAC8E68) else Color(0xFFA2845E)
        PURPLE -> colors.systemPurple
        GREEN -> colors.systemGreen
        PINK -> colors.systemPink
        GRAY -> colors.systemGray
    }
}

@Serializable
data class SearchResultVisualStyle(
    val symbolName: String,
    val colorKey: SearchResultVisualColor
)

@Serializable
private data class PersistedStyleEntry(
    val style: SearchResultVisualStyle,
    val updatedAt: String
)

object SearchResultVisualStyleStore {

    private const val MAX_STORED_STYLES = 750
    private const val MAX_STYLE_AGE_MS = 60L * 60L * 24L * 45L * 1000L
    private const val PERSIST_DEBOUNCE_MS = 2_000L
    private const val FILE_NAME = "SearchResultVisualStyles.json"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = MapSerializer(String.serializer(), PersistedStyleEntry.serializer())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val file: File by lazy { File(AppDirectories.base(), FILE_NAME) }

    private var entries: Map<String, PersistedStyleEntry> = emptyMap()
    private var didLoad = false
    private var needsPersist = false
    private var persistJob: Job? = null

    private val _styles = MutableStateFlow<Map<String, SearchResultVisualStyle>>(emptyMap())
    val styles: StateFlow<Map<String, SearchResultVisualStyle>> = _styles.asStateFlow()

    @Synchronized
    fun style(resultId: String): SearchResultVisualStyle? {
        ensureLoaded()
        return entries[resultId]?.style
    }

    @Synchronized
    fun setStyles(newStyles: Map<String, SearchResultVisualStyle>) {
        if (newStyles.isEmpty()) return
        ensureLoaded()

        val now = System.currentTimeMillis()
        val updated = entries.toMutableMap()
        var hasChanges = false

        for ((resultId, style) in newStyles) {
            if (updated[resultId]?.style != style) {
                updated[resultId] = PersistedStyleEntry(style, Instant.ofEpochMilli(now).toString())
                hasChanges = true
            }
        }

        val didPrune = prune(updated, now)
        if (!hasChanges && !didPrune) return

        publish(updated)
        schedulePersist()
    }

    @Synchronized
    fun flush() {
        if (!needsPersist) return
        needsPersist = false
        persistJob?.cancel()
        persistJob = null
        val snapshot = entries
        scope.launch { write(snapshot) }
    }

    private fun ensureLoaded() {
        if (didLoad) return
        didLoad = true

        val now = System.currentTimeMillis()
        val decoded = runCatching {
            if (!file.exists()) return@runCatching null
            json.decodeFromString(serializer, file.readText())
        }.getOrNull() ?: return

        val loaded = decoded.toMutableMap()
        val didPrune = prune(loaded, now)
        publish(loaded)
        if (didPrune) schedulePersist()
    }

    private fun publish(updated: Map<String, PersistedStyleEntry>) {
        entries = updated
        _styles.value = updated.mapValues { it.value.style }
    }

    private fun schedulePersist() {
        needsPersist = true
        persistJob?.cancel()
        persistJob = scope.launch {
            delay(PERSIST_DEBOUNCE_MS)
            persistNow()
        }
    }

    @Synchronized
    private fun snapshotForPersist(): Map<String, PersistedStyleEntry>? {
        if (!needsPersist) return null
        needsPersist = false
        persistJob = null
        return entries
    }

    private fun persistNow() {
        val snapshot = snapshotForPersist() ?: return
        write(snapshot)
    }

    private fun write(snapshot: Map<String, PersistedStyleEntry>) {
        runCatching {
            file.parentFile?.mkdirs()
            val temporary = File(file.parentFile, "$FILE_NAME.tmp")
            temporary.writeText(json.encodeToString(serializer, snapshot))
            if (!temporary.renameTo(file)) {
                file.writeText(json.encodeToString(serializer, snapshot))
                temporary.delete()
            }
        }
    }

    private fun prune(entries: MutableMap<String, PersistedStyleEntry>, referenceMillis: Long): Boolean {
        var didPrune = false
        val expirationMillis = referenceMillis - MAX_STYLE_AGE_MS

        val countBeforeExpiry = entries.size
        val expired = entries.filterValues { millis(it.updatedAt, referenceMillis) < expirationMillis }.keys
        expired.forEach { entries.remove(it) }
        if (entries.size != countBeforeExpiry) didPrune = true

        if (entries.size > MAX_STORED_STYLES) {
            val keysToKeep = entries.entries
                .sortedByDescending { millis(it.value.updatedAt, referenceMillis) }
                .take(MAX_STORED_STYLES)
                .map { it.key }
                .toSet()
            entries.keys.retainAll(keysToKeep)
            didPrune = true
        }

        return didPrune
    }

    private fun millis(iso: String, fallback: Long): Long =
        runCatching { Instant.parse(iso).toEpochMilli() }.getOrDefault(fallback)
}

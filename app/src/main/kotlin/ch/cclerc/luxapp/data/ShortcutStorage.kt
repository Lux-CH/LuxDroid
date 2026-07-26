package ch.cclerc.luxapp.data

import ch.cclerc.luxapp.domain.shortcut.UserShortcut
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

interface ShortcutStorageProtocol {
    val shortcuts: StateFlow<List<UserShortcut>>

    fun loadShortcuts(): List<UserShortcut>
    fun saveShortcuts(shortcuts: List<UserShortcut>)
    fun addShortcut(shortcut: UserShortcut)
    fun updateShortcut(shortcut: UserShortcut)
    fun deleteShortcut(id: String)
    fun moveShortcut(fromIndex: Int, toIndex: Int)
}

class ShortcutStorage(
    private val file: File = File(AppDirectories.base(), "shortcuts.json")
) : ShortcutStorageProtocol {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val serializer = ListSerializer(UserShortcut.serializer())

    private val _shortcuts = MutableStateFlow(loadShortcutsFromDisk())
    override val shortcuts: StateFlow<List<UserShortcut>> = _shortcuts.asStateFlow()

    private fun loadShortcutsFromDisk(): List<UserShortcut> {
        if (!file.exists()) return emptyList()
        return runCatching { json.decodeFromString(serializer, file.readText()) }.getOrDefault(emptyList())
    }

    private fun persistToDisk(shortcuts: List<UserShortcut>) {
        runCatching {
            file.parentFile?.mkdirs()
            val payload = json.encodeToString(serializer, shortcuts)
            val temporary = File(file.parentFile, "${file.name}.tmp")
            temporary.writeText(payload)
            if (!temporary.renameTo(file)) {
                file.writeText(payload)
                temporary.delete()
            }
        }
    }

    override fun loadShortcuts(): List<UserShortcut> = _shortcuts.value

    override fun saveShortcuts(shortcuts: List<UserShortcut>) {
        _shortcuts.value = shortcuts
        persistToDisk(shortcuts)
    }

    override fun addShortcut(shortcut: UserShortcut) {
        saveShortcuts(_shortcuts.value + shortcut)
    }

    override fun updateShortcut(shortcut: UserShortcut) {
        val current = _shortcuts.value
        val index = current.indexOfFirst { it.id == shortcut.id }
        if (index < 0) return
        val updated = current.toMutableList()
        updated[index] = shortcut
        saveShortcuts(updated)
    }

    override fun deleteShortcut(id: String) {
        saveShortcuts(_shortcuts.value.filter { it.id != id })
    }

    override fun moveShortcut(fromIndex: Int, toIndex: Int) {
        val current = _shortcuts.value
        if (fromIndex < 0 || fromIndex >= current.size) return
        if (toIndex < 0 || toIndex > current.size) return
        if (fromIndex == toIndex) return

        val updated = current.toMutableList()
        val shortcut = updated.removeAt(fromIndex)
        val adjustedToIndex = if (toIndex > fromIndex) toIndex - 1 else toIndex
        updated.add(adjustedToIndex, shortcut)
        saveShortcuts(updated)
    }
}

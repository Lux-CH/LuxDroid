package ch.cclerc.luxapp.domain.shortcut

import android.location.Location
import ch.cclerc.luxapp.data.Settings
import ch.cclerc.luxapp.data.ShortcutStorage
import ch.cclerc.luxapp.data.ShortcutStorageProtocol
import ch.cclerc.luxapp.domain.map.LatLng
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ShortcutManager internal constructor(
    private val storage: ShortcutStorageProtocol,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
) {
    companion object {
        const val MAX_VISIBLE_SHORTCUTS = 2

        val shared: ShortcutManager by lazy { ShortcutManager(ShortcutStorage()) }
    }

    val shortcuts: StateFlow<List<UserShortcut>>
        get() = storage.shortcuts

    private val _visibleShortcuts = MutableStateFlow<List<UserShortcut>>(emptyList())
    val visibleShortcuts: StateFlow<List<UserShortcut>> = _visibleShortcuts.asStateFlow()

    private var lastUserLocation: LatLng? = null

    init {
        updateVisibleShortcuts()
        scope.launch {
            storage.shortcuts.collect {
                updateVisibleShortcuts(lastUserLocation)
            }
        }
    }

    fun refreshFor(location: Location?) {
        updateVisibleShortcuts(location?.let { LatLng(it.latitude, it.longitude) })
    }

    fun updateVisibleShortcuts(userLocation: LatLng? = null) {
        lastUserLocation = userLocation
        val current = shortcuts.value

        _visibleShortcuts.value = if (Settings.useTimeBasedRelevance) {
            current
                .mapIndexed { index, shortcut ->
                    shortcut to shortcut.relevanceScore(userLocation = userLocation, originalIndex = index)
                }
                .sortedByDescending { it.second }
                .map { it.first }
                .take(MAX_VISIBLE_SHORTCUTS)
        } else {
            current.take(MAX_VISIBLE_SHORTCUTS)
        }
    }

    fun addShortcut(shortcut: UserShortcut) {
        storage.addShortcut(shortcut)
    }

    fun updateShortcut(shortcut: UserShortcut) {
        storage.updateShortcut(shortcut)
    }

    fun deleteShortcut(id: String) {
        storage.deleteShortcut(id)
    }

    fun moveShortcut(fromIndex: Int, toIndex: Int) {
        storage.moveShortcut(fromIndex, toIndex)
    }
}

package ch.cclerc.luxapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.cclerc.luxapp.core.LocationService
import ch.cclerc.luxapp.domain.shortcut.ShortcutManager
import ch.cclerc.luxapp.domain.shortcut.UserShortcut
import ch.cclerc.luxcom.api.reverseGeocode
import ch.cclerc.luxcom.model.LocationType
import ch.cclerc.luxcom.model.SearchResult
import java.util.Calendar
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ShortcutEditorViewModel(
    private val shortcutManager: ShortcutManager = ShortcutManager.shared
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isEditing = MutableStateFlow(false)
    val isEditing: StateFlow<Boolean> = _isEditing.asStateFlow()

    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name.asStateFlow()

    private val _selectedSymbol = MutableStateFlow("house")
    val selectedSymbol: StateFlow<String> = _selectedSymbol.asStateFlow()

    private val _selectedLocation = MutableStateFlow<SearchResult?>(null)
    val selectedLocation: StateFlow<SearchResult?> = _selectedLocation.asStateFlow()

    private val _hasTimeSchedule = MutableStateFlow(false)
    val hasTimeSchedule: StateFlow<Boolean> = _hasTimeSchedule.asStateFlow()

    private val _selectedDays = MutableStateFlow<Set<UserShortcut.TimeSchedule.Weekday>>(emptySet())
    val selectedDays: StateFlow<Set<UserShortcut.TimeSchedule.Weekday>> = _selectedDays.asStateFlow()

    private val _selectedHour = MutableStateFlow(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
    val selectedHour: StateFlow<Int> = _selectedHour.asStateFlow()

    private val _selectedMinute = MutableStateFlow(Calendar.getInstance().get(Calendar.MINUTE))
    val selectedMinute: StateFlow<Int> = _selectedMinute.asStateFlow()

    private var editedShortcutId: String? = null

    val canSave: Boolean
        get() = _name.value.trim().isNotEmpty() && _selectedLocation.value != null

    fun setupForEditing(shortcut: UserShortcut?) {
        if (shortcut == null) return
        _isEditing.value = true
        editedShortcutId = shortcut.id
        _name.value = shortcut.name
        _selectedSymbol.value = shortcut.symbol

        val schedule = shortcut.timeSchedule
        if (schedule != null) {
            _hasTimeSchedule.value = true
            _selectedDays.value = schedule.daysOfWeek
            _selectedHour.value = schedule.time.hour
            _selectedMinute.value = schedule.time.minute
        }

        _selectedLocation.value = shortcut.toSearchResult()
    }

    fun updateName(value: String) {
        _name.value = value
    }

    fun updateSymbol(value: String) {
        _selectedSymbol.value = value
    }

    fun updateLocation(value: SearchResult?) {
        _selectedLocation.value = value
    }

    fun setHasTimeSchedule(value: Boolean) {
        _hasTimeSchedule.value = value
    }

    fun toggleDay(day: UserShortcut.TimeSchedule.Weekday) {
        val current = _selectedDays.value
        _selectedDays.value = if (current.contains(day)) current - day else current + day
    }

    fun updateTime(hour: Int, minute: Int) {
        _selectedHour.value = hour
        _selectedMinute.value = minute
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun useCurrentLocation(onResult: (SearchResult?) -> Unit = {}) {
        val location = LocationService.location.value
        if (location == null) {
            _errorMessage.value = "Impossible d'accéder à votre position"
            onResult(null)
            return
        }

        _isLoading.value = true

        viewModelScope.launch {
            try {
                val results = reverseGeocode(lat = location.latitude, lon = location.longitude)
                _isLoading.value = false
                val first = results.firstOrNull()
                val result = if (first != null) {
                    SearchResult(
                        type = LocationType.PLACE,
                        tokens = emptyList(),
                        name = first.name,
                        id = first.id,
                        lat = location.latitude,
                        lon = location.longitude,
                        areas = first.areas,
                        score = 1.0
                    )
                } else {
                    fallbackCurrentLocation(location.latitude, location.longitude)
                }
                _selectedLocation.value = result
                onResult(result)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                _isLoading.value = false
                _errorMessage.value = "Erreur lors de la géolocalisation : ${error.localizedMessage ?: error}"
                val result = fallbackCurrentLocation(location.latitude, location.longitude)
                _selectedLocation.value = result
                onResult(result)
            }
        }
    }

    private fun fallbackCurrentLocation(lat: Double, lon: Double): SearchResult = SearchResult(
        type = LocationType.PLACE,
        tokens = emptyList(),
        name = "Ma position actuelle",
        id = UUID.randomUUID().toString(),
        lat = lat,
        lon = lon,
        areas = emptyList(),
        score = 1.0
    )

    fun convertToSearchResult(shortcut: UserShortcut): SearchResult = shortcut.toSearchResult()

    fun save(): Boolean {
        val location = _selectedLocation.value ?: return false
        val trimmedName = _name.value.trim()
        if (trimmedName.isEmpty()) return false

        val coordinates = UserShortcut.Coordinates(
            latitude = location.lat,
            longitude = location.lon,
            locationName = location.name
        )

        val timeSchedule = if (_hasTimeSchedule.value && _selectedDays.value.isNotEmpty()) {
            UserShortcut.TimeSchedule(
                daysOfWeek = _selectedDays.value,
                time = UserShortcut.TimeSchedule.TimeComponents(
                    hour = _selectedHour.value,
                    minute = _selectedMinute.value
                )
            )
        } else {
            null
        }

        val stopId = if (location.type == LocationType.STOP) location.id else null

        val existingId = editedShortcutId
        if (_isEditing.value && existingId != null) {
            shortcutManager.updateShortcut(
                UserShortcut(
                    id = existingId,
                    name = _name.value,
                    symbol = _selectedSymbol.value,
                    coordinates = coordinates,
                    timeSchedule = timeSchedule,
                    stopId = stopId
                )
            )
        } else {
            shortcutManager.addShortcut(
                UserShortcut(
                    name = _name.value,
                    symbol = _selectedSymbol.value,
                    coordinates = coordinates,
                    timeSchedule = timeSchedule,
                    stopId = stopId
                )
            )
        }

        return true
    }
}

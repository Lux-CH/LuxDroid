package ch.cclerc.luxapp.domain.search

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class PoiOpenState {
    data class Open(val until: String? = null) : PoiOpenState()
    data object OpenAllDay : PoiOpenState()
    data class ClosingSoon(val at: String? = null) : PoiOpenState()
    data class OpeningSoon(val at: String? = null) : PoiOpenState()
    data class Closed(val opensAt: String? = null) : PoiOpenState()
    data object TemporarilyClosed : PoiOpenState()
    data object PermanentlyClosed : PoiOpenState()

    val label: String
        get() = when (this) {
            is Open -> until?.let { "Ouvert jusqu'à $it" } ?: "Ouvert"
            is OpenAllDay -> "Ouvert 24/24"
            is ClosingSoon -> at?.let { "Ferme bientôt (à $it)" } ?: "Ferme bientôt"
            is OpeningSoon -> at?.let { "Ouvre bientôt (à $it)" } ?: "Ouvre bientôt"
            is Closed -> opensAt?.let { "Fermé (ouvre à $it)" } ?: "Fermé"
            is TemporarilyClosed -> "Temporairement fermé"
            is PermanentlyClosed -> "Définitivement fermé"
        }

    val colorKey: SearchResultVisualColor
        get() = when (this) {
            is Open, is OpenAllDay -> SearchResultVisualColor.GREEN
            is ClosingSoon -> SearchResultVisualColor.ORANGE
            is OpeningSoon -> SearchResultVisualColor.YELLOW
            is Closed, is TemporarilyClosed, is PermanentlyClosed -> SearchResultVisualColor.RED
        }
}

object SearchResultOpenStateStore {

    private val _states = MutableStateFlow<Map<String, PoiOpenState>>(emptyMap())
    val states: StateFlow<Map<String, PoiOpenState>> = _states.asStateFlow()

    fun openState(resultId: String): PoiOpenState? = _states.value[resultId]

    fun setOpenStates(newStates: Map<String, PoiOpenState>) {
        if (newStates.isEmpty()) return
        _states.value = _states.value + newStates
    }
}

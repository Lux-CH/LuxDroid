package ch.cclerc.luxapp.domain.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.cclerc.luxapp.core.LocationService
import ch.cclerc.luxcom.api.reverseGeocode
import ch.cclerc.luxcom.model.LocationType
import ch.cclerc.luxcom.model.SearchResult
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class LocationSearchViewModel(
    private val hybridSearchService: HybridLocationSearchService = HybridLocationSearchService()
) : ViewModel() {

    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults: StateFlow<List<SearchResult>> = _searchResults.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _showMinCharactersMessage = MutableStateFlow(false)
    val showMinCharactersMessage: StateFlow<Boolean> = _showMinCharactersMessage.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    var userLocation: Pair<Double, Double>? = null

    private var searchJob: Job? = null

    fun performSearch(query: String) {
        searchJob?.cancel()

        if (query.isEmpty()) {
            _isLoading.value = false
            _searchResults.value = emptyList()
            _showMinCharactersMessage.value = false
            _errorMessage.value = null
            return
        }

        if (query.length < MIN_QUERY_LENGTH) {
            _isLoading.value = false
            _searchResults.value = emptyList()
            _showMinCharactersMessage.value = true
            _errorMessage.value = null
            return
        }

        _showMinCharactersMessage.value = false
        _errorMessage.value = null
        _isLoading.value = true

        searchJob = viewModelScope.launch {
            delay(DEBOUNCE_MILLIS)
            if (!isActive) return@launch

            val location = userLocation

            try {
                val results = hybridSearchService.search(query, location?.first, location?.second)
                if (!isActive) return@launch
                _searchResults.value = filterResults(results)
                _isLoading.value = false
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                if (!isActive) return@launch
                _searchResults.value = emptyList()
                _errorMessage.value = error.message ?: "Recherche indisponible"
                _isLoading.value = false
            }
        }
    }

    fun clear() {
        searchJob?.cancel()
        _searchResults.value = emptyList()
        _isLoading.value = false
        _showMinCharactersMessage.value = false
        _errorMessage.value = null
    }

    suspend fun resolve(result: SearchResult): SearchResult = hybridSearchService.resolve(result)

    fun useCurrentLocation(onResult: (SearchResult?) -> Unit) {
        val location = LocationService.location.value
        if (location == null) {
            onResult(null)
            return
        }

        viewModelScope.launch {
            try {
                val results = reverseGeocode(lat = location.latitude, lon = location.longitude)
                val first = results.firstOrNull()
                if (first != null) {
                    onResult(
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
                    )
                } else {
                    onResult(
                        SearchResult(
                            type = LocationType.PLACE,
                            tokens = emptyList(),
                            name = "Ma position actuelle",
                            id = UUID.randomUUID().toString(),
                            lat = location.latitude,
                            lon = location.longitude,
                            areas = emptyList(),
                            score = 1.0
                        )
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                onResult(null)
            }
        }
    }

    private fun filterResults(results: List<SearchResult>): List<SearchResult> {
        val seenIds = mutableSetOf<String>()
        val deduped = mutableListOf<SearchResult>()

        for (result in results) {
            if (!seenIds.add(result.id)) continue
            deduped.add(result)
        }

        return deduped
    }

    private companion object {
        const val MIN_QUERY_LENGTH = 3
        const val DEBOUNCE_MILLIS = 125L
    }
}

package ch.cclerc.luxapp.viewmodel

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.cclerc.luxapp.core.LocationAuthState
import ch.cclerc.luxapp.core.LocationService
import ch.cclerc.luxapp.core.NetworkMonitor
import ch.cclerc.luxapp.data.LuxStatus
import ch.cclerc.luxapp.data.Progress
import ch.cclerc.luxapp.data.StatusFeed
import ch.cclerc.luxapp.data.WarningMessage
import ch.cclerc.luxcom.api.geocode
import ch.cclerc.luxcom.map.getMapSearchResults
import ch.cclerc.luxcom.model.LocationType
import ch.cclerc.luxcom.model.SearchResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class StopsViewModel : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults: StateFlow<List<SearchResult>> = _searchResults.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isSearchMode = MutableStateFlow(false)
    val isSearchMode: StateFlow<Boolean> = _isSearchMode.asStateFlow()

    private val _showMinCharactersMessage = MutableStateFlow(false)
    val showMinCharactersMessage: StateFlow<Boolean> = _showMinCharactersMessage.asStateFlow()

    private val _isWaitingForLocation = MutableStateFlow(false)
    val isWaitingForLocation: StateFlow<Boolean> = _isWaitingForLocation.asStateFlow()

    private val _maintenanceStatus = MutableStateFlow<LuxStatus?>(null)
    val maintenanceStatus: StateFlow<LuxStatus?> = _maintenanceStatus.asStateFlow()

    private val _warningMessage = MutableStateFlow<WarningMessage?>(null)
    val warningMessage: StateFlow<WarningMessage?> = _warningMessage.asStateFlow()

    val location: StateFlow<Location?> = LocationService.location
    val authorizationState: StateFlow<LocationAuthState> = LocationService.authorizationState
    val isOnline: StateFlow<Boolean> = NetworkMonitor.isOnline

    private var lastFetchedLocation: Location? = null
    private var pendingFetchLocation: Location? = null

    private var backgroundRefreshJob: Job? = null
    private var searchJob: Job? = null
    private var timerJob: Job? = null
    private var observersJob: Job? = null
    private var started = false

    private val isAuthorizationNotAllowed: Boolean
        get() = LocationService.authorizationState.value == LocationAuthState.Denied

    fun start() {
        if (started) return
        started = true

        LocationService.startMonitoring()
        NetworkMonitor.start()

        viewModelScope.launch { _warningMessage.value = StatusFeed.fetchWarningMessage() }

        observersJob = viewModelScope.launch {
            launch {
                var previous = LocationService.location.value
                LocationService.location.collect { newValue ->
                    val wasWaiting = _isWaitingForLocation.value
                    if (wasWaiting && newValue != null) {
                        _isWaitingForLocation.value = false
                        loadNearbyStops(showLoading = true)
                    } else if (!isAuthorizationNotAllowed && newValue !== previous) {
                        checkLocationAndRefresh()
                    }
                    previous = newValue
                }
            }
            launch {
                var first = true
                LocationService.authorizationState.collect {
                    if (first) {
                        first = false
                        return@collect
                    }
                    if (!isAuthorizationNotAllowed && LocationService.location.value != null) {
                        loadNearbyStops(showLoading = true)
                    }
                }
            }
        }

        timerJob = viewModelScope.launch {
            while (isActive) {
                delay(REFRESH_INTERVAL_MS)
                onRefreshTick()
            }
        }

        val current = LocationService.location.value
        if (current == null && !isAuthorizationNotAllowed) {
            _isWaitingForLocation.value = true
        } else if (Progress.searchResults.isEmpty() && !isAuthorizationNotAllowed) {
            loadNearbyStops(showLoading = true)
        } else if (!isAuthorizationNotAllowed) {
            checkLocationAndRefresh()
        }
    }

    fun stop() {
        if (!started) return
        started = false

        LocationService.stopMonitoring()
        NetworkMonitor.stop()

        backgroundRefreshJob?.cancel()
        backgroundRefreshJob = null
        observersJob?.cancel()
        observersJob = null
        timerJob?.cancel()
        timerJob = null
        _isWaitingForLocation.value = false
    }

    fun onEnterForeground() {
        LocationService.resumeUpdates()

        NetworkMonitor.stop()
        NetworkMonitor.start()

        if (!isAuthorizationNotAllowed) {
            if (Progress.searchResults.isEmpty()) {
                loadNearbyStops(showLoading = true)
            } else {
                refreshNearbyStopsInBackground()
            }
        }
    }

    fun reload() {
        publishResults(emptyList())
        lastFetchedLocation = null
        _maintenanceStatus.value = null
        _warningMessage.value = null

        if (!isAuthorizationNotAllowed && LocationService.location.value != null) {
            loadNearbyStops(showLoading = true)
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            performSearch()
        }
    }

    fun resetSearch() {
        searchJob?.cancel()
        searchJob = null
        _searchQuery.value = ""
        _isSearchMode.value = false
        _showMinCharactersMessage.value = false
        loadNearbyStops(showLoading = false)
    }

    private suspend fun performSearch() {
        val query = _searchQuery.value
        _isSearchMode.value = query.isNotEmpty()

        if (query.isEmpty()) {
            publishResults(emptyList())
            _showMinCharactersMessage.value = false
            return
        }

        if (query.length < MIN_SEARCH_CHARACTERS) {
            publishResults(emptyList())
            _showMinCharactersMessage.value = true
            return
        }

        _showMinCharactersMessage.value = false

        cancelBackgroundTasks()

        val current = LocationService.location.value ?: return
        try {
            val results = geocode(
                text = query,
                type = LocationType.STOP,
                place = current.latitude to current.longitude,
                placeBias = 2
            )
            publishResults(results)
            _isLoading.value = false
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            _isLoading.value = false
        }
    }

    fun checkLocationAndRefresh() {
        if (isAuthorizationNotAllowed) return
        val current = LocationService.location.value
        if (current == null) {
            _isWaitingForLocation.value = true
            return
        }

        _isWaitingForLocation.value = false

        val reference = pendingFetchLocation ?: lastFetchedLocation
        if (reference == null) {
            loadNearbyStops(showLoading = false)
            return
        }

        if (current.distanceTo(reference) >= SIGNIFICANT_DISTANCE_M) {
            loadNearbyStops(showLoading = false)
        }
    }

    fun loadNearbyStops(showLoading: Boolean) {
        if (isAuthorizationNotAllowed) return

        val pending = pendingFetchLocation
        val current = LocationService.location.value
        val running = backgroundRefreshJob
        if (pending != null && current != null && running != null && running.isActive &&
            current.distanceTo(pending) < DUPLICATE_FETCH_DISTANCE_M
        ) {
            return
        }

        if (showLoading) _isLoading.value = true
        backgroundRefreshJob?.cancel()

        backgroundRefreshJob = viewModelScope.launch {
            val fetchLocation = LocationService.location.value
            if (fetchLocation == null) {
                if (showLoading) _isLoading.value = false
                if (!isAuthorizationNotAllowed) {
                    _isWaitingForLocation.value = true
                }
                return@launch
            }

            _isWaitingForLocation.value = false
            pendingFetchLocation = fetchLocation

            try {
                val results = getMapSearchResults(
                    currentLat = fetchLocation.latitude,
                    currentLon = fetchLocation.longitude
                )

                val filtered = results.filter { it.lat != 0.0 && it.lon != 0.0 }

                publishResults(filtered)
                lastFetchedLocation = fetchLocation
                pendingFetchLocation = null

                if (filtered.isEmpty() && _maintenanceStatus.value == null) {
                    checkMaintenanceStatus()
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                pendingFetchLocation = null
                if (_maintenanceStatus.value == null) {
                    checkMaintenanceStatus()
                }
            } finally {
                if (showLoading) _isLoading.value = false
                if (backgroundRefreshJob?.isActive != false) {
                    backgroundRefreshJob = null
                }
            }
        }
    }

    fun refreshNearbyStopsInBackground() {
        if (isAuthorizationNotAllowed) return
        val running = backgroundRefreshJob
        if (running != null && running.isActive) return
        loadNearbyStops(showLoading = false)
    }

    fun cancelBackgroundTasks() {
        backgroundRefreshJob?.cancel()
        backgroundRefreshJob = null
    }

    suspend fun checkMaintenanceStatus() {
        _maintenanceStatus.value = StatusFeed.fetchStatus()
    }

    fun refreshMaintenanceStatus() {
        viewModelScope.launch { checkMaintenanceStatus() }
    }

    private fun onRefreshTick() {
        if (isAuthorizationNotAllowed) return
        val current = LocationService.location.value
        val last = lastFetchedLocation
        if (current == null || last == null) {
            if (current != null) {
                refreshNearbyStopsInBackground()
            }
            return
        }
        if (current.distanceTo(last) < SIGNIFICANT_DISTANCE_M) {
            refreshNearbyStopsInBackground()
        }
    }

    private fun publishResults(results: List<SearchResult>) {
        Progress.searchResults = results
        _searchResults.value = results
    }

    override fun onCleared() {
        super.onCleared()
        stop()
    }

    private companion object {
        const val SIGNIFICANT_DISTANCE_M = 100f
        const val DUPLICATE_FETCH_DISTANCE_M = 10f
        const val REFRESH_INTERVAL_MS = 60_000L
        const val SEARCH_DEBOUNCE_MS = 125L
        const val MIN_SEARCH_CHARACTERS = 3
    }
}

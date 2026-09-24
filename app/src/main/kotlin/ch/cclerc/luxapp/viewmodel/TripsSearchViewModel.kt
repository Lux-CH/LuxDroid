package ch.cclerc.luxapp.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.cclerc.luxapp.data.Progress
import ch.cclerc.luxapp.data.Settings
import ch.cclerc.luxapp.domain.search.RouteOptionsStore
import ch.cclerc.luxapp.core.LocationService
import ch.cclerc.luxapp.domain.routing.PlannedRoute
import ch.cclerc.luxapp.domain.routing.PlannedVia
import ch.cclerc.luxapp.domain.routing.StitchedRoutePlanner
import ch.cclerc.luxapp.domain.search.HybridLocationSearchService
import ch.cclerc.luxcom.api.getRoute
import ch.cclerc.luxcom.api.reverseGeocode
import ch.cclerc.luxcom.geo.calculateDistance
import ch.cclerc.luxcom.model.LocationType
import ch.cclerc.luxcom.model.SearchResult
import ch.cclerc.luxcom.model.trip.Itinerary
import ch.cclerc.luxcom.model.trip.RouteOptions
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.math.max
import kotlin.math.min

sealed class SelectedLocation {
    data class SearchResultLocation(val result: SearchResult) : SelectedLocation()
    data object CurrentPosition : SelectedLocation()

    val id: String
        get() = when (this) {
            is SearchResultLocation -> result.id
            CurrentPosition -> "current_position"
        }

    val displayName: String
        get() = when (this) {
            is SearchResultLocation -> result.name
            CurrentPosition -> "Position Actuelle"
        }

    val searchResult: SearchResult?
        get() = (this as? SearchResultLocation)?.result

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SelectedLocation) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

enum class DepartureType(val label: String) {
    LEAVE_AT("Partir à"),
    ARRIVE_BY("Arriver à");

    val id: String get() = label
}

sealed interface SearchField {
    data object FROM : SearchField
    data object TO : SearchField
    data object NONE : SearchField
    data class VIA(val id: String) : SearchField

    val isVia: Boolean get() = this is VIA
}

data class ViaStop(
    val id: String = UUID.randomUUID().toString(),
    val location: SearchResult? = null,
    val minimumStay: Int = 0
)

enum class RoutePreset(val title: String, val symbol: String) {
    FASTEST("Le plus rapide", "bolt.fill"),
    FEWER_TRANSFERS("Moins de changements", "arrow.triangle.swap"),
    LESS_WALKING("Moins de marche", "figure.walk"),
    RELAXED("Correspondances larges", "tortoise.fill");

    fun maxTransfers(base: Int): Int = if (this == FEWER_TRANSFERS) min(base, 1) else base

    fun transferBuffer(base: Int): Int = if (this == RELAXED) max(base, 5) else base

    fun maxAccessWalk(base: Int?): Int? = if (this == LESS_WALKING) min(base ?: 900, 300) else base
}

class TripsSearchViewModel : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true }
    private val historySerializer = ListSerializer(SearchResult.serializer())

    private val _searchHistory = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchHistory: StateFlow<List<SearchResult>> = _searchHistory.asStateFlow()

    private val _fromQuery = MutableStateFlow("")
    val fromQuery: StateFlow<String> = _fromQuery.asStateFlow()

    private val _toQuery = MutableStateFlow("")
    val toQuery: StateFlow<String> = _toQuery.asStateFlow()

    private val _viaQuery = MutableStateFlow("")
    val viaQuery: StateFlow<String> = _viaQuery.asStateFlow()

    private val _vias = MutableStateFlow<List<ViaStop>>(emptyList())
    val vias: StateFlow<List<ViaStop>> = _vias.asStateFlow()

    val maxVias = 2

    private val _routePreset = MutableStateFlow(
        RoutePreset.entries.firstOrNull { it.name == Settings.prefs.getString(ROUTE_PRESET_KEY, null) }
            ?: RoutePreset.FASTEST
    )
    val routePreset: StateFlow<RoutePreset> = _routePreset.asStateFlow()

    private val _isPresetFallback = MutableStateFlow(false)
    val isPresetFallback: StateFlow<Boolean> = _isPresetFallback.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults: StateFlow<List<SearchResult>> = _searchResults.asStateFlow()

    private val _showMinCharactersMessage = MutableStateFlow(false)
    val showMinCharactersMessage: StateFlow<Boolean> = _showMinCharactersMessage.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _fromLocation = MutableStateFlow<SelectedLocation?>(null)
    val fromLocation: StateFlow<SelectedLocation?> = _fromLocation.asStateFlow()

    private val _toLocation = MutableStateFlow<SelectedLocation?>(null)
    val toLocation: StateFlow<SelectedLocation?> = _toLocation.asStateFlow()

    private val _activeField = MutableStateFlow<SearchField>(SearchField.FROM)
    val activeField: StateFlow<SearchField> = _activeField.asStateFlow()

    private val _trips = MutableStateFlow<List<Itinerary>>(emptyList())
    val trips: StateFlow<List<Itinerary>> = _trips.asStateFlow()

    private val _directs = MutableStateFlow<List<Itinerary>>(emptyList())
    val directs: StateFlow<List<Itinerary>> = _directs.asStateFlow()

    private val _isSearchingTrips = MutableStateFlow(false)
    val isSearchingTrips: StateFlow<Boolean> = _isSearchingTrips.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _showTripResults = MutableStateFlow(false)
    val showTripResults: StateFlow<Boolean> = _showTripResults.asStateFlow()

    private val _showSettings = MutableStateFlow(false)
    val showSettings: StateFlow<Boolean> = _showSettings.asStateFlow()

    private val _previousPageCursor = MutableStateFlow<String?>(null)
    val previousPageCursor: StateFlow<String?> = _previousPageCursor.asStateFlow()

    private val _nextPageCursor = MutableStateFlow<String?>(null)
    val nextPageCursor: StateFlow<String?> = _nextPageCursor.asStateFlow()

    private val _isLoadingEarlier = MutableStateFlow(false)
    val isLoadingEarlier: StateFlow<Boolean> = _isLoadingEarlier.asStateFlow()

    private val _isLoadingLater = MutableStateFlow(false)
    val isLoadingLater: StateFlow<Boolean> = _isLoadingLater.asStateFlow()

    private val _isChangingContent = MutableStateFlow(false)
    val isChangingContent: StateFlow<Boolean> = _isChangingContent.asStateFlow()

    private val _animateIn = MutableStateFlow(false)
    val animateIn: StateFlow<Boolean> = _animateIn.asStateFlow()

    private val _routeOptions = MutableStateFlow(RouteOptionsStore.defaults)
    val routeOptions: StateFlow<RouteOptions> = _routeOptions.asStateFlow()

    private val _hasCustomSettings = MutableStateFlow(false)
    val hasCustomSettings: StateFlow<Boolean> = _hasCustomSettings.asStateFlow()

    private val _departureType = MutableStateFlow(DepartureType.LEAVE_AT)
    val departureType: StateFlow<DepartureType> = _departureType.asStateFlow()

    private val _selectedDate = MutableStateFlow<Instant?>(null)
    val selectedDate: StateFlow<Instant?> = _selectedDate.asStateFlow()

    private val _showPastDateWarning = MutableStateFlow(false)
    val showPastDateWarning: StateFlow<Boolean> = _showPastDateWarning.asStateFlow()

    var searchService: HybridLocationSearchService? = null
    var resolvePlace: (suspend (SearchResult) -> SearchResult)? = null

    private var allTrips: List<Itinerary> = emptyList()
    private var currentPageIndex = 0

    private var searchJob: Job? = null
    private var tripsJob: Job? = null
    private var pagingJob: Job? = null

    private val hasMoreEarlier: Boolean get() = !_previousPageCursor.value.isNullOrEmpty()
    private val hasMoreLater: Boolean get() = !_nextPageCursor.value.isNullOrEmpty()

    val showSkeleton: StateFlow<Boolean> = combine(_isSearchingTrips, _trips, _directs) { loading, trips, directs ->
        loading && trips.isEmpty() && directs.isEmpty()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        fetchRouteOptionsPreferences()
        loadSearchHistory()
    }

    val isSearchActive: Boolean
        get() = _activeField.value != SearchField.NONE &&
            (_fromQuery.value.length >= MIN_SEARCH_CHARACTERS ||
                _toQuery.value.length >= MIN_SEARCH_CHARACTERS ||
                _viaQuery.value.length >= MIN_SEARCH_CHARACTERS ||
                _searchResults.value.isNotEmpty() ||
                _showMinCharactersMessage.value)

    val canAddVia: Boolean
        get() = _vias.value.size < maxVias && _vias.value.none { it.location == null }

    val allQueriesEmpty: Boolean
        get() = _fromQuery.value.isEmpty() && _toQuery.value.isEmpty() && _viaQuery.value.isEmpty()

    val canLoadEarlier: Boolean get() = currentPageIndex > 0 || hasMoreEarlier

    val canLoadLater: Boolean
        get() = currentPageIndex < maxPageIndex() || hasMoreLater

    private fun loadSearchHistory() {
        val raw = Settings.prefs.getString(HISTORY_KEY, null)
        if (raw.isNullOrEmpty()) {
            _searchHistory.value = emptyList()
            return
        }
        _searchHistory.value = runCatching { json.decodeFromString(historySerializer, raw) }
            .getOrDefault(emptyList())
    }

    private fun saveSearchHistory() {
        val encoded = runCatching { json.encodeToString(historySerializer, _searchHistory.value) }.getOrNull()
        Settings.prefs.edit().putString(HISTORY_KEY, encoded ?: "").apply()
    }

    fun addToHistory(result: SearchResult) {
        val filtered = _searchHistory.value.filterNot { it.id == result.id }
        _searchHistory.value = (listOf(result) + filtered).take(MAX_HISTORY_ITEMS)
        saveSearchHistory()
    }

    fun removeFromHistory(id: String) {
        _searchHistory.value = _searchHistory.value.filterNot { it.id == id }
        saveSearchHistory()
    }

    fun clearHistory() {
        _searchHistory.value = emptyList()
        Settings.prefs.edit().putString(HISTORY_KEY, "").apply()
    }

    fun fetchRouteOptionsPreferences() {
        _routeOptions.value = RouteOptionsStore.load(_routeOptions.value)
        checkIfSettingsDifferFromDefaults()
    }

    private fun checkIfSettingsDifferFromDefaults() {
        _hasCustomSettings.value = RouteOptionsStore.hasCustomSettings(_routeOptions.value)
    }

    fun updateRouteOptions(options: RouteOptions) {
        _routeOptions.value = options
        RouteOptionsStore.save(options)
        checkIfSettingsDifferFromDefaults()
        if (_fromLocation.value != null && _toLocation.value != null) {
            searchTrips()
        }
    }

    fun setShowSettings(value: Boolean) {
        _showSettings.value = value
    }

    fun resetSearch() {
        _isLoading.value = false
        when (val field = _activeField.value) {
            SearchField.FROM -> setQuery(SearchField.FROM, "")
            SearchField.TO -> setQuery(SearchField.TO, "")
            is SearchField.VIA -> setQuery(field, "")
            SearchField.NONE -> Unit
        }
        _showMinCharactersMessage.value = false
        _searchResults.value = emptyList()
    }

    fun setActiveSearchField(field: SearchField) {
        _activeField.value = field
        if (field.isVia) {
            _showTripResults.value = false
        }
        when (field) {
            SearchField.FROM -> {
                _searchQuery.value = _fromQuery.value
                performSearch(_fromQuery.value)
            }
            SearchField.TO -> {
                _searchQuery.value = _toQuery.value
                performSearch(_toQuery.value)
            }
            is SearchField.VIA -> {
                _searchQuery.value = _viaQuery.value
                performSearch(_viaQuery.value)
            }
            SearchField.NONE -> {
                _searchQuery.value = ""
                _searchResults.value = emptyList()
            }
        }
    }

    fun onQueryChange(query: String) {
        setQuery(_activeField.value, query)
        onChange(query)
    }

    fun onChange(newSearchQuery: String) {
        if (newSearchQuery.isNotEmpty()) {
            _showTripResults.value = false
        }
        if (newSearchQuery.isEmpty()) {
            cancelBackgroundTasks()
            _isLoading.value = false
            _searchResults.value = emptyList()
            _showMinCharactersMessage.value = false
        } else if (newSearchQuery.length < MIN_SEARCH_CHARACTERS) {
            cancelBackgroundTasks()
            _isLoading.value = false
            _showMinCharactersMessage.value = true
            _searchResults.value = emptyList()
        } else {
            _showMinCharactersMessage.value = false
            performSearch(newSearchQuery)
        }
    }

    private fun setQuery(field: SearchField, value: String) {
        when (field) {
            SearchField.FROM -> _fromQuery.value = value
            SearchField.TO -> _toQuery.value = value
            is SearchField.VIA -> _viaQuery.value = value
            SearchField.NONE -> Unit
        }
        if (field == _activeField.value) {
            _searchQuery.value = value
        }
    }

    fun performSearch(query: String) {
        cancelBackgroundTasks()

        if (query.isEmpty()) {
            _isLoading.value = false
            _searchResults.value = emptyList()
            _showMinCharactersMessage.value = false
            return
        }

        if (query.length < MIN_SEARCH_CHARACTERS) {
            _isLoading.value = false
            _searchResults.value = emptyList()
            _showMinCharactersMessage.value = true
            return
        }

        _isLoading.value = true
        _showMinCharactersMessage.value = false

        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)

            val location = LocationService.location.value
            val results = hybridSearch(query, location?.latitude, location?.longitude)

            if (currentSearchQuery() != query) return@launch
            _searchResults.value = results
            _isLoading.value = false
            searchJob = null
        }
    }

    private suspend fun hybridSearch(query: String, lat: Double?, lon: Double?): List<SearchResult> {
        val service = searchService ?: HybridLocationSearchService().also { searchService = it }
        return try {
            service.search(query, lat, lon)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            emptyList()
        }
    }

    private fun currentSearchQuery(): String = when (_activeField.value) {
        SearchField.FROM -> _fromQuery.value
        SearchField.TO -> _toQuery.value
        is SearchField.VIA -> _viaQuery.value
        SearchField.NONE -> ""
    }

    fun cancelBackgroundTasks() {
        searchJob?.cancel()
        searchJob = null
    }

    fun selectLocation(location: SearchResult) {
        viewModelScope.launch {
            val resolver = resolvePlace
            val resolved = if (resolver != null && location.lat == 0.0 && location.lon == 0.0) {
                runCatching { resolver(location) }.getOrDefault(location)
            } else {
                location
            }
            applySelectedLocation(resolved)
        }
    }

    private fun applySelectedLocation(location: SearchResult) {
        val active = _activeField.value
        if (active is SearchField.VIA) {
            addToHistory(location)
            _searchResults.value = emptyList()
            applyViaStop(location, active.id)
            return
        }
        addToHistory(location)
        when (_activeField.value) {
            SearchField.FROM -> {
                _fromLocation.value = SelectedLocation.SearchResultLocation(location)
                setQuery(SearchField.FROM, "")
                setActiveFieldSilently(SearchField.TO)
                if (_toLocation.value != null) searchTrips()
            }
            SearchField.TO -> {
                _toLocation.value = SelectedLocation.SearchResultLocation(location)
                setQuery(SearchField.TO, "")
                setActiveFieldSilently(SearchField.NONE)
                searchTrips()
            }
            is SearchField.VIA, SearchField.NONE -> Unit
        }
        _searchResults.value = emptyList()
    }

    fun selectCurrentPosition() {
        val active = _activeField.value
        if (active is SearchField.VIA) {
            val coordinates = LocationService.location.value ?: return
            _searchResults.value = emptyList()
            viewModelScope.launch {
                val stop = try {
                    reverseGeocode(coordinates.latitude, coordinates.longitude, LocationType.STOP).firstOrNull()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Throwable) {
                    null
                }
                applyViaStop(stop, active.id)
            }
            return
        }
        when (_activeField.value) {
            SearchField.FROM -> {
                _fromLocation.value = SelectedLocation.CurrentPosition
                setQuery(SearchField.FROM, "")
                setActiveFieldSilently(SearchField.TO)
                if (_toLocation.value != null) searchTrips()
            }
            SearchField.TO -> {
                _toLocation.value = SelectedLocation.CurrentPosition
                setQuery(SearchField.TO, "")
                setActiveFieldSilently(SearchField.NONE)
                searchTrips()
            }
            is SearchField.VIA, SearchField.NONE -> Unit
        }
        _searchResults.value = emptyList()
    }

    private fun setActiveFieldSilently(field: SearchField) {
        _activeField.value = field
        _searchQuery.value = currentSearchQuery()
    }

    fun removeFromLocation() {
        _fromLocation.value = null
        setActiveFieldSilently(SearchField.FROM)
        clearResults()
    }

    fun removeToLocation() {
        _toLocation.value = null
        setActiveFieldSilently(SearchField.TO)
        clearResults()
    }

    fun swap() {
        val previousFrom = _fromLocation.value
        _fromLocation.value = _toLocation.value
        _toLocation.value = previousFrom
        _vias.value = _vias.value.reversed()

        if (_fromLocation.value != null && _toLocation.value != null) {
            searchTrips()
        } else {
            clearResults()
        }
    }

    private fun clearResults() {
        _showTripResults.value = false
        _trips.value = emptyList()
        _directs.value = emptyList()
        allTrips = emptyList()
        currentPageIndex = 0
    }

    fun addVia() {
        if (!canAddVia) return
        val via = ViaStop()
        _vias.value = _vias.value + via
        _viaQuery.value = ""
        _showTripResults.value = false
        setActiveSearchField(SearchField.VIA(via.id))
    }

    fun removeVia(id: String) {
        val hadLocation = _vias.value.firstOrNull { it.id == id }?.location != null
        _vias.value = _vias.value.filterNot { it.id == id }
        if (_activeField.value == SearchField.VIA(id)) {
            _viaQuery.value = ""
            _searchResults.value = emptyList()
            _showMinCharactersMessage.value = false
            setActiveFieldSilently(fieldAfterVia())
        }
        if (hadLocation) {
            refreshTripsIfReady()
        } else if (_fromLocation.value != null && _toLocation.value != null) {
            _showTripResults.value = true
        }
    }

    fun setViaMinimumStay(minutes: Int, id: String) {
        val index = _vias.value.indexOfFirst { it.id == id }
        if (index < 0 || _vias.value[index].minimumStay == minutes) return
        _vias.value = _vias.value.toMutableList().also { it[index] = it[index].copy(minimumStay = minutes) }
        if (_vias.value[index].location != null) {
            refreshTripsIfReady()
        }
    }

    fun setRoutePreset(preset: RoutePreset) {
        if (preset == _routePreset.value) return
        _routePreset.value = preset
        Settings.prefs.edit().putString(ROUTE_PRESET_KEY, preset.name).apply()
        refreshTripsIfReady()
    }

    private fun refreshTripsIfReady() {
        if (_fromLocation.value != null && _toLocation.value != null) {
            searchTrips()
        }
    }

    private fun fieldAfterVia(): SearchField = when {
        _toLocation.value == null -> SearchField.TO
        _fromLocation.value == null -> SearchField.FROM
        else -> SearchField.NONE
    }

    private fun applyViaStop(stop: SearchResult?, viaId: String) {
        val index = _vias.value.indexOfFirst { it.id == viaId }
        if (index < 0) return
        if (stop == null) {
            _errorMessage.value = "Aucun arrêt trouvé à proximité de ce lieu"
            return
        }
        _vias.value = _vias.value.toMutableList().also { it[index] = it[index].copy(location = stop) }
        _viaQuery.value = ""
        _searchResults.value = emptyList()
        setActiveFieldSilently(fieldAfterVia())
        refreshTripsIfReady()
    }

    fun isCurrentPositionAvailable(): Boolean = LocationService.location.value != null

    fun setDepartureType(type: DepartureType) {
        _departureType.value = type
    }

    fun setSelectedDate(date: Instant?) {
        _selectedDate.value = date
        updatePastDateWarning()
    }

    fun updatePastDateWarning() {
        val date = _selectedDate.value
        _showPastDateWarning.value = date != null &&
            date.isBefore(Instant.now().minusSeconds(PAST_DATE_TOLERANCE_S))
    }

    fun searchTrips(pageCursor: String? = null) {
        val from = getRouteLocation(_fromLocation.value)
        val to = getRouteLocation(_toLocation.value)
        if (from == null || to == null) {
            _errorMessage.value = "Impossible d'obtenir les coordonnées"
            return
        }

        if (pageCursor == null) {
            _isSearchingTrips.value = true
            allTrips = emptyList()
            currentPageIndex = 0
        }

        _showTripResults.value = true
        updatePastDateWarning()

        val current = _routeOptions.value
        val plannedVias = _vias.value.mapNotNull { via ->
            via.location?.let { PlannedVia(it, via.minimumStay) }
        }
        val needsStitching = StitchedRoutePlanner.needsStitching(plannedVias)
        val viaIds = if (needsStitching) emptyList() else plannedVias.map { it.location.id }
        val viaStays = if (needsStitching) emptyList() else plannedVias.map { it.stay }
        val timeForRequest = _selectedDate.value ?: Instant.now()

        fun options(preset: RoutePreset) = current.copy(
            from = from,
            to = to,
            via = viaIds.ifEmpty { null },
            viaMinimumStay = if (viaStays.any { it > 0 }) viaStays else emptyList(),
            time = timeForRequest,
            arriveBy = _departureType.value == DepartureType.ARRIVE_BY,
            maxTransfers = preset.maxTransfers(current.maxTransfers),
            minTransferTime = preset.transferBuffer(current.minTransferTime),
            numItineraries = 5,
            pageCursor = pageCursor,
            timetableView = true,
            maxPreTransitTime = preset.maxAccessWalk(current.maxPreTransitTime),
            maxPostTransitTime = preset.maxAccessWalk(current.maxPostTransitTime),
            numLegAlternatives = 0
        )

        suspend fun plan(preset: RoutePreset): PlannedRoute = if (needsStitching) {
            StitchedRoutePlanner.plan(options(preset), plannedVias)
        } else {
            PlannedRoute(getRoute(options(preset)))
        }

        val preset = if (pageCursor != null && _isPresetFallback.value) RoutePreset.FASTEST else _routePreset.value

        tripsJob?.cancel()
        tripsJob = viewModelScope.launch {
            try {
                var result = plan(preset)
                var fellBack = false
                if (pageCursor == null && preset != RoutePreset.FASTEST &&
                    result.itineraries.isEmpty() && result.direct.isEmpty()
                ) {
                    result = plan(RoutePreset.FASTEST)
                    fellBack = true
                }
                if (pageCursor == null) {
                    _isPresetFallback.value = fellBack
                }

                val loadingEarlier = _isLoadingEarlier.value
                val loadingLater = _isLoadingLater.value

                if (loadingEarlier) {
                    allTrips = result.itineraries + allTrips
                    currentPageIndex = 0
                } else if (loadingLater) {
                    val oldMaxPageIndex = maxPageIndex()
                    allTrips = allTrips + result.itineraries
                    currentPageIndex = min(oldMaxPageIndex + 1, maxPageIndex())
                } else {
                    allTrips = result.itineraries
                    currentPageIndex = if (_departureType.value == DepartureType.ARRIVE_BY && allTrips.isNotEmpty()) {
                        (allTrips.size - 1) / ITEMS_PER_PAGE
                    } else {
                        0
                    }
                }

                _directs.value = if (plannedVias.isEmpty()) result.direct else emptyList()

                if (loadingEarlier) {
                    _previousPageCursor.value = result.previousPageCursor
                } else if (loadingLater) {
                    _nextPageCursor.value = result.nextPageCursor
                } else {
                    _previousPageCursor.value = result.previousPageCursor
                    _nextPageCursor.value = result.nextPageCursor
                }

                updateDisplayedTrips()

                _isSearchingTrips.value = false
                _isLoadingEarlier.value = false
                _isLoadingLater.value = false
                _isChangingContent.value = false
                _animateIn.value = true
                _errorMessage.value = null
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                Log.d(TAG, "Trip search error", error)
                _errorMessage.value = TRIP_SEARCH_ERROR
                _isSearchingTrips.value = false
                _isLoadingEarlier.value = false
                _isLoadingLater.value = false
                _isChangingContent.value = false
            }
        }
    }

    private fun maxPageIndex(): Int = max(0, (allTrips.size - 1) / ITEMS_PER_PAGE)

    private fun updateDisplayedTrips() {
        val startIndex = currentPageIndex * ITEMS_PER_PAGE
        val endIndex = min(startIndex + ITEMS_PER_PAGE, allTrips.size)
        _trips.value = if (startIndex < allTrips.size) allTrips.subList(startIndex, endIndex).toList() else emptyList()
    }

    fun loadEarlier() {
        _isChangingContent.value = true

        if (currentPageIndex > 0) {
            _isLoadingEarlier.value = true
            currentPageIndex -= 1
            updateDisplayedTrips()
            settlePaging { _isLoadingEarlier.value = false }
        } else {
            val cursor = _previousPageCursor.value
            if (hasMoreEarlier && cursor != null) {
                _isLoadingEarlier.value = true
                searchTrips(pageCursor = cursor)
            } else {
                _isChangingContent.value = false
            }
        }
    }

    fun loadLater() {
        _isChangingContent.value = true

        if (currentPageIndex < maxPageIndex()) {
            _isLoadingLater.value = true
            currentPageIndex += 1
            updateDisplayedTrips()
            settlePaging { _isLoadingLater.value = false }
        } else {
            val cursor = _nextPageCursor.value
            if (hasMoreLater && cursor != null) {
                _isLoadingLater.value = true
                searchTrips(pageCursor = cursor)
            } else {
                _isChangingContent.value = false
            }
        }
    }

    private fun settlePaging(clearFlag: () -> Unit) {
        pagingJob?.cancel()
        pagingJob = viewModelScope.launch {
            delay(PAGE_SETTLE_MS)
            clearFlag()
            _isChangingContent.value = false
            _animateIn.value = true
        }
    }

    private fun getRouteLocation(location: SelectedLocation?): RouteOptions.RouteLocation? {
        return when (location) {
            null -> null
            is SelectedLocation.SearchResultLocation -> {
                val result = location.result
                if (result.type == LocationType.STOP) {
                    RouteOptions.RouteLocation(result.id)
                } else {
                    RouteOptions.RouteLocation(result.lat, result.lon, result.level)
                }
            }
            SelectedLocation.CurrentPosition -> {
                val current = LocationService.location.value ?: return null
                val nearbyStop = Progress.searchResults.firstOrNull { stop ->
                    stop.lat != 0.0 && stop.lon != 0.0 &&
                        calculateDistance(current.latitude, current.longitude, stop.lat, stop.lon) <= NEARBY_SNAP_M
                }
                if (nearbyStop != null) {
                    RouteOptions.RouteLocation(nearbyStop.id)
                } else {
                    RouteOptions.RouteLocation(current.latitude, current.longitude, null)
                }
            }
        }
    }

    fun handleInitialSearchResult(result: SearchResult, targetField: SearchField) {
        addToHistory(result)
        val location = SelectedLocation.SearchResultLocation(result)

        if (targetField is SearchField.VIA) {
            _fromQuery.value = ""
            _toQuery.value = ""
            _searchResults.value = emptyList()
            _showMinCharactersMessage.value = false
            _isLoading.value = false
            applyViaStop(result, targetField.id)
            return
        }

        if (_fromLocation.value == null && _toLocation.value != null) {
            _fromLocation.value = location
        } else if (_toLocation.value == null && _fromLocation.value != null) {
            _toLocation.value = location
        } else {
            if (targetField == SearchField.TO) {
                _toLocation.value = location
            } else {
                _fromLocation.value = location
            }
        }

        _fromQuery.value = ""
        _toQuery.value = ""
        _searchQuery.value = ""
        _searchResults.value = emptyList()
        _showMinCharactersMessage.value = false
        _isLoading.value = false

        if (_fromLocation.value != null && _toLocation.value != null) {
            setActiveFieldSilently(SearchField.NONE)
            searchTrips()
        } else {
            setActiveFieldSilently(if (_fromLocation.value == null) SearchField.FROM else SearchField.TO)
        }
    }

    fun setupInitialCurrentPosition() {
        if (LocationService.location.value != null && _fromLocation.value == null) {
            _fromLocation.value = SelectedLocation.CurrentPosition
        }
    }

    fun reset() {
        cancelBackgroundTasks()
        tripsJob?.cancel()
        tripsJob = null
        pagingJob?.cancel()
        pagingJob = null

        _fromQuery.value = ""
        _toQuery.value = ""
        _viaQuery.value = ""
        _vias.value = emptyList()
        _searchQuery.value = ""
        _searchResults.value = emptyList()
        _showMinCharactersMessage.value = false
        _isLoading.value = false
        _isPresetFallback.value = false
        _fromLocation.value = null
        _toLocation.value = null
        _activeField.value = SearchField.FROM
        _errorMessage.value = null
        _showSettings.value = false
        _previousPageCursor.value = null
        _nextPageCursor.value = null
        _isLoadingEarlier.value = false
        _isLoadingLater.value = false
        _isChangingContent.value = false
        _animateIn.value = false
        _isSearchingTrips.value = false
        _departureType.value = DepartureType.LEAVE_AT
        _selectedDate.value = null
        _showPastDateWarning.value = false
        clearResults()
        fetchRouteOptionsPreferences()
    }

    override fun onCleared() {
        super.onCleared()
        cancelBackgroundTasks()
        tripsJob?.cancel()
        pagingJob?.cancel()
    }

    private companion object {
        const val TAG = "TripsSearchViewModel"
        const val TRIP_SEARCH_ERROR = "Erreur: la recherche d'itinéraires a échoué"
        const val HISTORY_KEY = "tripSearchHistory"
        const val ROUTE_PRESET_KEY = "routePreset"
        const val MAX_HISTORY_ITEMS = 20
        const val MIN_SEARCH_CHARACTERS = 3
        const val SEARCH_DEBOUNCE_MS = 125L
        const val ITEMS_PER_PAGE = 6
        const val PAGE_SETTLE_MS = 500L
        const val NEARBY_SNAP_M = 15.0
        const val DUPLICATE_DISTANCE_M = 120.0
        const val PAST_DATE_TOLERANCE_S = 600L
    }
}

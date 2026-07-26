package ch.cclerc.luxapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.cclerc.luxapp.data.Progress
import ch.cclerc.luxapp.domain.TripOption
import ch.cclerc.luxapp.domain.UrlHandler
import ch.cclerc.luxapp.domain.UrlHandlerError
import ch.cclerc.luxapp.domain.UrlHandlerResult
import ch.cclerc.luxapp.ui.MainNavigationScreen
import ch.cclerc.luxapp.ui.home.NearbyStopsView
import ch.cclerc.luxapp.ui.itinerary.ItineraryStopDetailView
import ch.cclerc.luxapp.ui.itinerary.presentItinerary
import ch.cclerc.luxapp.ui.navigation.AppChrome
import ch.cclerc.luxapp.ui.navigation.CoverController
import ch.cclerc.luxapp.ui.navigation.LocalBackStack
import ch.cclerc.luxapp.ui.navigation.LocalCoverController
import ch.cclerc.luxapp.ui.navigation.LuxBackStack
import ch.cclerc.luxapp.ui.navigation.LuxCoverRequest
import ch.cclerc.luxapp.ui.navigation.LocalSheetController
import ch.cclerc.luxapp.ui.navigation.LuxDestination
import ch.cclerc.luxapp.ui.settings.SettingsScreen
import ch.cclerc.luxapp.ui.settings.ShortcutsListView
import ch.cclerc.luxapp.ui.stop.IndividualStopView
import ch.cclerc.luxapp.ui.stops.StopsContentView
import ch.cclerc.luxapp.ui.theme.LuxTheme
import ch.cclerc.luxapp.ui.trips.TripsSearchContentSlot
import ch.cclerc.luxapp.ui.trips.TripsSearchHeaderSlot
import ch.cclerc.luxapp.ui.trips.TripsSearchStandalone
import ch.cclerc.luxapp.ui.trips.rememberTripsSearchScreenState
import ch.cclerc.luxapp.viewmodel.SearchField
import ch.cclerc.luxapp.viewmodel.StopsViewModel
import ch.cclerc.luxcom.model.LocationType
import ch.cclerc.luxcom.model.Place
import ch.cclerc.luxcom.model.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class MainActivity : ComponentActivity() {
    private val incomingUri = MutableStateFlow<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        incomingUri.value = intent?.data
        setContent {
            LuxTheme {
                StatusBarAppearance()
                AppChrome(
                    destinationContent = { destination -> DestinationRenderer(destination) }
                ) {
                    val backStack = LocalBackStack.current
                    val coverController = LocalCoverController.current
                    val sheetController = LocalSheetController.current
                    val stopsViewModel: StopsViewModel = viewModel()
                    val tripsSearchState = rememberTripsSearchScreenState()
                    var appliedQuery by remember { mutableStateOf("") }

                    DeepLinkHandler(
                        incoming = incomingUri,
                        onConsumed = { incomingUri.value = null }
                    )

                    MainNavigationScreen(
                        onSearchEnter = { tripsSearchState.enterSearchMode() },
                        onSearchReset = { tripsSearchState.reset() },
                        onSearchPrefillDestination = { result ->
                            tripsSearchState.prefillDestination(result)
                        },
                        onOpenSavedItinerary = { itinerary ->
                            coverController.presentItinerary(itinerary = itinerary)
                        },
                        shortcutsSheet = {
                            ShortcutsListView(onBack = { sheetController.dismiss() })
                        },
                        settingsSheet = {
                            SettingsScreen(onClose = { sheetController.dismiss() })
                        },
                        searchHeader = { onBack ->
                            TripsSearchHeaderSlot(
                                state = tripsSearchState,
                                modifier = Modifier.fillMaxWidth(),
                                onBack = onBack
                            )
                        },
                        searchContent = {
                            TripsSearchContentSlot(
                                state = tripsSearchState,
                                onOpenItinerary = { itinerary, destinationName ->
                                    coverController.presentItinerary(
                                        itinerary = itinerary,
                                        destinationName = destinationName
                                    )
                                }
                            )
                        },
                        homeContent = {
                            NearbyStopsView(
                                onOpenStop = { stop -> openStop(backStack, stop) },
                                onOpenTrip = { tripId, options ->
                                    coverController.presentItinerary(
                                        tripId = tripId,
                                        fromNearby = true,
                                        otherTripOptions = options
                                    )
                                },
                                viewModel = stopsViewModel
                            )
                        },
                        stopsContent = { query ->
                            LaunchedEffect(query) {
                                if (query == appliedQuery) return@LaunchedEffect
                                appliedQuery = query
                                if (query.isEmpty()) {
                                    stopsViewModel.resetSearch()
                                } else {
                                    stopsViewModel.onSearchQueryChanged(query)
                                }
                            }

                            val isSearchMode by stopsViewModel.isSearchMode.collectAsState()
                            val isLoading by stopsViewModel.isLoading.collectAsState()
                            val showMinCharactersMessage by
                                stopsViewModel.showMinCharactersMessage.collectAsState()

                            StopsContentView(
                                isSearchMode = isSearchMode,
                                isLoading = isLoading,
                                searchResults = Progress.searchResults,
                                showMinCharactersMessage = showMinCharactersMessage,
                                onOpenStop = { stop -> openStop(backStack, stop) }
                            )
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingUri.value = intent.data
    }
}

private const val LUXTRIP_MAX_BYTES = 51200

private const val OPEN_ERROR_FALLBACK =
    "Une erreur est survenue lors de son ouverture. Il est possible que le lien ait expiré."

@Composable
private fun DeepLinkHandler(incoming: StateFlow<Uri?>, onConsumed: () -> Unit) {
    val context = LocalContext.current
    val coverController = LocalCoverController.current
    val scope = rememberCoroutineScope()
    val uri by incoming.collectAsState()

    var pendingRemoteId by remember { mutableStateOf<String?>(null) }
    var pendingLocalUri by remember { mutableStateOf<Uri?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    fun showError(message: String) {
        errorMessage = message.ifEmpty { OPEN_ERROR_FALLBACK }
    }

    fun present(result: UrlHandlerResult) {
        when (result) {
            is UrlHandlerResult.ItineraryReady ->
                coverController.presentItinerary(itinerary = result.itinerary)

            is UrlHandlerResult.StopPlace ->
                coverController.presentStopPlanning(result.stopId, result.name)

            is UrlHandlerResult.StopDetail ->
                coverController.presentStopDetail(result.stopId, result.name)

            is UrlHandlerResult.ConfirmationRequired -> pendingRemoteId = result.id
            UrlHandlerResult.LocalFileConfirmationRequired -> Unit
            is UrlHandlerResult.Error -> showError(result.error.description)
        }
    }

    LaunchedEffect(uri) {
        val current = uri ?: return@LaunchedEffect
        onConsumed()

        val scheme = current.scheme?.lowercase()
        if (scheme == "content" || scheme == "file") {
            pendingLocalUri = current
            return@LaunchedEffect
        }

        if (scheme == "https" || scheme == "http") {
            if (current.path?.contains("share.html") == true) {
                val identifier = current.fragment?.trim('/')?.takeIf { it.isNotEmpty() }
                if (identifier == null) {
                    showError(UrlHandlerError.InvalidUrl.description)
                } else {
                    pendingRemoteId = identifier
                }
                return@LaunchedEffect
            }
        }

        when (val result = UrlHandler.process(current.toString())) {
            UrlHandlerResult.LocalFileConfirmationRequired -> pendingLocalUri = current
            else -> present(result)
        }
    }

    pendingRemoteId?.let { identifier ->
        SharedItineraryConfirmationDialog(
            onConfirm = {
                pendingRemoteId = null
                isLoading = true
                scope.launch {
                    val result = UrlHandler.handleConfirmedItinerary(identifier)
                    isLoading = false
                    when (result) {
                        is UrlHandlerResult.ItineraryReady ->
                            coverController.presentItinerary(itinerary = result.itinerary)

                        is UrlHandlerResult.Error -> showError(result.error.description)
                        else -> showError("Une erreur est survenue.")
                    }
                }
            },
            onCancel = { pendingRemoteId = null }
        )
    }

    pendingLocalUri?.let { localUri ->
        SharedItineraryConfirmationDialog(
            onConfirm = {
                pendingLocalUri = null
                isLoading = true
                scope.launch {
                    val data = withContext(Dispatchers.IO) { readLuxTripBytes(context, localUri) }
                    isLoading = false
                    if (data == null) {
                        showError(UrlHandlerError.InvalidFile.description)
                    } else {
                        present(UrlHandler.handleLocalItinerary(data))
                    }
                }
            },
            onCancel = { pendingLocalUri = null }
        )
    }

    if (isLoading) {
        AlertDialog(
            onDismissRequest = {},
            title = {
                Text(
                    text = "Chargement de l'itinéraire...",
                    style = LuxTheme.type.headline
                )
            },
            confirmButton = {}
        )
    }

    errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = {
                Text(
                    text = "L'itinéraire n'a pas pu être ouvert.",
                    style = LuxTheme.type.headline
                )
            },
            text = { Text(text = message, style = LuxTheme.type.subheadline) },
            confirmButton = {
                TextButton(onClick = { errorMessage = null }) {
                    Text(text = "OK", color = LuxTheme.accent)
                }
            }
        )
    }
}

@Composable
private fun SharedItineraryConfirmationDialog(onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                text = "Êtes-vous sûr de vouloir ouvrir cet itinéraire ?",
                style = LuxTheme.type.headline
            )
        },
        text = {
            Text(
                text = "Cet itinéraire vous a été partagé. " +
                    "Assurez-vous qu'il provient d'une source fiable.",
                style = LuxTheme.type.subheadline,
                color = LuxTheme.colors.secondaryLabel
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = "Ouvrir", color = LuxTheme.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(text = "Annuler", color = LuxTheme.accent)
            }
        }
    )
}

private fun readLuxTripBytes(context: Context, uri: Uri): ByteArray? = runCatching {
    context.contentResolver.openInputStream(uri)?.use { stream ->
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(8192)
        while (buffer.size() <= LUXTRIP_MAX_BYTES) {
            val read = stream.read(chunk)
            if (read <= 0) break
            buffer.write(chunk, 0, read)
        }
        buffer.toByteArray()
    }
}.getOrNull()

private fun deepLinkSearchResult(stopId: String, name: String): SearchResult = SearchResult(
    type = LocationType.STOP,
    tokens = emptyList(),
    name = name,
    id = stopId,
    lat = 0.0,
    lon = 0.0,
    areas = emptyList(),
    score = 0.0
)

private fun CoverController.presentStopPlanning(stopId: String, name: String) {
    present(
        LuxCoverRequest {
            TripsSearchStandalone(
                onDismiss = { dismiss() },
                initialSearchResult = deepLinkSearchResult(stopId, name),
                initialTargetField = SearchField.TO,
                onOpenItinerary = { itinerary, destinationName ->
                    presentItinerary(itinerary = itinerary, destinationName = destinationName)
                }
            )
        }
    )
}

private fun CoverController.presentStopDetail(stopId: String, name: String) {
    present(
        LuxCoverRequest {
            ItineraryStopDetailView(
                stop = Place(
                    name = name,
                    stopId = stopId,
                    lat = 0.0,
                    lon = 0.0,
                    level = 0.0,
                    vertexType = Place.VertexType.TRANSIT
                ),
                onDismiss = { dismiss() },
                onPlanTrip = { stop -> presentStopPlanning(stop.id, stop.name) },
                onOpenTrip = { tripId, options ->
                    presentItinerary(tripId = tripId, otherTripOptions = options)
                }
            )
        }
    )
}

private fun openStop(backStack: LuxBackStack, stop: SearchResult) {
    backStack.push(LuxDestination.IndividualStop(stop))
}

@Composable
private fun DestinationRenderer(destination: LuxDestination) {
    val backStack = LocalBackStack.current
    val coverController = LocalCoverController.current
    when (destination) {
        is LuxDestination.IndividualStop -> IndividualStopView(
            stop = destination.stop,
            onPlanTrip = { stop ->
                backStack.push(LuxDestination.TripsSearch(stop, toField = true))
            },
            onOpenTrip = { tripId, options: List<TripOption> ->
                coverController.presentItinerary(tripId = tripId, otherTripOptions = options)
            },
            onBack = { backStack.pop() }
        )

        is LuxDestination.TripsSearch -> TripsSearchStandalone(
            onDismiss = { backStack.pop() },
            initialSearchResult = destination.initial,
            initialTargetField = if (destination.toField) SearchField.TO else SearchField.FROM,
            onOpenItinerary = { itinerary, destinationName ->
                coverController.presentItinerary(
                    itinerary = itinerary,
                    destinationName = destinationName
                )
            }
        )

        is LuxDestination.Placeholder -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(destination.label)
        }
    }
}

@Composable
private fun StatusBarAppearance() {
    val view = androidx.compose.ui.platform.LocalView.current
    val isDark = LuxTheme.isDark
    androidx.compose.runtime.SideEffect {
        val activity = view.context as? ComponentActivity ?: return@SideEffect
        val controller = androidx.core.view.WindowCompat.getInsetsController(activity.window, view)
        controller.isAppearanceLightStatusBars = !isDark
        controller.isAppearanceLightNavigationBars = !isDark
    }
}

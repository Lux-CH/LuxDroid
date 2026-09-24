package ch.cclerc.luxapp.ui.itinerary

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.cclerc.luxapp.ui.components.IosActivityIndicator
import ch.cclerc.luxapp.core.HapticFeedback
import ch.cclerc.luxapp.core.SFSymbol
import ch.cclerc.luxapp.domain.TripOption
import ch.cclerc.luxapp.ui.map.ItineraryMapScreen
import ch.cclerc.luxapp.ui.navigation.DetentSheet
import ch.cclerc.luxapp.ui.navigation.DetentSheetState
import ch.cclerc.luxapp.ui.navigation.LocalCoverController
import ch.cclerc.luxapp.ui.navigation.LuxCoverRequest
import ch.cclerc.luxapp.ui.navigation.SheetDetent
import ch.cclerc.luxapp.ui.theme.LuxMaterials
import ch.cclerc.luxapp.ui.theme.LuxTheme
import ch.cclerc.luxapp.ui.theme.iosShadow
import ch.cclerc.luxapp.ui.trips.TripsSearchStandalone
import ch.cclerc.luxapp.viewmodel.ItineraryViewModel
import ch.cclerc.luxapp.viewmodel.MapTrackingMode
import ch.cclerc.luxapp.viewmodel.SearchField
import ch.cclerc.luxcom.model.Place
import ch.cclerc.luxcom.model.trip.Itinerary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalContext
import ch.cclerc.luxapp.core.LocationService
import ch.cclerc.luxapp.data.Settings
import ch.cclerc.luxapp.domain.map.LatLng
import ch.cclerc.luxapp.domain.onboard.LegLiveMerger
import ch.cclerc.luxapp.domain.onboard.OnboardSession
import ch.cclerc.luxapp.ui.navigation.LocalSheetController
import ch.cclerc.luxapp.ui.navigation.LuxSheetRequest
import ch.cclerc.luxapp.ui.onboard.OnboardIntroCallout
import ch.cclerc.luxapp.ui.onboard.OnboardNavigationScreen
import ch.cclerc.luxapp.ui.onboard.OnboardStopPickerSheet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val OverlayButtonSize = 45.dp
private val IntroCalloutGap = 12.dp
private val OverlayColumnSpacing = 12.dp
private val OverlayHorizontalPadding = 16.dp
private val SingleMapBottomInset = 72.5.dp
private val MultipleMapBottomInset = 165.dp
private val SheetCornerRadius = 38.dp
private const val SINGLE_SHEET_FRACTION = 0.1f
private const val MULTIPLE_SHEET_FRACTION = 0.225f
private const val SHEET_TRANSITION_MILLIS = 300

private val exactTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

fun formatTripOptionTime(instant: Instant): String = exactTimeFormatter.format(instant)

@Composable
fun ItineraryView(
    tripId: String,
    fromNearby: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    otherTripOptions: List<TripOption> = emptyList()
) {
    val accent = LuxTheme.accent
    val viewModel = remember(tripId) {
        ItineraryViewModel.forTrip(tripId, otherTripOptions, accent)
    }

    LaunchedEffect(viewModel, otherTripOptions) {
        viewModel.setTripOptions(otherTripOptions)
    }

    ItineraryScaffold(
        viewModel = viewModel,
        isSingle = true,
        fromNearby = fromNearby,
        onDismiss = onDismiss,
        modifier = modifier
    )
}

@Composable
fun ItineraryView(
    itinerary: Itinerary,
    fromNearby: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    destinationName: String? = null
) {
    val accent = LuxTheme.accent
    val viewModel = remember(itinerary, destinationName) {
        ItineraryViewModel.forItinerary(itinerary, destinationName, accent)
    }

    ItineraryScaffold(
        viewModel = viewModel,
        isSingle = false,
        fromNearby = fromNearby,
        onDismiss = onDismiss,
        modifier = modifier
    )
}

@Composable
private fun ItineraryScaffold(
    viewModel: ItineraryViewModel,
    isSingle: Boolean,
    fromNearby: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LuxTheme.colors
    val accent = LuxTheme.accent
    val coverController = LocalCoverController.current
    val scope = rememberCoroutineScope()
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    var showDetails by remember { mutableStateOf(true) }
    val context = LocalContext.current
    val sheets = LocalSheetController.current
    var onboardSession by remember { mutableStateOf<OnboardSession?>(null) }
    var showsOnboardIntro by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { onboardSession?.stop() }
    }

    fun startOnboard(itinerary: Itinerary) {
        val session = OnboardSession(context, itinerary, viewModel.destinationName)
        showDetails = false
        viewModel.trackingMode = MapTrackingMode.NONE
        onboardSession = session
        session.start()
    }

    fun endOnboard() {
        viewModel.resumeTasks()
        onboardSession = null
        scope.launch {
            delay(350)
            showDetails = true
        }
    }

    fun dismissOnboardIntro() {
        if (!showsOnboardIntro && Settings.onboardIntroSeen) return
        Settings.onboardIntroSeen = true
        showsOnboardIntro = false
    }

    fun onOnboardTapped() {
        HapticFeedback.mediumImpact()
        dismissOnboardIntro()
        val itinerary = viewModel.itinerary ?: return
        if (!isSingle) {
            startOnboard(itinerary)
            return
        }
        val tripLeg = itinerary.legs.firstOrNull() ?: return
        showDetails = false
        scope.launch {
            delay(350)
            var started = false
            sheets.present(
                LuxSheetRequest(
                    cornerRadius = SheetCornerRadius,
                    detents = listOf(SheetDetent.Medium, SheetDetent.Large)
                ) {
                    DisposableEffect(Unit) {
                        onDispose { if (!started) showDetails = true }
                    }
                    val location = LocationService.location.value
                    OnboardStopPickerSheet(
                        tripLeg = tripLeg,
                        userLocation = location?.let { LatLng(it.latitude, it.longitude) },
                        onSelect = { board, alight ->
                            val leg = LegLiveMerger.slice(tripLeg, board, alight)
                            started = leg != null
                            sheets.dismiss()
                            if (leg != null) {
                                startOnboard(
                                    Itinerary(
                                        duration = leg.duration,
                                        startTime = leg.startTime,
                                        endTime = leg.endTime,
                                        transfers = 0,
                                        legs = listOf(leg)
                                    )
                                )
                            }
                        },
                        onCancel = { sheets.dismiss() }
                    )
                }
            )
        }
    }

    val sheetState = remember(isSingle) {
        DetentSheetState(
            listOf(
                SheetDetent.Fraction(
                    if (isSingle) SINGLE_SHEET_FRACTION else MULTIPLE_SHEET_FRACTION
                ),
                SheetDetent.Medium,
                SheetDetent.Large
            )
        )
    }

    val mapBottomInset = if (isSingle) SingleMapBottomInset else MultipleMapBottomInset

    DisposableEffect(viewModel) {
        onDispose { viewModel.cleanup() }
    }

    fun closeStopDetail() {
        coverController.dismiss()
        viewModel.selectedStop = null
        showDetails = true
    }

    fun openStopDetail(place: Place) {
        showDetails = false
        coverController.present(
            LuxCoverRequest(dismissOnBack = false) {
                BackHandler { closeStopDetail() }
                ItineraryStopDetailView(
                    stop = place,
                    onDismiss = { closeStopDetail() },
                    onPlanTrip = { searchResult ->
                        coverController.present(
                            LuxCoverRequest {
                                TripsSearchStandalone(
                                    onDismiss = { coverController.dismiss() },
                                    initialSearchResult = searchResult,
                                    initialTargetField = SearchField.TO,
                                    onOpenItinerary = { itinerary, destination ->
                                        coverController.presentItinerary(
                                            itinerary = itinerary,
                                            destinationName = destination
                                        )
                                    }
                                )
                            }
                        )
                    },
                    onOpenTrip = { tripId, options ->
                        coverController.presentItinerary(
                            tripId = tripId,
                            otherTripOptions = options
                        )
                    }
                )
            }
        )
    }

    val activeSession = onboardSession
    if (activeSession != null) {
        OnboardNavigationScreen(
            session = activeSession,
            onEnd = { endOnboard() },
            modifier = modifier
        )
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.secondarySystemBackground)
    ) {
        ItineraryMapScreen(
            viewModel = viewModel,
            sheetPeekHeight = mapBottomInset,
            modifier = Modifier.fillMaxSize(),
            fromNearby = fromNearby,
            onOpenExpandedStop = { place -> openStopDetail(place) },
            onSheetVisibilityChange = { visible -> showDetails = visible },
            onTrackingCancelled = {}
        )

        val error = viewModel.errorMessage
        when {
            viewModel.isLoading && viewModel.itinerary == null -> ItineraryLoadingOverlay()
            error != null && viewModel.itinerary == null -> ItineraryErrorOverlay(error)
            else -> Unit
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = OverlayHorizontalPadding, top = topInset + OverlayColumnSpacing),
            verticalArrangement = Arrangement.spacedBy(OverlayColumnSpacing)
        ) {
            OverlayCircleButton(
                symbol = "chevron.backward",
                onClick = {
                    showDetails = false
                    onDismiss()
                }
            )

            OverlayCircleButton(
                symbol = when (viewModel.trackingMode) {
                    MapTrackingMode.NONE -> "location"
                    MapTrackingMode.FOLLOW -> "location.fill"
                    MapTrackingMode.FOLLOW_WITH_HEADING -> "location.north.line.fill"
                },
                onClick = { viewModel.cycleTrackingMode() }
            )

            if (viewModel.otherTripOptions.size > 1) {
                TripSelectionButton(
                    options = viewModel.otherTripOptions,
                    currentTripId = viewModel.tripId,
                    isBusy = viewModel.isLoading || viewModel.isSwitchingTrip,
                    onSelect = { tripId ->
                        HapticFeedback.lightImpact()
                        scope.launch { viewModel.switchToTrip(tripId) }
                    }
                )
            }
        }

        val loadedItinerary = viewModel.itinerary
        if (loadedItinerary != null) {
            val canStartOnboard = OnboardSession.canStart(loadedItinerary)
            LaunchedEffect(canStartOnboard) {
                if (!canStartOnboard || Settings.onboardIntroSeen || showsOnboardIntro) return@LaunchedEffect
                delay(800)
                if (Settings.onboardIntroSeen || onboardSession != null) return@LaunchedEffect
                HapticFeedback.success()
                showsOnboardIntro = true
                delay(12_000)
                dismissOnboardIntro()
            }
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = OverlayHorizontalPadding, top = topInset + OverlayColumnSpacing),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(OverlayColumnSpacing)
            ) {
                if (canStartOnboard) {
                    Box {
                        Box(
                            modifier = Modifier
                                .size(OverlayButtonSize)
                                .iosShadow(Color.Black.copy(alpha = 0.18f), 2.dp, shape = CircleShape)
                                .clip(CircleShape)
                                .background(accent, CircleShape)
                                .clickable { onOnboardTapped() },
                            contentAlignment = Alignment.Center
                        ) {
                            SFSymbol(name = "location.north.line.fill", size = 17.sp, color = Color.White, weight = 600)
                        }
                        androidx.compose.animation.AnimatedVisibility(
                            visible = showsOnboardIntro,
                            enter = scaleIn(transformOrigin = TransformOrigin(1f, 0.5f), initialScale = 0.6f) + fadeIn(),
                            exit = scaleOut(transformOrigin = TransformOrigin(1f, 0.5f), targetScale = 0.6f) + fadeOut(),
                            modifier = Modifier.layout { measurable, _ ->
                                val placeable = measurable.measure(Constraints())
                                layout(0, 0) {
                                    placeable.place(-placeable.width - IntroCalloutGap.roundToPx(), 0)
                                }
                            }
                        ) {
                            OnboardIntroCallout(onDismiss = { dismissOnboardIntro() })
                        }
                    }
                }
                ShareButtonView(
                    itinerary = loadedItinerary,
                    compact = true,
                    showCompactSaveAction = !isSingle
                )
            }
        }

        AnimatedVisibility(
            visible = showDetails,
            modifier = Modifier.fillMaxSize(),
            enter = slideInVertically(
                animationSpec = tween(SHEET_TRANSITION_MILLIS, easing = EaseOut)
            ) { it },
            exit = slideOutVertically(
                animationSpec = tween(SHEET_TRANSITION_MILLIS, easing = EaseOut)
            ) { it }
        ) {
            DetentSheet(
                state = sheetState,
                cornerRadius = SheetCornerRadius,
                showDragIndicator = true
            ) {
                ItineraryDetailSheet(
                    viewModel = viewModel,
                    isSingle = isSingle,
                    sheetState = sheetState,
                    onOpenSubLeg = { subTripId ->
                        coverController.presentItinerary(tripId = subTripId)
                    }
                )
            }
        }
    }
}

@Composable
private fun ItineraryLoadingOverlay() {
    val colors = LuxTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.secondarySystemBackground),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
    ) {
        IosActivityIndicator(size = 32.dp, color = LuxTheme.accent)
        Text(
            text = "Chargement de l'itinéraire...",
            style = LuxTheme.type.subheadline,
            color = colors.secondaryLabel
        )
    }
}

@Composable
private fun ItineraryErrorOverlay(message: String) {
    val colors = LuxTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.secondarySystemBackground)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
    ) {
        SFSymbol(name = "exclamationmark.triangle", size = 34.sp, color = colors.systemOrange)
        Text(
            text = "Une erreur est survenue lors du chargement de l'itinéraire.",
            style = LuxTheme.type.headline,
            color = colors.label,
            textAlign = TextAlign.Center
        )
        Text(
            text = message,
            style = LuxTheme.type.subheadline,
            color = colors.secondaryLabel,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun OverlayCircleButton(
    symbol: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(OverlayButtonSize)
            .iosShadow(
                color = Color.Black.copy(alpha = 0.18f),
                blurRadius = 2.dp,
                shape = CircleShape
            )
            .clip(CircleShape)
            .background(LuxMaterials.ultraThick(), CircleShape)
            .border(0.5.dp, LuxTheme.colors.label.copy(alpha = 0.1f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        SFSymbol(name = symbol, size = 17.sp, color = LuxTheme.accent)
    }
}

@Composable
private fun TripSelectionButton(
    options: List<TripOption>,
    currentTripId: String,
    isBusy: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LuxTheme.colors
    val accent = LuxTheme.accent
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .size(OverlayButtonSize)
                .iosShadow(
                    color = Color.Black.copy(alpha = 0.18f),
                    blurRadius = 2.dp,
                    shape = CircleShape
                )
                .clip(CircleShape)
                .background(LuxMaterials.ultraThick(), CircleShape)
                .border(0.5.dp, colors.label.copy(alpha = 0.1f), CircleShape)
                .clickable(enabled = !isBusy) { expanded = true },
            contentAlignment = Alignment.Center
        ) {
            if (isBusy) {
                IosActivityIndicator(
                    size = 20.dp,
                    color = accent
                )
            } else {
                SFSymbol(name = "clock", size = 17.sp, color = accent)
            }
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                val isCurrent = option.id == currentTripId
                DropdownMenuItem(
                    text = {
                        Text(
                            text = formatTripOptionTime(option.startTime),
                            style = LuxTheme.type.body,
                            color = if (isCurrent) colors.secondaryLabel else colors.label
                        )
                    },
                    trailingIcon = if (isCurrent) {
                        { SFSymbol(name = "checkmark", size = 15.sp, color = accent) }
                    } else {
                        null
                    },
                    enabled = !isCurrent,
                    onClick = {
                        expanded = false
                        if (!isCurrent) onSelect(option.id)
                    }
                )
            }
        }
    }
}

package ch.cclerc.luxapp.ui.home

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.cclerc.luxapp.core.LocationAuthState
import ch.cclerc.luxapp.core.LocationService
import ch.cclerc.luxapp.core.SFSymbol
import ch.cclerc.luxapp.data.Progress
import ch.cclerc.luxapp.data.Settings
import ch.cclerc.luxapp.data.StatusFeed
import ch.cclerc.luxapp.domain.TripOption
import ch.cclerc.luxapp.ui.anim.scaleClickable
import ch.cclerc.luxapp.ui.components.HintIndicator
import ch.cclerc.luxapp.ui.theme.InterFontFamily
import ch.cclerc.luxapp.ui.theme.LuxTheme
import ch.cclerc.luxapp.viewmodel.StopsViewModel
import ch.cclerc.luxcom.geo.StopGrouping
import ch.cclerc.luxcom.model.SearchResult
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import androidx.compose.runtime.collectAsState

private data class GroupBudget(val station: Int, val first: Int, val second: Int)

private fun groupBudgetFor(screenHeightDp: Int): GroupBudget = when {
    screenHeightDp >= 900 -> GroupBudget(7, 4, 2)
    screenHeightDp >= 840 -> GroupBudget(6, 3, 2)
    screenHeightDp >= 800 -> GroupBudget(5, 2, 2)
    else -> GroupBudget(4, 2, 1)
}

private fun nearbyStation(stops: List<SearchResult>): SearchResult? {
    val closest = stops.firstOrNull() ?: return null
    if (closest.servesRail) return closest
    if (!StopGrouping.isStationForecourt(closest.name)) return null
    return stops.firstOrNull { it.servesRail }
}

private fun formatStatusDate(dateString: String): String = runCatching {
    val instant = java.time.Instant.parse(dateString)
    DateTimeFormatter
        .ofLocalizedDateTime(FormatStyle.SHORT, FormatStyle.SHORT)
        .withLocale(Locale.FRANCE)
        .withZone(ZoneId.systemDefault())
        .format(instant)
}.getOrDefault(dateString)

@Composable
fun NearbyStopsView(
    onOpenStop: (SearchResult) -> Unit,
    onOpenTrip: (String, List<TripOption>) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StopsViewModel = viewModel()
) {
    val colors = LuxTheme.colors
    val context = LocalContext.current
    val configuration = LocalConfiguration.current

    val authorizationState by viewModel.authorizationState.collectAsState()
    val isWaitingForLocation by viewModel.isWaitingForLocation.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val maintenanceStatus by viewModel.maintenanceStatus.collectAsState()
    val warningMessage by viewModel.warningMessage.collectAsState()
    val searchResults = Progress.searchResults

    val isAuthorizationNotAllowed = authorizationState == LocationAuthState.Denied

    var showingSuggestion by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        LocationService.refreshAuthState()
    }

    DisposableEffect(viewModel) {
        viewModel.start()
        onDispose { viewModel.stop() }
    }

    LaunchedEffect(Unit) {
        if (LocationService.authorizationState.value == LocationAuthState.NotDetermined) {
            LocationService.markPermissionRequested()
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                LocationService.refreshAuthState()
                viewModel.onEnterForeground()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when {
            isAuthorizationNotAllowed -> {
                Spacer(Modifier.weight(1f))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 0.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    SFSymbol("location.slash", 64.sp, colors.label)
                    Text(
                        text = "Accès à la localisation refusé",
                        style = LuxTheme.type.body,
                        color = colors.label,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                    Text(
                        text = "Pour afficher les arrêts à proximité, veuillez autoriser l'accès à votre position dans les réglages.",
                        style = LuxTheme.type.footnote,
                        color = colors.systemGray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 5.dp)
                    )
                    Box(
                        Modifier
                            .background(
                                colors.secondarySystemBackground.copy(alpha = 0.7f),
                                RoundedCornerShape(percent = 50)
                            )
                            .border(
                                0.5.dp,
                                colors.label.copy(alpha = 0.1f),
                                RoundedCornerShape(percent = 50)
                            )
                            .scaleClickable {
                                val intent = Intent(
                                    AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", context.packageName, null)
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                runCatching { context.startActivity(intent) }
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "Ouvrir les Réglages",
                            fontFamily = InterFontFamily,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = LuxTheme.accent
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
            }

            isWaitingForLocation -> {
                LoadingLabel("En attente de votre position...")
            }

            !isOnline -> {
                Spacer(Modifier.weight(1f))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    SFSymbol("wifi.slash", 64.sp, colors.label)
                    Text(
                        text = "Aucune connexion à Internet.",
                        style = LuxTheme.type.body,
                        color = colors.label,
                        modifier = Modifier.padding(top = 16.dp, start = 16.dp, end = 16.dp)
                    )
                    Text(
                        text = "Vérifiez vos données mobile ou votre connexion Wi-Fi et réessayez.",
                        style = LuxTheme.type.footnote,
                        color = colors.systemGray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
            }

            isLoading -> {
                LoadingLabel("Chargement des arrêts à proximité...")
            }

            searchResults.isEmpty() -> {
                val status = maintenanceStatus
                if (status != null && status.isMaintenance) {
                    Spacer(Modifier.weight(1f))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        SFSymbol("wrench.fill", 48.sp, LuxTheme.accent)
                        Text(
                            text = "Maintenance en cours",
                            style = LuxTheme.type.title2,
                            color = colors.label
                        )
                        Text(
                            text = status.message,
                            style = LuxTheme.type.callout,
                            color = colors.secondaryLabel,
                            textAlign = TextAlign.Center
                        )
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            val eta = status.estimatedDateOfResolution
                            if (eta != null) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    SFSymbol("clock", 12.sp, colors.secondaryLabel)
                                    Text(
                                        text = "Résolution prévue : ${formatStatusDate(eta)}",
                                        style = LuxTheme.type.caption,
                                        color = colors.secondaryLabel
                                    )
                                }
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SFSymbol("arrow.clockwise", 12.sp, colors.secondaryLabel)
                                Text(
                                    text = "Dernière mise à jour : ${formatStatusDate(status.dateOfUpdate)}",
                                    style = LuxTheme.type.caption,
                                    color = colors.secondaryLabel
                                )
                            }
                            LinkButton("Actualiser") { viewModel.refreshMaintenanceStatus() }
                            LinkButton("État des serveurs") { openStatusPage(context) }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    Spacer(Modifier.weight(1f))
                } else {
                    Text(
                        text = "Aucun arrêt à proximité trouvé.",
                        style = LuxTheme.type.body,
                        color = colors.systemGray,
                        modifier = Modifier.padding(16.dp)
                    )
                    LinkButton("État des serveurs") { openStatusPage(context) }
                }
            }

            else -> {
                val budget = groupBudgetFor(configuration.screenHeightDp)
                val nearbyStops = searchResults.take(2)
                val station = nearbyStation(nearbyStops)

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(2.5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (station != null) {
                            CompactStopView(
                                stop = station,
                                maxGroupsToShow = budget.station,
                                dontShowLastDivider = true,
                                isLastStopOverall = true,
                                onOpenStop = onOpenStop,
                                onOpenTrip = onOpenTrip,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            nearbyStops.forEachIndexed { index, result ->
                                val isLastStop = index == min(1, searchResults.size - 1)
                                val groupsToShow = if (index == 0) {
                                    budget.first
                                } else if (showingSuggestion) {
                                    max(1, budget.second - 1)
                                } else {
                                    budget.second
                                }
                                CompactStopView(
                                    stop = result,
                                    maxGroupsToShow = groupsToShow,
                                    dontShowLastDivider = true,
                                    isLastStopOverall = isLastStop,
                                    onOpenStop = onOpenStop,
                                    onOpenTrip = onOpenTrip,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 20.dp),
                            thickness = 0.5.dp,
                            color = colors.separator
                        )
                    }

                    val warning = warningMessage
                    when {
                        Settings.appLaunchCount < 5 &&
                            Progress.numOfTimesStopViewWasOpened < 2 &&
                            !Settings.firstLaunch -> {
                            LaunchedEffect(Unit) { showingSuggestion = true }
                            HintIndicator(
                                icon = "chevron.compact.up",
                                message = "Glissez vers le haut pour voir plus d'arrêts à proximité",
                                delayMs = 2000,
                                durationMs = 35000,
                                onDismiss = { showingSuggestion = false },
                                modifier = Modifier.padding(top = 14.dp)
                            )
                        }

                        Progress.numOfTimesTripViewWasOpened < 2 &&
                            !Progress.shownTripViewSuggestion &&
                            !Settings.firstLaunch -> {
                            LaunchedEffect(Unit) { showingSuggestion = true }
                            HintIndicator(
                                icon = "chevron.compact.down",
                                message = "Glissez vers le bas pour planifier un itinéraire ou obtenir des directions",
                                delayMs = 2000,
                                durationMs = 35000,
                                onDismiss = {
                                    Progress.shownTripViewSuggestion = true
                                    showingSuggestion = false
                                },
                                modifier = Modifier.padding(top = 14.dp)
                            )
                        }

                        warning != null && warning.show -> {
                            LaunchedEffect(Unit) { showingSuggestion = true }
                            HintIndicator(
                                icon = warning.icon,
                                message = warning.message,
                                delayMs = 250,
                                durationMs = 10000,
                                onDismiss = { showingSuggestion = false },
                                modifier = Modifier.padding(top = 14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingLabel(message: String) {
    val colors = LuxTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.height(18.dp),
            strokeWidth = 2.dp,
            color = colors.secondaryLabel
        )
        Text(
            text = message,
            style = LuxTheme.type.footnote,
            color = colors.secondaryLabel
        )
    }
}

@Composable
private fun LinkButton(title: String, onClick: () -> Unit) {
    Text(
        text = title,
        fontFamily = InterFontFamily,
        fontSize = 17.sp,
        color = LuxTheme.accent,
        modifier = Modifier
            .scaleClickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

private fun openStatusPage(context: android.content.Context) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(StatusFeed.SERVER_STATUS_URL))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

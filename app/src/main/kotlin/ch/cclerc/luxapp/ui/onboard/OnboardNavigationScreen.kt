package ch.cclerc.luxapp.ui.onboard

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.cclerc.luxapp.core.HapticFeedback
import ch.cclerc.luxapp.core.SFSymbol
import ch.cclerc.luxapp.domain.DisruptionManager
import ch.cclerc.luxapp.domain.onboard.OnboardPhase
import ch.cclerc.luxapp.domain.onboard.OnboardSession
import ch.cclerc.luxapp.domain.onboard.disruptionGroups
import ch.cclerc.luxapp.domain.onboard.dismissAlert
import ch.cclerc.luxapp.domain.onboard.dismissCrowdPrompt
import ch.cclerc.luxapp.domain.onboard.reportRide
import ch.cclerc.luxapp.domain.onboard.setSharing
import ch.cclerc.luxapp.domain.onboard.updateDisruptions
import ch.cclerc.luxapp.ui.anim.PlainIndication
import ch.cclerc.luxapp.ui.itinerary.DisruptionsListView
import ch.cclerc.luxapp.ui.itinerary.ItineraryStopDetailView
import ch.cclerc.luxapp.ui.itinerary.presentItinerary
import ch.cclerc.luxapp.ui.navigation.DetentSheet
import ch.cclerc.luxapp.ui.navigation.DetentSheetState
import ch.cclerc.luxapp.ui.navigation.LocalCoverController
import ch.cclerc.luxapp.ui.navigation.LocalSheetController
import ch.cclerc.luxapp.ui.navigation.LuxCoverRequest
import ch.cclerc.luxapp.ui.navigation.LuxSheetRequest
import ch.cclerc.luxapp.ui.navigation.SheetDetent
import ch.cclerc.luxapp.ui.navigation.SheetPushHost
import ch.cclerc.luxapp.ui.theme.LuxMaterials
import ch.cclerc.luxapp.ui.theme.LuxShapes
import ch.cclerc.luxapp.ui.theme.LuxTheme
import ch.cclerc.luxapp.ui.theme.iosShadow
import ch.cclerc.luxcom.model.feedback.ReportAttribute
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val COMPACT_KEY = "onboard-compact"

@Composable
fun OnboardNavigationScreen(
    session: OnboardSession,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LuxTheme.colors
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val sheets = LocalSheetController.current
    val covers = LocalCoverController.current
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomSafe = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    var isFollowing by remember { mutableStateOf(true) }
    var showsOverview by remember { mutableStateOf(false) }
    var bannerHeight by remember { mutableStateOf(120.dp) }
    var compactHeight by remember { mutableStateOf(150.dp) }
    var allowsMediumDetent by remember { mutableStateOf(false) }
    var showsDisruptions by remember { mutableStateOf(false) }

    val sheetState = remember { DetentSheetState(listOf(SheetDetent.Height(COMPACT_KEY, 150.dp), SheetDetent.Large)) }
    val compactDetent = SheetDetent.Height(COMPACT_KEY, compactHeight)
    LaunchedEffect(compactHeight, allowsMediumDetent) {
        sheetState.detents = listOfNotNull(
            compactDetent,
            if (allowsMediumDetent) SheetDetent.Medium else null,
            SheetDetent.Large
        )
    }

    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    fun end() {
        session.stop()
        onEnd()
    }

    BackHandler(enabled = !showsDisruptions) { end() }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        session.refreshMotion()
    }

    fun requestPermissions() {
        session.requestNotificationPermission()
        val permissions = buildList {
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            add(Manifest.permission.ACTIVITY_RECOGNITION)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    LaunchedEffect(Unit) {
        if (!session.needsSharingConsent) {
            requestPermissions()
            return@LaunchedEffect
        }
        delay(1_200)
        sheets.present(
            LuxSheetRequest(
                cornerRadius = LuxShapes.r38,
                detents = listOf(SheetDetent.Large),
                interactiveDismiss = false,
                showDragIndicator = false
            ) {
                OnboardConsentSheet(onAnswer = { shares ->
                    session.setSharing(shares)
                    sheets.dismiss()
                    requestPermissions()
                })
            }
        )
    }

    val disruptions by DisruptionManager.shared.disruptions.collectAsState()
    val hasLoaded by DisruptionManager.shared.hasLoaded.collectAsState()
    LaunchedEffect(disruptions, hasLoaded) {
        if (hasLoaded) session.updateDisruptions(disruptions)
    }

    LaunchedEffect(session.phase) {
        if (session.phase == OnboardPhase.ARRIVED) sheetState.animateTo(compactDetent)
    }

    fun recenter() {
        HapticFeedback.lightImpact()
        showsOverview = false
        isFollowing = true
    }

    fun openDisruptions() {
        scope.launch {
            if (sheetState.currentDetent == compactDetent) {
                allowsMediumDetent = true
                delay(16)
                sheetState.animateTo(SheetDetent.Medium)
            }
            showsDisruptions = true
        }
    }

    fun closeDisruptions() {
        showsDisruptions = false
        scope.launch {
            if (sheetState.currentDetent == SheetDetent.Medium) sheetState.animateTo(compactDetent)
            delay(350)
            allowsMediumDetent = false
        }
    }

    fun openStop(place: ch.cclerc.luxcom.model.Place) {
        covers.present(
            LuxCoverRequest {
                ItineraryStopDetailView(
                    stop = place,
                    onDismiss = { covers.dismiss() },
                    onPlanTrip = { covers.dismiss() },
                    onOpenTrip = { tripId, options -> covers.presentItinerary(tripId = tripId, otherTripOptions = options) }
                )
            }
        )
    }

    Box(modifier.fillMaxSize().background(colors.secondarySystemBackground)) {
        OnboardMapView(
            session = session,
            isFollowing = isFollowing,
            showsOverview = showsOverview,
            topInset = topInset + 4.dp + bannerHeight + 12.dp,
            bottomInset = compactHeight + bottomSafe + 40.dp,
            onUserMovedMap = {
                if (isFollowing || showsOverview) {
                    isFollowing = false
                    showsOverview = false
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp)
                .padding(top = topInset + 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OnboardInstructionBanner(
                session = session,
                modifier = Modifier
                    .onSizeChanged { bannerHeight = with(density) { it.height.toDp() } }
                    .clickable(interactionSource = null, indication = PlainIndication) { recenter() }
            )

            AnimatedContent(
                targetState = session.alert,
                transitionSpec = {
                    (slideInVertically { -it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut())
                },
                label = "onboardAlert"
            ) { alert ->
                if (alert != null) OnboardAlertToast(alert, onDismiss = { session.dismissAlert() })
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ControlButton(if (session.voiceEnabled) "speaker.wave.2.fill" else "speaker.slash.fill") {
                        HapticFeedback.selectionChanged()
                        session.toggleVoice(!session.voiceEnabled)
                    }
                    ControlButton(
                        if (showsOverview) "location.north.line.fill" else "point.topleft.down.to.point.bottomright.curvepath.fill"
                    ) {
                        HapticFeedback.selectionChanged()
                        showsOverview = !showsOverview
                        isFollowing = !showsOverview
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            AnimatedVisibility(
                visible = !isFollowing && !showsOverview,
                enter = slideInHorizontally { -it } + fadeIn(),
                exit = slideOutHorizontally { -it } + fadeOut()
            ) {
                RecenterButton(Modifier.padding(bottom = compactHeight + bottomSafe + 56.dp)) { recenter() }
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        ) {
            AnimatedVisibility(
                visible = session.replan != null || session.isReplanning,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                ReplanCard(session, Modifier.padding(bottom = 10.dp))
            }
            AnimatedVisibility(
                visible = session.replan == null && !session.isReplanning && session.showsCrowdPrompt,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                CrowdPromptCard(
                    onAnswer = { session.reportRide(ReportAttribute.CROWD, it) },
                    onDismiss = { session.dismissCrowdPrompt() },
                    modifier = Modifier.padding(bottom = 10.dp)
                )
            }
            Spacer(Modifier.height(compactHeight + bottomSafe + 56.dp))
        }

        DetentSheet(state = sheetState, cornerRadius = LuxShapes.r38, showDragIndicator = true) {
            SheetPushHost(
                pushed = if (showsDisruptions) session.disruptionGroups else null,
                onPop = { closeDisruptions() },
                destination = { groups -> DisruptionsListView(groups = groups, onBack = { closeDisruptions() }) }
            ) {
                OnboardBottomPanel(
                    session = session,
                    isExpanded = sheetState.currentDetent == SheetDetent.Large,
                    onEnd = { end() },
                    onOpenDetail = { openDisruptions() },
                    onSelectStop = { openStop(it) },
                    onCompactHeightChange = { height ->
                        if ((height - compactHeight).value.let { kotlin.math.abs(it) } > 1) compactHeight = height
                    }
                )
            }
        }
    }
}

@Composable
private fun ControlButton(symbol: String, onClick: () -> Unit) {
    val accent = LuxTheme.accent
    Box(
        modifier = Modifier
            .size(45.dp)
            .iosShadow(Color.Black.copy(alpha = 0.18f), 2.dp, 0.dp, CircleShape)
            .clip(CircleShape)
            .background(LuxMaterials.ultraThick(), CircleShape)
            .border(0.5.dp, LuxTheme.colors.label.copy(alpha = 0.1f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        SFSymbol(name = symbol, size = 17.sp, color = accent, weight = 600)
    }
}

@Composable
private fun RecenterButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    val accent = LuxTheme.accent
    Row(
        modifier = modifier
            .iosShadow(Color.Black.copy(alpha = 0.18f), 2.dp, 0.dp, CircleShape)
            .clip(CircleShape)
            .background(LuxMaterials.ultraThick(), CircleShape)
            .border(0.5.dp, LuxTheme.colors.label.copy(alpha = 0.1f), CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SFSymbol(name = "location.north.line.fill", size = 15.sp, color = accent, weight = 600)
        Text("Recentrer", style = LuxTheme.type.subheadline.copy(fontWeight = FontWeight.SemiBold), color = accent)
    }
}

package ch.cclerc.luxapp.ui.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ch.cclerc.luxapp.core.HapticFeedback
import ch.cclerc.luxapp.domain.intelligence.DepartureAlertPlanner
import ch.cclerc.luxapp.domain.intelligence.IntelligenceLearner
import ch.cclerc.luxapp.domain.intelligence.IntelligenceStore
import ch.cclerc.luxapp.ui.anim.scaleClickable
import ch.cclerc.luxapp.ui.components.settings.SectionHeader
import ch.cclerc.luxapp.ui.components.settings.SettingsCard
import ch.cclerc.luxapp.ui.components.settings.SettingsRow
import ch.cclerc.luxapp.ui.components.settings.SettingsToggle
import ch.cclerc.luxapp.ui.navigation.LocalSheetController
import ch.cclerc.luxapp.ui.navigation.LuxSheetRequest
import ch.cclerc.luxapp.ui.theme.LuxShapes
import ch.cclerc.luxapp.ui.theme.LuxTheme
import ch.cclerc.luxapp.ui.trips.intelligence.IntelligenceSetupView
import kotlinx.coroutines.launch

@Composable
internal fun IntelligenceSettingsCard(onOpenLearning: () -> Unit) {
    val colors = LuxTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheets = LocalSheetController.current
    val profile by IntelligenceStore.profileFlow.collectAsState()
    val learning by IntelligenceStore.learningFlow.collectAsState()
    val departureAlerts by IntelligenceStore.departureAlertsFlow.collectAsState()
    var hasCalendar by remember { mutableStateOf(DepartureAlertPlanner.hasCalendarAccess(context)) }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val calendarLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCalendar = granted
        DepartureAlertPlanner.refresh(force = true)
    }

    SettingsCard {
        SectionHeader(
            icon = "sparkles",
            iconColor = colors.systemPurple,
            title = "Intelligent",
            subtitle = "Suggestions selon la météo, l'affluence et vos habitudes"
        )
        SettingsRow(
            icon = "sparkles",
            title = "Préférences",
            subtitle = if (profile.isConfigured) {
                "Météo, affluence, marche et habitudes"
            } else {
                "Quelques questions pour l'adapter à vous"
            },
            onClick = {
                sheets.present(
                    LuxSheetRequest(cornerRadius = LuxShapes.r36, showDragIndicator = false) {
                        IntelligenceSetupView(onDismiss = { sheets.dismiss() })
                    }
                )
            }
        )

        SettingsToggle(
            icon = "bell.badge.fill",
            title = "Alertes de départ",
            subtitle = "Une notification au bon moment pour vos raccourcis programmés et vos rendez-vous.",
            checked = departureAlerts,
            onCheckedChange = { enabled ->
                if (enabled) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                scope.launch { DepartureAlertPlanner.setEnabled(enabled) }
            }
        )

        if (departureAlerts && !hasCalendar) {
            SettingsRow(
                icon = "calendar.badge.plus",
                title = "Inclure le calendrier",
                subtitle = "Prévenir aussi pour les rendez-vous qui ont un lieu",
                showChevron = false,
                onClick = { calendarLauncher.launch(Manifest.permission.READ_CALENDAR) }
            )
        }

        SettingsToggle(
            icon = "brain",
            title = "Apprendre de mes choix",
            subtitle = "Intelligent s'ajuste doucement d'après les trajets que vous choisissez vraiment.",
            checked = learning.isEnabled,
            onCheckedChange = { IntelligenceStore.learning = IntelligenceStore.learning.copy(isEnabled = it) }
        )

        if (learning.isEnabled) {
            SettingsRow(
                icon = "list.bullet.rectangle",
                title = "Ce que Lux a appris",
                subtitle = if (learning.observations == 0) "Rien pour l'instant" else "D'après ${learning.observations} choix",
                onClick = onOpenLearning
            )
        }
    }
}

@Composable
internal fun IntelligenceLearningView(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LuxTheme.colors
    val learning by IntelligenceStore.learningFlow.collectAsState()
    var confirmsReset by remember { mutableStateOf(false) }
    val learned = IntelligenceLearner.summary(learning)

    SettingsSubScreen(title = "Apprentissage", onBack = onBack, modifier = modifier) {
        SettingsCard(Modifier.padding(horizontal = 16.dp)) {
            SectionHeader(
                icon = "brain",
                iconColor = colors.systemPurple,
                title = "Ce que Lux a appris",
                subtitle = "D'après ${learning.observations} choix"
            )
            if (learned.isEmpty()) {
                SettingsRow(
                    icon = "hourglass",
                    title = if (learning.observations == 0) "Rien pour l'instant" else "Rien de marquant",
                    subtitle = if (learning.observations == 0) {
                        "Choisissez quelques trajets, Lux prend des notes."
                    } else {
                        "Vos réponses font foi."
                    },
                    showChevron = false
                )
            } else {
                learned.forEach { line ->
                    SettingsRow(icon = line.symbol, title = line.text, subtitle = "", showChevron = false)
                }
            }
        }

        if (learning.observations > 0) {
            SettingsCard(Modifier.padding(horizontal = 16.dp)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .scaleClickable { confirmsReset = true }
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    Text(
                        "Effacer l'apprentissage",
                        style = LuxTheme.type.body.copy(fontWeight = FontWeight.Medium),
                        color = colors.systemRed
                    )
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }

    if (confirmsReset) {
        AlertDialog(
            onDismissRequest = { confirmsReset = false },
            title = { Text("Effacer l'apprentissage") },
            text = { Text("Intelligent oubliera ce qu'il a appris de vos choix. Vos réponses sont conservées.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmsReset = false
                    IntelligenceStore.learning = IntelligenceStore.learning.erased()
                    HapticFeedback.mediumImpact()
                }) { Text("Effacer", color = colors.systemRed) }
            },
            dismissButton = {
                TextButton(onClick = { confirmsReset = false }) { Text("Annuler") }
            }
        )
    }
}

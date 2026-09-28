package ch.cclerc.luxapp.ui.settings

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.cclerc.luxapp.core.HapticFeedback
import ch.cclerc.luxapp.core.SFSymbol
import ch.cclerc.luxapp.data.CrowdConsent
import ch.cclerc.luxapp.data.Settings
import ch.cclerc.luxapp.domain.LineScoreManager
import ch.cclerc.luxapp.domain.shortcut.ShortcutManager
import ch.cclerc.luxapp.ui.anim.scaleClickable
import ch.cclerc.luxapp.ui.components.settings.LineStylePicker
import ch.cclerc.luxapp.ui.components.settings.SectionHeader
import ch.cclerc.luxapp.ui.components.settings.SettingsCard
import ch.cclerc.luxapp.ui.components.settings.SettingsPicker
import ch.cclerc.luxapp.ui.components.settings.SettingsRow
import ch.cclerc.luxapp.ui.components.settings.SettingsToggle
import ch.cclerc.luxapp.ui.navigation.LocalSheetController
import ch.cclerc.luxapp.ui.theme.LuxTheme
import java.util.Locale
import java.io.File
import ch.cclerc.luxapp.domain.onboard.OnboardMotionRecorder
import androidx.core.content.FileProvider
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import android.text.format.Formatter

private const val PRIVACY_FR = "https://lux.cclerc.ch/privacy/fr.html"
private const val PRIVACY_EN = "https://lux.cclerc.ch/privacy/en.html"
private const val TPG_NETWORK_MAP =
    "https://www.tpg.ch/sites/default/files/2025-08/Geneve%20TPG%20Plan%20Schematique%202025-08-18.pdf"
private const val HELP_MAIL = "lux-help@cclerc.ch"

internal fun appVersionName(context: Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
        .getOrNull() ?: "Inconnue"

internal fun isDebugBuild(context: Context): Boolean =
    (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

internal fun openCustomTab(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        putExtra("android.support.customtabs.extra.SESSION", null as Bundle?)
        putExtra("androidx.browser.customtabs.extra.SHARE_STATE", 2)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}

internal fun openHelpMail(context: Context) {
    val version = appVersionName(context)
    val body = "\n\n---\nVeuillez ne pas supprimer le texte ci-dessous\nv$version"
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$HELP_MAIL")).apply {
        putExtra(Intent.EXTRA_SUBJECT, "Aide Lux")
        putExtra(Intent.EXTRA_TEXT, body)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}

private fun isFrench(): Boolean = Locale.getDefault().language == "fr"

internal sealed interface SettingsRoute {
    data object Root : SettingsRoute
    data object Shortcuts : SettingsRoute
    data object LineScores : SettingsRoute
    data object AccentColor : SettingsRoute
    data object ColorScheme : SettingsRoute
    data object Advanced : SettingsRoute
    data object Credits : SettingsRoute
    data object Stats : SettingsRoute
}

internal class SettingsNavigator {
    val stack = mutableStateListOf<SettingsRoute>(SettingsRoute.Root)
    var popping by mutableStateOf(false)
        private set

    fun push(route: SettingsRoute) {
        popping = false
        stack.add(route)
    }

    fun pop() {
        if (stack.size <= 1) return
        popping = true
        stack.removeAt(stack.lastIndex)
    }
}

@Composable
internal fun SettingsHeaderCard(
    icon: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    val colors = LuxTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.secondarySystemGroupedBackgroundElevated, shape)
            .border(0.5.dp, colors.hairline, shape)
            .padding(vertical = 24.dp, horizontal = 20.dp)
    ) {
        SFSymbol(name = icon, size = 40.sp, color = LuxTheme.accent)
        Text(
            text = title,
            style = LuxTheme.type.title2,
            color = colors.label
        )
        Text(
            text = subtitle,
            style = LuxTheme.type.subheadline,
            color = colors.secondaryLabel,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
internal fun SettingsSubScreen(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LuxTheme.colors
    Column(
        modifier
            .fillMaxSize()
            .background(colors.systemGroupedBackgroundElevated)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 20.dp, top = 26.dp, bottom = 6.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .scaleClickable(haptic = false) {
                        HapticFeedback.softImpact()
                        onBack()
                    }
            ) {
                SFSymbol(name = "chevron.left", size = 18.sp, color = LuxTheme.accent)
            }
            Text(
                text = title,
                style = LuxTheme.type.headline,
                color = colors.label
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content
        )
    }
}

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null
) {
    val sheets = LocalSheetController.current
    val nav = remember { SettingsNavigator() }
    val close = onClose ?: { sheets.dismiss() }

    BackHandler(enabled = nav.stack.size > 1) { nav.pop() }

    AnimatedContent(
        targetState = nav.stack.last(),
        transitionSpec = {
            if (nav.popping) {
                (slideInHorizontally { -it / 4 } + fadeIn()) togetherWith
                    (slideOutHorizontally { it } + fadeOut())
            } else {
                (slideInHorizontally { it } + fadeIn()) togetherWith
                    (slideOutHorizontally { -it / 4 } + fadeOut())
            }
        },
        label = "settingsNav",
        modifier = modifier.fillMaxSize()
    ) { route ->
        when (route) {
            SettingsRoute.Root -> SettingsRootView(nav = nav, onClose = close)
            SettingsRoute.Shortcuts -> ShortcutsListView(onBack = { nav.pop() })
            SettingsRoute.LineScores -> LineScoreView(onBack = { nav.pop() })
            SettingsRoute.AccentColor -> AccentColorCustomizerView(onBack = { nav.pop() })
            SettingsRoute.ColorScheme -> ColorSchemeSelectionView(
                onBack = { nav.pop() },
                onPicked = close
            )
            SettingsRoute.Advanced -> AdvancedSettingsView(
                onBack = { nav.pop() },
                onOpenStats = { nav.push(SettingsRoute.Stats) }
            )
            SettingsRoute.Credits -> CreditsView(onBack = { nav.pop() })
            SettingsRoute.Stats -> StatsView(onBack = { nav.pop() })
        }
    }
}

@Composable
private fun SettingsRootView(nav: SettingsNavigator, onClose: () -> Unit) {
    val colors = LuxTheme.colors
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val shortcuts by ShortcutManager.shared.shortcuts.collectAsState()
    val lineScores by LineScoreManager.shared.allScores.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.systemGroupedBackgroundElevated)
            .verticalScroll(rememberScrollState())
            .padding(top = 26.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = "Paramètres",
            style = LuxTheme.type.largeTitle,
            color = colors.label,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        SettingsHeaderCard(
            icon = "gearshape",
            title = "Paramètres",
            subtitle = "Personnalisez votre expérience Lux",
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Column(
            Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsCard {
                SectionHeader(
                    icon = "link",
                    iconColor = colors.systemBlue,
                    title = "Raccourcis",
                    subtitle = "Accès rapide à vos destinations"
                )
                val plural = if (shortcuts.size > 1) "s" else ""
                SettingsRow(
                    icon = "list.star",
                    title = "Gérer les raccourcis",
                    subtitle = "${shortcuts.size} raccourci$plural configuré$plural",
                    onClick = { nav.push(SettingsRoute.Shortcuts) }
                )
                SettingsToggle(
                    icon = "textformat",
                    title = "Afficher les titres",
                    subtitle = "Affiche le nom des raccourcis",
                    checked = Settings.showShortcutLabel,
                    onCheckedChange = { Settings.showShortcutLabel = it }
                )
                SettingsToggle(
                    icon = "clock.fill",
                    title = "Tri par pertinence",
                    subtitle = "Affiche les raccourcis les plus pertinents en premier",
                    checked = Settings.useTimeBasedRelevance,
                    onCheckedChange = {
                        Settings.useTimeBasedRelevance = it
                        ShortcutManager.shared.updateVisibleShortcuts()
                    }
                )
            }

            SettingsCard {
                SectionHeader(
                    icon = "chart.line.uptrend.xyaxis",
                    iconColor = colors.systemGreen,
                    title = "Lignes",
                    subtitle = "Gérez les lignes que vous fréquentez le plus souvent"
                )
                val highScore = lineScores.count { it.totalScore >= 2.0 }
                SettingsRow(
                    icon = "chart.bar.fill",
                    title = "Lignes préférées",
                    subtitle = if (highScore == 0) {
                        "Aucune ligne enregistrée"
                    } else {
                        "$highScore ligne${if (highScore > 1) "s" else ""}"
                    },
                    onClick = { nav.push(SettingsRoute.LineScores) }
                )
            }

            SettingsCard {
                SectionHeader(
                    icon = "paintbrush.fill",
                    iconColor = colors.systemRed,
                    title = "Personnalisation",
                    subtitle = "Adaptez l'interface à vos préférences"
                )
                SettingsToggle(
                    icon = "clock.badge",
                    title = "Afficher le retard exact",
                    subtitle = "Affiche le retard à côté de l'heure prévue (sinon, inclus dans l'heure)",
                    checked = Settings.showDelayInsteadOfDirectTime,
                    onCheckedChange = { Settings.showDelayInsteadOfDirectTime = it }
                )
                if (configuration.smallestScreenWidthDp < 600) {
                    SettingsToggle(
                        icon = "rectangle.compress.vertical",
                        title = "Interface compacte",
                        subtitle = "Réduire l'espacement dans l'onglet des arrêts",
                        checked = Settings.reduceSpacerBtwnStopContent,
                        onCheckedChange = { Settings.reduceSpacerBtwnStopContent = it }
                    )
                }
                SettingsToggle(
                    icon = "list.bullet.indent",
                    title = "Afficher l'historique",
                    subtitle = "Liste les recherches récentes dans vos destinations",
                    checked = Settings.showHistory,
                    onCheckedChange = { Settings.showHistory = it }
                )
                LineStylePicker(
                    icon = "slider.horizontal.3",
                    title = "Affichage des lignes",
                    subtitle = "Choisissez comment les lignes sont affichées dans l'application"
                )
                SettingsRow(
                    icon = "paintpalette",
                    title = "Couleur de l'app",
                    subtitle = "Personnalisez l'apparence de l'application",
                    onClick = { nav.push(SettingsRoute.AccentColor) }
                )
                SettingsRow(
                    icon = "circle.lefthalf.filled",
                    title = "Mode d'affichage",
                    subtitle = "Chosissez la mode d'affichage de l'app (clair, sombre, auto..)",
                    onClick = { nav.push(SettingsRoute.ColorScheme) }
                )
            }

            SettingsCard {
                SectionHeader(
                    icon = "location.north.line.fill",
                    iconColor = colors.systemBlue,
                    title = "Mode À bord",
                    subtitle = "Navigation pas à pas pendant votre trajet"
                )
                SettingsToggle(
                    icon = "speaker.wave.2.fill",
                    title = "Guidage vocal",
                    subtitle = "Annonce les changements de direction, les retards et quand descendre",
                    checked = Settings.onboardVoiceGuidance,
                    onCheckedChange = { Settings.onboardVoiceGuidance = it }
                )
                SettingsToggle(
                    icon = "antenna.radiowaves.left.and.right",
                    title = "Partager la position du véhicule",
                    subtitle = "À bord, envoie anonymement la position du véhicule pour calculer son retard et l'afficher en direct aux autres voyageurs",
                    checked = Settings.onboardCrowdConsent == CrowdConsent.GRANTED,
                    onCheckedChange = {
                        Settings.onboardCrowdConsent = if (it) CrowdConsent.GRANTED else CrowdConsent.DECLINED
                    }
                )
                SettingsToggle(
                    icon = "waveform.path.ecg",
                    title = "Enregistrer les mouvements",
                    subtitle = "Garde sur l'appareil l'accéléromètre et la position de chaque trajet, pour améliorer la détection des arrêts sous terre",
                    checked = Settings.onboardMotionRecording,
                    onCheckedChange = { Settings.onboardMotionRecording = it }
                )
                MotionRecordingsRow()
            }

            SettingsCard {
                SettingsRow(
                    icon = "flask.fill",
                    title = "Fonctionnalités avancées",
                    subtitle = "Options pour les utilisateurs expérimentés",
                    onClick = { nav.push(SettingsRoute.Advanced) }
                )
            }

            SettingsCard {
                SectionHeader(
                    icon = "info.circle.fill",
                    iconColor = colors.systemGray,
                    title = "À propos",
                    subtitle = "Informations sur l'application"
                )
                SettingsRow(
                    icon = "heart",
                    title = "Crédits",
                    subtitle = "Contributeurs ayant aidé à la création de l'application",
                    onClick = { nav.push(SettingsRoute.Credits) }
                )
                SettingsRow(
                    icon = "lock.shield",
                    title = "Politique de confidentialité",
                    subtitle = "Consultez la politique de confidentialité en ligne",
                    external = true,
                    onClick = { openCustomTab(context, if (isFrench()) PRIVACY_FR else PRIVACY_EN) }
                )
                SettingsRow(
                    icon = "questionmark.circle",
                    title = "Aide",
                    subtitle = "Une question, un bug ou une suggestion ? Cliquez ici.",
                    external = true,
                    onClick = { openHelpMail(context) }
                )
                SettingsRow(
                    icon = "app.badge",
                    title = "Version",
                    subtitle = appVersionName(context),
                    showChevron = false
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Lux v${appVersionName(context)}",
                style = LuxTheme.type.caption2,
                color = colors.tertiaryLabel,
                modifier = Modifier.scaleClickable(haptic = false) { onClose() }
            )
        }
    }
}

@Composable
private fun AdvancedSettingsView(onBack: () -> Unit, onOpenStats: () -> Unit) {
    val colors = LuxTheme.colors
    val context = LocalContext.current
    SettingsSubScreen(title = "Fonctionnalités avancées", onBack = onBack) {
        SettingsHeaderCard(
            icon = "flask.fill",
            title = "Fonctionnalités avancées",
            subtitle = "Options pour les utilisateurs expérimentés",
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        SettingsCard(Modifier.padding(horizontal = 16.dp)) {
            SectionHeader(
                icon = "flask.fill",
                iconColor = colors.systemPurple,
                title = "Fonctionnalités avancées",
                subtitle = "Options pour les utilisateurs expérimentés"
            )
            SettingsToggle(
                icon = "exclamationmark.bubble",
                title = "Contribuer à CrowdBack",
                subtitle = "Consultez et partagez des informations en temps réel sur les transports.",
                checked = Settings.crowdbackAllowed,
                onCheckedChange = { Settings.crowdbackAllowed = it }
            )
            SettingsToggle(
                icon = "link",
                title = "Activer l'option de partage d'arrêt",
                subtitle = "Ajouter un bouton vous permettant de partager un arrêt sous forme de lien.",
                checked = Settings.showDebug,
                onCheckedChange = { Settings.showDebug = it }
            )
            SettingsPicker(
                icon = "square.and.arrow.up",
                title = "Durée de partage d'itinéraire",
                subtitle = "Choisissez combien de temps un itinéraire partagé reste accessible" +
                    if (Settings.luxTripShareExpiryTimeH >= 4320) {
                        "\n⚠︎ Le temps d'expiration sélectionné est élevé. " +
                            "Lux ne peut garantir une telle période de rétention."
                    } else {
                        ""
                    },
                selection = Settings.luxTripShareExpiryTimeH,
                options = listOf(
                    24 to "1 jour",
                    168 to "7 jours",
                    720 to "1 mois",
                    4320 to "6 mois",
                    8760 to "1 an"
                ),
                onSelectionChange = { Settings.luxTripShareExpiryTimeH = it }
            )
            SettingsRow(
                icon = "map",
                title = "Afficher le plan",
                subtitle = "Ouvrir la carte du réseau officiel des tpg",
                external = true,
                onClick = { openCustomTab(context, TPG_NETWORK_MAP) }
            )
            if (isDebugBuild(context)) {
                SettingsRow(
                    icon = "chart.bar.doc.horizontal",
                    title = "Statistiques",
                    subtitle = "Consultez les statistiques de votre usage de l'application (dev only)",
                    onClick = onOpenStats
                )
            }
        }
    }
}

@Composable
private fun MotionRecordingsRow() {
    val colors = LuxTheme.colors
    val accent = LuxTheme.accent
    val context = LocalContext.current
    var recordings by remember { mutableStateOf(OnboardMotionRecorder.recordings(context)) }
    var size by remember { mutableStateOf(OnboardMotionRecorder.totalSize(context)) }
    var confirmsDelete by remember { mutableStateOf(false) }

    fun reload() {
        recordings = OnboardMotionRecorder.recordings(context)
        size = OnboardMotionRecorder.totalSize(context)
    }

    if (recordings.isEmpty()) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            SFSymbol(name = "square.and.arrow.up", size = 20.sp, color = accent)
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .weight(1f)
                .scaleClickable { shareRecordings(context, recordings) }
        ) {
            Text(
                text = "Exporter les enregistrements",
                style = LuxTheme.type.body.copy(fontWeight = FontWeight.Medium),
                color = colors.label
            )
            Text(
                text = "${recordings.size} trajets · ${Formatter.formatShortFileSize(context, size)}",
                style = LuxTheme.type.caption,
                color = colors.secondaryLabel
            )
        }
        Box(
            Modifier
                .size(32.dp)
                .scaleClickable { confirmsDelete = true },
            contentAlignment = Alignment.Center
        ) {
            SFSymbol(name = "trash", size = 17.sp, color = colors.systemRed)
        }
    }

    if (confirmsDelete) {
        AlertDialog(
            onDismissRequest = { confirmsDelete = false },
            title = { Text("Supprimer les enregistrements ?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmsDelete = false
                    OnboardMotionRecorder.deleteAll(context)
                    reload()
                }) { Text("Supprimer", color = colors.systemRed) }
            },
            dismissButton = {
                TextButton(onClick = { confirmsDelete = false }) { Text("Annuler") }
            }
        )
    }
}

private fun shareRecordings(context: Context, recordings: List<File>) {
    val authority = "${context.packageName}.recordings"
    val uris = ArrayList(recordings.map { FileProvider.getUriForFile(context, authority, it) })
    val intent = Intent(Intent.ACTION_SEND_MULTIPLE)
        .setType("text/csv")
        .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { context.startActivity(Intent.createChooser(intent, null)) }
}

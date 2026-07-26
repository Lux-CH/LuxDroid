package ch.cclerc.luxapp.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import ch.cclerc.luxapp.data.Progress
import ch.cclerc.luxapp.data.Settings
import ch.cclerc.luxapp.ui.components.settings.SectionHeader
import ch.cclerc.luxapp.ui.components.settings.SettingsCard
import ch.cclerc.luxapp.ui.theme.LuxTheme

@Composable
fun StatsView(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LuxTheme.colors
    SettingsSubScreen(title = "Statistiques", onBack = onBack, modifier = modifier) {
        SettingsHeaderCard(
            icon = "chart.bar.doc.horizontal",
            title = "Statistiques",
            subtitle = "Consultez les statistiques de votre usage de l'app (dev only)",
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        SettingsCard(Modifier.padding(horizontal = 16.dp)) {
            SectionHeader(
                icon = "gearshape.2",
                iconColor = colors.systemPurple,
                title = "settingsDomain",
                subtitle = "Les paramètres ci-dessous sont issus du domaine \"settings\"."
            )
            StatRow("appLaunchCount", Settings.appLaunchCount)
            StatDivider()
            StatRow("isFirstLaunch", Settings.firstLaunch)
            StatDivider()
            StatRow("tripShareExpiryHours", Settings.luxTripShareExpiryTimeH)
            StatDivider()
            if (Settings.customScheme) {
                StatRow("customAccent", Settings.customSchemeSelection)
                StatDivider()
            }
            StatRow("crowdbackAllowed", Settings.crowdbackAllowed)
        }

        SettingsCard(Modifier.padding(horizontal = 16.dp)) {
            SectionHeader(
                icon = "trophy",
                iconColor = colors.systemPurple,
                title = "progressDomain",
                subtitle = "Les paramètres ci-dessous sont issus du domaine \"progress\"."
            )
            StatRow("stopViewOpened", Progress.numOfTimesStopViewWasOpened)
            StatDivider()
            StatRow("tripViewOpened", Progress.numOfTimesTripViewWasOpened)
        }

        SettingsCard(Modifier.padding(horizontal = 16.dp)) {
            SectionHeader(
                icon = "lightbulb",
                iconColor = colors.systemPurple,
                title = "tipsDomain",
                subtitle = "Les paramètres ci-dessous sont issus du domaine \"tips\"."
            )
            StatRow("shownTripViewSuggestion", Progress.shownTripViewSuggestion)
        }
    }
}

@Composable
private fun StatRow(varName: String, value: Any) {
    val colors = LuxTheme.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp, vertical = 15.dp)
    ) {
        Text(text = varName, style = LuxTheme.type.body, color = colors.label)
        Spacer(Modifier.weight(1f))
        Text(
            text = value.toString(),
            style = LuxTheme.type.subheadline.copy(fontFamily = FontFamily.Monospace),
            color = colors.secondaryLabel
        )
    }
}

@Composable
private fun StatDivider() {
    HorizontalDivider(thickness = 0.5.dp, color = LuxTheme.colors.separator)
}

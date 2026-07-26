package ch.cclerc.luxapp.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.cclerc.luxapp.core.HapticFeedback
import ch.cclerc.luxapp.core.SFSymbol
import ch.cclerc.luxapp.data.LineScore
import ch.cclerc.luxapp.domain.LineScoreManager
import ch.cclerc.luxapp.ui.anim.scaleClickable
import ch.cclerc.luxapp.ui.components.LinePill
import ch.cclerc.luxapp.ui.components.settings.SectionHeader
import ch.cclerc.luxapp.ui.components.settings.SettingsCard
import ch.cclerc.luxapp.ui.components.settings.SettingsRow
import ch.cclerc.luxapp.ui.navigation.LocalSheetController
import ch.cclerc.luxapp.ui.navigation.LuxSheetRequest
import ch.cclerc.luxapp.ui.navigation.SheetDetent
import ch.cclerc.luxapp.ui.theme.LuxSprings
import ch.cclerc.luxapp.ui.theme.LuxTheme
import ch.cclerc.luxcom.model.TransportationMode
import java.util.Locale

@Composable
fun LineScoreView(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LuxTheme.colors
    val accent = LuxTheme.accent
    val sheets = LocalSheetController.current
    val manager = LineScoreManager.shared
    val scores by manager.allScores.collectAsState()
    var showLowScoreLines by remember { mutableStateOf(false) }

    val highScoreLines = scores.filter { it.totalScore >= 2.0 }.sortedByDescending { it.totalScore }
    val lowScoreLines = scores
        .filter { it.totalScore < 2.0 && it.totalScore != 0.0 }
        .sortedByDescending { it.totalScore }

    SettingsSubScreen(title = "Lignes préférées", onBack = onBack, modifier = modifier) {
        SettingsHeaderCard(
            icon = "chart.bar.fill",
            title = "Lignes préférées",
            subtitle = "Mettez en avant les lignes que vous fréquentez le plus.",
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        SettingsCard(Modifier.padding(horizontal = 16.dp)) {
            SectionHeader(
                icon = "list.bullet",
                iconColor = colors.systemBlue,
                title = "Mes lignes",
                subtitle = "Gérez vos lignes et leurs scores"
            )

            if (scores.isEmpty()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp, horizontal = 24.dp)
                ) {
                    SFSymbol(name = "tram", size = 50.sp, color = colors.secondaryLabel)
                    Text(
                        text = "Aucune ligne ajoutée",
                        style = LuxTheme.type.headline,
                        color = colors.secondaryLabel
                    )
                    Text(
                        text = "Ajoutez des lignes pour qu'elles soient mises en avant.",
                        style = LuxTheme.type.subheadline,
                        color = colors.secondaryLabel,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                highScoreLines.forEach { lineScore ->
                    LineScoreRow(lineScore = lineScore)
                }

                if (lowScoreLines.isNotEmpty()) {
                    val chevronRotation by animateFloatAsState(
                        targetValue = if (showLowScoreLines) 90f else 0f,
                        animationSpec = LuxSprings.springFor(0.4, 0.8),
                        label = "autoGroupChevron"
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.quaternarySystemFill)
                            .scaleClickable(haptic = false) {
                                HapticFeedback.softImpact()
                                showLowScoreLines = !showLowScoreLines
                            }
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                    ) {
                        SFSymbol(name = "eye.slash", size = 12.sp, color = colors.secondaryLabel)
                        Text(
                            text = "Ajoutées automatiquement",
                            style = LuxTheme.type.caption,
                            color = colors.secondaryLabel
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "${lowScoreLines.size}",
                            style = LuxTheme.type.caption2,
                            color = colors.secondaryLabel,
                            modifier = Modifier
                                .clip(RoundedCornerShape(percent = 50))
                                .background(colors.tertiarySystemFill)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                        SFSymbol(
                            name = "chevron.right",
                            size = 11.sp,
                            color = colors.secondaryLabel,
                            modifier = Modifier.graphicsLayer { rotationZ = chevronRotation }
                        )
                    }

                    AnimatedVisibility(
                        visible = showLowScoreLines,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            lowScoreLines.forEach { lineScore ->
                                LineScoreRow(
                                    lineScore = lineScore,
                                    modifier = Modifier.graphicsLayer { alpha = 0.7f }
                                )
                            }
                        }
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .scaleClickable {
                        sheets.present(
                            LuxSheetRequest(
                                cornerRadius = 38.dp,
                                detents = listOf(SheetDetent.Medium)
                            ) {
                                AddLineScoreView(onDismiss = { sheets.dismiss() })
                            }
                        )
                    }
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                SFSymbol(name = "plus.circle.fill", size = 17.sp, color = accent)
                Text(
                    text = "Ajouter une ligne",
                    style = LuxTheme.type.body,
                    color = accent
                )
            }
        }

        SettingsCard(Modifier.padding(horizontal = 16.dp)) {
            SectionHeader(
                icon = "gearshape",
                iconColor = colors.systemOrange,
                title = "Actions rapides",
                subtitle = "Gestion globale des scores"
            )
            SettingsRow(
                icon = "arrow.clockwise",
                title = "Réinitialiser tous les scores",
                subtitle = "Remet à zéro tous les scores des lignes",
                showChevron = false,
                onClick = if (scores.isEmpty()) {
                    null
                } else {
                    {
                        HapticFeedback.mediumImpact()
                        scores.forEach { manager.removeScore(it.routeShortName) }
                    }
                }
            )
        }
    }
}

@Composable
private fun LineScoreRow(lineScore: LineScore, modifier: Modifier = Modifier) {
    val colors = LuxTheme.colors
    val manager = LineScoreManager.shared
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        LinePill(
            line = lineScore.routeShortName,
            agencyId = null,
            mode = TransportationMode.BUS,
            width = 50.dp,
            height = 30.dp,
            fontSize = 14.sp
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = "Ligne ${lineScore.routeShortName}",
                style = LuxTheme.type.body.copy(fontWeight = FontWeight.Medium),
                color = colors.label
            )
            Text(
                text = "Score: ${String.format(Locale.ROOT, "%.1f", lineScore.totalScore)}",
                style = LuxTheme.type.caption,
                color = colors.secondaryLabel
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(28.dp)
                    .scaleClickable { manager.addScore(lineScore.routeShortName, 1.0) },
                contentAlignment = Alignment.Center
            ) {
                SFSymbol(name = "plus.circle.fill", size = 20.sp, color = colors.systemGreen)
            }
            if (lineScore.totalScore > 2.9) {
                Box(
                    Modifier
                        .size(28.dp)
                        .scaleClickable { manager.addScore(lineScore.routeShortName, -1.0) },
                    contentAlignment = Alignment.Center
                ) {
                    SFSymbol(name = "minus.circle.fill", size = 20.sp, color = colors.systemOrange)
                }
            }
            Box(
                Modifier
                    .size(28.dp)
                    .scaleClickable { manager.removeScore(lineScore.routeShortName) },
                contentAlignment = Alignment.Center
            ) {
                SFSymbol(name = "trash.circle.fill", size = 20.sp, color = colors.systemRed)
            }
        }
    }
}

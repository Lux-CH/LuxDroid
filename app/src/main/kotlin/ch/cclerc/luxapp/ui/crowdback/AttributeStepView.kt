package ch.cclerc.luxapp.ui.crowdback

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import ch.cclerc.luxapp.core.SFSymbol
import ch.cclerc.luxapp.ui.anim.PlainIndication
import ch.cclerc.luxapp.ui.theme.LuxColors
import ch.cclerc.luxapp.ui.theme.LuxTheme
import ch.cclerc.luxcom.model.feedback.ReportAttribute

val ReportAttribute.displayName: String
    get() = when (this) {
        ReportAttribute.CROWD -> "Affluence"
        ReportAttribute.CLEAN -> "Propreté"
        ReportAttribute.HEAT -> "Température"
        ReportAttribute.NOISE -> "Bruit"
        ReportAttribute.SMELL -> "Odeur"
    }

val ReportAttribute.lowLevelText: String
    get() = when (this) {
        ReportAttribute.CROWD -> "Vide"
        ReportAttribute.CLEAN -> "Sale"
        ReportAttribute.HEAT -> "Froid"
        ReportAttribute.NOISE -> "Silencieux"
        ReportAttribute.SMELL -> "Pas d'odeur"
    }

val ReportAttribute.highLevelText: String
    get() = when (this) {
        ReportAttribute.CROWD -> "Bondé"
        ReportAttribute.CLEAN -> "Propre"
        ReportAttribute.HEAT -> "Chaud"
        ReportAttribute.NOISE -> "Bruyant"
        ReportAttribute.SMELL -> "Forte odeur"
    }

fun ReportAttribute.intensityDescription(level: Int): String = when (this) {
    ReportAttribute.CROWD -> when (level) {
        1 -> "Vide"
        2 -> "Peu occupé"
        3 -> "Modéré"
        4 -> "Occupé"
        5 -> "Bondé"
        else -> ""
    }

    ReportAttribute.CLEAN -> when (level) {
        1 -> "Très sale"
        2 -> "Sale"
        3 -> "Correct"
        4 -> "Propre"
        5 -> "Très propre"
        else -> ""
    }

    ReportAttribute.HEAT -> when (level) {
        1 -> "Froid"
        2 -> "Frais"
        3 -> "Tempéré"
        4 -> "Chaud"
        5 -> "Très chaud"
        else -> ""
    }

    ReportAttribute.NOISE -> when (level) {
        1 -> "Silencieux"
        2 -> "Calme"
        3 -> "Modéré"
        4 -> "Bruyant"
        5 -> "Très bruyant"
        else -> ""
    }

    ReportAttribute.SMELL -> when (level) {
        1 -> "Pas d'odeur"
        2 -> "Légère"
        3 -> "Perceptible"
        4 -> "Forte"
        5 -> "Très forte"
        else -> ""
    }
}

fun ReportAttribute.color(level: Int, colors: LuxColors): Color = color(level.toDouble(), colors)

@Composable
fun AttributeStepView(
    attribute: ReportAttribute,
    selectedLevel: Int,
    onLevelChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LuxTheme.colors
    val accent = LuxTheme.accent
    val selectedColor = attribute.color(selectedLevel, colors)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .background(accent.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                SFSymbol(
                    name = attribute.iconName,
                    size = 24.sp,
                    color = accent
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = attribute.displayName,
                    style = LuxTheme.type.title3.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.label
                )
                Text(
                    text = "Évaluez le niveau actuel",
                    style = LuxTheme.type.caption,
                    color = colors.secondaryLabel
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = attribute.lowLevelText,
                    style = LuxTheme.type.caption2,
                    color = colors.secondaryLabel
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = attribute.highLevelText,
                    style = LuxTheme.type.caption2,
                    color = colors.secondaryLabel
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                (1..5).forEach { level ->
                    val target = if (level <= selectedLevel) {
                        attribute.color(level, colors)
                    } else {
                        colors.systemGray5
                    }
                    val barColor by animateColorAsState(target, tween(200))
                    Box(
                        Modifier
                            .weight(1f)
                            .height(8.dp)
                            .background(barColor, RoundedCornerShape(6.dp))
                            .clickable(
                                interactionSource = null,
                                indication = PlainIndication,
                                onClick = { onLevelChange(level) }
                            )
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$selectedLevel/5",
                    style = LuxTheme.type.caption.copy(fontWeight = FontWeight.Medium),
                    color = selectedColor
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = attribute.intensityDescription(selectedLevel),
                    style = LuxTheme.type.caption.copy(fontWeight = FontWeight.Medium),
                    color = selectedColor
                )
            }
        }
    }
}

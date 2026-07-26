package ch.cclerc.luxapp.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.cclerc.luxapp.core.HapticFeedback
import ch.cclerc.luxapp.core.SFSymbol
import ch.cclerc.luxapp.domain.LineScoreManager
import ch.cclerc.luxapp.ui.anim.scaleClickable
import ch.cclerc.luxapp.ui.components.ModernCard
import ch.cclerc.luxapp.ui.components.ModernCardStyle
import ch.cclerc.luxapp.ui.theme.LuxTheme
import ch.cclerc.luxcom.model.TransportationMode
import java.util.Locale

@Composable
fun AddLineScoreView(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LuxTheme.colors
    val accent = LuxTheme.accent
    var lineNumber by remember { mutableStateOf("") }
    val isValidLine = lineNumber.trim().isNotEmpty()

    Column(
        modifier
            .fillMaxSize()
            .background(colors.systemGroupedBackground)
            .imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 26.dp, bottom = 8.dp)
        ) {
            Text(
                text = "Annuler",
                style = LuxTheme.type.body,
                color = accent,
                modifier = Modifier.scaleClickable(haptic = false) { onDismiss() }
            )
        }

        Spacer(Modifier.weight(1f))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            Text(
                text = "Ajouter une ligne",
                style = LuxTheme.type.title2.copy(fontWeight = FontWeight.Bold),
                color = colors.label
            )
            Text(
                text = "Entrez le numéro de la ligne à ajouter aux favoris",
                style = LuxTheme.type.subheadline,
                color = colors.secondaryLabel,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.padding(top = 30.dp))

        ModernCard(
            style = ModernCardStyle.Normal,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
            ) {
                Text(
                    text = "Numéro de ligne".uppercase(Locale.getDefault()),
                    style = LuxTheme.type.caption,
                    color = colors.secondaryLabel
                )
                EditableLinePill(
                    lineNumber = lineNumber,
                    onLineNumberChange = { lineNumber = it },
                    mode = TransportationMode.BUS
                )
            }
        }

        Spacer(Modifier.weight(1f))

        ModernCard(
            style = ModernCardStyle.Accent,
            optionalColor = accent,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .scaleClickable(enabled = isValidLine) {
                    HapticFeedback.mediumImpact()
                    LineScoreManager.shared.addScore(lineNumber.trim().uppercase(Locale.ROOT), 2.0)
                    onDismiss()
                }
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Spacer(Modifier.weight(1f))
                SFSymbol(name = "plus", size = 17.sp, color = accent)
                Text(
                    text = "Ajouter cette ligne",
                    style = LuxTheme.type.subheadline.copy(fontWeight = FontWeight.Medium),
                    color = accent
                )
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

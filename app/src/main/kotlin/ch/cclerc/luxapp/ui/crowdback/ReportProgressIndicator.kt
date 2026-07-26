package ch.cclerc.luxapp.ui.crowdback

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ch.cclerc.luxapp.ui.theme.LuxSprings
import ch.cclerc.luxapp.ui.theme.LuxTheme

@Composable
fun ReportProgressIndicator(
    currentStep: Int,
    totalSteps: Int,
    modifier: Modifier = Modifier
) {
    val colors = LuxTheme.colors
    val accent = LuxTheme.accent

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            (0 until totalSteps).forEach { step ->
                val size by animateDpAsState(
                    if (step == currentStep) 12.dp else 8.dp,
                    LuxSprings.springFor(0.4, 0.7)
                )
                Box(
                    Modifier
                        .size(size)
                        .background(
                            if (step <= currentStep) accent else colors.systemGray4,
                            CircleShape
                        )
                )
            }
        }

        Row(
            modifier = Modifier
                .background(accent.copy(alpha = 0.1f), RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${currentStep + 1}",
                style = LuxTheme.type.caption.copy(fontWeight = FontWeight.SemiBold),
                color = accent
            )
            Text(
                text = "sur",
                style = LuxTheme.type.caption,
                color = colors.secondaryLabel
            )
            Text(
                text = "$totalSteps",
                style = LuxTheme.type.caption.copy(fontWeight = FontWeight.Medium),
                color = colors.secondaryLabel
            )
        }
    }
}

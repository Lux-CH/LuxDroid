package ch.cclerc.luxapp.ui.itinerary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ch.cclerc.luxapp.ui.theme.LuxTheme

@Composable
fun StopTypeLabel(
    isDepartureStop: Boolean,
    isArrivalStop: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = LuxTheme.colors
    val shape = RoundedCornerShape(8.dp)

    when {
        isDepartureStop -> Text(
            text = "Départ",
            style = LuxTheme.type.caption.copy(fontWeight = FontWeight.Medium),
            color = colors.systemGreen,
            modifier = modifier
                .background(colors.systemGreen.copy(alpha = 0.15f), shape)
                .padding(horizontal = 8.dp, vertical = 3.dp)
        )
        isArrivalStop -> Text(
            text = "Arrivée",
            style = LuxTheme.type.caption.copy(fontWeight = FontWeight.Medium),
            color = colors.systemRed,
            modifier = modifier
                .background(colors.systemRed.copy(alpha = 0.15f), shape)
                .padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

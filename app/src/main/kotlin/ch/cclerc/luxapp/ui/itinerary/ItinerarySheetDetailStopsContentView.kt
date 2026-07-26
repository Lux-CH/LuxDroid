package ch.cclerc.luxapp.ui.itinerary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ch.cclerc.luxapp.ui.theme.LuxTheme
import ch.cclerc.luxcom.model.Place

@Composable
fun ItinerarySheetDetailStopsContentView(
    stops: List<Place>,
    legColor: Color,
    fromStop: Place,
    toStop: Place,
    duration: Int,
    isMultipleLeg: Boolean,
    onSelectStop: (Place) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LuxTheme.colors

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isMultipleLeg) "Arrêts" else "Prochains arrêts",
                style = LuxTheme.type.headline,
                color = colors.label
            )
            Spacer(Modifier.weight(1f))
            if (isMultipleLeg) {
                Text(
                    text = "${duration / 60}min",
                    style = LuxTheme.type.subheadline,
                    color = colors.secondaryLabel
                )
                Text(
                    text = "•",
                    style = LuxTheme.type.subheadline,
                    color = colors.secondaryLabel
                )
            }
            Text(
                text = "${stops.size} arrêts",
                style = LuxTheme.type.subheadline,
                color = colors.secondaryLabel
            )
        }

        if (stops.isEmpty()) {
            ItineraryContentUnavailable(
                symbol = "flag.checkered",
                title = "Aucun arrêt prévu",
                description = "Plus d'arrêts prévus sur cette ligne.",
                modifier = Modifier.padding(16.dp)
            )
        } else {
            ItineraryStopTimelineView(
                stops = stops,
                legColor = legColor,
                accentColor = LuxTheme.accent,
                fromStop = fromStop,
                toStop = toStop,
                isMultipleLeg = isMultipleLeg,
                onSelectStop = onSelectStop,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

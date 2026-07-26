package ch.cclerc.luxapp.ui.stop.expanded

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.cclerc.luxapp.core.SFSymbol
import ch.cclerc.luxapp.ui.anim.scaleClickable
import ch.cclerc.luxapp.ui.components.LinePill
import ch.cclerc.luxapp.ui.home.ArrivalMinuteView
import ch.cclerc.luxapp.ui.theme.LuxTheme
import ch.cclerc.luxcom.model.stop.StopTime

@Composable
fun ExpandedDepartureRowView(
    stopTime: StopTime,
    onOpenTrip: (String) -> Unit,
    onSelectLine: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LuxTheme.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.secondarySystemBackground.copy(alpha = 0.1f))
            .scaleClickable {
                onSelectLine(stopTime.routeShortName)
                onOpenTrip(stopTime.tripId)
            }
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LinePill(
            line = stopTime.routeShortName,
            agencyId = stopTime.agencyId,
            mode = stopTime.mode,
            width = 32.dp,
            height = 20.dp,
            fontSize = 13.sp
        )

        SFSymbol(
            name = "arrow.right",
            size = 16.sp,
            color = colors.label.copy(alpha = 0.3f)
        )

        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            Text(
                text = stopTime.headsign ?: "Inconnu",
                style = LuxTheme.type.body,
                fontWeight = FontWeight.Medium,
                color = colors.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        ArrivalMinuteView(incomingStop = stopTime, shouldAutoRefresh = true)
    }
}

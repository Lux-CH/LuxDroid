package ch.cclerc.luxapp.ui.stop.expanded

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ch.cclerc.luxapp.domain.GroupedStopTime
import ch.cclerc.luxapp.domain.TripOption
import ch.cclerc.luxapp.ui.components.LinePill
import ch.cclerc.luxapp.ui.theme.LuxTheme
import ch.cclerc.luxcom.model.TransportationMode

fun getTrackType(track: String): String =
    if (track.toIntOrNull() != null) "Voie $track" else "Quai $track"

@Composable
fun ExpandedGroupView(
    group: GroupedStopTime,
    animateIn: Boolean,
    entranceTracker: EntranceTracker,
    onOpenTrip: (String, List<TripOption>) -> Unit,
    onSelectLine: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LuxTheme.colors
    val playEntrance = rememberEntrancePlayback(entranceTracker, "group|${group.id}")
    val first = group.stopTimes.firstOrNull()

    val displayTrack = group.stopTimes
        .firstOrNull { it.place.track != null || it.place.scheduledTrack != null }
        ?.place?.track
        ?: first?.place?.scheduledTrack

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LinePill(
                line = group.routeShortName,
                agencyId = first?.agencyId,
                mode = first?.mode ?: TransportationMode.BUS
            )

            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                Text(
                    text = group.headsign,
                    style = LuxTheme.type.headline,
                    color = colors.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (displayTrack != null) {
                Text(
                    text = getTrackType(displayTrack),
                    style = LuxTheme.type.caption,
                    color = colors.secondaryLabel.copy(alpha = 0.7f)
                )
            }
        }

        DepartureGrid(
            group = group,
            animateIn = animateIn,
            playEntrance = playEntrance,
            onOpenTrip = onOpenTrip,
            onSelectLine = onSelectLine
        )
    }
}

@Composable
private fun DepartureGrid(
    group: GroupedStopTime,
    animateIn: Boolean,
    playEntrance: Boolean,
    onOpenTrip: (String, List<TripOption>) -> Unit,
    onSelectLine: (String) -> Unit
) {
    val stopTimes = group.stopTimes.take(4)
    val rows = stopTimes.chunked(2)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        rows.forEachIndexed { rowIndex, rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowItems.forEachIndexed { columnIndex, stopTime ->
                    DepartureTimeRow(
                        stopTime = stopTime,
                        group = group,
                        index = rowIndex * 2 + columnIndex,
                        animateIn = animateIn,
                        playEntrance = playEntrance,
                        onOpenTrip = onOpenTrip,
                        onSelectLine = onSelectLine,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowItems.size < 2) {
                    Box(Modifier.weight(1f))
                }
            }
        }
    }
}

package ch.cclerc.luxapp.ui.home

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import ch.cclerc.luxapp.domain.GroupedStopTime
import ch.cclerc.luxapp.domain.TripOption
import ch.cclerc.luxapp.ui.anim.scaleClickable
import ch.cclerc.luxapp.ui.components.LinePill
import ch.cclerc.luxapp.ui.theme.InterFontFamily
import ch.cclerc.luxapp.ui.theme.LuxTheme
import ch.cclerc.luxcom.geo.StopGrouping
import ch.cclerc.luxcom.model.TransportationMode
import java.time.Instant

val TransportationMode.displayName: String
    get() = when (this) {
        TransportationMode.WALK -> "À pied"
        TransportationMode.BIKE -> "Vélo"
        TransportationMode.RENTAL -> "Location"
        TransportationMode.CAR -> "Voiture"
        TransportationMode.CAR_PARKING -> "Parking"
        TransportationMode.ODM -> "ODM"
        TransportationMode.TRANSIT -> "Transport en commun"
        TransportationMode.TRAM -> "Tram"
        TransportationMode.SUBWAY -> "Métro"
        TransportationMode.FERRY -> "Mouette"
        TransportationMode.AIRPLANE -> "Avion"
        TransportationMode.METRO -> "Métro"
        TransportationMode.BUS -> "Bus"
        TransportationMode.COACH -> "Autocar"
        TransportationMode.RAIL -> "Train"
        TransportationMode.HIGHSPEED_RAIL -> "Train"
        TransportationMode.LONG_DISTANCE -> "Train"
        TransportationMode.NIGHT_RAIL -> "Train"
        TransportationMode.REGIONAL_FAST_RAIL -> "Train"
        TransportationMode.REGIONAL_RAIL -> "Train"
        TransportationMode.SUBURBAN -> "Train"
        TransportationMode.FUNICULAR -> "Funiculaire"
        TransportationMode.OTHER -> "Autre"
    }

private val wordRegex = Regex("[\\p{L}\\p{N}’']+")

val String.shortnameCapitalize: String
    get() = if (uppercase() == this) {
        this
    } else {
        wordRegex.replace(this) { match ->
            val word = match.value
            word.substring(0, 1).uppercase() + word.substring(1).lowercase()
        }
    }

internal data class ParsedStopName(val city: String, val location: String)

internal fun parseStopName(headsign: String): ParsedStopName {
    val components = headsign.split(",")
    val city = components.firstOrNull()?.trim() ?: headsign
    val location = if (components.size > 1) components[1].trim() else ""
    return ParsedStopName(city, location)
}

@Composable
internal fun TrackBadge(track: String, modifier: Modifier = Modifier) {
    val colors = LuxTheme.colors
    val badgeColor = colors.secondaryLabel.copy(alpha = colors.secondaryLabel.alpha * 0.8f)
    Box(
        modifier = modifier
            .size(11.dp)
            .border(0.5.dp, badgeColor, RoundedCornerShape(3.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = track,
            fontFamily = InterFontFamily,
            fontSize = 8.sp,
            lineHeight = 8.sp,
            color = badgeColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
            style = LocalTextStyle.current.copy(
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.Both
                )
            )
        )
    }
}

@Composable
fun IncomingBusView(
    group: GroupedStopTime,
    onOpenTrip: (String, List<TripOption>) -> Unit,
    onSelectLine: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LuxTheme.colors
    val parsed = parseStopName(group.headsign)
    val shouldUseNormalDisplay = parsed.location.isEmpty()
    val isCityReleavant = StopGrouping.isGenericLocationTerm(parsed.location)

    val displayTrack: String? = group.stopTimes
        .firstOrNull { it.place.track != null || it.place.scheduledTrack != null }
        ?.place?.track
        ?: group.stopTimes.firstOrNull()?.place?.scheduledTrack

    val first = group.stopTimes.firstOrNull()

    Row(
        modifier = modifier.scaleClickable {
            val tripId = first?.tripId ?: return@scaleClickable
            val otherTripOptions = group.stopTimes.take(10).map { stopTime ->
                TripOption(
                    id = stopTime.tripId,
                    startTime = stopTime.place.departure
                        ?: stopTime.place.scheduledDeparture
                        ?: stopTime.place.arrival
                        ?: stopTime.place.scheduledArrival
                        ?: Instant.now()
                )
            }
            onSelectLine(group.routeShortName)
            onOpenTrip(tripId, otherTripOptions)
        },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(horizontalAlignment = Alignment.Start) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 3.dp)
            ) {
                LinePill(
                    line = group.routeShortName,
                    agencyId = first?.agencyId,
                    mode = first?.mode ?: TransportationMode.BUS,
                    width = 45.dp,
                    height = 30.dp,
                    fontSize = 16.5.sp
                )
                Column(
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    if (shouldUseNormalDisplay) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = group.headsign,
                                fontFamily = InterFontFamily,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.label
                            )
                            if (!displayTrack.isNullOrEmpty()) {
                                TrackBadge(displayTrack)
                            }
                        }
                    } else {
                        Text(
                            text = parsed.city,
                            fontFamily = InterFontFamily,
                            fontSize = 11.sp,
                            fontWeight = if (isCityReleavant) FontWeight.SemiBold else FontWeight.Medium,
                            color = colors.secondaryLabel
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = parsed.location.shortnameCapitalize,
                                fontFamily = InterFontFamily,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.label
                            )
                            if (!displayTrack.isNullOrEmpty()) {
                                TrackBadge(displayTrack, Modifier.padding(top = 2.25.dp))
                            }
                        }
                    }
                }
            }
        }

        Box(Modifier.weight(1f))

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (first != null) {
                ArrivalMinuteView(
                    incomingStop = first,
                    shouldAutoRefresh = true,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (group.stopTimes.size > 1) {
                ArrivalMinuteView(
                    incomingStop = group.stopTimes[1],
                    shouldAutoRefresh = true,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier.alpha(0.7f)
                )
            }
        }
    }
}

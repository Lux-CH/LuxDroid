package ch.cclerc.luxapp.ui.itinerary

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import ch.cclerc.luxapp.core.SFSymbol
import ch.cclerc.luxapp.ui.anim.PlainIndication
import ch.cclerc.luxapp.ui.stops.formatDistance
import ch.cclerc.luxapp.ui.stop.expanded.getTrackType
import ch.cclerc.luxapp.ui.theme.LuxTheme
import ch.cclerc.luxapp.ui.theme.legColor
import ch.cclerc.luxapp.ui.trips.TripResultView
import ch.cclerc.luxapp.viewmodel.ItineraryViewModel
import ch.cclerc.luxcom.model.Place
import ch.cclerc.luxcom.model.TransportationMode
import ch.cclerc.luxcom.model.trip.Itinerary
import ch.cclerc.luxcom.model.trip.Leg
import java.time.Instant

private const val SIGNPOST_INLINE_ID = "signpost"
private const val TIGHT_CONNECTION_BUFFER_SECONDS = 80.0

internal fun calculateUpcomingStopsForMultiLeg(leg: Leg): List<Place> {
    val intermediateStops = leg.intermediateStops ?: return emptyList()
    val cutoff = Instant.now().minusSeconds(25)
    val allStops = buildList {
        add(leg.from)
        addAll(intermediateStops)
        add(leg.to)
    }
    val upcoming = allStops.filter { stop ->
        val relevantTime = stop.departure ?: stop.arrival ?: return@filter true
        relevantTime >= cutoff
    }
    return upcoming.ifEmpty { allStops }
}

@Composable
private fun signpostInlineContent(size: TextUnit, color: Color): Map<String, InlineTextContent> =
    mapOf(
        SIGNPOST_INLINE_ID to InlineTextContent(
            Placeholder(1.2.em, 1.2.em, PlaceholderVerticalAlign.TextCenter)
        ) {
            SFSymbol(name = "signpost.right", size = size, color = color)
        }
    )

private fun signpostText(prefix: String, name: String): AnnotatedString = buildAnnotatedString {
    append(prefix)
    appendInlineContent(SIGNPOST_INLINE_ID, "→")
    append(" ")
    append(name)
}

@Composable
fun MultipleItineraryDetailView(
    itinerary: Itinerary,
    viewModel: ItineraryViewModel,
    modifier: Modifier = Modifier,
    onOpenSubLeg: (String) -> Unit = {}
) {
    val colors = LuxTheme.colors
    var tightConnectionAlert by remember { mutableStateOf<Pair<String, String>?>(null) }

    fun tightConnectionLegs(walkingLeg: Leg, legIndex: Int): Pair<Leg, Leg>? {
        if (walkingLeg.mode != TransportationMode.WALK) return null
        if (legIndex <= 0 || legIndex >= itinerary.legs.size - 1) return null

        val previousLeg = itinerary.legs[legIndex - 1]
        val nextLeg = itinerary.legs[legIndex + 1]

        if (previousLeg.mode == TransportationMode.WALK || nextLeg.mode == TransportationMode.WALK) return null

        val previousArrival = previousLeg.to.arrival ?: previousLeg.to.scheduledArrival ?: previousLeg.endTime
        val nextDeparture = nextLeg.from.departure ?: previousLeg.from.scheduledDeparture ?: nextLeg.startTime

        val totalConnectionTime = (nextDeparture.toEpochMilli() - previousArrival.toEpochMilli()) / 1000.0
        val bufferTime = totalConnectionTime - walkingLeg.duration.toDouble()

        return if (bufferTime < TIGHT_CONNECTION_BUFFER_SECONDS) previousLeg to nextLeg else null
    }

    Column(modifier = modifier.fillMaxSize()) {
        TripResultView(
            itinerary = itinerary,
            destinationName = viewModel.destinationName,
            modifier = Modifier.padding(top = 30.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = 7.5.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            itinerary.legs.forEachIndexed { legIndex, leg ->
                key(leg.legGeometry.points, legIndex) {
                    if (leg.mode != TransportationMode.WALK) {
                        val color = legColor(leg, brightIt = true)

                        LegHeaderView(
                            leg = leg,
                            legColor = color,
                            isSingle = false,
                            nextStop = null,
                            modifier = Modifier
                                .padding(horizontal = 20.dp)
                                .padding(top = 25.dp, bottom = 15.dp),
                            onOpenSubLeg = onOpenSubLeg
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 20.dp),
                            thickness = 0.5.dp,
                            color = colors.separator
                        )

                        val legDisruptions = rememberDisruptions(leg)
                        if (legDisruptions.isNotEmpty()) {
                            DisruptionSectionView(
                                disruptions = legDisruptions,
                                modifier = Modifier
                                    .padding(horizontal = 20.dp)
                                    .padding(top = 14.dp)
                            )
                        }

                        ItinerarySheetDetailStopsContentView(
                            stops = calculateUpcomingStopsForMultiLeg(leg),
                            legColor = color,
                            fromStop = leg.from,
                            toStop = leg.to,
                            duration = leg.duration,
                            isMultipleLeg = true,
                            isRealTime = leg.realTime,
                            isCancelled = leg.cancelled,
                            onSelectStop = { viewModel.selectedStop = it },
                            modifier = Modifier
                                .padding(horizontal = 20.dp)
                                .padding(top = 16.dp, bottom = 10.dp)
                        )
                    } else {
                        val tightLegs = tightConnectionLegs(leg, legIndex)

                        WalkingLegSection(
                            leg = leg,
                            legIndex = legIndex,
                            legCount = itinerary.legs.size,
                            destinationName = viewModel.destinationName,
                            isTightConnection = tightLegs != null,
                            onTightConnectionTap = {
                                tightLegs?.let { (fromLeg, toLeg) ->
                                    tightConnectionAlert =
                                        (fromLeg.routeShortName ?: fromLeg.headsign ?: "le transport précédent") to
                                            (toLeg.routeShortName ?: toLeg.headsign ?: "le transport suivant")
                                }
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            ShareButtonView(
                itinerary = itinerary,
                compact = false,
                modifier = Modifier.padding(bottom = 20.dp)
            )
        }
    }

    val alert = tightConnectionAlert
    if (alert != null) {
        AlertDialog(
            onDismissRequest = { tightConnectionAlert = null },
            confirmButton = {
                TextButton(onClick = { tightConnectionAlert = null }) { Text("OK") }
            },
            title = { Text("Correspondance risquée") },
            text = {
                Text(
                    "Attention : le temps entre le ${alert.first} et le ${alert.second} est court. " +
                        "Vous risqueriez de rater votre correspondance.\n" +
                        "Pour éviter cela, augmentez le temps d'attente minimum dans les options d'itinéraire (page précédente)."
                )
            }
        )
    }
}

@Composable
private fun WalkingLegSection(
    leg: Leg,
    legIndex: Int,
    legCount: Int,
    destinationName: String?,
    isTightConnection: Boolean,
    onTightConnectionTap: () -> Unit
) {
    val colors = LuxTheme.colors
    val blue = colors.systemBlue

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .background(blue.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                SFSymbol(
                    name = if (leg.from.name != leg.to.name) "figure.walk" else "arrow.left.arrow.right",
                    size = 16.sp,
                    color = blue,
                    weight = 500
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    WalkingDescriptionText(
                        leg = leg,
                        legIndex = legIndex,
                        legCount = legCount,
                        destinationName = destinationName
                    )

                    if (isTightConnection) {
                        Box(
                            Modifier.clickable(
                                interactionSource = null,
                                indication = PlainIndication,
                                onClick = onTightConnectionTap
                            )
                        ) {
                            SFSymbol(
                                name = "exclamationmark.triangle.fill",
                                size = 14.sp,
                                color = colors.systemRed
                            )
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatDistance(leg.distance ?: 0.0),
                        style = LuxTheme.type.caption,
                        color = colors.secondaryLabel
                    )
                    Text(text = "•", style = LuxTheme.type.caption, color = colors.systemGray)
                    Text(
                        text = "${leg.duration / 60} min",
                        style = LuxTheme.type.caption,
                        color = if (isTightConnection) colors.systemRed else colors.secondaryLabel
                    )

                    val track = leg.to.track
                    if (track != null && leg.from.name != leg.to.name) {
                        Text(text = "•", style = LuxTheme.type.caption, color = colors.systemGray)
                        Text(
                            text = getTrackType(track),
                            style = LuxTheme.type.caption,
                            color = colors.secondaryLabel
                        )
                    } else if (isTightConnection) {
                        Text(text = "-", style = LuxTheme.type.caption, color = colors.systemRed)
                        Text(text = "Risqué", style = LuxTheme.type.caption, color = colors.systemRed)
                    }
                }
            }

        }

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 20.dp),
            thickness = 0.5.dp,
            color = colors.separator
        )
    }
}

@Composable
private fun WalkingDescriptionText(
    leg: Leg,
    legIndex: Int,
    legCount: Int,
    destinationName: String?
) {
    val colors = LuxTheme.colors
    val style = LuxTheme.type.subheadline.copy(fontWeight = FontWeight.Medium)
    val inline = signpostInlineContent(15.sp, colors.label)

    val fromName = leg.from.name
    val toName = leg.to.name

    val text: AnnotatedString = when {
        legIndex == 0 -> signpostText("Marchez jusqu'à ", toName)
        legIndex == legCount - 1 ->
            if (destinationName != null) {
                signpostText("Marchez jusqu'à ", destinationName)
            } else {
                AnnotatedString("Marchez vers votre destination")
            }
        else -> {
            val fromTrack = leg.from.track
            val toTrack = leg.to.track
            if (fromName == toName) {
                if (fromTrack != null && toTrack != null && fromTrack != toTrack) {
                    val fromLabel = if (fromTrack.toIntOrNull() != null) "de la voie" else "du quai"
                    val toLabel = if (toTrack.toIntOrNull() != null) "à la voie" else "au quai"
                    AnnotatedString("Passez $fromLabel $fromTrack $toLabel $toTrack")
                } else {
                    AnnotatedString("Correspondance à $fromName")
                }
            } else {
                AnnotatedString("Marchez de $fromName à $toName")
            }
        }
    }

    Text(
        text = text,
        inlineContent = inline,
        style = style,
        color = colors.label,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}


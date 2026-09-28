package ch.cclerc.luxapp.ui.onboard

import ch.cclerc.luxapp.ui.theme.legColor
import ch.cclerc.luxapp.ui.itinerary.LocalTimelineLineColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.cclerc.luxapp.core.SFSymbol
import ch.cclerc.luxapp.domain.onboard.ConnectionRisk
import ch.cclerc.luxapp.domain.onboard.CrowdState
import ch.cclerc.luxapp.domain.onboard.OnboardAlert
import ch.cclerc.luxapp.domain.onboard.OnboardPhase
import ch.cclerc.luxapp.domain.onboard.OnboardSession
import ch.cclerc.luxapp.domain.onboard.allStops
import ch.cclerc.luxapp.domain.onboard.approachingVehicleDistance
import ch.cclerc.luxapp.domain.onboard.arrivalDelayMinutes
import ch.cclerc.luxapp.domain.onboard.canReportRide
import ch.cclerc.luxapp.domain.onboard.capitalizedFirstLetter
import ch.cclerc.luxapp.domain.onboard.currentLegArrival
import ch.cclerc.luxapp.domain.onboard.currentLegDelayMinutes
import ch.cclerc.luxapp.domain.onboard.disruptionGroups
import ch.cclerc.luxapp.domain.onboard.endsWithButton
import ch.cclerc.luxapp.domain.onboard.isTransit
import ch.cclerc.luxapp.domain.onboard.reportRide
import ch.cclerc.luxapp.domain.onboard.setSharing
import ch.cclerc.luxapp.domain.onboard.spokenLineName
import ch.cclerc.luxapp.domain.station.StationWalk
import ch.cclerc.luxapp.ui.anim.NumericText
import ch.cclerc.luxapp.ui.anim.PlainIndication
import ch.cclerc.luxapp.ui.anim.pulse
import ch.cclerc.luxapp.ui.anim.scaleClickable
import ch.cclerc.luxapp.ui.components.LinePill
import ch.cclerc.luxapp.ui.components.linePillAppearance
import ch.cclerc.luxapp.ui.itinerary.DisruptionsRow
import ch.cclerc.luxapp.ui.itinerary.ItinerarySheetDetailStopsContentView
import ch.cclerc.luxapp.ui.itinerary.formatTime
import ch.cclerc.luxapp.ui.stop.expanded.getTrackType
import ch.cclerc.luxapp.ui.stops.formatDistance
import ch.cclerc.luxapp.ui.theme.LuxMaterials
import ch.cclerc.luxapp.ui.theme.LuxTheme
import ch.cclerc.luxapp.ui.theme.LuxTypography
import ch.cclerc.luxapp.ui.theme.TpgFontFamily
import ch.cclerc.luxapp.ui.theme.getLegColor
import ch.cclerc.luxapp.ui.theme.iosShadow
import ch.cclerc.luxapp.ui.theme.lightenLegColor
import ch.cclerc.luxapp.ui.theme.isLegColorDark
import ch.cclerc.luxcom.model.trip.Leg
import java.time.Instant
import kotlin.math.ceil
import kotlin.math.max
import kotlinx.coroutines.delay

internal val WalkBlue = Color(red = 0.1f, green = 0.42f, blue = 0.85f)
private val ArrivedGreen = Color(red = 0.13f, green = 0.6f, blue = 0.33f)
private val OffRouteOrange = Color(red = 0.85f, green = 0.45f, blue = 0.05f)
private val UrgentRed = Color(red = 0.86f, green = 0.18f, blue = 0.2f)

internal fun gradientOf(color: Color): Brush =
    Brush.verticalGradient(listOf(lightenLegColor(color, 0.08f).copy(alpha = color.alpha), color))

@Composable
internal fun rememberNow(periodMs: Long = 1_000): Instant {
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(periodMs) {
        while (true) {
            now = Instant.now()
            delay(periodMs)
        }
    }
    return now
}

internal fun bright(leg: Leg, accent: Color): Color {
    val color = getLegColor(leg, false, accent)
    return if (isLegColorDark(color)) lightenLegColor(color) else color
}

@Composable
fun OnboardInstructionBanner(session: OnboardSession, modifier: Modifier = Modifier) {
    val accent = LuxTheme.accent
    val leg = session.currentLeg
    val urgent = session.phase == OnboardPhase.RIDING && session.stopsRemaining <= 1
    val appearance = leg?.let { linePillAppearance(it.routeShortName ?: "", it.agencyId, it.mode, accent) }

    val tint = when (session.phase) {
        OnboardPhase.ARRIVED -> ArrivedGreen
        OnboardPhase.WALKING -> if (session.isOffRoute) OffRouteOrange else WalkBlue
        OnboardPhase.WAITING, OnboardPhase.RIDING -> when {
            appearance == null -> accent
            urgent -> UrgentRed
            else -> appearance.lineColor
        }
    }
    val foreground = when (session.phase) {
        OnboardPhase.WALKING, OnboardPhase.ARRIVED -> Color.White
        OnboardPhase.WAITING, OnboardPhase.RIDING ->
            if (appearance == null || urgent) Color.White else appearance.textColorOnLineColor
    }
    val shape = RoundedCornerShape(26.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .iosShadow(Color.Black.copy(alpha = 0.25f), 12.dp, 5.dp, shape)
            .clip(shape)
            .background(gradientOf(tint), shape)
            .border(0.5.dp, Color.White.copy(alpha = 0.15f), shape)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (session.phase) {
                OnboardPhase.WALKING -> WalkingContent(session, foreground)
                OnboardPhase.WAITING -> if (leg != null) WaitingContent(session, leg, foreground)
                OnboardPhase.RIDING -> if (leg != null) RidingContent(session, leg, foreground)
                OnboardPhase.ARRIVED -> ArrivedContent(session, foreground)
            }
        }
        BannerFooter(session, foreground)
    }
}

@Composable
private fun WalkingContent(session: OnboardSession, foreground: Color) {
    val maneuver = session.nextManeuver
    Box(Modifier.width(54.dp), contentAlignment = Alignment.Center) {
        SFSymbol(
            name = if (session.isOffRoute) {
                "arrow.triangle.turn.up.right.diamond.fill"
            } else {
                maneuver?.symbolName ?: "mappin.and.ellipse"
            },
            size = 38.sp,
            color = foreground,
            weight = 700
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val distance = session.distanceToManeuver
        if (distance != null) {
            NumericText(
                text = formatDistance(distance),
                style = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold),
                color = foreground
            )
        }
        Text(
            text = walkingInstruction(session),
            fontSize = 19.sp,
            fontWeight = FontWeight.SemiBold,
            color = foreground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun walkingInstruction(session: OnboardSession): String {
    if (session.isOffRoute) return "Recalcul de l'itinéraire…"
    session.nextManeuver?.let { return it.instruction }
    val walk = session.stationWalk
    val track = walk?.toTrack
    if (session.isInStation && walk != null && walk.kind != StationWalk.Kind.LEAVING && track != null) {
        return "Rejoignez ${StationWalk.trackPhrase(track)}"
    }
    val leg = session.currentLeg ?: return ""
    val isLast = session.legIndex == session.legs.size - 1
    return "Marchez jusqu'à ${session.placeName(leg.to, isDestination = isLast)}"
}

@Composable
private fun LineLabel(leg: Leg, foreground: Color) {
    val appearance = linePillAppearance(leg.routeShortName ?: "", leg.agencyId, leg.mode, LuxTheme.accent)
    Text(
        text = appearance.formattedLine,
        style = TextStyle(fontFamily = TpgFontFamily, fontSize = 30.sp, color = foreground),
        maxLines = 1,
        modifier = Modifier.defaultMinSize(minWidth = 44.dp)
    )
}

private fun trainArrival(leg: Leg): Instant? {
    val arrival = leg.from.arrival ?: return null
    return if (leg.startTime.epochSecond - arrival.epochSecond >= 60) arrival else null
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.WaitingContent(session: OnboardSession, leg: Leg, foreground: Color) {
    LineLabel(leg, foreground)
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = leg.headsign?.let { "Direction $it" } ?: leg.spokenLineName.capitalizedFirstLetter,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            color = foreground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = session.placeName(leg.from),
            style = LuxTheme.type.subheadline.copy(fontWeight = FontWeight.Medium),
            color = foreground.copy(alpha = 0.85f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
    val now = rememberNow()
    val arrival = trainArrival(leg)
    if (arrival != null && arrival.isAfter(now)) {
        Countdown(arrival, "arrivée du train", foreground)
    } else {
        Countdown(leg.startTime, "départ", foreground)
    }
}

@Composable
private fun Countdown(date: Instant, caption: String, foreground: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CountdownText(
            target = date,
            showsSeconds = true,
            style = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold),
            color = foreground
        )
        Text(
            text = caption,
            style = LuxTheme.type.caption.copy(fontWeight = FontWeight.SemiBold),
            color = foreground.copy(alpha = 0.85f)
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.RidingContent(session: OnboardSession, leg: Leg, foreground: Color) {
    LineLabel(leg, foreground)
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = if (session.stopsRemaining <= 1) "Descendez au prochain arrêt" else "Descendez à",
            style = LuxTheme.type.subheadline.copy(fontWeight = FontWeight.SemiBold),
            color = foreground.copy(alpha = 0.85f)
        )
        Text(
            text = session.placeName(leg.to),
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
            color = foreground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
    val pulse by animateFloatAsState(
        targetValue = if (session.stopsRemaining <= 1) 0.55f else 1f,
        animationSpec = tween(800),
        label = "stopsPulse"
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = if (session.stopsRemaining <= 1) Modifier.alpha(pulse) else Modifier
    ) {
        NumericText(
            text = "${session.stopsRemaining}",
            style = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold),
            color = foreground
        )
        Text(
            text = if (session.stopsRemaining > 1) "arrêts" else "arrêt",
            style = LuxTheme.type.caption.copy(fontWeight = FontWeight.SemiBold),
            color = foreground.copy(alpha = 0.85f)
        )
    }
}

@Composable
private fun ArrivedContent(session: OnboardSession, foreground: Color) {
    Box(Modifier.width(54.dp), contentAlignment = Alignment.Center) {
        SFSymbol(name = "checkmark.circle.fill", size = 38.sp, color = foreground, weight = 700)
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("Vous êtes arrivé", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = foreground)
        Text(
            text = session.destinationName.capitalizedFirstLetter,
            style = LuxTheme.type.subheadline.copy(fontWeight = FontWeight.Medium),
            color = foreground.copy(alpha = 0.85f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun stationTracksText(walk: StationWalk): String? = when (walk.kind) {
    StationWalk.Kind.TRANSFER -> {
        val to = walk.toTrack
        val from = walk.fromTrack
        when {
            to == null -> null
            from == null || from == to -> getTrackType(to)
            else -> "${getTrackType(from)} → ${getTrackType(to)}"
        }
    }
    StationWalk.Kind.ENTERING -> walk.toTrack?.let { "Départ ${getTrackType(it).lowercase()}" }
    StationWalk.Kind.LEAVING -> walk.fromTrack?.let { "Arrivée ${getTrackType(it).lowercase()}" }
}

@Composable
private fun BannerFooter(session: OnboardSession, foreground: Color) {
    val style = LuxTheme.type.subheadline.copy(fontWeight = FontWeight.SemiBold)
    val walk = session.stationWalk
    val tracks = if (session.isInStation && walk != null) stationTracksText(walk) else null

    @Composable
    fun Footer(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.18f))
                .padding(horizontal = 18.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }

    @Composable
    fun Symbol(name: String) = SFSymbol(name = name, size = 15.sp, color = foreground, weight = 600)

    @Composable
    fun Label(text: String, modifier: Modifier = Modifier) =
        Text(text = text, style = style, color = foreground, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)

    when {
        tracks != null -> Footer {
            Symbol("train.side.front.car")
            Label(tracks)
            session.followingManeuver?.let {
                Label("· Puis")
                Symbol(it.symbolName)
            }
        }
        session.followsTimetable && session.phase == OnboardPhase.RIDING -> Footer {
            Symbol("dot.radiowaves.up.forward")
            Label("Position d'après l'horaire en temps réel")
        }
        session.hasWeakGPS && session.phase != OnboardPhase.ARRIVED && !session.followsTimetable -> Footer {
            Symbol("location.slash.fill")
            Label("Signal GPS faible · position estimée")
        }
        session.phase == OnboardPhase.WALKING -> session.followingManeuver?.let { then ->
            Footer {
                Label("Puis")
                Symbol(then.symbolName)
            }
        }
        session.phase == OnboardPhase.WAITING -> session.currentLeg?.let { leg ->
            val now = rememberNow()
            val parts = mutableListOf(formatTime(leg.startTime))
            leg.from.track?.takeIf { it.isNotEmpty() }?.let { parts.add(getTrackType(it)) }
            val arrival = trainArrival(leg)
            if (arrival != null && !arrival.isAfter(now) && leg.startTime.isAfter(now)) parts.add("train en gare")
            session.approachingVehicleDistance?.let { parts.add("en direct à ${formatDistance(it)}") }
            Footer {
                Symbol(if (session.approachingVehicle != null) "dot.radiowaves.up.forward" else "clock")
                Label(parts.joinToString(" · "), Modifier.weight(1f, fill = false))
                if (leg.cancelled) DelayBadge("Supprimé", LuxTheme.colors.systemRed)
            }
        }
        session.phase == OnboardPhase.RIDING -> session.currentLeg?.let { leg ->
            val stops = leg.allStops
            val delay = session.currentLegDelayMinutes
            val nextName = if (session.stopsRemaining > 1 && session.nextStopIndex < stops.size) {
                stops[session.nextStopIndex].name
            } else {
                null
            }
            Footer {
                if (nextName != null) {
                    Symbol("arrow.up.to.line")
                    Label("Prochain : $nextName", Modifier.weight(1f))
                } else {
                    Symbol("figure.walk.departure")
                    Label("Arrivée à ${formatTime(session.currentLegArrival)}", Modifier.weight(1f))
                }
                if (delay != 0) {
                    DelayBadge(
                        if (delay > 0) "+$delay min" else "$delay min",
                        if (delay > 0) LuxTheme.colors.systemOrange else LuxTheme.colors.systemCyan
                    )
                }
            }
        }
        else -> Unit
    }
}

@Composable
fun DelayBadge(text: String, color: Color, onTint: Boolean = true) {
    Text(
        text = text,
        style = LuxTheme.type.caption.copy(fontWeight = FontWeight.Bold),
        color = if (onTint) color else Color.White,
        modifier = Modifier
            .background(if (onTint) Color.White else color, CircleShape)
            .padding(horizontal = 7.dp, vertical = 2.dp)
    )
}

fun shortCountdown(date: Instant, now: Instant): String {
    val seconds = (date.toEpochMilli() - now.toEpochMilli()) / 1000.0
    if (seconds < 30) return "0'"
    val minutes = ceil(seconds / 60).toInt()
    if (minutes < 60) return "$minutes'"
    return "${minutes / 60}h${String.format(java.util.Locale.ROOT, "%02d", minutes % 60)}"
}

@Composable
fun CountdownText(
    target: Instant,
    style: TextStyle,
    color: Color,
    showsSeconds: Boolean = false,
    modifier: Modifier = Modifier
) {
    val now = rememberNow()
    val seconds = ((target.toEpochMilli() - now.toEpochMilli()) / 1000).toInt()
    val text = if (showsSeconds && seconds in 1..59) {
        String.format(java.util.Locale.ROOT, "0:%02d", seconds)
    } else {
        shortCountdown(target, now)
    }
    NumericText(
        text = text,
        style = if (text.contains(":")) LuxTypography.timeVariant(style) else style,
        color = color,
        modifier = modifier
    )
}

@Composable
fun OnboardAlertToast(alert: OnboardAlert, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LuxTheme.colors
    val isCritical = alert.severity == OnboardAlert.Severity.CRITICAL
    val shape = RoundedCornerShape(20.dp)
    val content = if (isCritical) Color.White else colors.label

    Row(
        modifier = modifier
            .fillMaxWidth()
            .iosShadow(Color.Black.copy(alpha = 0.18f), 10.dp, 4.dp, shape)
            .clip(shape)
            .then(
                if (isCritical) {
                    Modifier.background(gradientOf(colors.systemRed), shape)
                } else {
                    Modifier
                        .background(LuxMaterials.regular(), shape)
                        .border(1.dp, alert.severity.color(colors).copy(alpha = 0.35f), shape)
                }
            )
            .clickable(interactionSource = null, indication = PlainIndication, onClick = onDismiss)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(30.dp), contentAlignment = Alignment.Center) {
            SFSymbol(
                name = alert.symbolName,
                size = 20.sp,
                color = if (isCritical) Color.White else alert.severity.color(colors),
                weight = 700
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(alert.title, style = LuxTheme.type.subheadline.copy(fontWeight = FontWeight.Bold), color = content)
            alert.message?.let {
                Text(
                    it,
                    style = LuxTheme.type.footnote,
                    color = content.copy(alpha = 0.85f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Box(
            Modifier
                .size(28.dp)
                .clickable(interactionSource = null, indication = PlainIndication, onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            SFSymbol(
                name = "xmark",
                size = 12.sp,
                color = if (isCritical) Color.White.copy(alpha = 0.8f) else colors.secondaryLabel,
                weight = 700
            )
        }
    }
}

@Composable
fun OnboardBottomPanel(
    session: OnboardSession,
    isExpanded: Boolean,
    onEnd: () -> Unit,
    onOpenDetail: () -> Unit,
    onSelectStop: (ch.cclerc.luxcom.model.Place) -> Unit,
    onCompactHeightChange: (Dp) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LuxTheme.colors
    val density = LocalDensity.current
    val summaryOnly = session.phase == OnboardPhase.ARRIVED || (session.phase == OnboardPhase.WALKING && session.nextTransitLeg == null && session.legDisruptions.isEmpty())
    val expandedAlpha by animateFloatAsState(if (isExpanded) 1f else 0f, tween(250), label = "expandedAlpha")

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { onCompactHeightChange(with(density) { it.height.toDp() }) }
                .padding(top = 24.dp, bottom = if (session.endsWithButton) 2.dp else if (summaryOnly) 24.dp else 6.dp)
        ) {
            SummaryRow(session, onEnd, Modifier.padding(horizontal = 22.dp))
            ContextRow(session, Modifier.padding(horizontal = 22.dp).padding(top = 12.dp))
            AnimatedVisibility(
                visible = session.legDisruptions.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                DisruptionsRow(
                    groups = session.disruptionGroups,
                    action = onOpenDetail,
                    modifier = Modifier.padding(horizontal = 22.dp).padding(top = 10.dp)
                )
            }
        }

        if (session.phase != OnboardPhase.ARRIVED) {
            Column(Modifier.alpha(expandedAlpha)) {
                HorizontalDivider(Modifier.padding(horizontal = 22.dp), thickness = 0.5.dp, color = colors.separator)
                if (isExpanded) {
                    ExpandedContent(session, onSelectStop, Modifier.padding(bottom = 24.dp))
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(session: OnboardSession, onEnd: () -> Unit, modifier: Modifier) {
    val colors = LuxTheme.colors
    val now = rememberNow(10_000)
    val lastTransit = session.legs.lastOrNull { it.isTransit }
    val arrivalColor = when {
        lastTransit == null || !lastTransit.realTime -> colors.label
        lastTransit.arrivalDelayMinutes >= 2 -> colors.systemOrange
        else -> colors.systemGreen
    }
    val minutes = max(0, ceil((session.arrivalDate.toEpochMilli() - now.toEpochMilli()) / 60_000.0).toInt())
    val time = if (minutes >= 60) "${minutes / 60} h ${String.format(java.util.Locale.ROOT, "%02d", minutes % 60)}" else "$minutes min"
    val remaining = if (session.phase == OnboardPhase.ARRIVED) {
        "Trajet terminé"
    } else {
        "$time · ${formatDistance(session.remainingDistance)}"
    }

    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                NumericText(
                    text = formatTime(session.arrivalDate),
                    style = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold),
                    color = arrivalColor
                )
                Text(
                    "arrivée",
                    style = LuxTheme.type.subheadline.copy(fontWeight = FontWeight.Medium),
                    color = colors.secondaryLabel,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            Text(remaining, style = LuxTypography.timeVariant(LuxTheme.type.subheadline), color = colors.secondaryLabel)
        }
        val arrived = session.phase == OnboardPhase.ARRIVED
        Text(
            text = if (arrived) "Terminer" else "Quitter",
            style = LuxTheme.type.headline,
            color = Color.White,
            modifier = Modifier
                .clip(CircleShape)
                .background(gradientOf(if (arrived) colors.systemGreen else colors.systemRed), CircleShape)
                .scaleClickable(haptic = false, onClick = onEnd)
                .padding(horizontal = if (arrived) 22.dp else 20.dp, vertical = 12.dp)
        )
    }
}

@Composable
private fun ContextRow(session: OnboardSession, modifier: Modifier) {
    when (session.phase) {
        OnboardPhase.WALKING -> session.nextTransitLeg?.let { (_, next) ->
            Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                NextTransitRow(session, next)
                val formation = session.formation
                if (session.isInStation && formation != null) {
                    FormationSummary(formation, session.formationPlatformSectors)
                }
            }
        }
        OnboardPhase.WAITING -> Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            session.formation?.let {
                FormationSummary(it, session.formationPlatformSectors, Modifier.padding(bottom = 2.dp))
            }
            session.rideInfo?.let { RideCommunityStrip(it) }
            val accent = LuxTheme.accent
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .background(gradientOf(accent), CircleShape)
                    .scaleClickable(haptic = false) { session.confirmBoarded() }
                    .padding(vertical = 11.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SFSymbol(name = "checkmark.circle.fill", size = 15.sp, color = Color.White, weight = 600)
                Text("Je suis à bord", style = LuxTheme.type.subheadline.copy(fontWeight = FontWeight.SemiBold), color = Color.White)
            }
        }
        OnboardPhase.RIDING -> Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RideProgress(session)
            session.rideInfo?.let { RideCommunityStrip(it) }
            CrowdRow(session)
        }
        OnboardPhase.ARRIVED -> Unit
    }
}

@Composable
private fun NextTransitRow(session: OnboardSession, leg: Leg) {
    val colors = LuxTheme.colors
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.label.copy(alpha = 0.05f), shape)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Ensuite", style = LuxTheme.type.footnote.copy(fontWeight = FontWeight.SemiBold), color = colors.secondaryLabel)
        LinePill(leg.routeShortName ?: "", leg.agencyId, leg.mode, width = 38.dp, height = 24.dp, fontSize = 13.sp)
        Column(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val style = LuxTheme.type.subheadline.copy(fontWeight = FontWeight.SemiBold)
                Text("${formatTime(leg.startTime)} ·", style = style, color = colors.label)
                CountdownText(leg.startTime, style, colors.label, showsSeconds = true)
            }
            Text(
                session.placeName(leg.from),
                style = LuxTheme.type.caption,
                color = colors.secondaryLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        when (session.connectionRisk) {
            ConnectionRisk.COMFORTABLE -> Unit
            ConnectionRisk.TIGHT -> RiskLabel("Serré", "hare.fill", colors.systemOrange)
            ConnectionRisk.MISSED -> RiskLabel("Compromis", "exclamationmark.triangle.fill", colors.systemRed)
        }
    }
}

@Composable
private fun RiskLabel(text: String, symbol: String, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        SFSymbol(name = symbol, size = 12.sp, color = color, weight = 700)
        Text(text, style = LuxTheme.type.caption.copy(fontWeight = FontWeight.Bold), color = color)
    }
}

@Composable
private fun RideProgress(session: OnboardSession) {
    val colors = LuxTheme.colors
    val stops = session.currentStops
    val color = session.currentLeg?.let { bright(it, LuxTheme.accent) } ?: LuxTheme.accent
    val progress by animateFloatAsState(session.stopProgress.toFloat(), tween(1000), label = "rideProgress")
    val background = colors.systemBackground
    val track = colors.secondaryLabel.copy(alpha = 0.2f)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(12.dp)
        ) {
            val count = max(stops.size, 2)
            val spacing = size.width / (count - 1)
            val midY = size.height / 2
            val barHeight = 4.dp.toPx()
            drawRoundRect(track, Offset(0f, midY - barHeight / 2), androidx.compose.ui.geometry.Size(size.width, barHeight), androidx.compose.ui.geometry.CornerRadius(barHeight / 2))
            drawRoundRect(color, Offset(0f, midY - barHeight / 2), androidx.compose.ui.geometry.Size(size.width * progress, barHeight), androidx.compose.ui.geometry.CornerRadius(barHeight / 2))
            for (index in stops.indices) {
                val passed = index < session.nextStopIndex
                val isEnd = index == stops.size - 1
                val radius = (if (isEnd) 6.dp else 3.5.dp).toPx()
                val center = Offset((index * spacing).coerceIn(radius, size.width - radius), midY)
                drawCircle(if (passed) color else background, radius, center)
                drawCircle(color, radius, center, style = Stroke((if (isEnd) 3.dp else 1.5.dp).toPx()))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Text(
                stops.firstOrNull()?.let { session.placeName(it) } ?: "",
                style = LuxTheme.type.caption,
                color = colors.secondaryLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                stops.lastOrNull()?.let { session.placeName(it) } ?: "",
                style = LuxTheme.type.caption.copy(fontWeight = FontWeight.SemiBold),
                color = colors.secondaryLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CrowdRow(session: OnboardSession) {
    val colors = LuxTheme.colors
    val accent = LuxTheme.accent
    if (session.followsTimetable) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            SFSymbol(name = "checkmark.seal.fill", size = 12.sp, color = colors.systemGreen)
            Text("Train suivi avec le temps réel officiel", style = LuxTheme.type.caption, color = colors.secondaryLabel)
        }
        return
    }
    val sharing = session.isSharingPosition
    val text = if (!sharing) {
        "Position du véhicule non partagée"
    } else {
        when (val state = session.crowdStatus) {
            is CrowdState.Contributing ->
                if (state.riders > 1) {
                    "Position du véhicule partagée avec ${state.riders - 1} autre(s) voyageur(s)"
                } else {
                    "Vous partagez la position du véhicule"
                }
            CrowdState.Unverified -> "Véhicule non confirmé, rien n'est partagé"
            CrowdState.Learning, null -> "Partage de la position du véhicule…"
        }
    }
    val pulse = if (sharing) Modifier.pulse(1f, 0.4f, 900) else Modifier
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        SFSymbol(
            name = if (sharing) "dot.radiowaves.up.forward" else "antenna.radiowaves.left.and.right.slash",
            size = 12.sp,
            color = if (sharing) accent else colors.secondaryLabel,
            modifier = pulse
        )
        Text(text, style = LuxTheme.type.caption, color = colors.secondaryLabel, maxLines = 2, modifier = Modifier.weight(1f))
        Switch(
            checked = sharing,
            onCheckedChange = { session.setSharing(it) },
            colors = SwitchDefaults.colors(checkedTrackColor = accent, checkedThumbColor = Color.White),
            modifier = Modifier.scale(0.7f).height(24.dp)
        )
    }
}

@Composable
private fun ExpandedContent(
    session: OnboardSession,
    onSelectStop: (ch.cclerc.luxcom.model.Place) -> Unit,
    modifier: Modifier
) {
    val colors = LuxTheme.colors
    val leg = session.currentLeg
    Column(modifier.padding(horizontal = 22.dp).padding(top = 16.dp)) {
        if ((session.phase == OnboardPhase.RIDING || session.phase == OnboardPhase.WAITING) && leg != null) {
            CompositionLocalProvider(LocalTimelineLineColor provides legColor(leg)) {
                ItinerarySheetDetailStopsContentView(
                    stops = session.upcomingStops,
                    legColor = bright(leg, LuxTheme.accent),
                    fromStop = leg.from,
                    toStop = leg.to,
                    duration = leg.duration,
                    isMultipleLeg = false,
                    isRealTime = leg.realTime,
                    isCancelled = leg.cancelled,
                    onSelectStop = onSelectStop
                )
            }
            if (session.canReportRide) {
                HorizontalDivider(Modifier.padding(vertical = 16.dp), thickness = 0.5.dp, color = colors.separator)
                RideRatingSection(session.rideReports, session.rideInfo) { attribute, level ->
                    session.reportRide(attribute, level)
                }
            }
        } else {
            val remaining = if (session.legIndex + 1 < session.legs.size) {
                session.legs.subList(session.legIndex + 1, session.legs.size)
            } else {
                emptyList()
            }
            remaining.forEach { LegRow(it) }
        }

        if (session.phase != OnboardPhase.ARRIVED) {
            Row(
                modifier = Modifier
                    .padding(top = 14.dp)
                    .clickable(interactionSource = null, indication = PlainIndication) { session.skipToNextStep() },
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SFSymbol(name = "forward.end.fill", size = 14.sp, color = LuxTheme.accent)
                Text(
                    "Passer à l'étape suivante",
                    style = LuxTheme.type.subheadline.copy(fontWeight = FontWeight.Medium),
                    color = LuxTheme.accent
                )
            }
        }
    }
}

@Composable
private fun LegRow(leg: Leg) {
    val colors = LuxTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leg.isTransit) {
            LinePill(leg.routeShortName ?: "", leg.agencyId, leg.mode, width = 38.dp, height = 24.dp, fontSize = 13.sp)
            Text(
                leg.headsign?.let { "Direction $it" } ?: "",
                style = LuxTheme.type.subheadline,
                color = colors.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        } else {
            Box(Modifier.width(38.dp), contentAlignment = Alignment.Center) {
                SFSymbol(name = "figure.walk", size = 17.sp, color = colors.systemBlue)
            }
            Text(
                "Marchez ${max(1, leg.duration / 60)} min",
                style = LuxTheme.type.subheadline,
                color = colors.label,
                modifier = Modifier.weight(1f)
            )
        }
        Text(
            formatTime(leg.startTime),
            style = LuxTypography.timeVariant(LuxTheme.type.subheadline.copy(fontWeight = FontWeight.Medium)),
            color = colors.secondaryLabel
        )
    }
}


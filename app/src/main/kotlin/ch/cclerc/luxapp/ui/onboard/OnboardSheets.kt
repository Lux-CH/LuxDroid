package ch.cclerc.luxapp.ui.onboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.cclerc.luxapp.core.HapticFeedback
import ch.cclerc.luxapp.core.SFSymbol
import ch.cclerc.luxapp.domain.map.LatLng
import ch.cclerc.luxapp.domain.map.distanceTo
import ch.cclerc.luxapp.domain.onboard.allStops
import ch.cclerc.luxapp.domain.onboard.coordinate
import ch.cclerc.luxapp.domain.scheduledDifference
import ch.cclerc.luxapp.ui.anim.PlainIndication
import ch.cclerc.luxapp.ui.anim.pulse
import ch.cclerc.luxapp.ui.anim.scaleClickable
import ch.cclerc.luxapp.ui.components.LinePill
import ch.cclerc.luxapp.ui.itinerary.formatTime
import ch.cclerc.luxapp.ui.theme.LuxMaterials
import ch.cclerc.luxapp.ui.theme.LuxTheme
import ch.cclerc.luxapp.ui.theme.LuxTypography
import ch.cclerc.luxapp.ui.theme.iosShadow
import ch.cclerc.luxcom.model.trip.Leg
import java.time.Instant

@Composable
fun OnboardConsentSheet(onAnswer: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val colors = LuxTheme.colors
    val accent = LuxTheme.accent
    Column(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.padding(top = 34.dp).size(112.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(112.dp).background(accent.copy(alpha = 0.12f), CircleShape))
                Box(
                    Modifier
                        .size(80.dp)
                        .iosShadow(accent.copy(alpha = 0.4f), 12.dp, 6.dp, CircleShape)
                        .background(gradientOf(accent), CircleShape)
                )
                SFSymbol(name = "tram.fill", size = 36.sp, color = Color.White, weight = 600)
                Box(
                    Modifier
                        .offset(x = 38.dp, y = (-34).dp)
                        .size(34.dp)
                        .iosShadow(Color.Black.copy(alpha = 0.12f), 4.dp, 2.dp, CircleShape)
                        .background(colors.systemBackground, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    SFSymbol(
                        name = "dot.radiowaves.up.forward",
                        size = 17.sp,
                        color = accent,
                        weight = 700,
                        modifier = Modifier.pulse(1f, 0.35f, 700)
                    )
                }
            }

            Text(
                "Aidez les autres voyageurs",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = colors.label,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 20.dp)
            )
            Text(
                "Pendant que vous êtes à bord, Lux peut partager anonymement la position du véhicule.",
                style = LuxTheme.type.body,
                color = colors.secondaryLabel,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp).padding(horizontal = 12.dp)
            )

            Column(
                Modifier.padding(top = 26.dp).padding(horizontal = 6.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                ConsentPoint("location.fill", colors.systemBlue, "La position du véhicule, pas la vôtre",
                    "Uniquement à bord, recalée sur le tracé de la ligne. Aucun identifiant, rien n'est conservé.")
                ConsentPoint("clock.badge.checkmark.fill", colors.systemGreen, "Des retards pour tous",
                    "Des retards justes, mesurés depuis le véhicule, même quand il n'y a pas de temps réel.")
                ConsentPoint("dot.radiowaves.up.forward", colors.systemOrange, "Le véhicule en direct",
                    "Ceux qui l'attendent voient où il se trouve sur la carte.")
                ConsentPoint("hand.raised.fill", colors.systemPurple, "Vous gardez la main",
                    "S'arrête dès que vous descendez. Désactivable à tout moment, ici ou dans les réglages.")
            }
            Spacer(Modifier.heightIn(min = 20.dp))
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Partager anonymement",
                style = LuxTheme.type.headline,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .background(gradientOf(accent), CircleShape)
                    .scaleClickable(haptic = false) {
                        HapticFeedback.success()
                        onAnswer(true)
                    }
                    .padding(vertical = 15.dp)
            )
            Text(
                "Pas maintenant",
                style = LuxTheme.type.headline,
                color = accent,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(interactionSource = null, indication = PlainIndication) {
                        HapticFeedback.lightImpact()
                        onAnswer(false)
                    }
                    .padding(vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun ConsentPoint(symbol: String, color: Color, title: String, text: String) {
    val colors = LuxTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.width(30.dp), contentAlignment = Alignment.Center) {
            SFSymbol(name = symbol, size = 19.sp, color = color, weight = 600)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = LuxTheme.type.subheadline.copy(fontWeight = FontWeight.SemiBold), color = colors.label)
            Text(text, style = LuxTheme.type.subheadline, color = colors.secondaryLabel)
        }
    }
}

@Composable
fun OnboardIntroCallout(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LuxTheme.colors
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = modifier
            .iosShadow(Color.Black.copy(alpha = 0.15f), 8.dp, 3.dp, shape)
            .clip(shape)
            .background(LuxMaterials.regular(), shape)
            .border(0.5.dp, colors.label.copy(alpha = 0.1f), shape)
            .clickable(interactionSource = null, indication = PlainIndication, onClick = onDismiss)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(Modifier.width(210.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                "NOUVEAU",
                style = LuxTheme.type.caption2.copy(fontWeight = FontWeight.Black),
                color = LuxTheme.accent
            )
            Text("Laissez Lux vous guider", style = LuxTheme.type.subheadline.copy(fontWeight = FontWeight.Bold), color = colors.label)
            Text(
                "Guidage pas à pas, alerte avant votre arrêt et retards en direct.",
                style = LuxTheme.type.caption,
                color = colors.secondaryLabel
            )
        }
        SFSymbol(name = "xmark", size = 11.sp, color = colors.secondaryLabel, weight = 700, modifier = Modifier.padding(top = 2.dp))
    }
}

fun defaultBoardIndex(tripLeg: Leg, userLocation: LatLng?): Int {
    val now = Instant.now()
    val stops = tripLeg.allStops
    fun time(index: Int): Instant? = stops[index].departure ?: stops[index].arrival

    if (userLocation != null) {
        val nearby = stops.indices
            .filter { index -> (time(index)?.isAfter(now.minusSeconds(180)) ?: true) && index < stops.size - 1 }
            .map { it to stops[it].coordinate.distanceTo(userLocation) }
            .filter { it.second < 250 }
            .minByOrNull { it.second }
        if (nearby != null) return nearby.first
    }
    val lastServed = stops.indices.lastOrNull { index -> time(index)?.let { !it.isAfter(now) } ?: false }
    if (lastServed != null && lastServed < stops.size - 1) return lastServed
    return 0
}

@Composable
fun OnboardStopPickerSheet(
    tripLeg: Leg,
    userLocation: LatLng?,
    onSelect: (Int, Int) -> Unit,
    onCancel: () -> Unit
) {
    val colors = LuxTheme.colors
    val accent = LuxTheme.accent
    val stops = tripLeg.allStops
    val board = defaultBoardIndex(tripLeg, userLocation)
    val color = bright(tripLeg, accent)

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 16.dp, bottom = 8.dp)) {
            Text(
                "Annuler",
                style = LuxTheme.type.body,
                color = accent,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .clickable(interactionSource = null, indication = PlainIndication, onClick = onCancel)
            )
            Text("Où descendez-vous ?", style = LuxTheme.type.headline, color = colors.label, modifier = Modifier.align(Alignment.Center))
        }
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LinePill(tripLeg.routeShortName ?: "", tripLeg.agencyId, tripLeg.mode, width = 38.dp, height = 24.dp, fontSize = 13.sp)
                Text(
                    "Depuis ${stops[board].name}",
                    style = LuxTheme.type.footnote,
                    color = colors.secondaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            val shape = RoundedCornerShape(12.dp)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(colors.secondarySystemGroupedBackgroundElevated, shape)
            ) {
                val choices = stops.indices.filter { it > board }
                choices.forEachIndexed { position, index ->
                    val stop = stops[index]
                    val isEnd = index == stops.size - 1
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                HapticFeedback.mediumImpact()
                                onSelect(board, index)
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(11.dp)
                                .background(if (isEnd) color else Color.Transparent, CircleShape)
                                .border(2.5.dp, color, CircleShape)
                        )
                        Text(stop.name, style = LuxTheme.type.body, color = colors.label, modifier = Modifier.weight(1f))
                        (stop.arrival ?: stop.scheduledArrival)?.let { time ->
                            Text(
                                formatTime(time),
                                style = LuxTypography.timeVariant(LuxTheme.type.subheadline.copy(fontWeight = FontWeight.Medium)),
                                color = if (stop.scheduledDifference() >= 2) colors.systemOrange else colors.secondaryLabel
                            )
                        }
                    }
                    if (position < choices.lastIndex) {
                        HorizontalDivider(Modifier.padding(start = 39.dp), thickness = 0.5.dp, color = colors.separator)
                    }
                }
            }
            Text(
                "Lux vous guide jusqu'à votre arrêt et vous prévient quand descendre, même écran verrouillé.",
                style = LuxTheme.type.footnote,
                color = colors.secondaryLabel,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).padding(bottom = 16.dp)
            )
        }
    }
}

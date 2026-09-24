package ch.cclerc.luxapp.ui.onboard

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.cclerc.luxapp.core.HapticFeedback
import ch.cclerc.luxapp.core.SFSymbol
import ch.cclerc.luxapp.domain.onboard.OnboardSession
import ch.cclerc.luxapp.domain.onboard.ReplanProposal
import ch.cclerc.luxapp.domain.onboard.ReplanReason
import ch.cclerc.luxapp.domain.onboard.acceptReplan
import ch.cclerc.luxapp.domain.onboard.declineReplan
import ch.cclerc.luxapp.ui.anim.PlainIndication
import ch.cclerc.luxapp.ui.anim.scaleClickable
import ch.cclerc.luxapp.ui.components.IosActivityIndicator
import ch.cclerc.luxapp.ui.components.LinePill
import ch.cclerc.luxapp.ui.itinerary.formatTime
import ch.cclerc.luxapp.ui.theme.LuxColors
import ch.cclerc.luxapp.ui.theme.LuxMaterials
import ch.cclerc.luxapp.ui.theme.LuxTheme
import ch.cclerc.luxapp.ui.theme.LuxTypography
import ch.cclerc.luxapp.ui.theme.iosShadow
import ch.cclerc.luxcom.model.feedback.InfoResponse
import ch.cclerc.luxcom.model.feedback.ReportAttribute
import ch.cclerc.luxcom.model.trip.Leg
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

val rideAttributes = listOf(ReportAttribute.CROWD, ReportAttribute.CLEAN, ReportAttribute.HEAT, ReportAttribute.NOISE)

val ReportAttribute.title: String
    get() = when (this) {
        ReportAttribute.CROWD -> "Affluence"
        ReportAttribute.CLEAN -> "Propreté"
        ReportAttribute.HEAT -> "Température"
        ReportAttribute.NOISE -> "Bruit"
        ReportAttribute.SMELL -> "Odeur"
    }

val ReportAttribute.symbolName: String
    get() = when (this) {
        ReportAttribute.CROWD -> "person.3.fill"
        ReportAttribute.CLEAN -> "sparkles"
        ReportAttribute.HEAT -> "thermometer.medium"
        ReportAttribute.NOISE -> "speaker.wave.2.fill"
        ReportAttribute.SMELL -> "nose.fill"
    }

fun ReportAttribute.label(level: Int): String {
    val index = level.coerceIn(1, 5) - 1
    return when (this) {
        ReportAttribute.CROWD -> listOf("Vide", "Places assises", "Quelques debout", "Debout", "Bondé")
        ReportAttribute.CLEAN -> listOf("Très sale", "Sale", "Correct", "Propre", "Impeccable")
        ReportAttribute.HEAT -> listOf("Froid", "Frais", "Agréable", "Chaud", "Étouffant")
        ReportAttribute.NOISE -> listOf("Silencieux", "Calme", "Normal", "Bruyant", "Très bruyant")
        ReportAttribute.SMELL -> listOf("Agréable", "Neutre", "Correct", "Désagréable", "Insupportable")
    }[index]
}

fun ReportAttribute.color(level: Double, colors: LuxColors): Color {
    val scale = when (this) {
        ReportAttribute.HEAT -> listOf(colors.systemCyan, colors.systemBlue, colors.systemGreen, colors.systemOrange, colors.systemRed)
        ReportAttribute.CLEAN -> listOf(colors.systemRed, colors.systemOrange, colors.systemYellow, colors.systemGreen, colors.systemGreen)
        ReportAttribute.CROWD, ReportAttribute.NOISE, ReportAttribute.SMELL ->
            listOf(colors.systemGreen, colors.systemGreen, colors.systemYellow, colors.systemOrange, colors.systemRed)
    }
    return scale[(level.roundToInt() - 1).coerceIn(0, 4)]
}

private val ReportAttribute.key: String get() = name.lowercase()

fun InfoResponse.communityLevel(attribute: ReportAttribute): Pair<Double, Boolean>? {
    val live = rt?.get(attribute.key)
    if (live != null && live.trustLevel >= 2) return live.level.toDouble() to true
    val average = this.average[attribute.key]
    if (average != null && average.trustLevel >= 2 && average.reportCount > 0) return average.level to false
    return null
}

@Composable
fun RideCommunityStrip(info: InfoResponse) {
    val colors = LuxTheme.colors
    val entries = rideAttributes.mapNotNull { attribute ->
        info.communityLevel(attribute)?.let { Triple(attribute, it.first, it.second) }
    }
    if (entries.isEmpty()) return
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        entries.forEach { (attribute, level, isLive) ->
            val tint = attribute.color(level, colors)
            Row(
                modifier = Modifier
                    .background(tint.copy(alpha = 0.13f), CircleShape)
                    .padding(horizontal = 9.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SFSymbol(name = attribute.symbolName, size = 11.sp, color = tint, weight = 600)
                Text(
                    attribute.label(level.roundToInt()),
                    style = LuxTheme.type.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = tint
                )
                if (isLive) Box(Modifier.size(5.dp).background(colors.systemGreen, CircleShape))
            }
        }
    }
}

@Composable
private fun cardModifier(): Modifier {
    val shape = RoundedCornerShape(24.dp)
    return Modifier
        .fillMaxWidth()
        .iosShadow(Color.Black.copy(alpha = 0.14f), 12.dp, 4.dp, shape)
        .clip(shape)
        .background(LuxMaterials.regular(), shape)
        .padding(16.dp)
}

@Composable
fun CrowdPromptCard(onAnswer: (Int) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LuxTheme.colors
    var answered by remember { mutableStateOf<Int?>(null) }
    val answers = listOf("person" to 1, "chair.fill" to 2, "figure.stand" to 4, "person.3.fill" to 5)

    LaunchedEffect(answered) {
        if (answered != null) {
            delay(1_800)
            onDismiss()
        }
    }

    AnimatedContent(
        targetState = answered != null,
        transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.95f)) togetherWith fadeOut() },
        modifier = modifier.then(cardModifier()),
        label = "crowdPrompt"
    ) { done ->
        if (done) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                SFSymbol(name = "checkmark.circle.fill", size = 22.sp, color = colors.systemGreen)
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text("Merci !", style = LuxTheme.type.headline, color = colors.label)
                    Text("Votre avis aide les autres voyageurs", style = LuxTheme.type.caption, color = colors.secondaryLabel)
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text("Il y a du monde à bord ?", style = LuxTheme.type.headline, color = colors.label)
                        Text("Une touche suffit, c'est anonyme", style = LuxTheme.type.caption, color = colors.secondaryLabel)
                    }
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(colors.label.copy(alpha = 0.06f), CircleShape)
                            .clickable(interactionSource = null, indication = PlainIndication, onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        SFSymbol(name = "xmark", size = 12.sp, color = colors.secondaryLabel, weight = 700)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    answers.forEach { (symbol, level) ->
                        val tint = ReportAttribute.CROWD.color(level.toDouble(), colors)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(tint.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                                .scaleClickable(haptic = false) {
                                    HapticFeedback.selectionChanged()
                                    onAnswer(level)
                                    answered = level
                                }
                                .padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SFSymbol(name = symbol, size = 19.sp, color = tint, weight = 600)
                            Text(
                                ReportAttribute.CROWD.label(level),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = tint,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RideRatingSection(
    reports: Map<ReportAttribute, Int>,
    info: InfoResponse?,
    onRate: (ReportAttribute, Int) -> Unit
) {
    val colors = LuxTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Comment est ce trajet ?", style = LuxTheme.type.headline, color = colors.label, modifier = Modifier.weight(1f))
            Text("Anonyme", style = LuxTheme.type.caption, color = colors.secondaryLabel)
        }
        rideAttributes.forEach { attribute ->
            RideRatingRow(attribute, reports[attribute], info?.communityLevel(attribute)?.first) { onRate(attribute, it) }
        }
    }
}

@Composable
private fun RideRatingRow(attribute: ReportAttribute, reported: Int?, community: Double?, onRate: (Int) -> Unit) {
    val colors = LuxTheme.colors
    val shown = reported ?: community?.roundToInt()
    val tint = shown?.let { attribute.color(it.toDouble(), colors) } ?: colors.secondaryLabel

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(34.dp)
                .background(tint.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            SFSymbol(name = attribute.symbolName, size = 15.sp, color = tint, weight = 600)
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    attribute.title,
                    style = LuxTheme.type.subheadline.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.label,
                    modifier = Modifier.weight(1f)
                )
                if (reported != null) {
                    Text(
                        attribute.label(reported),
                        style = LuxTheme.type.caption.copy(fontWeight = FontWeight.SemiBold),
                        color = attribute.color(reported.toDouble(), colors)
                    )
                    SFSymbol(name = "checkmark.circle.fill", size = 12.sp, color = colors.systemGreen)
                } else if (community != null) {
                    Text(attribute.label(community.roundToInt()), style = LuxTheme.type.caption, color = colors.secondaryLabel)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (level in 1..5) {
                    val fill = when {
                        reported != null -> if (level <= reported) attribute.color(reported.toDouble(), colors) else colors.secondaryLabel.copy(alpha = 0.15f)
                        community != null && level <= community.roundToInt() -> attribute.color(community, colors).copy(alpha = 0.35f)
                        else -> colors.secondaryLabel.copy(alpha = 0.15f)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(interactionSource = null, indication = PlainIndication) {
                                HapticFeedback.selectionChanged()
                                onRate(level)
                            }
                            .padding(vertical = 6.dp)
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .background(fill, CircleShape)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ReplanCard(session: OnboardSession, modifier: Modifier = Modifier) {
    val colors = LuxTheme.colors
    val accent = LuxTheme.accent
    val proposal = session.replan

    Column(modifier.then(cardModifier()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (proposal == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                IosActivityIndicator(size = 18.dp)
                Text(
                    "Recherche d'une alternative…",
                    style = LuxTheme.type.subheadline.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.label
                )
            }
            return@Column
        }

        val (title, symbol, tint) = when (proposal.reason) {
            ReplanReason.CONNECTION -> Triple("Correspondance compromise", "arrow.triangle.branch", colors.systemOrange)
            ReplanReason.MISSED_DEPARTURE -> Triple("Départ manqué", "arrow.triangle.branch", colors.systemOrange)
            ReplanReason.CANCELLED -> Triple("Véhicule supprimé", "xmark.octagon.fill", colors.systemRed)
            ReplanReason.EARLIER -> Triple("Départ plus tôt possible", "hare.fill", colors.systemGreen)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(34.dp)
                    .background(gradientOf(tint), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                SFSymbol(name = symbol, size = 17.sp, color = Color.White, weight = 700)
            }
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(title, style = LuxTheme.type.headline, color = colors.label)
                Text("Nouvel itinéraire trouvé", style = LuxTheme.type.caption, color = colors.secondaryLabel)
            }
        }

        ProposalRow(session, proposal)

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Garder l'actuel",
                style = LuxTheme.type.subheadline.copy(fontWeight = FontWeight.SemiBold),
                color = colors.label,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(colors.label.copy(alpha = 0.07f), CircleShape)
                    .scaleClickable { session.declineReplan() }
                    .padding(vertical = 11.dp)
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(gradientOf(accent), CircleShape)
                    .scaleClickable(haptic = false) { session.acceptReplan() }
                    .padding(vertical = 11.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val style = LuxTheme.type.subheadline.copy(fontWeight = FontWeight.SemiBold)
                Text("Utiliser", style = style, color = Color.White)
                proposal.autoApplyAt?.let { autoApplyAt ->
                    val now = rememberNow()
                    val seconds = maxOf(0, ceil((autoApplyAt.toEpochMilli() - now.toEpochMilli()) / 1000.0).toInt())
                    Text("${seconds}s", style = LuxTypography.timeVariant(style), color = Color.White.copy(alpha = 0.75f))
                }
            }
        }
    }
}

@Composable
private fun ProposalRow(session: OnboardSession, proposal: ReplanProposal) {
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
        val transit: Leg? = proposal.firstTransit
        if (transit != null) {
            LinePill(transit.routeShortName ?: "", transit.agencyId, transit.mode, width = 42.dp, height = 26.dp, fontSize = 14.sp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    "${formatTime(transit.startTime)} · ${session.placeName(transit.from)}",
                    style = LuxTheme.type.subheadline.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    transit.headsign?.let { "Direction $it" } ?: "",
                    style = LuxTheme.type.caption,
                    color = colors.secondaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            Box(Modifier.width(42.dp), contentAlignment = Alignment.Center) {
                SFSymbol(name = "figure.walk", size = 20.sp, color = colors.systemBlue)
            }
            Text(
                "À pied jusqu'à destination",
                style = LuxTheme.type.subheadline.copy(fontWeight = FontWeight.SemiBold),
                color = colors.label,
                modifier = Modifier.weight(1f)
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(formatTime(proposal.arrival), style = LuxTypography.timeVariant(LuxTheme.type.headline), color = colors.label)
            val minutes = (proposal.lateBy / 60).roundToInt()
            if (minutes != 0) {
                Text(
                    if (minutes > 0) "+$minutes min" else "$minutes min",
                    style = LuxTheme.type.caption.copy(fontWeight = FontWeight.Bold),
                    color = if (minutes > 0) colors.systemOrange else colors.systemGreen
                )
            }
        }
    }
}

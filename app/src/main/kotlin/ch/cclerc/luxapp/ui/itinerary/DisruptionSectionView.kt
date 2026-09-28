package ch.cclerc.luxapp.ui.itinerary

import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import ch.cclerc.luxapp.ui.components.IosActivityIndicator
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.cclerc.luxapp.core.HapticFeedback
import ch.cclerc.luxapp.core.SFSymbol
import ch.cclerc.luxapp.domain.DisruptionManager
import ch.cclerc.luxapp.ui.anim.PlainIndication
import ch.cclerc.luxapp.ui.components.LinePill
import ch.cclerc.luxapp.ui.theme.LuxColors
import ch.cclerc.luxapp.ui.theme.LuxMaterials
import ch.cclerc.luxapp.ui.theme.LuxTheme
import ch.cclerc.luxcom.model.Disruption
import ch.cclerc.luxcom.model.trip.Leg

@Immutable
data class DisruptionGroup(
    val id: String,
    val leg: Leg?,
    val disruptions: List<Disruption>
)

val LocalOpenDisruptions = staticCompositionLocalOf<((List<DisruptionGroup>) -> Unit)?> { null }

@Composable
fun rememberDisruptions(leg: Leg?): List<Disruption> {
    val all by DisruptionManager.shared.disruptions.collectAsState()
    if (leg == null) return emptyList()
    return remember(all, leg) { DisruptionManager.matching(all, leg) }
}

@Composable
fun DisruptionSectionView(
    disruptions: List<Disruption>,
    modifier: Modifier = Modifier
) {
    DisruptionsRow(
        groups = listOf(DisruptionGroup(id = "leg", leg = null, disruptions = disruptions)),
        modifier = modifier
    )
}

@Composable
fun DisruptionsRow(
    groups: List<DisruptionGroup>,
    modifier: Modifier = Modifier,
    action: (() -> Unit)? = null
) {
    val all = groups.flatMap { it.disruptions }
    if (all.isEmpty()) return

    val colors = LuxTheme.colors
    val openDisruptions = LocalOpenDisruptions.current
    val shape = RoundedCornerShape(14.dp)
    val count = all.size
    val scope = rememberCoroutineScope()
    var isOpening by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { isOpening = false } }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.label.copy(alpha = 0.05f), shape)
            .clickable(interactionSource = null, indication = PlainIndication) {
                if (isOpening) return@clickable
                HapticFeedback.lightImpact()
                isOpening = true
                if (action != null) action() else openDisruptions?.invoke(groups)
                scope.launch {
                    delay(2_000)
                    isOpening = false
                }
            }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SFSymbol(
            name = "exclamationmark.triangle.fill",
            size = 17.sp,
            color = colors.systemOrange,
            weight = 600
        )
        Text(
            text = if (count == 1) "1 perturbation" else "$count perturbations",
            style = LuxTheme.type.subheadline.copy(fontWeight = FontWeight.SemiBold),
            color = colors.label
        )
        Spacer(Modifier.weight(1f))
        if (isOpening) {
            IosActivityIndicator(size = 16.dp)
        } else {
            SFSymbol(
                name = "chevron.right",
                size = 13.sp,
                color = colors.tertiaryLabel,
                weight = 600
            )
        }
    }
}

private data class DisruptionEntry(
    val id: String,
    val leg: Leg?,
    val disruption: Disruption
)

private data class DisruptionCategory(
    val id: String,
    val entries: List<DisruptionEntry>
) {
    val symbol: String
        get() {
            val name = id.lowercase()
            return when {
                "travaux" in name || "chantier" in name -> "wrench.and.screwdriver.fill"
                "manifestation" in name || "événement" in name -> "flag.fill"
                "dévi" in name -> "arrow.triangle.turn.up.right.diamond.fill"
                "information" in name || "info" in name -> "info.circle.fill"
                else -> "exclamationmark.triangle.fill"
            }
        }

    fun color(colors: LuxColors): Color {
        val name = id.lowercase()
        return when {
            "manifestation" in name || "événement" in name -> colors.systemPurple
            "information" in name || "info" in name || "dévi" in name -> colors.systemBlue
            "travaux" in name || "chantier" in name -> colors.systemOrange
            else -> colors.systemRed
        }
    }
}

@Composable
fun DisruptionsListView(
    groups: List<DisruptionGroup>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LuxTheme.colors
    val showsLines = groups.size > 1
    val categories = remember(groups) {
        val order = mutableListOf<String>()
        val entries = mutableMapOf<String, MutableList<DisruptionEntry>>()
        for (group in groups) {
            for (disruption in group.disruptions) {
                val short = disruption.shortTitle
                val name = short.take(1).uppercase() + short.drop(1)
                if (entries[name] == null) order.add(name)
                entries.getOrPut(name) { mutableListOf() }
                    .add(DisruptionEntry("${group.id}-${disruption.id}", group.leg, disruption))
            }
        }
        order.map { DisruptionCategory(it, entries[it].orEmpty()) }
    }
    val cardShape = RoundedCornerShape(16.dp)

    Column(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 14.dp, bottom = 10.dp)
        ) {
            Text(
                text = "Perturbations",
                style = LuxTheme.type.headline,
                color = colors.label,
                modifier = Modifier.align(Alignment.Center)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(colors.secondarySystemFill, CircleShape)
                    .clickable(interactionSource = null, indication = PlainIndication, onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                SFSymbol(name = "chevron.left", size = 16.sp, color = colors.label, weight = 600)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            categories.forEach { category ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.padding(start = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val style = LuxTheme.type.subheadline.copy(fontWeight = FontWeight.SemiBold)
                        SFSymbol(name = category.symbol, size = 15.sp, color = category.color(colors), weight = 600)
                        Text(text = category.id, style = style, color = colors.label)
                        Text(text = "${category.entries.size}", style = style, color = colors.secondaryLabel)
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(cardShape)
                            .background(
                                if (colors.isDark) {
                                    colors.tertiarySystemBackground.copy(alpha = 0.9f)
                                } else {
                                    LuxMaterials.ultraThick()
                                },
                                cardShape
                            )
                    ) {
                        category.entries.forEachIndexed { index, entry ->
                            DisruptionRow(entry = entry, showsLines = showsLines)
                            if (index < category.entries.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = if (showsLines) 58.dp else 14.dp),
                                    thickness = 0.5.dp,
                                    color = colors.separator
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DisruptionRow(entry: DisruptionEntry, showsLines: Boolean) {
    val colors = LuxTheme.colors
    val title = entry.disruption.displayTitle
    val headline = if (title.length > 28) title else null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        val leg = entry.leg
        if (showsLines && leg != null) {
            LinePill(
                line = leg.routeShortName ?: "",
                agencyId = leg.agencyId,
                mode = leg.mode,
                width = 34.dp,
                height = 20.dp,
                fontSize = 11.sp
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (headline != null) {
                Text(
                    text = headline,
                    style = LuxTheme.type.subheadline.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.label
                )
            }
            Text(
                text = entry.disruption.displayText,
                style = if (headline == null) LuxTheme.type.subheadline else LuxTheme.type.footnote,
                color = if (headline == null) colors.label else colors.secondaryLabel
            )
        }
    }
}

private fun decodingHtmlEntities(text: String): String =
    android.text.Html.fromHtml(text, android.text.Html.FROM_HTML_MODE_LEGACY).toString()

val Disruption.displayTitle: String
    get() {
        if (text != null) return decodingHtmlEntities(title ?: "")
        val raw = decodingHtmlEntities(lineDisruption)
        val index = raw.indexOf(" - ")
        if (index < 0) return ""
        return raw.substring(0, index).trim()
    }

val Disruption.displayText: String
    get() {
        text?.let { return decodingHtmlEntities(it) }
        val raw = decodingHtmlEntities(lineDisruption)
        val index = raw.indexOf(" - ")
        if (index < 0) return raw
        return raw.substring(index + 3).trim()
    }

val Disruption.shortTitle: String
    get() {
        val title = displayTitle
        return if (title.isEmpty() || title.length > 28) "Perturbation" else title
    }

val Disruption.summary: String
    get() {
        val title = displayTitle
        return if (title.length > 28) title else displayText
    }

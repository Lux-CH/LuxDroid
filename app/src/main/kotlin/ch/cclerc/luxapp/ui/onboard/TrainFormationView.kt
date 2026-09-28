package ch.cclerc.luxapp.ui.onboard

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.TextUnit
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.cclerc.luxapp.core.SFSymbol
import ch.cclerc.luxapp.ui.map.StationStyle
import ch.cclerc.luxapp.ui.theme.LuxTheme
import ch.cclerc.luxcom.station.TrainFormation
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private val SecondClass = Color(0xFF2E45A8)
private val FirstClass = Color(red = 0.8f, green = 0.12f, blue = 0.16f)
private const val GAP = 3f
private val CoachHeight = 30.dp

private val TrainFormation.Coach.services: List<String>
    get() {
        val symbols = mutableListOf<String>()
        if (isRestaurant) symbols.add("fork.knife")
        if ("wheelchair" in o) symbols.add("figure.roll")
        if ("bike" in o) symbols.add("bicycle")
        if (t == "FA" || "family" in o) symbols.add("figure.2.and.child.holdinghands")
        return symbols
    }

private val TrainFormation.Coach.bodyKind: String
    get() {
        val kind = when {
            isLocomotive -> "L"
            t == "12" -> "M"
            isFirstClass -> "1"
            else -> "2"
        }
        return if (closed) "$kind-" else kind
    }

private sealed interface RunContent {
    val key: String

    data class Label(val text: String) : RunContent {
        override val key: String get() = "t$text"
    }

    data class Services(val symbols: List<String>) : RunContent {
        override val key: String get() = "s" + symbols.joinToString(",")
    }

    data class Occupancy(val level: Int) : RunContent {
        override val key: String get() = "o$level"
    }
}

enum class FormationPage { CLASSES, SERVICES, OCCUPANCY }

object FormationOccupancy {
    fun text(level: Int): String = when (level) {
        1 -> "places libres"
        2 -> "peu de places libres"
        else -> "places debout uniquement"
    }
}

private data class Span(val x: Float, val width: Float)

private class FormationLayout(formation: TrainFormation, platformSectors: List<String>, availableWidth: Float) {
    class Block(val id: Int, val coaches: List<TrainFormation.Coach>, val widths: List<Float>, val span: Span, val isFront: Boolean, val isRear: Boolean)
    class Sector(val id: Int, val letter: String, val span: Span, val isCovered: Boolean)
    class RunLabel(val id: String, val span: Span, val content: RunContent)

    val coaches = formation.coaches
    val occupancy = formation.occupancy
    val blocks: List<Block>
    val sectors: List<Sector>
    val total: Float
    val trainCenter: Float
    private val x: List<Float>
    private val widths: List<Float>

    init {
        val blockRanges = runs(coaches.map { it.bodyKind })
        val units = coaches.sumOf { if (it.isLocomotive) 0.75 else 1.0 }.toFloat()
        val gaps = max(0, blockRanges.size - 1) * GAP

        val lettered = coaches.mapNotNull { it.s }
        var axis = (platformSectors + lettered).toSet().sorted()
        val first = lettered.firstOrNull()
        val last = lettered.lastOrNull()
        if (first != null && last != null && first > last) axis = axis.reversed()
        val firstIndex = first?.let { axis.indexOf(it) } ?: 0
        val lastIndex = last?.let { axis.indexOf(it) } ?: max(0, axis.size - 1)
        val spanned = (abs(lastIndex - firstIndex) + 1).toFloat()
        val sectorCount = max(axis.size, 1).toFloat()

        val minimumCoach = 18f
        val sectorWidth = max(availableWidth / sectorCount, (minimumCoach * units + gaps) / spanned)
        var trainStart = min(firstIndex, lastIndex) * sectorWidth
        var trainLength = spanned * sectorWidth
        var coachWidth = (trainLength - gaps) / max(units, 1f)
        if (coachWidth > 40f) {
            coachWidth = 40f
            val length = 40f * units + gaps
            trainStart += (trainLength - length) / 2
            trainLength = length
        }

        val xs = mutableListOf<Float>()
        val ws = mutableListOf<Float>()
        var cursor = trainStart
        for (range in blockRanges) {
            for (index in range) {
                val width = if (coaches[index].isLocomotive) (coachWidth * 0.75f).roundToInt().toFloat() else coachWidth
                xs.add(cursor)
                ws.add(width)
                cursor += width
            }
            cursor += GAP
        }
        x = xs
        widths = ws
        total = if (axis.isEmpty()) max(0f, cursor - GAP) else sectorCount * sectorWidth
        trainCenter = trainStart + trainLength / 2

        blocks = blockRanges.mapIndexed { offset, range ->
            Block(
                id = offset,
                coaches = coaches.subList(range.first, range.last + 1),
                widths = ws.subList(range.first, range.last + 1),
                span = span(range),
                isFront = offset == 0,
                isRear = offset == blockRanges.lastIndex
            )
        }
        val covered = formation.coveredSectors
        sectors = axis.mapIndexed { index, letter ->
            Sector(index, letter, Span(index * sectorWidth, sectorWidth), letter in covered)
        }
    }

    fun labels(page: FormationPage): List<RunLabel> {
        val contents = coaches.map { content(it, page, occupancy) }
        return runs(contents).mapNotNull { range ->
            val content = contents[range.first] ?: return@mapNotNull null
            RunLabel("${range.first}-${range.last}-${content.key}", span(range), content)
        }
    }

    private fun span(range: IntRange): Span {
        val start = x[range.first]
        return Span(start, x[range.last] + widths[range.last] - start)
    }

    companion object {
        fun content(coach: TrainFormation.Coach, page: FormationPage, occupancy: TrainFormation.Occupancy?): RunContent? {
            if (coach.isLocomotive || coach.closed) return null
            if (page == FormationPage.SERVICES && coach.services.isNotEmpty()) return RunContent.Services(coach.services)
            if (page == FormationPage.OCCUPANCY) occupancy?.level(coach)?.let { return RunContent.Occupancy(it) }
            if (coach.isRestaurant) return RunContent.Services(listOf("fork.knife"))
            return RunContent.Label(
                when (coach.t) {
                    "12" -> "1·2"
                    "FA" -> "2"
                    else -> coach.t
                }
            )
        }

        fun <K> runs(keys: List<K?>): List<IntRange> {
            val runs = mutableListOf<IntRange>()
            keys.forEachIndexed { index, key ->
                val last = runs.lastOrNull()
                if (last != null && key != null && keys[last.last] == key && last.last == index - 1) {
                    runs[runs.lastIndex] = last.first..index
                } else {
                    runs.add(index..index)
                }
            }
            return runs
        }
    }
}

private fun coachShape(slantsLeading: Boolean, slantsTrailing: Boolean, density: Float) = GenericShape { size, _ ->
    val radius = 4f * density
    val slant = min(8f * density, size.width * 0.35f)
    moveTo(if (slantsLeading) slant else radius, 0f)
    lineTo(size.width - if (slantsTrailing) slant else radius, 0f)
    if (slantsTrailing) {
        lineTo(size.width, slant)
    } else {
        quadraticTo(size.width, 0f, size.width, radius)
    }
    lineTo(size.width, size.height - radius)
    quadraticTo(size.width, size.height, size.width - radius, size.height)
    lineTo(radius, size.height)
    quadraticTo(0f, size.height, 0f, size.height - radius)
    if (slantsLeading) {
        lineTo(0f, slant)
    } else {
        lineTo(0f, radius)
        quadraticTo(0f, 0f, radius, 0f)
    }
    close()
}

@Composable
fun TrainFormationView(
    formation: TrainFormation,
    platformSectors: List<String> = emptyList(),
    modifier: Modifier = Modifier
) {
    val colors = LuxTheme.colors
    var page by remember { mutableIntStateOf(0) }
    val pages = buildList {
        add(FormationPage.CLASSES)
        if (formation.coaches.any { it.services.isNotEmpty() }) add(FormationPage.SERVICES)
        if (formation.occupancy?.isKnown == true) add(FormationPage.OCCUPANCY)
    }

    LaunchedEffect(formation) {
        val count = pages.size
        if (count <= 1) return@LaunchedEffect
        while (true) {
            delay(4_000)
            page = (page + 1) % count
        }
    }

    BoxWithConstraints(modifier.fillMaxWidth().semantics { contentDescription = accessibilityText(formation) }) {
        val available = maxWidth.value
        val layout = remember(formation, platformSectors, available) { FormationLayout(formation, platformSectors, available) }
        val scroll = rememberScrollState()
        val density = LocalDensity.current

        LaunchedEffect(layout.trainCenter, layout.total) {
            val target = with(density) { (layout.trainCenter - available / 2).dp.toPx() }.roundToInt()
            scroll.scrollTo(target.coerceIn(0, scroll.maxValue))
        }

        Row(Modifier.horizontalScroll(scroll, enabled = layout.total > available)) {
            Column(
                Modifier.width(max(layout.total, available).dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SectorRuler(layout)
                Box(
                    Modifier
                        .width(layout.total.dp)
                        .height(CoachHeight)
                ) {
                    layout.blocks.forEach { block ->
                        BlockBody(
                            block,
                            Modifier
                                .offset(x = block.span.x.dp)
                                .width(block.span.width.dp)
                                .fillMaxHeight()
                        )
                    }
                    AnimatedContent(
                        targetState = page,
                        transitionSpec = {
                            slideInVertically(tween(600)) { -it } togetherWith slideOutVertically(tween(600)) { it }
                        },
                        modifier = Modifier.fillMaxHeight(),
                        label = "formationPage"
                    ) { shownPage ->
                        Box(Modifier.width(layout.total.dp).fillMaxHeight()) {
                            layout.labels(pages[shownPage % pages.size]).forEach { label ->
                                Box(
                                    Modifier
                                        .offset(x = label.span.x.dp)
                                        .width(label.span.width.dp)
                                        .fillMaxHeight()
                                        .padding(horizontal = 2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    RunLabelView(label.content, label.span.width.dp)
                                }
                            }
                        }
                    }
                }
                val railColor = colors.secondaryLabel.copy(alpha = 0.45f)
                Canvas(
                    Modifier
                        .width(layout.total.dp)
                        .height(5.dp)
                ) {
                    val lineHeight = 1.2.dp.toPx()
                    drawRect(railColor, Offset(0f, size.height / 2 - lineHeight / 2), Size(size.width, lineHeight))
                    var x = 2.dp.toPx()
                    val step = 5.dp.toPx()
                    while (x < size.width - 1.dp.toPx()) {
                        drawRect(railColor, Offset(x, 0f), Size(1.dp.toPx(), size.height))
                        x += step
                    }
                }
            }
        }
    }
}

@Composable
private fun SectorRuler(layout: FormationLayout) {
    val colors = LuxTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Box(Modifier.width(layout.total.dp).height(17.dp)) {
            layout.sectors.forEach { sector ->
                Box(
                    Modifier
                        .offset(x = sector.span.x.dp)
                        .width(sector.span.width.dp),
                    contentAlignment = Alignment.Center
                ) {
                    SectorChipView(sector.letter, covered = if (sector.isCovered) null else false, firstClass = false)
                }
            }
        }
        val lineColor = colors.secondaryLabel.copy(alpha = 0.7f)
        Canvas(
            Modifier
                .width(layout.total.dp)
                .height(7.dp)
        ) {
            val first = layout.sectors.firstOrNull() ?: return@Canvas
            val last = layout.sectors.last()
            val ticks = mutableListOf(first.span.x)
            layout.sectors.zipWithNext { previous, next ->
                ticks.add((previous.span.x + previous.span.width + next.span.x) / 2)
            }
            ticks.add(last.span.x + last.span.width)
            val stroke = 1.2.dp.toPx()
            fun inset(value: Float) = (value.dp.toPx()).coerceIn(1f, size.width - 1f)
            drawLine(lineColor, Offset(inset(ticks.first()), size.height / 2), Offset(inset(ticks.last()), size.height / 2), stroke)
            ticks.forEach { tick ->
                drawLine(lineColor, Offset(inset(tick), 0f), Offset(inset(tick), size.height), stroke)
            }
        }
    }
}

@Composable
private fun BlockBody(block: FormationLayout.Block, modifier: Modifier) {
    val coach = block.coaches[0]
    val density = LocalDensity.current.density
    Box(modifier.clip(coachShape(block.isFront, block.isRear, density))) {
        when {
            coach.isLocomotive -> Box(Modifier.matchParentSize().background(Color(0.32f, 0.32f, 0.32f)))
            coach.closed -> Box(Modifier.matchParentSize().background(Color(0.55f, 0.55f, 0.55f)))
            coach.t == "12" -> Row(Modifier.matchParentSize()) {
                block.widths.forEach { width ->
                    Row(Modifier.width(width.dp).fillMaxHeight()) {
                        Box(Modifier.weight(1f).fillMaxHeight().background(FirstClass))
                        Box(Modifier.weight(1f).fillMaxHeight().background(SecondClass))
                    }
                }
            }
            else -> Box(Modifier.matchParentSize().background(if (coach.isFirstClass) FirstClass else SecondClass))
        }
    }
}

@Composable
private fun RunLabelView(content: RunContent, width: Dp) {
    when (content) {
        is RunContent.Label -> Text(
            content.text,
            fontSize = if (content.text.length > 1) 12.sp else 15.sp,
            fontWeight = FontWeight.Black,
            color = Color.White,
            maxLines = 1
        )
        is RunContent.Occupancy -> when {
            width.value >= 38f -> PeopleLevel(content.level, 11.sp, Color.White)
            width.value >= 26f -> PeopleLevel(content.level, 8.sp, Color.White)
            else -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                SFSymbol(name = "person.fill", size = 9.sp, color = Color.White, weight = 800)
                Text("${content.level}", fontSize = 9.sp, fontWeight = FontWeight.Black, color = Color.White, maxLines = 1)
            }
        }
        is RunContent.Services -> {
            val symbols = content.symbols
            val fitting = when {
                width.value >= symbols.take(3).size * 15f -> symbols.take(3) to 12
                width.value >= symbols.take(3).size * 11f -> symbols.take(3) to 9
                width.value >= symbols.take(2).size * 11f -> symbols.take(2) to 9
                width.value >= 11f -> symbols.take(1) to 9
                else -> symbols.take(1) to 7
            }
            Row(horizontalArrangement = Arrangement.spacedBy(if (fitting.second < 10) 2.dp else 3.dp)) {
                fitting.first.forEach { SFSymbol(name = it, size = fitting.second.sp, color = Color.White, weight = 700) }
            }
        }
    }
}

@Composable
fun SectorChipView(letter: String, covered: Boolean?, firstClass: Boolean, modifier: Modifier = Modifier) {
    val yellow = Color(0.99f, 0.8f, 0.1f)
    Box(
        modifier = modifier
            .size(17.dp)
            .alpha(if (covered == false) 0.4f else 1f)
            .shadow(1.dp, CircleShape)
            .background(Color.White, CircleShape)
            .drawBehind {
                if (firstClass) {
                    drawArc(
                        color = yellow,
                        startAngle = 216f,
                        sweepAngle = 108f,
                        useCenter = false,
                        style = Stroke(3.dp.toPx())
                    )
                }
            }
            .border(1.2.dp, StationStyle.signBlue, CircleShape)
            .semantics { contentDescription = "Secteur $letter" },
        contentAlignment = Alignment.Center
    ) {
        Text(letter, fontSize = 10.sp, fontWeight = FontWeight.Black, color = StationStyle.signBlue)
    }
}

private fun accessibilityText(formation: TrainFormation): String {
    val sectors = formation.sectors
    val parts = mutableListOf<String>()
    if (sectors.first.isNotEmpty()) parts.add("1re classe secteur ${TrainFormation.sectorText(sectors.first)}")
    if (sectors.second.isNotEmpty()) parts.add("2e classe secteur ${TrainFormation.sectorText(sectors.second)}")
    if (sectors.restaurant.isNotEmpty()) parts.add("restaurant secteur ${TrainFormation.sectorText(sectors.restaurant)}")
    if (sectors.bike.isNotEmpty()) parts.add("vélos secteur ${TrainFormation.sectorText(sectors.bike)}")
    if (sectors.wheelchair.isNotEmpty()) parts.add("fauteuils roulants secteur ${TrainFormation.sectorText(sectors.wheelchair)}")
    formation.occupancy?.let { occupancy ->
        occupancy.first?.let { parts.add("1re classe ${FormationOccupancy.text(it)}") }
        occupancy.second?.let { parts.add("2e classe ${FormationOccupancy.text(it)}") }
    }
    return parts.joinToString(", ")
}

@Composable
fun PeopleLevel(level: Int, size: TextUnit, color: Color, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(if (size.value < 10) 0.dp else 1.dp)) {
        for (index in 1..3) {
            SFSymbol(
                name = "person.fill",
                size = size,
                color = color,
                weight = 700,
                modifier = Modifier.alpha(if (index <= level) 1f else 0.3f)
            )
        }
    }
}

@Composable
fun OccupancyForecastRow(occupancy: TrainFormation.Occupancy, modifier: Modifier = Modifier) {
    val colors = LuxTheme.colors
    val description = buildList {
        add("Affluence prévue")
        occupancy.first?.let { add("1re classe ${FormationOccupancy.text(it)}") }
        occupancy.second?.let { add("2e classe ${FormationOccupancy.text(it)}") }
    }.joinToString(", ")
    Row(
        modifier
            .fillMaxWidth()
            .semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            SFSymbol(name = "person.2.fill", size = 12.sp, color = colors.secondaryLabel, weight = 600)
            Text("Affluence prévue", style = LuxTheme.type.caption.copy(fontWeight = FontWeight.SemiBold), color = colors.secondaryLabel)
        }
        Spacer(Modifier.weight(1f).widthIn(min = 4.dp))
        occupancy.first?.let { OccupancyEntry("1", it, FirstClass) }
        occupancy.second?.let { OccupancyEntry("2", it, SecondClass) }
    }
}

@Composable
private fun OccupancyEntry(travelClass: String, level: Int, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(20.dp)
                .background(color, RoundedCornerShape(5.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(travelClass, fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color.White)
        }
        PeopleLevel(level, 11.sp, LuxTheme.colors.label)
    }
}

@Composable
fun FormationSummary(formation: TrainFormation, platformSectors: List<String>, modifier: Modifier = Modifier) {
    val occupancy = formation.occupancy
    if (formation.coaches.isNotEmpty()) {
        TrainFormationView(formation, platformSectors, modifier)
    } else if (occupancy != null && occupancy.isKnown) {
        OccupancyForecastRow(occupancy, modifier)
    }
}

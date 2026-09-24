package ch.cclerc.luxapp.domain.station

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import ch.cclerc.luxapp.domain.map.LatLng
import ch.cclerc.luxapp.domain.map.distanceTo
import ch.cclerc.luxapp.ui.stop.expanded.getTrackType
import ch.cclerc.luxapp.ui.theme.getLegColor
import ch.cclerc.luxcom.model.trip.Leg
import ch.cclerc.luxcom.station.StationLayout
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

fun List<List<Double>>?.toLatLngs(): List<LatLng> =
    orEmpty().mapNotNull { if (it.size >= 2) LatLng(it[0], it[1]) else null }

val StationLayout.Access.coordinate: LatLng get() = LatLng(lat, lon)
val StationLayout.Track.coordinate: LatLng get() = LatLng(lat, lon)
val StationLayout.Sector.coordinate: LatLng get() = LatLng(lat, lon)

enum class StationDetail {
    HIDDEN, TRACKS, LABELS, ALL_LABELS, ACCESS;

    companion object {
        fun of(cameraDistance: Double): StationDetail = when {
            cameraDistance < 700 -> ACCESS
            cameraDistance < 1400 -> ALL_LABELS
            cameraDistance < 2800 -> LABELS
            cameraDistance < 6000 -> TRACKS
            else -> HIDDEN
        }
    }
}

data class RailStyle(
    val band: Double,
    val tieLength: Double,
    val tieThickness: Double,
    val tieSpacing: Double
) {
    companion object {
        val rail = RailStyle(band = 0.45, tieLength = 2.2, tieThickness = 0.3, tieSpacing = 3.2)
        val ourRail = RailStyle(band = 0.7, tieLength = 2.4, tieThickness = 0.35, tieSpacing = 3.2)
    }
}

@Immutable
class StationOverlayContent private constructor(
    val areas: List<Area>,
    val rails: List<Area>,
    val access: List<AccessPoint>,
    val stairs: List<Stairway>,
    val lines: List<Line>,
    val labels: List<Label>
) {
    data class Line(val id: String, val coordinates: List<LatLng>)

    data class Label(
        val id: String,
        val coordinate: LatLng,
        val text: String,
        val color: Color?,
        val accessibilityText: String
    )

    data class Area(val id: String, val coordinates: List<LatLng>, val color: Color?)

    data class Stairway(val id: String, val band: List<LatLng>, val treads: List<LatLng>)

    data class AccessPoint(val id: String, val coordinate: LatLng, val kind: StationLayout.Access.Kind)

    val isEmpty: Boolean
        get() = areas.isEmpty() && rails.isEmpty() && lines.isEmpty() && labels.isEmpty() &&
            access.isEmpty() && stairs.isEmpty()

    fun visibleLabels(detail: StationDetail): List<Label> = when (detail) {
        StationDetail.HIDDEN, StationDetail.TRACKS -> emptyList()
        StationDetail.LABELS -> labels.filter { it.color != null }
        StationDetail.ALL_LABELS, StationDetail.ACCESS -> labels
    }

    fun visibleAccess(detail: StationDetail): List<AccessPoint> =
        if (detail >= StationDetail.ACCESS) access else emptyList()

    fun visibleStairs(detail: StationDetail): List<Stairway> =
        if (detail >= StationDetail.ALL_LABELS) stairs else emptyList()

    companion object {
        val Empty = StationOverlayContent(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())

        fun of(legs: List<Leg>, layouts: Map<Int, StationLayout>): StationOverlayContent {
            if (layouts.isEmpty()) return Empty

            val areas = mutableListOf<Area>()
            val rails = mutableListOf<Area>()
            val access = mutableListOf<AccessPoint>()
            val stairs = mutableListOf<Stairway>()
            val lines = mutableListOf<Line>()
            val labels = mutableListOf<Label>()

            val highlights = mutableMapOf<Int, MutableMap<String, Color>>()
            val pins = mutableMapOf<Int, MutableList<LatLng>>()
            for (leg in legs.reversed()) {
                if (!leg.mode.isMainlineRail) continue
                val color = getLegColor(leg)
                for (place in listOf(leg.to, leg.from)) {
                    val uic = StationLayout.uic(place.stopId) ?: continue
                    val layout = layouts[uic] ?: continue
                    pins.getOrPut(uic) { mutableListOf() }.add(LatLng(place.lat, place.lon))
                    val track = layout.track(place.track ?: place.scheduledTrack, place.stopId) ?: continue
                    highlights.getOrPut(uic) { mutableMapOf() }[track.track] = color
                }
            }

            for ((uic, layout) in layouts.toSortedMap()) {
                val highlighted = highlights[uic].orEmpty()
                val stationPins = pins[uic].orEmpty()
                layout.rails.orEmpty().forEachIndexed { index, rail ->
                    val coordinates = rail.line.toLatLngs()
                    if (coordinates.size < 2) return@forEachIndexed
                    val color = rail.track?.let { highlighted[it] }
                    val outline = RailShape.outline(coordinates, if (color == null) RailStyle.rail else RailStyle.ourRail)
                    if (outline.size < 3) return@forEachIndexed
                    rails.add(Area("$uic-r-$index", outline, color))
                }

                val usedPlatforms = layout.platforms.orEmpty()
                    .filter { platform -> platform.tracks.orEmpty().any { highlighted[it] != null } }
                    .map { it.ring.toLatLngs() }
                val stationAccess = mutableListOf<AccessPoint>()
                layout.access.orEmpty().forEachIndexed { index, point ->
                    val stairway = stairway(point, "$uic-s-$index")
                    if (stairway != null) {
                        stairs.add(stairway)
                        return@forEachIndexed
                    }
                    val coordinate = point.coordinate
                    if (usedPlatforms.isNotEmpty() && usedPlatforms.none { distance(coordinate, it) < 30 }) {
                        return@forEachIndexed
                    }
                    if (stationAccess.any { it.kind == point.kind && it.coordinate.distanceTo(coordinate) < 20 }) {
                        return@forEachIndexed
                    }
                    stationAccess.add(AccessPoint("$uic-a-$index", coordinate, point.kind))
                }
                access += stationAccess

                for (platform in layout.platforms.orEmpty()) {
                    val ring = platform.ring.toLatLngs()
                    if (ring.size < 3) continue
                    val color = platform.tracks.orEmpty().firstNotNullOfOrNull { highlighted[it] }
                    areas.add(Area("$uic-p-${platform.name ?: "${areas.size}"}", ring, color))
                }

                val placedSigns = mutableListOf<LatLng>()
                val orderedTracks = layout.tracks.withIndex().sortedWith(
                    compareBy({ if (highlighted[it.value.track] == null) 1 else 0 }, { it.index })
                )
                for ((trackIndex, track) in orderedTracks) {
                    val color = highlighted[track.track]
                    val edges = track.edges.map { it.toLatLngs() }.filter { it.size >= 2 }
                    edges.forEachIndexed { index, edge ->
                        lines.add(Line("$uic-${track.track}-$index", edge))
                    }
                    val sign = signCoordinate(
                        track = track,
                        edges = edges,
                        staggered = trackIndex % 2 == 0,
                        pins = stationPins,
                        signs = placedSigns,
                        access = stationAccess.map { it.coordinate }
                    )
                    placedSigns.add(sign)
                    labels.add(
                        Label(
                            id = "$uic-${track.track}",
                            coordinate = sign,
                            text = track.track,
                            color = color,
                            accessibilityText = getTrackType(track.track)
                        )
                    )
                }
            }

            return StationOverlayContent(
                areas = areas,
                rails = rails.sortedBy { if (it.color == null) 0 else 1 },
                access = access,
                stairs = stairs,
                lines = lines,
                labels = labels.sortedBy { if (it.color == null) 0 else 1 }
            )
        }

        private fun signCoordinate(
            track: StationLayout.Track,
            edges: List<List<LatLng>>,
            staggered: Boolean,
            pins: List<LatLng>,
            signs: List<LatLng>,
            access: List<LatLng>
        ): LatLng {
            val edge = edges.maxByOrNull { length(it) } ?: return track.coordinate
            val preferred = if (staggered) 0.3 else 0.7
            val fractions = generateSequence(0.1) { it + 0.025 }.takeWhile { it <= 0.9 + 1e-9 }.toList()
            val candidates = fractions.sortedBy { abs(it - preferred) }.map { point(edge, it) }

            fun clearance(coordinate: LatLng, others: List<LatLng>): Double =
                others.minOfOrNull { coordinate.distanceTo(it) } ?: Double.POSITIVE_INFINITY

            fun score(coordinate: LatLng): Double = min(
                min(clearance(coordinate, pins) / 45, clearance(coordinate, signs) / 35),
                clearance(coordinate, access) / 15
            )

            return candidates.firstOrNull { score(it) >= 1 }
                ?: candidates.maxByOrNull { score(it) }
                ?: track.coordinate
        }

        private fun stairway(access: StationLayout.Access, id: String): Stairway? {
            if (access.kind == StationLayout.Access.Kind.ELEVATOR) return null
            val ring = access.ring.toLatLngs()
            if (ring.size >= 3) return Stairway(id, ring, emptyList())
            val line = access.line.toLatLngs()
            if (line.size < 2) return null
            val width = access.width ?: if (access.kind == StationLayout.Access.Kind.ESCALATOR) 1.2 else 2.5
            val band = RailShape.outline(line, RailStyle(width, width, 0.0, Double.MAX_VALUE))
            val treads = RailShape.outline(line, RailStyle(0.06, width, 0.16, 0.55))
            if (band.size < 3) return null
            return Stairway(id, band, treads)
        }

        private fun distance(point: LatLng, outline: List<LatLng>): Double {
            val first = outline.firstOrNull() ?: return Double.POSITIVE_INFINITY
            val metresPerLat = 111_132.0
            val metresPerLon = 111_320.0 * cos(point.latitude * Math.PI / 180)
            fun xy(c: LatLng): Pair<Double, Double> =
                (c.longitude - point.longitude) * metresPerLon to (c.latitude - point.latitude) * metresPerLat
            var best = Double.POSITIVE_INFINITY
            val closed = outline + first
            for (index in 0 until closed.size - 1) {
                val (ax, ay) = xy(closed[index])
                val (bx, by) = xy(closed[index + 1])
                val dx = bx - ax
                val dy = by - ay
                val length2 = dx * dx + dy * dy
                val t = if (length2 > 0) max(0.0, min(1.0, -(ax * dx + ay * dy) / length2)) else 0.0
                best = min(best, hypot(ax + dx * t, ay + dy * t))
            }
            return best
        }

        private fun length(line: List<LatLng>): Double =
            line.zipWithNext().sumOf { (a, b) -> a.distanceTo(b) }

        private fun point(line: List<LatLng>, fraction: Double): LatLng {
            var remaining = length(line) * fraction
            for ((a, b) in line.zipWithNext()) {
                val segment = a.distanceTo(b)
                if (segment >= remaining && segment > 0) {
                    val t = remaining / segment
                    return LatLng(
                        a.latitude + (b.latitude - a.latitude) * t,
                        a.longitude + (b.longitude - a.longitude) * t
                    )
                }
                remaining -= segment
            }
            return line.lastOrNull() ?: line[0]
        }
    }
}

object RailShape {
    fun outline(line: List<LatLng>, style: RailStyle): List<LatLng> {
        val origin = line.firstOrNull() ?: return emptyList()
        if (line.size < 2) return emptyList()
        val metresPerLat = 111_132.0
        val metresPerLon = 111_320.0 * cos(origin.latitude * Math.PI / 180)
        val xy = line.map {
            (it.longitude - origin.longitude) * metresPerLon to (it.latitude - origin.latitude) * metresPerLat
        }

        val along = mutableListOf(0.0)
        for (i in 1 until xy.size) {
            along.add(along[i - 1] + hypot(xy[i].first - xy[i - 1].first, xy[i].second - xy[i - 1].second))
        }
        val total = along.last()
        if (total <= 0.5) return emptyList()

        data class Frame(val x: Double, val y: Double, val nx: Double, val ny: Double)

        fun frame(s: Double): Frame {
            var i = 1
            while (i < along.size - 1 && along[i] < s) i++
            val a = xy[i - 1]
            val b = xy[i]
            val length = max(along[i] - along[i - 1], 1e-6)
            val t = min(1.0, max(0.0, (s - along[i - 1]) / length))
            val dx = (b.first - a.first) / length
            val dy = (b.second - a.second) / length
            return Frame(a.first + (b.first - a.first) * t, a.second + (b.second - a.second) * t, -dy, dx)
        }

        val stations = along.map { it to style.band / 2 }.toMutableList()
        var tie = style.tieSpacing / 2
        while (tie + style.tieThickness / 2 < total) {
            val start = tie - style.tieThickness / 2
            val end = tie + style.tieThickness / 2
            stations += listOf(
                start to style.band / 2,
                start to style.tieLength / 2,
                end to style.tieLength / 2,
                end to style.band / 2
            )
            tie += style.tieSpacing
        }
        val sorted = stations.withIndex()
            .sortedWith(compareBy({ it.value.first }, { it.index }))
            .map { it.value }

        fun point(station: Pair<Double, Double>, side: Double): LatLng {
            val f = frame(station.first)
            val x = f.x + f.nx * station.second * side
            val y = f.y + f.ny * station.second * side
            return LatLng(origin.latitude + y / metresPerLat, origin.longitude + x / metresPerLon)
        }
        return sorted.map { point(it, 1.0) } + sorted.reversed().map { point(it, -1.0) }
    }
}

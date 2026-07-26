package ch.cclerc.luxcom.map

import ch.cclerc.luxcom.geo.StopGrouping
import ch.cclerc.luxcom.geo.calculateBoundingBox
import ch.cclerc.luxcom.geo.departureRadiusMeters
import ch.cclerc.luxcom.model.LocationType
import ch.cclerc.luxcom.model.Place
import ch.cclerc.luxcom.model.SearchResult
import ch.cclerc.luxcom.model.TransportationMode
import ch.cclerc.luxcom.net.ApiClient
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max

suspend fun getMapStops(minLat: Double, minLon: Double, maxLat: Double, maxLon: Double): List<Place> {
    val queryItems = listOf("min" to "$minLat, $minLon", "max" to "$maxLat, $maxLon")
    return ApiClient.fetch(endpoint = "/map/stops", queryItems = queryItems)
}

suspend fun getMapSearchResults(currentLat: Double, currentLon: Double): List<SearchResult> {
    for (radius in listOf(500.0, 1000.0, 5000.0)) {
        val bbox = calculateBoundingBox(currentLat, currentLon, radius)
        val fetchBox = calculateBoundingBox(currentLat, currentLon, radius + departureRadiusMeters)

        val stops = getMapStops(fetchBox.minLat, fetchBox.minLon, fetchBox.maxLat, fetchBox.maxLon)

        val seen = mutableMapOf<String, Int>()
        val uniqueStops = mutableListOf<Place>()
        val pointsById = mutableMapOf<String, MutableList<Pair<Double, Double>>>()
        for (place in stops) {
            val id = place.parentId ?: place.stopId ?: ""
            if (id.isEmpty()) continue
            pointsById.getOrPut(id) { mutableListOf() }.add(place.lat to place.lon)
            val index = seen[id]
            if (index != null) {
                val existing = uniqueStops[index]
                var modes = existing.modes
                for (mode in place.modes) {
                    if (!modes.contains(mode)) {
                        modes = modes + mode
                    }
                }
                if (modes !== existing.modes) {
                    uniqueStops[index] = existing.copy(modes = modes)
                }
            } else {
                seen[id] = uniqueStops.size
                uniqueStops.add(place)
            }
        }

        val groups = groupPlacesByStop(uniqueStops)
        val neighbourhood = groups.map { group ->
            val points = mutableListOf<Pair<Double, Double>>()
            var servesRail = false
            for (place in group) {
                val id = place.parentId ?: place.stopId ?: ""
                points.addAll(pointsById[id] ?: listOf(place.lat to place.lon))
                servesRail = servesRail || place.modes.any { it.isRail }
            }
            Pair(points, servesRail)
        }

        val railPoints = mutableListOf<Triple<Double, Double, Int>>()
        for ((index, entry) in neighbourhood.withIndex()) {
            if (!entry.second) continue
            for (point in entry.first) {
                railPoints.add(Triple(point.first, point.second, index))
            }
        }
        val maxLatDelta = departureRadiusMeters / 111_000 * 1.1
        val maxLonDelta = maxLatDelta / cos(currentLat * PI / 180)

        if (groups.isNotEmpty()) {
            val results = groups.mapIndexedNotNull { groupIndex, group ->
                val closest = group.map { place ->
                    val id = place.parentId ?: place.stopId ?: ""
                    val candidates = pointsById[id] ?: listOf(place.lat to place.lon)
                    var best = Triple(Double.MAX_VALUE, place.lat, place.lon)
                    for (point in candidates) {
                        val distance = StopGrouping.distance(
                            currentLat, currentLon,
                            point.first, point.second
                        )
                        if (distance < best.first) {
                            best = Triple(distance, point.first, point.second)
                        }
                    }
                    best
                }
                val nearest = closest.indices.minByOrNull { closest[it].first }
                    ?: return@mapIndexedNotNull null

                var ranked: Place? = null
                for (candidate in group) {
                    if (candidate.importance == null) continue
                    val current = ranked
                    if (current == null) {
                        ranked = candidate
                        continue
                    }
                    val l = current.importance ?: -1.0
                    val r = candidate.importance ?: -1.0
                    val increasing = if (l != r) {
                        l < r
                    } else {
                        !current.modes.any { it.isRail } && candidate.modes.any { it.isRail }
                    }
                    if (increasing) {
                        ranked = candidate
                    }
                }
                val identifying = ranked ?: group.firstOrNull { it.parentId != null } ?: group[nearest]
                val id = identifying.parentId ?: identifying.stopId
                if (id.isNullOrEmpty()) return@mapIndexedNotNull null

                val modes = mutableListOf<TransportationMode>()
                for (place in group) {
                    for (mode in place.modes) {
                        if (!modes.contains(mode)) {
                            modes.add(mode)
                        }
                    }
                }

                val points = neighbourhood[groupIndex].first
                if (points.none {
                        it.first >= bbox.minLat && it.first <= bbox.maxLat && it.second >= bbox.minLon && it.second <= bbox.maxLon
                    }) {
                    return@mapIndexedNotNull null
                }

                val hasRailNeighbour = railPoints.any { other ->
                    if (other.third == groupIndex) return@any false
                    points.any { point ->
                        abs(point.first - other.first) <= maxLatDelta
                            && abs(point.second - other.second) <= maxLonDelta
                            && StopGrouping.distance(point.first, point.second, other.first, other.second) <=
                                departureRadiusMeters
                    }
                }

                val groupedStopIds = mutableListOf<String>()
                for (place in group) {
                    val memberId = place.parentId ?: place.stopId ?: ""
                    if (memberId.isNotEmpty() && !groupedStopIds.contains(memberId)) {
                        groupedStopIds.add(memberId)
                    }
                }

                val score = max(0.0, 1.0 - (closest[nearest].first / radius))
                SearchResult(
                    type = LocationType.STOP,
                    tokens = listOf(emptyList()),
                    name = identifying.name,
                    id = id,
                    lat = closest[nearest].second,
                    lon = closest[nearest].third,
                    level = group[nearest].level,
                    street = null,
                    houseNumber = null,
                    zip = null,
                    areas = emptyList(),
                    score = score,
                    modes = modes,
                    groupedStopIds = groupedStopIds,
                    hasRailNeighbour = hasRailNeighbour
                )
            }.sortedByDescending { it.score }

            if (results.isNotEmpty()) {
                return results.filter { it.lat != 0.0 && it.lon != 0.0 }
            }
        }

        println("no stops found within $radius, expanding..")
    }
    return emptyList()
}

internal fun groupPlacesByStop(places: List<Place>): List<List<Place>> {
    val groups = mutableListOf<MutableList<Place>>()
    val groupsByFamily = mutableMapOf<String, MutableList<Int>>()

    for (place in places) {
        val family = StopGrouping.stationFamily(place.name)
        val name = StopGrouping.normalizedName(place.name)
        val candidates = groupsByFamily[family] ?: emptyList()
        val match = candidates.firstOrNull { index ->
            groups[index].any { member ->
                val limit = if (StopGrouping.normalizedName(member.name) == name) {
                    StopGrouping.maxDistance
                } else {
                    StopGrouping.familyMaxDistance
                }
                StopGrouping.distance(
                    member.lat, member.lon,
                    place.lat, place.lon
                ) <= limit
            }
        }

        if (match != null) {
            groups[match].add(place)
        } else {
            groupsByFamily.getOrPut(family) { mutableListOf() }.add(groups.size)
            groups.add(mutableListOf(place))
        }
    }

    return groups
}

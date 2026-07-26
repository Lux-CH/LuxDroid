package ch.cclerc.luxapp.domain.map

import ch.cclerc.luxcom.model.Place
import ch.cclerc.luxcom.model.TransportationMode
import ch.cclerc.luxcom.model.trip.Leg
import ch.cclerc.luxcom.model.trip.LegGeometry
import java.time.Instant
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VehicleVisualisationTest {

    private val start = Instant.ofEpochSecond(1_700_000_000)
    private val end = start.plusSeconds(600)

    private fun line(count: Int): List<LatLng> =
        (0 until count).map { LatLng(46.5 + it * 0.001, 6.6) }

    private fun place(
        name: String,
        point: LatLng,
        arrival: Instant? = null,
        departure: Instant? = null
    ) = Place(
        name = name,
        stopId = name,
        lat = point.latitude,
        lon = point.longitude,
        arrival = arrival,
        departure = departure,
        vertexType = Place.VertexType.TRANSIT
    )

    private fun leg(
        coordinates: List<LatLng>,
        intermediateStops: List<Place>? = null,
        mode: TransportationMode = TransportationMode.BUS
    ): Leg {
        val points = encodePolyline(coordinates, 1e6)
        return Leg(
            mode = mode,
            from = place("from", coordinates.first(), departure = start),
            to = place("to", coordinates.last(), arrival = end),
            duration = 600,
            startTime = start,
            endTime = end,
            scheduledStartTime = start,
            scheduledEndTime = end,
            realTime = true,
            intermediateStops = intermediateStops,
            legGeometry = LegGeometry(points = points, length = coordinates.size)
        )
    }

    @Test
    fun returnsNoKeyFramesForDegeneratePolyline() {
        val coordinates = listOf(LatLng(46.5, 6.6))
        val degenerate = leg(coordinates)
        assertTrue(VehicleVisualisation.calculateKeyFrames(degenerate, degenerate.legGeometry.points).isEmpty())
    }

    @Test
    fun keyFrameTimesAreMonotonicAndSpanTheLeg() {
        val coordinates = line(41)
        val target = leg(coordinates)
        val frames = VehicleVisualisation.calculateKeyFrames(target, target.legGeometry.points)

        assertTrue(frames.size >= 2)
        assertEquals(start.epochSecond.toDouble(), frames.first().time)
        assertEquals(end.epochSecond.toDouble(), frames.last().time, 1e-6)

        frames.zipWithNext { previous, next ->
            assertTrue(next.time >= previous.time, "times regressed: ${previous.time} -> ${next.time}")
        }
    }

    @Test
    fun samplesEveryThirdPointOnLongSegments() {
        val coordinates = line(61)
        val target = leg(coordinates)
        val frames = VehicleVisualisation.calculateKeyFrames(target, target.legGeometry.points)

        assertEquals(1 + (0..60).count { it % 3 == 0 }, frames.size)
    }

    @Test
    fun samplesEveryPointOnShortSegments() {
        val coordinates = line(10)
        val target = leg(coordinates)
        val frames = VehicleVisualisation.calculateKeyFrames(target, target.legGeometry.points)

        assertEquals(11, frames.size)
    }

    @Test
    fun injectsDwellWhenArrivalEqualsDeparture() {
        val coordinates = line(41)
        val stopInstant = start.plusSeconds(300)
        val stopPoint = coordinates[20]
        val target = leg(
            coordinates,
            intermediateStops = listOf(place("mid", stopPoint, arrival = stopInstant, departure = stopInstant))
        )

        val frames = VehicleVisualisation.calculateKeyFrames(target, target.legGeometry.points)
        val dwellFrame = frames.first { it.isDwelling }
        val dwellIndex = frames.indexOf(dwellFrame)
        val resumeFrame = frames[dwellIndex + 1]

        assertEquals(stopInstant.epochSecond.toDouble(), dwellFrame.time)
        assertEquals(dwellFrame.point, resumeFrame.point)
        assertEquals(25.0, resumeFrame.time - dwellFrame.time, 1e-6)
        assertTrue(resumeFrame.isStop)
        assertTrue(!resumeFrame.isDwelling)
    }

    @Test
    fun railDwellUsesRailDwellTime() {
        val coordinates = line(41)
        val stopInstant = start.plusSeconds(300)
        val target = leg(
            coordinates,
            intermediateStops = listOf(
                place("mid", coordinates[20], arrival = stopInstant, departure = stopInstant)
            ),
            mode = TransportationMode.REGIONAL_RAIL
        )

        val frames = VehicleVisualisation.calculateKeyFrames(target, target.legGeometry.points)
        val dwellIndex = frames.indexOfFirst { it.isDwelling }

        assertEquals(50.0, frames[dwellIndex + 1].time - frames[dwellIndex].time, 1e-6)
    }

    @Test
    fun positionIsFrozenDuringDwell() {
        val coordinates = line(41)
        val stopInstant = start.plusSeconds(300)
        val target = leg(
            coordinates,
            intermediateStops = listOf(
                place("mid", coordinates[20], arrival = stopInstant, departure = stopInstant)
            )
        )

        val frames = VehicleVisualisation.calculateKeyFrames(target, target.legGeometry.points)
        val dwellFrame = frames.first { it.isDwelling }

        val duringDwell = VehicleVisualisation.interpolatePosition(dwellFrame.time + 12.0, frames)
        assertEquals(dwellFrame.point, duringDwell)
    }

    @Test
    fun positionClampsAtBothEnds() {
        val coordinates = line(41)
        val target = leg(coordinates)
        val frames = VehicleVisualisation.calculateKeyFrames(target, target.legGeometry.points)

        assertEquals(frames.first().point, VehicleVisualisation.interpolatePosition(frames.first().time - 500.0, frames))
        assertEquals(frames.last().point, VehicleVisualisation.interpolatePosition(frames.last().time + 500.0, frames))
        assertNull(VehicleVisualisation.interpolatePosition(frames.first().time, emptyList()))
    }

    @Test
    fun positionAdvancesMonotonicallyAlongTheLine() {
        val coordinates = line(41)
        val target = leg(coordinates)
        val frames = VehicleVisualisation.calculateKeyFrames(target, target.legGeometry.points)

        var previousLatitude = -90.0
        var t = frames.first().time
        while (t <= frames.last().time) {
            val position = assertNotNull(VehicleVisualisation.interpolatePosition(t, frames))
            assertTrue(position.latitude >= previousLatitude - 1e-9)
            previousLatitude = position.latitude
            t += 5.0
        }

        assertTrue(abs(previousLatitude - coordinates.last().latitude) < 1e-4)
    }

    @Test
    fun velocityProfileIsSlowerAtTheEdgesThanInTheMiddle() {
        val coordinates = line(41)
        val target = leg(coordinates)
        val frames = VehicleVisualisation.calculateKeyFrames(target, target.legGeometry.points)

        val begin = assertNotNull(VehicleVisualisation.interpolatePosition(frames.first().time, frames))
        val afterAccel = assertNotNull(VehicleVisualisation.interpolatePosition(frames.first().time + 60.0, frames))
        val midStart = assertNotNull(VehicleVisualisation.interpolatePosition(frames.first().time + 270.0, frames))
        val midEnd = assertNotNull(VehicleVisualisation.interpolatePosition(frames.first().time + 330.0, frames))

        val accelerated = begin.distanceTo(afterAccel)
        val cruised = midStart.distanceTo(midEnd)

        assertTrue(cruised > accelerated, "cruise ($cruised m) should outpace acceleration ($accelerated m)")
    }

    @Test
    fun headingsStayWithinCompassRange() {
        val coordinates = line(41)
        val target = leg(coordinates)
        val frames = VehicleVisualisation.calculateKeyFrames(target, target.legGeometry.points)

        frames.forEach { assertTrue(it.heading in 0.0..360.0) }
        assertEquals(0.0, frames.first().heading, 1e-6)
    }

    @Test
    fun walkingPathMetricsInterpolateByDistanceFraction() {
        val coordinates = line(11)
        val metrics = buildWalkingPathMetrics(coordinates)

        assertEquals(11, metrics.cumulativeDistances.size)
        assertEquals(0.0, metrics.cumulativeDistances.first())
        assertTrue(metrics.totalDistance > 0.0)

        assertEquals(coordinates.first(), interpolateOnPath(metrics, 0.0))
        assertEquals(coordinates.first(), interpolateOnPath(metrics, -1.0))
        assertEquals(coordinates.last(), interpolateOnPath(metrics, 1.0))
        assertEquals(coordinates.last(), interpolateOnPath(metrics, 4.0))
        assertNull(interpolateOnPath(buildWalkingPathMetrics(emptyList()), 0.5))

        val middle = assertNotNull(interpolateOnPath(metrics, 0.5))
        assertEquals(coordinates[5].latitude, middle.latitude, 1e-9)
    }

    @Test
    fun equirectangularDistanceMatchesKnownSpan() {
        val distance = LatLng(46.5, 6.6).distanceTo(LatLng(46.51, 6.6))
        assertEquals(1111.9, distance, 1.0)
    }
}

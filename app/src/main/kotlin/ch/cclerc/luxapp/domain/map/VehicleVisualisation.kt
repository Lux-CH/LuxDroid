package ch.cclerc.luxapp.domain.map

import ch.cclerc.luxapp.domain.dwellTime
import ch.cclerc.luxcom.model.trip.Leg
import java.time.Instant
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

object VehicleVisualisation {

    data class KeyFrame(
        val point: LatLng,
        val heading: Double,
        val time: Double,
        val isStop: Boolean,
        val isDwelling: Boolean
    )

    private data class StopSample(
        val arrivalTime: Double?,
        var departureTime: Double,
        val coordinate: LatLng
    )

    private data class MappedStop(
        val arrivalTime: Double?,
        val departureTime: Double,
        val index: Int
    )

    fun calculateKeyFrames(leg: Leg, polylineString: String, precision: Double = 1e6): List<KeyFrame> {
        val coordinates = PolylineCodec.decode(polylineString, precision)
        if (coordinates.size < 2) return emptyList()

        val departureTime = leg.startTime.epochSeconds()
        val arrivalTime = leg.endTime.epochSeconds()

        val stops = ArrayList<StopSample>()

        stops.add(StopSample(null, departureTime, LatLng(leg.from.lat, leg.from.lon)))

        leg.intermediateStops?.forEach { stop ->
            val stopArrival = (stop.arrival ?: stop.scheduledArrival)?.epochSeconds()
            val stopDeparture = (stop.departure ?: stop.scheduledDeparture)?.epochSeconds()

            if (stopDeparture != null) {
                stops.add(StopSample(stopArrival, stopDeparture, LatLng(stop.lat, stop.lon)))
            } else if (stopArrival != null) {
                stops.add(StopSample(stopArrival, stopArrival, LatLng(stop.lat, stop.lon)))
            }
        }

        stops.add(StopSample(arrivalTime, arrivalTime, LatLng(leg.to.lat, leg.to.lon)))

        val dwellTime = leg.mode.dwellTime
        for (i in 1 until stops.size - 1) {
            val stopArrival = stops[i].arrivalTime
            if (stopArrival != null && abs(stopArrival - stops[i].departureTime) < 1.0) {
                stops[i].departureTime = stopArrival + dwellTime
            }
        }

        val mappedStops = stops.map { stop ->
            MappedStop(stop.arrivalTime, stop.departureTime, findClosestPointIndex(coordinates, stop.coordinate))
        }

        val keyFrames = ArrayList<KeyFrame>()

        val firstStop = mappedStops.first()
        keyFrames.add(
            KeyFrame(
                point = coordinates[firstStop.index],
                heading = if (firstStop.index + 1 < coordinates.size) {
                    coordinates[firstStop.index].greatCircleBearing(coordinates[firstStop.index + 1])
                } else {
                    0.0
                },
                time = firstStop.departureTime,
                isStop = true,
                isDwelling = false
            )
        )

        for (i in 0 until mappedStops.size - 1) {
            val startStop = mappedStops[i]
            val endStop = mappedStops[i + 1]
            val endArrival = endStop.arrivalTime

            if (endArrival != null && endArrival < endStop.departureTime) {
                createMovementSegment(
                    coordinates = coordinates,
                    fromIndex = startStop.index,
                    toIndex = endStop.index,
                    startTime = startStop.departureTime,
                    endTime = endArrival,
                    keyFrames = keyFrames,
                    isDepartingFromStop = true,
                    isArrivingAtStop = true
                )

                keyFrames.add(
                    KeyFrame(
                        point = coordinates[endStop.index],
                        heading = calculateHeading(coordinates, endStop.index),
                        time = endArrival,
                        isStop = true,
                        isDwelling = true
                    )
                )

                keyFrames.add(
                    KeyFrame(
                        point = coordinates[endStop.index],
                        heading = calculateHeading(coordinates, endStop.index),
                        time = endStop.departureTime,
                        isStop = true,
                        isDwelling = false
                    )
                )
            } else {
                createMovementSegment(
                    coordinates = coordinates,
                    fromIndex = startStop.index,
                    toIndex = endStop.index,
                    startTime = startStop.departureTime,
                    endTime = endStop.departureTime,
                    keyFrames = keyFrames,
                    isDepartingFromStop = true,
                    isArrivingAtStop = true
                )
            }
        }

        return keyFrames
    }

    private fun calculateHeading(coordinates: List<LatLng>, index: Int): Double = when {
        index + 1 < coordinates.size -> coordinates[index].greatCircleBearing(coordinates[index + 1])
        index > 0 -> coordinates[index - 1].greatCircleBearing(coordinates[index])
        else -> 0.0
    }

    private fun createMovementSegment(
        coordinates: List<LatLng>,
        fromIndex: Int,
        toIndex: Int,
        startTime: Double,
        endTime: Double,
        keyFrames: MutableList<KeyFrame>,
        isDepartingFromStop: Boolean,
        isArrivingAtStop: Boolean
    ) {
        val segmentDuration = endTime - startTime
        if (segmentDuration <= 0.0 || fromIndex == toIndex) return

        val segmentRange = if (fromIndex <= toIndex) fromIndex..toIndex else fromIndex downTo toIndex
        val segmentCoordinates = segmentRange.map { coordinates[it] }

        var totalDistance = 0.0
        for (i in 1 until segmentCoordinates.size) {
            totalDistance += segmentCoordinates[i - 1].distanceTo(segmentCoordinates[i])
        }

        if (totalDistance > 0.0) {
            val distances = ArrayList<Double>(segmentCoordinates.size)
            distances.add(0.0)
            var cumulativeDistance = 0.0

            for (i in 1 until segmentCoordinates.size) {
                cumulativeDistance += segmentCoordinates[i - 1].distanceTo(segmentCoordinates[i])
                distances.add(cumulativeDistance)
            }

            val accelerationDistance = if (isDepartingFromStop) min(totalDistance * 0.15, 200.0) else 0.0
            val decelerationDistance = if (isArrivingAtStop) min(totalDistance * 0.15, 200.0) else 0.0
            val cruisingDistance = totalDistance - accelerationDistance - decelerationDistance

            val accelerationTime = if (isDepartingFromStop) segmentDuration * 0.12 else 0.0
            val decelerationTime = if (isArrivingAtStop) segmentDuration * 0.12 else 0.0
            val cruisingTime = segmentDuration - accelerationTime - decelerationTime

            for (i in segmentCoordinates.indices) {
                val distance = distances[i]
                var time = startTime

                if (distance <= accelerationDistance && accelerationDistance > 0.0) {
                    val accelerationProgress = distance / accelerationDistance
                    time += accelerationTime * easeOutCubic(accelerationProgress)
                } else if (distance >= totalDistance - decelerationDistance && decelerationDistance > 0.0) {
                    val decelerationProgress =
                        (distance - (totalDistance - decelerationDistance)) / decelerationDistance
                    time += accelerationTime + cruisingTime + decelerationTime * easeInCubic(decelerationProgress)
                } else if (cruisingDistance > 0.0) {
                    val cruisingProgress = (distance - accelerationDistance) / cruisingDistance
                    time += accelerationTime + cruisingTime * cruisingProgress
                }

                val samplingInterval = max(1, min(segmentCoordinates.size / 20, 3))
                if (i == 0 || i == segmentCoordinates.size - 1 || i % samplingInterval == 0) {
                    val isFirstKeyFrame = i == 0
                    val isLastKeyFrame = i == segmentCoordinates.size - 1

                    val heading = when {
                        i < segmentCoordinates.size - 1 ->
                            segmentCoordinates[i].greatCircleBearing(segmentCoordinates[i + 1])
                        i > 0 -> segmentCoordinates[i - 1].greatCircleBearing(segmentCoordinates[i])
                        keyFrames.isNotEmpty() -> keyFrames.last().heading
                        else -> 0.0
                    }

                    keyFrames.add(
                        KeyFrame(
                            point = segmentCoordinates[i],
                            heading = heading,
                            time = time,
                            isStop = isFirstKeyFrame || isLastKeyFrame,
                            isDwelling = false
                        )
                    )
                }
            }
        } else {
            val heading = if (keyFrames.isEmpty()) 0.0 else keyFrames.last().heading
            keyFrames.add(
                KeyFrame(
                    point = coordinates[fromIndex],
                    heading = heading,
                    time = endTime,
                    isStop = true,
                    isDwelling = false
                )
            )
        }
    }

    private fun easeInCubic(x: Double): Double = x * x * x

    private fun easeOutCubic(x: Double): Double = 1.0 - (1.0 - x).pow(3)

    private fun findClosestPointIndex(coordinates: List<LatLng>, target: LatLng): Int {
        var closestDistance = Double.POSITIVE_INFINITY
        var closestIndex = 0

        coordinates.forEachIndexed { index, coordinate ->
            val distance = coordinate.squaredDistanceTo(target)
            if (distance < closestDistance) {
                closestDistance = distance
                closestIndex = index
            }
        }

        return closestIndex
    }

    fun interpolatePosition(timestamp: Double, keyFrames: List<KeyFrame>): LatLng? {
        if (keyFrames.isEmpty()) return null

        if (timestamp <= keyFrames.first().time) return keyFrames.first().point
        if (timestamp >= keyFrames.last().time) return keyFrames.last().point

        for (i in 1 until keyFrames.size) {
            val startFrame = keyFrames[i - 1]
            val endFrame = keyFrames[i]

            if (timestamp >= startFrame.time && timestamp <= endFrame.time) {
                if (startFrame.isDwelling && endFrame.isDwelling) {
                    return startFrame.point
                }

                val segmentDuration = endFrame.time - startFrame.time
                if (segmentDuration <= 0.0) return startFrame.point
                val progress = (timestamp - startFrame.time) / segmentDuration

                return interpolateCoordinate(startFrame.point, endFrame.point, progress)
            }
        }

        return keyFrames.last().point
    }
}

fun Instant.epochSeconds(): Double = epochSecond.toDouble() + nano / 1_000_000_000.0

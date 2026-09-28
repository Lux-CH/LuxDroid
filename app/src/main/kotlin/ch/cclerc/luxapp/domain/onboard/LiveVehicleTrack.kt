package ch.cclerc.luxapp.domain.onboard

import ch.cclerc.luxapp.domain.map.LatLng
import ch.cclerc.luxcom.relay.RelayClient
import java.time.Instant
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

class LiveVehicleTrack private constructor(
    val path: RoutePath,
    vehicle: RelayClient.CrowdVehicle,
    private var reportedAlong: Double,
    private var receivedAt: Instant
) {
    var vehicle: RelayClient.CrowdVehicle = vehicle
        private set
    private var correction = 0.0

    fun update(vehicle: RelayClient.CrowdVehicle, date: Instant) {
        val projection = path.project(LatLng(vehicle.lat, vehicle.lon), reportedAlong) ?: return
        val shown = along(date)
        this.vehicle = vehicle
        reportedAlong = projection.along
        receivedAt = date
        correction = 0.0
        val gap = shown - along(date)
        correction = if (abs(gap) < MAX_CORRECTION) gap else 0.0
    }

    fun along(date: Instant, limit: Double? = null): Double {
        val age = min(MAX_PROJECTION, max(0.0, (date.toEpochMilli() - receivedAt.toEpochMilli()) / 1000.0))
        var target = reportedAlong + max(0.0, vehicle.speed ?: 0.0) * age
        if (limit != null && reportedAlong <= limit) target = min(target, limit)
        target = min(target, path.length)
        return target + correction * exp(-age / SETTLE_TIME)
    }

    fun coordinate(date: Instant, limit: Double? = null): LatLng? = path.coordinate(along(date, limit))

    companion object {
        private const val MAX_PROJECTION = 20.0
        private const val SETTLE_TIME = 0.8
        private const val MAX_CORRECTION = 300.0

        fun create(
            path: RoutePath,
            vehicle: RelayClient.CrowdVehicle,
            receivedAt: Instant,
            hint: Double? = null
        ): LiveVehicleTrack? {
            if (path.isEmpty) return null
            val projection = path.project(LatLng(vehicle.lat, vehicle.lon), hint) ?: return null
            return LiveVehicleTrack(path, vehicle, projection.along, receivedAt)
        }
    }
}

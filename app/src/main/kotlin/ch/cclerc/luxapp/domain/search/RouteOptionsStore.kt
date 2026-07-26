package ch.cclerc.luxapp.domain.search

import ch.cclerc.luxapp.data.Settings
import ch.cclerc.luxcom.model.PedestrianProfile
import ch.cclerc.luxcom.model.TransportationMode
import ch.cclerc.luxcom.model.trip.RouteOptions
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

object RouteOptionsStore {

    const val DEFAULT_MAX_TRANSFERS = 5
    const val DEFAULT_MIN_TRANSFER_TIME = 0
    val DEFAULT_PEDESTRIAN_PROFILE = PedestrianProfile.FOOT
    const val DEFAULT_MAX_WALKING_TIME = 900
    const val DEFAULT_PEDESTRIAN_SPEED = 1.2

    const val KEY_MAX_TRANSFERS = "routeOptionsMaxTransfers"
    const val KEY_MIN_TRANSFER_TIME = "routeOptionsMinTransferTime"
    const val KEY_PEDESTRIAN_PROFILE = "routeOptionsPedestrianProfile"
    const val KEY_TRANSPORT_MODES = "routeOptionsTransportModes"
    const val KEY_MAX_WALKING_TIME = "routeOptionsMaxWalkingTime"
    const val KEY_PEDESTRIAN_SPEED = "routeOptionsPedestrianSpeed"

    private val json = Json { ignoreUnknownKeys = true }
    private val modesSerializer = ListSerializer(String.serializer())

    private val prefs get() = Settings.prefs

    val defaults: RouteOptions
        get() = RouteOptions(
            from = RouteOptions.RouteLocation(0.0, 0.0),
            to = RouteOptions.RouteLocation(0.0, 0.0),
            via = null,
            viaMinimumStay = emptyList(),
            time = null,
            arriveBy = false,
            maxTransfers = DEFAULT_MAX_TRANSFERS,
            minTransferTime = DEFAULT_MIN_TRANSFER_TIME,
            pedestrianProfile = DEFAULT_PEDESTRIAN_PROFILE,
            pedestrianSpeed = null,
            transitModes = null,
            numItineraries = 5,
            pageCursor = null,
            timetableView = true,
            maxPreTransitTime = null,
            maxPostTransitTime = null
        )

    fun load(base: RouteOptions = defaults): RouteOptions {
        val maxTransfers = prefs.getInt(KEY_MAX_TRANSFERS, DEFAULT_MAX_TRANSFERS)
        val minTransferTime = prefs.getInt(KEY_MIN_TRANSFER_TIME, DEFAULT_MIN_TRANSFER_TIME)
        val maxWalkingTime = prefs.getInt(KEY_MAX_WALKING_TIME, DEFAULT_MAX_WALKING_TIME)
        val pedestrianSpeed = readPedestrianSpeed()
        val pedestrianProfile = readPedestrianProfile()
        val transportModes = readTransportModes()

        return base.copy(
            maxTransfers = maxTransfers,
            minTransferTime = minTransferTime,
            pedestrianProfile = pedestrianProfile,
            pedestrianSpeed = if (pedestrianSpeed == DEFAULT_PEDESTRIAN_SPEED) null else pedestrianSpeed,
            transitModes = transportModes,
            maxPreTransitTime = if (maxWalkingTime == DEFAULT_MAX_WALKING_TIME) null else maxWalkingTime,
            maxPostTransitTime = if (maxWalkingTime == DEFAULT_MAX_WALKING_TIME) null else maxWalkingTime
        )
    }

    fun save(options: RouteOptions) {
        val walkingTime = options.maxPreTransitTime ?: DEFAULT_MAX_WALKING_TIME
        val pedestrianSpeed = options.pedestrianSpeed ?: DEFAULT_PEDESTRIAN_SPEED
        val modes = options.transitModes.orEmpty().distinct()

        prefs.edit()
            .putInt(KEY_MAX_TRANSFERS, options.maxTransfers)
            .putInt(KEY_MIN_TRANSFER_TIME, options.minTransferTime)
            .putString(KEY_PEDESTRIAN_PROFILE, options.pedestrianProfile.name)
            .putInt(KEY_MAX_WALKING_TIME, walkingTime)
            .putLong(KEY_PEDESTRIAN_SPEED, pedestrianSpeed.toRawBits())
            .putString(
                KEY_TRANSPORT_MODES,
                if (modes.isEmpty()) "" else json.encodeToString(modesSerializer, modes.map { it.rawValue })
            )
            .apply()
    }

    fun hasCustomSettings(options: RouteOptions): Boolean {
        val maxWalkingTime = options.maxPreTransitTime ?: DEFAULT_MAX_WALKING_TIME
        val pedestrianSpeed = options.pedestrianSpeed ?: DEFAULT_PEDESTRIAN_SPEED

        return options.maxTransfers != DEFAULT_MAX_TRANSFERS ||
            options.minTransferTime != DEFAULT_MIN_TRANSFER_TIME ||
            options.pedestrianProfile != DEFAULT_PEDESTRIAN_PROFILE ||
            !options.transitModes.isNullOrEmpty() ||
            maxWalkingTime != DEFAULT_MAX_WALKING_TIME ||
            pedestrianSpeed != DEFAULT_PEDESTRIAN_SPEED
    }

    fun maxWalkingTime(options: RouteOptions): Int = options.maxPreTransitTime ?: DEFAULT_MAX_WALKING_TIME

    fun pedestrianSpeed(options: RouteOptions): Double = options.pedestrianSpeed ?: DEFAULT_PEDESTRIAN_SPEED

    fun apply(
        options: RouteOptions,
        maxTransfers: Int = options.maxTransfers,
        minTransferTime: Int = options.minTransferTime,
        pedestrianProfile: PedestrianProfile = options.pedestrianProfile,
        maxWalkingTime: Int = maxWalkingTime(options),
        pedestrianSpeed: Double = pedestrianSpeed(options),
        transitModes: List<TransportationMode>? = options.transitModes
    ): RouteOptions = options.copy(
        maxTransfers = maxTransfers,
        minTransferTime = minTransferTime,
        pedestrianProfile = pedestrianProfile,
        pedestrianSpeed = if (pedestrianSpeed == DEFAULT_PEDESTRIAN_SPEED) null else pedestrianSpeed,
        transitModes = if (transitModes.isNullOrEmpty()) null else transitModes,
        maxPreTransitTime = if (maxWalkingTime == DEFAULT_MAX_WALKING_TIME) null else maxWalkingTime,
        maxPostTransitTime = if (maxWalkingTime == DEFAULT_MAX_WALKING_TIME) null else maxWalkingTime
    )

    private fun readPedestrianProfile(): PedestrianProfile {
        val stored = prefs.getString(KEY_PEDESTRIAN_PROFILE, DEFAULT_PEDESTRIAN_PROFILE.name)
        return PedestrianProfile.entries.firstOrNull { it.name.equals(stored, ignoreCase = true) }
            ?: DEFAULT_PEDESTRIAN_PROFILE
    }

    private fun readPedestrianSpeed(): Double {
        val stored = runCatching {
            prefs.getLong(KEY_PEDESTRIAN_SPEED, DEFAULT_PEDESTRIAN_SPEED.toRawBits())
        }.getOrDefault(DEFAULT_PEDESTRIAN_SPEED.toRawBits())
        return Double.fromBits(stored)
    }

    private fun readTransportModes(): List<TransportationMode>? {
        val raw = prefs.getString(KEY_TRANSPORT_MODES, null)
        if (raw.isNullOrEmpty()) return null
        val decoded = runCatching { json.decodeFromString(modesSerializer, raw) }.getOrNull() ?: return null
        val modes = decoded.mapNotNull { value ->
            TransportationMode.entries.firstOrNull { it.rawValue == value }
        }.distinct()
        return modes.ifEmpty { null }
    }
}

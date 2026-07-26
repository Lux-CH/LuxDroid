package ch.cclerc.luxapp.domain.shortcut

import ch.cclerc.luxapp.domain.map.LatLng
import ch.cclerc.luxapp.domain.map.distanceTo
import ch.cclerc.luxcom.model.LocationType
import ch.cclerc.luxcom.model.SearchResult
import java.util.Calendar
import java.util.Date
import java.util.UUID
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlinx.serialization.Serializable

@Serializable
data class UserShortcut(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val symbol: String,
    val coordinates: Coordinates,
    val timeSchedule: TimeSchedule? = null,
    val stopId: String? = null
) {
    @Serializable
    data class Coordinates(
        val latitude: Double,
        val longitude: Double,
        val locationName: String
    )

    @Serializable
    data class TimeSchedule(
        val daysOfWeek: Set<Weekday>,
        val time: TimeComponents
    ) {
        @Serializable
        enum class Weekday(val rawValue: Int) {
            MONDAY(1),
            TUESDAY(2),
            WEDNESDAY(3),
            THURSDAY(4),
            FRIDAY(5),
            SATURDAY(6),
            SUNDAY(7);

            val displayName: String
                get() = when (this) {
                    MONDAY -> "Lun"
                    TUESDAY -> "Mar"
                    WEDNESDAY -> "Mer"
                    THURSDAY -> "Jeu"
                    FRIDAY -> "Ven"
                    SATURDAY -> "Sam"
                    SUNDAY -> "Dim"
                }

            val shortDisplayName: String
                get() = when (this) {
                    MONDAY -> "L"
                    TUESDAY -> "Ma"
                    WEDNESDAY -> "Me"
                    THURSDAY -> "J"
                    FRIDAY -> "V"
                    SATURDAY -> "S"
                    SUNDAY -> "D"
                }

            companion object {
                fun fromRawValue(rawValue: Int): Weekday? = entries.firstOrNull { it.rawValue == rawValue }
            }
        }

        @Serializable
        data class TimeComponents(
            val hour: Int,
            val minute: Int
        ) {
            val displayString: String
                get() = String.format("%02d:%02d", hour, minute)
        }
    }

    val latLng: LatLng
        get() = LatLng(coordinates.latitude, coordinates.longitude)

    fun toSearchResult(): SearchResult = SearchResult(
        type = if (stopId != null) LocationType.STOP else LocationType.PLACE,
        tokens = listOf(listOf(0, name.length)),
        name = name,
        id = stopId ?: id,
        lat = coordinates.latitude,
        lon = coordinates.longitude,
        areas = emptyList(),
        score = 1.0
    )

    fun relevanceScore(
        currentDate: Date = Date(),
        userLocation: LatLng? = null,
        originalIndex: Int? = null
    ): Double {
        var score = 0.0

        score += 1.0

        if (originalIndex != null) {
            val orderBonus = max(0.0, 2.0 - (originalIndex.toDouble() * 0.5))
            score += orderBonus
        }

        val schedule = timeSchedule
        if (schedule != null) {
            val calendar = Calendar.getInstance()
            calendar.time = currentDate
            val currentWeekday = calendar.get(Calendar.DAY_OF_WEEK)
            val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
            val currentMinute = calendar.get(Calendar.MINUTE)

            val mappedWeekday = if (currentWeekday == 1) 7 else currentWeekday - 1

            val weekday = TimeSchedule.Weekday.fromRawValue(mappedWeekday)
            if (weekday != null && schedule.daysOfWeek.contains(weekday)) {
                score += 10.0

                val currentTimeInMinutes = currentHour * 60 + currentMinute
                val scheduledTimeInMinutes = schedule.time.hour * 60 + schedule.time.minute
                val timeDifference = abs(currentTimeInMinutes - scheduledTimeInMinutes)

                if (timeDifference <= 240) {
                    val proximityScore = max(0.0, 5.0 - (timeDifference.toDouble() / 48.0))
                    score += proximityScore
                }
            } else {
                val timeDifference =
                    abs(currentHour * 60 + currentMinute - schedule.time.hour * 60 + schedule.time.minute)
                if (timeDifference <= 240) {
                    val timeOnlyScore = max(0.0, 3.0 - (timeDifference.toDouble() / 80.0))
                    score += timeOnlyScore
                }
            }
        }

        if (userLocation != null) {
            val distance = userLocation.distanceTo(latLng)
            score -= calculateDistancePenalty(distance)
        }

        return score
    }

    private fun calculateDistancePenalty(distance: Double): Double {
        val distanceKm = distance / 1000.0

        return when {
            distanceKm < 0.025 -> 100.0
            distanceKm < 0.1 -> 80.0 + (20.0 * (0.1 - distanceKm) / 0.075)
            distanceKm < 0.5 -> 60.0 * exp(-distanceKm * 3.0) + 20.0
            distanceKm < 1.0 -> {
                val normalizedDistance = (distanceKm - 0.5) / 0.5
                40.0 * (1.0 - normalizedDistance.pow(0.7)) + 15.0
            }
            distanceKm < 2.0 -> {
                val normalizedDistance = (distanceKm - 1.0) / 1.0
                25.0 * (1.0 - normalizedDistance.pow(0.6)) + 8.0
            }
            else -> max(0.5, 5.0 * exp(-distanceKm / 20.0))
        }
    }
}

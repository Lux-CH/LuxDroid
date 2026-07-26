package ch.cclerc.luxcom.model

import ch.cclerc.luxcom.serialization.TransportationModeSerializer
import kotlinx.serialization.Serializable

@Serializable(with = TransportationModeSerializer::class)
enum class TransportationMode(val rawValue: String) {
    WALK("WALK"),
    BIKE("BIKE"),
    RENTAL("RENTAL"),
    CAR("CAR"),
    CAR_PARKING("CAR_PARKING"),
    ODM("ODM"),
    TRANSIT("TRANSIT"),
    TRAM("TRAM"),
    SUBWAY("SUBWAY"),
    FERRY("FERRY"),
    AIRPLANE("AIRPLANE"),
    METRO("METRO"),
    BUS("BUS"),
    COACH("COACH"),
    RAIL("RAIL"),
    HIGHSPEED_RAIL("HIGHSPEED_RAIL"),
    LONG_DISTANCE("LONG_DISTANCE"),
    NIGHT_RAIL("NIGHT_RAIL"),
    REGIONAL_FAST_RAIL("REGIONAL_FAST_RAIL"),
    REGIONAL_RAIL("REGIONAL_RAIL"),
    SUBURBAN("SUBURBAN"),
    FUNICULAR("FUNICULAR"),
    OTHER("OTHER");

    val isRail: Boolean
        get() = when (this) {
            RAIL, HIGHSPEED_RAIL, LONG_DISTANCE, NIGHT_RAIL,
            REGIONAL_FAST_RAIL, REGIONAL_RAIL, SUBURBAN, FUNICULAR, METRO, SUBWAY -> true
            else -> false
        }

    val usesSquaredPill: Boolean
        get() = isRail || this == FERRY
}

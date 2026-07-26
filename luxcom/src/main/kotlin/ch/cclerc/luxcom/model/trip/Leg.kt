package ch.cclerc.luxcom.model.trip

import ch.cclerc.luxcom.model.Place
import ch.cclerc.luxcom.model.TransportationMode
import ch.cclerc.luxcom.serialization.InstantIso8601Serializer
import java.time.Instant
import kotlinx.serialization.Serializable

@Serializable
data class Leg(
    val mode: TransportationMode,
    val from: Place,
    val to: Place,
    val duration: Int,
    @Serializable(with = InstantIso8601Serializer::class) val startTime: Instant,
    @Serializable(with = InstantIso8601Serializer::class) val endTime: Instant,
    @Serializable(with = InstantIso8601Serializer::class) val scheduledStartTime: Instant,
    @Serializable(with = InstantIso8601Serializer::class) val scheduledEndTime: Instant,
    val realTime: Boolean,
    val distance: Double? = null,
    val headsign: String? = null,
    val routeShortName: String? = null,
    val intermediateStops: List<Place>? = null,
    val legGeometry: LegGeometry,
    val agencyId: String? = null,
    val tripId: String? = null,
    val steps: List<StepInstruction>? = null,
    val interlineWithPreviousLeg: Boolean? = null,
    val alternatives: List<List<Leg>>? = null
)

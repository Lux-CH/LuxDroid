package ch.cclerc.luxcom.luxtrip

import ch.cclerc.luxcom.model.Place
import ch.cclerc.luxcom.model.TransportationMode
import ch.cclerc.luxcom.model.trip.Direction
import ch.cclerc.luxcom.model.trip.Itinerary
import ch.cclerc.luxcom.model.trip.Leg
import ch.cclerc.luxcom.model.trip.LegGeometry
import ch.cclerc.luxcom.model.trip.StepInstruction
import ch.cclerc.luxcom.model.trip.StepPolyline
import java.time.Instant
import org.msgpack.core.MessagePack
import org.msgpack.core.MessagePacker
import org.msgpack.core.MessageUnpacker
import org.msgpack.value.ValueType

object LuxTripCodec {
    private val modesByRawValue = TransportationMode.entries.associateBy { it.rawValue }
    private val directionNames =
        Direction.entries.associateWith { Direction.serializer().descriptor.getElementName(it.ordinal) }
    private val directionsByName =
        directionNames.entries.associate { (direction, name) -> name to direction }

    fun encode(itinerary: Itinerary): ByteArray =
        MessagePack.newDefaultBufferPacker().use { packer ->
            packer.packItinerary(itinerary)
            packer.flush()
            packer.toByteArray()
        }

    fun decode(data: ByteArray): Itinerary =
        MessagePack.newDefaultUnpacker(data).use { unpacker ->
            unpacker.unpackItinerary()
        }

    private fun MessagePacker.packMapHeaderCompat(count: Int) {
        if (count == 15) {
            addPayload(byteArrayOf(0xde.toByte(), 0x00, 0x0f))
        } else {
            packMapHeader(count)
        }
    }

    private fun MessagePacker.packArrayHeaderCompat(count: Int) {
        if (count == 15) {
            addPayload(byteArrayOf(0xdc.toByte(), 0x00, 0x0f))
        } else {
            packArrayHeader(count)
        }
    }

    private fun MessagePacker.packItinerary(value: Itinerary) {
        packMapHeaderCompat(5)
        packString("duration")
        packInt(value.duration)
        packString("startTime")
        packTimestamp(value.startTime)
        packString("endTime")
        packTimestamp(value.endTime)
        packString("transfers")
        packInt(value.transfers)
        packString("legs")
        packArrayHeaderCompat(value.legs.size)
        value.legs.forEach { packLeg(it) }
    }

    private fun MessagePacker.packLeg(value: Leg) {
        var count = 10
        if (value.distance != null) count++
        if (value.headsign != null) count++
        if (value.routeShortName != null) count++
        if (value.intermediateStops != null) count++
        if (value.agencyId != null) count++
        if (value.tripId != null) count++
        if (value.steps != null) count++
        if (value.interlineWithPreviousLeg != null) count++
        if (value.alternatives != null) count++
        packMapHeaderCompat(count)
        packString("mode")
        packString(value.mode.rawValue)
        packString("from")
        packPlace(value.from)
        packString("to")
        packPlace(value.to)
        packString("duration")
        packInt(value.duration)
        packString("startTime")
        packTimestamp(value.startTime)
        packString("endTime")
        packTimestamp(value.endTime)
        packString("scheduledStartTime")
        packTimestamp(value.scheduledStartTime)
        packString("scheduledEndTime")
        packTimestamp(value.scheduledEndTime)
        packString("realTime")
        packBoolean(value.realTime)
        value.distance?.let {
            packString("distance")
            packDouble(it)
        }
        value.headsign?.let {
            packString("headsign")
            packString(it)
        }
        value.routeShortName?.let {
            packString("routeShortName")
            packString(it)
        }
        value.intermediateStops?.let { stops ->
            packString("intermediateStops")
            packArrayHeaderCompat(stops.size)
            stops.forEach { packPlace(it) }
        }
        packString("legGeometry")
        packLegGeometry(value.legGeometry)
        value.agencyId?.let {
            packString("agencyId")
            packString(it)
        }
        value.tripId?.let {
            packString("tripId")
            packString(it)
        }
        value.steps?.let { steps ->
            packString("steps")
            packArrayHeaderCompat(steps.size)
            steps.forEach { packStepInstruction(it) }
        }
        value.interlineWithPreviousLeg?.let {
            packString("interlineWithPreviousLeg")
            packBoolean(it)
        }
        value.alternatives?.let { alternatives ->
            packString("alternatives")
            packArrayHeaderCompat(alternatives.size)
            alternatives.forEach { group ->
                packArrayHeaderCompat(group.size)
                group.forEach { packLeg(it) }
            }
        }
    }

    private fun MessagePacker.packPlace(value: Place) {
        var count = 6
        if (value.stopId != null) count++
        if (value.parentId != null) count++
        if (value.arrival != null) count++
        if (value.departure != null) count++
        if (value.scheduledArrival != null) count++
        if (value.scheduledDeparture != null) count++
        if (value.scheduledTrack != null) count++
        if (value.track != null) count++
        if (value.importance != null) count++
        packMapHeaderCompat(count)
        packString("name")
        packString(value.name)
        value.stopId?.let {
            packString("stopId")
            packString(it)
        }
        value.parentId?.let {
            packString("parentId")
            packString(it)
        }
        packString("lat")
        packDouble(value.lat)
        packString("lon")
        packDouble(value.lon)
        packString("level")
        packDouble(value.level)
        value.arrival?.let {
            packString("arrival")
            packTimestamp(it)
        }
        value.departure?.let {
            packString("departure")
            packTimestamp(it)
        }
        value.scheduledArrival?.let {
            packString("scheduledArrival")
            packTimestamp(it)
        }
        value.scheduledDeparture?.let {
            packString("scheduledDeparture")
            packTimestamp(it)
        }
        value.scheduledTrack?.let {
            packString("scheduledTrack")
            packString(it)
        }
        value.track?.let {
            packString("track")
            packString(it)
        }
        packString("vertexType")
        packString(value.vertexType.name)
        packString("modes")
        packArrayHeaderCompat(value.modes.size)
        value.modes.forEach { packString(it.rawValue) }
        value.importance?.let {
            packString("importance")
            packDouble(it)
        }
    }

    private fun MessagePacker.packLegGeometry(value: LegGeometry) {
        packMapHeaderCompat(2)
        packString("points")
        packString(value.points)
        packString("length")
        packInt(value.length)
    }

    private fun MessagePacker.packStepInstruction(value: StepInstruction) {
        var count = 9
        if (value.osmWay != null) count++
        packMapHeaderCompat(count)
        packString("relativeDirection")
        packString(directionNames.getValue(value.relativeDirection))
        packString("distance")
        packDouble(value.distance)
        packString("fromLevel")
        packDouble(value.fromLevel)
        packString("toLevel")
        packDouble(value.toLevel)
        value.osmWay?.let {
            packString("osmWay")
            packInt(it)
        }
        packString("streetName")
        packString(value.streetName)
        packString("exit")
        packString(value.exit)
        packString("stayOn")
        packBoolean(value.stayOn)
        packString("area")
        packBoolean(value.area)
        packString("polyline")
        packStepPolyline(value.polyline)
    }

    private fun MessagePacker.packStepPolyline(value: StepPolyline) {
        packMapHeaderCompat(3)
        packString("points")
        packString(value.points)
        packString("precision")
        packInt(value.precision)
        packString("length")
        packInt(value.length)
    }

    private fun MessageUnpacker.readString(): String? {
        if (tryUnpackNil()) return null
        return unpackString()
    }

    private fun MessageUnpacker.readInt(): Int? {
        if (tryUnpackNil()) return null
        return unpackInt()
    }

    private fun MessageUnpacker.readDouble(): Double? {
        if (tryUnpackNil()) return null
        return when (nextFormat.valueType) {
            ValueType.FLOAT -> unpackDouble()
            ValueType.INTEGER -> unpackLong().toDouble()
            else -> {
                skipValue()
                null
            }
        }
    }

    private fun MessageUnpacker.readBoolean(): Boolean? {
        if (tryUnpackNil()) return null
        return unpackBoolean()
    }

    private fun MessageUnpacker.readInstant(): Instant? {
        if (tryUnpackNil()) return null
        return unpackTimestamp()
    }

    private fun <T> MessageUnpacker.readList(element: MessageUnpacker.() -> T): List<T>? {
        if (tryUnpackNil()) return null
        val size = unpackArrayHeader()
        val out = ArrayList<T>(size)
        repeat(size) { out.add(element()) }
        return out
    }

    private fun MessageUnpacker.readMode(): TransportationMode =
        modesByRawValue[readString()] ?: TransportationMode.OTHER

    private fun MessageUnpacker.readDirection(): Direction =
        directionsByName[readString()] ?: Direction.continueStraight

    private fun MessageUnpacker.readVertexType(): Place.VertexType {
        val raw = readString()
        return Place.VertexType.entries.firstOrNull { it.name == raw } ?: Place.VertexType.NORMAL
    }

    private fun emptyPlace() = Place(
        name = "",
        lat = 0.0,
        lon = 0.0,
        vertexType = Place.VertexType.NORMAL
    )

    private fun MessageUnpacker.unpackItinerary(): Itinerary {
        var duration = 0
        var startTime = Instant.EPOCH
        var endTime = Instant.EPOCH
        var transfers = 0
        var legs: List<Leg> = emptyList()
        repeat(unpackMapHeader()) {
            when (unpackString()) {
                "duration" -> duration = readInt() ?: 0
                "startTime" -> startTime = readInstant() ?: Instant.EPOCH
                "endTime" -> endTime = readInstant() ?: Instant.EPOCH
                "transfers" -> transfers = readInt() ?: 0
                "legs" -> legs = readList { unpackLeg() } ?: emptyList()
                else -> skipValue()
            }
        }
        return Itinerary(duration, startTime, endTime, transfers, legs)
    }

    private fun MessageUnpacker.unpackLeg(): Leg {
        var mode = TransportationMode.OTHER
        var from = emptyPlace()
        var to = emptyPlace()
        var duration = 0
        var startTime = Instant.EPOCH
        var endTime = Instant.EPOCH
        var scheduledStartTime = Instant.EPOCH
        var scheduledEndTime = Instant.EPOCH
        var realTime = false
        var distance: Double? = null
        var headsign: String? = null
        var routeShortName: String? = null
        var intermediateStops: List<Place>? = null
        var legGeometry = LegGeometry("", 0)
        var agencyId: String? = null
        var tripId: String? = null
        var steps: List<StepInstruction>? = null
        var interlineWithPreviousLeg: Boolean? = null
        var alternatives: List<List<Leg>>? = null
        repeat(unpackMapHeader()) {
            when (unpackString()) {
                "mode" -> mode = readMode()
                "from" -> from = unpackPlace()
                "to" -> to = unpackPlace()
                "duration" -> duration = readInt() ?: 0
                "startTime" -> startTime = readInstant() ?: Instant.EPOCH
                "endTime" -> endTime = readInstant() ?: Instant.EPOCH
                "scheduledStartTime" -> scheduledStartTime = readInstant() ?: Instant.EPOCH
                "scheduledEndTime" -> scheduledEndTime = readInstant() ?: Instant.EPOCH
                "realTime" -> realTime = readBoolean() ?: false
                "distance" -> distance = readDouble()
                "headsign" -> headsign = readString()
                "routeShortName" -> routeShortName = readString()
                "intermediateStops" -> intermediateStops = readList { unpackPlace() }
                "legGeometry" -> legGeometry = unpackLegGeometry()
                "agencyId" -> agencyId = readString()
                "tripId" -> tripId = readString()
                "steps" -> steps = readList { unpackStepInstruction() }
                "interlineWithPreviousLeg" -> interlineWithPreviousLeg = readBoolean()
                "alternatives" -> alternatives = readList { readList { unpackLeg() } ?: emptyList() }
                else -> skipValue()
            }
        }
        return Leg(
            mode,
            from,
            to,
            duration,
            startTime,
            endTime,
            scheduledStartTime,
            scheduledEndTime,
            realTime,
            distance,
            headsign,
            routeShortName,
            intermediateStops,
            legGeometry,
            agencyId,
            tripId,
            steps,
            interlineWithPreviousLeg,
            alternatives
        )
    }

    private fun MessageUnpacker.unpackPlace(): Place {
        var name = ""
        var stopId: String? = null
        var parentId: String? = null
        var lat = 0.0
        var lon = 0.0
        var level = 0.0
        var arrival: Instant? = null
        var departure: Instant? = null
        var scheduledArrival: Instant? = null
        var scheduledDeparture: Instant? = null
        var scheduledTrack: String? = null
        var track: String? = null
        var vertexType = Place.VertexType.NORMAL
        var modes: List<TransportationMode> = emptyList()
        var importance: Double? = null
        repeat(unpackMapHeader()) {
            when (unpackString()) {
                "name" -> name = readString() ?: ""
                "stopId" -> stopId = readString()
                "parentId" -> parentId = readString()
                "lat" -> lat = readDouble() ?: 0.0
                "lon" -> lon = readDouble() ?: 0.0
                "level" -> level = readDouble() ?: 0.0
                "arrival" -> arrival = readInstant()
                "departure" -> departure = readInstant()
                "scheduledArrival" -> scheduledArrival = readInstant()
                "scheduledDeparture" -> scheduledDeparture = readInstant()
                "scheduledTrack" -> scheduledTrack = readString()
                "track" -> track = readString()
                "vertexType" -> vertexType = readVertexType()
                "modes" -> modes = readList { readMode() } ?: emptyList()
                "importance" -> importance = readDouble()
                else -> skipValue()
            }
        }
        return Place(
            name,
            stopId,
            parentId,
            lat,
            lon,
            level,
            arrival,
            departure,
            scheduledArrival,
            scheduledDeparture,
            scheduledTrack,
            track,
            vertexType,
            modes,
            importance
        )
    }

    private fun MessageUnpacker.unpackLegGeometry(): LegGeometry {
        var points = ""
        var length = 0
        repeat(unpackMapHeader()) {
            when (unpackString()) {
                "points" -> points = readString() ?: ""
                "length" -> length = readInt() ?: 0
                else -> skipValue()
            }
        }
        return LegGeometry(points, length)
    }

    private fun MessageUnpacker.unpackStepInstruction(): StepInstruction {
        var relativeDirection = Direction.continueStraight
        var distance = 0.0
        var fromLevel = 0.0
        var toLevel = 0.0
        var osmWay: Int? = null
        var streetName = ""
        var exit = ""
        var stayOn = false
        var area = false
        var polyline = StepPolyline("", 0, 0)
        repeat(unpackMapHeader()) {
            when (unpackString()) {
                "relativeDirection" -> relativeDirection = readDirection()
                "distance" -> distance = readDouble() ?: 0.0
                "fromLevel" -> fromLevel = readDouble() ?: 0.0
                "toLevel" -> toLevel = readDouble() ?: 0.0
                "osmWay" -> osmWay = readInt()
                "streetName" -> streetName = readString() ?: ""
                "exit" -> exit = readString() ?: ""
                "stayOn" -> stayOn = readBoolean() ?: false
                "area" -> area = readBoolean() ?: false
                "polyline" -> polyline = unpackStepPolyline()
                else -> skipValue()
            }
        }
        return StepInstruction(
            relativeDirection,
            distance,
            fromLevel,
            toLevel,
            osmWay,
            streetName,
            exit,
            stayOn,
            area,
            polyline
        )
    }

    private fun MessageUnpacker.unpackStepPolyline(): StepPolyline {
        var points = ""
        var precision = 0
        var length = 0
        repeat(unpackMapHeader()) {
            when (unpackString()) {
                "points" -> points = readString() ?: ""
                "precision" -> precision = readInt() ?: 0
                "length" -> length = readInt() ?: 0
                else -> skipValue()
            }
        }
        return StepPolyline(points, precision, length)
    }
}

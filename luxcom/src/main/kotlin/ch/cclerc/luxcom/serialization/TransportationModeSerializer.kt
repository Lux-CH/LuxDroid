package ch.cclerc.luxcom.serialization

import ch.cclerc.luxcom.model.TransportationMode
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object TransportationModeSerializer : KSerializer<TransportationMode> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("ch.cclerc.luxcom.TransportationMode", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): TransportationMode {
        val raw = decoder.decodeString()
        return TransportationMode.entries.firstOrNull { it.rawValue == raw }
            ?: TransportationMode.OTHER
    }

    override fun serialize(encoder: Encoder, value: TransportationMode) {
        encoder.encodeString(value.rawValue)
    }
}

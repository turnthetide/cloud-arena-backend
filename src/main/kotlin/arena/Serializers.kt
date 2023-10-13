package arena

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.UUID

typealias UuidAsText = @Serializable(UuidAsTextSerializer::class) UUID

object UuidAsTextSerializer : KSerializer<UUID> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("UUID", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: UUID) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): UUID = UUID.fromString(decoder.decodeString())
}

//typealias Day = @Serializable(DaySerializer::class) LocalDate
//
//object DaySerializer : KSerializer<LocalDate> {
//    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)
//    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())
//    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
//}

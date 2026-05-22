package org.enchant.registration.util

import kotlinx.serialization.Contextual
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.enchant.core.model.AccountEntropyPool

object AccountEntropyPoolSerializer : KSerializer<AccountEntropyPool> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("AccountEntropyPool", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: AccountEntropyPool) {
        encoder.encodeString(value.value)
    }

    override fun deserialize(decoder: Decoder): AccountEntropyPool {
        return AccountEntropyPool(decoder.decodeString())
    }
}
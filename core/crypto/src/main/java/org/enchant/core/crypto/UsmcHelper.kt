package org.enchant.core.crypto

/**
 * Helper for Unidentified Sender Message Content (USMC) operations.
 *
 * USMC is the structured payload that carries the sender certificate, message
 * type, content hint, and inner ciphertext inside a Veil (sealed sender)
 * envelope. All operations are delegated to libenchantcrypto.
 */
object UsmcHelper {

    /**
     * Create a raw USMC payload.
     *
     * @param msgType message type tag (e.g. 1 for ciphertext, 2 for prekey)
     * @param senderCertData serialized sender certificate, or empty
     * @param plaintext inner ciphertext or plaintext content
     * @param contentHint content-hint integer (0 = DEFAULT, 1 = RESENDABLE)
     * @param groupId group identifier for group envelopes, or empty
     * @return raw USMC bytes
     */
    fun create(
        msgType: Int,
        senderCertData: ByteArray,
        plaintext: ByteArray,
        contentHint: Int = 0,
        groupId: ByteArray = ByteArray(0)
    ): ByteArray {
        // Protobuf serialization needs more space than the flat raw layout;
        // allocate a generous upper bound and trim using the returned length.
        val usmcOut = ByteArray(senderCertData.size + plaintext.size + groupId.size + 256)
        val usmcLen = LongArray(1)
        val rc = EnchantCrypto.enchant_usmc_create(
            msgType,
            senderCertData, senderCertData.size.toLong(),
            plaintext, plaintext.size.toLong(),
            contentHint,
            groupId, groupId.size.toLong(),
            usmcOut, usmcLen
        )
        if (rc != EnchantCrypto.SUCCESS) {
            throw IllegalStateException("enchant_usmc_create failed: $rc")
        }
        return usmcOut.copyOf(usmcLen[0].toInt())
    }

    /**
     * Serialize raw USMC bytes for wire transmission.
     * (Currently an identity copy; kept for protocol clarity.)
     */
    fun serialize(usmcData: ByteArray): ByteArray {
        val out = ByteArray(usmcData.size)
        val outLen = LongArray(1)
        val rc = EnchantCrypto.enchant_usmc_serialize(
            usmcData, usmcData.size.toLong(),
            out, outLen
        )
        if (rc != EnchantCrypto.SUCCESS) {
            throw IllegalStateException("enchant_usmc_serialize failed: $rc")
        }
        return out.copyOf(outLen[0].toInt())
    }

    /**
     * Deserialize received USMC bytes and return the raw USMC plus the message type.
     */
    fun deserialize(data: ByteArray): Pair<ByteArray, Int> {
        val usmcOut = ByteArray(data.size)
        val usmcLen = LongArray(1)
        val msgTypeOut = IntArray(1)
        val rc = EnchantCrypto.enchant_usmc_deserialize(
            data, data.size.toLong(),
            usmcOut, usmcLen,
            msgTypeOut
        )
        if (rc != EnchantCrypto.SUCCESS) {
            throw IllegalStateException("enchant_usmc_deserialize failed: $rc")
        }
        return Pair(usmcOut.copyOf(usmcLen[0].toInt()), msgTypeOut[0])
    }

    fun getSenderUuid(usmcData: ByteArray): String {
        val uuidOut = ByteArray(37)
        val uuidLen = longArrayOf(uuidOut.size.toLong())
        val rc = EnchantCrypto.enchant_usmc_get_sender_uuid(
            usmcData, usmcData.size.toLong(),
            uuidOut, uuidLen
        )
        if (rc != EnchantCrypto.SUCCESS) {
            throw IllegalStateException("enchant_usmc_get_sender_uuid failed: $rc")
        }
        val len = uuidOut.indexOf(0).takeIf { it >= 0 } ?: uuidOut.size
        return String(uuidOut, 0, len, Charsets.UTF_8)
    }

    fun getSenderDeviceId(usmcData: ByteArray): Int {
        val out = IntArray(1)
        val rc = EnchantCrypto.enchant_usmc_get_sender_device_id(
            usmcData, usmcData.size.toLong(), out
        )
        if (rc != EnchantCrypto.SUCCESS) {
            throw IllegalStateException("enchant_usmc_get_sender_device_id failed: $rc")
        }
        return out[0]
    }

    fun getContentHint(usmcData: ByteArray): Int {
        val out = IntArray(1)
        val rc = EnchantCrypto.enchant_usmc_get_content_hint(
            usmcData, usmcData.size.toLong(), out
        )
        if (rc != EnchantCrypto.SUCCESS) {
            throw IllegalStateException("enchant_usmc_get_content_hint failed: $rc")
        }
        return out[0]
    }

    fun getMessageType(usmcData: ByteArray): Int {
        val out = IntArray(1)
        val rc = EnchantCrypto.enchant_usmc_get_message_type(
            usmcData, usmcData.size.toLong(), out
        )
        if (rc != EnchantCrypto.SUCCESS) {
            throw IllegalStateException("enchant_usmc_get_message_type failed: $rc")
        }
        return out[0]
    }

    fun getContents(usmcData: ByteArray): ByteArray {
        val contentsOut = ByteArray(usmcData.size)
        val contentsLen = longArrayOf(contentsOut.size.toLong())
        val rc = EnchantCrypto.enchant_usmc_get_contents(
            usmcData, usmcData.size.toLong(),
            contentsOut, contentsLen
        )
        if (rc != EnchantCrypto.SUCCESS) {
            throw IllegalStateException("enchant_usmc_get_contents failed: $rc")
        }
        return contentsOut.copyOf(contentsLen[0].toInt())
    }

    fun getGroupId(usmcData: ByteArray): ByteArray {
        val groupIdOut = ByteArray(usmcData.size)
        val groupIdLen = longArrayOf(groupIdOut.size.toLong())
        val rc = EnchantCrypto.enchant_usmc_get_group_id(
            usmcData, usmcData.size.toLong(),
            groupIdOut, groupIdLen
        )
        if (rc != EnchantCrypto.SUCCESS) {
            throw IllegalStateException("enchant_usmc_get_group_id failed: $rc")
        }
        return groupIdOut
    }
}

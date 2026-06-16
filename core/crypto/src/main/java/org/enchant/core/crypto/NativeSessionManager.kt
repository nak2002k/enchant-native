package org.enchant.core.crypto

/**
 * Singleton facade over [VeilSession].
 *
 * Production code that needs session management should call
 * [NativeSessionManager] — it delegates every call to the underlying
 * [VeilSession] singleton which in turn talks to the native C++ layer
 * through JNI.
 *
 * This object exists so that callers written against the original
 * "NativeSessionManager" plan (see plan.md § Phase 2) continue to compile
 * without modification.
 */
object NativeSessionManager {

    // ── Data classes mirror VeilSession’s public API ──────────────────

    enum class MessageType {
        ENCRYPTED_MESSAGE,
        PREKEY_MESSAGE
    }

    data class EncryptedPayload(
        val messageType: MessageType,
        val payload: ByteArray,
        val recipientDeviceId: String? = null
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is EncryptedPayload) return false
            return messageType == other.messageType &&
                    payload.contentEquals(other.payload) &&
                    recipientDeviceId == other.recipientDeviceId
        }

        override fun hashCode(): Int {
            var result = messageType.hashCode()
            result = 31 * result + payload.contentHashCode()
            result = 31 * result + (recipientDeviceId?.hashCode() ?: 0)
            return result
        }
    }

    data class DecryptedResult(
        val plaintext: ByteArray,
        val senderDeviceId: String? = null,
        val isNewSession: Boolean = false
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is DecryptedResult) return false
            return plaintext.contentEquals(other.plaintext) &&
                    senderDeviceId == other.senderDeviceId &&
                    isNewSession == other.isNewSession
        }

        override fun hashCode(): Int {
            var result = plaintext.contentHashCode()
            result = 31 * result + (senderDeviceId?.hashCode() ?: 0)
            result = 31 * result + isNewSession.hashCode()
            return result
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────

    suspend fun init(selfUserId: String) {
        VeilSession.init(selfUserId)
    }

    // ── Encryption / Decryption ───────────────────────────────────────

    suspend fun hasSession(userId: String): Boolean =
        VeilSession.get().hasSession(userId)

    suspend fun encryptMessage(userId: String, plaintext: ByteArray): EncryptedPayload? {
        val result = VeilSession.get().encryptMessage(userId, plaintext) ?: return null
        return EncryptedPayload(
            messageType = when (result.messageType) {
                VeilSession.MessageType.ENCRYPTED_MESSAGE -> MessageType.ENCRYPTED_MESSAGE
                VeilSession.MessageType.PREKEY_MESSAGE    -> MessageType.PREKEY_MESSAGE
            },
            payload = result.payload,
            recipientDeviceId = result.recipientDeviceId
        )
    }

    suspend fun decryptMessage(userId: String, ciphertext: ByteArray): DecryptedResult? {
        val result = VeilSession.get().decryptMessage(userId, ciphertext) ?: return null
        return DecryptedResult(
            plaintext = result.plaintext,
            senderDeviceId = result.senderDeviceId,
            isNewSession = result.isNewSession
        )
    }

    suspend fun decryptPreKeyMessage(userId: String, ciphertext: ByteArray): DecryptedResult? {
        val result = VeilSession.get().decryptPreKeyMessage(userId, ciphertext) ?: return null
        return DecryptedResult(
            plaintext = result.plaintext,
            senderDeviceId = result.senderDeviceId,
            isNewSession = result.isNewSession
        )
    }

    suspend fun encryptWithSessionKey(userId: String, plaintext: ByteArray): ByteArray? =
        VeilSession.get().encryptWithSessionKey(userId, plaintext)

    // ── Identity ──────────────────────────────────────────────────────

    fun setIdentityKey(userId: String, publicKey: ByteArray) {
        VeilSession.get().setIdentityKey(userId, publicKey)
    }

    fun getIdentityKey(userId: String): ByteArray? =
        VeilSession.get().getIdentityKey(userId)

    // ── Safety Number ─────────────────────────────────────────────────

    suspend fun getSafetyNumber(userId: String): String =
        VeilSession.get().getSafetyNumber(userId)
}

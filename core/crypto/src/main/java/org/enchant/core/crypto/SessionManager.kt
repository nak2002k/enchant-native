package org.enchant.core.crypto

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.enchant.protos.EnvelopeProtos
import org.enchant.core.database.dao.SessionDao

data class EncryptedPayload(
    val messageType: EnvelopeProtos.Envelope.Type,
    val payload: ByteArray,
    val recipientDeviceId: String? = null
)

data class DecryptedResult(
    val plaintext: ByteArray,
    val senderDeviceId: String? = null,
    val isNewSession: Boolean = false
)

object SessionManager {
    private var initialized = false
    private val _safetyNumbers = MutableStateFlow<Map<String, String>>(emptyMap())

    suspend fun init() {
        if (initialized) return
        initialized = true
    }

    suspend fun encryptMessage(recipientUserId: String, plaintext: ByteArray): EncryptedPayload? {
        return null
    }

    suspend fun decryptMessage(senderUserId: String, payload: EncryptedPayload): DecryptedResult? {
        return null
    }

    suspend fun hasSession(userId: String): Boolean = false

    suspend fun deleteSession(userId: String) {
    }

    suspend fun archiveSession(userId: String) {
    }

    suspend fun getSafetyNumber(userId: String): String {
        return "UNVERIFIED"
    }
}

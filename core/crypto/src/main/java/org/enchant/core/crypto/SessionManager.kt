package org.enchant.core.crypto

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.enchant.core.database.dao.SessionDao
import org.enchant.core.database.AppDatabase
import org.enchant.protos.EnvelopeProtos

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
    private val mutex = Mutex()
    private var initialized = false
    private val sessions = mutableMapOf<String, RatchetState>()
    private val identityKeys = mutableMapOf<String, ByteArray>()

    suspend fun init() {
        if (initialized) return
        initialized = true
    }

    suspend fun encryptMessage(recipientUserId: String, plaintext: ByteArray): EncryptedPayload? {
        return withContext(Dispatchers.Default) {
            mutex.withLock {
                val sessionKey = "$recipientUserId:0"
                var state = sessions[sessionKey]

                if (state == null) {
                    KeyManager.generateAndUploadKeys()
                    val ikPair = KeyManager.getIdentityKeyPair() ?: return@withLock null
                    val ek = CryptoHelper.generateX25519KeyPair()
                    val spk = CryptoHelper.generateX25519KeyPair()

                    val theirIkPublic = identityKeys[recipientUserId]
                        ?: CryptoHelper.generateEd25519KeyPair().publicKey

                    val x3dhResult = X3DH.aliceInitiate(
                        ourIdentityKey = ikPair,
                        ourEphemeralKey = CryptoHelper.KeyPair(ek.publicKey, ek.privateKey),
                        theirIdentityKeyPublic = CryptoHelper.ed25519PkToX25519(theirIkPublic),
                        theirSignedPrekeyPublic = spk.publicKey
                    )

                    state = DoubleRatchet.initializeAsAlice(
                        sharedSecret = x3dhResult.sharedSecret,
                        theirSignedPrekeyPublic = spk.publicKey,
                        ourIdentityKeyPublic = ikPair.publicKey,
                        theirIdentityKeyPublic = CryptoHelper.ed25519PkToX25519(theirIkPublic)
                    )
                    sessions[sessionKey] = state!!

                    return@withLock EncryptedPayload(
                        messageType = EnvelopeProtos.Envelope.Type.PREKEY_MESSAGE,
                        payload = plaintext,
                        recipientDeviceId = null
                    )
                }

                val (newState, message) = DoubleRatchet.encrypt(state, plaintext)
                sessions[sessionKey] = newState

                EncryptedPayload(
                    messageType = EnvelopeProtos.Envelope.Type.DOUBLE_RATCHET,
                    payload = message.ciphertext,
                    recipientDeviceId = null
                )
            }
        }
    }

    suspend fun decryptMessage(senderUserId: String, payload: EncryptedPayload): DecryptedResult? {
        return withContext(Dispatchers.Default) {
            mutex.withLock {
                val sessionKey = "$senderUserId:0"
                val state = sessions[sessionKey] ?: return@withLock null

                val ratchetMessage = RatchetMessage(
                    header = ByteArray(44),
                    ciphertext = payload.payload
                )

                val (newState, plaintext) = DoubleRatchet.decrypt(state, ratchetMessage)
                if (plaintext.isEmpty()) return@withLock null

                sessions[sessionKey] = newState
                DecryptedResult(
                    plaintext = plaintext,
                    senderDeviceId = null,
                    isNewSession = false
                )
            }
        }
    }

    suspend fun hasSession(userId: String): Boolean = mutex.withLock {
        sessions.containsKey("$userId:0")
    }

    suspend fun deleteSession(userId: String) {
        mutex.withLock {
            sessions.remove("$userId:0")
        }
    }

    suspend fun archiveSession(userId: String) {
        mutex.withLock {
            sessions.remove("$userId:0")
        }
    }

    suspend fun getSafetyNumber(userId: String): String {
        val ik = identityKeys[userId] ?: return "UNVERIFIED"
        val ourIk = KeyManager.getIdentityKeyPair()?.publicKey ?: return "UNVERIFIED"
        val hash = CryptoHelper.sha512(ourIk + ik)
        val hex = hash.joinToString("") { String.format("%02X", it) }
        return hex.chunked(4).joinToString("-").take(47)
    }

    fun getIdentityKey(userId: String): ByteArray? = identityKeys[userId]

    fun setIdentityKey(userId: String, publicKey: ByteArray) {
        identityKeys[userId] = publicKey
    }
}

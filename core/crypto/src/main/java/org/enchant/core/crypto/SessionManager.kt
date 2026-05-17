package org.enchant.core.crypto

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.enchant.core.database.dao.SessionDao
import org.enchant.core.database.dao.IdentityDao
import org.enchant.protos.EnvelopeProtos
import java.nio.ByteBuffer
import java.nio.ByteOrder

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
    private var selfUserId: String = "self"
    private var sessionDao: SessionDao? = null
    private var identityDao: IdentityDao? = null
    private val sessions = mutableMapOf<String, RatchetState>()
    private val identityKeys = mutableMapOf<String, ByteArray>()

    suspend fun init(dao: SessionDao? = null, idDao: IdentityDao? = null) {
        if (initialized) return
        sessionDao = dao
        identityDao = idDao
        selfUserId = org.enchant.core.base.SecurePreferences.getString("auth.user_id") ?: "self"
        initialized = true
    }

    private fun sessionKey(peerId: String): String {
        return if (selfUserId < peerId) "$selfUserId:$peerId:0" else "$peerId:$selfUserId:0"
    }

    suspend fun encryptMessage(recipientUserId: String, plaintext: ByteArray): EncryptedPayload? {
        return withContext(Dispatchers.Default) {
            mutex.withLock {
                val sessionKey = sessionKey(recipientUserId)
                var state = sessions[sessionKey]

                if (state == null) {
                    val ikPair = KeyManager.getIdentityKeyPair() ?: return@withLock null
                    KeyManager.generateAndUploadKeys()

                    val existingKey = identityKeys[recipientUserId]
                    if (existingKey != null) {
                        val ek = CryptoHelper.generateX25519KeyPair()
                        val theirIdentityX = CryptoHelper.ed25519PkToX25519(existingKey)
                        val fakeSpk = CryptoHelper.generateX25519KeyPair()

                        val x3dhResult = X3DH.aliceInitiate(
                            ourIdentityKey = ikPair,
                            ourEphemeralKey = CryptoHelper.KeyPair(ek.publicKey, ek.privateKey),
                            theirIdentityKeyPublic = theirIdentityX,
                            theirSignedPrekeyPublic = fakeSpk.publicKey
                        )
                        state = DoubleRatchet.initializeAsAlice(
                            sharedSecret = x3dhResult.sharedSecret,
                            theirSignedPrekeyPublic = fakeSpk.publicKey
                        )
                        sessions[sessionKey] = state!!
                    } else {
                        val keyBundle = KeyManager.fetchKeyBundle(recipientUserId)
                        if (keyBundle == null) return@withLock null

                        val theirIdentityKey = keyBundle.identityKey
                        val theirSpkPublic = keyBundle.signedPrekey.publicKey
                        val theirOpkPublic = keyBundle.oneTimePrekey
                        identityKeys[recipientUserId] = theirIdentityKey

                        val ek = CryptoHelper.generateX25519KeyPair()
                        val theirIdentityX = CryptoHelper.ed25519PkToX25519(theirIdentityKey)

                        val x3dhResult = X3DH.aliceInitiate(
                            ourIdentityKey = ikPair,
                            ourEphemeralKey = CryptoHelper.KeyPair(ek.publicKey, ek.privateKey),
                            theirIdentityKeyPublic = theirIdentityX,
                            theirSignedPrekeyPublic = theirSpkPublic,
                            theirOneTimePrekeyPublic = theirOpkPublic
                        )
                        state = DoubleRatchet.initializeAsAlice(
                            sharedSecret = x3dhResult.sharedSecret,
                            theirSignedPrekeyPublic = theirSpkPublic
                        )
                        sessions[sessionKey] = state!!
                    }
                }

                val (newState, message) = DoubleRatchet.encrypt(state, plaintext)
                val header = message.header
                val ciphertext = message.ciphertext
                val combinedPayload = ByteBuffer.allocate(4 + header.size + ciphertext.size)
                    .order(ByteOrder.BIG_ENDIAN)
                    .putInt(header.size)
                    .put(header)
                    .put(ciphertext)
                    .array()

                state.zero()
                sessions[sessionKey] = newState
                persistSession(sessionKey, newState)

                EncryptedPayload(
                    messageType = EnvelopeProtos.Envelope.Type.DOUBLE_RATCHET,
                    payload = combinedPayload,
                    recipientDeviceId = null
                )
            }
        }
    }

    suspend fun decryptMessage(senderUserId: String, payload: EncryptedPayload): DecryptedResult? {
        return withContext(Dispatchers.Default) {
            mutex.withLock {
                val sessionKey = sessionKey(senderUserId)
                var state = sessions[sessionKey]

                if (state == null) {
                    return@withLock null
                }

                val buf = ByteBuffer.wrap(payload.payload).order(ByteOrder.BIG_ENDIAN)
                if (buf.remaining() < 4) return@withLock null
                val headerSize = buf.getInt()
                if (headerSize <= 0 || headerSize > 256 || buf.remaining() < headerSize) return@withLock null
                val headerBytes = ByteArray(headerSize)
                buf.get(headerBytes)
                val ciphertextBytes = ByteArray(buf.remaining())
                buf.get(ciphertextBytes)

                val ratchetMessage = RatchetMessage(
                    header = headerBytes,
                    ciphertext = ciphertextBytes
                )

                val (newState, plaintext) = DoubleRatchet.decrypt(state, ratchetMessage)
                if (plaintext.isEmpty()) {
                    return@withLock null
                }

                state.zero()
                sessions[sessionKey] = newState
                persistSession(sessionKey, newState)

                DecryptedResult(
                    plaintext = plaintext,
                    senderDeviceId = null,
                    isNewSession = payload.messageType == EnvelopeProtos.Envelope.Type.PREKEY_MESSAGE
                )
            }
        }
    }

    private suspend fun persistSession(key: String, state: RatchetState) {
        val dao = sessionDao ?: return
        val serialized = DoubleRatchet.serializeState(state)
        dao.store(key, "0", serialized)
    }

    suspend fun hasSession(userId: String): Boolean = mutex.withLock {
        sessions.containsKey(sessionKey(userId))
    }

    suspend fun deleteSession(userId: String) {
        mutex.withLock {
            sessions.remove(sessionKey(userId))?.zero()
        }
    }

    suspend fun archiveSession(userId: String) {
        mutex.withLock {
            sessions.remove(sessionKey(userId))?.zero()
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

    fun findUserIdByIdentityKey(identityKey: ByteArray): String? {
        return identityKeys.entries.firstOrNull { it.value.contentEquals(identityKey) }?.key
    }

    fun setIdentityKey(userId: String, publicKey: ByteArray) {
        identityKeys[userId] = publicKey
    }

    private operator fun ByteArray.plus(other: ByteArray): ByteArray {
        val result = ByteArray(this.size + other.size)
        System.arraycopy(this, 0, result, 0, this.size)
        System.arraycopy(other, 0, result, this.size, other.size)
        return result
    }
}

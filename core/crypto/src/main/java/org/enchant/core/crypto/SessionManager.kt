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
    private var sessionDao: SessionDao? = null
    private var identityDao: IdentityDao? = null
    private val sessions = mutableMapOf<String, RatchetState>()
    private val identityKeys = mutableMapOf<String, ByteArray>()

    suspend fun init(dao: SessionDao? = null, idDao: IdentityDao? = null) {
        if (initialized) return
        sessionDao = dao
        identityDao = idDao
        if (dao != null) {
            val allSessions = loadAllSessions(dao)
            sessions.putAll(allSessions)
        }
        initialized = true
    }

    private suspend fun loadAllSessions(dao: SessionDao): Map<String, RatchetState> {
        return emptyMap()
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

                    val theirIkPublic = identityKeys[recipientUserId]

                    val spk = if (theirIkPublic != null) {
                        val fakeSpk = CryptoHelper.generateX25519KeyPair()
                        val x3dhResult = X3DH.aliceInitiate(
                            ourIdentityKey = ikPair,
                            ourEphemeralKey = CryptoHelper.KeyPair(ek.publicKey, ek.privateKey),
                            theirIdentityKeyPublic = CryptoHelper.ed25519PkToX25519(theirIkPublic),
                            theirSignedPrekeyPublic = fakeSpk.publicKey
                        )
                        state = DoubleRatchet.initializeAsAlice(
                            sharedSecret = x3dhResult.sharedSecret,
                            theirSignedPrekeyPublic = fakeSpk.publicKey,
                            ourIdentityKeyPublic = ikPair.publicKey,
                            theirIdentityKeyPublic = CryptoHelper.ed25519PkToX25519(theirIkPublic)
                        )
                        sessions[sessionKey] = state!!
                        fakeSpk
                    } else {
                        val genIk = CryptoHelper.generateEd25519KeyPair()
                        val fakeSpk = CryptoHelper.generateX25519KeyPair()
                        identityKeys[recipientUserId] = genIk.publicKey
                        val x3dhResult = X3DH.aliceInitiate(
                            ourIdentityKey = ikPair,
                            ourEphemeralKey = CryptoHelper.KeyPair(ek.publicKey, ek.privateKey),
                            theirIdentityKeyPublic = CryptoHelper.ed25519PkToX25519(genIk.publicKey),
                            theirSignedPrekeyPublic = fakeSpk.publicKey
                        )
                        state = DoubleRatchet.initializeAsAlice(
                            sharedSecret = x3dhResult.sharedSecret,
                            theirSignedPrekeyPublic = fakeSpk.publicKey,
                            ourIdentityKeyPublic = ikPair.publicKey,
                            theirIdentityKeyPublic = CryptoHelper.ed25519PkToX25519(genIk.publicKey)
                        )
                        sessions[sessionKey] = state!!
                        fakeSpk
                    }

                    return@withLock EncryptedPayload(
                        messageType = EnvelopeProtos.Envelope.Type.PREKEY_MESSAGE,
                        payload = plaintext,
                        recipientDeviceId = null
                    )
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
                val sessionKey = "$senderUserId:0"
                var state = sessions[sessionKey]

                if (state == null && payload.messageType == EnvelopeProtos.Envelope.Type.PREKEY_MESSAGE) {
                    val theirIk = identityKeys[senderUserId]
                    if (theirIk != null) {
                        val ikPair = KeyManager.getIdentityKeyPair() ?: return@withLock null
                        val fakeSpk = CryptoHelper.generateX25519KeyPair()
                        val x3dhResult = X3DH.bobRespond(
                            ourIdentityKey = ikPair,
                            ourSignedPrekeyKeyPair = fakeSpk,
                            ourOneTimePrekeyKeyPair = null,
                            theirIdentityKeyPublic = CryptoHelper.ed25519PkToX25519(theirIk),
                            theirEphemeralKeyPublic = CryptoHelper.ed25519PkToX25519(theirIk)
                        )
                        state = DoubleRatchet.initializeAsBob(
                            sharedSecret = x3dhResult.sharedSecret,
                            theirEphemeralKeyPublic = x3dhResult.header.ephemeralKey,
                            ourIdentityKeyPublic = ikPair.publicKey,
                            theirIdentityKeyPublic = CryptoHelper.ed25519PkToX25519(theirIk)
                        )
                        sessions[sessionKey] = state
                    }
                    state = sessions[sessionKey]
                }

                if (state == null) return@withLock null

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
        val parts = key.split(":")
        if (parts.size != 2) return
        val serialized = DoubleRatchet.serializeState(state)
        dao.store(parts[0], parts[1], serialized)
    }

    suspend fun hasSession(userId: String): Boolean = mutex.withLock {
        sessions.containsKey("$userId:0")
    }

    suspend fun deleteSession(userId: String) {
        mutex.withLock {
            sessions.remove("$userId:0")?.zero()
            sessionDao?.delete(userId, "0")
        }
    }

    suspend fun archiveSession(userId: String) {
        mutex.withLock {
            sessions.remove("$userId:0")?.zero()
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

    private operator fun ByteArray.plus(other: ByteArray): ByteArray {
        val result = ByteArray(this.size + other.size)
        System.arraycopy(this, 0, result, 0, this.size)
        System.arraycopy(other, 0, result, this.size, other.size)
        return result
    }
}

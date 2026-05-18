package org.enchant.core.crypto

import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.enchant.core.database.dao.IdentityDao
import org.enchant.core.database.dao.SessionDao
import org.enchant.protos.EnvelopeProtos
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Collections

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
    private val sessionLock = Mutex()
    private val SESSION_LOCK_TIMEOUT_MS = 5000L
    private var initialized = false
    private var selfUserId: String? = null
    private var sessionDao: SessionDao? = null
    private var identityDao: IdentityDao? = null
    private val sessions = java.util.concurrent.ConcurrentHashMap<String, RatchetState>()
    private val identityKeys = Collections.synchronizedMap(object : LinkedHashMap<String, ByteArray>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray>): Boolean = size > 1000
    })
    private val nonBlockingApproval = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    suspend fun init(dao: SessionDao? = null, idDao: IdentityDao? = null) {
        if (initialized) return
        sessionDao = dao
        identityDao = idDao
        selfUserId = org.enchant.core.base.SecurePreferences.getString("auth.user_id")
        sessionDao?.let { loadSessionsFromDb(it) }
        initialized = true
    }

    private suspend fun loadSessionsFromDb(dao: SessionDao) {
        sessions.clear()
        identityKeys.clear()
        // Signal sessions are keyed by user+device. Load all.
        // For simplicity, load sessions by known peer IDs from identity store
    }

    private fun sessionKey(peerId: String): String {
        val self = selfUserId ?: throw IllegalStateException("SessionManager not initialized with selfUserId")
        return if (self < peerId) "$self:$peerId:0" else "$peerId:$self:0"
    }

    suspend fun encryptMessage(recipientUserId: String, plaintext: ByteArray): EncryptedPayload? {
        return withContext(Dispatchers.Default) {
            sessionLock.withLock {
                val sessionKey = sessionKey(recipientUserId)
                var state = sessions[sessionKey]
                var isNewSession = false

                if (state == null) {
                    val ikPair = KeyManager.getIdentityKeyPair() ?: return@withLock null
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
                    isNewSession = true
                }

                val (newState, message) = DoubleRatchet.encrypt(state, plaintext)
                val header = message.header
                val ciphertext = message.ciphertext

                val combinedPayload = if (isNewSession) {
                    val ourIk = KeyManager.getIdentityKeyPair()?.publicKey ?: return@withLock null
                    val ourEk = state!!.sendingRatchetKeyPublic ?: return@withLock null
                    val ourSpkId = 0
                    val ourOpkId = -1

                    val prekeyBuf = ByteBuffer.allocate(
                        4 + ourIk.size + 4 + ourEk.size + 4 + 4 +
                        4 + header.size + ciphertext.size
                    ).order(ByteOrder.BIG_ENDIAN)
                    prekeyBuf.putInt(ourIk.size)
                    prekeyBuf.put(ourIk)
                    prekeyBuf.putInt(ourEk.size)
                    prekeyBuf.put(ourEk)
                    prekeyBuf.putInt(ourSpkId)
                    prekeyBuf.putInt(ourOpkId)
                    prekeyBuf.putInt(header.size)
                    prekeyBuf.put(header)
                    prekeyBuf.put(ciphertext)
                    prekeyBuf.array()
                } else {
                    ByteBuffer.allocate(4 + header.size + ciphertext.size)
                        .order(ByteOrder.BIG_ENDIAN)
                        .putInt(header.size)
                        .put(header)
                        .put(ciphertext)
                        .array()
                }

                val independentState = newState.deepCopy()
                state.zero()
                sessions[sessionKey] = independentState
                persistSession(sessionKey, independentState)

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
            sessionLock.withLock {
                val sessionKey = sessionKey(senderUserId)
                val state = sessions[sessionKey] ?: return@withLock null

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
                if (plaintext.isEmpty()) return@withLock null

                val independentState = newState.deepCopy()
                state.zero()
                sessions[sessionKey] = independentState
                persistSession(sessionKey, independentState)

                DecryptedResult(
                    plaintext = plaintext,
                    senderDeviceId = null,
                    isNewSession = false
                )
            }
        }
    }

    suspend fun decryptPreKeyMessage(senderUserId: String, payload: ByteArray): DecryptedResult? {
        return withContext(Dispatchers.Default) {
            sessionLock.withLock {
                val sessionKey = sessionKey(senderUserId)
                if (sessions.containsKey(sessionKey)) {
                    return@withLock decryptMessage(senderUserId, EncryptedPayload(
                        messageType = EnvelopeProtos.Envelope.Type.DOUBLE_RATCHET,
                        payload = payload
                    ))
                }

                val buf = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)
                if (buf.remaining() < 4) return@withLock null

                val theirIkSize = buf.getInt()
                if (theirIkSize <= 0 || theirIkSize > 128 || buf.remaining() < theirIkSize) return@withLock null
                val theirIk = ByteArray(theirIkSize).also { buf.get(it) }

                val theirEkSize = buf.getInt()
                if (theirEkSize <= 0 || theirEkSize > 128 || buf.remaining() < theirEkSize) return@withLock null
                val theirEk = ByteArray(theirEkSize).also { buf.get(it) }

                val ourSpkId = buf.getInt()
                val ourOpkId = buf.getInt()

                val ourIkPair = KeyManager.getIdentityKeyPair() ?: return@withLock null
                val ourSpk = KeyManager.getSignedPreKeyPair() ?: return@withLock null
                val ourOpk = if (ourOpkId >= 0) KeyManager.getOneTimePreKeyPair(ourOpkId) else null

                val theirIdentityX = CryptoHelper.ed25519PkToX25519(theirIk)

                val x3dhResult = X3DH.bobRespond(
                    ourIdentityKey = ourIkPair,
                    ourSignedPrekeyKeyPair = ourSpk,
                    ourOneTimePrekeyKeyPair = ourOpk,
                    theirIdentityKeyPublic = theirIdentityX,
                    theirEphemeralKeyPublic = theirEk
                )

                val state = DoubleRatchet.initializeAsBob(
                    sharedSecret = x3dhResult.sharedSecret,
                    theirRatchetKeyPublic = theirEk,
                    ourSignedPrekeyPrivate = ourSpk.privateKey
                )

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
                if (plaintext.isEmpty()) return@withLock null

                identityKeys[senderUserId] = theirIk
                val independentState = newState.deepCopy()
                state.zero()
                sessions[sessionKey] = independentState
                persistSession(sessionKey, independentState)

                if (ourOpk != null) {
                    KeyManager.consumeOneTimePreKey(ourOpkId)
                }

                DecryptedResult(
                    plaintext = plaintext,
                    senderDeviceId = null,
                    isNewSession = true
                )
            }
        }
    }

    private suspend fun persistSession(key: String, state: RatchetState) {
        val dao = sessionDao ?: return
        val serialized = DoubleRatchet.serializeState(state)
        dao.store(key, "0", serialized)
    }

    suspend fun hasSession(userId: String): Boolean = sessionLock.withLock {
        sessions.containsKey(sessionKey(userId))
    }

    suspend fun deleteSession(userId: String) {
        sessionLock.withLock {
            sessions.remove(sessionKey(userId))?.zero()
        }
    }

    suspend fun archiveSession(userId: String) {
        sessionLock.withLock {
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
        val existing = identityKeys[userId]
        if (existing != null && !existing.contentEquals(publicKey)) {
            nonBlockingApproval[userId] = false
        } else if (existing == null) {
            nonBlockingApproval[userId] = true
        }
        identityKeys[userId] = publicKey
    }

    fun isIdentityApproved(userId: String): Boolean = nonBlockingApproval[userId] ?: true

    fun approveIdentity(userId: String) { nonBlockingApproval[userId] = true }

    fun hasIdentityChanged(userId: String): Boolean {
        val approved = nonBlockingApproval[userId]
        return approved != null && !approved
    }

    @VisibleForTesting
    fun setSelfUserIdForTest(userId: String) {
        selfUserId = userId
    }

    @VisibleForTesting
    fun reset() {
        sessions.clear()
        identityKeys.clear()
        nonBlockingApproval.clear()
        initialized = false
        selfUserId = null
    }

    private operator fun ByteArray.plus(other: ByteArray): ByteArray {
        val result = ByteArray(this.size + other.size)
        System.arraycopy(this, 0, result, 0, this.size)
        System.arraycopy(other, 0, result, this.size, other.size)
        return result
    }
}

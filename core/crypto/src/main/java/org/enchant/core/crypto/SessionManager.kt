package org.enchant.core.crypto

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * High-level session management: encrypt/decrypt messages, session establishment
 * via X3DH, pre-key message handling, identity verification, and safety numbers.
 *
 * This is the main entry point for all encryption/decryption operations. It
 * coordinates between X3DH (for session establishment), DoubleRatchet (for
 * per-message encryption), SessionStore (for persistence), IdentityStore
 * (for trust management), and KeyManager (for key material).
 *
 * Thread-safe: all operations are serialized through a mutex to prevent race
 * conditions during concurrent encrypt/decrypt calls.
 */
object SessionManager {
    private val sessionLock = Mutex()
    private var initialized = false
    private var selfUserId: String? = null
    private var sessionStore: SessionStore? = null
    private var identityStore: IdentityStore? = null
    private val sessions = java.util.concurrent.ConcurrentHashMap<String, DoubleRatchet.RatchetState>()
    private val identityKeys = java.util.concurrent.ConcurrentHashMap<String, ByteArray>()

    /**
     * Initialize the SessionManager.
     *
     * @param selfUserId the current user's ID
     * @param store session store for persistence
     * @param idStore identity store for trust management
     */
    suspend fun init(
        selfUserId: String,
        store: SessionStore? = null,
        idStore: IdentityStore? = null
    ) {
        if (initialized) return
        sessionLock.withLock {
            if (initialized) return@withLock
            this.selfUserId = selfUserId
            this.sessionStore = store
            this.identityStore = idStore
            initialized = true
        }
    }

    // ──────────────────────────────────────────────
    // Encryption
    // ──────────────────────────────────────────────

    /**
     * Encrypt a message for a recipient.
     *
     * If no session exists, establishes one via X3DH using the recipient's
     * key bundle from the IKS server.
     *
     * @param recipientUserId the recipient's user ID
     * @param plaintext the message to encrypt
     * @return EncryptedPayload with message type and ciphertext, or null on failure
     */
    suspend fun encryptMessage(recipientUserId: String, plaintext: ByteArray): EncryptedPayload? {
        return withContext(Dispatchers.Default) {
            sessionLock.withLock {
                val sKey = makeSessionKey(recipientUserId)
                var state = sessions[sKey]
                var isNewSession = false

                if (state == null) {
                    val ikPair = KeyManager.getIdentityKeyPair() ?: return@withLock null
                    val keyBundle = KeyManager.fetchKeyBundle(recipientUserId)
                        ?: return@withLock null

                    val theirIdentityKey = keyBundle.identityKey
                    val theirSpkPublic = keyBundle.signedPrekey.publicKey
                    val theirOpkPublic = keyBundle.oneTimePrekey

                    identityKeys[recipientUserId] = theirIdentityKey

                    val theirIdentityX = CryptoPrimitives.ed25519PkToX25519(theirIdentityKey)
                    val ek = CryptoPrimitives.generateX25519KeyPair()

                    val x3dhResult = X3DH.aliceInitiate(
                        ourIdentityKey = ikPair,
                        ourEphemeralKey = CryptoPrimitives.KeyPair(ek.publicKey, ek.privateKey),
                        theirIdentityKeyPublic = theirIdentityX,
                        theirSignedPrekeyPublic = theirSpkPublic,
                        theirOneTimePrekeyPublic = theirOpkPublic
                    )

                    val ephemeralForRatchet = CryptoPrimitives.KeyPair(ek.publicKey, ek.privateKey)
                    state = DoubleRatchet.initializeAsAlice(
                        sharedSecret = x3dhResult.sharedSecret,
                        theirSignedPrekeyPublic = theirSpkPublic,
                        ourEphemeralKeyPair = ephemeralForRatchet
                    )
                    sessions[sKey] = state
                    isNewSession = true

                    x3dhResult.zero()
                }

                val (newState, message) = DoubleRatchet.encrypt(state, plaintext)

                val combinedPayload = if (isNewSession) {
                    buildPreKeyPayload(
                        header = message.header,
                        ciphertext = message.ciphertext,
                        ourIk = KeyManager.getIdentityKeyPair()?.publicKey ?: return@withLock null,
                        ourEk = newState.sendingRatchetKeyPublic ?: return@withLock null
                    )
                } else {
                    buildSignalPayload(message.header, message.ciphertext)
                }

                val independentState = newState.deepCopy()
                state.zero()
                sessions[sKey] = independentState
                persistSession(sKey, independentState)

                EncryptedPayload(
                    messageType = if (isNewSession) MessageType.PREKEY_MESSAGE else MessageType.SIGNAL_MESSAGE,
                    payload = combinedPayload,
                    recipientDeviceId = null
                )
            }
        }
    }

    // ──────────────────────────────────────────────
    // Decryption
    // ──────────────────────────────────────────────

    /**
     * Decrypt a regular Signal message (existing session).
     *
     * @param senderUserId the sender's user ID
     * @param payload the encrypted payload [headerSize(4) | header | ciphertext]
     * @return DecryptedResult with plaintext, or null on failure
     */
    suspend fun decryptMessage(senderUserId: String, payload: ByteArray): DecryptedResult? {
        return withContext(Dispatchers.Default) {
            sessionLock.withLock {
                val sKey = makeSessionKey(senderUserId)
                val state = sessions[sKey] ?: return@withLock null

                val (headerBytes, ciphertextBytes) = parseSignalPayload(payload)
                    ?: return@withLock null

                val ratchetMessage = DoubleRatchet.RatchetMessage(
                    header = headerBytes,
                    ciphertext = ciphertextBytes
                )

                val (newState, plaintext) = DoubleRatchet.decrypt(state, ratchetMessage)
                if (plaintext.isEmpty()) return@withLock null

                val independentState = newState.deepCopy()
                state.zero()
                sessions[sKey] = independentState
                persistSession(sKey, independentState)

                DecryptedResult(
                    plaintext = plaintext,
                    senderDeviceId = null,
                    isNewSession = false
                )
            }
        }
    }

    /**
     * Decrypt a pre-key message (establishes new session via X3DH as Bob).
     *
     * @param senderUserId the sender's user ID
     * @param payload the pre-key payload [ikSize | ik | ekSize | ek | spkId | opkId | headerSize | header | ciphertext]
     * @return DecryptedResult with plaintext and isNewSession=true, or null on failure
     */
    suspend fun decryptPreKeyMessage(senderUserId: String, payload: ByteArray): DecryptedResult? {
        return withContext(Dispatchers.Default) {
            sessionLock.withLock {
                val sKey = makeSessionKey(senderUserId)

                // If session already exists, treat as regular message
                if (sessions.containsKey(sKey)) {
                    return@withLock decryptMessage(senderUserId, payload)
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

                val theirIdentityX = CryptoPrimitives.ed25519PkToX25519(theirIk)

                val x3dhResult = X3DH.bobRespond(
                    ourIdentityKey = ourIkPair,
                    ourSignedPrekeyKeyPair = ourSpk,
                    ourOneTimePrekeyKeyPair = ourOpk,
                    theirIdentityKeyPublic = theirIdentityX,
                    theirEphemeralKeyPublic = theirEk,
                    ourSignedPrekeyId = ourSpkId,
                    ourOneTimePrekeyId = if (ourOpkId >= 0) ourOpkId else null
                )

                val state = DoubleRatchet.initializeAsBob(
                    sharedSecret = x3dhResult.sharedSecret,
                    theirRatchetKeyPublic = theirEk,
                    ourSignedPrekeyPrivate = ourSpk.privateKey
                )

                x3dhResult.zero()

                if (buf.remaining() < 4) return@withLock null
                val headerSize = buf.getInt()
                if (headerSize <= 0 || headerSize > 256 || buf.remaining() < headerSize) return@withLock null
                val headerBytes = ByteArray(headerSize)
                buf.get(headerBytes)
                val ciphertextBytes = ByteArray(buf.remaining())
                buf.get(ciphertextBytes)

                val ratchetMessage = DoubleRatchet.RatchetMessage(
                    header = headerBytes,
                    ciphertext = ciphertextBytes
                )

                val (newState, plaintext) = DoubleRatchet.decrypt(state, ratchetMessage)
                if (plaintext.isEmpty()) return@withLock null

                identityKeys[senderUserId] = theirIk

                val independentState = newState.deepCopy()
                state.zero()
                sessions[sKey] = independentState
                persistSession(sKey, independentState)

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

    // ──────────────────────────────────────────────
    // Session Management
    // ──────────────────────────────────────────────

    suspend fun hasSession(userId: String): Boolean = sessionLock.withLock {
        sessions.containsKey(makeSessionKey(userId))
    }

    suspend fun deleteSession(userId: String) = sessionLock.withLock {
        sessions.remove(makeSessionKey(userId))?.zero()
        sessionStore?.delete(userId)
    }

    suspend fun archiveSession(userId: String) = sessionLock.withLock {
        sessions.remove(makeSessionKey(userId))?.zero()
    }

    suspend fun loadSessionsFromDb() = sessionLock.withLock {
        val store = sessionStore ?: return@withLock
        store.loadAll().forEach { row ->
            val state = DoubleRatchet.deserializeState(row.serialized)
            if (state != null) {
                sessions[row.userId] = state
            }
        }
    }

    // ──────────────────────────────────────────────
    // Identity & Safety
    // ──────────────────────────────────────────────

    /**
     * Compute the safety number for a user.
     *
     * The safety number is a human-readable fingerprint of both parties'
     * identity keys, used for out-of-band verification.
     */
    suspend fun getSafetyNumber(userId: String): String {
        val theirIk = identityKeys[userId] ?: return "UNVERIFIED"
        val ourIk = KeyManager.getIdentityKeyPair()?.publicKey ?: return "UNVERIFIED"
        return identityStore?.computeSafetyNumber(ourIk, theirIk) ?: "UNVERIFIED"
    }

    fun getIdentityKey(userId: String): ByteArray? = identityKeys[userId]

    fun setIdentityKey(userId: String, publicKey: ByteArray) {
        identityKeys[userId] = publicKey.copyOf()
    }

    fun hasIdentityChanged(userId: String): Boolean {
        val stored = identityKeys[userId] ?: return false
        return identityStore?.let { idStore ->
            // NOTE: This requires the identity store to have the latest known key
            false
        } ?: false
    }

    @kotlinx.coroutines.ExperimentalCoroutinesApi
    fun reset() {
        sessions.clear()
        identityKeys.clear()
        initialized = false
        selfUserId = null
    }

    // ──────────────────────────────────────────────
    // Internal Helpers
    // ──────────────────────────────────────────────

    private fun makeSessionKey(peerId: String): String {
        val self = selfUserId ?: throw IllegalStateException("SessionManager not initialized")
        return if (self < peerId) "$self:$peerId:0" else "$peerId:$self:0"
    }

    private suspend fun persistSession(key: String, state: DoubleRatchet.RatchetState) {
        val store = sessionStore ?: return
        val serialized = DoubleRatchet.serializeState(state)
        store.store(key, serialized)
    }

    private fun buildPreKeyPayload(
        header: ByteArray,
        ciphertext: ByteArray,
        ourIk: ByteArray,
        ourEk: ByteArray
    ): ByteArray {
        val spkId = 0
        val opkId = -1
        return ByteBuffer.allocate(
            4 + ourIk.size + 4 + ourEk.size + 4 + 4 + 4 + header.size + ciphertext.size
        ).order(ByteOrder.BIG_ENDIAN).apply {
            putInt(ourIk.size); put(ourIk)
            putInt(ourEk.size); put(ourEk)
            putInt(spkId)
            putInt(opkId)
            putInt(header.size); put(header)
            put(ciphertext)
        }.array()
    }

    private fun buildSignalPayload(header: ByteArray, ciphertext: ByteArray): ByteArray {
        return ByteBuffer.allocate(4 + header.size + ciphertext.size)
            .order(ByteOrder.BIG_ENDIAN)
            .putInt(header.size)
            .put(header)
            .put(ciphertext)
            .array()
    }

    private fun parseSignalPayload(payload: ByteArray): Pair<ByteArray, ByteArray>? {
        return try {
            val buf = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)
            if (buf.remaining() < 4) return null
            val headerSize = buf.getInt()
            if (headerSize <= 0 || headerSize > 256 || buf.remaining() < headerSize) return null
            val headerBytes = ByteArray(headerSize)
            buf.get(headerBytes)
            val ciphertextBytes = ByteArray(buf.remaining())
            buf.get(ciphertextBytes)
            Pair(headerBytes, ciphertextBytes)
        } catch (_: Exception) {
            null
        }
    }

    // ──────────────────────────────────────────────
    // Data Classes
    // ──────────────────────────────────────────────

    enum class MessageType {
        SIGNAL_MESSAGE,
        PREKEY_MESSAGE
    }

    data class EncryptedPayload(
        val messageType: MessageType,
        val payload: ByteArray,
        val recipientDeviceId: String? = null
    )

    data class DecryptedResult(
        val plaintext: ByteArray,
        val senderDeviceId: String? = null,
        val isNewSession: Boolean = false
    )
}

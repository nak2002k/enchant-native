package org.enchant.core.crypto

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

/**
 * Native session management via libenchantcrypto JNI calls.
 *
 * Replaces the Kotlin-only SessionManager/DoubleRatchet/X3DH with native
 * C++ session management. All cryptographic operations (X3DH key agreement,
 * Double Ratchet, session state management) run in the native library.
 *
 * Each instance owns its own native identity store and session store,
 * allowing separate Alice/Bob contexts for proper encrypt/decrypt roundtrips.
 *
 * Thread-safe: all operations are serialized through a mutex.
 *
 * NOTE: Native session store is in-memory only. Sessions are lost on process
 * restart. Persistence support requires implementing callback-based stores
 * (enchant_session_store_create_with_callbacks).
 */
class VeilSession private constructor(
    val selfUserId: String,
    private var identityStoreHandle: Long,
    private var sessionStoreHandle: Long,
    private var sessionManagerHandle: Long
) {
    private val sessionLock = Mutex()
    private val identityKeys = java.util.concurrent.ConcurrentHashMap<String, ByteArray>()

    /**
     * Cleanup native resources. Call when the manager is no longer needed.
     */
    fun close() {
        if (sessionManagerHandle != 0L) {
            EnchantCrypto.enchant_session_manager_destroy(sessionManagerHandle)
            sessionManagerHandle = 0L
        }
        if (sessionStoreHandle != 0L) {
            EnchantCrypto.enchant_session_store_destroy(sessionStoreHandle)
            sessionStoreHandle = 0L
        }
        if (identityStoreHandle != 0L) {
            EnchantCrypto.enchant_identity_store_destroy(identityStoreHandle)
            identityStoreHandle = 0L
        }
    }

    /**
     * Get the local identity public key from the native store.
     *
     * @return 32-byte Ed25519 public key, or null on failure
     */
    suspend fun getLocalIdentityPublicKey(): ByteArray? = sessionLock.withLock {
        val pub = ByteArray(32)
        val priv = ByteArray(64)
        val rc = EnchantCrypto.enchant_identity_store_get_key_pair(identityStoreHandle, pub, priv)
        if (rc != EnchantCrypto.SUCCESS) null else pub
    }

    /**
     * Get the local identity private key from the native store.
     *
     * @return 64-byte Ed25519 private key (seed + public), or null on failure
     */
    suspend fun getLocalIdentityPrivateKey(): ByteArray? = sessionLock.withLock {
        val pub = ByteArray(32)
        val priv = ByteArray(64)
        val rc = EnchantCrypto.enchant_identity_store_get_key_pair(identityStoreHandle, pub, priv)
        if (rc != EnchantCrypto.SUCCESS) null else priv
    }

    /**
     * Set the local registration ID in the native identity store.
     */
    suspend fun setLocalRegistrationId(regId: Int): Boolean = sessionLock.withLock {
        val rc = EnchantCrypto.enchant_identity_store_set_registration_id(identityStoreHandle, regId)
        rc == EnchantCrypto.SUCCESS
    }

    // ──────────────────────────────────────────────
    // Encryption
    // ──────────────────────────────────────────────

    /**
     * Encrypt a message for a recipient.
     *
     * If no session exists with the recipient, establishes one via X3DH
     * using the recipient's key bundle from the IKS server.
     *
     * @param recipientUserId the recipient's user ID
     * @param plaintext the message to encrypt
     * @return EncryptedPayload with message type and ciphertext, or null on failure
     */
    suspend fun encryptMessage(recipientUserId: String, plaintext: ByteArray): EncryptedPayload? {
        return withContext(Dispatchers.Default) {
            sessionLock.withLock {
                val device = extractDeviceId(recipientUserId)

                val hasSession = IntArray(1)
                EnchantCrypto.enchant_session_manager_has_session(
                    sessionManagerHandle, recipientUserId, device, hasSession
                )

                if (hasSession[0] == 0) {
                    val keyBundle = KeyManager.fetchKeyBundle(recipientUserId) ?: return@withLock null
                    identityKeys[recipientUserId] = keyBundle.identityKey

                    val rc = EnchantCrypto.enchant_session_manager_establish(
                        sessionManagerHandle,
                        recipientUserId,
                        device,
                        keyBundle.identityKey,
                        1,
                        keyBundle.signedPrekey.publicKey,
                        keyBundle.signedPrekey.signature,
                        keyBundle.signedPrekey.signature.size.toLong(),
                        1,
                        keyBundle.oneTimePrekey ?: ByteArray(0),
                        1
                    )
                    if (rc != EnchantCrypto.SUCCESS) return@withLock null

                    if (rc == EnchantCrypto.SUCCESS) {
                        EnchantCrypto.enchant_identity_store_save_identity(
                            identityStoreHandle, recipientUserId, device, keyBundle.identityKey
                        )
                    }
                }

                val maxCiphertext = plaintext.size + 512
                val ciphertext = ByteArray(maxCiphertext)
                val ciphertextLen = longArrayOf(maxCiphertext.toLong())
                val messageType = intArrayOf(0)

                val rc = EnchantCrypto.enchant_session_manager_encrypt(
                    sessionManagerHandle, recipientUserId, device,
                    plaintext, plaintext.size.toLong(),
                    ciphertext, ciphertextLen, messageType
                )
                if (rc != EnchantCrypto.SUCCESS) {
                    return@withLock null
                }

                val actualType = when (messageType[0]) {
                    1 -> MessageType.PREKEY_MESSAGE
                    2 -> MessageType.ENCRYPTED_MESSAGE
                    else -> MessageType.ENCRYPTED_MESSAGE
                }

                EncryptedPayload(
                    messageType = actualType,
                    payload = ciphertext.copyOf(ciphertextLen[0].toInt()),
                    recipientDeviceId = null
                )
            }
        }
    }

    /**
     * Encrypt a message using the existing session key directly.
     *
     * Used for media key encryption where we want to use the current session
     * state without establishing a new session.
     */
    suspend fun encryptWithSessionKey(recipientUserId: String, plaintext: ByteArray): ByteArray? {
        return withContext(Dispatchers.Default) {
            sessionLock.withLock {
                val device = extractDeviceId(recipientUserId)

                val hasSession = IntArray(1)
                EnchantCrypto.enchant_session_manager_has_session(
                    sessionManagerHandle, recipientUserId, device, hasSession
                )
                if (hasSession[0] == 0) return@withLock null

                val maxCiphertext = plaintext.size + 512
                val ciphertext = ByteArray(maxCiphertext)
                val ciphertextLen = longArrayOf(maxCiphertext.toLong())
                val messageType = intArrayOf(0)

                val rc = EnchantCrypto.enchant_session_manager_encrypt(
                    sessionManagerHandle, recipientUserId, device,
                    plaintext, plaintext.size.toLong(),
                    ciphertext, ciphertextLen, messageType
                )
                if (rc != EnchantCrypto.SUCCESS) return@withLock null

                ciphertext.copyOf(ciphertextLen[0].toInt())
            }
        }
    }

    // ──────────────────────────────────────────────
    // Decryption
    // ──────────────────────────────────────────────

    /**
     * Decrypt a regular encrypted message (existing session).
     */
    suspend fun decryptMessage(senderUserId: String, ciphertext: ByteArray): DecryptedResult? {
        return withContext(Dispatchers.Default) {
            sessionLock.withLock {
                val device = extractDeviceId(senderUserId)

                val maxPlaintext = ciphertext.size + 256
                val plaintext = ByteArray(maxPlaintext)
                val plaintextLen = longArrayOf(maxPlaintext.toLong())

                val rc = EnchantCrypto.enchant_session_manager_decrypt(
                    sessionManagerHandle, senderUserId, device,
                    ciphertext, ciphertext.size.toLong(),
                    2,
                    plaintext, plaintextLen
                )
                if (rc != EnchantCrypto.SUCCESS) {
                    return@withLock null
                }

                DecryptedResult(
                    plaintext = plaintext.copyOf(plaintextLen[0].toInt()),
                    senderDeviceId = null,
                    isNewSession = false
                )
            }
        }
    }

    /**
     * Decrypt a pre-key message (establishes new session via X3DH as Bob).
     */
    suspend fun decryptPreKeyMessage(senderUserId: String, ciphertext: ByteArray): DecryptedResult? {
        return withContext(Dispatchers.Default) {
            sessionLock.withLock {
                val device = extractDeviceId(senderUserId)

                val maxPlaintext = ciphertext.size + 256
                val plaintext = ByteArray(maxPlaintext)
                val plaintextLen = longArrayOf(maxPlaintext.toLong())

                val rc = EnchantCrypto.enchant_session_manager_decrypt(
                    sessionManagerHandle, senderUserId, device,
                    ciphertext, ciphertext.size.toLong(),
                    1,
                    plaintext, plaintextLen
                )
                if (rc != EnchantCrypto.SUCCESS) {
                    return@withLock null
                }

                DecryptedResult(
                    plaintext = plaintext.copyOf(plaintextLen[0].toInt()),
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
        val device = extractDeviceId(userId)
        val hasSession = IntArray(1)
        EnchantCrypto.enchant_session_manager_has_session(
            sessionManagerHandle, userId, device, hasSession
        )
        hasSession[0] != 0
    }

    suspend fun deleteSession(userId: String) = sessionLock.withLock {
        val device = extractDeviceId(userId)
        EnchantCrypto.enchant_session_manager_archive_session(
            sessionManagerHandle, userId, device
        )
    }

    suspend fun archiveSession(userId: String) = sessionLock.withLock {
        val device = extractDeviceId(userId)
        EnchantCrypto.enchant_session_manager_archive_session(
            sessionManagerHandle, userId, device
        )
    }

    suspend fun loadSessionsFromDb() {
        // Native in-memory store has no persistence. No-op.
    }

    // ──────────────────────────────────────────────
    // Identity & Safety
    // ──────────────────────────────────────────────

    /**
     * Compute the safety number for a peer.
     */
    suspend fun getSafetyNumber(userId: String): String {
        val theirIk = identityKeys[userId] ?: return "UNVERIFIED"
        val ourIk = getLocalIdentityPublicKey() ?: return "UNVERIFIED"
        return try {
            val safetyNumberOut = ByteArray(64)
            val outLen = longArrayOf(64)
            val rc = EnchantCrypto.enchant_safety_number_generate(
                ourIk, theirIk,
                selfUserId, userId,
                safetyNumberOut, outLen
            )
            if (rc == EnchantCrypto.SUCCESS) {
                safetyNumberOut.joinToString("") { "%02x".format(it) }
            } else "UNVERIFIED"
        } catch (_: Exception) {
            "UNVERIFIED"
        }
    }

    fun getIdentityKey(userId: String): ByteArray? = identityKeys[userId]

    fun setIdentityKey(userId: String, publicKey: ByteArray) {
        identityKeys[userId] = publicKey.copyOf()
        val device = extractDeviceId(userId)
        EnchantCrypto.enchant_identity_store_save_identity(
            identityStoreHandle, userId, device, publicKey
        )
    }

    fun hasIdentityChanged(userId: String): Boolean {
        // TODO: Implement proper identity change detection via native store
        return false
    }

    // ──────────────────────────────────────────────
    // Internal Helpers
    // ──────────────────────────────────────────────

    /**
     * Establish a session with a peer using their key bundle.
     *
     * @param peerUserId the peer's user ID
     * @param device the peer's device ID
     * @param keyBundle the peer's key bundle (identity, signed prekey, one-time prekey)
     * @return true if the session was established successfully
     */
    suspend fun establishSession(
        peerUserId: String,
        device: Int,
        keyBundle: KeyManager.KeyBundle
    ): Boolean = sessionLock.withLock {
        identityKeys[peerUserId] = keyBundle.identityKey

        val rc = EnchantCrypto.enchant_session_manager_establish(
            sessionManagerHandle,
            peerUserId,
            device,
            keyBundle.identityKey,
            1,
            keyBundle.signedPrekey.publicKey,
            keyBundle.signedPrekey.signature,
            keyBundle.signedPrekey.signature.size.toLong(),
            1,
            keyBundle.oneTimePrekey ?: ByteArray(0),
            1
        )

        if (rc == EnchantCrypto.SUCCESS) {
            EnchantCrypto.enchant_identity_store_save_identity(
                identityStoreHandle, peerUserId, device, keyBundle.identityKey
            )
        }

        rc == EnchantCrypto.SUCCESS
    }

    /**
     * Like [establishSession] but uses a caller-supplied ephemeral private key
     * for the X3DH handshake. This is intended for test harnesses that need
     * two parties to derive the same X3DH shared secret by sharing the same
     * ephemeral key. Production code should use [establishSession].
     */
    suspend fun establishSessionWithEphemeral(
        peerUserId: String,
        device: Int,
        keyBundle: KeyManager.KeyBundle,
        ourEphemeralPrivate: ByteArray
    ): Boolean = sessionLock.withLock {
        identityKeys[peerUserId] = keyBundle.identityKey

        val rc = EnchantCrypto.enchant_session_manager_establish_with_ephemeral(
            sessionManagerHandle,
            peerUserId,
            device,
            keyBundle.identityKey,
            1,
            keyBundle.signedPrekey.publicKey,
            keyBundle.signedPrekey.signature,
            keyBundle.signedPrekey.signature.size.toLong(),
            1,
            keyBundle.oneTimePrekey ?: ByteArray(0),
            1,
            ourEphemeralPrivate,
            ourEphemeralPrivate.size.toLong()
        )

        if (rc == EnchantCrypto.SUCCESS) {
            EnchantCrypto.enchant_identity_store_save_identity(
                identityStoreHandle, peerUserId, device, keyBundle.identityKey
            )
        }

        rc == EnchantCrypto.SUCCESS
    }

    private suspend fun establishSessionNative(recipientUserId: String, device: Int): Boolean {
        val keyBundle = KeyManager.fetchKeyBundle(recipientUserId) ?: return false
        return establishSession(recipientUserId, device, keyBundle)
    }

    private fun extractDeviceId(userId: String): Int {
        val parts = userId.split(":")
        return if (parts.size > 1) {
            parts.last().toIntOrNull() ?: 1
        } else {
            1
        }
    }

    companion object {
        /**
         * Create a new VeilSession instance with its own native stores.
         *
         * @param selfUserId the current user's ID
         * @param store unused (kept for API compatibility)
         * @param idStore unused (kept for API compatibility)
         * @return a new VeilSession instance
         */
        suspend fun create(
            selfUserId: String,
            store: SessionStore? = null,
            idStore: IdentityStore? = null
        ): VeilSession {
            val idStoreOut = LongArray(1)
            val rc1 = EnchantCrypto.enchant_identity_store_create(idStoreOut)
            if (rc1 != EnchantCrypto.SUCCESS) {
                throw IllegalStateException("Failed to create native identity store: $rc1")
            }
            val identityStoreHandle = idStoreOut[0]

            val sessionStoreOut = LongArray(1)
            val rc2 = EnchantCrypto.enchant_session_store_create(sessionStoreOut)
            if (rc2 != EnchantCrypto.SUCCESS) {
                EnchantCrypto.enchant_identity_store_destroy(identityStoreHandle)
                throw IllegalStateException("Failed to create native session store: $rc2")
            }
            val sessionStoreHandle = sessionStoreOut[0]

            val managerOut = LongArray(1)
            val rc3 = EnchantCrypto.enchant_session_manager_create(
                identityStoreHandle, sessionStoreHandle, managerOut
            )
            if (rc3 != EnchantCrypto.SUCCESS) {
                EnchantCrypto.enchant_session_store_destroy(sessionStoreHandle)
                EnchantCrypto.enchant_identity_store_destroy(identityStoreHandle)
                throw IllegalStateException("Failed to create native session manager: $rc3")
            }
            val sessionManagerHandle = managerOut[0]

            return VeilSession(
                selfUserId,
                identityStoreHandle,
                sessionStoreHandle,
                sessionManagerHandle
            )
        }

        // ──────────────────────────────────────────────
        // Singleton for production use (Alice OR Bob, not both)
        // ──────────────────────────────────────────────
        @Volatile
        private var singleton: VeilSession? = null

        /**
         * Initialize the singleton instance. Used for production where there's
         * only one user per process.
         */
        suspend fun init(
            selfUserId: String,
            store: SessionStore? = null,
            idStore: IdentityStore? = null
        ): VeilSession {
            val existing = singleton
            if (existing != null && existing.selfUserId == selfUserId) {
                return existing
            }
            val newInstance = create(selfUserId, store, idStore)
            singleton = newInstance
            return newInstance
        }

        /**
         * Get the singleton instance. Must call init() first.
         */
        fun get(): VeilSession =
            singleton ?: throw IllegalStateException("VeilSession not initialized")

        /**
         * Reset the singleton. Destroys native resources.
         */
        fun reset() {
            val old = singleton
            singleton = null
            old?.close()
        }
    }

    // ──────────────────────────────────────────────
    // Data Classes
    // ──────────────────────────────────────────────

    enum class MessageType {
        ENCRYPTED_MESSAGE,
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

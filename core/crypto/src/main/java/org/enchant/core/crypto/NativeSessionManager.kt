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
 * Thread-safe: all operations are serialized through a mutex.
 *
 * NOTE: Native session store is in-memory only. Sessions are lost on process
 * restart. Persistence support requires implementing callback-based stores
 * (enchant_session_store_create_with_callbacks).
 */
object NativeSessionManager {
    private val sessionLock = Mutex()
    private var initialized = false
    private var selfUserId: String? = null
    private var identityStoreHandle: Long = 0
    private var sessionStoreHandle: Long = 0
    private var sessionManagerHandle: Long = 0
    private val identityKeys = java.util.concurrent.ConcurrentHashMap<String, ByteArray>()

    /**
     * Initialize the native session manager.
     *
     * Creates native identity store, session store, and session manager handles.
     * The native identity store generates a fresh Ed25519 key pair; the caller
     * should fetch the public key and register it with the server.
     *
     * @param selfUserId the current user's ID (used for address resolution)
     */
    suspend fun init(
        selfUserId: String,
        @Suppress("UNUSED_PARAMETER") store: SessionStore? = null,
        @Suppress("UNUSED_PARAMETER") idStore: IdentityStore? = null
    ) {
        if (initialized) return
        sessionLock.withLock {
            if (initialized) return@withLock
            this.selfUserId = selfUserId

            val idStoreOut = LongArray(1)
            val rc1 = EnchantCrypto.enchant_identity_store_create(idStoreOut)
            if (rc1 != EnchantCrypto.SUCCESS) {
                throw IllegalStateException("Failed to create native identity store: $rc1")
            }
            identityStoreHandle = idStoreOut[0]

            val sessionStoreOut = LongArray(1)
            val rc2 = EnchantCrypto.enchant_session_store_create(sessionStoreOut)
            if (rc2 != EnchantCrypto.SUCCESS) {
                EnchantCrypto.enchant_identity_store_destroy(identityStoreHandle)
                identityStoreHandle = 0
                throw IllegalStateException("Failed to create native session store: $rc2")
            }
            sessionStoreHandle = sessionStoreOut[0]

            val managerOut = LongArray(1)
            val rc3 = EnchantCrypto.enchant_session_manager_create(
                identityStoreHandle, sessionStoreHandle, managerOut
            )
            if (rc3 != EnchantCrypto.SUCCESS) {
                EnchantCrypto.enchant_session_store_destroy(sessionStoreHandle)
                EnchantCrypto.enchant_identity_store_destroy(identityStoreHandle)
                sessionStoreHandle = 0
                identityStoreHandle = 0
                throw IllegalStateException("Failed to create native session manager: $rc3")
            }
            sessionManagerHandle = managerOut[0]

            initialized = true
        }
    }

    /**
     * Get the local identity public key from the native store.
     *
     * @return 32-byte Ed25519 public key, or null if not initialized
     */
    suspend fun getLocalIdentityPublicKey(): ByteArray? = sessionLock.withLock {
        if (!initialized) return@withLock null
        val pub = ByteArray(32)
        val priv = ByteArray(64)
        val rc = EnchantCrypto.enchant_identity_store_get_key_pair(identityStoreHandle, pub, priv)
        if (rc != EnchantCrypto.SUCCESS) null else pub
    }

    /**
     * Set the local registration ID in the native identity store.
     */
    suspend fun setLocalRegistrationId(regId: Int): Boolean = sessionLock.withLock {
        if (!initialized) return@withLock false
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
                if (!initialized) return@withLock null
                val device = extractDeviceId(recipientUserId)

                val hasSession = IntArray(1)
                EnchantCrypto.enchant_session_manager_has_session(
                    sessionManagerHandle, recipientUserId, device, hasSession
                )

                if (hasSession[0] == 0) {
                    val established = establishSessionNative(recipientUserId, device)
                    if (!established) return@withLock null
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
                    System.err.println("NativeSessionManager.encrypt failed: rc=$rc, has_session_before=${hasSession[0]}, msg_type_out=${messageType[0]}")
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
                if (!initialized) return@withLock null
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
     *
     * @param senderUserId the sender's user ID
     * @param ciphertext the encrypted payload
     * @return DecryptedResult with plaintext, or null on failure
     */
    suspend fun decryptMessage(senderUserId: String, ciphertext: ByteArray): DecryptedResult? {
        return withContext(Dispatchers.Default) {
            sessionLock.withLock {
                if (!initialized) return@withLock null
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
                if (rc != EnchantCrypto.SUCCESS) return@withLock null

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
     *
     * @param senderUserId the sender's user ID
     * @param ciphertext the pre-key encrypted payload
     * @return DecryptedResult with plaintext and isNewSession=true, or null on failure
     */
    suspend fun decryptPreKeyMessage(senderUserId: String, ciphertext: ByteArray): DecryptedResult? {
        return withContext(Dispatchers.Default) {
            sessionLock.withLock {
                if (!initialized) return@withLock null
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
                if (rc != EnchantCrypto.SUCCESS) return@withLock null

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
        if (!initialized) return@withLock false
        val device = extractDeviceId(userId)
        val hasSession = IntArray(1)
        EnchantCrypto.enchant_session_manager_has_session(
            sessionManagerHandle, userId, device, hasSession
        )
        hasSession[0] != 0
    }

    suspend fun deleteSession(userId: String) = sessionLock.withLock {
        if (!initialized) return@withLock
        val device = extractDeviceId(userId)
        EnchantCrypto.enchant_session_manager_archive_session(
            sessionManagerHandle, userId, device
        )
    }

    suspend fun archiveSession(userId: String) = sessionLock.withLock {
        if (!initialized) return@withLock
        val device = extractDeviceId(userId)
        EnchantCrypto.enchant_session_manager_archive_session(
            sessionManagerHandle, userId, device
        )
    }

    /**
     * Load sessions from the native session store (no-op for in-memory store).
     */
    suspend fun loadSessionsFromDb() {
        // Native in-memory store has no persistence. No-op.
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
        val ourIk = getLocalIdentityPublicKey() ?: return "UNVERIFIED"
        return try {
            val safetyNumberOut = ByteArray(64)
            val outLen = longArrayOf(64)
            val rc = EnchantCrypto.enchant_safety_number_generate(
                ourIk, theirIk,
                selfUserId ?: "", userId,
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
        if (initialized) {
            val device = extractDeviceId(userId)
            EnchantCrypto.enchant_identity_store_save_identity(
                identityStoreHandle, userId, device, publicKey
            )
        }
    }

    fun hasIdentityChanged(userId: String): Boolean {
        return identityKeys.containsKey(userId)
    }

    @kotlinx.coroutines.ExperimentalCoroutinesApi
    fun reset() {
        if (sessionManagerHandle != 0L) {
            EnchantCrypto.enchant_session_manager_destroy(sessionManagerHandle)
            sessionManagerHandle = 0
        }
        if (sessionStoreHandle != 0L) {
            EnchantCrypto.enchant_session_store_destroy(sessionStoreHandle)
            sessionStoreHandle = 0
        }
        if (identityStoreHandle != 0L) {
            EnchantCrypto.enchant_identity_store_destroy(identityStoreHandle)
            identityStoreHandle = 0
        }
        identityKeys.clear()
        initialized = false
        selfUserId = null
    }

    // ──────────────────────────────────────────────
    // Internal Helpers
    // ──────────────────────────────────────────────

    /**
     * Establish a session with a recipient using X3DH.
     *
     * Fetches the recipient's key bundle and calls the native session manager
     * establish function.
     */
    private suspend fun establishSessionNative(recipientUserId: String, device: Int): Boolean {
        val keyBundle = KeyManager.fetchKeyBundle(recipientUserId) ?: return false
        identityKeys[recipientUserId] = keyBundle.identityKey

        val ikPair = KeyManager.getIdentityKeyPair()

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
            0
        )

        if (rc != EnchantCrypto.SUCCESS) {
            System.err.println("NativeSessionManager.establish_session failed: rc=$rc, recipientUserId=$recipientUserId, ik_size=${keyBundle.identityKey.size}, spk_size=${keyBundle.signedPrekey.publicKey.size}, sig_size=${keyBundle.signedPrekey.signature.size}, otk_present=${keyBundle.oneTimePrekey != null}")
        }

        if (rc == EnchantCrypto.SUCCESS && ikPair != null) {
            EnchantCrypto.enchant_identity_store_save_identity(
                identityStoreHandle, recipientUserId, device, keyBundle.identityKey
            )
        }

        return rc == EnchantCrypto.SUCCESS
    }

    /**
     * Extract device ID from a user ID.
     *
     * Currently defaults to device 1 for all users.
     * Multi-device support will use proper device ID resolution.
     */
    private fun extractDeviceId(userId: String): Int {
        val parts = userId.split(":")
        return if (parts.size > 1) {
            parts.last().toIntOrNull() ?: 1
        } else {
            1
        }
    }

    // ──────────────────────────────────────────────
    // Data Classes (same as old SessionManager)
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

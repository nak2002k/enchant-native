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
    private val TAG = "VeilSession"
    private val identityKeys = java.util.concurrent.ConcurrentHashMap<String, ByteArray>()
    private val peerDeviceIds = java.util.concurrent.ConcurrentHashMap<String, String>()

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
                android.util.Log.d("VeilSession", "encryptMessage: $recipientUserId/dev=$device hasSession=${hasSession[0]}")

                if (hasSession[0] == 0) {
                    val keyBundle = KeyManager.fetchKeyBundle(recipientUserId) ?: run {
                        android.util.Log.w("VeilSession", "no key bundle for $recipientUserId")
                        return@withLock null
                    }
                    android.util.Log.d("VeilSession",
                        "encryptMessage: establishing fresh session for $recipientUserId " +
                        "spkId=${keyBundle.signedPrekeyId} opkId=${keyBundle.oneTimePrekeyId} " +
                        "opkLen=${keyBundle.oneTimePrekey?.size}")
                    identityKeys[recipientUserId] = keyBundle.identityKey

                    val rc = nativeEstablish(recipientUserId, device, keyBundle)
                    if (rc != EnchantCrypto.SUCCESS) return@withLock null

                    EnchantCrypto.enchant_identity_store_save_identity(
                        identityStoreHandle, recipientUserId, device, keyBundle.identityKey
                    )
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
                android.util.Log.d(TAG, "encryptMessage: $recipientUserId type=${actualType} " +
                    "len=${ciphertextLen[0]} hasSessionBefore=$hasSession")
                if (ciphertextLen[0] >= 76) {
                    val h = ciphertext.copyOf(76).joinToString("") { "%02x".format(it) }
                    android.util.Log.d(TAG, "encryptMessage: header=$h")
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
     * Decrypt a first-message prekey from a sender (responder path).
     *
     * This is the X3DH responder: the initiator's first message includes
     * the ephemeral public key and prekey IDs so the responder can derive
     * the same shared secret. The responder's prekey private keys must
     * already be stored in the identity store (via storeSignedPrekey /
     * storeOneTimePrekey).
     *
     * The signed-prekey and one-time-prekey IDs are read from the 76-byte
     * prekey header embedded in [ciphertext] (big-endian at offsets 64 and 68),
     * so callers do not need to supply them.
     *
     * @param senderUserId the sender's user ID
     * @param ciphertext the prekey message (76-byte header + envelope)
     * @return decrypted plaintext or null on failure
     */
    suspend fun decryptPrekeyMessage(
        senderUserId: String,
        ciphertext: ByteArray
    ): DecryptedResult? {
        return withContext(Dispatchers.Default) {
            sessionLock.withLock {
                val device = extractDeviceId(senderUserId)

                if (ciphertext.size < 76) {
                    android.util.Log.e(TAG, "decryptPrekeyMessage: header too short ctLen=${ciphertext.size} from=$senderUserId dev=$device")
                    return@withLock null
                }

                val ourSignedPrekeyId = ((ciphertext[64].toInt() and 0xFF) shl 24) or
                    ((ciphertext[65].toInt() and 0xFF) shl 16) or
                    ((ciphertext[66].toInt() and 0xFF) shl 8) or
                    (ciphertext[67].toInt() and 0xFF)
                val ourOneTimePrekeyId = ((ciphertext[68].toInt() and 0xFF) shl 24) or
                    ((ciphertext[69].toInt() and 0xFF) shl 16) or
                    ((ciphertext[70].toInt() and 0xFF) shl 8) or
                    (ciphertext[71].toInt() and 0xFF)
                android.util.Log.d(TAG, "decryptPrekeyMessage: ctLen=${ciphertext.size} from=$senderUserId dev=$device spkId=$ourSignedPrekeyId opkId=$ourOneTimePrekeyId")

                // Diagnose: do we actually hold these keys in the native store?
                val spkProbe = ByteArray(32)
                val opkProbe = ByteArray(32)
                val spkRc = EnchantCrypto.enchant_identity_store_get_signed_prekey_private(
                    identityStoreHandle, ourSignedPrekeyId, spkProbe, 32L)
                val opkRc = EnchantCrypto.enchant_identity_store_get_one_time_prekey_private(
                    identityStoreHandle, ourOneTimePrekeyId, opkProbe, 32L)
                android.util.Log.w(TAG,
                    "decryptPrekeyMessage: storeProbe spkId=$ourSignedPrekeyId rc=$spkRc " +
                    "opkId=$ourOneTimePrekeyId rc=$opkRc")

                val maxPlaintext = ciphertext.size + 256
                val plaintext = ByteArray(maxPlaintext)
                val plaintextLen = longArrayOf(maxPlaintext.toLong())

                val rc = EnchantCrypto.enchant_session_manager_decrypt_prekey(
                    sessionManagerHandle, senderUserId, device,
                    ciphertext, ciphertext.size.toLong(),
                    ourSignedPrekeyId, ourOneTimePrekeyId,
                    plaintext, plaintextLen
                )
                android.util.Log.d(TAG, "decryptPrekeyMessage: native rc=$rc plaintextLen=${plaintextLen[0]} from=$senderUserId dev=$device spkId=$ourSignedPrekeyId opkId=$ourOneTimePrekeyId")
                if (rc != EnchantCrypto.SUCCESS) {
                    android.util.Log.e(TAG, "decryptPrekeyMessage FAILED: rc=$rc (name=$senderUserId, dev=$device, ctLen=${ciphertext.size}) spkId=$ourSignedPrekeyId opkId=$ourOneTimePrekeyId")
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

    /**
     * Store a signed prekey private key in the native identity store
     * so the X3DH responder can look it up by ID when processing a
     * prekey message.
     */
    fun storeSignedPrekey(prekeyId: Int, privateKey: ByteArray): Int {
        return EnchantCrypto.enchant_identity_store_store_signed_prekey(
            identityStoreHandle, prekeyId, privateKey, privateKey.size.toLong()
        )
    }

    /**
     * Store a one-time prekey private key in the native identity store
     * so the X3DH responder can look it up by ID when processing a
     * prekey message.
     */
    fun storeOneTimePrekey(prekeyId: Int, privateKey: ByteArray): Int {
        return EnchantCrypto.enchant_identity_store_store_one_time_prekey(
            identityStoreHandle, prekeyId, privateKey, privateKey.size.toLong()
        )
    }

    /**
     * Read back a one-time prekey private key from the native identity store.
     * Exists so the app can audit that the native responder store holds the
     * same keys as the Kotlin PreKeyStore (the store is a plain in-memory
     * copy and can silently diverge if a sync is missed).
     */
    fun getNativeOneTimePrekeyPrivate(prekeyId: Int): ByteArray? {
        val out = ByteArray(32)
        val rc = EnchantCrypto.enchant_identity_store_get_one_time_prekey_private(
            identityStoreHandle, prekeyId, out, 32
        )
        return if (rc == EnchantCrypto.SUCCESS) out else null
    }

    /**
     * Read back the signed prekey private key from the native identity store.
     */
    fun getNativeSignedPrekeyPrivate(prekeyId: Int): ByteArray? {
        val out = ByteArray(32)
        val rc = EnchantCrypto.enchant_identity_store_get_signed_prekey_private(
            identityStoreHandle, prekeyId, out, 32
        )
        return if (rc == EnchantCrypto.SUCCESS) out else null
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

    /**
     * Return the age of the current session for a peer in seconds.
     * Returns 0 if no session exists.
     */
    suspend fun getSessionAge(userId: String): Long = sessionLock.withLock {
        val device = extractDeviceId(userId)
        val ageOut = LongArray(1)
        val rc = EnchantCrypto.enchant_session_manager_get_session_age(
            sessionManagerHandle, userId, device, ageOut
        )
        if (rc == EnchantCrypto.SUCCESS) ageOut[0] else 0L
    }

    /**
     * Check whether the session for a peer has expired.
     *
     * @param userId peer address
     * @param maxAgeSeconds maximum allowed session age; defaults to 90 days
     * @return true if the session is missing or older than maxAgeSeconds
     */
    suspend fun isSessionExpired(
        userId: String,
        maxAgeSeconds: Long = 90L * 24 * 60 * 60
    ): Boolean = sessionLock.withLock {
        val device = extractDeviceId(userId)
        val expiredOut = IntArray(1)
        val rc = EnchantCrypto.enchant_session_manager_is_expired(
            sessionManagerHandle, userId, device, maxAgeSeconds, expiredOut
        )
        if (rc != EnchantCrypto.SUCCESS) return@withLock true
        expiredOut[0] != 0
    }

    /**
     * Archive the session if it has expired.
     *
     * @return true if a session was archived
     */
    suspend fun archiveIfExpired(
        userId: String,
        maxAgeSeconds: Long = 90L * 24 * 60 * 60
    ): Boolean = sessionLock.withLock {
        val device = extractDeviceId(userId)
        val expiredOut = IntArray(1)
        val rc = EnchantCrypto.enchant_session_manager_is_expired(
            sessionManagerHandle, userId, device, maxAgeSeconds, expiredOut
        )
        if (rc == EnchantCrypto.SUCCESS && expiredOut[0] != 0) {
            EnchantCrypto.enchant_session_manager_archive_session(
                sessionManagerHandle, userId, device
            )
            true
        } else {
            false
        }
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

    fun getUserIdForIdentityKey(publicKey: ByteArray): String? =
        identityKeys.entries.firstOrNull { it.value.contentEquals(publicKey) }?.key

    fun setPeerDeviceId(userId: String, deviceId: String) {
        if (deviceId.isNotBlank()) peerDeviceIds[userId] = deviceId
    }

    fun getPeerDeviceId(userId: String): String? = peerDeviceIds[userId]

    fun setIdentityKey(userId: String, publicKey: ByteArray) {
        identityKeys[userId] = publicKey.copyOf()
        val device = extractDeviceId(userId)
        EnchantCrypto.enchant_identity_store_save_identity(
            identityStoreHandle, userId, device, publicKey
        )
    }

    /**
     * Override the local identity key pair in the native store.
     *
     * This is used by the X3DH responder (Bob) to set a known key pair
     * so that Alice's X3DH handshake uses Bob's real identity.
     *
     * @param publicKey 32-byte X25519 public key
     * @param privateKey 32-byte X25519 private key
     */
    fun setIdentityKeyPair(publicKey: ByteArray, privateKey: ByteArray) {
        val rc = EnchantCrypto.enchant_identity_store_set_key_pair(
            identityStoreHandle, publicKey, privateKey
        )
        if (rc != EnchantCrypto.SUCCESS) {
            throw IllegalStateException("Failed to set identity key pair: $rc")
        }
    }

    fun hasIdentityChanged(userId: String, newIdentityKey: ByteArray): Boolean {
        val existing = identityKeys[userId] ?: return false
        return !existing.contentEquals(newIdentityKey)
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

        val rc = nativeEstablish(peerUserId, device, keyBundle)

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
        val opk = keyBundle.oneTimePrekey ?: return@withLock false

        val edKey = keyBundle.ed25519IdentityKey
        val rc = if (edKey != null && edKey.size == 32) {
            EnchantCrypto.enchant_session_manager_establish_v2(
                sessionManagerHandle, peerUserId, device,
                keyBundle.identityKey, edKey,
                keyBundle.signedPrekeyId,
                keyBundle.signedPrekey.publicKey, keyBundle.signedPrekey.signature,
                keyBundle.signedPrekey.signature.size.toLong(),
                keyBundle.oneTimePrekeyId, opk, 1
            )
        } else {
            EnchantCrypto.enchant_session_manager_establish_with_ephemeral(
                sessionManagerHandle,
                peerUserId,
                device,
                keyBundle.identityKey,
                keyBundle.signedPrekeyId,
                keyBundle.signedPrekey.publicKey,
                keyBundle.signedPrekey.signature,
                keyBundle.signedPrekey.signature.size.toLong(),
                keyBundle.oneTimePrekeyId,
                opk,
                1,
                ourEphemeralPrivate,
                ourEphemeralPrivate.size.toLong()
            )
        }

        if (rc == EnchantCrypto.SUCCESS) {
            EnchantCrypto.enchant_identity_store_save_identity(
                identityStoreHandle, peerUserId, device, keyBundle.identityKey
            )
        }

        rc == EnchantCrypto.SUCCESS
    }

    /**
     * X3DH session setup against the peer's key bundle.
     *
     * Routed through `establish_with_ephemeral` (null ephemeral = library
     * generates one) because the bundled libenchantcrypto.so still exports the
     * pre-0.3 `enchant_session_manager_establish`, which lacks the prekey-id
     * parameters and misreads the argument list.
     */
    private fun nativeEstablish(
        peerUserId: String,
        device: Int,
        keyBundle: KeyManager.KeyBundle
    ): Int {
        val ik = keyBundle.identityKey
        val spk = keyBundle.signedPrekey.publicKey
        val sig = keyBundle.signedPrekey.signature
        val opk = keyBundle.oneTimePrekey
        if (ik.size != 32 || spk.size != 32 || sig.size != 64) {
            android.util.Log.w(
                "VeilSession",
                "establish $peerUserId: bad bundle sizes ik=${ik.size} spk=${spk.size} sig=${sig.size}"
            )
            return EnchantCrypto.ERROR_INVALID_FORMAT
        }
        if (opk == null || opk.size != 32) {
            android.util.Log.w("VeilSession", "establish $peerUserId: bad opk size=${opk?.size}")
            return EnchantCrypto.ERROR_INVALID_FORMAT
        }
        run {
            val p = ByteArray(32); val s = ByteArray(64)
            val irc = EnchantCrypto.enchant_identity_store_get_key_pair(identityStoreHandle, p, s)
            android.util.Log.w("VeilSession", "identity_store rc=$irc handle=$identityStoreHandle")
        }
        val edKey = keyBundle.ed25519IdentityKey
        val rc = if (edKey != null && edKey.size == 32) {
            val r = EnchantCrypto.enchant_session_manager_establish_v2(
                sessionManagerHandle, peerUserId, device,
                ik, edKey,
                keyBundle.signedPrekeyId, spk, sig, sig.size.toLong(),
                keyBundle.oneTimePrekeyId, opk, 1
            )
            android.util.Log.d("VeilSession",
                "nativeEstablish v2 rc=$r peer=$peerUserId/dev=$device spkId=${keyBundle.signedPrekeyId} " +
                "opkId=${keyBundle.oneTimePrekeyId} ikPrefix=${ik.take(4).joinToString("") { "%02x".format(it) }}")
            r
        } else {
            val r = EnchantCrypto.enchant_session_manager_establish_with_ephemeral(
                sessionManagerHandle,
                peerUserId,
                device,
                ik,
                keyBundle.signedPrekeyId,
                spk,
                sig,
                sig.size.toLong(),
                keyBundle.oneTimePrekeyId,
                opk,
                1,
                null,
                0L
            )
            android.util.Log.d("VeilSession",
                "nativeEstablish with_ephemeral rc=$r peer=$peerUserId/dev=$device " +
                "spkId=${keyBundle.signedPrekeyId} opkId=${keyBundle.oneTimePrekeyId}")
            r
        }
        if (rc != EnchantCrypto.SUCCESS) {
            android.util.Log.w(
                "VeilSession",
                "establish $peerUserId/$device failed rc=$rc spkId=${keyBundle.signedPrekeyId} opkId=${keyBundle.oneTimePrekeyId}"
            )
        }
        return rc
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
            idStore: IdentityStore? = null,
            sessionDbPath: String? = null
        ): VeilSession {
            val idStoreOut = LongArray(1)
            val rc1 = EnchantCrypto.enchant_identity_store_create(idStoreOut)
            if (rc1 != EnchantCrypto.SUCCESS) {
                throw IllegalStateException("Failed to create native identity store: $rc1")
            }
            val identityStoreHandle = idStoreOut[0]

            val sessionStoreOut = LongArray(1)
            // When a DB path is supplied, use the SQLite-backed session store
            // (encrypted at rest in the lib) so sessions survive app restarts.
            val rc2 = if (sessionDbPath != null) {
                EnchantCrypto.enchant_session_store_create_sqlite(sessionStoreOut, sessionDbPath)
            } else {
                EnchantCrypto.enchant_session_store_create(sessionStoreOut)
            }
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
            idStore: IdentityStore? = null,
            sessionDbPath: String? = null
        ): VeilSession {
            val existing = singleton
            if (existing != null && existing.selfUserId == selfUserId) {
                return existing
            }
            val newInstance = create(selfUserId, store, idStore, sessionDbPath)
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

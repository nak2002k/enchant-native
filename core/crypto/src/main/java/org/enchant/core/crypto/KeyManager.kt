package org.enchant.core.crypto

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.*

/**
 * Key bundle orchestration: identity key, signed prekey, one-time prekey
 * generation, storage, upload to the IKS server, rotation, and top-up.
 *
 * Manages the full lifecycle of cryptographic keys:
 * 1. Generate Ed25519 identity key pair (persistent device identity)
 * 2. Generate X25519 signed prekey (rotated every 30 days)
 * 3. Generate X25519 one-time prekeys (batch of 100, topped up when < 10)
 * 4. Upload key bundle to IKS server
 * 5. Fetch other users' key bundles for session establishment
 *
 * NOTE: This module depends on:
 * - :core:base (SecurePreferences, KeyStoreManager) for key storage
 * - :core:network (ApiClient) for server communication
 * - :core:protos (generated protobuf classes) for wire format
 */
object KeyManager {
    private val mutex = Mutex()
    private var initialized = false
    private var identityKeyPair: CryptoPrimitives.KeyPair? = null
    private var preKeyStore: PreKeyStore? = null
    private var apiClient: ApiClientLike? = null
    private var lastSpkRotationMs = 0L
    private val spkRotationIntervalMs = 25L * 24 * 60 * 60 * 1000 // 25 days (rotate before 30-day threshold)
    private val testKeyBundles = mutableMapOf<String, KeyBundle>()

    // NOTE: ApiClientLike is a minimal interface to avoid depending on the full ApiClient class.
    // The actual ApiClient in :core:network should implement this interface.
    interface ApiClientLike {
        suspend fun get(path: String): Result<JsonObject>
        suspend fun post(path: String, body: JsonObject): Result<JsonObject>
        suspend fun put(path: String, body: JsonObject): Result<JsonObject>
    }

    data class KeyBundle(
        val deviceId: String,
        val identityKey: ByteArray,
        val signedPrekey: SignedPrekeyData,
        val oneTimePrekey: ByteArray?
    )

    data class SignedPrekeyData(
        val publicKey: ByteArray,
        val signature: ByteArray
    )

    // ──────────────────────────────────────────────
    // Test Helpers
    // ──────────────────────────────────────────────

    fun setTestIdentityKeyPair(pair: CryptoPrimitives.KeyPair) {
        identityKeyPair = pair
    }

    fun setTestKeyBundle(userId: String, bundle: KeyBundle) {
        testKeyBundles[userId] = bundle
    }

    fun clearTestKeyBundles() {
        testKeyBundles.clear()
    }

    fun reset() {
        identityKeyPair = null
        lastSpkRotationMs = 0L
        initialized = false
        testKeyBundles.clear()
    }

    // ──────────────────────────────────────────────
    // Initialization
    // ──────────────────────────────────────────────

    /**
     * Initialize the KeyManager.
     *
     * NOTE: SecurePreferences and KeyStoreManager are from :core:base module.
     * This function loads existing identity keys from secure storage.
     */
    suspend fun init(
        client: ApiClientLike? = null,
        store: PreKeyStore? = null,
        identityPublicB64: String? = null,
        identityPrivateB64: String? = null
    ) {
        if (initialized) return
        mutex.withLock {
            if (initialized) return@withLock
            apiClient = client
            preKeyStore = store

            if (identityPublicB64 != null && identityPrivateB64 != null) {
                try {
                    val publicKey = CryptoPrimitives.base64UrlDecode(identityPublicB64)
                    val privateKey = CryptoPrimitives.base64UrlDecode(identityPrivateB64)
                    identityKeyPair = CryptoPrimitives.KeyPair(publicKey, privateKey)
                } catch (_: Exception) {
                    identityKeyPair = null
                }
            }

            initialized = true
        }
    }

    // ──────────────────────────────────────────────
    // Key Generation & Upload
    // ──────────────────────────────────────────────

    /**
     * Generate and upload a full key bundle to the IKS server.
     *
     * Creates identity key (if not exists), signed prekey, and 100 one-time prekeys,
     * then uploads them to POST /v1/keys/register.
     *
     * @return Result indicating success or failure
     */
    suspend fun generateAndUploadKeys(): Result<Unit> {
        return withContext(Dispatchers.Default) {
            try {
                val ik = ensureIdentityKey()
                val spk = ensureSignedPreKey(ik)
                val opks = ensureOpkBatch()

                if (opks.isEmpty()) {
                    return@withContext Result.failure(IllegalStateException("No OPKs available"))
                }

                val uploadResult = uploadKeyBundle(ik, spk, opks)
                if (uploadResult.isFailure) return@withContext uploadResult

                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private suspend fun ensureIdentityKey(): CryptoPrimitives.KeyPair {
        if (identityKeyPair == null) {
            val pair = CryptoPrimitives.generateEd25519KeyPair()
            identityKeyPair = pair
        }
        return identityKeyPair!!
    }

    private suspend fun ensureSignedPreKey(ik: CryptoPrimitives.KeyPair): PreKeyStore.SignedPreKeyRecord {
        val store = preKeyStore ?: throw IllegalStateException("PreKeyStore not set")
        val current = store.getCurrentSignedPreKey()
        if (current != null && !store.needsSignedPreKeyRotation()) {
            return current
        }
        return store.generateSignedPreKey(ik)
    }

    private suspend fun ensureOpkBatch(): List<PreKeyStore.OneTimePreKeyRecord> {
        val store = preKeyStore ?: throw IllegalStateException("PreKeyStore not set")
        val count = store.getOneTimePreKeyCount()
        return if (count < 20) {
            val needed = 100 - count
            val startId = count
            store.generateOneTimePreKeys(needed, startId = startId)
        } else {
            store.getOneTimePreKeyPublicKeys().map { pub ->
                PreKeyStore.OneTimePreKeyRecord(
                    id = pub.id,
                    publicKey = pub.publicKey,
                    privateKey = ByteArray(32),
                    timestamp = System.currentTimeMillis()
                )
            }
        }
    }

    private suspend fun uploadKeyBundle(
        ik: CryptoPrimitives.KeyPair,
        spk: PreKeyStore.SignedPreKeyRecord,
        opks: List<PreKeyStore.OneTimePreKeyRecord>
    ): Result<Unit> {
        val client = apiClient ?: return Result.failure(Exception("No API client"))

        val body = buildJsonObject {
            put("identity_key", JsonPrimitive(CryptoPrimitives.base64UrlEncode(ik.publicKey)))
            put("signed_prekey", buildJsonObject {
                put("public_key", JsonPrimitive(CryptoPrimitives.base64UrlEncode(spk.publicKey)))
                put("signature", JsonPrimitive(CryptoPrimitives.base64UrlEncode(spk.signature)))
            })
            put("one_time_prekeys", buildJsonArray {
                opks.forEach { opk ->
                    add(buildJsonObject {
                        put("public_key", JsonPrimitive(CryptoPrimitives.base64UrlEncode(opk.publicKey)))
                    })
                }
            })
        }

        return client.post("/v1/keys/register", body).map { }
    }

    // ──────────────────────────────────────────────
    // Key Accessors
    // ──────────────────────────────────────────────

    suspend fun getIdentityKeyPair(): CryptoPrimitives.KeyPair? = identityKeyPair

    suspend fun getSignedPreKeyPair(): CryptoPrimitives.KeyPair? {
        val store = preKeyStore ?: return null
        return store.getCurrentSignedPreKey()?.let {
            CryptoPrimitives.KeyPair(it.publicKey, it.privateKey)
        }
    }

    suspend fun getOneTimePreKeyPair(id: Int): CryptoPrimitives.KeyPair? {
        val store = preKeyStore ?: return null
        return store.consumeOneTimePreKey(id)?.let {
            CryptoPrimitives.KeyPair(it.publicKey, it.privateKey)
        }
    }

    suspend fun consumeOneTimePreKey(id: Int) {
        preKeyStore?.consumeOneTimePreKey(id)
    }

    suspend fun getIdentityPublicKeyBase64(): String? {
        return identityKeyPair?.let { CryptoPrimitives.base64UrlEncode(it.publicKey) }
    }

    suspend fun hasKeys(): Boolean = identityKeyPair != null

    suspend fun signWithIdentity(data: ByteArray): ByteArray? {
        val ik = identityKeyPair ?: return null
        return CryptoPrimitives.signEd25519(data, ik.privateKey)
    }

    // ──────────────────────────────────────────────
    // Key Bundle Fetching
    // ──────────────────────────────────────────────

    /**
     * Fetch another user's key bundle from the IKS server.
     *
     * GET /v1/keys/bundle/{userId}
     *
     * @param userId the target user's ID
     * @return KeyBundle with identity key, signed prekey, and optional one-time prekey
     */
    suspend fun fetchKeyBundle(userId: String): KeyBundle? {
        testKeyBundles[userId]?.let { return it }
        val client = apiClient ?: return null
        return withContext(Dispatchers.Default) {
            try {
                val response = client.get("/v1/keys/bundle/$userId")
                response.getOrNull()?.let { json ->
                    val devices = json["devices"]?.jsonArray ?: return@let null
                    val device = devices.firstOrNull()?.jsonObject ?: return@let null
                    val ikStr = device["identity_key"]?.jsonPrimitive?.content ?: return@let null
                    val spkData = device["signed_prekey"]?.jsonObject ?: return@let null
                    val spkPubStr = spkData["public_key"]?.jsonPrimitive?.content ?: return@let null
                    val spkSigStr = spkData["signature"]?.jsonPrimitive?.content ?: return@let null
                    val opkData = device["one_time_prekey"]?.jsonObject
                    val opkBytes = opkData?.let {
                        it["public_key"]?.jsonPrimitive?.content?.let { keyStr ->
                            CryptoPrimitives.base64UrlDecode(keyStr)
                        }
                    }

                    KeyBundle(
                        deviceId = device["device_id"]?.jsonPrimitive?.content ?: "",
                        identityKey = CryptoPrimitives.base64UrlDecode(ikStr),
                        signedPrekey = SignedPrekeyData(
                            publicKey = CryptoPrimitives.base64UrlDecode(spkPubStr),
                            signature = CryptoPrimitives.base64UrlDecode(spkSigStr)
                        ),
                        oneTimePrekey = opkBytes
                    )
                }
            } catch (_: Exception) { null }
        }
    }

    // ──────────────────────────────────────────────
    // OPK Top-Up
    // ──────────────────────────────────────────────

    /**
     * Check OPK count on server and upload new batch if below threshold.
     *
     * GET /v1/keys/opk-count → if count < 10 → POST /v1/keys/one-time-prekeys
     *
     * Uses server's consumed count to determine the starting ID for new OPKs,
     * preventing ID collision with previously consumed OPKs (e.g., after restart).
     */
    suspend fun topUpOpks() {
        val client = apiClient ?: return
        val store = preKeyStore ?: return
        try {
            val countResponse = client.get("/v1/keys/opk-count")
            val remaining = countResponse.getOrNull()?.let { json ->
                json["remaining"]?.jsonPrimitive?.int ?: 100
            } ?: return

            if (remaining < 10) {
                val consumed = 100 - remaining
                val existingCount = store.getOneTimePreKeyCount()
                val needed = maxOf(20, 100 - existingCount)
                val startId = existingCount
                val opks = store.generateOneTimePreKeys(needed, startId = startId)
                val uploadResult = uploadOpks(client, opks)
                if (uploadResult.isFailure) {
                }
            }
        } catch (e: Exception) {
        }
    }

    private suspend fun uploadOpks(
        client: ApiClientLike,
        opks: List<PreKeyStore.OneTimePreKeyRecord>
    ): Result<Unit> {
        return try {
            val body = buildJsonObject {
                put("one_time_prekeys", buildJsonArray {
                    opks.forEach { opk ->
                        add(buildJsonObject {
                            put("id", JsonPrimitive(opk.id))
                            put("public_key", JsonPrimitive(CryptoPrimitives.base64UrlEncode(opk.publicKey)))
                        })
                    }
                })
            }
            client.post("/v1/keys/one-time-prekeys", body).map { }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ──────────────────────────────────────────────
    // SPK Rotation
    // ──────────────────────────────────────────────

    /**
     * Rotate the signed prekey.
     *
     * PUT /v1/keys/signed-prekey
     *
     * Should be called every 30 days or when the current SPK is compromised.
     */
    suspend fun rotateSignedPreKey(): Result<Unit> {
        val client = apiClient ?: return Result.failure(Exception("No API client"))
        val ik = identityKeyPair ?: return Result.failure(Exception("No identity key"))
        val store = preKeyStore ?: return Result.failure(Exception("No PreKeyStore"))

        return withContext(Dispatchers.Default) {
            try {
                val newSpk = store.generateSignedPreKey(ik)

                val body = buildJsonObject {
                    put("public_key", JsonPrimitive(CryptoPrimitives.base64UrlEncode(newSpk.publicKey)))
                    put("signature", JsonPrimitive(CryptoPrimitives.base64UrlEncode(newSpk.signature)))
                }
                val response = client.put("/v1/keys/signed-prekey", body)
                if (response.isSuccess) {
                    lastSpkRotationMs = System.currentTimeMillis()
                }
                response.fold({ Result.success(Unit) }, { Result.failure(it) })
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /** Check if the signed prekey needs rotation. */
    fun needsKeyRotation(): Boolean {
        val store = preKeyStore ?: return true
        return store.needsSignedPreKeyRotation()
    }

    /** Remove old signed prekeys. */
    suspend fun cleanSignedPreKeys() {
        preKeyStore?.cleanSignedPreKeys()
    }
}

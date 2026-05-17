package org.enchant.core.crypto

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import org.enchant.core.base.KeyStoreManager
import org.enchant.core.base.SecurePreferences
import org.enchant.core.network.ApiClient

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

object KeyManager {
    private val mutex = Mutex()
    @Volatile
    private var initialized = false
    private var identityKeyPair: CryptoHelper.KeyPair? = null
    private var spkKeyPair: CryptoHelper.KeyPair? = null
    private var spkSignature: ByteArray? = null
    private var apiClient: ApiClient? = null
    private var lastSpkRotationMs = 0L
    private val spkRotationIntervalMs = 25L * 24 * 60 * 60 * 1000 // 25 days

    suspend fun init(client: ApiClient? = null) {
        if (initialized) return
        apiClient = client
        mutex.withLock {
            if (initialized) return@withLock
            val existingIkPublic = SecurePreferences.getString("crypto.identity_public_ks")
            if (existingIkPublic != null) {
                val publicKey = CryptoHelper.base64UrlDecode(existingIkPublic)
                val wrappedPrivate = SecurePreferences.getString("crypto.identity_private_ks") ?: ""
                val privateKeyEncoded = wrappedPrivate.split(",").map { it.toInt().toByte() }.toByteArray()
                val privateKey = KeyStoreManager.decrypt(
                    KeyStoreManager.KEY_ALIAS_DB_ENCRYPTION,
                    privateKeyEncoded
                )
                if (privateKey != null) {
                    identityKeyPair = CryptoHelper.KeyPair(publicKey, privateKey)
                }
            }
            loadSpk()
            lastSpkRotationMs = SecurePreferences.getLong("crypto.spk_last_rotation", 0L)
            initialized = true
        }
    }

    private suspend fun loadSpk() {
        val pubB64 = SecurePreferences.getString("crypto.spk_public")
        val privWrapped = SecurePreferences.getString("crypto.spk_private")
        val sigB64 = SecurePreferences.getString("crypto.spk_signature")
        if (pubB64 != null && privWrapped != null && sigB64 != null) {
            val publicKey = CryptoHelper.base64UrlDecode(pubB64)
            val privEncoded = privWrapped.split(",").map { it.toInt().toByte() }.toByteArray()
            val privateKey = KeyStoreManager.decrypt(KeyStoreManager.KEY_ALIAS_DB_ENCRYPTION, privEncoded)
            if (privateKey != null) {
                spkKeyPair = CryptoHelper.KeyPair(publicKey, privateKey)
                spkSignature = CryptoHelper.base64UrlDecode(sigB64)
            }
        }
    }

    private suspend fun saveKeyPair(alias: String, keyPair: CryptoHelper.KeyPair) {
        val pubB64 = CryptoHelper.base64UrlEncode(keyPair.publicKey)
        val wrappedPriv = KeyStoreManager.encrypt(KeyStoreManager.KEY_ALIAS_DB_ENCRYPTION, keyPair.privateKey)
        if (wrappedPriv != null) {
            val privStr = wrappedPriv.joinToString(",") { it.toInt().toString() }
            SecurePreferences.putString("${alias}_public_ks", pubB64)
            SecurePreferences.putString("${alias}_private_ks", privStr)
        }
    }

    suspend fun generateAndUploadKeys(): Result<Unit> {
        return withContext(Dispatchers.Default) {
            try {
                if (identityKeyPair == null) {
                    val pair = CryptoHelper.generateEd25519KeyPair()
                    identityKeyPair = pair
                    saveKeyPair("crypto.identity", pair)
                }
                if (spkKeyPair == null) {
                    generateSpk()
                }
                uploadKeyBundle()
                topUpOpks()
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private suspend fun uploadKeyBundle() {
        val client = apiClient ?: return
        val ik = identityKeyPair ?: return
        val spk = spkKeyPair ?: return
        val sig = spkSignature ?: return

        val body = buildJsonObject {
            put("identity_key", JsonPrimitive(CryptoHelper.base64UrlEncode(ik.publicKey)))
            put("signed_prekey", buildJsonObject {
                put("public_key", JsonPrimitive(CryptoHelper.base64UrlEncode(spk.publicKey)))
                put("signature", JsonPrimitive(CryptoHelper.base64UrlEncode(sig)))
            })
            put("one_time_prekeys", buildJsonArray {
                val opks = loadLocalOpks()
                opks.forEach { opkPub ->
                    add(buildJsonObject { put("public_key", JsonPrimitive(CryptoHelper.base64UrlEncode(opkPub))) })
                }
            })
        }
        client.post("/v1/keys/register", body)
    }

    private suspend fun generateSpk() {
        val ik = identityKeyPair ?: return
        spkKeyPair = CryptoHelper.generateX25519KeyPair()
        val spkPubX = spkKeyPair!!.publicKey
        spkSignature = CryptoHelper.signEd25519(spkPubX, ik.privateKey)
        val pubB64 = CryptoHelper.base64UrlEncode(spkKeyPair!!.publicKey)
        val sigB64 = CryptoHelper.base64UrlEncode(spkSignature!!)
        val wrappedPriv = KeyStoreManager.encrypt(KeyStoreManager.KEY_ALIAS_DB_ENCRYPTION, spkKeyPair!!.privateKey)
        if (wrappedPriv != null) {
            val privStr = wrappedPriv.joinToString(",") { it.toInt().toString() }
            SecurePreferences.putString("crypto.spk_public", pubB64)
            SecurePreferences.putString("crypto.spk_private", privStr)
            SecurePreferences.putString("crypto.spk_signature", sigB64)
        }
    }

    suspend fun getIdentityKeyPair(): CryptoHelper.KeyPair? = identityKeyPair

    suspend fun getIdentityPublicKeyBase64(): String? {
        return identityKeyPair?.let { CryptoHelper.base64UrlEncode(it.publicKey) }
    }

    suspend fun hasKeys(): Boolean = identityKeyPair != null

    suspend fun signWithIdentity(data: ByteArray): ByteArray? {
        val ik = identityKeyPair ?: return null
        return CryptoHelper.signEd25519(data, ik.privateKey)
    }

    suspend fun fetchKeyBundle(userId: String): KeyBundle? {
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
                    val opkStr = device["one_time_prekey"]?.jsonPrimitive?.content

                    KeyBundle(
                        deviceId = device["device_id"]?.jsonPrimitive?.content ?: "",
                        identityKey = CryptoHelper.base64UrlDecode(ikStr),
                        signedPrekey = SignedPrekeyData(
                            publicKey = CryptoHelper.base64UrlDecode(spkPubStr),
                            signature = CryptoHelper.base64UrlDecode(spkSigStr)
                        ),
                        oneTimePrekey = if (opkStr != null) CryptoHelper.base64UrlDecode(opkStr) else null
                    )
                }
            } catch (_: Exception) { null }
        }
    }

    suspend fun topUpOpks() {
        val client = apiClient ?: return
        try {
            val countResponse = client.get("/v1/keys/opk-count")
            val remaining = countResponse.getOrNull()?.let { json ->
                json["remaining"]?.jsonPrimitive?.int ?: 100
            } ?: return

            if (remaining < 10) {
                val opks = generateOpks(100)
                uploadOpks(client, opks)
                storeOpksLocally(opks)
            }
        } catch (e: Exception) { Log.w("KeyManager", "OPK top-up failed: ${e.message}") }
    }

    private fun generateOpks(count: Int): List<CryptoHelper.KeyPair> {
        return (1..count).map { CryptoHelper.generateX25519KeyPair() }
    }

    private suspend fun uploadOpks(client: ApiClient, opks: List<CryptoHelper.KeyPair>) {
        val body = buildJsonObject {
            put("one_time_prekeys", buildJsonArray {
                opks.forEach { opk ->
                    add(buildJsonObject { put("public_key", JsonPrimitive(CryptoHelper.base64UrlEncode(opk.publicKey))) })
                }
            })
        }
        client.post("/v1/keys/one-time-prekeys", body)
    }

    private suspend fun storeOpksLocally(opks: List<CryptoHelper.KeyPair>) {
        SecurePreferences.putInt("crypto.opk_count", opks.size)
        opks.forEachIndexed { i, opk ->
            val pubB64 = CryptoHelper.base64UrlEncode(opk.publicKey)
            val wrappedPriv = KeyStoreManager.encrypt(KeyStoreManager.KEY_ALIAS_DB_ENCRYPTION, opk.privateKey)
            if (wrappedPriv != null) {
                val privStr = wrappedPriv.joinToString(",") { it.toInt().toString() }
                SecurePreferences.putString("crypto.opk_${i}_public", pubB64)
                SecurePreferences.putString("crypto.opk_${i}_private", privStr)
            }
        }
    }

    private suspend fun loadLocalOpks(): List<ByteArray> {
        val count = SecurePreferences.getInt("crypto.opk_count", 0)
        return (0 until count).mapNotNull { i ->
            val pub = SecurePreferences.getString("crypto.opk_${i}_public") ?: return@mapNotNull null
            try { CryptoHelper.base64UrlDecode(pub) } catch (_: Exception) { null }
        }
    }

    suspend fun rotateSignedPreKey(): Result<Unit> {
        val client = apiClient ?: return Result.failure(Exception("ApiClient not available"))
        return withContext(Dispatchers.Default) {
            try {
                val ik = identityKeyPair ?: return@withContext Result.failure(Exception("No identity key"))
                val newSpk = CryptoHelper.generateX25519KeyPair()
                val spkPubX = newSpk.publicKey
                val newSig = CryptoHelper.signEd25519(spkPubX, ik.privateKey)

                val body = buildJsonObject {
                    put("public_key", JsonPrimitive(CryptoHelper.base64UrlEncode(newSpk.publicKey)))
                    put("signature", JsonPrimitive(CryptoHelper.base64UrlEncode(newSig)))
                }
                val response = client.put("/v1/keys/signed-prekey", body)
                if (response.isSuccess) {
                    spkKeyPair = newSpk
                    spkSignature = newSig
                    val pubB64 = CryptoHelper.base64UrlEncode(newSpk.publicKey)
                    val sigB64 = CryptoHelper.base64UrlEncode(newSig)
                    val wrappedPriv = KeyStoreManager.encrypt(KeyStoreManager.KEY_ALIAS_DB_ENCRYPTION, newSpk.privateKey)
                    if (wrappedPriv != null) {
                        val privStr = wrappedPriv.joinToString(",") { it.toInt().toString() }
                        SecurePreferences.putString("crypto.spk_public", pubB64)
                        SecurePreferences.putString("crypto.spk_private", privStr)
                        SecurePreferences.putString("crypto.spk_signature", sigB64)
                    }
                    lastSpkRotationMs = System.currentTimeMillis()
                    SecurePreferences.putLong("crypto.spk_last_rotation", lastSpkRotationMs)
                }
                response.fold({ Result.success(Unit) }, { Result.failure(it) })
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun cleanSignedPreKeys() {
        val threshold = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        if (lastSpkRotationMs > 0 && lastSpkRotationMs < threshold) {
            SecurePreferences.remove("crypto.spk_public")
            SecurePreferences.remove("crypto.spk_private")
            SecurePreferences.remove("crypto.spk_signature")
            SecurePreferences.putLong("crypto.spk_last_rotation", 0L)
            spkKeyPair = null
            spkSignature = null
        }
    }

    fun needsKeyRotation(): Boolean {
        return (System.currentTimeMillis() - lastSpkRotationMs) > spkRotationIntervalMs
    }
}

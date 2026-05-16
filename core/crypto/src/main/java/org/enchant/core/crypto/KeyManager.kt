package org.enchant.core.crypto

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.enchant.core.base.KeyStoreManager
import org.enchant.core.base.SecurePreferences

object KeyManager {
    private val mutex = Mutex()
    private var initialized = false
    private var identityKeyPair: CryptoHelper.KeyPair? = null
    private var spkKeyPair: CryptoHelper.KeyPair? = null
    private var spkSignature: ByteArray? = null

    suspend fun init() {
        if (initialized) return
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
        val wrappedPriv = KeyStoreManager.encrypt(
            KeyStoreManager.KEY_ALIAS_DB_ENCRYPTION,
            keyPair.privateKey
        )
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
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private suspend fun generateSpk() {
        val ik = identityKeyPair ?: return
        spkKeyPair = CryptoHelper.generateX25519KeyPair()
        val spkPubX = CryptoHelper.ed25519PkToX25519(spkKeyPair!!.publicKey)
        spkSignature = CryptoHelper.signEd25519(spkPubX, ik.privateKey)
        val pubB64 = CryptoHelper.base64UrlEncode(spkKeyPair!!.publicKey)
        val sigB64 = CryptoHelper.base64UrlEncode(spkSignature!!)
        val wrappedPriv = KeyStoreManager.encrypt(
            KeyStoreManager.KEY_ALIAS_DB_ENCRYPTION,
            spkKeyPair!!.privateKey
        )
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

    suspend fun topUpOpks() {}

    suspend fun rotateSignedPreKey(): Result<Unit> = Result.success(Unit)
}

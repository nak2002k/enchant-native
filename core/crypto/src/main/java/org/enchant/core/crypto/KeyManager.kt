package org.enchant.core.crypto

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.enchant.core.base.KeyStoreManager
import org.enchant.core.base.SecurePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object KeyManager {
    private val mutex = Mutex()
    private var initialized = false
    private var identityKeyPair: CryptoHelper.KeyPair? = null

    suspend fun init() {
        if (initialized) return
        mutex.withLock {
            if (initialized) return@withLock
            val existingIkPublic = SecurePreferences.getString("crypto.identity_public")
            if (existingIkPublic != null) {
                val publicKey = CryptoHelper.base64UrlDecode(existingIkPublic)
                val privateKey = CryptoHelper.base64UrlDecode(
                    SecurePreferences.getString("crypto.identity_private") ?: ""
                )
                identityKeyPair = CryptoHelper.KeyPair(publicKey, privateKey)
            }
            initialized = true
        }
    }

    suspend fun generateAndUploadKeys(): Result<Unit> {
        return withContext(Dispatchers.Default) {
            try {
                if (identityKeyPair == null) {
                    identityKeyPair = CryptoHelper.generateEd25519KeyPair()
                    SecurePreferences.putString("crypto.identity_public",
                        CryptoHelper.base64UrlEncode(identityKeyPair!!.publicKey))
                    SecurePreferences.putString("crypto.identity_private",
                        CryptoHelper.base64UrlEncode(identityKeyPair!!.privateKey))
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getIdentityKeyPair(): CryptoHelper.KeyPair? {
        return identityKeyPair
    }

    suspend fun getIdentityPublicKeyBase64(): String? {
        return identityKeyPair?.let { CryptoHelper.base64UrlEncode(it.publicKey) }
    }

    suspend fun hasKeys(): Boolean = identityKeyPair != null

    suspend fun topUpOpks() {}

    suspend fun rotateSignedPreKey(): Result<Unit> = Result.success(Unit)
}

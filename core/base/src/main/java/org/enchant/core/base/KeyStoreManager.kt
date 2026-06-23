package org.enchant.core.base

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.util.Log
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.KeyStoreException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object KeyStoreManager {

    private const val TAG = "KeyStoreManager"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    const val KEY_ALIAS_IDENTITY = "enchant_identity_key"
    const val KEY_ALIAS_DB_ENCRYPTION = "enchant_db_key"

    @Volatile
    private var initialized = false
    @Volatile
    private var _isHardwareBacked = false

    suspend fun init(context: Context) {
        if (initialized) return
        _isHardwareBacked = try {
            val spec = KeyGenParameterSpec.Builder("__enchant_hardware_test__", KeyProperties.PURPOSE_SIGN)
                .setIsStrongBoxBacked(true)
                .build()
            val kg = KeyPairGenerator.getInstance("EC", ANDROID_KEYSTORE)
            kg.initialize(spec)
            kg.generateKeyPair()
            val ks = getKeyStore()
            ks.deleteEntry("__enchant_hardware_test__")
            true
        } catch (_: Exception) {
            false
        }
        initialized = true
    }

    suspend fun generateKey(
        alias: String,
        purpose: Int,
        requireAuth: Boolean = false
    ): Boolean {
        val ks = getKeyStore()
        if (ks.containsAlias(alias)) return false
        val hasSignOrVerify = (purpose and (KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)) != 0
        val hasEncryptOrDecrypt = (purpose and (KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)) != 0
        if (hasSignOrVerify) {
            val spec = KeyGenParameterSpec.Builder(alias, purpose)
                .setAlgorithmParameterSpec(java.security.spec.ECGenParameterSpec("secp256r1"))
                .setKeySize(256)
                .apply {
                    if (_isHardwareBacked) setIsStrongBoxBacked(true)
                    if (requireAuth) {
                        setUserAuthenticationRequired(true)
                        setUserAuthenticationValidityDurationSeconds(300)
                    }
                }
                .build()
            val kg = KeyPairGenerator.getInstance("EC", ANDROID_KEYSTORE)
            kg.initialize(spec)
            kg.generateKeyPair()
        } else if (hasEncryptOrDecrypt) {
            val spec = KeyGenParameterSpec.Builder(alias, purpose)
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .apply {
                    if (_isHardwareBacked) setIsStrongBoxBacked(true)
                }
                .build()
            val kg = KeyGenerator.getInstance("AES", ANDROID_KEYSTORE)
            kg.init(spec)
            kg.generateKey()
        } else {
            throw IllegalArgumentException("Unsupported key purpose: $purpose")
        }
        return true
    }

    fun keyExists(alias: String): Boolean {
        return try {
            getKeyStore().containsAlias(alias)
        } catch (e: Exception) {
            Log.w(TAG, "keyExists check failed: ${e.message}")
            false
        }
    }

    suspend fun deleteKey(alias: String) {
        try {
            getKeyStore().deleteEntry(alias)
        } catch (e: KeyStoreException) {
            Log.w(TAG, "deleteKey failed: ${e.message}")
        }
    }

    fun keyInfo(alias: String): KeyStoreEntryInfo? {
        return try {
            val ks = getKeyStore()
            val entry = ks.getEntry(alias, null) ?: return null
            val creationDate = ks.getCreationDate(alias)
            when (entry) {
                is KeyStore.PrivateKeyEntry -> {
                    val factory = KeyFactory.getInstance(entry.privateKey.algorithm, ANDROID_KEYSTORE)
                    val keySpec = factory.getKeySpec(entry.privateKey, KeyInfo::class.java) as KeyInfo
                    KeyStoreEntryInfo(
                        alias = alias,
                        algorithm = entry.privateKey.algorithm,
                        isInsideSecureHardware = keySpec.isInsideSecureHardware,
                        origin = keySpec.origin.toString(),
                        purposes = keySpec.purposes,
                        keySize = keySpec.keySize,
                        creationDate = creationDate
                    )
                }
                is KeyStore.SecretKeyEntry -> {
                    val factory = KeyFactory.getInstance(entry.secretKey.algorithm, ANDROID_KEYSTORE)
                    val keySpec = factory.getKeySpec(entry.secretKey, KeyInfo::class.java) as KeyInfo
                    KeyStoreEntryInfo(
                        alias = alias,
                        algorithm = entry.secretKey.algorithm,
                        isInsideSecureHardware = keySpec.isInsideSecureHardware,
                        origin = keySpec.origin.toString(),
                        purposes = keySpec.purposes,
                        keySize = keySpec.keySize,
                        creationDate = creationDate
                    )
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.w(TAG, "keyInfo failed: ${e.message}")
            null
        }
    }

    suspend fun sign(alias: String, data: ByteArray): ByteArray? {
        return try {
            val ks = getKeyStore()
            val entry = ks.getEntry(alias, null) as? KeyStore.PrivateKeyEntry ?: return null
            val signature = java.security.Signature.getInstance("SHA256withECDSA")
            signature.initSign(entry.privateKey)
            signature.update(data)
            signature.sign()
        } catch (e: Exception) {
            Log.w(TAG, "sign failed: ${e.message}")
            null
        }
    }

    suspend fun verify(alias: String, data: ByteArray, signatureBytes: ByteArray): Boolean {
        return try {
            val ks = getKeyStore()
            val cert = ks.getCertificate(alias) ?: return false
            val signature = java.security.Signature.getInstance("SHA256withECDSA")
            signature.initVerify(cert)
            signature.update(data)
            signature.verify(signatureBytes)
        } catch (e: Exception) {
            Log.w(TAG, "verify failed: ${e.message}")
            false
        }
    }

    suspend fun encrypt(alias: String, plaintext: ByteArray): ByteArray? {
        return try {
            val ks = getKeyStore()
            val key = ks.getKey(alias, null) as? SecretKey ?: return null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val ct = cipher.doFinal(plaintext)
            ByteArray(iv.size + ct.size).apply {
                iv.copyInto(this, 0)
                ct.copyInto(this, iv.size)
            }
        } catch (e: Exception) {
            Log.w(TAG, "encrypt failed: ${e.message}")
            null
        }
    }

    suspend fun decrypt(alias: String, ciphertext: ByteArray): ByteArray? {
        return try {
            if (ciphertext.size < 13) return null
            val ks = getKeyStore()
            val key = ks.getKey(alias, null) as? SecretKey ?: return null
            val iv = ciphertext.copyOfRange(0, 12)
            val ct = ciphertext.copyOfRange(12, ciphertext.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            cipher.doFinal(ct)
        } catch (e: Exception) {
            Log.w(TAG, "decrypt failed: ${e.message}")
            null
        }
    }

    suspend fun getOrCreateDatabaseKey(retryCount: Int = 0): ByteArray {
        if (retryCount > 2) {
            throw IllegalStateException("Failed to retrieve database key after 3 attempts")
        }
        val alias = KEY_ALIAS_DB_ENCRYPTION
        if (!keyExists(alias)) {
            generateKey(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
        }
        val raw = SecurePreferences.getString("db.passphrase")
        if (raw == null) {
            val key = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
            val wrapped = encrypt(alias, key) ?: throw IllegalStateException("Failed to encrypt DB key")
            SecurePreferences.putString("db.passphrase", java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(wrapped))
            return key
        }
        val bytes = try {
            java.util.Base64.getUrlDecoder().decode(raw)
        } catch (e: Exception) {
            SecurePreferences.remove("db.passphrase")
            return getOrCreateDatabaseKey(retryCount + 1)
        }
        return decrypt(alias, bytes) ?: throw IllegalStateException("Failed to decrypt DB key")
    }

    fun isHardwareBacked(): Boolean {
        check(initialized) { "KeyStoreManager not initialized. Call KeyStoreManager.init(context) first." }
        return _isHardwareBacked
    }

    private fun getKeyStore(): KeyStore {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE)
        ks.load(null)
        return ks
    }

    data class KeyStoreEntryInfo(
        val alias: String,
        val algorithm: String,
        val isInsideSecureHardware: Boolean,
        val origin: String,
        val purposes: Int,
        val keySize: Int?,
        val creationDate: java.util.Date?
    )
}

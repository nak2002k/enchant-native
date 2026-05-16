package org.enchant.core.base

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.KeyStoreException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object KeyStoreManager {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEYSTORE_TYPE = "PKCS12"

    const val KEY_ALIAS_IDENTITY = "enchant_identity_key"
    const val KEY_ALIAS_DB_ENCRYPTION = "enchant_db_key"

    private var initialized = false
    private var _isHardwareBacked = false

    suspend fun init(context: Context) {
        if (initialized) return
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE)
        ks.load(null)
        _isHardwareBacked = try {
            val spec = KeyGenParameterSpec.Builder("__test__", KeyProperties.PURPOSE_SIGN)
                .setIsStrongBoxBacked(true)
                .build()
            val kg = KeyPairGenerator.getInstance("EC", ANDROID_KEYSTORE)
            kg.initialize(spec)
            kg.generateKeyPair()
            ks.deleteEntry("__test__")
            true
        } catch (_: Exception) {
            false
        }
        initialized = true
    }

    suspend fun generateKey(alias: String, purpose: Int): Boolean {
        val ks = getKeyStore()
        if (ks.containsAlias(alias)) return false
        when (purpose) {
            KeyProperties.PURPOSE_SIGN, KeyProperties.PURPOSE_VERIFY -> {
                val spec = KeyGenParameterSpec.Builder(alias, purpose)
                    .setAlgorithmParameterSpec(java.security.spec.ECGenParameterSpec("secp256r1"))
                    .setKeySize(256)
                    .apply {
                        if (_isHardwareBacked) setIsStrongBoxBacked(true)
                    }
                    .build()
                val kg = KeyPairGenerator.getInstance("EC", ANDROID_KEYSTORE)
                kg.initialize(spec)
                kg.generateKeyPair()
            }
            KeyProperties.PURPOSE_ENCRYPT, KeyProperties.PURPOSE_DECRYPT -> {
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
            }
            else -> throw IllegalArgumentException("Unsupported key purpose: $purpose")
        }
        return true
    }

    fun keyExists(alias: String): Boolean {
        return try {
            getKeyStore().containsAlias(alias)
        } catch (_: Exception) {
            false
        }
    }

    suspend fun deleteKey(alias: String) {
        try {
            getKeyStore().deleteEntry(alias)
        } catch (_: KeyStoreException) {
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
        } catch (_: Exception) {
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
        } catch (_: Exception) {
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
        } catch (_: Exception) {
            null
        }
    }

    suspend fun decrypt(alias: String, ciphertext: ByteArray): ByteArray? {
        return try {
            val ks = getKeyStore()
            val key = ks.getKey(alias, null) as? SecretKey ?: return null
            val iv = ciphertext.copyOfRange(0, 12)
            val ct = ciphertext.copyOfRange(12, ciphertext.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            cipher.doFinal(ct)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun getWrappedKeyBytes(alias: String): ByteArray? {
        return try {
            val ks = getKeyStore()
            val entry = ks.getEntry(alias, null)
            null
        } catch (_: Exception) {
            null
        }
    }

    suspend fun getOrCreateDatabaseKey(): ByteArray {
        val alias = KEY_ALIAS_DB_ENCRYPTION
        if (!keyExists(alias)) {
            generateKey(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
        }
        val raw = SecurePreferences.getString("db.passphrase")
        if (raw == null) {
            val key = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
            val wrapped = encrypt(alias, key) ?: throw IllegalStateException("Failed to encrypt DB key")
            SecurePreferences.putString("db.passphrase", wrapped.joinToString(",") { it.toString() })
            return key
        }
        val bytes = raw.split(",").map { it.toInt().toByte() }.toByteArray()
        return decrypt(alias, bytes) ?: throw IllegalStateException("Failed to decrypt DB key")
    }

    fun isHardwareBacked(): Boolean = _isHardwareBacked

    private fun getKeyStore(): KeyStore {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE)
        ks.load(null)
        return ks
    }
}

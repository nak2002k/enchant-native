package org.enchant.backup

import org.enchant.core.base.SecurePreferences
import org.enchant.core.crypto.CryptoPrimitives
import org.enchant.core.crypto.EnchantCrypto

/**
 * Recovery-key backup (Signal model): a random 32-byte recovery seed
 * (entropy) is derived through the lib into an account key, then into the
 * backup key used to encrypt backups. Because the chain is deterministic, the
 * same recovery seed can re-derive the backup key on any device — unlike a
 * PIN-only key, it survives a PIN reset.
 *
 * entropy -> account_key = HKDF(entropy, "enchant_account_key")
 * backup_key         = HKDF(account_key, "enchant_backup_key")
 */
object BackupKeyManager {

    private const val PREF_RECOVERY_ENTROPY = "backup.recovery_entropy"
    private const val PREF_ACCOUNT_KEY = "backup.account_key"

    /** The 32-byte recovery seed, creating + persisting one on first use. */
    fun getOrCreateRecoveryEntropy(): ByteArray? {
        SecurePreferences.getString(PREF_RECOVERY_ENTROPY)?.let { b64 ->
            return runCatching { CryptoPrimitives.base64UrlDecode(b64) }.getOrNull()
        }
        val entropy = ByteArray(32)
        val rc = EnchantCrypto.enchant_account_entropy_create(entropy)
        if (rc != EnchantCrypto.SUCCESS) return null
        SecurePreferences.putString(PREF_RECOVERY_ENTROPY, CryptoPrimitives.base64UrlEncode(entropy))
        return entropy
    }

    /** The account key derived from the recovery entropy (persisted). */
    fun getAccountKey(): ByteArray? {
        SecurePreferences.getString(PREF_ACCOUNT_KEY)?.let { b64 ->
            return runCatching { CryptoPrimitives.base64UrlDecode(b64) }.getOrNull()
        }
        val entropy = getOrCreateRecoveryEntropy() ?: return null
        val accountKey = ByteArray(32)
        val rc = EnchantCrypto.enchant_account_key_derive(entropy, accountKey)
        if (rc != EnchantCrypto.SUCCESS) return null
        EnchantCrypto.enchant_secure_zero(entropy, entropy.size.toLong())
        SecurePreferences.putString(PREF_ACCOUNT_KEY, CryptoPrimitives.base64UrlEncode(accountKey))
        return accountKey
    }

    /** The backup key = HKDF(account_key). Used to encrypt backup content. */
    fun getBackupKey(): ByteArray? {
        val accountKey = getAccountKey() ?: return null
        val backupKey = ByteArray(32)
        val rc = EnchantCrypto.enchant_backup_key_derive(accountKey, backupKey)
        if (rc != EnchantCrypto.SUCCESS) return null
        return backupKey
    }

    /** Re-derive the account + backup keys from a caller-supplied recovery seed. */
    fun importRecoveryEntropy(entropy: ByteArray): Boolean {
        if (entropy.size != 32) return false
        val accountKey = ByteArray(32)
        if (EnchantCrypto.enchant_account_key_derive(entropy, accountKey) != EnchantCrypto.SUCCESS) return false
        EnchantCrypto.enchant_secure_zero(entropy, entropy.size.toLong())
        SecurePreferences.putString(PREF_RECOVERY_ENTROPY, CryptoPrimitives.base64UrlEncode(entropy))
        SecurePreferences.putString(PREF_ACCOUNT_KEY, CryptoPrimitives.base64UrlEncode(accountKey))
        return true
    }

    fun clear() {
        SecurePreferences.remove(PREF_RECOVERY_ENTROPY)
        SecurePreferences.remove(PREF_ACCOUNT_KEY)
    }
}

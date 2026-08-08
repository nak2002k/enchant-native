package org.enchant.core.crypto

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.enchant.core.base.SecurePreferences
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * SP6: sealed-sender certificate management.
 *
 * The server mints short-lived sender certificates (binding this user's id +
 * device + identity key) at POST /v1/certificate/delivery. The certificate is
 * embedded inside every sealed-sender payload so the RECIPIENT (who decrypts)
 * can attribute the message — the server never sees the sender identity in
 * anonymous sealed-send traffic.
 */
object SenderCertificateManager {

    private const val PREF_CERT = "sealed_sender_certificate"
    private const val PREF_CERT_EXPIRY = "sealed_sender_certificate_expiry"
    private const val CERT_TTL_MS = 7L * 24 * 3600 * 1000

    /** The cached sender certificate (canonical protobuf bytes), or null. */
    fun cachedCertificate(): ByteArray? {
        val b64 = SecurePreferences.getString(PREF_CERT)
            ?: return null
        val expiry = SecurePreferences.getLong(PREF_CERT_EXPIRY, 0L)
        if (expiry != 0L && System.currentTimeMillis() > expiry) return null
        return runCatching { CryptoPrimitives.base64UrlDecode(b64) }.getOrNull()
    }

    /**
     * Returns a valid sender certificate, fetching a fresh one from the server
     * if the cached copy is missing or expired.
     */
    suspend fun getSenderCertificate(forceRefresh: Boolean = false): ByteArray? {
        if (!forceRefresh) {
            cachedCertificate()?.let { return it }
        }
        return refresh()
    }

    private suspend fun refresh(): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val identityKeyPair = KeyManager.getIdentityKeyPair()
                ?: return@withContext null
            val identityB64 = CryptoPrimitives.base64UrlEncode(identityKeyPair.publicKey)
            val client = KeyManager.apiClientOrNull() ?: return@withContext null
            val result = client.post("/v1/certificate/delivery", kotlinx.serialization.json.buildJsonObject {
                put("identity_key", kotlinx.serialization.json.JsonPrimitive(identityB64))
            })
            val json = result.getOrNull() ?: return@withContext null
            val certB64 = json["certificate"]?.jsonPrimitive?.contentOrNull
                ?: return@withContext null
            val cert = CryptoPrimitives.base64UrlDecode(certB64)
            SecurePreferences.putString(PREF_CERT, certB64)
            SecurePreferences.putLong(PREF_CERT_EXPIRY, System.currentTimeMillis() + CERT_TTL_MS)
            cert
        } catch (e: Exception) {
            android.util.Log.w("SenderCert", "refresh failed: ${e.message}")
            null
        }
    }

    fun clear() {
        SecurePreferences.remove(PREF_CERT)
        SecurePreferences.remove(PREF_CERT_EXPIRY)
    }
}

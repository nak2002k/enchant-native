package org.enchant.contacts

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.enchant.core.network.ApiClient
import org.enchant.core.base.SecurePreferences
import org.enchant.core.auth.AuthConstants
import org.enchant.core.crypto.CryptoPrimitives

class ContactDiscovery(private val apiClient: ApiClient) {

    data class DiscoveredContact(
        val phoneHash: String,
        val userId: String?,
        val username: String?,
        val displayName: String?,
        val isRegistered: Boolean
    )

    suspend fun discoverContacts(phoneNumbers: List<String>): Result<List<DiscoveredContact>> = withContext(Dispatchers.IO) {
        try {
            val hashes = phoneNumbers.map { hashPhoneNumber(it) }
            val results = mutableListOf<DiscoveredContact>()
            val batchSize = 1000

            hashes.chunked(batchSize).forEach { batch ->
                val requestBody = buildJsonObject {
                    put("phone_hashes", buildJsonArray {
                        batch.forEach { hash -> add(JsonPrimitive(hash)) }
                    })
                }

                val response = apiClient.post("/v1/contacts/match", requestBody)
                response.onSuccess { json ->
                    val matches = json["matches"]?.jsonArray ?: JsonArray(emptyList())
                    matches.forEach { match ->
                        val obj = match.jsonObject
                        results.add(DiscoveredContact(
                            phoneHash = obj["phone_hash"]?.jsonPrimitive?.content ?: "",
                            userId = obj["user_id"]?.jsonPrimitive?.content,
                            username = obj["username"]?.jsonPrimitive?.content,
                            displayName = obj["display_name"]?.jsonPrimitive?.content,
                            isRegistered = obj["is_registered"]?.jsonPrimitive?.content == "true"
                        ))
                    }
                }
            }
            Result.success(results)
        } catch (e: Exception) {
            Log.e("ContactDiscovery", "Discovery failed: ${e.message}")
            Result.failure(e)
        }
    }

    private fun hashPhoneNumber(phone: String): String {
        val digits = phone.replace(Regex("[^0-9+]"), "")
        val saltB64 = SecurePreferences.getString(AuthConstants.PHONE_SALT_KEY)
            ?: return ""
        val salt = CryptoPrimitives.base64UrlDecode(saltB64)
        val mac = CryptoPrimitives.hmacSha256(salt, digits.toByteArray(Charsets.UTF_8))
        return CryptoPrimitives.base64UrlEncode(mac)
    }
}

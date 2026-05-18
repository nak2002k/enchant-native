package org.enchant.contacts

import android.content.ContentResolver
import android.database.Cursor
import android.provider.ContactsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.enchant.core.network.ApiClient
import java.security.MessageDigest

data class PhoneContact(
    val name: String,
    val phoneNumber: String,
    val normalizedE164: String
)

data class MatchedContact(
    val userId: String,
    val phoneNumber: String,
    val displayName: String?,
    val username: String?,
    val avatarMediaId: String?
)

class ContactSyncService(
    private val apiClient: ApiClient,
    private val contentResolver: ContentResolver
) {
    suspend fun syncContacts(): Result<List<MatchedContact>> = withContext(Dispatchers.Default) {
        try {
            val deviceContacts = readDeviceContacts()
            if (deviceContacts.isEmpty()) return@withContext Result.success(emptyList())

            val hashedNumbers = deviceContacts.map { hashPhoneNumber(it.normalizedE164) }
            val body = buildJsonObject {
                put("hashes", kotlinx.serialization.json.buildJsonArray {
                    hashedNumbers.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
                })
            }

            val response = apiClient.post("/v1/contacts/match", body)
            response.fold(
                onSuccess = { json ->
                    val matched = json["matches"]?.jsonArray?.map { item ->
                        val obj = item.jsonObject
                        MatchedContact(
                            userId = obj["user_id"]?.jsonPrimitive?.content ?: "",
                            phoneNumber = obj["phone_number"]?.jsonPrimitive?.content ?: "",
                            displayName = obj["display_name"]?.jsonPrimitive?.content,
                            username = obj["username"]?.jsonPrimitive?.content,
                            avatarMediaId = obj["avatar_media_id"]?.jsonPrimitive?.content
                        )
                    } ?: emptyList()
                    Result.success(matched)
                },
                onFailure = { Result.failure(it) }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun hashPhoneNumber(e164: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(e164.encodeToByteArray()).joinToString("") { "%02x".format(it) }
    }

    suspend fun readDeviceContacts(): List<PhoneContact> = withContext(Dispatchers.IO) {
        val contacts = mutableListOf<PhoneContact>()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(uri, projection, null, null, null)
            cursor?.use { c ->
                val nameIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (c.moveToNext()) {
                    val name = c.getString(nameIdx) ?: "Unknown"
                    val rawNumber = c.getString(numberIdx) ?: continue
                    val e164 = normalizeToE164(rawNumber)
                    if (e164 != null) {
                        contacts.add(PhoneContact(name = name, phoneNumber = rawNumber, normalizedE164 = e164))
                    }
                }
            }
        } catch (_: SecurityException) {
        } finally {
            cursor?.close()
        }
        contacts.distinctBy { it.normalizedE164 }
    }

    private fun normalizeToE164(number: String): String? {
        return try {
            val phoneUtil = com.google.i18n.phonenumbers.PhoneNumberUtil.getInstance()
            val parsed = phoneUtil.parse(number, null)
            if (phoneUtil.isValidNumber(parsed)) {
                phoneUtil.format(parsed, com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat.E164)
            } else null
        } catch (_: Exception) { null }
    }
}

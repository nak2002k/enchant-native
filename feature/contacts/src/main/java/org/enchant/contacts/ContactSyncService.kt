package org.enchant.contacts

import android.content.ContentResolver
import android.database.Cursor
import android.provider.ContactsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.enchant.core.network.ApiClient
import org.enchant.core.base.SecurePreferences
import org.enchant.core.auth.AuthConstants
import org.enchant.core.crypto.CryptoPrimitives

data class PhoneContact(
    val name: String,
    val normalizedE164: String
)

data class MatchedContact(
    val userId: String,
    val displayName: String?,
    val username: String?,
    val avatarMediaId: String?
)

sealed class ContactSyncError : Exception() {
    data object PermissionDenied : ContactSyncError()
    data class Network(override val message: String) : ContactSyncError()
    data class Server(override val message: String) : ContactSyncError()
    data class Unknown(override val message: String) : ContactSyncError()
}

class ContactSyncService(
    private val apiClient: ApiClient,
    private val contentResolver: ContentResolver
) {
    companion object {
        private const val BATCH_SIZE = 1000
        private const val DEBOUNCE_MS = 2000L
    }

    private val _syncTrigger = MutableStateFlow(0L)

    suspend fun debouncedSyncContacts(): Result<List<MatchedContact>> {
        _syncTrigger.value = System.currentTimeMillis()
        return debouncedSyncInternal()
    }

    private suspend fun debouncedSyncInternal(): Result<List<MatchedContact>> {
        val triggerTime = _syncTrigger.value
        delay(DEBOUNCE_MS)
        if (_syncTrigger.value != triggerTime) {
            return debouncedSyncInternal()
        }
        return syncContacts()
    }

    suspend fun syncContacts(): Result<List<MatchedContact>> = withContext(Dispatchers.Default) {
        try {
            val deviceContacts = readDeviceContacts()
            if (deviceContacts.isEmpty()) return@withContext Result.success(emptyList())

            val hashedNumbers = deviceContacts.map { hashPhoneNumber(it.normalizedE164) }
            val allMatched = mutableListOf<MatchedContact>()

            hashedNumbers.chunked(BATCH_SIZE).forEach { batch ->
                val body = buildJsonObject {
                    put("phone_hashes", kotlinx.serialization.json.buildJsonArray {
                        batch.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
                    })
                }

                val response = apiClient.post("/v1/contacts/match", body)
                response.fold(
                    onSuccess = { json ->
                        val matched = json["matches"]?.jsonArray?.map { item ->
                            val obj = item.jsonObject
                            MatchedContact(
                                userId = obj["user_id"]?.jsonPrimitive?.content ?: "",
                                displayName = obj["display_name"]?.jsonPrimitive?.content,
                                username = obj["username"]?.jsonPrimitive?.content,
                                avatarMediaId = obj["avatar_media_id"]?.jsonPrimitive?.content
                            )
                        } ?: emptyList()
                        allMatched.addAll(matched)
                    },
                    onFailure = { return@withContext Result.failure(it) }
                )
            }
            Result.success(allMatched)
        } catch (e: SecurityException) {
            Result.failure(ContactSyncError.PermissionDenied)
        } catch (e: java.net.UnknownHostException) {
            Result.failure(ContactSyncError.Network(e.message ?: "No network connection"))
        } catch (e: Exception) {
            Result.failure(ContactSyncError.Unknown(e.message ?: "Unknown error"))
        }
    }

    /**
     * Phone hash uses HMAC-SHA256 keyed by the per-user phone_salt returned at
     * auth time, so hashes are not dictionary-reversible without the salt.
     * The backend stores the same base64url(HMAC-SHA256(salt, phone)) value.
     */
    fun hashPhoneNumber(phone: String): String {
        val saltB64 = SecurePreferences.getString(AuthConstants.PHONE_SALT_KEY)
            ?: return ""
        val salt = CryptoPrimitives.base64UrlDecode(saltB64)
        val mac = CryptoPrimitives.hmacSha256(salt, phone.toByteArray(Charsets.UTF_8))
        return CryptoPrimitives.base64UrlEncode(mac)
    }

    suspend fun readDeviceContacts(): List<PhoneContact> = withContext(Dispatchers.IO) {
        val contacts = mutableListOf<PhoneContact>()
        val seen = mutableSetOf<String>()
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
                    if (e164 != null && seen.add(e164)) {
                        contacts.add(PhoneContact(name = name, normalizedE164 = e164))
                    }
                }
            }
        } catch (e: SecurityException) {
            throw e
        } finally {
            cursor?.close()
        }
        contacts
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

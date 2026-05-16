package org.enchant.contacts.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.enchant.core.database.DatabasePool
import org.enchant.core.database.entity.RecipientEntity
import org.enchant.core.database.util.CursorMapper
import org.enchant.core.network.ApiClient

data class Contact(
    val userId: String,
    val username: String? = null,
    val displayName: String? = null,
    val phoneNumber: String? = null,
    val avatarMediaId: String? = null,
    val customName: String? = null,
    val isBlocked: Boolean = false,
    val addedTs: String? = null
)

sealed class ContactResult {
    data class Added(val added: Boolean) : ContactResult()
    data class Removed(val removed: Boolean) : ContactResult()
    data class Matches(val matches: List<Contact>) : ContactResult()
    data class Failed(val error: String) : ContactResult()
}

class ContactsRepository(
    private val apiClient: ApiClient,
    private val pool: DatabasePool
) {
    suspend fun addContact(contactUserId: String, customName: String? = null): ContactResult {
        return withContext(Dispatchers.Default) {
            try {
                val response = apiClient.post("/v1/contacts", buildJsonObject {
                    put("contact_user_id", contactUserId)
                    if (customName != null) put("custom_name", customName)
                })
                response.fold(
                    onSuccess = { ContactResult.Added(true) },
                    onFailure = { ContactResult.Failed(it.message ?: "Failed to add contact") }
                )
            } catch (e: Exception) {
                ContactResult.Failed(e.message ?: "Network error")
            }
        }
    }

    suspend fun getContacts(): List<Contact> = withContext(Dispatchers.Default) {
        try {
            val response = apiClient.get("/v1/contacts")
            response.fold(
                onSuccess = { json ->
                    val contacts = json["contacts"]?.jsonArray?.map { item ->
                        val obj = item.jsonObject
                        Contact(
                            userId = obj["contact_user_id"]?.jsonPrimitive?.content ?: "",
                            username = obj["username"]?.jsonPrimitive?.content,
                            displayName = obj["custom_name"]?.jsonPrimitive?.content ?: obj["username"]?.jsonPrimitive?.content,
                            customName = obj["custom_name"]?.jsonPrimitive?.content,
                            addedTs = obj["added_ts"]?.jsonPrimitive?.content
                        )
                    } ?: emptyList()
                    pool.write { db ->
                        db.execSQL("DELETE FROM recipients")
                        contacts.forEach { c ->
                            db.execSQL("""
                                INSERT OR REPLACE INTO recipients (recipient_id, username, display_name)
                                VALUES (?, ?, ?)
                            """, arrayOf(c.userId, c.username, c.displayName))
                        }
                    }
                    contacts
                },
                onFailure = {
                    pool.read { db ->
                        CursorMapper.mapToList<RecipientEntity>(
                            db.query("SELECT * FROM recipients", null)
                        ).map { Contact(userId = it.recipientId, username = it.username, displayName = it.displayName) }
                    }
                }
            )
        } catch (e: Exception) {
            pool.read { db ->
                CursorMapper.mapToList<RecipientEntity>(
                    db.query("SELECT * FROM recipients", null)
                ).map { Contact(userId = it.recipientId, username = it.username, displayName = it.displayName) }
            }
        }
    }

    fun getCachedContacts(): Flow<List<Contact>> = callbackFlow {
        val entities = pool.read { db ->
            CursorMapper.mapToList<RecipientEntity>(
                db.query("SELECT * FROM recipients ORDER BY display_name ASC", null)
            )
        }
        trySend(entities.map { Contact(userId = it.recipientId, username = it.username, displayName = it.displayName) })
        awaitClose {}
    }

    suspend fun removeContact(contactUserId: String): ContactResult {
        return withContext(Dispatchers.Default) {
            try {
                val response = apiClient.del("/v1/contacts/$contactUserId")
                response.fold(
                    onSuccess = {
                        pool.write { db ->
                            db.execSQL("DELETE FROM recipients WHERE recipient_id = ?", arrayOf(contactUserId))
                        }
                        ContactResult.Removed(true)
                    },
                    onFailure = { ContactResult.Failed(it.message ?: "Failed to remove contact") }
                )
            } catch (e: Exception) {
                ContactResult.Failed(e.message ?: "Network error")
            }
        }
    }

    suspend fun checkIfContact(contactUserId: String): Boolean = withContext(Dispatchers.Default) {
        try {
            val response = apiClient.get("/v1/contacts/check/$contactUserId")
            response.fold(
                onSuccess = { json -> json["is_contact"]?.jsonPrimitive?.content?.toBoolean() ?: false },
                onFailure = { false }
            )
        } catch (_: Exception) { false }
    }

    suspend fun searchUsers(query: String): List<Contact> = withContext(Dispatchers.Default) {
        if (query.isBlank()) return@withContext emptyList()
        try {
            val response = apiClient.get("/v1/profile/search", mapOf("username" to query))
            response.fold(
                onSuccess = { json ->
                    json["results"]?.jsonArray?.map { item ->
                        val obj = item.jsonObject
                        Contact(
                            userId = obj["user_id"]?.jsonPrimitive?.content ?: "",
                            username = obj["username"]?.jsonPrimitive?.content,
                            displayName = obj["display_name"]?.jsonPrimitive?.content
                        )
                    } ?: emptyList()
                },
                onFailure = { emptyList() }
            )
        } catch (_: Exception) { emptyList() }
    }

    suspend fun blockUser(userId: String): ContactResult {
        return withContext(Dispatchers.Default) {
            try {
                val response = apiClient.post("/v1/blocks/$userId")
                response.fold(
                    onSuccess = {
                        pool.write { db ->
                            db.execSQL("UPDATE recipients SET is_blocked = 1 WHERE recipient_id = ?", arrayOf(userId))
                        }
                        ContactResult.Added(true)
                    },
                    onFailure = { ContactResult.Failed(it.message ?: "Failed to block user") }
                )
            } catch (e: Exception) {
                ContactResult.Failed(e.message ?: "Network error")
            }
        }
    }

    suspend fun unblockUser(userId: String): ContactResult {
        return withContext(Dispatchers.Default) {
            try {
                val response = apiClient.del("/v1/blocks/$userId")
                response.fold(
                    onSuccess = {
                        pool.write { db ->
                            db.execSQL("UPDATE recipients SET is_blocked = 0 WHERE recipient_id = ?", arrayOf(userId))
                        }
                        ContactResult.Removed(true)
                    },
                    onFailure = { ContactResult.Failed(it.message ?: "Failed to unblock user") }
                )
            } catch (e: Exception) {
                ContactResult.Failed(e.message ?: "Network error")
            }
        }
    }

    suspend fun getBlockedUsers(): List<Contact> = withContext(Dispatchers.Default) {
        try {
            val response = apiClient.get("/v1/blocks")
            response.fold(
                onSuccess = { json ->
                    json["blocks"]?.jsonArray?.map { item ->
                        val obj = item.jsonObject
                        Contact(
                            userId = obj["user_id"]?.jsonPrimitive?.content ?: "",
                            username = obj["username"]?.jsonPrimitive?.content
                        )
                    } ?: emptyList()
                },
                onFailure = { emptyList() }
            )
        } catch (_: Exception) { emptyList() }
    }
}

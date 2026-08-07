package org.enchant.contacts.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonPrimitive
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
    val addedTs: String? = null,
    val phoneHash: String? = null
)

data class PhoneMatch(
    val userId: String,
    val username: String?,
    val displayName: String?,
    val phoneHash: String
)

sealed class ContactResult {
    data class Added(val added: Boolean) : ContactResult()
    data class Removed(val removed: Boolean) : ContactResult()
    data class Blocked(val blocked: Boolean) : ContactResult()
    data class Unblocked(val unblocked: Boolean) : ContactResult()
    data class Matches(val matches: List<Contact>) : ContactResult()
    data class RequestSent(val friendRequestId: String) : ContactResult()
    data class RequestPending(val friendRequestId: String) : ContactResult()
    data class RequestAccepted(val friendUserId: String) : ContactResult()
    data class Failed(val error: String) : ContactResult()
}

data class FriendRequestItem(
    val id: String,
    val userId: String,
    val displayName: String?,
    val username: String?,
    val createdAt: String?
)

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
                    onSuccess = { json ->
                        val status = json["status"]?.jsonPrimitive?.content
                        val frId = json["friend_request_id"]?.jsonPrimitive?.content ?: ""
                        when (status) {
                            "mutual", "already_contact" -> ContactResult.Added(true)
                            "request_sent" -> ContactResult.RequestSent(frId)
                            "request_pending" -> ContactResult.RequestPending(frId)
                            else -> ContactResult.Added(true)
                        }
                    },
                    onFailure = { ContactResult.Failed(it.message ?: "Failed to add contact") }
                )
            } catch (e: Exception) {
                ContactResult.Failed(e.message ?: "Network error")
            }
        }
    }

    suspend fun sendFriendRequest(toUserId: String): ContactResult = withContext(Dispatchers.Default) {
        try {
            val response = apiClient.post("/v1/friend-requests", buildJsonObject {
                put("to_user_id", toUserId)
            })
            response.fold(
                onSuccess = { json ->
                    val status = json["status"]?.jsonPrimitive?.content
                    val frId = json["friend_request_id"]?.jsonPrimitive?.content ?: ""
                    when (status) {
                        "already_contact" -> ContactResult.Added(true)
                        else -> ContactResult.RequestSent(frId)
                    }
                },
                onFailure = { ContactResult.Failed(it.message ?: "Failed to send request") }
            )
        } catch (e: Exception) {
            ContactResult.Failed(e.message ?: "Network error")
        }
    }

    suspend fun acceptFriendRequest(requestId: String): ContactResult = withContext(Dispatchers.Default) {
        try {
            val response = apiClient.put("/v1/friend-requests/$requestId/accept", buildJsonObject {})
            response.fold(
                onSuccess = {
                    val friendId = it["friend_user_id"]?.jsonPrimitive?.content ?: ""
                    ContactResult.RequestAccepted(friendId)
                },
                onFailure = { ContactResult.Failed(it.message ?: "Failed to accept") }
            )
        } catch (e: Exception) {
            ContactResult.Failed(e.message ?: "Network error")
        }
    }

    suspend fun declineFriendRequest(requestId: String): ContactResult = withContext(Dispatchers.Default) {
        try {
            val response = apiClient.put("/v1/friend-requests/$requestId/decline", buildJsonObject {})
            response.fold(
                onSuccess = { ContactResult.Removed(true) },
                onFailure = { ContactResult.Failed(it.message ?: "Failed to decline") }
            )
        } catch (e: Exception) {
            ContactResult.Failed(e.message ?: "Network error")
        }
    }

    suspend fun listFriendRequests(): List<FriendRequestItem> = withContext(Dispatchers.Default) {
        try {
            val incoming = apiClient.get("/v1/friend-requests/incoming").getOrNull()
            val items = mutableListOf<FriendRequestItem>()
            (incoming?.get("requests")?.jsonArray ?: kotlinx.serialization.json.JsonArray(emptyList()))
                .forEach { r ->
                    val obj = r.jsonObject
                    items.add(FriendRequestItem(
                        id = obj["id"]?.jsonPrimitive?.content ?: "",
                        userId = obj["from_user_id"]?.jsonPrimitive?.content ?: "",
                        displayName = obj["display_name"]?.jsonPrimitive?.content,
                        username = obj["username"]?.jsonPrimitive?.content,
                        createdAt = obj["created_ts"]?.jsonPrimitive?.content
                    ))
                }
            items
        } catch (e: Exception) { emptyList() }
    }

    suspend fun syncContacts(): List<Contact> = withContext(Dispatchers.Default) {
        try {
            val response = apiClient.get("/v1/contacts")
            response.fold(
                onSuccess = { json ->
                    val contacts = json["contacts"]?.jsonArray?.map { item ->
                        val obj = item.jsonObject
                        Contact(
                            userId = obj["contact_user_id"]?.jsonPrimitive?.content
                                ?: obj["user_id"]?.jsonPrimitive?.content ?: "",
                            username = obj["username"]?.jsonPrimitive?.content,
                            displayName = obj["custom_name"]?.jsonPrimitive?.content
                                ?: obj["display_name"]?.jsonPrimitive?.content
                                ?: obj["username"]?.jsonPrimitive?.content,
                            customName = obj["custom_name"]?.jsonPrimitive?.content,
                            addedTs = obj["added_ts"]?.jsonPrimitive?.content
                        )
                    } ?: emptyList()
                    pool.write { db ->
                        db.beginTransaction()
                        try {
                            db.execSQL("DELETE FROM recipients")
                            contacts.forEach { c ->
                                db.execSQL("""
                                    INSERT OR REPLACE INTO recipients (recipient_id, username, display_name)
                                    VALUES (?, ?, ?)
                                """, arrayOf(c.userId, c.username, c.displayName))
                            }
                            db.setTransactionSuccessful()
                        } finally {
                            db.endTransaction()
                        }
                    }
                    contacts
                },
                onFailure = {
                    pool.readWith { db ->
                        CursorMapper.mapToList<RecipientEntity>(
                            db.rawQuery("SELECT * FROM recipients", null)
                        ).map { Contact(userId = it.recipientId, username = it.username, displayName = it.displayName) }
                    }
                }
            )
        } catch (e: Exception) {
            pool.readWith { db ->
                CursorMapper.mapToList<RecipientEntity>(
                    db.rawQuery("SELECT * FROM recipients", null)
                ).map { Contact(userId = it.recipientId, username = it.username, displayName = it.displayName) }
            }
        }
    }

    suspend fun getContacts(): List<Contact> = withContext(Dispatchers.Default) {
        pool.readWith { db ->
            CursorMapper.mapToList<RecipientEntity>(
                db.rawQuery("SELECT * FROM recipients ORDER BY display_name ASC", null)
            ).map { Contact(userId = it.recipientId, username = it.username, displayName = it.displayName) }
        }
    }

    fun getCachedContacts(): Flow<List<Contact>> = callbackFlow {
        val collectJob = launch {
            val entities = pool.readWith { db ->
                CursorMapper.mapToList<RecipientEntity>(
                    db.rawQuery("SELECT * FROM recipients ORDER BY display_name ASC", null)
                )
            }
            trySend(entities.map { Contact(userId = it.recipientId, username = it.username, displayName = it.displayName) })
        }
        awaitClose { collectJob.cancel() }
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
                        ContactResult.Blocked(true)
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
                        ContactResult.Unblocked(true)
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

    suspend fun matchPhoneContacts(phoneHashes: List<String>): List<PhoneMatch> =
        withContext(Dispatchers.Default) {
            if (phoneHashes.isEmpty()) return@withContext emptyList()
            val allMatches = mutableListOf<PhoneMatch>()
            try {
                phoneHashes.chunked(1000).forEach { batch ->
                    val response = apiClient.post("/v1/contacts/match", buildJsonObject {
                        put("phone_hashes", buildJsonArray {
                            batch.forEach { hash -> add(JsonPrimitive(hash)) }
                        })
                    })
                    response.fold(
                        onSuccess = { json ->
                            val matches = json["matches"]?.jsonArray?.map { item ->
                                val obj = item.jsonObject
                                PhoneMatch(
                                    userId = obj["user_id"]?.jsonPrimitive?.content ?: "",
                                    username = obj["username"]?.jsonPrimitive?.content,
                                    displayName = obj["display_name"]?.jsonPrimitive?.content,
                                    phoneHash = obj["phone_hash"]?.jsonPrimitive?.content ?: ""
                                )
                            } ?: emptyList()
                            allMatches.addAll(matches)
                        },
                        onFailure = { }
                    )
                }
                allMatches
            } catch (_: Exception) { emptyList() }
        }
}

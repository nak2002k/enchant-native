package org.enchant.groups.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.enchant.core.database.DatabasePool
import org.enchant.core.database.entity.GroupEntity
import org.enchant.core.database.entity.GroupMemberEntity
import org.enchant.core.database.util.CursorMapper
import org.enchant.core.network.ApiClient

data class Group(
    val groupId: String,
    val name: String,
    val description: String? = null,
    val avatarMediaId: String? = null,
    val myRole: MemberRole = MemberRole.MEMBER,
    val memberCount: Int = 0,
    val inviteLink: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class GroupMember(
    val userId: String,
    val role: MemberRole,
    val displayName: String? = null,
    val username: String? = null,
    val joinedAt: Long? = null
)

data class InviteLink(
    val linkCode: String,
    val expiresTs: String? = null,
    val maxUses: Int = 0
)

data class JoinRequest(
    val requestId: String,
    val requesterUserId: String,
    val username: String? = null,
    val status: String,
    val requestedTs: String? = null
)

enum class MemberRole(val value: String) {
    OWNER("owner"),
    ADMIN("admin"),
    SUPERADMIN("superadmin"),
    MEMBER("member");

    companion object {
        fun fromString(value: String): MemberRole = entries.find {
            it.value == value.lowercase()
        } ?: MEMBER
    }
}

sealed class GroupResult {
    data class Success(val groupId: String, val name: String, val memberCount: Int, val role: MemberRole = MemberRole.MEMBER) : GroupResult()
    data class MemberAdded(val added: Int) : GroupResult()
    data class MemberRemoved(val removed: Boolean) : GroupResult()
    data class Updated(val updated: Boolean) : GroupResult()
    data class Deleted(val deleted: Boolean) : GroupResult()
    data class InviteCreated(val linkCode: String, val expiresTs: String?) : GroupResult()
    data class Joined(val groupId: String, val name: String) : GroupResult()
    data class Preview(val name: String, val description: String?, val memberCount: Int) : GroupResult()
    data class InviteRevoked(val revoked: Boolean) : GroupResult()
    data class RequestApproved(val approved: Boolean) : GroupResult()
    data class Failed(val error: String) : GroupResult()
}

class GroupsRepository(
    private val apiClient: ApiClient,
    private val pool: DatabasePool
) {
    suspend fun createGroup(
        name: String,
        description: String?,
        initialMemberIds: List<String>?,
        addMembersPolicy: String = "ALL_MEMBERS",
        joinType: String = "INVITE_ONLY"
    ): GroupResult {
        return withContext(Dispatchers.Default) {
            try {
                if (name.isBlank() || name.length > 100) {
                    return@withContext GroupResult.Failed("Name must be 1-100 characters")
                }
                if (description != null && description.length > 512) {
                    return@withContext GroupResult.Failed("Description must be max 512 characters")
                }
                val body = buildJsonObject {
                    put("name", name)
                    if (description != null) put("description", description)
                    if (initialMemberIds != null) {
                        put("initial_member_ids", initialMemberIds.joinToString(","))
                    }
                    put("add_members_policy", addMembersPolicy)
                    put("join_type", joinType)
                }
                val response = apiClient.post("/v1/groups", body)
                response.fold(
                    onSuccess = { json ->
                        val groupId = json["group_id"]?.jsonPrimitive?.content ?: ""
                        val groupName = json["name"]?.jsonPrimitive?.content ?: name
                        val count = json["member_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                        pool.write { db ->
                            db.execSQL("""
                                INSERT OR REPLACE INTO groups_table (group_id, name, description, my_role, member_count)
                                VALUES (?, ?, ?, ?, ?)
                            """, arrayOf(groupId, name, description, MemberRole.OWNER.value, count.toString()))
                        }
                        GroupResult.Success(groupId, groupName, count)
                    },
                    onFailure = { GroupResult.Failed(it.message ?: "Failed to create group") }
                )
            } catch (e: Exception) {
                GroupResult.Failed(e.message ?: "Network error")
            }
        }
    }

    suspend fun getGroups(): List<Group> = withContext(Dispatchers.Default) {
        try {
            val response = apiClient.get("/v1/groups")
            response.fold(
                onSuccess = { json ->
                    val groups = json["groups"]?.jsonArray?.map { item ->
                        val obj = item.jsonObject
                        Group(
                            groupId = obj["group_id"]?.jsonPrimitive?.content ?: "",
                            name = obj["name"]?.jsonPrimitive?.content ?: "",
                            myRole = MemberRole.fromString(obj["role"]?.jsonPrimitive?.content ?: "member"),
                            memberCount = obj["member_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                        )
                    } ?: emptyList()
                    pool.write { db ->
                        db.beginTransaction()
                        try {
                            db.execSQL("DELETE FROM groups_table")
                            groups.forEach { g ->
                                db.execSQL("""
                                    INSERT OR REPLACE INTO groups_table (group_id, name, my_role, member_count)
                                    VALUES (?, ?, ?, ?)
                                """, arrayOf(g.groupId, g.name, g.myRole.value, g.memberCount.toString()))
                            }
                            db.setTransactionSuccessful()
                        } finally {
                            db.endTransaction()
                        }
                    }
                    groups
                },
                onFailure = {
                    pool.readWith { db ->
                        CursorMapper.mapToList<GroupEntity>(
                            db.rawQuery("SELECT * FROM groups_table", null)
                        ).map { Group(groupId = it.groupId, name = it.name, myRole = MemberRole.fromString(it.myRole), memberCount = it.memberCount) }
                    }
                }
            )
        } catch (e: Exception) {
            pool.readWith { db ->
                CursorMapper.mapToList<GroupEntity>(
                    db.rawQuery("SELECT * FROM groups_table", null)
                ).map { Group(groupId = it.groupId, name = it.name, myRole = MemberRole.fromString(it.myRole), memberCount = it.memberCount) }
            }
        }
    }

    suspend fun getCachedGroups(): List<Group> = withContext(Dispatchers.Default) {
        pool.readWith { db ->
            CursorMapper.mapToList<GroupEntity>(db.rawQuery("SELECT * FROM groups_table ORDER BY name", null))
        }.map { entity ->
            Group(
                groupId = entity.groupId,
                name = entity.name,
                myRole = MemberRole.fromString(entity.myRole),
                memberCount = entity.memberCount
            )
        }
    }

    suspend fun getGroupInfo(groupId: String): GroupResult {
        return withContext(Dispatchers.Default) {
            try {
                val response = apiClient.get("/v1/groups/$groupId")
                response.fold(
                    onSuccess = { json ->
                        val name = json["name"]?.jsonPrimitive?.content ?: ""
                        val desc = json["description"]?.jsonPrimitive?.content
                        val count = json["member_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                        val role = MemberRole.fromString(json["role"]?.jsonPrimitive?.content ?: "member")
                        GroupResult.Success(groupId, name, count, role)
                    },
                    onFailure = { GroupResult.Failed(it.message ?: "Failed to fetch group") }
                )
            } catch (e: Exception) {
                GroupResult.Failed(e.message ?: "Network error")
            }
        }
    }

    suspend fun getMembers(groupId: String): List<GroupMember> = withContext(Dispatchers.Default) {
        try {
            val response = apiClient.get("/v1/groups/$groupId/members")
            response.fold(
                onSuccess = { json ->
                    json["members"]?.jsonArray?.map { item ->
                        val obj = item.jsonObject
                        GroupMember(
                            userId = obj["user_id"]?.jsonPrimitive?.content ?: "",
                            role = MemberRole.fromString(obj["role"]?.jsonPrimitive?.content ?: "member"),
                            displayName = obj["display_name"]?.jsonPrimitive?.content,
                            username = obj["username"]?.jsonPrimitive?.content,
                            joinedAt = obj["joined_at"]?.jsonPrimitive?.content?.toLongOrNull()
                        )
                    } ?: emptyList()
                },
                onFailure = { emptyList() }
            )
        } catch (_: Exception) { emptyList() }
    }

    suspend fun addMembers(groupId: String, userIds: List<String>): GroupResult {
        if (userIds.isEmpty()) return GroupResult.Failed("At least one user ID required")
        return withContext(Dispatchers.Default) {
            try {
                val response = apiClient.post("/v1/groups/$groupId/members", buildJsonObject {
                    put("user_ids", userIds.joinToString(","))
                })
                response.fold(
                    onSuccess = { GroupResult.MemberAdded(it["added"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0) },
                    onFailure = { GroupResult.Failed(it.message ?: "Failed to add members") }
                )
            } catch (e: Exception) {
                GroupResult.Failed(e.message ?: "Network error")
            }
        }
    }

    suspend fun removeMember(groupId: String, userId: String): GroupResult {
        return withContext(Dispatchers.Default) {
            try {
                val response = apiClient.del("/v1/groups/$groupId/members/$userId")
                response.fold(
                    onSuccess = { GroupResult.MemberRemoved(true) },
                    onFailure = { GroupResult.Failed(it.message ?: "Failed to remove member") }
                )
            } catch (e: Exception) {
                GroupResult.Failed(e.message ?: "Network error")
            }
        }
    }

    suspend fun updateGroup(groupId: String, name: String? = null, description: String? = null): GroupResult {
        return withContext(Dispatchers.Default) {
            try {
                val body = buildJsonObject {
                    if (name != null) put("name", name)
                    if (description != null) put("description", description)
                }
                val response = apiClient.put("/v1/groups/$groupId", body)
                response.fold(
                    onSuccess = { GroupResult.Updated(true) },
                    onFailure = { GroupResult.Failed(it.message ?: "Failed to update group") }
                )
            } catch (e: Exception) {
                GroupResult.Failed(e.message ?: "Network error")
            }
        }
    }

    suspend fun updateMemberRole(groupId: String, userId: String, role: MemberRole): GroupResult {
        if (role == MemberRole.OWNER) return GroupResult.Failed("Cannot assign owner role via update")
        return withContext(Dispatchers.Default) {
            try {
                val response = apiClient.put("/v1/groups/$groupId/members/$userId/role", buildJsonObject {
                    put("role", role.value)
                })
                response.fold(
                    onSuccess = { GroupResult.Updated(true) },
                    onFailure = { GroupResult.Failed(it.message ?: "Failed to update role") }
                )
            } catch (e: Exception) {
                GroupResult.Failed(e.message ?: "Network error")
            }
        }
    }

    suspend fun transferOwnership(groupId: String, newOwnerUserId: String): GroupResult {
        return withContext(Dispatchers.Default) {
            try {
                val response = apiClient.put("/v1/groups/$groupId/owner", buildJsonObject {
                    put("new_owner_user_id", newOwnerUserId)
                })
                response.fold(
                    onSuccess = { GroupResult.Updated(true) },
                    onFailure = { GroupResult.Failed(it.message ?: "Failed to transfer ownership") }
                )
            } catch (e: Exception) {
                GroupResult.Failed(e.message ?: "Network error")
            }
        }
    }

    suspend fun deleteGroup(groupId: String): GroupResult {
        return withContext(Dispatchers.Default) {
            try {
                val response = apiClient.del("/v1/groups/$groupId")
                response.fold(
                    onSuccess = {
                        pool.write { db ->
                            db.execSQL("DELETE FROM groups_table WHERE group_id = ?", arrayOf(groupId))
                            db.execSQL("DELETE FROM group_members WHERE group_id = ?", arrayOf(groupId))
                            try { db.execSQL("DELETE FROM messages_table WHERE group_id = ?", arrayOf(groupId)) } catch (_: Exception) {}
                            try { db.execSQL("DELETE FROM join_requests_table WHERE group_id = ?", arrayOf(groupId)) } catch (_: Exception) {}
                        }
                        GroupResult.Deleted(true)
                    },
                    onFailure = { GroupResult.Failed(it.message ?: "Failed to delete group") }
                )
            } catch (e: Exception) {
                GroupResult.Failed(e.message ?: "Network error")
            }
        }
    }

    suspend fun leaveGroup(groupId: String): GroupResult {
        return withContext(Dispatchers.Default) {
            try {
                val response = apiClient.post("/v1/groups/$groupId/leave")
                response.fold(
                    onSuccess = {
                        pool.write { db ->
                            db.execSQL("DELETE FROM groups_table WHERE group_id = ?", arrayOf(groupId))
                            db.execSQL("DELETE FROM group_members WHERE group_id = ?", arrayOf(groupId))
                        }
                        GroupResult.Deleted(true)
                    },
                    onFailure = { GroupResult.Failed(it.message ?: "Failed to leave group") }
                )
            } catch (e: Exception) {
                GroupResult.Failed(e.message ?: "Network error")
            }
        }
    }

    suspend fun createInviteLink(groupId: String, maxUses: Int = 10, expiresTs: String? = null): GroupResult {
        return withContext(Dispatchers.Default) {
            try {
                val response = apiClient.post("/v1/groups/$groupId/invite-link", buildJsonObject {
                    put("max_uses", maxUses)
                    if (expiresTs != null) put("expires_ts", expiresTs)
                })
                response.fold(
                    onSuccess = { json ->
                        GroupResult.InviteCreated(
                            linkCode = json["link_code"]?.jsonPrimitive?.content ?: "",
                            expiresTs = json["expires_ts"]?.jsonPrimitive?.content
                        )
                    },
                    onFailure = { GroupResult.Failed(it.message ?: "Failed to create invite link") }
                )
            } catch (e: Exception) {
                GroupResult.Failed(e.message ?: "Network error")
            }
        }
    }

    suspend fun joinViaLink(linkCode: String): GroupResult {
        return withContext(Dispatchers.Default) {
            try {
                val response = apiClient.post("/v1/groups/join/$linkCode")
                response.fold(
                    onSuccess = { json ->
                        GroupResult.Joined(
                            groupId = json["group_id"]?.jsonPrimitive?.content ?: "",
                            name = json["name"]?.jsonPrimitive?.content ?: ""
                        )
                    },
                    onFailure = { GroupResult.Failed(it.message ?: "Failed to join group") }
                )
            } catch (e: Exception) {
                GroupResult.Failed(e.message ?: "Network error")
            }
        }
    }

    suspend fun previewInviteLink(linkCode: String): GroupResult {
        return withContext(Dispatchers.Default) {
            try {
                val response = apiClient.get("/v1/groups/join/$linkCode")
                response.fold(
                    onSuccess = { json ->
                        GroupResult.Preview(
                            name = json["name"]?.jsonPrimitive?.content ?: "",
                            description = json["description"]?.jsonPrimitive?.content,
                            memberCount = json["member_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                        )
                    },
                    onFailure = { GroupResult.Failed(it.message ?: "Failed to preview link") }
                )
            } catch (e: Exception) {
                GroupResult.Failed(e.message ?: "Network error")
            }
        }
    }

    suspend fun revokeInviteLink(groupId: String, linkId: String): GroupResult {
        return withContext(Dispatchers.Default) {
            try {
                val response = apiClient.del("/v1/groups/$groupId/invite-link/$linkId")
                response.fold(
                    onSuccess = { GroupResult.InviteRevoked(true) },
                    onFailure = { GroupResult.Failed(it.message ?: "Failed to revoke link") }
                )
            } catch (e: Exception) {
                GroupResult.Failed(e.message ?: "Network error")
            }
        }
    }

    suspend fun getJoinRequests(groupId: String): List<JoinRequest> = withContext(Dispatchers.Default) {
        try {
            val response = apiClient.get("/v1/groups/$groupId/join-requests")
            response.fold(
                onSuccess = { json ->
                    json["requests"]?.jsonArray?.map { item ->
                        val obj = item.jsonObject
                        JoinRequest(
                            requestId = obj["request_id"]?.jsonPrimitive?.content ?: "",
                            requesterUserId = obj["requester_user_id"]?.jsonPrimitive?.content ?: "",
                            status = obj["status"]?.jsonPrimitive?.content ?: "",
                            requestedTs = obj["requested_ts"]?.jsonPrimitive?.content
                        )
                    } ?: emptyList()
                },
                onFailure = { emptyList() }
            )
        } catch (_: Exception) { emptyList() }
    }

    suspend fun approveJoinRequest(groupId: String, requestId: String, approve: Boolean): GroupResult {
        return withContext(Dispatchers.Default) {
            try {
                val response = apiClient.put("/v1/groups/$groupId/join-requests/$requestId", buildJsonObject {
                    put("approve", approve)
                })
                response.fold(
                    onSuccess = { GroupResult.RequestApproved(approve) },
                    onFailure = { GroupResult.Failed(it.message ?: "Failed to process request") }
                )
            } catch (e: Exception) {
                GroupResult.Failed(e.message ?: "Network error")
            }
        }
    }

    suspend fun updateDisappearingMessages(groupId: String, enabled: Boolean, durationSeconds: Int): GroupResult {
        return withContext(Dispatchers.Default) {
            try {
                val body = buildJsonObject {
                    put("disappear_timer_seconds", if (enabled) durationSeconds else 0)
                }
                val response = apiClient.put("/v1/groups/$groupId/settings", body)
                response.fold(
                    onSuccess = { GroupResult.Updated(true) },
                    onFailure = { GroupResult.Failed(it.message ?: "Failed to update disappearing messages") }
                )
            } catch (e: Exception) {
                GroupResult.Failed(e.message ?: "Network error")
            }
        }
    }
}

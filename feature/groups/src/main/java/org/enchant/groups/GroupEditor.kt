package org.enchant.groups

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.enchant.core.network.ApiClient
import kotlin.random.Random

enum class GroupAccessPolicy {
    ALL_MEMBERS, ADMIN_ONLY
}

enum class GroupLinkState {
    DISABLED, ENABLED, APPROVAL_REQUIRED
}

sealed class GroupEditResult {
    data object Success : GroupEditResult()
    data class Conflict(val serverRevision: String) : GroupEditResult()
    data class Failure(val reason: String, val isRetryable: Boolean) : GroupEditResult()
}

class GroupEditor(
    private val groupId: String,
    private val apiClient: ApiClient
) {
    private val mutex = Mutex()
    private var revision: String? = null

    suspend fun addMembers(userIds: List<String>): GroupEditResult = mutex.withLock {
        executeWithRetry {
            apiClient.post("/v1/groups/$groupId/members", buildJsonObject {
                put("user_ids", userIds.joinToString(","))
            })
        }
    }

    suspend fun removeMember(userId: String): GroupEditResult = mutex.withLock {
        executeWithRetry {
            apiClient.del("/v1/groups/$groupId/members/$userId")
        }
    }

    suspend fun setMemberAdmin(userId: String, isAdmin: Boolean): GroupEditResult = mutex.withLock {
        executeWithRetry {
            apiClient.put("/v1/groups/$groupId/members/$userId/role", buildJsonObject {
                put("role", if (isAdmin) "admin" else "member")
            })
        }
    }

    suspend fun updateGroupTimer(seconds: Int): GroupEditResult = mutex.withLock {
        executeWithRetry {
            apiClient.put("/v1/groups/$groupId", buildJsonObject {
                put("disappearing_timer", seconds)
            })
        }
    }

    suspend fun updateAttributesRights(policy: GroupAccessPolicy): GroupEditResult = mutex.withLock {
        executeWithRetry {
            apiClient.put("/v1/groups/$groupId", buildJsonObject {
                put("attributes_access", policy.name)
            })
        }
    }

    suspend fun updateMembershipRights(policy: GroupAccessPolicy): GroupEditResult = mutex.withLock {
        executeWithRetry {
            apiClient.put("/v1/groups/$groupId", buildJsonObject {
                put("membership_access", policy.name)
            })
        }
    }

    suspend fun setAnnouncementGroup(isAnnouncementOnly: Boolean): GroupEditResult = mutex.withLock {
        executeWithRetry {
            apiClient.put("/v1/groups/$groupId", buildJsonObject {
                put("is_announcement_group", isAnnouncementOnly)
            })
        }
    }

    suspend fun revokeInvites(userIds: List<String>): GroupEditResult = mutex.withLock {
        executeWithRetry {
            apiClient.post("/v1/groups/$groupId/revoke-invites", buildJsonObject {
                put("user_ids", userIds.joinToString(","))
            })
        }
    }

    suspend fun approveJoinRequest(requestId: String): GroupEditResult = mutex.withLock {
        executeWithRetry {
            apiClient.put("/v1/groups/$groupId/join-requests/$requestId", buildJsonObject {
                put("approve", true)
            })
        }
    }

    suspend fun denyJoinRequest(requestId: String): GroupEditResult = mutex.withLock {
        executeWithRetry {
            apiClient.put("/v1/groups/$groupId/join-requests/$requestId", buildJsonObject {
                put("approve", false)
            })
        }
    }

    suspend fun banUser(userId: String): GroupEditResult = mutex.withLock {
        executeWithRetry {
            apiClient.post("/v1/groups/$groupId/bans", buildJsonObject {
                put("user_id", userId)
            })
        }
    }

    suspend fun unbanUser(userId: String): GroupEditResult = mutex.withLock {
        executeWithRetry {
            apiClient.del("/v1/groups/$groupId/bans/$userId")
        }
    }

    suspend fun ejectMember(userId: String, block: Boolean, removeMessages: Boolean): GroupEditResult = mutex.withLock {
        executeWithRetry {
            apiClient.post("/v1/groups/$groupId/eject", buildJsonObject {
                put("user_id", userId)
                put("block", block)
                put("remove_messages", removeMessages)
            })
        }
    }

    suspend fun terminateGroup(): GroupEditResult = mutex.withLock {
        executeWithRetry {
            apiClient.del("/v1/groups/$groupId")
        }
    }

    suspend fun acceptInvite(): GroupEditResult = mutex.withLock {
        executeWithRetry {
            apiClient.post("/v1/groups/$groupId/accept")
        }
    }

    suspend fun cycleGroupLinkPassword(): GroupEditResult = mutex.withLock {
        executeWithRetry {
            apiClient.post("/v1/groups/$groupId/cycle-link-password")
        }
    }

    suspend fun setJoinByGroupLinkState(state: GroupLinkState): GroupEditResult = mutex.withLock {
        executeWithRetry {
            apiClient.put("/v1/groups/$groupId", buildJsonObject {
                put("join_by_link", state.name)
            })
        }
    }

    private suspend fun executeWithRetry(
        maxRetries: Int = 3,
        block: suspend () -> Result<JsonObject>
    ): GroupEditResult {
        var lastError: GroupEditResult = GroupEditResult.Failure("Unknown error", false)
        for (attempt in 1..maxRetries) {
            val response = block()
            val result = response.fold(
                onSuccess = {
                    updateRevision(it)
                    GroupEditResult.Success
                },
                onFailure = { error ->
                    val msg = error.message ?: ""
                    when {
                        msg.contains("409") || msg.contains("Conflict") -> {
                            GroupEditResult.Conflict(extractRevision(error))
                        }
                        attempt < maxRetries -> null
                        else -> GroupEditResult.Failure(msg, true)
                    }
                }
            )
            if (result != null) return result
            if (attempt < maxRetries) {
                val baseDelay = 1000L * attempt
                val jitter = Random.nextLong(0, baseDelay / 2)
                kotlinx.coroutines.delay(baseDelay + jitter)
            }
        }
        return lastError
    }

    private fun updateRevision(json: JsonObject) {
        json["revision"]?.jsonPrimitive?.content?.let {
            revision = it
        }
    }

    private fun extractRevision(error: Throwable): String {
        val msg = error.message ?: return ""
        val patterns = listOf("revision:", "revision=", "current_revision:")
        for (pattern in patterns) {
            val idx = msg.indexOf(pattern, ignoreCase = true)
            if (idx >= 0) {
                return msg.substring(idx + pattern.length).trim().take(20).filter { it.isLetterOrDigit() || it == '-' }
            }
        }
        return ""
    }
}

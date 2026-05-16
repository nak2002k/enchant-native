package org.enchant.groups

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.enchant.core.database.dao.GroupDao
import org.enchant.core.database.dao.GroupDao.GroupEntity
import org.enchant.core.network.ApiClient

data class GroupUpdateResult(
    val success: Boolean,
    val newRevision: String? = null,
    val error: String? = null
)

data class GroupChangeLogEntry(
    val revision: String,
    val timestamp: Long,
    val changes: List<String>
)

class GroupStateProcessor(
    private val apiClient: ApiClient,
    private val groupDao: GroupDao
) {
    suspend fun processGroupUpdate(groupId: String, update: JsonObject): GroupUpdateResult {
        return try {
            val revision = update["revision"]?.jsonPrimitive?.content ?: ""
            val name = update["name"]?.jsonPrimitive?.content
            val description = update["description"]?.jsonPrimitive?.content
            val avatar = update["avatar_media_id"]?.jsonPrimitive?.content

            if (name != null || description != null || avatar != null) {
                groupDao.update(groupId, name, description, avatar)
            }
            GroupUpdateResult(true, revision)
        } catch (e: Exception) {
            GroupUpdateResult(false, error = e.message)
        }
    }

    suspend fun forceUpdateFromServer(groupId: String): GroupUpdateResult {
        return try {
            val response = apiClient.get("/v1/groups/$groupId")
            response.fold(
                onSuccess = { json ->
                    val name = json["name"]?.jsonPrimitive?.content ?: ""
                    val description = json["description"]?.jsonPrimitive?.content
                    val avatar = json["avatar_media_id"]?.jsonPrimitive?.content
                    val revision = json["revision"]?.jsonPrimitive?.content ?: ""

                    groupDao.insert(
                        GroupEntity(
                            groupId = groupId,
                            name = name,
                            description = description,
                            avatarMediaId = avatar,
                            myRole = json["my_role"]?.jsonPrimitive?.content ?: "member",
                            memberCount = json["member_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                        )
                    )
                    GroupUpdateResult(true, revision)
                },
                onFailure = { GroupUpdateResult(false, error = it.message) }
            )
        } catch (e: Exception) {
            GroupUpdateResult(false, error = e.message)
        }
    }

    suspend fun getGroupChangeLog(groupId: String, sinceRevision: String): List<GroupChangeLogEntry> {
        return try {
            val response = apiClient.get("/v1/groups/$groupId/changelog", mapOf("since" to sinceRevision))
            response.fold(
                onSuccess = { json ->
                    json["entries"]?.jsonArray?.map { item ->
                        val obj = item.jsonObject
                        GroupChangeLogEntry(
                            revision = obj["revision"]?.jsonPrimitive?.content ?: "",
                            timestamp = obj["timestamp"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
                            changes = obj["changes"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
                        )
                    } ?: emptyList()
                },
                onFailure = { emptyList() }
            )
        } catch (_: Exception) { emptyList() }
    }

    suspend fun handleP2PChange(groupId: String, update: JsonObject): GroupUpdateResult {
        return processGroupUpdate(groupId, update)
    }

    suspend fun updateLocalGroupToRevision(groupId: String, targetRevision: String): GroupUpdateResult {
        val current = groupDao.getById(groupId)
        val changeLog = getGroupChangeLog(groupId, current?.myRole ?: "0")
        return try {
            for (entry in changeLog) {
                val response = apiClient.get("/v1/groups/$groupId/revision/${entry.revision}")
                response.fold(
                    onSuccess = { json -> processGroupUpdate(groupId, json) },
                    onFailure = { return GroupUpdateResult(false, error = it.message) }
                )
            }
            GroupUpdateResult(true, targetRevision)
        } catch (e: Exception) {
            GroupUpdateResult(false, error = e.message)
        }
    }
}

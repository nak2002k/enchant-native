package org.enchant.calls.calllinks

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.enchant.core.calls.CallLinkCredentials
import org.enchant.core.calls.CallLinkData
import org.enchant.core.calls.CallLinkRestrictions
import org.enchant.core.calls.IceServer
import org.enchant.core.network.ApiClient

class CallLinkManager(private val apiClient: ApiClient) {
    suspend fun createCallLink(name: String, restrictions: CallLinkRestrictions): Result<String> {
        return withContext(Dispatchers.Default) {
            try {
                val response = apiClient.post("/v1/calls/links", buildJsonObject {
                    put("name", name)
                    put("restrictions", restrictions.name)
                })
                response.fold(
                    onSuccess = { json ->
                        val roomId = json["room_id"]?.jsonPrimitive?.content
                        if (roomId != null) Result.success(roomId)
                        else Result.failure(Exception("No room_id in response"))
                    },
                    onFailure = { Result.failure(it) }
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getCallLink(roomId: String): Result<CallLinkData> {
        return withContext(Dispatchers.Default) {
            try {
                val response = apiClient.get("/v1/calls/links/$roomId")
                response.fold(
                    onSuccess = { json ->
                        Result.success(CallLinkData(
                            roomId = json["room_id"]?.jsonPrimitive?.content ?: roomId,
                            name = json["name"]?.jsonPrimitive?.content ?: "",
                            creatorId = json["creator_id"]?.jsonPrimitive?.content ?: "",
                            restrictions = parseRestrictions(json["restrictions"]?.jsonPrimitive?.content),
                            isActive = json["is_active"]?.jsonPrimitive?.content?.toBoolean() ?: true
                        ))
                    },
                    onFailure = { Result.failure(it) }
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun updateCallLinkName(roomId: String, name: String) {
        withContext(Dispatchers.Default) {
            try {
                apiClient.put("/v1/calls/links/$roomId", buildJsonObject { put("name", name) })
            } catch (e: Exception) { android.util.Log.w("Enchant", "silent: ${e.message}") }
        }
    }

    suspend fun updateCallLinkRestrictions(roomId: String, restrictions: CallLinkRestrictions) {
        withContext(Dispatchers.Default) {
            try {
                apiClient.put("/v1/calls/links/$roomId", buildJsonObject { put("restrictions", restrictions.name) })
            } catch (e: Exception) { android.util.Log.w("Enchant", "silent: ${e.message}") }
        }
    }

    suspend fun deleteCallLink(roomId: String) {
        withContext(Dispatchers.Default) {
            try {
                apiClient.del("/v1/calls/links/$roomId")
            } catch (e: Exception) { android.util.Log.w("Enchant", "silent: ${e.message}") }
        }
    }

    suspend fun joinCallLink(roomId: String): Result<CallLinkData> {
        return withContext(Dispatchers.Default) {
            try {
                val linkResult = getCallLink(roomId)
                linkResult
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getCallLinkCredentials(roomId: String): Result<CallLinkCredentials> {
        return withContext(Dispatchers.Default) {
            try {
                Result.success(CallLinkCredentials(
                    roomId = roomId,
                    authToken = "",
                    iceServers = listOf(IceServer(urls = listOf("stun:stun.l.google.com:19302")))
                ))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private fun parseRestrictions(value: String?): CallLinkRestrictions {
        return try { CallLinkRestrictions.valueOf(value ?: "ANYONE") }
        catch (_: Exception) { CallLinkRestrictions.ANYONE }
    }
}

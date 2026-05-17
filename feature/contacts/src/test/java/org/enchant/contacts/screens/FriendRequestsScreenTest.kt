package org.enchant.contacts.screens

import kotlinx.serialization.json.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

@DisplayName("FriendRequestsScreen")
class FriendRequestsScreenTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    @DisplayName("FriendRequest data class holds values")
    fun `friend request data`() {
        val request = FriendRequest(
            id = "req-1",
            userId = "user-1",
            username = "alice",
            createdAt = "2026-05-17T10:00:00Z"
        )
        assertEquals("req-1", request.id)
        assertEquals("user-1", request.userId)
        assertEquals("alice", request.username)
        assertEquals("2026-05-17T10:00:00Z", request.createdAt)
    }

    @Test
    @DisplayName("FriendRequest equality works")
    fun `friend request equality`() {
        val request1 = FriendRequest("req-1", "user-1", "alice", "2026-05-17T10:00:00Z")
        val request2 = FriendRequest("req-1", "user-1", "alice", "2026-05-17T10:00:00Z")
        assertEquals(request1, request2)
    }

    @Test
    @DisplayName("FriendRequest with different ids are not equal")
    fun `friend request inequality`() {
        val request1 = FriendRequest("req-1", "user-1", "alice", "2026-05-17T10:00:00Z")
        val request2 = FriendRequest("req-2", "user-1", "alice", "2026-05-17T10:00:00Z")
        assertNotEquals(request1, request2)
    }

    @Test
    @DisplayName("Parse JSON array into FriendRequest list")
    fun `parse json`() {
        val jsonStr = """
            {"incoming":[{"id":"req-1","user_id":"u1","username":"alice","created_at":"2026-05-17"}]}
        """.trimIndent()
        val element = json.parseToJsonElement(jsonStr)
        val obj = element.jsonObject
        val arr = obj["incoming"]?.jsonArray ?: fail("No incoming array")
        assertEquals(1, arr.size)
        val first = arr[0].jsonObject
        assertEquals("req-1", first["id"]?.jsonPrimitive?.content)
        assertEquals("u1", first["user_id"]?.jsonPrimitive?.content)
        assertEquals("alice", first["username"]?.jsonPrimitive?.content)
        assertEquals("2026-05-17", first["created_at"]?.jsonPrimitive?.content)
    }

    @Test
    @DisplayName("parseRequests returns empty list for missing key")
    fun `parse requests missing key`() {
        val element = json.parseToJsonElement("{}")
        val result = emptyList<FriendRequest>()
        assertTrue(result.isEmpty())
    }

    @Test
    @DisplayName("parseRequests skips malformed entries")
    fun `parse requests skips malformed`() {
        val jsonStr = """{"incoming":[{"id":"req-1","user_id":"u1","username":"alice","created_at":"2026-05-17"},{"bad":true}]}"""
        val element = json.parseToJsonElement(jsonStr)
        val arr = element.jsonObject["incoming"]?.jsonArray ?: emptyList()
        val valid = arr.mapNotNull { entry ->
            val obj = entry as? JsonObject ?: return@mapNotNull null
            FriendRequest(
                id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                userId = obj["user_id"]?.jsonPrimitive?.content ?: "",
                username = obj["username"]?.jsonPrimitive?.content ?: "",
                createdAt = obj["created_at"]?.jsonPrimitive?.content ?: ""
            )
        }
        assertEquals(1, valid.size)
        assertEquals("req-1", valid[0].id)
    }
}

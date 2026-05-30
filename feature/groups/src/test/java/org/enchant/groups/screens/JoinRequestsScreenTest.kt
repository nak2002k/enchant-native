package org.enchant.groups.screens

import kotlinx.serialization.json.*
import org.enchant.groups.data.JoinRequest
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

@DisplayName("JoinRequestsScreen")
class JoinRequestsScreenTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    @DisplayName("JoinRequest data class holds values")
    fun `join request data`() {
        val request = JoinRequest(
            requestId = "jr-1",
            requesterUserId = "user-1",
            username = "bob",
            status = "pending",
            requestedTs = "2026-05-17T10:00:00Z"
        )
        assertEquals("jr-1", request.requestId)
        assertEquals("user-1", request.requesterUserId)
        assertEquals("bob", request.username)
        assertEquals("2026-05-17T10:00:00Z", request.requestedTs)
    }

    @Test
    @DisplayName("Multiple JoinRequests can be stored in a list")
    fun `multiple requests`() {
        val requests = listOf(
            JoinRequest("jr-1", "u1", "alice", "pending", "2026-05-17"),
            JoinRequest("jr-2", "u2", "bob", "pending", "2026-05-16"),
            JoinRequest("jr-3", "u3", "charlie", "pending", "2026-05-15")
        )
        assertEquals(3, requests.size)
        assertEquals("alice", requests[0].username)
        assertEquals("bob", requests[1].username)
        assertEquals("charlie", requests[2].username)
    }

    @Test
    @DisplayName("JoinRequest equality works")
    fun `join request equality`() {
        val r1 = JoinRequest("jr-1", "u1", "alice", "pending", "2026-05-17")
        val r2 = JoinRequest("jr-1", "u1", "alice", "pending", "2026-05-17")
        assertEquals(r1, r2)
    }

    @Test
    @DisplayName("JoinRequest with different ids are not equal")
    fun `join request inequality`() {
        val r1 = JoinRequest("jr-1", "u1", "alice", "pending", "2026-05-17")
        val r2 = JoinRequest("jr-2", "u1", "alice", "pending", "2026-05-17")
        assertNotEquals(r1, r2)
    }

    @Test
    @DisplayName("Parse JSON response into JoinRequest")
    fun `parse json`() {
        val jsonStr = """
            {"requests":[{"request_id":"jr-1","requester_user_id":"u1","username":"alice","status":"pending","requested_ts":"2026-05-17"}]}
        """.trimIndent()
        val obj = json.parseToJsonElement(jsonStr).jsonObject
        val arr = obj["requests"]?.jsonArray ?: fail("No requests array")
        assertEquals(1, arr.size)
        val first = arr[0].jsonObject
        assertEquals("jr-1", first["request_id"]?.jsonPrimitive?.content)
        assertEquals("u1", first["requester_user_id"]?.jsonPrimitive?.content)
        assertEquals("alice", first["username"]?.jsonPrimitive?.content)
        assertEquals("2026-05-17", first["requested_ts"]?.jsonPrimitive?.content)
    }

    @Test
    @DisplayName("Parse JSON with unknown fields does not crash")
    fun `parse json with unknown fields`() {
        val jsonStr = """{"requests":[{"request_id":"jr-1","requester_user_id":"u1","username":"alice","status":"pending","requested_ts":"2026-05-17","extra_field":"ignored"}]}"""
        val obj = json.parseToJsonElement(jsonStr).jsonObject
        val arr = obj["requests"]?.jsonArray
        assertNotNull(arr)
        assertEquals(1, arr?.size)
    }

    @Test
    @DisplayName("Empty requests list parses to empty list")
    fun `empty requests`() {
        val jsonStr = """{"requests":[]}"""
        val obj = json.parseToJsonElement(jsonStr).jsonObject
        val arr = obj["requests"]?.jsonArray ?: fail("No requests array")
        assertTrue(arr.isEmpty())
    }
}

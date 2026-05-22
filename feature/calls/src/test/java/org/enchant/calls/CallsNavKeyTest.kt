package org.enchant.calls

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("CallsNavKey serialization")
class CallsNavKeyTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Nested
    @DisplayName("CallLog")
    inner class CallLog {

        @Test
        fun `serialization round-trip`() {
            val original = CallsNavKey.CallLog
            val serialized = json.encodeToString(CallsNavKey.serializer(), original)
            val deserialized = json.decodeFromString(CallsNavKey.serializer(), serialized)
            assertEquals(original, deserialized)
        }
    }

    @Nested
    @DisplayName("OutgoingCall")
    inner class OutgoingCall {

        @Test
        fun `serialization round-trip with valid recipientId`() {
            val original = CallsNavKey.OutgoingCall(recipientId = 42L)
            val serialized = json.encodeToString(CallsNavKey.serializer(), original)
            val deserialized = json.decodeFromString(CallsNavKey.serializer(), serialized)
            assertEquals(original, deserialized)
        }

        @Test
        fun `serialization round-trip with zero recipientId`() {
            val original = CallsNavKey.OutgoingCall(recipientId = 0L)
            val serialized = json.encodeToString(CallsNavKey.serializer(), original)
            val deserialized = json.decodeFromString(CallsNavKey.serializer(), serialized)
            assertEquals(original, deserialized)
        }

        @Test
        fun `serialization round-trip with negative recipientId`() {
            val original = CallsNavKey.OutgoingCall(recipientId = -1L)
            val serialized = json.encodeToString(CallsNavKey.serializer(), original)
            val deserialized = json.decodeFromString(CallsNavKey.serializer(), serialized)
            assertEquals(original, deserialized)
        }
    }

    @Nested
    @DisplayName("IncomingCall")
    inner class IncomingCall {

        @Test
        fun `serialization round-trip with valid ids`() {
            val original = CallsNavKey.IncomingCall(callerId = 42L, callId = "call-abc-123")
            val serialized = json.encodeToString(CallsNavKey.serializer(), original)
            val deserialized = json.decodeFromString(CallsNavKey.serializer(), serialized)
            assertEquals(original, deserialized)
        }

        @Test
        fun `serialization round-trip with empty callId`() {
            val original = CallsNavKey.IncomingCall(callerId = 1L, callId = "")
            val serialized = json.encodeToString(CallsNavKey.serializer(), original)
            val deserialized = json.decodeFromString(CallsNavKey.serializer(), serialized)
            assertEquals(original, deserialized)
        }
    }

    @Nested
    @DisplayName("ActiveCall")
    inner class ActiveCall {

        @Test
        fun `serialization round-trip with valid callId`() {
            val original = CallsNavKey.ActiveCall(callId = "call-xyz-789")
            val serialized = json.encodeToString(CallsNavKey.serializer(), original)
            val deserialized = json.decodeFromString(CallsNavKey.serializer(), serialized)
            assertEquals(original, deserialized)
        }

        @Test
        fun `serialization round-trip with empty callId`() {
            val original = CallsNavKey.ActiveCall(callId = "")
            val serialized = json.encodeToString(CallsNavKey.serializer(), original)
            val deserialized = json.decodeFromString(CallsNavKey.serializer(), serialized)
            assertEquals(original, deserialized)
        }
    }

    @Nested
    @DisplayName("GroupCall")
    inner class GroupCall {

        @Test
        fun `serialization round-trip with valid groupId`() {
            val original = CallsNavKey.GroupCall(groupId = 99L)
            val serialized = json.encodeToString(CallsNavKey.serializer(), original)
            val deserialized = json.decodeFromString(CallsNavKey.serializer(), serialized)
            assertEquals(original, deserialized)
        }

        @Test
        fun `serialization round-trip with zero groupId`() {
            val original = CallsNavKey.GroupCall(groupId = 0L)
            val serialized = json.encodeToString(CallsNavKey.serializer(), original)
            val deserialized = json.decodeFromString(CallsNavKey.serializer(), serialized)
            assertEquals(original, deserialized)
        }
    }

    @Nested
    @DisplayName("CallLink")
    inner class CallLink {

        @Test
        fun `serialization round-trip with valid linkRoomId`() {
            val original = CallsNavKey.CallLink(linkRoomId = "room-abc")
            val serialized = json.encodeToString(CallsNavKey.serializer(), original)
            val deserialized = json.decodeFromString(CallsNavKey.serializer(), serialized)
            assertEquals(original, deserialized)
        }

        @Test
        fun `serialization round-trip with empty linkRoomId`() {
            val original = CallsNavKey.CallLink(linkRoomId = "")
            val serialized = json.encodeToString(CallsNavKey.serializer(), original)
            val deserialized = json.decodeFromString(CallsNavKey.serializer(), serialized)
            assertEquals(original, deserialized)
        }
    }
}

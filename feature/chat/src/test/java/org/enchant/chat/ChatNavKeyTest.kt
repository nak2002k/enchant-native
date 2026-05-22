package org.enchant.chat

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("ChatNavKey serialization")
class ChatNavKeyTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Nested
    @DisplayName("Conversation")
    inner class Conversation {

        @Test
        fun `serialization round-trip with valid threadId`() {
            val original = ChatNavKey.Conversation(threadId = 42L)
            val serialized = json.encodeToString(ChatNavKey.serializer(), original)
            val deserialized = json.decodeFromString(ChatNavKey.serializer(), serialized)
            assertEquals(original, deserialized)
        }

        @Test
        fun `serialization round-trip with zero threadId`() {
            val original = ChatNavKey.Conversation(threadId = 0L)
            val serialized = json.encodeToString(ChatNavKey.serializer(), original)
            val deserialized = json.decodeFromString(ChatNavKey.serializer(), serialized)
            assertEquals(original, deserialized)
        }

        @Test
        fun `serialization round-trip with negative threadId`() {
            val original = ChatNavKey.Conversation(threadId = -1L)
            val serialized = json.encodeToString(ChatNavKey.serializer(), original)
            val deserialized = json.decodeFromString(ChatNavKey.serializer(), serialized)
            assertEquals(original, deserialized)
        }

        @Test
        fun `serialization round-trip with max Long threadId`() {
            val original = ChatNavKey.Conversation(threadId = Long.MAX_VALUE)
            val serialized = json.encodeToString(ChatNavKey.serializer(), original)
            val deserialized = json.decodeFromString(ChatNavKey.serializer(), serialized)
            assertEquals(original, deserialized)
        }
    }

    @Nested
    @DisplayName("MediaViewer")
    inner class MediaViewer {

        @Test
        fun `serialization round-trip with valid ids`() {
            val original = ChatNavKey.MediaViewer(messageId = 100L, attachmentId = 200L)
            val serialized = json.encodeToString(ChatNavKey.serializer(), original)
            val deserialized = json.decodeFromString(ChatNavKey.serializer(), serialized)
            assertEquals(original, deserialized)
        }

        @Test
        fun `serialization round-trip with zero ids`() {
            val original = ChatNavKey.MediaViewer(messageId = 0L, attachmentId = 0L)
            val serialized = json.encodeToString(ChatNavKey.serializer(), original)
            val deserialized = json.decodeFromString(ChatNavKey.serializer(), serialized)
            assertEquals(original, deserialized)
        }

        @Test
        fun `serialization round-trip with negative ids`() {
            val original = ChatNavKey.MediaViewer(messageId = -1L, attachmentId = -1L)
            val serialized = json.encodeToString(ChatNavKey.serializer(), original)
            val deserialized = json.decodeFromString(ChatNavKey.serializer(), serialized)
            assertEquals(original, deserialized)
        }
    }
}

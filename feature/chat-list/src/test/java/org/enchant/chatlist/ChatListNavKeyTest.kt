package org.enchant.chatlist

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("ChatListNavKey serialization")
class ChatListNavKeyTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Nested
    @DisplayName("ConversationList")
    inner class ConversationList {

        @Test
        fun `serialization round-trip`() {
            val original = ChatListNavKey.ConversationList
            val serialized = json.encodeToString(ChatListNavKey.serializer(), original)
            val deserialized = json.decodeFromString(ChatListNavKey.serializer(), serialized)
            assertEquals(original, deserialized)
        }
    }

    @Nested
    @DisplayName("ArchiveList")
    inner class ArchiveList {

        @Test
        fun `serialization round-trip with default includeArchived`() {
            val original = ChatListNavKey.ArchiveList()
            val serialized = json.encodeToString(ChatListNavKey.serializer(), original)
            val deserialized = json.decodeFromString(ChatListNavKey.serializer(), serialized)
            assertEquals(original, deserialized)
        }

        @Test
        fun `serialization round-trip with includeArchived true`() {
            val original = ChatListNavKey.ArchiveList(includeArchived = true)
            val serialized = json.encodeToString(ChatListNavKey.serializer(), original)
            val deserialized = json.decodeFromString(ChatListNavKey.serializer(), serialized)
            assertEquals(original, deserialized)
        }

        @Test
        fun `serialization round-trip with includeArchived false`() {
            val original = ChatListNavKey.ArchiveList(includeArchived = false)
            val serialized = json.encodeToString(ChatListNavKey.serializer(), original)
            val deserialized = json.decodeFromString(ChatListNavKey.serializer(), serialized)
            assertEquals(original, deserialized)
        }
    }
}

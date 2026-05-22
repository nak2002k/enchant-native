package org.enchant

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("MainNavigationDetailLocation")
class MainNavigationDetailLocationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Nested
    @DisplayName("serialization")
    inner class SerializationTests {

        @Test
        @DisplayName("Empty serializes and deserializes")
        fun `Empty serializes and deserializes`() {
            val encoded = json.encodeToString(MainNavigationDetailLocation.serializer(), MainNavigationDetailLocation.Empty)
            val decoded = json.decodeFromString(MainNavigationDetailLocation.serializer(), encoded)
            assertEquals(MainNavigationDetailLocation.Empty, decoded)
        }

        @Test
        @DisplayName("Conversation serializes with threadId")
        fun `Conversation serializes with threadId`() {
            val location = MainNavigationDetailLocation.Conversation(123L)
            val encoded = json.encodeToString(MainNavigationDetailLocation.serializer(), location)
            val decoded = json.decodeFromString(MainNavigationDetailLocation.serializer(), encoded)
            assertEquals(location, decoded)
        }

        @Test
        @DisplayName("Chats sub-location serializes correctly")
        fun `Chats sub-location serializes correctly`() {
            val location = MainNavigationDetailLocation.Chats.MessageDetails("recipient1", 456L)
            val encoded = json.encodeToString(MainNavigationDetailLocation.serializer(), location)
            val decoded = json.decodeFromString(MainNavigationDetailLocation.serializer(), encoded)
            assertEquals(location, decoded)
        }

        @Test
        @DisplayName("Calls sub-location serializes correctly")
        fun `Calls sub-location serializes correctly`() {
            val location = MainNavigationDetailLocation.Calls.CallLinks.EditCallLinkName("room123")
            val encoded = json.encodeToString(MainNavigationDetailLocation.serializer(), location)
            val decoded = json.decodeFromString(MainNavigationDetailLocation.serializer(), encoded)
            assertEquals(location, decoded)
        }

        @Test
        @DisplayName("Settings serializes and deserializes")
        fun `Settings serializes and deserializes`() {
            val location = MainNavigationDetailLocation.Settings
            val encoded = json.encodeToString(MainNavigationDetailLocation.serializer(), location)
            val decoded = json.decodeFromString(MainNavigationDetailLocation.serializer(), encoded)
            assertEquals(location, decoded)
        }
    }

    @Nested
    @DisplayName("isContentRoot")
    inner class IsContentRootTests {

        @Test
        @DisplayName("Empty is content root")
        fun `Empty is content root`() {
            assertTrue(MainNavigationDetailLocation.Empty.isContentRoot)
        }

        @Test
        @DisplayName("Settings is content root")
        fun `Settings is content root`() {
            assertTrue(MainNavigationDetailLocation.Settings.isContentRoot)
        }

        @Test
        @DisplayName("Conversation is not content root")
        fun `Conversation is not content root`() {
            assertFalse(MainNavigationDetailLocation.Conversation(1L).isContentRoot)
        }

        @Test
        @DisplayName("Chats sub-location is not content root")
        fun `Chats sub-location is not content root`() {
            assertFalse(MainNavigationDetailLocation.Chats.MessageDetails("r", 1L).isContentRoot)
        }

        @Test
        @DisplayName("Calls sub-location is not content root")
        fun `Calls sub-location is not content root`() {
            assertFalse(MainNavigationDetailLocation.Calls.CallLinks.EditCallLinkName("room").isContentRoot)
        }

        @Test
        @DisplayName("Archive is not content root")
        fun `Archive is not content root`() {
            assertFalse(MainNavigationDetailLocation.Archive.isContentRoot)
        }
    }
}
package org.enchant

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("MainNavigationRouter")
class MainNavigationRouterTest {

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
        @DisplayName("Archive is not content root")
        fun `Archive is not content root`() {
            assertFalse(MainNavigationDetailLocation.Archive.isContentRoot)
        }

        @Test
        @DisplayName("Groups is not content root")
        fun `Groups is not content root`() {
            assertFalse(MainNavigationDetailLocation.Groups.isContentRoot)
        }

        @Test
        @DisplayName("Status is not content root")
        fun `Status is not content root`() {
            assertFalse(MainNavigationDetailLocation.Status.isContentRoot)
        }

        @Test
        @DisplayName("ConversationList is not content root")
        fun `ConversationList is not content root`() {
            assertFalse(MainNavigationDetailLocation.ConversationList.isContentRoot)
        }
    }

    @Nested
    @DisplayName("MainNavigationDetailLocation.Chats")
    inner class ChatsSubLocationTests {

        @Test
        @DisplayName("MessageDetails serializes correctly")
        fun `MessageDetails serializes correctly`() {
            val location = MainNavigationDetailLocation.Chats.MessageDetails("recipient1", 123L)
            assertNotNull(location)
        }

        @Test
        @DisplayName("ConversationSettings serializes correctly")
        fun `ConversationSettings serializes correctly`() {
            val location = MainNavigationDetailLocation.Chats.ConversationSettings("recipient1")
            assertNotNull(location)
        }

        @Test
        @DisplayName("Chats sub-location is not content root")
        fun `Chats sub-location is not content root`() {
            assertFalse(MainNavigationDetailLocation.Chats.MessageDetails("r", 1L).isContentRoot)
        }
    }

    @Nested
    @DisplayName("MainNavigationDetailLocation.Calls")
    inner class CallsSubLocationTests {

        @Test
        @DisplayName("CallLinks.EditCallLinkName serializes correctly")
        fun `CallLinks serializes correctly`() {
            val location = MainNavigationDetailLocation.Calls.EditCallLinkName("room123")
            assertNotNull(location)
        }

        @Test
        @DisplayName("Calls sub-location is not content root")
        fun `Calls sub-location is not content root`() {
            assertFalse(MainNavigationDetailLocation.Calls.EditCallLinkName("room").isContentRoot)
        }
    }
}
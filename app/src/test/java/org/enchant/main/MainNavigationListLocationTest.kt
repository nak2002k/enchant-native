package org.enchant.main

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("MainNavigationListLocation")
class MainNavigationListLocationTest {

    @Nested
    @DisplayName("entries")
    inner class EntriesTests {

        @Test
        @DisplayName("has 4 tab entries")
        fun `has 4 tab entries`() {
            assertEquals(4, MainNavigationListLocation.entries.size)
        }

        @Test
        @DisplayName("contains CHATS")
        fun `contains CHATS`() {
            assertTrue(MainNavigationListLocation.entries.contains(MainNavigationListLocation.CHATS))
        }

        @Test
        @DisplayName("contains ARCHIVE")
        fun `contains ARCHIVE`() {
            assertTrue(MainNavigationListLocation.entries.contains(MainNavigationListLocation.ARCHIVE))
        }

        @Test
        @DisplayName("contains CALLS")
        fun `contains CALLS`() {
            assertTrue(MainNavigationListLocation.entries.contains(MainNavigationListLocation.CALLS))
        }

        @Test
        @DisplayName("contains STORIES")
        fun `contains STORIES`() {
            assertTrue(MainNavigationListLocation.entries.contains(MainNavigationListLocation.STORIES))
        }
    }
}

@DisplayName("MainFloatingActionButtons")
class MainFloatingActionButtonsTest {

    @Nested
    @DisplayName("Empty callback")
    inner class EmptyCallbackTests {

        @Test
        @DisplayName("onNewChatClick does not throw")
        fun `onNewChatClick does not throw`() {
            val callback = MainFloatingActionButtonsCallback.Empty
            callback.onNewChatClick()
        }

        @Test
        @DisplayName("onNewCallClick does not throw")
        fun `onNewCallClick does not throw`() {
            val callback = MainFloatingActionButtonsCallback.Empty
            callback.onNewCallClick()
        }

        @Test
        @DisplayName("onCameraClick does not throw")
        fun `onCameraClick does not throw`() {
            val callback = MainFloatingActionButtonsCallback.Empty
            callback.onCameraClick(MainNavigationListLocation.CHATS)
        }
    }
}
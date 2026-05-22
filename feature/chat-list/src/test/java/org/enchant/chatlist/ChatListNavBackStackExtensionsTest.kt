package org.enchant.chatlist

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("ChatListNavBackStackExtensions")
class ChatListNavBackStackExtensionsTest {

    @Nested
    @DisplayName("goToArchive")
    inner class GoToArchive {

        @Test
        fun `adds ArchiveList to empty stack`() {
            val stack = NavBackStack<NavKey>()
            stack.goToArchive()
            assertEquals(1, stack.size)
            assertTrue(stack.get(0) is ChatListNavKey.ArchiveList)
        }

        @Test
        fun `adds ArchiveList on top of existing stack`() {
            val stack = NavBackStack<NavKey>()
            stack.add(ChatListNavKey.ConversationList)
            stack.goToArchive()
            assertEquals(2, stack.size)
            assertTrue(stack.get(1) is ChatListNavKey.ArchiveList)
        }

        @Test
        fun `pops to ArchiveList when already in stack`() {
            val stack = NavBackStack<NavKey>()
            stack.add(ChatListNavKey.ConversationList)
            stack.add(ChatListNavKey.ArchiveList())
            stack.add(ChatListNavKey.ConversationList)
            stack.goToArchive()
            assertEquals(2, stack.size)
            assertTrue(stack.get(1) is ChatListNavKey.ArchiveList)
        }

        @Test
        fun `does not duplicate when ArchiveList is at top`() {
            val stack = NavBackStack<NavKey>()
            stack.add(ChatListNavKey.ConversationList)
            stack.add(ChatListNavKey.ArchiveList())
            stack.goToArchive()
            assertEquals(2, stack.size)
        }

        @Test
        fun `adds ArchiveList when not present and other keys exist`() {
            val stack = NavBackStack<NavKey>()
            stack.add(ChatListNavKey.ConversationList)
            stack.goToArchive()
            assertEquals(2, stack.size)
            assertTrue(stack.get(1) is ChatListNavKey.ArchiveList)
        }

        @Test
        fun `pops to ArchiveList with non-ArchiveList entries between`() {
            val stack = NavBackStack<NavKey>()
            stack.add(ChatListNavKey.ConversationList)
            stack.add(ChatListNavKey.ArchiveList())
            stack.add(object : NavKey {})
            stack.add(object : NavKey {})
            stack.goToArchive()
            assertEquals(2, stack.size)
            assertTrue(stack.get(1) is ChatListNavKey.ArchiveList)
        }
    }
}

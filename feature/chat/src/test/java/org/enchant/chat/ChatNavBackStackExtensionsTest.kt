package org.enchant.chat

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("ChatNavBackStackExtensions")
class ChatNavBackStackExtensionsTest {

    @Nested
    @DisplayName("goToConversation")
    inner class GoToConversation {

        @Test
        fun `adds Conversation to empty stack`() {
            val stack = NavBackStack<NavKey>()
            stack.goToConversation(42L)
            assertEquals(1, stack.size)
            val key = stack.get(0) as ChatNavKey.Conversation
            assertEquals(42L, key.threadId)
        }

        @Test
        fun `adds Conversation on top of existing stack`() {
            val stack = NavBackStack<NavKey>()
            stack.add(ChatNavKey.Conversation(1L))
            stack.goToConversation(2L)
            assertEquals(2, stack.size)
            val key = stack.get(1) as ChatNavKey.Conversation
            assertEquals(2L, key.threadId)
        }

        @Test
        fun `pops to existing Conversation when already in stack`() {
            val stack = NavBackStack<NavKey>()
            stack.add(ChatNavKey.Conversation(1L))
            stack.add(ChatNavKey.Conversation(2L))
            stack.add(ChatNavKey.Conversation(3L))
            stack.goToConversation(1L)
            assertEquals(1, stack.size)
            val key = stack.get(0) as ChatNavKey.Conversation
            assertEquals(1L, key.threadId)
        }

        @Test
        fun `does not duplicate when Conversation is at top`() {
            val stack = NavBackStack<NavKey>()
            stack.add(ChatNavKey.Conversation(1L))
            stack.goToConversation(1L)
            assertEquals(1, stack.size)
        }

        @Test
        fun `handles zero threadId`() {
            val stack = NavBackStack<NavKey>()
            stack.goToConversation(0L)
            assertEquals(1, stack.size)
            val key = stack.get(0) as ChatNavKey.Conversation
            assertEquals(0L, key.threadId)
        }

        @Test
        fun `handles negative threadId`() {
            val stack = NavBackStack<NavKey>()
            stack.goToConversation(-1L)
            assertEquals(1, stack.size)
            val key = stack.get(0) as ChatNavKey.Conversation
            assertEquals(-1L, key.threadId)
        }

        @Test
        fun `pops to conversation even when different conversations exist between`() {
            val stack = NavBackStack<NavKey>()
            stack.add(ChatNavKey.Conversation(1L))
            stack.add(object : NavKey {})
            stack.add(ChatNavKey.Conversation(3L))
            stack.goToConversation(1L)
            assertEquals(1, stack.size)
        }
    }
}

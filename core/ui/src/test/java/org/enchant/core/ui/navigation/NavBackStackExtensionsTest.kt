package org.enchant.core.ui.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("NavBackStackExtensions")
class NavBackStackExtensionsTest {

    private fun createStack(count: Int): NavBackStack<NavKey> {
        val stack = NavBackStack<NavKey>()
        repeat(count) { stack.add(object : NavKey {}) }
        return stack
    }

    @Nested
    @DisplayName("navigateOrPopTo")
    inner class NavigateOrPopToTests {

        @Test
        @DisplayName("adds key if not in stack")
        fun `adds key when not in stack`() {
            val stack = createStack(1)
            val initialSize = stack.size
            stack.navigateOrPopTo(object : NavKey {})
            assertEquals(initialSize + 1, stack.size)
        }

        @Test
        @DisplayName("pops to key if already in stack")
        fun `pops to key when already in stack`() {
            val stack = createStack(3)
            val target = stack.get(1)
            val sizeBefore = stack.size
            stack.navigateOrPopTo(target)
            assertTrue(stack.size < sizeBefore)
        }

        @Test
        @DisplayName("does not duplicate if key is at top of stack")
        fun `no duplicate when key is at top`() {
            val stack = createStack(2)
            val topKey = stack.get(1)
            val sizeBefore = stack.size
            stack.navigateOrPopTo(topKey)
            assertEquals(sizeBefore, stack.size)
        }

        @Test
        @DisplayName("adds key when stack is empty")
        fun `adds key when stack is empty`() {
            val stack = NavBackStack<NavKey>()
            stack.navigateOrPopTo(object : NavKey {})
            assertEquals(1, stack.size)
        }
    }

    @Nested
    @DisplayName("safePop")
    inner class SafePopTests {

        @Test
        @DisplayName("returns and removes the last key")
        fun `returns and removes last key`() {
            val stack = createStack(2)
            val popped = stack.safePop()
            assertNotNull(popped)
            assertEquals(1, stack.size)
        }

        @Test
        @DisplayName("returns null when stack is empty")
        fun `returns null when empty`() {
            val stack = NavBackStack<NavKey>()
            val popped = stack.safePop()
            assertNull(popped)
        }

        @Test
        @DisplayName("returns last when stack has one element")
        fun `returns last when one element`() {
            val stack = createStack(1)
            val popped = stack.safePop()
            assertNotNull(popped)
            assertEquals(0, stack.size)
        }
    }

    @Nested
    @DisplayName("NavBackStack creation")
    inner class CreationTests {

        @Test
        @DisplayName("can be instantiated")
        fun `can be instantiated`() {
            val stack = NavBackStack<NavKey>()
            assertNotNull(stack)
            assertTrue(stack.isEmpty())
        }

        @Test
        @DisplayName("size is 0 when empty")
        fun `size is 0 when empty`() {
            val stack = NavBackStack<NavKey>()
            assertEquals(0, stack.size)
        }

        @Test
        @DisplayName("add puts element on top")
        fun `add puts element on top`() {
            val stack = NavBackStack<NavKey>()
            stack.add(object : NavKey {})
            assertEquals(1, stack.size)
        }

        @Test
        @DisplayName("removeAt removes element at index")
        fun `removeAt removes at index`() {
            val stack = createStack(3)
            stack.removeAt(1)
            assertEquals(2, stack.size)
        }

        @Test
        @DisplayName("isNotEmpty returns true when elements exist")
        fun `isNotEmpty returns true when not empty`() {
            val stack = createStack(1)
            assertTrue(stack.isNotEmpty())
        }

        @Test
        @DisplayName("contains returns true for existing element")
        fun `contains returns true for existing element`() {
            val stack = createStack(2)
            val existing = stack.get(0)
            assertTrue(stack.contains(existing))
        }
    }
}
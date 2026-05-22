package org.enchant.window

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("AppScaffoldNavigator")
class AppScaffoldNavigatorTest {

    @Nested
    @DisplayName("AppScaffoldNavigatorImpl")
    inner class AppScaffoldNavigatorImplTests {

        @Test
        @DisplayName("initial state has no detail location")
        fun `initial state has no detail location`() {
            val navigator = AppScaffoldNavigatorImpl<String>()
            assertEquals(null, navigator.currentDetail.value)
        }

        @Test
        @DisplayName("initial state cannot navigate back")
        fun `initial state cannot navigate back`() {
            val navigator = AppScaffoldNavigatorImpl<String>()
            assertFalse(navigator.canNavigateBack.value)
        }

        @Test
        @DisplayName("navigateTo sets detail location")
        fun `navigateTo sets detail location`() {
            val navigator = AppScaffoldNavigatorImpl<String>()
            navigator.navigateTo("detail")
            assertEquals("detail", navigator.currentDetail.value)
        }

        @Test
        @DisplayName("navigateTo enables navigate back")
        fun `navigateTo enables navigate back`() {
            val navigator = AppScaffoldNavigatorImpl<String>()
            navigator.navigateTo("detail")
            assertTrue(navigator.canNavigateBack.value)
        }

        @Test
        @DisplayName("navigateBack clears detail and disables back")
        fun `navigateBack clears detail and disables back`() = runBlocking {
            val navigator = AppScaffoldNavigatorImpl<String>()
            navigator.navigateTo("detail")
            val result = navigator.navigateBack()
            assertTrue(result)
            assertEquals(null, navigator.currentDetail.value)
            assertFalse(navigator.canNavigateBack.value)
        }

        @Test
        @DisplayName("showList clears detail")
        fun `showList clears detail`() {
            val navigator = AppScaffoldNavigatorImpl<String>()
            navigator.navigateTo("detail")
            navigator.showList()
            assertEquals(null, navigator.currentDetail.value)
        }

        @Test
        @DisplayName("scaffoldDirective has default values")
        fun `scaffoldDirective has default values`() {
            val navigator = AppScaffoldNavigatorImpl<String>()
            assertNotNull(navigator.scaffoldDirective)
            assertEquals(1, navigator.scaffoldDirective.maxHorizontalPartitions)
        }
    }
}

@DisplayName("ScaffoldDirective")
class ScaffoldDirectiveTest {

    @Test
    @DisplayName("default values are correct")
    fun `default values are correct`() {
        val directive = ScaffoldDirective()
        assertEquals(1, directive.maxHorizontalPartitions)
        assertEquals(false, directive.hasFoldable)
        assertEquals(400, directive.defaultPanePreferredWidth)
    }

    @Test
    @DisplayName("custom values are preserved")
    fun `custom values are preserved`() {
        val directive = ScaffoldDirective(maxHorizontalPartitions = 2, hasFoldable = true, defaultPanePreferredWidth = 600)
        assertEquals(2, directive.maxHorizontalPartitions)
        assertEquals(true, directive.hasFoldable)
        assertEquals(600, directive.defaultPanePreferredWidth)
    }
}
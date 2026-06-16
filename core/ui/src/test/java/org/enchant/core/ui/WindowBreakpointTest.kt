package org.enchant.core.ui

import android.content.res.Resources
import android.util.DisplayMetrics
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("WindowBreakpoint")
class WindowBreakpointTest {

    @Nested
    @DisplayName("getWindowBreakpoint")
    inner class GetWindowBreakpointTests {

        @Test
        @DisplayName("returns SMALL for width < 600dp")
        fun `returns SMALL for narrow width`() {
            val resources = mockResources(400, 800)
            assertEquals(WindowBreakpoint.SMALL, resources.getWindowBreakpoint())
        }

        @Test
        @DisplayName("returns MEDIUM for width >= 600dp and < 840dp")
        fun `returns MEDIUM for medium width`() {
            val resources = mockResources(700, 1200)
            assertEquals(WindowBreakpoint.MEDIUM, resources.getWindowBreakpoint())
        }

        @Test
        @DisplayName("returns LARGE for width >= 840dp")
        fun `returns LARGE for wide width`() {
            val resources = mockResources(900, 1600)
            assertEquals(WindowBreakpoint.LARGE, resources.getWindowBreakpoint())
        }
    }

    @Nested
    @DisplayName("isSplitPane")
    inner class IsSplitPaneTests {

        @Test
        @DisplayName("returns false for SMALL breakpoint")
        fun `returns false for small breakpoint`() {
            val resources = mockResources(400, 800)
            assertEquals(false, resources.isSplitPane())
        }

        @Test
        @DisplayName("returns true for MEDIUM breakpoint")
        fun `returns true for medium breakpoint`() {
            val resources = mockResources(700, 1200)
            assertEquals(true, resources.isSplitPane())
        }

        @Test
        @DisplayName("returns true for LARGE breakpoint in landscape")
        fun `returns true for large landscape`() {
            val resources = mockResources(1200, 800)
            assertEquals(true, resources.isSplitPane())
        }

        @Test
        @DisplayName("returns false for LARGE breakpoint in portrait")
        fun `returns false for large portrait`() {
            val resources = mockResources(900, 1200)
            assertEquals(false, resources.isSplitPane())
        }

        @Test
        @DisplayName("returns true when forceSplitPane is true")
        fun `returns true when forced`() {
            val resources = mockResources(400, 800)
            assertEquals(true, resources.isSplitPane(forceSplitPane = true))
        }
    }
}

private fun mockResources(widthPx: Int, heightPx: Int): Resources {
    val displayMetrics = DisplayMetrics().apply {
        this.widthPixels = widthPx
        this.heightPixels = heightPx
        this.density = 1f
    }
    return mockk<Resources>(relaxed = true) {
        every { this@mockk.displayMetrics } returns displayMetrics
    }
}
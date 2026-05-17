package org.enchant.core.accessibility

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("RtlSupport")
class RtlSupportTest {

    @Nested @DisplayName("getTextAlignment")
    inner class GetTextAlignmentTest {
        @Test @DisplayName("returns END for RTL")
        fun `rtl returns end`() {
            val alignment = getTextAlignment(true)
            assertEquals(android.view.View.TEXT_ALIGNMENT_VIEW_END, alignment)
        }

        @Test @DisplayName("returns START for LTR")
        fun `ltr returns start`() {
            val alignment = getTextAlignment(false)
            assertEquals(android.view.View.TEXT_ALIGNMENT_VIEW_START, alignment)
        }

        @Test @DisplayName("RTL and LTR return different values")
        fun `rtl differs from ltr`() {
            assertFalse(getTextAlignment(true) == getTextAlignment(false))
        }
    }
}
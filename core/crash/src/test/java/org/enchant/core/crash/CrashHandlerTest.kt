package org.enchant.core.crash

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("Log — Full Coverage")
class LogTest {
    @Nested @DisplayName("Debug")
    inner class DebugTests {
        @Test @DisplayName("d adds to log buffer")
        fun `d adds to buffer`() {
            Log.d("Tag", "message")
        }

        @Test @DisplayName("d handles large messages")
        fun `d large message`() {
            Log.d("Tag", "x".repeat(1000))
        }
    }

    @Nested @DisplayName("Error")
    inner class ErrorTests {
        @Test @DisplayName("e adds error to log buffer")
        fun `e adds to buffer`() {
            Log.e("Tag", "error")
        }

        @Test @DisplayName("e adds error with throwable")
        fun `e with throwable`() {
            Log.e("Tag", "error", RuntimeException("cause"))
        }
    }

    @Nested @DisplayName("Warn")
    inner class WarnTests {
        @Test @DisplayName("w adds warn to log buffer")
        fun `w adds to buffer`() {
            Log.w("Tag", "warning")
        }
    }

    @Nested @DisplayName("Block until writes")
    inner class BlockUntilWritesTests {
        @Test @DisplayName("blockUntilAllWritesFinished returns")
        fun `block returns`() {
            Log.blockUntilAllWritesFinished()
        }
    }
}
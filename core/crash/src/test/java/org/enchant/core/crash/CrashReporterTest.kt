package org.enchant.core.crash

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("CrashReporter")
class CrashReporterTest {

    @Nested @DisplayName("PII scrubbing")
    inner class ScrubTest {
        @Test @DisplayName("scrub UUID")
        fun `scrub uuid`() {
            val input = "user 550e8400-e29b-41d4-a716-446655440000 logged in"
            val result = CrashReporter.scrub(input)
            assertFalse(result.contains("550e8400")) { "UUID was not scrubbed: $result" }
        }

        @Test @DisplayName("scrub phone")
        fun `scrub phone`() {
            val input = "phone: +15551234567"
            val result = CrashReporter.scrub(input)
            assertFalse(result.contains("+15551234567")) { "Phone was not scrubbed: $result" }
        }

        @Test @DisplayName("scrub email")
        fun `scrub email`() {
            val input = "email test@example.com"
            val result = CrashReporter.scrub(input)
            assertFalse(result.contains("test@example.com")) { "Email was not scrubbed: $result" }
        }

        @Test @DisplayName("safe text unchanged")
        fun `safe text`() {
            assertEquals("Hello world", CrashReporter.scrub("Hello world"))
        }

        @Test @DisplayName("empty string")
        fun `empty`() {
            assertEquals("", CrashReporter.scrub(""))
        }
    }
}
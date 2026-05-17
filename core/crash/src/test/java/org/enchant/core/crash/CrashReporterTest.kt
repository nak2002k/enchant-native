package org.enchant.core.crash

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("CrashReporter — Full Coverage")
class CrashReporterTest {

    @Nested @DisplayName("Scrub")
    inner class ScrubTest {
        @Test @DisplayName("scrub redacts base64 keys (40+ chars)")
        fun `scrub base64`() {
            val input = "Key: " + "A".repeat(44) + "="
            val result = CrashReporter.scrub(input)
            assertTrue(result.contains("[REDACTED_KEY]"))
        }

        @Test @DisplayName("scrub redacts UUIDs")
        fun `scrub uuid`() {
            val input = "ID: 550e8400-e29b-41d4-a716-446655440000"
            val result = CrashReporter.scrub(input)
            assertTrue(result.contains("[REDACTED_UUID]"))
            assertFalse(result.contains("550e8400"))
        }

        @Test @DisplayName("scrub redacts phone numbers")
        fun `scrub phone`() {
            val input = "Phone: +14155552671"
            val result = CrashReporter.scrub(input)
            assertTrue(result.contains("[REDACTED_PHONE]"))
            assertFalse(result.contains("+14155552671"))
        }

        @Test @DisplayName("scrub redacts emails")
        fun `scrub email`() {
            val input = "Email: user@example.com"
            val result = CrashReporter.scrub(input)
            assertTrue(result.contains("[REDACTED_EMAIL]"))
            assertFalse(result.contains("user@example.com"))
        }

        @Test @DisplayName("scrub leaves normal text unchanged")
        fun `scrub normal`() {
            val input = "Hello World"
            val result = CrashReporter.scrub(input)
            assertEquals("Hello World", result)
        }

        @Test @DisplayName("scrub handles empty string")
        fun `scrub empty`() {
            val result = CrashReporter.scrub("")
            assertEquals("", result)
        }

        @Test @DisplayName("scrub redacts multiple patterns")
        fun `scrub multiple`() {
            val input = "User 550e8400-e29b-41d4-a716-446655440000 with email test@test.com called +14155552671"
            val result = CrashReporter.scrub(input)
            assertTrue(result.contains("[REDACTED_UUID]"))
            assertTrue(result.contains("[REDACTED_EMAIL]"))
            assertTrue(result.contains("[REDACTED_PHONE]"))
        }

        @Test @DisplayName("scrub does not redact short strings that look like base64")
        fun `scrub short base64`() {
            val input = "SGVsbG8="
            val result = CrashReporter.scrub(input)
            assertEquals("SGVsbG8=", result)
        }

        @Test @DisplayName("scrub redacts international phone numbers")
        fun `scrub international phone`() {
            val input = "+441234567890"
            val result = CrashReporter.scrub(input)
            assertTrue(result.contains("[REDACTED_PHONE]"))
        }

        @Test @DisplayName("scrub redacts complex emails")
        fun `scrub complex email`() {
            val input = "user.name+tag@sub.domain.co.uk"
            val result = CrashReporter.scrub(input)
            assertTrue(result.contains("[REDACTED_EMAIL]"))
        }

        @Test @DisplayName("scrub handles special chars")
        fun `scrub special chars`() {
            val input = "Special: !@#$%^&*()"
            val result = CrashReporter.scrub(input)
            assertEquals("Special: !@#$%^&*()", result)
        }
    }

    @Nested @DisplayName("Initialization")
    inner class InitTest {
        @Test @DisplayName("init can be called multiple times without error")
        fun `init idempotent`() {
            CrashReporter.init()
            CrashReporter.init()
        }
    }
}

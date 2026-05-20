package org.enchant.core.base.logging

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.regex.Pattern

class ScrubberTest {

    @Test
    fun `scrub returns null for null input`() {
        assertNull(Scrubber.scrub(null))
    }

    @Test
    fun `scrub leaves clean message unchanged`() {
        assertEquals("Hello world", Scrubber.scrub("Hello world"))
    }

    @Test
    fun `scrub replaces phone numbers`() {
        val result = Scrubber.scrub("User +15551234567 logged in")!!
        assertTrue(result.contains("[PHONE:"))
        assertTrue(result.contains("] logged in"))
    }

    @Test
    fun `scrub replaces emails`() {
        val result = Scrubber.scrub("Contact user@example.com for help")!!
        assertTrue(result.contains("[EMAIL:"))
        assertTrue(result.contains("] for help"))
    }

    @Test
    fun `scrub replaces UUIDs`() {
        val result = Scrubber.scrub("Session abc12345-def0-1234-5678-abcdef123456 expired")!!
        assertTrue(result.contains("[UUID:"))
        assertTrue(result.contains("] expired"))
    }

    @Test
    fun `scrub replaces IPv4 addresses`() {
        val result = Scrubber.scrub("Connected from 192.168.1.100")!!
        assertTrue(result.contains("[IP:"))
        assertTrue(result.contains("]"))
    }

    @Test
    fun `scrub replaces URLs`() {
        val result = Scrubber.scrub("Visit https://example.com/path for info")!!
        assertTrue(result.contains("[URL:"))
        assertTrue(result.contains("] for info"))
    }

    @Test
    fun `scrub replaces multiple PII types`() {
        val result = Scrubber.scrub("User +15551234567 (user@test.com) from 10.0.0.1")!!
        assertTrue(result.contains("[PHONE:"))
        assertTrue(result.contains("[EMAIL:"))
        assertTrue(result.contains("[IP:"))
    }

    @Test
    fun `scrub produces consistent tokens for same value`() {
        val input = "Contact +15551234567 and +15551234567 again"
        val result = Scrubber.scrub(input)!!
        val tokens = Regex("\\[PHONE:([^]]+)\\]").findAll(result).map { it.groupValues[1] }.toList()
        assertEquals(2, tokens.size)
        assertEquals(tokens[0], tokens[1])
    }

    @Test
    fun `scrub produces different tokens for different values`() {
        val result = Scrubber.scrub("Call +15551111111 or +15552222222")!!
        val tokens = Regex("\\[PHONE:([^]]+)\\]").findAll(result).map { it.groupValues[1] }.toList()
        assertEquals(2, tokens.size)
        assertTrue(tokens[0] != tokens[1])
    }

    @Test
    fun `scrub with allowedPatterns preserves matching values`() {
        val allowedUrl = Pattern.compile("https://internal\\.enchant\\..*")
        val result = Scrubber.scrub(
            "Visit https://internal.enchant.chat/api and https://evil.com/steal",
            allowedUrl
        )!!
        assertTrue(result.contains("https://internal.enchant.chat/api"))
        assertTrue(result.contains("[URL:"))
    }

    @Test
    fun `scrub handles empty string`() {
        assertEquals("", Scrubber.scrub(""))
    }

    @Test
    fun `scrub does not modify message without PII`() {
        val message = "The quick brown fox jumps over the lazy dog"
        assertEquals(message, Scrubber.scrub(message))
    }
}

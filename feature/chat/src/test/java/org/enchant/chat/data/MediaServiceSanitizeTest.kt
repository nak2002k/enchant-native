package org.enchant.chat.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("MediaService.sanitizeMediaId")
class MediaServiceSanitizeTest {

    @Test
    @DisplayName("valid UUID passes through unchanged")
    fun `valid uuid unchanged`() {
        val id = "18383f05-8116-4a6f-8a8a-ed6866186202"
        assertEquals(id, MediaService.sanitizeMediaId(id))
    }

    @Test
    @DisplayName("path traversal with dot-dot is neutralized")
    fun `dot dot traversal neutralized`() {
        assertEquals("unknown", MediaService.sanitizeMediaId("../../../etc/passwd"))
    }

    @Test
    @DisplayName("path traversal with forward slash is neutralized")
    fun `forward slash neutralized`() {
        assertEquals("unknown", MediaService.sanitizeMediaId("../../secret/file"))
    }

    @Test
    @DisplayName("backslash path is neutralized")
    fun `backslash neutralized`() {
        assertEquals("unknown", MediaService.sanitizeMediaId("..\\..\\secret"))
    }

    @Test
    @DisplayName("absolute path is neutralized")
    fun `absolute path neutralized`() {
        assertEquals("unknown", MediaService.sanitizeMediaId("/etc/passwd"))
    }

    @Test
    @DisplayName("embedded traversal in otherwise valid id is neutralized")
    fun `embedded traversal neutralized`() {
        assertEquals("unknown", MediaService.sanitizeMediaId("a..b/c"))
    }

    @Test
    @DisplayName("null byte is neutralized")
    fun `null byte neutralized`() {
        assertEquals("unknown", MediaService.sanitizeMediaId("uuid\u0000/evil"))
    }

    @Test
    @DisplayName("empty mediaId falls back to unknown")
    fun `empty falls back`() {
        assertEquals("unknown", MediaService.sanitizeMediaId(""))
        assertEquals("unknown", MediaService.sanitizeMediaId("   "))
    }

    @Test
    @DisplayName("spaces and punctuation are stripped, never form a path")
    fun `punctuation stripped`() {
        val result = MediaService.sanitizeMediaId("my id!@#%^&*()=+[],.;:")
        assertFalse(result.contains(' '))
        assertFalse(result.contains('/'))
        assertFalse(result.contains('.'))
    }

    @Test
    @DisplayName("never returns an empty string")
    fun `never empty`() {
        assertEquals("unknown", MediaService.sanitizeMediaId("///"))
    }
}

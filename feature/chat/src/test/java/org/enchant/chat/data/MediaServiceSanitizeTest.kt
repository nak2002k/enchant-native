package org.enchant.chat.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
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

@DisplayName("MediaService.computeInSampleSize")
class MediaServiceSampleSizeTest {

    @Test
    @DisplayName("small image uses sample size 1")
    fun `small image sample 1`() {
        assertEquals(1, MediaService.computeInSampleSize(800, 600, 1024))
    }

    @Test
    @DisplayName("decompression bomb is downsampled")
    fun `bomb downsampled`() {
        val sample = MediaService.computeInSampleSize(100_000, 100_000, 2048)
        assertTrue(sample > 1)
        // Power-of-two sampling bounds decoded dims to <= 2x target.
        assertTrue(100_000 / sample <= 2 * 2048)
    }

    @Test
    @DisplayName("wide panoramic image is downsampled on width")
    fun `panorama downsampled`() {
        val sample = MediaService.computeInSampleSize(50_000, 500, 2048)
        assertTrue(50_000 / sample <= 2 * 2048)
    }

    @Test
    @DisplayName("portrait image is downsampled on height")
    fun `portrait downsampled`() {
        val sample = MediaService.computeInSampleSize(500, 50_000, 2048)
        assertTrue(50_000 / sample <= 2 * 2048)
    }

    @Test
    @DisplayName("invalid dimensions return sample size 1")
    fun `invalid dims sample 1`() {
        assertEquals(1, MediaService.computeInSampleSize(0, 0, 1024))
        assertEquals(1, MediaService.computeInSampleSize(-5, 10, 1024))
        assertEquals(1, MediaService.computeInSampleSize(100, 100, 0))
    }

    @Test
    @DisplayName("exactly target dimension keeps sample size 1")
    fun `at target keeps sample 1`() {
        assertEquals(1, MediaService.computeInSampleSize(2048, 2048, 2048))
    }

    @Test
    @DisplayName("slightly over target stays within 2x bound")
    fun `slightly over target stays bounded`() {
        val sample = MediaService.computeInSampleSize(3000, 3000, 2048)
        assertTrue(3000 / sample <= 2 * 2048)
    }
}

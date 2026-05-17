package org.enchant.status.screens

import org.enchant.status.StatusFeedEntry
import org.enchant.status.StatusViewer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StatusFeedScreenTest {

    @Test
    fun `StatusFeedEntry data class holds values`() {
        val viewer = StatusViewer(userId = "v1", username = "bob", viewedAt = "2025-01-01T01:00:00Z")
        val entry = StatusFeedEntry(
            statusId = "s1",
            userId = "u1",
            username = "alice",
            type = "text",
            text = "Hello",
            mediaId = null,
            backgroundColor = "#FF0000",
            createdAt = "2025-01-01T00:00:00Z",
            viewedBy = listOf(viewer),
            isViewed = false
        )
        assertEquals("s1", entry.statusId)
        assertEquals("u1", entry.userId)
        assertEquals("alice", entry.username)
        assertEquals("text", entry.type)
        assertEquals("Hello", entry.text)
        assertEquals(null, entry.mediaId)
        assertEquals("#FF0000", entry.backgroundColor)
        assertEquals("2025-01-01T00:00:00Z", entry.createdAt)
        assertEquals(1, entry.viewedBy.size)
        assertEquals(false, entry.isViewed)
    }

    @Test
    fun `StatusFeedEntry default values`() {
        val entry = StatusFeedEntry()
        assertEquals("", entry.statusId)
        assertEquals("", entry.userId)
        assertEquals("", entry.username)
        assertEquals("text", entry.type)
        assertEquals(null, entry.text)
        assertEquals(null, entry.mediaId)
        assertEquals(null, entry.backgroundColor)
        assertEquals("", entry.createdAt)
        assertTrue(entry.viewedBy.isEmpty())
        assertEquals(false, entry.isViewed)
    }
}

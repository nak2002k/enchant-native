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
            isViewed = false,
            isMine = true
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
        assertTrue(entry.isMine)
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
        assertEquals(false, entry.isMine)
    }

    @Test
    fun `feed grouping by userId`() {
        val entries = listOf(
            StatusFeedEntry(statusId = "s1", userId = "u1", username = "alice"),
            StatusFeedEntry(statusId = "s2", userId = "u1", username = "alice"),
            StatusFeedEntry(statusId = "s3", userId = "u2", username = "bob")
        )
        val grouped = entries.groupBy { it.userId }
        assertEquals(2, grouped.size)
        assertEquals(2, grouped["u1"]?.size)
        assertEquals(1, grouped["u2"]?.size)
    }

    @Test
    fun `StatusPrivacy Selected holds userIds`() {
        val selected = org.enchant.status.StatusPrivacy.Selected(userIds = listOf("u1", "u2"))
        assertEquals(2, selected.userIds.size)
        assertEquals("u1", selected.userIds[0])
    }
}

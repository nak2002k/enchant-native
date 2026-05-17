package org.enchant.status

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StatusViewModelTest {

    @Test
    fun `initial state has defaults`() {
        val state = StatusUiState()
        assertTrue(state.feed.isEmpty())
        assertEquals(null, state.myStatus)
        assertTrue(state.viewers.isEmpty())
        assertEquals(false, state.isLoading)
        assertEquals(null, state.error)
        assertEquals(null, state.successMessage)
    }

    @Test
    fun `StatusPrivacy enum values exist`() {
        assertNotNull(StatusPrivacy.AllContacts)
        assertNotNull(StatusPrivacy.Selected)
        assertNotNull(StatusPrivacy.CloseFriends)
    }

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
        assertEquals("bob", entry.viewedBy[0].username)
        assertEquals(false, entry.isViewed)
    }

    @Test
    fun `StatusUiState default values`() {
        val state = StatusUiState()
        assertTrue(state.feed.isEmpty())
        assertEquals(null, state.myStatus)
        assertTrue(state.viewers.isEmpty())
        assertEquals(false, state.isLoading)
        assertEquals(null, state.error)
        assertEquals(null, state.successMessage)
    }
}

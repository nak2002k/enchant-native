package org.enchant.channels.screens

import org.enchant.channels.ChannelPost
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("ChannelFeedScreen")
class ChannelFeedScreenTest {

    @Nested
    @DisplayName("ChannelPost data class")
    inner class ChannelPostDataClass {
        @Test
        fun `holds all values when constructed`() {
            val post = ChannelPost(
                postId = "p1",
                channelId = "c1",
                authorId = "author1",
                content = "Feed post content",
                mediaIds = listOf("media1"),
                isPinned = false,
                createdAt = "2025-06-01T12:00:00Z"
            )
            assertEquals("p1", post.postId)
            assertEquals("c1", post.channelId)
            assertEquals("author1", post.authorId)
            assertEquals("Feed post content", post.content)
            assertEquals(listOf("media1"), post.mediaIds)
            assertFalse(post.isPinned)
            assertEquals("2025-06-01T12:00:00Z", post.createdAt)
        }

        @Test
        fun `default values are empty`() {
            val post = ChannelPost()
            assertEquals("", post.postId)
            assertEquals("", post.channelId)
            assertEquals("", post.authorId)
            assertEquals("", post.content)
            assertTrue(post.mediaIds.isEmpty())
            assertFalse(post.isPinned)
            assertEquals("", post.createdAt)
        }
    }
}

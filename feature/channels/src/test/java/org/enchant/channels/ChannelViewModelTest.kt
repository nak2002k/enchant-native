package org.enchant.channels

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.enchant.core.network.ApiClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("ChannelViewModel")
class ChannelViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val mockApiClient: ApiClient = mockk()
    private lateinit var viewModel: ChannelViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = ChannelViewModel(mockApiClient)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Nested
    @DisplayName("initial state")
    inner class InitialState {
        @Test
        fun `default ChannelUiState has empty feed`() {
            assertTrue(viewModel.uiState.value.feed.isEmpty())
        }

        @Test
        fun `default ChannelUiState has no pinned post`() {
            assertNull(viewModel.uiState.value.pinnedPost)
        }

        @Test
        fun `default ChannelUiState has empty channels`() {
            assertTrue(viewModel.uiState.value.channels.isEmpty())
        }

        @Test
        fun `default ChannelUiState has empty myChannels`() {
            assertTrue(viewModel.uiState.value.myChannels.isEmpty())
        }

        @Test
        fun `default ChannelUiState has empty discoverResults`() {
            assertTrue(viewModel.uiState.value.discoverResults.isEmpty())
        }

        @Test
        fun `default ChannelUiState has empty searchResults`() {
            assertTrue(viewModel.uiState.value.searchResults.isEmpty())
        }

        @Test
        fun `default ChannelUiState has empty search query`() {
            assertEquals("", viewModel.uiState.value.searchQuery)
        }

        @Test
        fun `default ChannelUiState is not loading`() {
            assertFalse(viewModel.uiState.value.isLoading)
        }

        @Test
        fun `default ChannelUiState is not loading more`() {
            assertFalse(viewModel.uiState.value.isLoadingMore)
        }

        @Test
        fun `default ChannelUiState has no error`() {
            assertNull(viewModel.uiState.value.error)
        }

        @Test
        fun `default ChannelUiState has no success message`() {
            assertNull(viewModel.uiState.value.successMessage)
        }

        @Test
        fun `default ChannelUiState has null cursor`() {
            assertNull(viewModel.uiState.value.cursor)
        }
    }

    @Nested
    @DisplayName("ChannelPost data class")
    inner class ChannelPostDataClass {
        @Test
        fun `holds all values when constructed`() {
            val post = ChannelPost(
                postId = "p1",
                channelId = "c1",
                authorId = "author1",
                content = "Hello world",
                mediaIds = listOf("m1", "m2"),
                isPinned = true,
                createdAt = "2025-01-01T00:00:00Z"
            )
            assertEquals("p1", post.postId)
            assertEquals("c1", post.channelId)
            assertEquals("author1", post.authorId)
            assertEquals("Hello world", post.content)
            assertEquals(listOf("m1", "m2"), post.mediaIds)
            assertTrue(post.isPinned)
            assertEquals("2025-01-01T00:00:00Z", post.createdAt)
        }

        @Test
        fun `uses defaults for empty construction`() {
            val post = ChannelPost()
            assertEquals("", post.postId)
            assertEquals("", post.channelId)
            assertEquals("", post.authorId)
            assertEquals("", post.content)
            assertTrue(post.mediaIds.isEmpty())
            assertFalse(post.isPinned)
            assertEquals("", post.createdAt)
        }

        @Test
        fun `copy produces independent instance`() {
            val post = ChannelPost(postId = "p1", content = "original")
            val copied = post.copy(content = "modified")
            assertEquals("original", post.content)
            assertEquals("modified", copied.content)
        }
    }

    @Nested
    @DisplayName("Channel data class")
    inner class ChannelDataClass {
        @Test
        fun `holds all values when constructed`() {
            val channel = Channel(
                channelId = "c1",
                name = "Test Channel",
                description = "A test channel",
                avatarMediaId = "avatar1",
                subscriberCount = 42,
                isSubscribed = true
            )
            assertEquals("c1", channel.channelId)
            assertEquals("Test Channel", channel.name)
            assertEquals("A test channel", channel.description)
            assertEquals("avatar1", channel.avatarMediaId)
            assertEquals(42, channel.subscriberCount)
            assertTrue(channel.isSubscribed)
        }

        @Test
        fun `uses defaults for empty construction`() {
            val channel = Channel()
            assertEquals("", channel.channelId)
            assertEquals("", channel.name)
            assertNull(channel.description)
            assertNull(channel.avatarMediaId)
            assertEquals(0, channel.subscriberCount)
            assertFalse(channel.isSubscribed)
        }
    }

    @Nested
    @DisplayName("loadFeed")
    inner class LoadFeed {
        @Test
        fun `loads feed successfully`() = runTest {
            coEvery { mockApiClient.get("/v1/channels/c1/feed", any()) } returns Result.success(
                buildJsonObject {
                    putJsonArray("posts") {
                        add(buildJsonObject {
                            put("post_id", "p1")
                            put("channel_id", "c1")
                            put("author_id", "author1")
                            put("content", "Hello")
                            put("is_pinned", "false")
                            put("created_at", "2025-01-01T00:00:00Z")
                        })
                        add(buildJsonObject {
                            put("post_id", "p2")
                            put("channel_id", "c1")
                            put("author_id", "author2")
                            put("content", "Pinned post")
                            put("is_pinned", "true")
                            put("created_at", "2025-01-02T00:00:00Z")
                        })
                    }
                    put("cursor", "cursor_abc")
                }
            )

            viewModel.loadFeed("c1")
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(1, viewModel.uiState.value.feed.size)
            assertEquals("p1", viewModel.uiState.value.feed[0].postId)
            assertEquals("p2", viewModel.uiState.value.pinnedPost?.postId)
            assertEquals("cursor_abc", viewModel.uiState.value.cursor)
            assertFalse(viewModel.uiState.value.isLoading)
        }

        @Test
        fun `handles network error`() = runTest {
            coEvery { mockApiClient.get("/v1/channels/c1/feed", any()) } returns Result.failure(
                Exception("Network error")
            )

            viewModel.loadFeed("c1")
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals("Network error", viewModel.uiState.value.error)
            assertFalse(viewModel.uiState.value.isLoading)
        }

        @Test
        fun `handles exception`() = runTest {
            coEvery { mockApiClient.get("/v1/channels/c1/feed", any()) } throws RuntimeException("Crash")

            viewModel.loadFeed("c1")
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals("Crash", viewModel.uiState.value.error)
            assertFalse(viewModel.uiState.value.isLoading)
        }
    }

    @Nested
    @DisplayName("loadMore")
    inner class LoadMore {
        @Test
        fun `loads more posts when cursor exists`() = runTest {
            coEvery { mockApiClient.get("/v1/channels/c1/feed", any()) } returns Result.success(
                buildJsonObject {
                    putJsonArray("posts") {
                        add(buildJsonObject {
                            put("post_id", "p1")
                            put("channel_id", "c1")
                            put("author_id", "author1")
                            put("content", "Hello")
                            put("is_pinned", "false")
                            put("created_at", "2025-01-01T00:00:00Z")
                        })
                    }
                    put("cursor", "cursor_abc")
                }
            )

            viewModel.loadFeed("c1")
            testDispatcher.scheduler.advanceUntilIdle()

            coEvery { mockApiClient.get("/v1/channels/c1/feed", any()) } returns Result.success(
                buildJsonObject {
                    putJsonArray("posts") {
                        add(buildJsonObject {
                            put("post_id", "p2")
                            put("channel_id", "c1")
                            put("author_id", "author2")
                            put("content", "More")
                            put("is_pinned", "false")
                            put("created_at", "2025-01-02T00:00:00Z")
                        })
                    }
                    put("cursor", "cursor_def")
                }
            )

            viewModel.loadMore("c1")
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(2, viewModel.uiState.value.feed.size)
            assertEquals("cursor_def", viewModel.uiState.value.cursor)
            assertFalse(viewModel.uiState.value.isLoadingMore)
        }

        @Test
        fun `does nothing when cursor is null`() = runTest {
            viewModel.loadMore("c1")
            testDispatcher.scheduler.advanceUntilIdle()

            assertTrue(viewModel.uiState.value.feed.isEmpty())
        }
    }

    @Nested
    @DisplayName("subscribe")
    inner class Subscribe {
        @Test
        fun `subscribes successfully`() = runTest {
            coEvery { mockApiClient.post("/v1/channels/c1/subscribe") } returns Result.success(buildJsonObject {})

            viewModel.subscribe("c1")
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals("Subscribed", viewModel.uiState.value.successMessage)
        }

        @Test
        fun `handles subscribe error`() = runTest {
            coEvery { mockApiClient.post("/v1/channels/c1/subscribe") } returns Result.failure(
                Exception("Subscribe failed")
            )

            viewModel.subscribe("c1")
            testDispatcher.scheduler.advanceUntilIdle()

            assertNull(viewModel.uiState.value.error, "error should stay null when subscribe fails silently")
        }

        @Test
        fun `handles unsubscribe error`() = runTest {
            coEvery { mockApiClient.del("/v1/channels/c1/subscribe") } returns Result.failure(
                Exception("Unsubscribe failed")
            )

            viewModel.unsubscribe("c1")
            testDispatcher.scheduler.advanceUntilIdle()

            assertNull(viewModel.uiState.value.error, "error should stay null when unsubscribe fails silently")
        }

    @Nested
    @DisplayName("unsubscribe")
    inner class Unsubscribe {
        @Test
        fun `unsubscribes successfully`() = runTest {
            coEvery { mockApiClient.del("/v1/channels/c1/subscribe") } returns Result.success(buildJsonObject {})

            viewModel.unsubscribe("c1")
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals("Unsubscribed", viewModel.uiState.value.successMessage)
        }

        @Test
        fun `handles unsubscribe error`() = runTest {
            coEvery { mockApiClient.del("/v1/channels/c1/subscribe") } returns Result.failure(
                Exception("Unsubscribe failed")
            )

            viewModel.unsubscribe("c1")
            testDispatcher.scheduler.advanceUntilIdle()

            assertNull(viewModel.uiState.value.error, "error should stay null when unsubscribe fails silently")
        }
    }

    @Nested
    @DisplayName("discoverChannels")
    inner class DiscoverChannels {
        @Test
        fun `discovers channels successfully`() = runTest {
            coEvery { mockApiClient.get("/v1/channels/discover") } returns Result.success(
                buildJsonObject {
                    putJsonArray("channels") {
                        add(buildJsonObject {
                            put("channel_id", "c1")
                            put("name", "Channel One")
                            put("description", "First channel")
                            put("subscriber_count", "100")
                            put("is_subscribed", "false")
                        })
                        add(buildJsonObject {
                            put("channel_id", "c2")
                            put("name", "Channel Two")
                            put("subscriber_count", "200")
                            put("is_subscribed", "true")
                        })
                    }
                }
            )

            viewModel.discoverChannels()
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(2, viewModel.uiState.value.discoverResults.size)
            assertEquals("Channel One", viewModel.uiState.value.discoverResults[0].name)
            assertEquals("First channel", viewModel.uiState.value.discoverResults[0].description)
            assertEquals(100, viewModel.uiState.value.discoverResults[0].subscriberCount)
            assertFalse(viewModel.uiState.value.discoverResults[0].isSubscribed)
            assertTrue(viewModel.uiState.value.discoverResults[1].isSubscribed)
            assertFalse(viewModel.uiState.value.isLoading)
        }

        @Test
        fun `handles discover error`() = runTest {
            coEvery { mockApiClient.get("/v1/channels/discover") } throws RuntimeException("Timeout")

            viewModel.discoverChannels()
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals("Timeout", viewModel.uiState.value.error)
            assertFalse(viewModel.uiState.value.isLoading)
        }
    }

    @Nested
    @DisplayName("createChannel")
    inner class CreateChannel {
        @Test
        fun `creates channel successfully`() = runTest {
            coEvery { mockApiClient.post("/v1/channels", any()) } returns Result.success(buildJsonObject {})
            coEvery { mockApiClient.get("/v1/channels/my") } returns Result.success(
                buildJsonObject { putJsonArray("channels") {} }
            )

            viewModel.createChannel("New Channel", "A description")
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals("Channel created", viewModel.uiState.value.successMessage)
            assertFalse(viewModel.uiState.value.isLoading)
        }

        @Test
        fun `handles create error`() = runTest {
            coEvery { mockApiClient.post("/v1/channels", any()) } returns Result.failure(
                Exception("Name required")
            )

            viewModel.createChannel("", null)
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals("Name required", viewModel.uiState.value.error)
            assertFalse(viewModel.uiState.value.isLoading)
        }
    }

    @Nested
    @DisplayName("loadMyChannels")
    inner class LoadMyChannels {
        @Test
        fun `loads my channels successfully`() = runTest {
            coEvery { mockApiClient.get("/v1/channels/my") } returns Result.success(
                buildJsonObject {
                    putJsonArray("channels") {
                        add(buildJsonObject {
                            put("channel_id", "c1")
                            put("name", "My Channel")
                            put("subscriber_count", "50")
                        })
                    }
                }
            )

            viewModel.loadMyChannels()
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(1, viewModel.uiState.value.myChannels.size)
            assertEquals("My Channel", viewModel.uiState.value.myChannels[0].name)
            assertTrue(viewModel.uiState.value.myChannels[0].isSubscribed)
            assertFalse(viewModel.uiState.value.isLoading)
        }

        @Test
        fun `handles load my channels error`() = runTest {
            coEvery { mockApiClient.get("/v1/channels/my") } throws RuntimeException("Forbidden")

            viewModel.loadMyChannels()
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals("Forbidden", viewModel.uiState.value.error)
            assertFalse(viewModel.uiState.value.isLoading)
        }
    }

    @Nested
    @DisplayName("searchChannels")
    inner class SearchChannels {
        @Test
        fun `searches channels successfully`() = runTest {
            coEvery { mockApiClient.get("/v1/channels/search", mapOf("q" to "test")) } returns Result.success(
                buildJsonObject {
                    putJsonArray("channels") {
                        add(buildJsonObject {
                            put("channel_id", "c1")
                            put("name", "Test Channel")
                            put("subscriber_count", "10")
                            put("is_subscribed", "false")
                        })
                    }
                }
            )

            viewModel.searchChannels("test")
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(1, viewModel.uiState.value.searchResults.size)
            assertEquals("Test Channel", viewModel.uiState.value.searchResults[0].name)
            assertEquals("test", viewModel.uiState.value.searchQuery)
        }

        @Test
        fun `clears results for blank query`() = runTest {
            viewModel.searchChannels("   ")
            testDispatcher.scheduler.advanceUntilIdle()

            assertTrue(viewModel.uiState.value.searchResults.isEmpty())
        }

        @Test
        fun `handles search error`() = runTest {
            coEvery { mockApiClient.get("/v1/channels/search", mapOf("q" to "fail")) } throws RuntimeException("Search failed")

            viewModel.searchChannels("fail")
            testDispatcher.scheduler.advanceUntilIdle()

            assertTrue(viewModel.uiState.value.searchResults.isEmpty())
        }
    }

    @Nested
    @DisplayName("clearMessages")
    inner class ClearMessages {
        @Test
        fun `clears error and success messages`() = runTest {
            viewModel.uiState.value.copy(error = "Error", successMessage = "Success")
            viewModel.clearMessages()
            assertNull(viewModel.uiState.value.error)
            assertNull(viewModel.uiState.value.successMessage)
        }
    }
}

}

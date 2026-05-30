package org.enchant.status

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("StatusViewModel — Full Coverage")
class StatusViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: StatusViewModel
    private val mockApiClient = mockk<org.enchant.core.network.ApiClient>()
    private val json = Json { ignoreUnknownKeys = true }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = StatusViewModel(mockApiClient)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Nested @DisplayName("Load Feed")
    inner class LoadFeedTest {
        @Test @DisplayName("loadFeed sets error on failure")
        fun `load feed error`() = runTest {
            coEvery { mockApiClient.get("/v1/status/feed") } returns Result.failure(Exception("Network error"))
            viewModel.loadFeed()
            advanceUntilIdle()
            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertEquals("Network error", state.error)
            assertTrue(state.feed.isEmpty())
        }

        @Test @DisplayName("loadFeed parses feed entries with is_mine flag")
        fun `load feed success`() = runTest {
            val feedJson = json.parseToJsonElement(
                """
                {
                    "feed": [
                        {
                            "status_id": "s1",
                            "user_id": "u1",
                            "username": "alice",
                            "type": "text",
                            "text": "Hello",
                            "background_color": "#FF0000",
                            "created_at": "2025-01-01T00:00:00Z",
                            "is_viewed": "true",
                            "is_mine": "true"
                        }
                    ]
                }
                """.trimIndent()
            ).jsonObject
            coEvery { mockApiClient.get("/v1/status/feed") } returns Result.success(feedJson)
            viewModel.loadFeed()
            advanceUntilIdle()
            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertNull(state.error)
            assertEquals(1, state.feed.size)
            assertEquals("s1", state.feed[0].statusId)
            assertEquals("alice", state.feed[0].username)
            assertTrue(state.feed[0].isMine)
            assertNotNull(state.myStatus)
            assertEquals("s1", state.myStatus?.statusId)
        }

        @Test @DisplayName("loadFeed handles empty feed")
        fun `load feed empty`() = runTest {
            val feedJson = json.parseToJsonElement("""{"feed": []}""").jsonObject
            coEvery { mockApiClient.get("/v1/status/feed") } returns Result.success(feedJson)
            viewModel.loadFeed()
            advanceUntilIdle()
            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertTrue(state.feed.isEmpty())
            assertNull(state.myStatus)
        }

        @Test @DisplayName("loadFeed handles missing feed field gracefully")
        fun `load feed missing field`() = runTest {
            val feedJson = json.parseToJsonElement("""{"other": []}""").jsonObject
            coEvery { mockApiClient.get("/v1/status/feed") } returns Result.success(feedJson)
            viewModel.loadFeed()
            advanceUntilIdle()
            val state = viewModel.uiState.value
            assertTrue(state.feed.isEmpty())
        }
    }

    @Nested @DisplayName("Create Text Status")
    inner class CreateTextStatusTest {
        @Test @DisplayName("createTextStatus succeeds")
        fun `create text status success`() = runTest {
            coEvery { mockApiClient.post("/v1/status", any()) } returns Result.success(JsonObject(emptyMap()))
            coEvery { mockApiClient.get("/v1/status/feed") } returns Result.success(
                json.parseToJsonElement("""{"feed":[]}""").jsonObject
            )
            viewModel.createTextStatus("Hello!", "#FF5733", StatusPrivacy.AllContacts)
            advanceUntilIdle()
            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertEquals("Status created", state.successMessage)
        }

        @Test @DisplayName("createTextStatus rejects text over 700 chars")
        fun `create text status too long`() = runTest {
            val longText = "a".repeat(701)
            viewModel.createTextStatus(longText, "#FF5733", StatusPrivacy.AllContacts)
            advanceUntilIdle()
            val state = viewModel.uiState.value
            assertEquals("Status text exceeds 700 characters", state.error)
        }

        @Test @DisplayName("createTextStatus handles error")
        fun `create text status error`() = runTest {
            coEvery { mockApiClient.post("/v1/status", any()) } returns Result.failure(Exception("Server error"))
            viewModel.createTextStatus("Hello", "#FF5733", StatusPrivacy.AllContacts)
            advanceUntilIdle()
            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertEquals("Server error", state.error)
        }

        @Test @DisplayName("createTextStatus with selected contacts sends selected_contacts")
        fun `create text status with selected contacts`() = runTest {
            coEvery { mockApiClient.post("/v1/status", any()) } returns Result.success(JsonObject(emptyMap()))
            coEvery { mockApiClient.get("/v1/status/feed") } returns Result.success(
                json.parseToJsonElement("""{"feed":[]}""").jsonObject
            )
            viewModel.createTextStatus(
                "Hello", "#FF5733",
                StatusPrivacy.Selected(userIds = listOf("u1", "u2")),
                listOf("u1", "u2")
            )
            advanceUntilIdle()
            val state = viewModel.uiState.value
            assertEquals("Status created", state.successMessage)
        }
    }

    @Nested @DisplayName("View Status")
    inner class ViewStatusTest {
        @Test @DisplayName("viewStatus calls API")
        fun `view status success`() = runTest {
            coEvery { mockApiClient.post("/v1/status/status-1/view") } returns Result.success(JsonObject(emptyMap()))
            viewModel.viewStatus("status-1")
            advanceUntilIdle()
        }

        @Test @DisplayName("viewStatus handles failure gracefully")
        fun `view status failure`() = runTest {
            coEvery { mockApiClient.post("/v1/status/status-1/view") } returns Result.failure(Exception("fail"))
            viewModel.viewStatus("status-1")
            advanceUntilIdle()
        }
    }

    @Nested @DisplayName("Get Viewers")
    inner class GetViewersTest {
        @Test @DisplayName("getViewers parses viewer list from views field")
        fun `get viewers success`() = runTest {
            val viewersJson = json.parseToJsonElement(
                """
                {
                    "views": [
                        {
                            "user_id": "v1",
                            "username": "bob",
                            "viewed_at": "2025-01-01T01:00:00Z"
                        }
                    ]
                }
                """.trimIndent()
            ).jsonObject
            coEvery { mockApiClient.get("/v1/status/s1/views") } returns Result.success(viewersJson)
            viewModel.getViewers("s1")
            advanceUntilIdle()
            val state = viewModel.uiState.value
            assertEquals(1, state.viewers.size)
            assertEquals("bob", state.viewers[0].username)
        }

        @Test @DisplayName("getViewers handles missing views field")
        fun `get viewers missing field`() = runTest {
            val viewersJson = json.parseToJsonElement("""{"other": []}""").jsonObject
            coEvery { mockApiClient.get("/v1/status/s1/views") } returns Result.success(viewersJson)
            viewModel.getViewers("s1")
            advanceUntilIdle()
            val state = viewModel.uiState.value
            assertTrue(state.viewers.isEmpty())
        }
    }

    @Nested @DisplayName("Delete Status")
    inner class DeleteStatusTest {
        @Test @DisplayName("deleteStatus succeeds")
        fun `delete status success`() = runTest {
            coEvery { mockApiClient.del("/v1/status/s1") } returns Result.success(JsonObject(emptyMap()))
            coEvery { mockApiClient.get("/v1/status/feed") } returns Result.success(
                json.parseToJsonElement("""{"feed":[]}""").jsonObject
            )
            viewModel.deleteStatus("s1")
            advanceUntilIdle()
            val state = viewModel.uiState.value
            assertEquals("Status deleted", state.successMessage)
        }

        @Test @DisplayName("deleteStatus handles error")
        fun `delete status error`() = runTest {
            coEvery { mockApiClient.del("/v1/status/s1") } returns Result.failure(Exception("not found"))
            viewModel.deleteStatus("s1")
            advanceUntilIdle()
            val state = viewModel.uiState.value
            assertEquals("not found", state.error)
        }
    }

    @Nested @DisplayName("UI State")
    inner class UiStateTest {
        @Test @DisplayName("uiState has default values")
        fun `ui state defaults`() = runTest {
            val state = viewModel.uiState.value
            assertNotNull(state)
            assertTrue(state.feed.isEmpty())
            assertNull(state.myStatus)
            assertTrue(state.viewers.isEmpty())
            assertFalse(state.isLoading)
            assertNull(state.error)
            assertNull(state.successMessage)
        }

        @Test @DisplayName("clearMessages resets error and success")
        fun `clear messages`() = runTest {
            coEvery { mockApiClient.post("/v1/status", any()) } returns Result.failure(Exception("err"))
            viewModel.createTextStatus("Hi", "#000", StatusPrivacy.AllContacts)
            advanceUntilIdle()
            assertNotNull(viewModel.uiState.value.error)
            viewModel.clearMessages()
            assertNull(viewModel.uiState.value.error)
            assertNull(viewModel.uiState.value.successMessage)
        }
    }

    @Nested @DisplayName("Privacy")
    inner class PrivacyTest {
        @Test @DisplayName("Selected privacy carries userIds")
        fun `selected privacy`() {
            val selected = StatusPrivacy.Selected(userIds = listOf("u1", "u2"))
            assertTrue(selected is StatusPrivacy.Selected)
            assertEquals(2, selected.userIds.size)
        }
    }
}

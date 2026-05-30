package org.enchant.polls

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.enchant.core.network.ApiClient

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("PollViewModel — Full Coverage")
class PollViewModelTest {

    private lateinit var apiClient: ApiClient
    private lateinit var viewModel: PollViewModel
    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        apiClient = mockk(relaxed = true)
        viewModel = PollViewModel(apiClient)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Nested @DisplayName("Create Poll")
    inner class CreatePollTest {
        @Test @DisplayName("createPoll sends correct params to API")
        fun `create poll sends correct params`() = runTest {
            coEvery { apiClient.post(any(), any()) } returns Result.success(
                kotlinx.serialization.json.JsonObject(kotlinx.serialization.json.buildJsonObject {
                    put("poll_id", kotlinx.serialization.json.JsonPrimitive("poll-123"))
                    put("conversation_id", kotlinx.serialization.json.JsonPrimitive("conv-1"))
                    put("question", kotlinx.serialization.json.JsonPrimitive("Favorite color?"))
                    put("options", kotlinx.serialization.json.JsonArray(listOf(
                        kotlinx.serialization.json.buildJsonObject {
                            put("id", kotlinx.serialization.json.JsonPrimitive("opt-1"))
                            put("text", kotlinx.serialization.json.JsonPrimitive("Red"))
                        }
                    )))
                })
            )
            viewModel.createPoll(
                conversationId = "conv-1",
                question = "Favorite color?",
                options = listOf("Red", "Blue"),
                allowMultiple = false,
                anonymous = false,
                closesInSeconds = null
            )
            testDispatcher.scheduler.advanceUntilIdle()
            coVerify { apiClient.post("/v1/polls", any()) }
        }
    }

    @Nested @DisplayName("Vote")
    inner class VoteTest {
        @Test @DisplayName("vote sends option_ids to correct endpoint")
        fun `vote sends correct params`() = runTest {
            coEvery { apiClient.post(any(), any()) } returns Result.success(
                kotlinx.serialization.json.JsonObject(kotlinx.serialization.json.buildJsonObject {
                    put("poll_id", kotlinx.serialization.json.JsonPrimitive("poll-1"))
                    put("question", kotlinx.serialization.json.JsonPrimitive("Test?"))
                    put("options", kotlinx.serialization.json.JsonArray(emptyList()))
                    put("results", kotlinx.serialization.json.JsonObject(kotlinx.serialization.json.buildJsonObject {}))
                    put("your_vote", kotlinx.serialization.json.JsonArray(listOf(kotlinx.serialization.json.JsonPrimitive("opt-1"))))
                    put("total_votes", kotlinx.serialization.json.JsonPrimitive(1))
                    put("is_closed", kotlinx.serialization.json.JsonPrimitive(false))
                    put("allow_multiple", kotlinx.serialization.json.JsonPrimitive(false))
                })
            )
            viewModel.vote("poll-1", listOf("opt-1"))
            testDispatcher.scheduler.advanceUntilIdle()
            coVerify { apiClient.post("/v1/polls/poll-1/vote", any()) }
        }
    }

    @Nested @DisplayName("Load Poll")
    inner class LoadPollTest {
        @Test @DisplayName("loadPoll fetches from correct endpoint")
        fun `load poll calls correct endpoint`() = runTest {
            coEvery { apiClient.get(any()) } returns Result.success(
                kotlinx.serialization.json.JsonObject(kotlinx.serialization.json.buildJsonObject {
                    put("poll_id", kotlinx.serialization.json.JsonPrimitive("poll-1"))
                    put("question", kotlinx.serialization.json.JsonPrimitive("Best color?"))
                    put("options", kotlinx.serialization.json.JsonArray(listOf(
                        kotlinx.serialization.json.buildJsonObject {
                            put("id", kotlinx.serialization.json.JsonPrimitive("1"))
                            put("text", kotlinx.serialization.json.JsonPrimitive("Red"))
                        }
                    )))
                    put("results", kotlinx.serialization.json.JsonObject(kotlinx.serialization.json.buildJsonObject {
                        put("1", kotlinx.serialization.json.JsonPrimitive(5))
                    }))
                    put("your_vote", kotlinx.serialization.json.JsonArray(emptyList()))
                    put("total_votes", kotlinx.serialization.json.JsonPrimitive(5))
                    put("is_closed", kotlinx.serialization.json.JsonPrimitive(false))
                    put("allow_multiple", kotlinx.serialization.json.JsonPrimitive(false))
                })
            )
            viewModel.loadPoll("poll-1")
            testDispatcher.scheduler.advanceUntilIdle()
            coVerify { apiClient.get("/v1/polls/poll-1") }
        }
    }

    @Nested @DisplayName("Close Poll")
    inner class ClosePollTest {
        @Test @DisplayName("closePoll calls correct endpoint")
        fun `close poll calls correct endpoint`() = runTest {
            coEvery { apiClient.put(any()) } returns Result.success(
                kotlinx.serialization.json.JsonObject(kotlinx.serialization.json.buildJsonObject {
                    put("poll_id", kotlinx.serialization.json.JsonPrimitive("poll-1"))
                })
            )
            viewModel.closePoll("poll-1")
            testDispatcher.scheduler.advanceUntilIdle()
            coVerify { apiClient.put("/v1/polls/poll-1/close") }
        }
    }

    @Nested @DisplayName("UI State")
    inner class UiStateTest {
        @Test @DisplayName("uiState has default values")
        fun `ui state defaults`() = runTest {
            val state = viewModel.uiState.value
            assertNotNull(state)
            assertNull(state.currentPoll)
            assertFalse(state.isSubmitting)
            assertNull(state.error)
            assertNull(state.successMessage)
        }
    }
}
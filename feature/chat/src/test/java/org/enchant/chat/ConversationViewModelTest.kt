package org.enchant.chat

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.enchant.chat.data.ConversationRepository
import org.enchant.chat.data.MessageSendPipeline
import org.enchant.chat.data.SendResult
import org.enchant.core.base.AppConfig
import org.enchant.core.base.SecurePreferences
import org.enchant.core.model.Conversation
import org.enchant.core.model.Message
import org.enchant.core.network.ApiClient

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("ConversationViewModel")
class ConversationViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: ConversationViewModel
    private val mockRepo = mockk<ConversationRepository>(relaxed = true)
    private val mockApi = mockk<ApiClient>(relaxed = true)
    private val mockPipeline = mockk<MessageSendPipeline>(relaxed = true)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { mockPipeline.sendMessage(any(), any(), any(), any()) } returns SendResult.Success("env1")
        coEvery { mockPipeline.sendMediaMessage(any(), any(), any(), any()) } returns SendResult.Success("env1")
        coEvery { mockPipeline.editMessage(any(), any(), any()) } returns Result.success(Unit)
        every { mockRepo.getMessages(any(), any(), any()) } returns flowOf(emptyList())
        coEvery { mockRepo.getConversation(any()) } returns null
        viewModel = ConversationViewModel(repo = mockRepo, apiClient = mockApi, pipeline = mockPipeline)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Nested
    @DisplayName("sendTextMessage")
    inner class SendTextMessage {
        @Test
        fun sendsNonEmptyTextSuccessfully() = runTest {
            val result = viewModel.sendTextMessage("Hello")
            testDispatcher.scheduler.advanceUntilIdle()
            assertTrue(result, "sendTextMessage should return true")
        }

        @Test
        fun refusesEmptyText() = runTest {
            val result = viewModel.sendTextMessage("")
            assertFalse(result, "sendTextMessage should return false for empty text")
        }
    }

    @Nested
    @DisplayName("editMessage")
    inner class EditMessage {
        @Test
        fun editsWithNonEmptyText() = runTest {
            val result = viewModel.editMessage("env1", "Updated")
            assertTrue(result, "editMessage should return true")
        }

        @Test
        fun refusesEmptyText() = runTest {
            val result = viewModel.editMessage("env1", "")
            assertFalse(result, "editMessage should return false for empty text")
        }
    }

    @Nested
    @DisplayName("sendSticker")
    inner class SendSticker {
        @Test
        fun sendsSticker() = runTest {
            val result = viewModel.sendSticker("pack1", "sticker1")
            testDispatcher.scheduler.advanceUntilIdle()
            assertTrue(result)
        }
    }

    @Nested
    @DisplayName("deleteMessage")
    inner class DeleteMessage {
        @Test
        fun deleteForSelfDoesNotThrow() = runTest {
            viewModel.deleteMessage("env1", false)
            testDispatcher.scheduler.advanceUntilIdle()
        }

        @Test
        fun deleteForEveryoneDoesNotThrow() = runTest {
            viewModel.deleteMessage("env1", true)
            testDispatcher.scheduler.advanceUntilIdle()
        }
    }

    @Nested
    @DisplayName("searchInConversation")
    inner class Search {
        @Test
        fun clearsResultsOnEmptyQuery() = runTest {
            viewModel.searchInConversation("")
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(0, viewModel.searchResults.value.size)
        }
    }
}

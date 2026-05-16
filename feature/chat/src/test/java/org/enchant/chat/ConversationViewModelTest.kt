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

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("ConversationViewModel")
class ConversationViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: ConversationViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = ConversationViewModel()
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

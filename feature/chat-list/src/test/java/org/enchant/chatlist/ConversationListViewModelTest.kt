package org.enchant.chatlist

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

import org.enchant.chat.data.ConversationFilter
import org.enchant.chat.data.ConversationRepository
import org.enchant.core.model.Conversation
import org.enchant.core.model.ConversationType
import org.enchant.core.network.ApiClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("ConversationListViewModel")
class ConversationListViewModelTest {

    private val mockRepo = mockk<ConversationRepository>(relaxed = true)
    private val mockApi = mockk<ApiClient>(relaxed = true)
    private lateinit var viewModel: ConversationListViewModel

    @BeforeEach
    fun setUp() {
        val testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        every { mockRepo.getConversations(any()) } returns flowOf(emptyList())
        every { mockRepo.getUnreadCount() } returns flowOf(0)
        every { mockRepo.searchConversations(any()) } returns flowOf(emptyList())
        viewModel = ConversationListViewModel(mockRepo, mockApi)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Nested @DisplayName("init")
    inner class InitTest {
        @Test @DisplayName("loads conversations on init")
        fun `loads conversations`() = runTest {
            val convs = listOf(
                Conversation("1", ConversationType.DIRECT, "Hello", 1000L, 0),
                Conversation("2", ConversationType.DIRECT, "Hi", 2000L, 0)
            )
            every { mockRepo.getConversations(any()) } returns flowOf(convs)
            viewModel.init()
            advanceUntilIdle()
            assert(viewModel.conversations.value.size == 2)
        }

        @Test @DisplayName("loads unread count on init")
        fun `loads unread count`() = runTest {
            every { mockRepo.getUnreadCount() } returns flowOf(5)
            viewModel.init()
            advanceUntilIdle()
            assert(viewModel.unreadCount.value == 5)
        }

        @Test @DisplayName("starts with empty conversations")
        fun `starts empty`() {
            assert(viewModel.conversations.value.isEmpty())
        }
    }

    @Nested @DisplayName("filter")
    inner class FilterTest {
        @Test @DisplayName("select filter emits new filter value")
        fun `filter test`() = runTest {
            viewModel.selectFilter(ConversationFilter.UNREAD)
            assert(viewModel.filter.value == ConversationFilter.UNREAD)
        }
    }

    @Nested @DisplayName("search")
    inner class SearchTest {
        @Test @DisplayName("search query is updated immediately")
        fun `search query`() = runTest {
            viewModel.init()
            viewModel.search("test")
            assert(viewModel.searchQuery.value == "test")
        }

        @Test @DisplayName("clearing search sets empty query")
        fun `clear search`() = runTest {
            viewModel.init()
            viewModel.search("test")
            viewModel.search("")
            assert(viewModel.searchQuery.value == "")
        }
    }

    @Nested @DisplayName("conversation actions")
    inner class ActionsTest {
        @Test @DisplayName("selectConversation emits navigation event")
        fun `select conversation`() {
            viewModel.selectConversation("conv42")
            assert(viewModel.navigationEvent.value == "conv42")
        }
    }
}
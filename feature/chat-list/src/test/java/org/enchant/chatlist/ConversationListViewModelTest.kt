package org.enchant.chatlist

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.enchant.chat.data.ConversationRepository
import org.enchant.core.model.Conversation
import org.enchant.core.model.ConversationType
import org.enchant.core.network.ApiClient
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("ConversationListViewModel — Full Coverage")
class ConversationListViewModelTest {

    private lateinit var repo: ConversationRepository
    private lateinit var apiClient: ApiClient
    private lateinit var viewModel: ConversationListViewModel

    @BeforeEach
    fun setUp() {
        repo = mockk(relaxed = true)
        apiClient = mockk(relaxed = true)
        coEvery { repo.getConversations(any()) } returns flowOf(emptyList())
        coEvery { repo.getUnreadCount() } returns flowOf(0)
        viewModel = ConversationListViewModel(repo, apiClient)
    }

    @Nested @DisplayName("Init")
    inner class InitTest {
        @Test @DisplayName("init loads conversations")
        fun `init loads`() = runTest {
            coEvery { repo.getConversations(any()) } returns flowOf(
                listOf(
                    Conversation(id = "conv-1", type = ConversationType.DIRECT, lastMessage = "Hello", unreadCount = 1, lastMessageTimestamp = 1000),
                    Conversation(id = "conv-2", type = ConversationType.GROUP, lastMessage = "Hi", unreadCount = 0, lastMessageTimestamp = 2000)
                )
            )
            viewModel.init()
            coVerify { repo.getConversations(any()) }
        }
    }

    @Nested @DisplayName("Search")
    inner class SearchTest {
        @Test @DisplayName("search updates search query")
        fun `search updates query`() = runTest {
            viewModel.search("Hello")
            assertEquals("Hello", viewModel.searchQuery.value)
        }

        @Test @DisplayName("search clears results for empty query")
        fun `search empty query`() = runTest {
            viewModel.search("")
            assertEquals("", viewModel.searchQuery.value)
        }
    }

    @Nested @DisplayName("Select Filter")
    inner class SelectFilterTest {
        @Test @DisplayName("selectFilter changes conversation filter")
        fun `select filter`() = runTest {
            viewModel.selectFilter(org.enchant.chat.data.ConversationFilter.UNREAD)
            assertEquals(org.enchant.chat.data.ConversationFilter.UNREAD, viewModel.filter.value)
        }
    }

    @Nested @DisplayName("Archive Conversation")
    inner class ArchiveTest {
        @Test @DisplayName("archiveConversation archives a conversation")
        fun `archive conversation`() = runTest {
            viewModel.archiveConversation("conv-1")
            coVerify { repo.setArchived("conv-1", true) }
        }

        @Test @DisplayName("unarchiveConversation unarchives a conversation")
        fun `unarchive conversation`() = runTest {
            viewModel.unarchiveConversation("conv-1")
            coVerify { repo.setArchived("conv-1", false) }
        }
    }

    @Nested @DisplayName("Pin Conversation")
    inner class PinTest {
        @Test @DisplayName("pinConversation pins a conversation")
        fun `pin conversation`() = runTest {
            viewModel.pinConversation("conv-1")
            coVerify { repo.setPinned("conv-1", true) }
        }
    }

    @Nested @DisplayName("Mute Conversation")
    inner class MuteTest {
        @Test @DisplayName("muteConversation mutes a conversation")
        fun `mute conversation`() = runTest {
            viewModel.muteConversation("conv-1", 86400)
            coVerify { repo.setMuted("conv-1", true, 86400) }
        }
    }

    @Nested @DisplayName("Delete Conversation")
    inner class DeleteTest {
        @Test @DisplayName("deleteConversation deletes a conversation")
        fun `delete conversation`() = runTest {
            viewModel.deleteConversation("conv-1")
            coVerify { repo.deleteConversation("conv-1") }
        }
    }

    @Nested @DisplayName("Mark Read")
    inner class MarkReadTest {
        @Test @DisplayName("markRead marks conversation as read")
        fun `mark read`() = runTest {
            viewModel.markRead("conv-1")
            coVerify { repo.markConversationRead("conv-1") }
        }
    }

    @Nested @DisplayName("Navigation")
    inner class NavigationTest {
        @Test @DisplayName("selectConversation emits navigation event")
        fun `select conversation`() = runTest {
            viewModel.selectConversation("conv-1")
            assertEquals("conv-1", viewModel.navigationEvent.value)
        }

        @Test @DisplayName("clearNavigationEvent clears navigation event")
        fun `clear navigation`() = runTest {
            viewModel.selectConversation("conv-1")
            viewModel.clearNavigationEvent()
            assertNull(viewModel.navigationEvent.value)
        }
    }
}

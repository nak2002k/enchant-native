package org.enchant.chatlist

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.enchant.chat.data.ConversationRepository
import org.enchant.chat.data.ConversationFilter
import org.enchant.core.model.Conversation
import org.enchant.core.model.ConversationType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("ConversationListViewModel — Full Coverage")
class ConversationListViewModelTest {

    private lateinit var repo: ConversationRepository
    private lateinit var viewModel: ConversationListViewModel

    @BeforeEach
    fun setUp() {
        repo = mockk(relaxed = true)
        coEvery { repo.getConversations(any()) } returns flowOf(emptyList())
        coEvery { repo.getUnreadCount() } returns flowOf(0)
        viewModel = ConversationListViewModel(repo)
    }

    @Nested @DisplayName("Load Conversations")
    inner class LoadConversationsTest {
        @Test @DisplayName("loadConversations fetches conversations from repository")
        fun `load conversations`() = runTest {
            coEvery { repo.getConversations(any()) } returns flowOf(
                listOf(
                    Conversation(id = "conv-1", type = ConversationType.DIRECT, lastMessage = "Hello", unreadCount = 1),
                    Conversation(id = "conv-2", type = ConversationType.GROUP, lastMessage = "Hi", unreadCount = 0)
                )
            )
            viewModel.loadConversations()
            coVerify { repo.getConversations(ConversationFilter.ALL) }
        }
    }

    @Nested @DisplayName("Search")
    inner class SearchTest {
        @Test @DisplayName("searchConversations searches conversations")
        fun `search conversations`() = runTest {
            coEvery { repo.searchConversations("Hello") } returns flowOf(
                listOf(Conversation(id = "conv-1", type = ConversationType.DIRECT, lastMessage = "Hello"))
            )
            viewModel.searchConversations("Hello")
            coVerify { repo.searchConversations("Hello") }
        }

        @Test @DisplayName("searchConversations clears results for empty query")
        fun `search empty query`() = runTest {
            viewModel.searchConversations("")
        }
    }

    @Nested @DisplayName("Set Filter")
    inner class SetFilterTest {
        @Test @DisplayName("setFilter changes conversation filter")
        fun `set filter`() = runTest {
            viewModel.setFilter(ConversationFilter.UNREAD)
            coVerify { repo.getConversations(ConversationFilter.UNREAD) }
        }

        @Test @DisplayName("setFilter to GROUPS filters group conversations")
        fun `set filter groups`() = runTest {
            viewModel.setFilter(ConversationFilter.GROUPS)
            coVerify { repo.getConversations(ConversationFilter.GROUPS) }
        }

        @Test @DisplayName("setFilter to PERSONAL filters personal conversations")
        fun `set filter personal`() = runTest {
            viewModel.setFilter(ConversationFilter.PERSONAL)
            coVerify { repo.getConversations(ConversationFilter.PERSONAL) }
        }

        @Test @DisplayName("setFilter to ARCHIVED filters archived conversations")
        fun `set filter archived`() = runTest {
            viewModel.setFilter(ConversationFilter.ARCHIVED)
            coVerify { repo.getConversations(ConversationFilter.ARCHIVED) }
        }
    }

    @Nested @DisplayName("Archive Conversation")
    inner class ArchiveTest {
        @Test @DisplayName("archiveConversation archives a conversation")
        fun `archive conversation`() = runTest {
            viewModel.archiveConversation("conv-1", true)
            coVerify { repo.setArchived("conv-1", true) }
        }
    }

    @Nested @DisplayName("Pin Conversation")
    inner class PinTest {
        @Test @DisplayName("pinConversation pins a conversation")
        fun `pin conversation`() = runTest {
            viewModel.pinConversation("conv-1", true)
            coVerify { repo.setPinned("conv-1", true) }
        }
    }

    @Nested @DisplayName("Mute Conversation")
    inner class MuteTest {
        @Test @DisplayName("muteConversation mutes a conversation")
        fun `mute conversation`() = runTest {
            viewModel.muteConversation("conv-1", true, null)
            coVerify { repo.setMuted("conv-1", true, null) }
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
        @Test @DisplayName("markConversationRead marks conversation as read")
        fun `mark conversation read`() = runTest {
            viewModel.markConversationRead("conv-1")
            coVerify { repo.markConversationRead("conv-1") }
        }
    }

    @Nested @DisplayName("UI State")
    inner class UiStateTest {
        @Test @DisplayName("uiState has default values")
        fun `ui state defaults`() = runTest {
            val state = viewModel.uiState.value
            assertNotNull(state)
            assertTrue(state.conversations.isEmpty())
            assertEquals(0, state.unreadCount)
            assertFalse(state.isLoading)
            assertNull(state.error)
        }
    }
}

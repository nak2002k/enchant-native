package org.enchant.contacts

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.enchant.contacts.data.Contact
import org.enchant.contacts.data.ContactResult
import org.enchant.contacts.data.ContactsRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("ContactsViewModel — Full Coverage")
class ContactsViewModelTest {

    private lateinit var repo: ContactsRepository
    private lateinit var viewModel: ContactsViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repo = mockk(relaxed = true)
        coEvery { repo.getContacts() } returns emptyList()
        coEvery { repo.syncContacts() } returns emptyList()
        viewModel = ContactsViewModel(repo)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkObject(ContactsRepository::class)
    }

    @Nested @DisplayName("Load Contacts")
    inner class LoadContactsTest {
        @Test @DisplayName("loadContacts syncs then fetches contacts from repository")
        fun `load contacts`() = runTest {
            coEvery { repo.syncContacts() } returns listOf(
                Contact(userId = "user-1", username = "alice", displayName = "Alice")
            )
            coEvery { repo.getContacts() } returns listOf(
                Contact(userId = "user-1", username = "alice", displayName = "Alice")
            )
            viewModel.loadContacts()
            coVerify { repo.syncContacts() }
            coVerify { repo.getContacts() }
        }
    }

    @Nested @DisplayName("Search Contacts")
    inner class SearchContactsTest {
        @Test @DisplayName("searchContacts searches contacts by query")
        fun `search contacts`() = runTest {
            coEvery { repo.searchUsers("ali") } returns listOf(
                Contact(userId = "user-1", username = "alice", displayName = "Alice")
            )
            viewModel.searchContacts("ali")
            testScheduler.advanceUntilIdle()
            coVerify { repo.searchUsers("ali") }
        }

        @Test @DisplayName("searchContacts clears results for empty query")
        fun `search empty query`() = runTest {
            viewModel.searchContacts("")
            assertTrue(viewModel.uiState.value.searchResults.isEmpty())
        }
    }

    @Nested @DisplayName("Block User")
    inner class BlockUserTest {
        @Test @DisplayName("blockUser updates contact isBlocked state")
        fun `block user updates state`() = runTest {
            coEvery { repo.getContacts() } returns listOf(
                Contact(userId = "user-1", username = "alice", displayName = "Alice")
            )
            coEvery { repo.blockUser("user-1") } returns ContactResult.Blocked(true)
            coEvery { repo.getBlockedUsers() } returns emptyList()
            viewModel.loadContacts()
            viewModel.blockUser("user-1")
            val state = viewModel.uiState.value
            assertTrue(state.contacts.any { it.userId == "user-1" && it.isBlocked })
            assertEquals("User blocked", state.successMessage)
        }
    }

    @Nested @DisplayName("Unblock User")
    inner class UnblockUserTest {
        @Test @DisplayName("unblockUser updates contact isBlocked state")
        fun `unblock user updates state`() = runTest {
            coEvery { repo.getContacts() } returns listOf(
                Contact(userId = "user-1", username = "alice", displayName = "Alice", isBlocked = true)
            )
            coEvery { repo.unblockUser("user-1") } returns ContactResult.Unblocked(true)
            coEvery { repo.getBlockedUsers() } returns emptyList()
            viewModel.loadContacts()
            viewModel.unblockUser("user-1")
            val state = viewModel.uiState.value
            assertFalse(state.contacts.any { it.userId == "user-1" && it.isBlocked })
            assertEquals("User unblocked", state.successMessage)
        }
    }

    @Nested @DisplayName("UI State")
    inner class UiStateTest {
        @Test @DisplayName("uiState has default values")
        fun `ui state defaults`() = runTest {
            val state = viewModel.uiState.value
            assertNotNull(state)
            assertTrue(state.contacts.isEmpty())
            assertFalse(state.isLoading)
            assertNull(state.error)
        }
    }
}

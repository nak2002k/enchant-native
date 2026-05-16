package org.enchant.contacts

import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.enchant.contacts.data.Contact
import org.enchant.contacts.data.ContactResult
import org.enchant.contacts.data.ContactsRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("ContactsViewModel")
class ContactsViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val mockRepository: ContactsRepository = mockk()
    private lateinit var viewModel: ContactsViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = ContactsViewModel(mockRepository)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Nested
    @DisplayName("loadContacts")
    inner class LoadContacts {
        @Test
        fun `loads contacts successfully`() = runTest {
            val testContacts = listOf(
                Contact("u1", "alice", "Alice"),
                Contact("u2", "bob", "Bob")
            )
            coEvery { mockRepository.getContacts() } returns testContacts

            viewModel.loadContacts()
            testDispatcher.scheduler.advanceUntilIdle()

            assert(viewModel.uiState.value.contacts.size == 2)
            assert(viewModel.uiState.value.contacts[0].displayName == "Alice")
            assert(!viewModel.uiState.value.isLoading)
        }

        @Test
        fun `loads empty contacts list`() = runTest {
            coEvery { mockRepository.getContacts() } returns emptyList()

            viewModel.loadContacts()
            testDispatcher.scheduler.advanceUntilIdle()

            assert(viewModel.uiState.value.contacts.isEmpty())
            assert(!viewModel.uiState.value.isLoading)
        }

        @Test
        fun `handles error when loading contacts`() = runTest {
            coEvery { mockRepository.getContacts() } throws RuntimeException("Network error")

            viewModel.loadContacts()
            testDispatcher.scheduler.advanceUntilIdle()

            assert(viewModel.uiState.value.error != null)
            assert(!viewModel.uiState.value.isLoading)
        }
    }

    @Nested
    @DisplayName("searchContacts")
    inner class SearchContacts {
        @Test
        fun `searches by username prefix`() = runTest {
            val results = listOf(
                Contact("u1", "alice", "Alice"),
                Contact("u3", "alex", "Alex")
            )
            coEvery { mockRepository.searchUsers("al") } returns results

            viewModel.searchContacts("al")
            testDispatcher.scheduler.advanceUntilIdle()

            assert(viewModel.uiState.value.searchQuery == "al")
            assert(viewModel.uiState.value.searchResults.size == 2)
        }

        @Test
        fun `empty query clears search`() = runTest {
            viewModel.searchContacts("")
            testDispatcher.scheduler.advanceUntilIdle()

            assert(viewModel.uiState.value.searchResults.isEmpty())
        }

        @Test
        fun `no results returns empty`() = runTest {
            coEvery { mockRepository.searchUsers("zzz") } returns emptyList()

            viewModel.searchContacts("zzz")
            testDispatcher.scheduler.advanceUntilIdle()

            assert(viewModel.uiState.value.searchResults.isEmpty())
        }
    }

    @Nested
    @DisplayName("addContact")
    inner class AddContact {
        @Test
        fun `adds contact successfully`() = runTest {
            coEvery { mockRepository.getContacts() } returns emptyList()
            coEvery { mockRepository.addContact("u1", null) } returns ContactResult.Added(true)

            viewModel.addContact("u1")
            testDispatcher.scheduler.advanceUntilIdle()

            assert(viewModel.uiState.value.successMessage == "Contact added")
        }

        @Test
        fun `fails to add duplicate contact`() = runTest {
            coEvery { mockRepository.addContact("u1", null) } returns ContactResult.Failed("Contact already exists")

            viewModel.addContact("u1")
            testDispatcher.scheduler.advanceUntilIdle()

            assert(viewModel.uiState.value.error == "Contact already exists")
        }

        @Test
        fun `adds contact with custom name`() = runTest {
            coEvery { mockRepository.getContacts() } returns emptyList()
            coEvery { mockRepository.addContact("u1", "My Friend") } returns ContactResult.Added(true)

            viewModel.addContact("u1", "My Friend")
            testDispatcher.scheduler.advanceUntilIdle()

            assert(viewModel.uiState.value.successMessage == "Contact added")
        }
    }

    @Nested
    @DisplayName("removeContact")
    inner class RemoveContact {
        @Test
        fun `removes contact successfully`() = runTest {
            coEvery { mockRepository.getContacts() } returns emptyList()
            coEvery { mockRepository.removeContact("u1") } returns ContactResult.Removed(true)

            viewModel.removeContact("u1")
            testDispatcher.scheduler.advanceUntilIdle()

            assert(viewModel.uiState.value.successMessage == "Contact removed")
        }

        @Test
        fun `fails to remove non-existent contact`() = runTest {
            coEvery { mockRepository.removeContact("nonexistent") } returns ContactResult.Failed("Contact not found")

            viewModel.removeContact("nonexistent")
            testDispatcher.scheduler.advanceUntilIdle()

            assert(viewModel.uiState.value.error == "Contact not found")
        }
    }

    @Nested
    @DisplayName("blockUser")
    inner class BlockUser {
        @Test
        fun `blocks user successfully`() = runTest {
            coEvery { mockRepository.getBlockedUsers() } returns emptyList()
            coEvery { mockRepository.blockUser("u1") } returns ContactResult.Added(true)

            viewModel.blockUser("u1")
            testDispatcher.scheduler.advanceUntilIdle()

            assert(viewModel.uiState.value.successMessage == "User blocked")
        }

        @Test
        fun `fails to block user`() = runTest {
            coEvery { mockRepository.blockUser("u1") } returns ContactResult.Failed("Cannot block self")

            viewModel.blockUser("u1")
            testDispatcher.scheduler.advanceUntilIdle()

            assert(viewModel.uiState.value.error == "Cannot block self")
        }
    }

    @Nested
    @DisplayName("unblockUser")
    inner class UnblockUser {
        @Test
        fun `unblocks user successfully`() = runTest {
            coEvery { mockRepository.getBlockedUsers() } returns emptyList()
            coEvery { mockRepository.unblockUser("u1") } returns ContactResult.Removed(true)

            viewModel.unblockUser("u1")
            testDispatcher.scheduler.advanceUntilIdle()

            assert(viewModel.uiState.value.successMessage == "User unblocked")
        }
    }

    @Nested
    @DisplayName("loadBlockedUsers")
    inner class LoadBlockedUsers {
        @Test
        fun `loads blocked users`() = runTest {
            val blocked = listOf(Contact("u1", username = "blocked_user"))
            coEvery { mockRepository.getBlockedUsers() } returns blocked

            viewModel.loadBlockedUsers()
            testDispatcher.scheduler.advanceUntilIdle()

            assert(viewModel.uiState.value.blockedUsers.size == 1)
            assert(viewModel.uiState.value.blockedUsers[0].userId == "u1")
        }

        @Test
        fun `empty blocked list`() = runTest {
            coEvery { mockRepository.getBlockedUsers() } returns emptyList()

            viewModel.loadBlockedUsers()
            testDispatcher.scheduler.advanceUntilIdle()

            assert(viewModel.uiState.value.blockedUsers.isEmpty())
        }
    }

    @Nested
    @DisplayName("clearMessages")
    inner class ClearMessages {
        @Test
        fun `clears error message`() = runTest {
            viewModel = ContactsViewModel(mockRepository)
            _uiStateField.set(viewModel, ContactsUiState(error = "test error"))

            viewModel.clearMessages()

            assert(viewModel.uiState.value.error == null)
        }

        @Test
        fun `clears success message`() = runTest {
            viewModel = ContactsViewModel(mockRepository)
            _uiStateField.set(viewModel, ContactsUiState(successMessage = "test success"))

            viewModel.clearMessages()

            assert(viewModel.uiState.value.successMessage == null)
        }

        @Test
        fun `clears both messages simultaneously`() = runTest {
            viewModel = ContactsViewModel(mockRepository)
            _uiStateField.set(viewModel, ContactsUiState(error = "err", successMessage = "ok"))

            viewModel.clearMessages()

            assert(viewModel.uiState.value.error == null)
            assert(viewModel.uiState.value.successMessage == null)
        }
    }

    companion object {
        private val _uiStateField = ContactsViewModel::class.java.getDeclaredField("_uiState").apply {
            isAccessible = true
        }
    }
}

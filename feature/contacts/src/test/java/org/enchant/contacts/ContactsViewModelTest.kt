package org.enchant.contacts

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.enchant.contacts.data.ContactsRepository
import org.enchant.core.model.User
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("ContactsViewModel — Full Coverage")
class ContactsViewModelTest {

    private lateinit var repo: ContactsRepository
    private lateinit var viewModel: ContactsViewModel

    @BeforeEach
    fun setUp() {
        repo = mockk(relaxed = true)
        coEvery { repo.getContacts() } returns flowOf(emptyList())
        coEvery { repo.searchContacts(any()) } returns flowOf(emptyList())
        viewModel = ContactsViewModel(repo)
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(ContactsRepository::class)
    }

    @Nested @DisplayName("Load Contacts")
    inner class LoadContactsTest {
        @Test @DisplayName("loadContacts fetches contacts from repository")
        fun `load contacts`() = runTest {
            coEvery { repo.getContacts() } returns flowOf(
                listOf(User(userId = "user-1", username = "alice", displayName = "Alice"))
            )
            viewModel.loadContacts()
            coVerify { repo.getContacts() }
        }
    }

    @Nested @DisplayName("Search Contacts")
    inner class SearchContactsTest {
        @Test @DisplayName("searchContacts searches contacts by query")
        fun `search contacts`() = runTest {
            coEvery { repo.searchContacts("ali") } returns flowOf(
                listOf(User(userId = "user-1", username = "alice", displayName = "Alice"))
            )
            viewModel.searchContacts("ali")
            coVerify { repo.searchContacts("ali") }
        }

        @Test @DisplayName("searchContacts clears results for empty query")
        fun `search empty query`() = runTest {
            viewModel.searchContacts("")
            assertTrue(viewModel.uiState.value.searchResults.isEmpty())
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

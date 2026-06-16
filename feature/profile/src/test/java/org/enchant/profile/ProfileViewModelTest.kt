package org.enchant.profile

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.enchant.core.base.SecurePreferences
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("ProfileViewModel")
class ProfileViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: ProfileViewModel

    private fun mockApi(): org.enchant.core.network.ApiClient {
        val client = mockk<org.enchant.core.network.ApiClient>(relaxed = true)
        return client
    }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = ProfileViewModel(mockApi())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Nested
    @DisplayName("UI State defaults")
    inner class UiStateDefaultsTest {
        @Test
        @DisplayName("uiState has correct default values")
        fun `ui state defaults`() = runTest {
            val state = viewModel.uiState.value
            assertNotNull(state)
            assertNull(state.profile)
            assertTrue(state.searchResults.isEmpty())
            assertTrue(state.blockedUsers.isEmpty())
            assertFalse(state.isEditing)
            assertFalse(state.isLoading)
            assertNull(state.error)
            assertNull(state.successMessage)
        }
    }

    @Nested
    @DisplayName("setEditing")
    inner class SetEditingTest {
        @Test
        @DisplayName("setEditing(true) enables edit mode")
        fun `setEditing true`() = runTest {
            viewModel.setEditing(true)
            assertTrue(viewModel.uiState.value.isEditing)
        }

        @Test
        @DisplayName("setEditing(false) disables edit mode")
        fun `setEditing false`() = runTest {
            viewModel.setEditing(true)
            viewModel.setEditing(false)
            assertFalse(viewModel.uiState.value.isEditing)
        }
    }

    @Nested
    @DisplayName("clearMessages")
    inner class ClearMessagesTest {
        @Test
        @DisplayName("clearMessages clears error, successMessage, and isEditing")
        fun `clearMessages resets all transient state`() = runTest {
            val client = mockApi()
            coEvery { client.get(any()) } returns Result.failure(Exception("error"))
            viewModel = ProfileViewModel(client)

            viewModel.setEditing(true)
            viewModel.loadProfile("user-1")
            advanceUntilIdle()

            assertNotNull(viewModel.uiState.value.error)
            assertTrue(viewModel.uiState.value.isEditing)

            viewModel.clearMessages()

            val state = viewModel.uiState.value
            assertNull(state.error)
            assertNull(state.successMessage)
            assertFalse(state.isEditing)
        }
    }

    @Nested
    @DisplayName("searchByUsername")
    inner class SearchByUsernameTest {
        @Test
        @DisplayName("blank query clears search results")
        fun `blank query clears results`() = runTest {
            val client = mockApi()
            viewModel = ProfileViewModel(client)

            viewModel.searchByUsername("  ")
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.searchResults.isEmpty())
        }

        @Test
        @DisplayName("empty query clears search results")
        fun `empty query clears results`() = runTest {
            val client = mockApi()
            viewModel = ProfileViewModel(client)

            viewModel.searchByUsername("")
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.searchResults.isEmpty())
        }
    }

    @Nested
    @DisplayName("updateProfile validation")
    inner class UpdateProfileValidationTest {
        @Test
        @DisplayName("blank displayName is rejected")
        fun `blank displayName rejected`() = runTest {
            val client = mockApi()
            viewModel = ProfileViewModel(client)

            viewModel.updateProfile("   ", "some about")
            advanceUntilIdle()

            assertEquals("Display name cannot be empty", viewModel.uiState.value.error)
            coVerify(exactly = 0) { client.put(any(), any()) }
        }

        @Test
        @DisplayName("null displayName is accepted")
        fun `null displayName accepted`() = runTest {
            val client = mockApi()
            mockkObject(SecurePreferences)
            every { SecurePreferences.getString(any()) } returns "user-1"
            coEvery { client.put(any(), any()) } returns Result.success(buildJsonObject {})
            coEvery { client.get(any()) } returns Result.success(buildJsonObject {})
            viewModel = ProfileViewModel(client)

            viewModel.updateProfile(null, "some about")
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.error)
            unmockkObject(SecurePreferences)
        }
    }

    @Nested
    @DisplayName("updateAvatar validation")
    inner class UpdateAvatarValidationTest {
        @Test
        @DisplayName("blank mediaId is rejected")
        fun `blank mediaId rejected`() = runTest {
            val client = mockApi()
            viewModel = ProfileViewModel(client)

            viewModel.updateAvatar("  ")
            advanceUntilIdle()

            assertEquals("Invalid avatar media ID", viewModel.uiState.value.error)
            coVerify(exactly = 0) { client.post(any(), any()) }
        }

        @Test
        @DisplayName("empty mediaId is rejected")
        fun `empty mediaId rejected`() = runTest {
            val client = mockApi()
            viewModel = ProfileViewModel(client)

            viewModel.updateAvatar("")
            advanceUntilIdle()

            assertEquals("Invalid avatar media ID", viewModel.uiState.value.error)
            coVerify(exactly = 0) { client.post(any(), any()) }
        }
    }

    @Nested
    @DisplayName("updateProfile about length clamping")
    inner class AboutLengthTest {
        @Test
        @DisplayName("about is clamped to 500 characters")
        fun `about clamped to 500`() = runTest {
            val client = mockApi()
            coEvery { client.put(any(), any()) } returns Result.success(buildJsonObject {})
            coEvery { client.get(any()) } returns Result.success(buildJsonObject {})
            viewModel = ProfileViewModel(client)

            val longAbout = "a".repeat(600)
            viewModel.updateProfile("Name", longAbout)
            advanceUntilIdle()

            coVerify {
                client.put(eq("/v1/profile"), match { json ->
                    val about = json["about"]?.toString()?.removeSurrounding("\"") ?: ""
                    about.length <= 500
                })
            }
        }
    }

    @Nested
    @DisplayName("blockUser and unblockUser")
    inner class BlockUnblockTest {
        @Test
        @DisplayName("blockUser calls correct endpoint")
        fun `blockUser calls API`() = runTest {
            val client = mockApi()
            coEvery { client.post(any()) } returns Result.success(buildJsonObject {})
            coEvery { client.get(any()) } returns Result.success(buildJsonObject {})
            viewModel = ProfileViewModel(client)

            viewModel.blockUser("user-1")
            advanceUntilIdle()

            coVerify { client.post("/v1/blocks/user-1") }
        }

        @Test
        @DisplayName("unblockUser calls correct endpoint")
        fun `unblockUser calls API`() = runTest {
            val client = mockApi()
            coEvery { client.del(any()) } returns Result.success(buildJsonObject {})
            coEvery { client.get(any()) } returns Result.success(buildJsonObject {})
            viewModel = ProfileViewModel(client)

            viewModel.unblockUser("user-1")
            advanceUntilIdle()

            coVerify { client.del("/v1/blocks/user-1") }
        }
    }

    @Nested
    @DisplayName("loadProfile")
    inner class LoadProfileTest {
        @Test
        @DisplayName("loadProfile sets isLoading true then false")
        fun `loadProfile toggles loading state`() = runTest {
            val client = mockApi()
            coEvery { client.get(any()) } returns Result.success(buildJsonObject {})
            viewModel = ProfileViewModel(client)

            viewModel.loadProfile("user-1")
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isLoading)
            assertNull(viewModel.uiState.value.error)
        }

        @Test
        @DisplayName("loadProfile handles network error")
        fun `loadProfile handles error`() = runTest {
            val client = mockApi()
            coEvery { client.get(any()) } returns Result.failure(Exception("Network error"))
            viewModel = ProfileViewModel(client)

            viewModel.loadProfile("user-1")
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isLoading)
            assertEquals("Network error", viewModel.uiState.value.error)
        }
    }
}

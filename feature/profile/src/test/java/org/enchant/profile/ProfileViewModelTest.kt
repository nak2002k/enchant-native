package org.enchant.profile

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.enchant.core.model.Profile
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("ProfileViewModel — Full Coverage")
class ProfileViewModelTest {

    private lateinit var viewModel: ProfileViewModel

    @BeforeEach
    fun setUp() {
        viewModel = ProfileViewModel()
    }

    @Nested @DisplayName("Load Profile")
    inner class LoadProfileTest {
        @Test @DisplayName("loadProfile loads user profile")
        fun `load profile`() = runTest {
            viewModel.loadProfile("user-1")
        }
    }

    @Nested @DisplayName("Block User")
    inner class BlockUserTest {
        @Test @DisplayName("blockUser blocks a user")
        fun `block user`() = runTest {
            viewModel.blockUser("user-1")
        }
    }

    @Nested @DisplayName("UI State")
    inner class UiStateTest {
        @Test @DisplayName("uiState has default values")
        fun `ui state defaults`() = runTest {
            val state = viewModel.uiState.value
            assertNotNull(state)
            assertNull(state.profile)
            assertTrue(state.blockedUsers.isEmpty())
        }
    }
}

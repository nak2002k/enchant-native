package org.enchant.auth

import org.enchant.core.auth.AuthState
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("AuthViewModel")
class AuthViewModelTest {

    private lateinit var viewModel: AuthViewModel

    @BeforeEach
    fun setUp() {
        viewModel = AuthViewModel()
    }

    @Nested @DisplayName("initial state")
    inner class InitialStateTest {
        @Test @DisplayName("authState starts as Unknown")
        fun `auth state unknown`() {
            assertTrue(viewModel.authState.value is AuthState.Unknown)
        }

        @Test @DisplayName("viewModel is not null")
        fun `not null`() {
            assertNotNull(viewModel)
        }
    }
}
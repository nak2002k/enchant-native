package org.enchant.auth

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import org.enchant.core.auth.AuthManager
import org.enchant.core.auth.AuthState
import org.enchant.core.auth.RegistrationState
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("AuthViewModel — Full Coverage")
class AuthViewModelTest {

    @BeforeEach
    fun setUp() {
        mockkObject(AuthManager)
        every { AuthManager.authState } returns kotlinx.coroutines.flow.MutableStateFlow(AuthState.Unauthenticated)
        every { AuthManager.currentState } returns kotlinx.coroutines.flow.MutableStateFlow(RegistrationState.Welcome)
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(AuthManager)
    }

    @Nested @DisplayName("Request OTP")
    inner class RequestOtpTest {
        @Test @DisplayName("requestOtp calls AuthManager.requestOtp")
        fun `request otp`() = runTest {
            val viewModel = AuthViewModel()
            coEvery { AuthManager.requestOtp(any()) } returns kotlin.Result.success(Unit)
            viewModel.requestOtp("+15551234567")
            coVerify { AuthManager.requestOtp("+15551234567") }
        }
    }

    @Nested @DisplayName("Verify OTP")
    inner class VerifyOtpTest {
        @Test @DisplayName("verifyOtp calls AuthManager.verifyOtp")
        fun `verify otp`() = runTest {
            val viewModel = AuthViewModel()
            coEvery { AuthManager.verifyOtp(any()) } returns kotlin.Result.success(Unit)
            viewModel.verifyOtp("123456")
            coVerify { AuthManager.verifyOtp("123456") }
        }
    }

    @Nested @DisplayName("Resend OTP")
    inner class ResendOtpTest {
        @Test @DisplayName("resendOtp calls AuthManager.resendOtp")
        fun `resend otp`() = runTest {
            val viewModel = AuthViewModel()
            coEvery { AuthManager.resendOtp() } returns kotlin.Result.success(Unit)
            viewModel.resendOtp()
            coVerify { AuthManager.resendOtp() }
        }
    }

    @Nested @DisplayName("Register Keys")
    inner class RegisterKeysTest {
        @Test @DisplayName("registerKeys calls AuthManager.registerKeys")
        fun `register keys`() = runTest {
            val viewModel = AuthViewModel()
            coEvery { AuthManager.registerKeys() } returns kotlin.Result.success(Unit)
            viewModel.registerKeys()
            coVerify { AuthManager.registerKeys() }
        }
    }

    @Nested @DisplayName("Auth State")
    inner class AuthStateTest {
        @Test @DisplayName("authState reflects AuthManager.authState")
        fun `auth state reflects manager`() = runTest {
            val viewModel = AuthViewModel()
            val state = viewModel.authState.value
            assertTrue(state is AuthState.Unauthenticated)
        }
    }

    @Nested @DisplayName("Registration State")
    inner class RegistrationStateTest {
        @Test @DisplayName("registrationState reflects AuthManager.currentState")
        fun `registration state reflects manager`() = runTest {
            val viewModel = AuthViewModel()
            val state = viewModel.registrationState.value
            assertTrue(state is RegistrationState.Welcome)
        }
    }
}

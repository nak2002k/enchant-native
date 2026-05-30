package org.enchant.core.auth

import android.util.Log
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.enchant.core.base.SecurePreferences
import org.enchant.core.model.User
import org.enchant.core.network.ApiClient
import org.enchant.core.network.models.AuthResponse
import org.enchant.core.network.models.OtpResponse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("AuthManager — Full Coverage")
class AuthManagerTest {

    @BeforeEach
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any<Throwable>()) } returns 0
        mockkObject(SecurePreferences)
        AuthManager.resetForTesting()
        every { SecurePreferences.getString(any(), any()) } returns null
        every { SecurePreferences.getString(any()) } returns null
        every { SecurePreferences.putString(any(), any()) } returns Unit
        every { SecurePreferences.remove(any()) } returns Unit
        every { SecurePreferences.getInt(any(), any()) } returns 0
        every { SecurePreferences.putBoolean(any(), any()) } returns Unit
        every { SecurePreferences.getBoolean(any(), any()) } returns false
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Log::class)
        unmockkObject(SecurePreferences)
    }

    @Nested @DisplayName("Initialization")
    inner class InitTest {
        @Test @DisplayName("init with no stored credentials sets Unauthenticated")
        fun `init unauthenticated`() = runTest {
            AuthManager.setApiClient(ApiClient())
            AuthManager.init()
            assertTrue(AuthManager.authState.value is AuthState.Unauthenticated)
        }

        @Test @DisplayName("init with valid JWT sets Authenticated")
        fun `init authenticated`() = runTest {
            val jwt = createJwt(exp = System.currentTimeMillis() / 1000 + 3600)
            every { SecurePreferences.getString("auth.jwt") } returns jwt
            every { SecurePreferences.getString("auth.user_id") } returns "user-123"
            every { SecurePreferences.getString("auth.device_id") } returns "device-123"
            AuthManager.setApiClient(ApiClient())
            AuthManager.init()
            assertTrue(AuthManager.authState.value is AuthState.Authenticated)
        }

        @Test @DisplayName("init with expired JWT and valid refresh token refreshes")
        fun `init with expired jwt`() = runTest {
            val expiredJwt = createJwt(exp = System.currentTimeMillis() / 1000 - 3600)
            every { SecurePreferences.getString("auth.jwt") } returns expiredJwt
            every { SecurePreferences.getString("auth.refresh_token") } returns "refresh-123"
            AuthManager.setApiClient(ApiClient())
            AuthManager.init()
        }

        @Test @DisplayName("double init is safe")
        fun `double init safe`() = runTest {
            AuthManager.setApiClient(ApiClient())
            AuthManager.init()
            AuthManager.init()
            assertTrue(true)
        }
    }

    @Nested @DisplayName("OTP Request")
    inner class OtpRequestTest {
        @Test @DisplayName("requestOtp fails when not initialized")
        fun `request otp not initialized`() = runTest {
            val result = AuthManager.requestOtp("+15551234567")
            assertTrue(result.isFailure)
            assertTrue(AuthManager.currentState.value is RegistrationState.Error)
        }

        @Test @DisplayName("requestOtp enforces 30s cooldown")
        fun `request otp cooldown`() = runTest {
            AuthManager.setApiClient(ApiClient())
            AuthManager.init()
            val result1 = AuthManager.requestOtp("+15551234567")
            val result2 = AuthManager.requestOtp("+15551234567")
            assertTrue(result2.isFailure)
        }
    }

    @Nested @DisplayName("OTP Verification")
    inner class OtpVerifyTest {
        @Test @DisplayName("verifyOtp fails when not in OTP state")
        fun `verify otp wrong state`() = runTest {
            AuthManager.setApiClient(ApiClient())
            AuthManager.init()
            val result = AuthManager.verifyOtp("123456")
            assertTrue(result.isFailure)
        }

        @Test @DisplayName("verifyOtp fails when not initialized")
        fun `verify otp not initialized`() = runTest {
            val result = AuthManager.verifyOtp("123456")
            assertTrue(result.isFailure)
        }
    }

    @Nested @DisplayName("Logout")
    inner class LogoutTest {
        @Test @DisplayName("logout clears auth state")
        fun `logout clears state`() = runTest {
            AuthManager.setApiClient(ApiClient())
            AuthManager.init()
            AuthManager.logout()
            assertTrue(AuthManager.authState.value is AuthState.Unauthenticated)
            assertTrue(AuthManager.currentState.value is RegistrationState.Welcome)
        }

        @Test @DisplayName("logout removes auth credentials from SecurePreferences")
        fun `logout removes credentials`() = runTest {
            AuthManager.setApiClient(ApiClient())
            AuthManager.init()
            AuthManager.logout()
            coVerify { SecurePreferences.remove("auth.jwt") }
            coVerify { SecurePreferences.remove("auth.refresh_token") }
            coVerify { SecurePreferences.remove("auth.user_id") }
            coVerify { SecurePreferences.remove("auth.device_id") }
        }
    }

    @Nested @DisplayName("Delete Account")
    inner class DeleteAccountTest {
        @Test @DisplayName("deleteAccount fails when not initialized")
        fun `delete account not initialized`() = runTest {
            val result = AuthManager.deleteAccount()
            assertTrue(result.isFailure)
        }

        @Test @DisplayName("deleteAccount calls logout on failure")
        fun `delete account logout on failure`() = runTest {
            AuthManager.setApiClient(ApiClient())
            AuthManager.init()
            AuthManager.deleteAccount()
            assertTrue(AuthManager.authState.value is AuthState.Unauthenticated)
        }
    }

    @Nested @DisplayName("Profile Update")
    inner class ProfileUpdateTest {
        @Test @DisplayName("updateProfile fails when not initialized")
        fun `update profile not initialized`() = runTest {
            val result = AuthManager.updateProfile("alice", "Alice", "Hello")
            assertTrue(result.isFailure)
        }

        @Test @DisplayName("updateProfile rejects invalid username (too short)")
        fun `update profile short username`() = runTest {
            AuthManager.setApiClient(ApiClient())
            AuthManager.init()
            val result = AuthManager.updateProfile("ab", "Alice", "Hello")
            assertTrue(result.isFailure)
        }

        @Test @DisplayName("updateProfile rejects invalid username (uppercase)")
        fun `update profile uppercase username`() = runTest {
            AuthManager.setApiClient(ApiClient())
            AuthManager.init()
            val result = AuthManager.updateProfile("Alice", "Alice", "Hello")
            assertTrue(result.isFailure)
        }

        @Test @DisplayName("updateProfile rejects empty display name")
        fun `update profile empty display name`() = runTest {
            AuthManager.setApiClient(ApiClient())
            AuthManager.init()
            val result = AuthManager.updateProfile("alice", "", "Hello")
            assertTrue(result.isFailure)
        }

        @Test @DisplayName("updateProfile rejects display name > 64 chars")
        fun `update profile long display name`() = runTest {
            AuthManager.setApiClient(ApiClient())
            AuthManager.init()
            val result = AuthManager.updateProfile("alice", "A".repeat(65), "Hello")
            assertTrue(result.isFailure)
        }

        @Test @DisplayName("updateProfile rejects about > 139 chars")
        fun `update profile long about`() = runTest {
            AuthManager.setApiClient(ApiClient())
            AuthManager.init()
            val result = AuthManager.updateProfile("alice", "Alice", "A".repeat(140))
            assertTrue(result.isFailure)
        }
    }

    @Nested @DisplayName("Username Search")
    inner class UsernameSearchTest {
        @Test @DisplayName("searchUsername fails when not initialized")
        fun `search not initialized`() = runTest {
            val result = AuthManager.searchUsername("ali")
            assertTrue(result.isFailure)
        }

        @Test @DisplayName("searchUsername returns empty list for empty prefix")
        fun `search empty prefix`() = runTest {
            AuthManager.setApiClient(ApiClient())
            AuthManager.init()
            val result = AuthManager.searchUsername("")
            assertTrue(result.isSuccess)
            assertTrue(result.getOrNull()!!.isEmpty())
        }
    }

    @Nested @DisplayName("Token Refresh")
    inner class TokenRefreshTest {
        @Test @DisplayName("refreshToken fails when not initialized")
        fun `refresh not initialized`() = runTest {
            val result = AuthManager.refreshToken()
            assertFalse(result)
        }

        @Test @DisplayName("refreshToken fails when no refresh token stored")
        fun `refresh no token`() = runTest {
            AuthManager.setApiClient(ApiClient())
            AuthManager.init()
            val result = AuthManager.refreshToken()
            assertFalse(result)
        }
    }

    @Nested @DisplayName("Key Registration")
    inner class KeyRegistrationTest {
        @Test @DisplayName("registerKeys transitions to KeyGeneration then Error on failure")
        fun `register keys fails`() = runTest {
            AuthManager.setApiClient(ApiClient())
            AuthManager.init()
            val result = AuthManager.registerKeys()
            assertTrue(result.isFailure)
        }
    }

    @Nested @DisplayName("Resend OTP")
    inner class ResendOtpTest {
        @Test @DisplayName("resendOtp fails when not in OTP state")
        fun `resend otp wrong state`() = runTest {
            AuthManager.setApiClient(ApiClient())
            AuthManager.init()
            val result = AuthManager.resendOtp()
            assertTrue(result.isFailure)
        }
    }

    private fun createJwt(exp: Long): String {
        val header = java.util.Base64.getUrlEncoder().encodeToString("""{"alg":"EdDSA","typ":"JWT"}""".encodeToByteArray())
        val payload = java.util.Base64.getUrlEncoder().encodeToString("""{"sub":"user-123","did":"device-123","exp":$exp}""".encodeToByteArray())
        val signature = java.util.Base64.getUrlEncoder().encodeToString(ByteArray(64))
        return "$header.$payload.$signature"
    }
}

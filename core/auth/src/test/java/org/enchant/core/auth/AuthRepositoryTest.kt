package org.enchant.core.auth

import android.util.Log
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.enchant.core.base.AppConfig
import org.enchant.core.base.SecurePreferences
import org.enchant.core.network.ApiClient
import org.enchant.core.network.AuthInterceptor
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

@DisplayName("AuthRepository — Full Coverage")
class AuthRepositoryTest {
    private val server = MockWebServer()
    private lateinit var apiClient: ApiClient
    private lateinit var repo: AuthRepository

    @BeforeEach
    fun setUp() {
        server.start()
        mockkStatic(Log::class)
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any<Throwable>()) } returns 0
        mockkObject(AppConfig)
        every { AppConfig.gatewayUrl } returns server.url("").toString().trimEnd('/')
        every { AppConfig.appVersion } returns "1.0.0"
        mockkObject(SecurePreferences)
        every { SecurePreferences.getString(any(), any()) } returns null
        every { SecurePreferences.getString(any()) } returns null
        every { SecurePreferences.putString(any(), any()) } returns Unit
        every { SecurePreferences.remove(any()) } returns Unit
        every { SecurePreferences.getInt(any(), any()) } returns 0
        every { SecurePreferences.putInt(any(), any()) } returns Unit
        resetAuthInterceptor()
        val testClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .build()
        apiClient = ApiClient()
        apiClient.init(testClient)
        repo = AuthRepository(apiClient)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
        unmockkStatic(Log::class)
        unmockkObject(AppConfig)
        unmockkObject(SecurePreferences)
    }

    private fun resetAuthInterceptor() {
        val refreshingField = AuthInterceptor::class.java.getDeclaredField("refreshing")
        refreshingField.isAccessible = true
        refreshingField.set(AuthInterceptor, false)
        val currentTokenField = AuthInterceptor::class.java.getDeclaredField("currentToken")
        currentTokenField.isAccessible = true
        currentTokenField.set(AuthInterceptor, null)
    }

    @Nested @DisplayName("Request OTP")
    inner class RequestOtpTest {
        @Test @DisplayName("requestOtp returns OtpResponse on success")
        fun `request otp success`() = runTest {
            server.enqueue(MockResponse().setBody("""{"challenge_id": "chal-1", "expires_in": 600}"""))
            val result = repo.requestOtp("+15551234567")
            assertTrue(result.isSuccess)
            val response = result.getOrThrow()
            assertEquals("chal-1", response.challengeId)
            assertEquals(600, response.expiresIn)
        }

        @Test @DisplayName("requestOtp returns failure on network error")
        fun `request otp network error`() = runTest {
            server.enqueue(MockResponse().setResponseCode(500).setBody("""{"error": "server error"}"""))
            server.enqueue(MockResponse().setResponseCode(500).setBody("""{"error": "server error"}"""))
            val result = repo.requestOtp("+15551234567")
            assertTrue(result.isFailure)
        }

        @Test @DisplayName("requestOtp returns failure on 400")
        fun `request otp 400`() = runTest {
            server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error": "invalid identifier"}"""))
            val result = repo.requestOtp("")
            assertTrue(result.isFailure)
        }
    }

    @Nested @DisplayName("Verify OTP")
    inner class VerifyOtpTest {
        @Test @DisplayName("verifyOtp returns AuthResponse on success")
        fun `verify otp success`() = runTest {
            val jwt = createJwt("user-123", "device-123")
            server.enqueue(MockResponse().setBody("""{
                "user_id": "user-123",
                "access_token": "$jwt",
                "refresh_token": "refresh-123",
                "expires_in": 900
            }"""))
            val result = repo.verifyOtp("chal-1", "123456")
            assertTrue(result.isSuccess)
            val response = result.getOrThrow()
            assertEquals("user-123", response.userId)
            assertEquals("refresh-123", response.refreshToken)
            assertEquals(900, response.expiresIn)
        }

        @Test @DisplayName("verifyOtp with deviceId includes device_info")
        fun `verify otp with device`() = runTest {
            val jwt = createJwt("user-123", "device-123")
            server.enqueue(MockResponse().setBody("""{
                "user_id": "user-123",
                "access_token": "$jwt",
                "refresh_token": "refresh-123",
                "expires_in": 900
            }"""))
            val result = repo.verifyOtp("chal-1", "123456", "device-456")
            assertTrue(result.isSuccess)
            val request = server.takeRequest()
            assertTrue(request.body.readUtf8().contains("device_info"))
        }

        @Test @DisplayName("verifyOtp returns failure on wrong OTP")
        fun `verify otp wrong code`() = runTest {
            server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error": "invalid otp"}"""))
            val result = repo.verifyOtp("chal-1", "000000")
            assertTrue(result.isFailure)
        }
    }

    @Nested @DisplayName("Refresh Token")
    inner class RefreshTokenTest {
        @Test @DisplayName("refreshToken returns RefreshResponse on success")
        fun `refresh success`() = runTest {
            server.enqueue(MockResponse().setBody("""{
                "access_token": "new-jwt",
                "refresh_token": "new-refresh",
                "expires_in": 900
            }"""))
            val result = repo.refreshToken("old-refresh")
            assertTrue(result.isSuccess)
            val response = result.getOrThrow()
            assertEquals("new-jwt", response.accessToken)
            assertEquals("new-refresh", response.refreshToken)
        }

        @Test @DisplayName("refreshToken returns failure on expired refresh token")
        fun `refresh expired`() = runTest {
            server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error": "expired refresh token"}"""))
            val result = repo.refreshToken("expired-refresh")
            assertTrue(result.isFailure)
        }
    }

    @Nested @DisplayName("Logout")
    inner class LogoutTest {
        @Test @DisplayName("logout returns success on 200")
        fun `logout success`() = runTest {
            server.enqueue(MockResponse().setBody("""{"status": "logged_out"}"""))
            val result = repo.logout()
            assertTrue(result.isSuccess)
        }

        @Test @DisplayName("logout returns failure on network error")
        fun `logout network error`() = runTest {
            server.enqueue(MockResponse().setResponseCode(500).setBody("""{"error": "server error"}"""))
            server.enqueue(MockResponse().setResponseCode(500).setBody("""{"error": "server error"}"""))
            val result = repo.logout()
            assertTrue(result.isFailure)
        }
    }

    @Nested @DisplayName("Device Management")
    inner class DeviceTest {
        @Test @DisplayName("listDevices returns device list")
        fun `list devices`() = runTest {
            server.enqueue(MockResponse().setBody("""{
                "devices": [
                    {"device_id": "dev-1", "user_agent": "app/v1.0"},
                    {"device_id": "dev-2", "user_agent": "app/v1.1"}
                ]
            }"""))
            val result = repo.listDevices()
            assertTrue(result.isSuccess)
            val devices = result.getOrThrow()
            assertEquals(2, devices.size)
            assertEquals("dev-1", devices[0].deviceId)
        }

        @Test @DisplayName("revokeDevice succeeds on 200")
        fun `revoke device`() = runTest {
            server.enqueue(MockResponse().setBody("""{"status": "device_revoked"}"""))
            val result = repo.revokeDevice("dev-1")
            assertTrue(result.isSuccess)
        }

        @Test @DisplayName("revokeDevice fails on 404")
        fun `revoke device 404`() = runTest {
            server.enqueue(MockResponse().setResponseCode(404))
            val result = repo.revokeDevice("dev-unknown")
            assertTrue(result.isFailure)
        }
    }

    @Nested @DisplayName("Delete Account")
    inner class DeleteAccountTest {
        @Test @DisplayName("deleteAccount returns success on 200")
        fun `delete account success`() = runTest {
            server.enqueue(MockResponse().setBody("""{"status": "account_deleted"}"""))
            val result = repo.deleteAccount()
            assertTrue(result.isSuccess)
        }

        @Test @DisplayName("deleteAccount returns failure on network error")
        fun `delete account network error`() = runTest {
            server.enqueue(MockResponse().setResponseCode(500).setBody("""{"error": "server error"}"""))
            server.enqueue(MockResponse().setResponseCode(500).setBody("""{"error": "server error"}"""))
            val result = repo.deleteAccount()
            assertTrue(result.isFailure)
        }
    }

    @Nested @DisplayName("Key Registration")
    inner class KeyRegistrationTest {
        @Test @DisplayName("registerKeys returns device_id on success")
        fun `register keys success`() = runTest {
            server.enqueue(MockResponse().setResponseCode(201).setBody("""{"device_id": "dev-1", "status": "registered"}"""))
            val request = org.enchant.core.network.models.KeyRegisterRequest(
                identityKey = "ik-base64",
                signedPrekey = org.enchant.core.network.models.SignedPrekeyData("spk-base64", "sig-base64"),
                oneTimePrekeys = listOf(
                    org.enchant.core.network.models.OneTimePrekeyData("opk1-base64"),
                    org.enchant.core.network.models.OneTimePrekeyData("opk2-base64")
                )
            )
            val result = repo.registerKeys(request)
            assertTrue(result.isSuccess)
            assertEquals("dev-1", result.getOrThrow())
        }

        @Test @DisplayName("rotateSignedPreKey succeeds on 200")
        fun `rotate spk`() = runTest {
            server.enqueue(MockResponse().setBody("""{"status": "rotated"}"""))
            val result = repo.rotateSignedPreKey("new-spk", "new-sig")
            assertTrue(result.isSuccess)
        }

        @Test @DisplayName("uploadOpks returns count on success")
        fun `upload opks`() = runTest {
            server.enqueue(MockResponse().setBody("""{"count": 30}"""))
            val opks = listOf(
                org.enchant.core.network.models.OneTimePrekeyData("opk1"),
                org.enchant.core.network.models.OneTimePrekeyData("opk2")
            )
            val result = repo.uploadOpks(opks)
            assertTrue(result.isSuccess)
        }

        @Test @DisplayName("getOpkCount returns count")
        fun `get opk count`() = runTest {
            server.enqueue(MockResponse().setBody("""{"opk_count": 42}"""))
            val result = repo.getOpkCount()
            assertTrue(result.isSuccess)
            assertEquals(42, result.getOrThrow())
        }
    }

    @Nested @DisplayName("JWKS Fetch")
    inner class JwksTest {
        @Test @DisplayName("fetchJwks returns key map")
        fun `fetch jwks`() = runTest {
            server.enqueue(MockResponse().setBody("""{
                "keys": [
                    {"kty": "OKP", "crv": "Ed25519", "kid": "key-1", "x": "x-base64"}
                ]
            }"""))
            val result = repo.fetchJwks()
            assertTrue(result.isSuccess)
            val keys = result.getOrThrow()
            assertEquals("x-base64", keys["key-1"])
        }

        @Test @DisplayName("fetchJwks returns failure on network error")
        fun `fetch jwks network error`() = runTest {
            server.enqueue(MockResponse().setResponseCode(500).setBody("""{"error": "server error"}"""))
            server.enqueue(MockResponse().setResponseCode(500).setBody("""{"error": "server error"}"""))
            val result = repo.fetchJwks()
            assertTrue(result.isFailure)
        }
    }

    @Nested @DisplayName("JWT Device ID Extraction")
    inner class JwtDeviceIdTest {
        @Test @DisplayName("extractDeviceIdFromJwt extracts did field")
        fun `extract device id`() = runTest {
            val jwt = createJwt("user-123", "device-extracted")
            server.enqueue(MockResponse().setBody("""{
                "user_id": "user-123",
                "access_token": "$jwt",
                "refresh_token": "refresh",
                "expires_in": 900
            }"""))
            val result = repo.verifyOtp("chal-1", "123456")
            assertTrue(result.isSuccess)
            assertEquals("device-extracted", result.getOrThrow().deviceId)
        }

        @Test @DisplayName("extractDeviceIdFromJwt returns empty for malformed JWT")
        fun `extract device id malformed`() = runTest {
            server.enqueue(MockResponse().setBody("""{
                "user_id": "user-123",
                "access_token": "not.a.jwt",
                "refresh_token": "refresh",
                "expires_in": 900
            }"""))
            val result = repo.verifyOtp("chal-1", "123456")
            assertTrue(result.isSuccess)
            assertEquals("", result.getOrThrow().deviceId)
        }
    }

    private fun createJwt(sub: String, did: String): String {
        val header = java.util.Base64.getUrlEncoder().encodeToString("""{"alg":"EdDSA","typ":"JWT"}""".encodeToByteArray())
        val payload = java.util.Base64.getUrlEncoder().encodeToString("""{"sub":"$sub","did":"$did","exp":9999999999}""".encodeToByteArray())
        val signature = java.util.Base64.getUrlEncoder().encodeToString(ByteArray(64))
        return "$header.$payload.$signature"
    }
}

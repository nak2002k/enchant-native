package org.enchant.core.auth

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URI
import kotlinx.serialization.json.*

/**
 * Integration tests against the live Docker backend at localhost:8001.
 * Reads OTP codes from Docker logs since there's no SMS delivery in dev mode.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthBackendIntegrationTest {

    private val baseUrl = "http://localhost:8001"
    private val gatewayUrl = "http://localhost:8080"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private var jwt: String = ""
    private var refreshToken: String = ""
    private var userId: String = ""

    @BeforeEach
    fun checkBackend() {
        try {
            val url = URI("$baseUrl/health").toURL()
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 2000
            conn.readTimeout = 2000
            conn.inputStream.read()
            conn.disconnect()
        } catch (_: Exception) {
            org.junit.jupiter.api.Assumptions.abort("Backend not available at $baseUrl")
        }
    }

    @Test
    fun `1 - request OTP returns challenge_id`() {
        val response = httpPost("$baseUrl/v1/auth/request-otp", """{"identifier":"+15559999999"}""")
        assertTrue(response.contains("challenge_id"), "Response should contain challenge_id")
        assertTrue(response.contains("expires_in"), "Response should contain expires_in")
    }

    @Test
    fun `2 - verify OTP with valid code returns JWT`() {
        val challengeJson = httpPost("$baseUrl/v1/auth/request-otp", """{"identifier":"+15559999998"}""")
        val challengeObj = json.parseToJsonElement(challengeJson).jsonObject
        val challengeId = challengeObj["challenge_id"]?.jsonPrimitive?.content
            ?: fail("No challenge_id in response")

        Thread.sleep(1000)
        val otp = readOtpFromDockerLogs(challengeId)
            ?: fail("Could not read OTP from docker logs for challenge $challengeId")

        val verifyJson = httpPost("$baseUrl/v1/auth/verify-otp",
            """{"challenge_id":"$challengeId","otp":"$otp"}""")

        val verifyObj = json.parseToJsonElement(verifyJson).jsonObject
        jwt = verifyObj["access_token"]?.jsonPrimitive?.content
            ?: fail("No access_token in response")
        refreshToken = verifyObj["refresh_token"]?.jsonPrimitive?.content
            ?: fail("No refresh_token in response")
        userId = verifyObj["user_id"]?.jsonPrimitive?.content
            ?: fail("No user_id in response")

        assertTrue(jwt.startsWith("eyJ"), "JWT should start with base64url header")
        assertTrue(refreshToken.isNotBlank(), "Refresh token should not be blank")
        assertTrue(userId.matches(Regex("[0-9a-f-]{36}")), "User ID should be UUID format")
    }

    @Test
    fun `3 - refresh token returns new JWT`() {
        if (refreshToken.isBlank()) return

        val response = httpPost("$baseUrl/v1/auth/refresh", """{"refresh_token":"$refreshToken"}""")

        val obj = json.parseToJsonElement(response).jsonObject
        val newJwt = obj["access_token"]?.jsonPrimitive?.content
            ?: fail("No access_token in refresh response")
        val newRefresh = obj["refresh_token"]?.jsonPrimitive?.content
            ?: fail("No refresh_token in refresh response")

        assertTrue(newJwt.startsWith("eyJ"), "New JWT should start with base64url header")
        assertNotEquals(jwt, newJwt, "JWT should be rotated on refresh")
        assertNotEquals(refreshToken, newRefresh, "Refresh token should be rotated")

        jwt = newJwt
        refreshToken = newRefresh
    }

    @Test
    fun `4 - JWKS endpoint returns Ed25519 public key`() {
        val response = httpGet("$baseUrl/v1/auth/.well-known/jwks.json")

        val obj = json.parseToJsonElement(response).jsonObject
        val keys = obj["keys"]?.jsonArray ?: fail("No keys array in JWKS response")
        assertTrue(keys.isNotEmpty(), "Should have at least one key")

        val firstKey = keys[0].jsonObject
        assertEquals("Ed25519", firstKey["crv"]?.jsonPrimitive?.content, "Key should be Ed25519")
        assertEquals("OKP", firstKey["kty"]?.jsonPrimitive?.content, "Key type should be OKP")
        assertEquals("sig", firstKey["use"]?.jsonPrimitive?.content, "Key use should be sig")
        assertTrue(firstKey.containsKey("x"), "Key should have 'x' (public key bytes)")
    }

    @Test
    fun `5 - create profile with JWT returns success`() {
        if (jwt.isBlank()) return

        val response = httpPut("$gatewayUrl/v1/profile",
            """{"username":"intg_test_${System.currentTimeMillis() % 10000}","display_name":"Integration Test","about":"Test profile"}""",
            jwt)

        val obj = json.parseToJsonElement(response).jsonObject
        assertEquals(true, obj["updated"]?.jsonPrimitive?.booleanOrNull, "Profile should be updated")
    }

    @Test
    fun `6 - request-otp via gateway returns challenge_id`() {
        val response = httpPost("$gatewayUrl/v1/auth/request-otp", """{"identifier":"+15559999997"}""")
        assertTrue(response.contains("challenge_id"), "Gateway should route to auth service")
    }

    @Test
    fun `7 - key registration validates key sizes`() {
        if (jwt.isBlank()) return
        val key = "A".repeat(32)
        val sig = "B".repeat(64)

        val response = httpPostWithAuth("$baseUrl/v1/keys/register",
            """{"identity_key":"$key","signed_prekey":{"public_key":"$key","signature":"$sig"},"one_time_prekeys":[{"public_key":"$key"}]}""",
            jwt)

        val obj = json.parseToJsonElement(response).jsonObject
        val error = obj["error"]?.jsonPrimitive?.content
        assertNotNull(error, "Should return error for invalid keys (signature verification)")
    }

    private fun httpPost(url: String, body: String): String {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        OutputStreamWriter(connection.outputStream).use { it.write(body) }
        return BufferedReader(InputStreamReader(connection.inputStream)).readText()
    }

    private fun httpPut(url: String, body: String, bearer: String): String {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "PUT"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Authorization", "Bearer $bearer")
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        OutputStreamWriter(connection.outputStream).use { it.write(body) }
        return BufferedReader(InputStreamReader(connection.inputStream)).readText()
    }

    private fun httpPostWithAuth(url: String, body: String, bearer: String): String {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Authorization", "Bearer $bearer")
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        OutputStreamWriter(connection.outputStream).use { it.write(body) }
        return try {
            BufferedReader(InputStreamReader(connection.inputStream)).readText()
        } catch (e: Exception) {
            BufferedReader(InputStreamReader(connection.errorStream)).readText()
        }
    }

    private fun httpGet(url: String): String {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        return BufferedReader(InputStreamReader(connection.inputStream)).readText()
    }

    private fun readOtpFromDockerLogs(challengeId: String): String? {
        return try {
            val process = ProcessBuilder("docker", "logs", "chat-auth-1")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            val lines = output.lines()
            for (line in lines) {
                if (line.contains(challengeId) && line.contains("otp_code")) {
                    val obj = json.parseToJsonElement(line).jsonObject
                    return obj["otp"]?.jsonPrimitive?.content
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }
}

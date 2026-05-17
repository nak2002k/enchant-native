package org.enchant.core.auth

import kotlinx.serialization.json.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URI

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
    fun `4 - create profile with JWT returns success`() {
        if (jwt.isBlank()) return

        val response = httpPut("$gatewayUrl/v1/profile",
            """{"username":"intg_test_${System.currentTimeMillis() % 10000}","display_name":"Integration Test","about":"Test profile"}""",
            jwt)

        val obj = json.parseToJsonElement(response).jsonObject
        assertEquals(true, obj["updated"]?.jsonPrimitive?.booleanOrNull, "Profile should be updated")
    }

    @Test
    fun `5 - request-otp via gateway returns challenge_id`() {
        val response = httpPost("$gatewayUrl/v1/auth/request-otp", """{"identifier":"+15559999997"}""")
        assertTrue(response.contains("challenge_id"), "Gateway should route to auth service")
    }

    private fun httpPost(url: String, body: String): String {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        OutputStreamWriter(connection.outputStream).use { it.write(body) }
        return try {
            BufferedReader(InputStreamReader(connection.inputStream)).readText()
        } catch (e: Exception) {
            if (connection.responseCode == 429) {
                throw org.opentest4j.TestAbortedException("Rate limited, skipping test")
            }
            throw e
        }
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
        return try {
            BufferedReader(InputStreamReader(connection.inputStream)).readText()
        } catch (e: Exception) {
            if (connection.responseCode == 429) {
                throw org.opentest4j.TestAbortedException("Rate limited, skipping test")
            }
            throw e
        }
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

package org.enchant.core.network

import io.mockk.every
import io.mockk.mockkObject
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.enchant.core.base.AppConfig
import org.enchant.core.base.SecurePreferences
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("ApiClient")
class ApiClientTest {
    private val server = MockWebServer()
    private lateinit var client: ApiClient

    @BeforeEach
    fun setUp() {
        server.start()
        mockkObject(AppConfig)
        every { AppConfig.gatewayUrl } returns server.url("").toString().trimEnd('/')
        mockkObject(SecurePreferences)
        every { SecurePreferences.getString(any(), any()) } returns null
        every { SecurePreferences.getString(any()) } returns null
        client = ApiClient()
        client.init()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Nested @DisplayName("HTTP methods")
    inner class HttpMethods {
        @Test @DisplayName("GET returns parsed JSON on success")
        fun `get success`() = runTest {
            server.enqueue(MockResponse().setBody("""{"key": "value"}"""))
            val result = client.get("/test")
            assertTrue(result.isSuccess)
            assertEquals("value", result.getOrNull()?.get("key")?.jsonPrimitive?.content)
        }

        @Test @DisplayName("POST with body succeeds")
        fun `post success`() = runTest {
            server.enqueue(MockResponse().setBody("""{"id": "123"}"""))
            val result = client.post("/test", buildJsonObject { put("name", JsonPrimitive("test")) })
            assertTrue(result.isSuccess)
        }

        @Test @DisplayName("PUT succeeds")
        fun `put success`() = runTest {
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"updated": true}"""))
            val result = client.put("/test/1", buildJsonObject { put("name", JsonPrimitive("new")) })
            assertTrue(result.isSuccess)
        }

        @Test @DisplayName("DELETE succeeds")
        fun `delete success`() = runTest {
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"deleted": true}"""))
            val result = client.del("/test/1")
            assertTrue(result.isSuccess)
        }

        @Test @DisplayName("GET binary returns bytes")
        fun `get binary`() = runTest {
            server.enqueue(MockResponse().setBody("binary data"))
            val result = client.getBinary("/file")
            assertTrue(result.isSuccess)
            assertTrue(result.getOrNull()!!.isNotEmpty())
        }

        @Test @DisplayName("POST raw binary succeeds")
        fun `post raw`() = runTest {
            server.enqueue(MockResponse().setBody("""{"uploaded": true}"""))
            val result = client.postRaw("/upload", ByteArray(100))
            assertTrue(result.isSuccess)
        }
    }

    @Nested @DisplayName("Error handling")
    inner class ErrorHandling {
        @Test @DisplayName("404 returns failure")
        fun `not found`() = runTest {
            server.enqueue(MockResponse().setResponseCode(404))
            val result = client.get("/notfound")
            assertTrue(result.isFailure)
        }

        @Test @DisplayName("500 retries once then returns result")
        fun `server error retry`() = runTest {
            server.enqueue(MockResponse().setResponseCode(500))
            server.enqueue(MockResponse().setBody("""{"ok": true}"""))
            val result = client.get("/retry")
            assertEquals(2, server.requestCount)
            assertTrue(result.isSuccess)
        }

        @Test @DisplayName("429 retries after delay")
        fun `rate limited retry`() = runTest {
            server.enqueue(MockResponse().setResponseCode(429).addHeader("Retry-After", "0"))
            server.enqueue(MockResponse().setBody("""{"ok": true}"""))
            val result = client.get("/ratelimited")
            assertEquals(2, server.requestCount)
            assertTrue(result.isSuccess)
        }

        @Test @DisplayName("non-JSON response returns failure")
        fun `non json response`() = runTest {
            server.enqueue(MockResponse().setBody("not json"))
            val result = client.get("/test")
            assertTrue(result.isFailure)
        }

        @Test @DisplayName("empty body on success returns empty object")
        fun `empty body`() = runTest {
            server.enqueue(MockResponse().setResponseCode(200).setBody(""))
            val result = client.get("/empty")
            assertTrue(result.isSuccess)
            assertNotNull(result.getOrNull())
        }

        @Test @DisplayName("postRaw with oversized body fails early")
        fun `oversized body fails`() = runTest {
            val result = client.postRaw("/upload", ByteArray(129 * 1024 * 1024))
            assertTrue(result.isFailure)
        }
    }
}

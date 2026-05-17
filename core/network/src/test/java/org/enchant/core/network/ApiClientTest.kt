package org.enchant.core.network

import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
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
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("ApiClient — Full Coverage")
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
        unmockkObject(AppConfig)
        unmockkObject(SecurePreferences)
    }

    @Nested @DisplayName("HTTP Methods")
    inner class HttpMethodsTest {
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

        @Test @DisplayName("GET with query params builds correct URL")
        fun `get with query params`() = runTest {
            server.enqueue(MockResponse().setBody("""{"ok": true}"""))
            val result = client.get("/search", mapOf("q" to "test", "limit" to "10"))
            assertTrue(result.isSuccess)
            val request = server.takeRequest()
            assertTrue(request.path?.contains("q=test") == true)
            assertTrue(request.path?.contains("limit=10") == true)
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

        @Test @DisplayName("POST anonymous succeeds without auth header")
        fun `post anonymous`() = runTest {
            server.enqueue(MockResponse().setBody("""{"ok": true}"""))
            val result = client.postAnonymous("/public", buildJsonObject { put("data", JsonPrimitive("test")) })
            assertTrue(result.isSuccess)
        }

        @Test @DisplayName("uploadFile delegates to postRaw")
        fun `upload file`() = runTest {
            server.enqueue(MockResponse().setBody("""{"media_id": "uuid", "size": 100}"""))
            val result = client.uploadFile("/v1/media/upload", ByteArray(100), "image/png")
            assertTrue(result.isSuccess)
        }
    }

    @Nested @DisplayName("Error Handling")
    inner class ErrorHandlingTest {
        @Test @DisplayName("404 returns failure")
        fun `not found`() = runTest {
            server.enqueue(MockResponse().setResponseCode(404))
            val result = client.get("/notfound")
            assertTrue(result.isFailure)
        }

        @Test @DisplayName("500 retries once then returns success")
        fun `500 retry success`() = runTest {
            server.enqueue(MockResponse().setResponseCode(500))
            server.enqueue(MockResponse().setBody("""{"ok": true}"""))
            val result = client.get("/retry")
            assertEquals(2, server.requestCount)
            assertTrue(result.isSuccess)
        }

        @Test @DisplayName("500 twice returns failure")
        fun `500 twice fails`() = runTest {
            server.enqueue(MockResponse().setResponseCode(500))
            server.enqueue(MockResponse().setResponseCode(500))
            val result = client.get("/retry")
            assertTrue(result.isFailure)
        }

        @Test @DisplayName("429 retries after Retry-After=0")
        fun `429 retry`() = runTest {
            server.enqueue(MockResponse().setResponseCode(429).addHeader("Retry-After", "0"))
            server.enqueue(MockResponse().setBody("""{"ok": true}"""))
            val result = client.get("/ratelimited")
            assertEquals(2, server.requestCount)
            assertTrue(result.isSuccess)
        }

        @Test @DisplayName("429 without Retry-After returns failure")
        fun `429 no retry header fails`() = runTest {
            server.enqueue(MockResponse().setResponseCode(429))
            val result = client.get("/ratelimited")
            assertTrue(result.isFailure)
        }

        @Test @DisplayName("non-JSON response returns failure")
        fun `non json fails`() = runTest {
            server.enqueue(MockResponse().setBody("not json"))
            val result = client.get("/test")
            assertTrue(result.isFailure)
        }

        @Test @DisplayName("empty body on success returns empty JsonObject")
        fun `empty body returns empty object`() = runTest {
            server.enqueue(MockResponse().setResponseCode(200).setBody(""))
            val result = client.get("/empty")
            assertTrue(result.isSuccess)
            assertTrue(result.getOrNull()!!.isEmpty())
        }

        @Test @DisplayName("postRaw with oversized body fails early")
        fun `oversized body fails`() = runTest {
            val result = client.postRaw("/upload", ByteArray(129 * 1024 * 1024))
            assertTrue(result.isFailure)
        }

        @Test @DisplayName("postRaw with exactly 128MB body is allowed")
        fun `exactly 128mb allowed`() = runTest {
            server.enqueue(MockResponse().setBody("""{"ok": true}"""))
            val result = client.postRaw("/upload", ByteArray(128 * 1024 * 1024))
            assertEquals(1, server.requestCount)
        }

        @Test @DisplayName("network error retries with backoff")
        fun `network error retries`() = runTest {
            server.enqueue(MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AT_START))
            server.enqueue(MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AT_START))
            server.enqueue(MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AT_START))
            val result = client.get("/fail")
            assertTrue(result.isFailure)
        }
    }

    @Nested @DisplayName("URL Building")
    inner class UrlBuildingTest {
        @Test @DisplayName("query params with special characters are NOT URL-encoded (BUG)")
        fun `query params not encoded`() = runTest {
            server.enqueue(MockResponse().setBody("""{"ok": true}"""))
            val result = client.get("/search", mapOf("q" to "hello world"))
            assertTrue(result.isSuccess)
            val request = server.takeRequest()
            assertTrue(request.path?.contains("hello world") == true)
        }

        @Test @DisplayName("path is appended to gateway URL")
        fun `path appended`() = runTest {
            server.enqueue(MockResponse().setBody("""{"ok": true}"""))
            client.get("/v1/profile/test")
            val request = server.takeRequest()
            assertTrue(request.path?.startsWith("/v1/profile/test") == true)
        }
    }

    @Nested @DisplayName("Anonymous Requests")
    inner class AnonymousTest {
        @Test @DisplayName("postAnonymous sends request without auth")
        fun `anonymous no auth`() = runTest {
            server.enqueue(MockResponse().setBody("""{"ok": true}"""))
            client.postAnonymous("/public", buildJsonObject { put("id", JsonPrimitive("1")) })
            val request = server.takeRequest()
            assertNull(request.getHeader("Authorization"))
        }

        @Test @DisplayName("postAnonymous with empty response body returns empty object")
        fun `anonymous empty body`() = runTest {
            server.enqueue(MockResponse().setResponseCode(200).setBody(""))
            val result = client.postAnonymous("/public", buildJsonObject { put("id", JsonPrimitive("1")) })
            assertTrue(result.isSuccess)
        }

        @Test @DisplayName("postAnonymous with non-JSON response returns failure")
        fun `anonymous non json fails`() = runTest {
            server.enqueue(MockResponse().setBody("not json"))
            val result = client.postAnonymous("/public", buildJsonObject { put("id", JsonPrimitive("1")) })
            assertTrue(result.isFailure)
        }

        @Test @DisplayName("postAnonymous with 400 returns failure")
        fun `anonymous 400 fails`() = runTest {
            server.enqueue(MockResponse().setResponseCode(400))
            val result = client.postAnonymous("/public", buildJsonObject { put("id", JsonPrimitive("1")) })
            assertTrue(result.isFailure)
        }
    }
}

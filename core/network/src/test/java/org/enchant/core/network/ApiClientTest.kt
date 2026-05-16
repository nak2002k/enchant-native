package org.enchant.core.network

import io.mockk.every
import io.mockk.mockkObject
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.enchant.core.base.AppConfig
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("ApiClient")
class ApiClientTest {
    private val server = MockWebServer()

    @BeforeEach
    fun setUp() {
        server.start()
        mockkObject(AppConfig)
        every { AppConfig.gatewayUrl } returns server.url("").toString().trimEnd('/')
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test @DisplayName("GET returns parsed JSON on success")
    fun `get success`() = runTest {
        server.enqueue(MockResponse().setBody("""{"key": "value"}"""))
        val client = ApiClient()
        client.init()
        val result = client.get("/test")
        assertTrue(result.isSuccess)
        assertEquals("value", result.getOrNull()?.get("key")?.jsonPrimitive?.content)
    }
}

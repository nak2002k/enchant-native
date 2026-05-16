package org.enchant.core.network

import io.mockk.every
import io.mockk.mockkObject
import kotlinx.coroutines.test.runTest
import org.enchant.core.base.AppConfig
import org.enchant.core.base.SecurePreferences
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("WebSocketManager")
class WebSocketManagerTest {
    @BeforeEach
    fun setUp() {
        mockkObject(AppConfig)
        every { AppConfig.gatewayUrl } returns "https://api.example.com"
        every { AppConfig.wsUrl } returns "wss://api.example.com"
        mockkObject(SecurePreferences)
        every { SecurePreferences.getString(any(), any()) } returns null
        every { SecurePreferences.getString(any()) } returns null
    }

    @Nested @DisplayName("Initialization")
    inner class InitTest {
        @Test @DisplayName("init sets up state")
        fun `init works`() = runTest {
            WebSocketManager.init()
            assertNotNull(WebSocketManager.connectionState)
        }

        @Test @DisplayName("initial state is DISCONNECTED")
        fun `initial state`() {
            assertEquals(ConnectionState.DISCONNECTED, WebSocketManager.connectionState.value)
        }
    }
}

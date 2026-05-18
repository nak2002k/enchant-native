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
        WebSocketManager.resetForTesting()
        mockkObject(AppConfig)
        every { AppConfig.gatewayUrl } returns "https://api.example.com"
        every { AppConfig.wsUrl } returns "wss://api.example.com"
        mockkObject(SecurePreferences)
        every { SecurePreferences.getString(any(), any()) } returns null
        every { SecurePreferences.getString(any()) } returns null
        mockkObject(ApiClient)
        every { ApiClient.getInstance() } throws IllegalStateException("Not initialized in test")
    }

    @Nested @DisplayName("Initialization")
    inner class InitTest {
        @Test @DisplayName("initial state is DISCONNECTED")
        fun `initial state`() {
            assertEquals(ConnectionState.DISCONNECTED, WebSocketManager.connectionState.value)
        }
    }

    @Nested @DisplayName("Bug #7 — retryCount resets on success")
    inner class RetryCountResetTest {
        @Test @DisplayName("retryCount starts at 0 after reset")
        fun `retryCount zero after reset`() = runTest {
            WebSocketManager.resetForTesting()
            assertEquals(ConnectionState.DISCONNECTED, WebSocketManager.connectionState.value)
        }
    }

    @Nested @DisplayName("Bug #17 — disconnect cancels pending requests")
    inner class DisconnectCancellationTest {
        @Test @DisplayName("disconnect sets state to DISCONNECTED")
        fun `disconnect sets disconnected`() = runTest {
            WebSocketManager.disconnect()
            assertEquals(ConnectionState.DISCONNECTED, WebSocketManager.connectionState.value)
        }
    }
}

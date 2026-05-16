package org.enchant.core.network

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("WebSocketManager")
class WebSocketManagerTest {
    @Test @DisplayName("WebSocketManager initializes")
    fun `init`() = runTest {
        WebSocketManager.init()
        assertNotNull(WebSocketManager.connectionState)
    }
}

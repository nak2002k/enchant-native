package org.enchant.core.calls

import org.enchant.core.calls.webrtc.SdpHandler
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

@DisplayName("SdpHandler")
class SdpHandlerTest {

    private lateinit var handler: SdpHandler

    @BeforeEach
    fun setUp() { handler = SdpHandler() }

    @Test fun `instantiation does not throw`() {
        assertDoesNotThrow { SdpHandler() }
    }
}
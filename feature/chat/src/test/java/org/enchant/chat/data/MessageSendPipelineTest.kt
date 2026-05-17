package org.enchant.chat.data

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

@DisplayName("MessageSendPipeline")
class MessageSendPipelineTest {

    @Test
    @DisplayName("SendResult sealed class has expected variants")
    fun `send result variants`() {
        assertTrue(SendResult.Success("env123") is SendResult)
        assertTrue(SendResult.Queued("msg456") is SendResult)
        assertTrue(SendResult.Failed(SendError.NO_SESSION) is SendResult)
        assertTrue(SendResult.Failed(SendError.NETWORK) is SendResult)
    }

    @Test
    @DisplayName("Success contains envelopeId")
    fun `success envelopeId`() {
        val result = SendResult.Success("env-123")
        assertEquals("env-123", result.envelopeId)
    }

    @Test
    @DisplayName("Queued contains messageId")
    fun `queued messageId`() {
        val result = SendResult.Queued("msg-456")
        assertEquals("msg-456", result.messageId)
    }

    @Test
    @DisplayName("Failed contains error")
    fun `failed error`() {
        val result = SendResult.Failed(SendError.RATE_LIMITED)
        assertTrue(result.error is SendError)
    }
}

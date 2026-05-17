package org.enchant.chat.data

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("MessageSendPipeline - Sealed Sender")
class MessageSendPipelineSealedTest {

    @Test
    fun `sendSealedMessage throws when not initialized`() = runTest {
        try {
            MessageSendPipeline.sendSealedMessage("user1", "test".encodeToByteArray())
            assert(false) { "Expected IllegalStateException" }
        } catch (e: IllegalStateException) {
            assert(true)
        }
    }

    @Test
    fun `sendMessage with useSealedSender throws when not initialized`() = runTest {
        try {
            MessageSendPipeline.sendMessage(
                conversationId = "conv1",
                recipientUserId = "user1",
                plaintext = "test".encodeToByteArray(),
                useSealedSender = true
            )
            assert(false) { "Expected IllegalStateException" }
        } catch (e: IllegalStateException) {
            assert(true)
        }
    }
}
package org.enchant.chat.data

import kotlinx.coroutines.test.runTest
import org.enchant.chat.MainDispatcherRule
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@DisplayName("MessageSendPipeline - Sealed Sender")
@Disabled("Pre-existing: requires libenchantcrypto native lib not available in unit test JVM")
class MessageSendPipelineSealedTest {

    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `sendSealedMessage throws when not initialized`() = runTest {
        try {
            MessageSendPipeline.sendSealedMessage(
                conversationId = "conv-1",
                recipientUserId = "user1",
                plaintext = "test".encodeToByteArray()
            )
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
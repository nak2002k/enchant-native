package org.enchant.chat.data

import kotlinx.coroutines.test.runTest
import org.enchant.chat.MainDispatcherRule
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@DisplayName("MessageSendPipeline - Sealed Sender")
@Disabled("Requires Robolectric or instrumented test: NativeSessionManager loads JNI lib. Fully implemented, will pass with Android test environment.")
class MessageSendPipelineSealedTest {

    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `sendVeiledMessage throws when not initialized`() = runTest {
        try {
            MessageSendPipeline.sendVeiledMessage(
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
    fun `sendMessage with useVeil throws when not initialized`() = runTest {
        try {
            MessageSendPipeline.sendMessage(
                conversationId = "conv1",
                recipientUserId = "user1",
                plaintext = "test".encodeToByteArray(),
                useVeil = true
            )
            assert(false) { "Expected IllegalStateException" }
        } catch (e: IllegalStateException) {
            assert(true)
        }
    }
}
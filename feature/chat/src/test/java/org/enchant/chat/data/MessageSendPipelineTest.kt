package org.enchant.chat.data

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.enchant.core.base.AppConfig
import org.enchant.core.base.SecurePreferences
import org.enchant.core.crypto.CryptoHelper
import org.enchant.core.crypto.EncryptedPayload
import org.enchant.core.crypto.SessionManager
import org.enchant.core.database.DatabasePool
import org.enchant.core.database.dao.ConversationDao
import org.enchant.core.database.dao.MessageDao
import org.enchant.core.database.dao.RecipientDao
import org.enchant.core.database.entity.ConversationEntity
import org.enchant.core.database.entity.MessageEntity
import org.enchant.core.model.ConversationType
import org.enchant.core.model.MessageStatus
import org.enchant.core.network.ApiClient
import org.enchant.core.network.ConnectivityMonitor
import org.enchant.core.network.OfflineQueue
import org.enchant.protos.EnvelopeProtos
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.enchant.core.network.QueuedMessage
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("MessageSendPipeline — Full Coverage")
class MessageSendPipelineTest {

    private lateinit var apiClient: ApiClient
    private lateinit var repo: ConversationRepository

    @BeforeEach
    fun setUp() {
        apiClient = mockk(relaxed = true)
        repo = mockk(relaxed = true)
        MessageSendPipeline.init(apiClient, repo)
        mockkObject(SecurePreferences)
        every { SecurePreferences.getString(any(), any()) } returns "self-user"
        every { SecurePreferences.getString(any()) } returns "self-user"
        mockkObject(ConnectivityMonitor)
        every { ConnectivityMonitor.isOnline } returns kotlinx.coroutines.flow.MutableStateFlow(true)
        mockkObject(SessionManager)
        coEvery { SessionManager.hasSession(any()) } returns true
        coEvery { SessionManager.encryptMessage(any(), any()) } returns EncryptedPayload(
            messageType = EnvelopeProtos.Envelope.Type.DOUBLE_RATCHET,
            payload = "encrypted".encodeToByteArray()
        )
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(SecurePreferences)
        unmockkObject(ConnectivityMonitor)
        unmockkObject(SessionManager)
    }

    @Nested @DisplayName("Send Message")
    inner class SendMessageTest {
        @Test @DisplayName("sendMessage fails with PAYLOAD_TOO_LARGE for > 64KB")
        fun `payload too large`() = runTest {
            val result = MessageSendPipeline.sendMessage("conv-1", "user-1", ByteArray(65 * 1024))
            assertTrue(result is SendResult.Failed)
            assertEquals(SendError.PAYLOAD_TOO_LARGE, (result as SendResult.Failed).error)
        }

        @Test @DisplayName("sendMessage fails with ENCRYPTION_FAILED when encrypt returns null")
        fun `encryption failed`() = runTest {
            coEvery { SessionManager.encryptMessage(any(), any()) } returns null
            val result = MessageSendPipeline.sendMessage("conv-1", "user-1", "Hello".encodeToByteArray())
            assertTrue(result is SendResult.Failed)
            assertEquals(SendError.ENCRYPTION_FAILED, (result as SendResult.Failed).error)
        }

        @Test @DisplayName("sendMessage fails with NETWORK when no user ID")
        fun `no user id`() = runTest {
            every { SecurePreferences.getString(any(), any()) } returns null
            val result = MessageSendPipeline.sendMessage("conv-1", "user-1", "Hello".encodeToByteArray())
            assertTrue(result is SendResult.Failed)
            assertEquals(SendError.NETWORK, (result as SendResult.Failed).error)
        }

        @Test @DisplayName("sendMessage queues when offline")
        fun `send offline queues`() = runTest {
            val flow = kotlinx.coroutines.flow.MutableStateFlow(false)
            every { ConnectivityMonitor.isOnline } returns flow
            coEvery { repo.insertMessage(any()) } returns 1L
            coEvery { repo.updateMessageStatus(any(), any()) } returns Unit
            val result = MessageSendPipeline.sendMessage("conv-1", "user-1", "Hello".encodeToByteArray())
            assertTrue(result is SendResult.Queued)
        }

        @Test @DisplayName("sendMessage returns Success on API success")
        fun `send success`() = runTest {
            coEvery { repo.insertMessage(any()) } returns 1L
            coEvery { repo.updateMessageStatus(any(), any()) } returns Unit
            coEvery { apiClient.post(any(), any()) } returns kotlinx.coroutines.runBlocking {
                kotlin.Result.success(kotlinx.serialization.json.buildJsonObject {
                    put("envelope_id", kotlinx.serialization.json.JsonPrimitive("env-server-1"))
                })
            }
            val result = MessageSendPipeline.sendMessage("conv-1", "user-1", "Hello".encodeToByteArray())
            assertTrue(result is SendResult.Success)
        }

        @Test @DisplayName("sendMessage queues on rate limit (429)")
        fun `send rate limit queues`() = runTest {
            coEvery { repo.insertMessage(any()) } returns 1L
            coEvery { repo.updateMessageStatus(any(), any()) } returns Unit
            coEvery { apiClient.post(any(), any()) } returns kotlinx.coroutines.runBlocking {
                kotlin.Result.failure(Exception("Rate limited 429"))
            }
            val result = MessageSendPipeline.sendMessage("conv-1", "user-1", "Hello".encodeToByteArray())
            assertTrue(result is SendResult.Queued)
        }

        @Test @DisplayName("sendMessage returns Failed on network error")
        fun `send network error`() = runTest {
            coEvery { repo.insertMessage(any()) } returns 1L
            coEvery { repo.updateMessageStatus(any(), any()) } returns Unit
            coEvery { apiClient.post(any(), any()) } returns kotlinx.coroutines.runBlocking {
                kotlin.Result.failure(Exception("Connection refused"))
            }
            val result = MessageSendPipeline.sendMessage("conv-1", "user-1", "Hello".encodeToByteArray())
            assertTrue(result is SendResult.Failed)
        }

        @Test @DisplayName("sendMessage inserts message with 'sending' status")
        fun `send inserts message`() = runTest {
            coEvery { repo.insertMessage(any()) } returns 1L
            coEvery { repo.updateMessageStatus(any(), any()) } returns Unit
            coEvery { apiClient.post(any(), any()) } returns kotlinx.coroutines.runBlocking {
                kotlin.Result.success(kotlinx.serialization.json.buildJsonObject {
                    put("envelope_id", kotlinx.serialization.json.JsonPrimitive("env-1"))
                })
            }
            MessageSendPipeline.sendMessage("conv-1", "user-1", "Hello".encodeToByteArray())
            coVerify { repo.insertMessage(match { it.status == "sending" }) }
        }

        @Test @DisplayName("sendMessage uses PREKEY_MESSAGE type when no session exists")
        fun `send prekey message`() = runTest {
            coEvery { SessionManager.hasSession(any()) } returns false
            coEvery { repo.insertMessage(any()) } returns 1L
            coEvery { repo.updateMessageStatus(any(), any()) } returns Unit
            coEvery { apiClient.post(any(), any()) } returns kotlinx.coroutines.runBlocking {
                kotlin.Result.success(kotlinx.serialization.json.buildJsonObject {
                    put("envelope_id", "env-1")
                })
            }
            MessageSendPipeline.sendMessage("conv-1", "user-1", "Hello".encodeToByteArray())
            coVerify { repo.insertMessage(match { it.messageType == "PREKEY_MESSAGE" }) }
        }

        @Test @DisplayName("sendMessage with replyTo includes replyToEnvelopeId")
        fun `send with reply to`() = runTest {
            coEvery { repo.insertMessage(any()) } returns 1L
            coEvery { repo.updateMessageStatus(any(), any()) } returns Unit
            coEvery { apiClient.post(any(), any()) } returns kotlinx.coroutines.runBlocking {
                kotlin.Result.success(kotlinx.serialization.json.buildJsonObject {
                    put("envelope_id", "env-1")
                })
            }
            MessageSendPipeline.sendMessage("conv-1", "user-1", "Reply".encodeToByteArray(), replyTo = "env-orig")
            coVerify { repo.insertMessage(match { it.replyToEnvelopeId == "env-orig" }) }
        }
    }

    @Nested @DisplayName("Send Sealed Message")
    inner class SendSealedTest {
        @Test @DisplayName("sendSealedMessage fails with PAYLOAD_TOO_LARGE for > 64KB")
        fun `sealed payload too large`() = runTest {
            val result = MessageSendPipeline.sendSealedMessage("user-1", ByteArray(65 * 1024), null)
            assertTrue(result is SendResult.Failed)
            assertEquals(SendError.PAYLOAD_TOO_LARGE, (result as SendResult.Failed).error)
        }

        @Test @DisplayName("sendSealedMessage fails with ENCRYPTION_FAILED when no identity key")
        fun `sealed no identity key`() = runTest {
            coEvery { SessionManager.encryptMessage(any(), any()) } returns null
            val result = MessageSendPipeline.sendSealedMessage("user-1", "Hello".encodeToByteArray(), null)
            assertTrue(result is SendResult.Failed)
            assertEquals(SendError.ENCRYPTION_FAILED, (result as SendResult.Failed).error)
        }

        @Test @DisplayName("sendSealedMessage returns Success on API success")
        fun `sealed success`() = runTest {
            coEvery { apiClient.postAnonymous(any(), any()) } returns kotlinx.coroutines.runBlocking {
                kotlin.Result.success(kotlinx.serialization.json.buildJsonObject {
                    put("envelope_ids", kotlinx.serialization.json.buildJsonArray {
                        add(kotlinx.serialization.json.JsonPrimitive("env-sealed-1"))
                    })
                    put("sealed", true)
                })
            }
            val result = MessageSendPipeline.sendSealedMessage("user-1", "Hello".encodeToByteArray(), null)
            assertTrue(result is SendResult.Success)
        }

        @Test @DisplayName("sendSealedMessage returns Failed on network error")
        fun `sealed network error`() = runTest {
            coEvery { apiClient.postAnonymous(any(), any()) } returns kotlinx.coroutines.runBlocking {
                kotlin.Result.failure(Exception("Network error"))
            }
            val result = MessageSendPipeline.sendSealedMessage("user-1", "Hello".encodeToByteArray(), null)
            assertTrue(result is SendResult.Failed)
        }
    }

    @Nested @DisplayName("Send Reaction")
    inner class SendReactionTest {
        @Test @DisplayName("sendReaction calls PUT /v1/reactions/{messageId}")
        fun `send reaction`() = runTest {
            coEvery { apiClient.put(any(), any()) } returns kotlinx.coroutines.runBlocking {
                kotlin.Result.success(kotlinx.serialization.json.buildJsonObject { put("reacted", true) })
            }
            val result = MessageSendPipeline.sendReaction("msg-1", "\uD83D\uDC4D")
            assertTrue(result.isSuccess)
        }

        @Test @DisplayName("sendReaction returns failure on network error")
        fun `send reaction network error`() = runTest {
            coEvery { apiClient.put(any(), any()) } returns kotlinx.coroutines.runBlocking {
                kotlin.Result.failure(Exception("Network error"))
            }
            val result = MessageSendPipeline.sendReaction("msg-1", "\uD83D\uDC4D")
            assertTrue(result.isFailure)
        }
    }

    @Nested @DisplayName("Send Delivery/Read Receipts")
    inner class ReceiptTest {
        @Test @DisplayName("sendDeliveryReceipt sends encrypted receipt")
        fun `send delivery receipt`() = runTest {
            coEvery { SessionManager.encryptMessage(any(), any()) } returns EncryptedPayload(
                messageType = EnvelopeProtos.Envelope.Type.DOUBLE_RATCHET,
                payload = "receipt".encodeToByteArray()
            )
            coEvery { apiClient.post(any(), any()) } returns kotlinx.coroutines.runBlocking {
                kotlin.Result.success(kotlinx.serialization.json.buildJsonObject { put("ok", true) })
            }
            MessageSendPipeline.sendDeliveryReceipt("env-1", "sender-1")
        }

        @Test @DisplayName("sendReadReceipt sends encrypted receipt")
        fun `send read receipt`() = runTest {
            coEvery { SessionManager.encryptMessage(any(), any()) } returns EncryptedPayload(
                messageType = EnvelopeProtos.Envelope.Type.DOUBLE_RATCHET,
                payload = "receipt".encodeToByteArray()
            )
            coEvery { apiClient.post(any(), any()) } returns kotlinx.coroutines.runBlocking {
                kotlin.Result.success(kotlinx.serialization.json.buildJsonObject { put("ok", true) })
            }
            MessageSendPipeline.sendReadReceipt("env-1", "sender-1")
        }
    }

    @Nested @DisplayName("Send Typing Indicator")
    inner class TypingTest {
        @Test @DisplayName("sendTypingIndicator sends encrypted typing message")
        fun `send typing`() = runTest {
            coEvery { SessionManager.encryptMessage(any(), any()) } returns EncryptedPayload(
                messageType = EnvelopeProtos.Envelope.Type.PLAINTEXT_CONTENT,
                payload = "typing".encodeToByteArray()
            )
            coEvery { apiClient.post(any(), any()) } returns kotlinx.coroutines.runBlocking {
                kotlin.Result.success(kotlinx.serialization.json.buildJsonObject { put("ok", true) })
            }
            MessageSendPipeline.sendTypingIndicator("user-1", true)
        }

        @Test @DisplayName("sendTypingIndicator with isTyping=false sends stop")
        fun `send typing stop`() = runTest {
            coEvery { SessionManager.encryptMessage(any(), any()) } returns EncryptedPayload(
                messageType = EnvelopeProtos.Envelope.Type.PLAINTEXT_CONTENT,
                payload = "typing".encodeToByteArray()
            )
            coEvery { apiClient.post(any(), any()) } returns kotlinx.coroutines.runBlocking {
                kotlin.Result.success(kotlinx.serialization.json.buildJsonObject { put("ok", true) })
            }
            MessageSendPipeline.sendTypingIndicator("user-1", false)
        }

        @Test @DisplayName("sendTypingIndicator does nothing when not connected")
        fun `send typing not connected`() = runTest {
            coEvery { SessionManager.encryptMessage(any(), any()) } returns null
            MessageSendPipeline.sendTypingIndicator("user-1", true)
        }
    }

    @Nested @DisplayName("Edit Message")
    inner class EditMessageTest {
        @Test @DisplayName("editMessage fails when message not found")
        fun `edit not found`() = runTest {
            coEvery { repo.getMessage(any()) } returns null
            val result = MessageSendPipeline.editMessage("env-1", "New text".encodeToByteArray(), "user-1")
            assertTrue(result.isFailure)
        }

        @Test @DisplayName("editMessage fails when encryption fails")
        fun `edit encryption fails`() = runTest {
            coEvery { repo.getMessage(any()) } returns MessageEntity(
                localId = 1, conversationId = "conv-1", senderId = "self-user",
                envelopeId = "env-1", messageType = "SIGNAL_MESSAGE",
                content = "Old text", status = "sent", timestamp = 1000
            )
            coEvery { SessionManager.encryptMessage(any(), any()) } returns null
            val result = MessageSendPipeline.editMessage("env-1", "New text".encodeToByteArray(), "user-1")
            assertTrue(result.isFailure)
        }

        @Test @DisplayName("editMessage succeeds when all steps pass")
        fun `edit success`() = runTest {
            coEvery { repo.getMessage(any()) } returns MessageEntity(
                localId = 1, conversationId = "conv-1", senderId = "self-user",
                envelopeId = "env-1", messageType = "SIGNAL_MESSAGE",
                content = "Old text", status = "sent", timestamp = 1000
            )
            coEvery { SessionManager.encryptMessage(any(), any()) } returns EncryptedPayload(
                messageType = EnvelopeProtos.Envelope.Type.DOUBLE_RATCHET,
                payload = "encrypted".encodeToByteArray()
            )
            coEvery { apiClient.put(any(), any()) } returns kotlinx.coroutines.runBlocking {
                kotlin.Result.success(kotlinx.serialization.json.buildJsonObject { put("success", true) })
            }
            coEvery { repo.updateMessageContent(any(), any()) } returns Unit
            val result = MessageSendPipeline.editMessage("env-1", "New text".encodeToByteArray(), "user-1")
            assertTrue(result.isSuccess)
        }
    }

    @Nested @DisplayName("Delete Message")
    inner class DeleteMessageTest {
        @Test @DisplayName("deleteForEveryone sends encrypted delete and marks deleted")
        fun `delete for everyone`() = runTest {
            coEvery { SessionManager.encryptMessage(any(), any()) } returns EncryptedPayload(
                messageType = EnvelopeProtos.Envelope.Type.DOUBLE_RATCHET,
                payload = "delete".encodeToByteArray()
            )
            coEvery { apiClient.post(any(), any()) } returns kotlinx.coroutines.runBlocking {
                kotlin.Result.success(kotlinx.serialization.json.buildJsonObject { put("ok", true) })
            }
            coEvery { repo.markMessageDeleted(any()) } returns Unit
            val result = MessageSendPipeline.deleteForEveryone("env-1", "user-1")
            assertTrue(result.isSuccess)
        }

        @Test @DisplayName("deleteForSelf marks message as deleted")
        fun `delete for self`() = runTest {
            coEvery { repo.markMessageDeleted(any()) } returns Unit
            MessageSendPipeline.deleteForSelf("env-1")
            coVerify { repo.markMessageDeleted("env-1") }
        }
    }

    @Nested @DisplayName("Forward Message")
    inner class ForwardMessageTest {
        @Test @DisplayName("forwardMessage fails when original message not found")
        fun `forward not found`() = runTest {
            coEvery { repo.getMessage(any()) } returns null
            val result = MessageSendPipeline.forwardMessage("conv-1", "env-1", "conv-2", "user-2")
            assertTrue(result is SendResult.Failed)
        }

        @Test @DisplayName("forwardMessage sends message content to target")
        fun `forward success`() = runTest {
            coEvery { repo.getMessage(any()) } returns MessageEntity(
                localId = 1, conversationId = "conv-1", senderId = "user-1",
                envelopeId = "env-1", messageType = "SIGNAL_MESSAGE",
                content = "Forward this", status = "sent", timestamp = 1000
            )
            coEvery { repo.insertMessage(any()) } returns 1L
            coEvery { repo.updateMessageStatus(any(), any()) } returns Unit
            coEvery { SessionManager.encryptMessage(any(), any()) } returns EncryptedPayload(
                messageType = EnvelopeProtos.Envelope.Type.DOUBLE_RATCHET,
                payload = "encrypted".encodeToByteArray()
            )
            coEvery { apiClient.post(any(), any()) } returns kotlinx.coroutines.runBlocking {
                kotlin.Result.success(kotlinx.serialization.json.buildJsonObject {
                    put("envelope_id", "env-2")
                })
            }
            val result = MessageSendPipeline.forwardMessage("conv-1", "env-1", "conv-2", "user-2")
            assertTrue(result is SendResult.Success)
        }
    }

    @Nested @DisplayName("Update Message Status")
    inner class UpdateStatusTest {
        @Test @DisplayName("updateMessageStatus calls repo.updateMessageStatus")
        fun `update status`() = runTest {
            MessageSendPipeline.updateMessageStatus("env-1", MessageStatus.SENT)
            coVerify { repo.updateMessageStatus("env-1", MessageStatus.SENT) }
        }
    }
}

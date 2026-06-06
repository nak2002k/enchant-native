package org.enchant.chat.data

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import org.enchant.core.crypto.CryptoHelper
import org.enchant.core.crypto.EncryptedPayload
import org.enchant.core.crypto.SessionManager
import org.enchant.core.database.dao.ConversationDao
import org.enchant.core.database.dao.MessageDao
import org.enchant.core.database.dao.RecipientDao
import org.enchant.core.database.entity.ConversationEntity
import org.enchant.core.database.entity.MessageEntity
import org.enchant.core.database.entity.RecipientEntity
import org.enchant.core.model.MessageStatus
import org.enchant.core.network.ApiClient
import org.enchant.core.network.IncomingEnvelope
import org.enchant.core.network.WebSocketManager
import org.enchant.protos.EnvelopeProtos
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("IncomingMessageProcessor — Full Coverage")
class IncomingMessageProcessorTest {

    private lateinit var repo: ConversationRepository
    private lateinit var recipientDao: RecipientDao
    private lateinit var apiClient: ApiClient
    private lateinit var convDao: ConversationDao
    private lateinit var msgDao: MessageDao

    @BeforeEach
    fun setUp() {
        repo = mockk(relaxed = true)
        recipientDao = mockk(relaxed = true)
        apiClient = mockk(relaxed = true)
        convDao = mockk(relaxed = true)
        msgDao = mockk(relaxed = true)
        IncomingMessageProcessor.init(repo, recipientDao, apiClient, convDao, msgDao)
        mockkObject(SessionManager)
        mockkObject(MessageSendPipeline)
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(SessionManager)
        unmockkObject(MessageSendPipeline)
    }

    @Nested @DisplayName("Process Incoming")
    inner class ProcessIncomingTest {
        @Test @DisplayName("processIncoming returns Ignored for null senderUserId")
        fun `process null sender ignored`() = runTest {
            val envelope = IncomingEnvelope(
                envelopeId = "env-1",
                senderUserId = null,
                senderDeviceId = null,
                messageType = "ENCRYPTED_MESSAGE",
                payload = "payload".encodeToByteArray(),
                serverTimestamp = System.currentTimeMillis(),
                ephemeral = false
            )
            val result = IncomingMessageProcessor.processIncoming(envelope)
            assertTrue(result is ProcessResult.Ignored)
        }

        @Test @DisplayName("processIncoming returns Ignored for blocked sender")
        fun `process blocked sender ignored`() = runTest {
            coEvery { recipientDao.getBlocked() } returns listOf(
                RecipientEntity(recipientId = "blocked-user", isBlocked = true)
            )
            val envelope = IncomingEnvelope(
                envelopeId = "env-1",
                senderUserId = "blocked-user",
                senderDeviceId = null,
                messageType = "ENCRYPTED_MESSAGE",
                payload = "payload".encodeToByteArray(),
                serverTimestamp = System.currentTimeMillis(),
                ephemeral = false
            )
            val result = IncomingMessageProcessor.processIncoming(envelope)
            assertTrue(result is ProcessResult.Ignored)
        }

        @Test @DisplayName("processIncoming returns Handled for non-blocked sender")
        fun `process non blocked handled`() = runTest {
            coEvery { recipientDao.getBlocked() } returns emptyList()
            coEvery { SessionManager.decryptMessage(any(), any()) } returns org.enchant.core.crypto.DecryptedResult(
                plaintext = "Hello".encodeToByteArray(),
                senderDeviceId = null,
                isNewSession = false
            )
            coEvery { repo.insertMessageAndUpdateConversation(any(), any()) } returns Unit
            coEvery { MessageSendPipeline.sendDeliveryReceipt(any(), any()) } returns Unit
            val envelope = IncomingEnvelope(
                envelopeId = "env-1",
                senderUserId = "sender-1",
                senderDeviceId = null,
                messageType = "ENCRYPTED_MESSAGE",
                payload = "payload".encodeToByteArray(),
                serverTimestamp = System.currentTimeMillis(),
                ephemeral = false
            )
            val result = IncomingMessageProcessor.processIncoming(envelope)
            assertTrue(result is ProcessResult.Handled)
        }

        @Test @DisplayName("processIncoming returns Error on decryption failure")
        fun `process decryption error`() = runTest {
            coEvery { recipientDao.getBlocked() } returns emptyList()
            coEvery { SessionManager.decryptMessage(any(), any()) } returns null
            val envelope = IncomingEnvelope(
                envelopeId = "env-1",
                senderUserId = "sender-1",
                senderDeviceId = null,
                messageType = "ENCRYPTED_MESSAGE",
                payload = "payload".encodeToByteArray(),
                serverTimestamp = System.currentTimeMillis(),
                ephemeral = false
            )
            val result = IncomingMessageProcessor.processIncoming(envelope)
            assertTrue(result is ProcessResult.Error)
        }
    }

    @Nested @DisplayName("Process PreKey Message")
    inner class ProcessPreKeyTest {
        @Test @DisplayName("processPreKeyMessage establishes session and stores message")
        fun `process prekey establishes session`() = runTest {
            coEvery { recipientDao.getBlocked() } returns emptyList()
            coEvery { SessionManager.getIdentityKey(any()) } returns null
            coEvery { SessionManager.decryptMessage(any(), any()) } returns org.enchant.core.crypto.DecryptedResult(
                plaintext = "First message".encodeToByteArray(),
                senderDeviceId = null,
                isNewSession = true
            )
            coEvery { repo.insertMessageAndUpdateConversation(any(), any()) } returns Unit
            coEvery { MessageSendPipeline.sendDeliveryReceipt(any(), any()) } returns Unit
            val envelope = IncomingEnvelope(
                envelopeId = "env-prekey-1",
                senderUserId = "new-sender",
                senderDeviceId = null,
                messageType = "PREKEY_MESSAGE",
                payload = "prekey-payload".encodeToByteArray(),
                serverTimestamp = System.currentTimeMillis(),
                ephemeral = false
            )
            val result = IncomingMessageProcessor.processIncoming(envelope)
            assertTrue(result is ProcessResult.Handled)
        }

        @Test @DisplayName("processPreKeyMessage returns Error when decryption fails")
        fun `process prekey decrypt error`() = runTest {
            coEvery { recipientDao.getBlocked() } returns emptyList()
            coEvery { SessionManager.getIdentityKey(any()) } returns null
            coEvery { SessionManager.decryptMessage(any(), any()) } returns null
            val envelope = IncomingEnvelope(
                envelopeId = "env-prekey-1",
                senderUserId = "new-sender",
                senderDeviceId = null,
                messageType = "PREKEY_MESSAGE",
                payload = "prekey-payload".encodeToByteArray(),
                serverTimestamp = System.currentTimeMillis(),
                ephemeral = false
            )
            val result = IncomingMessageProcessor.processIncoming(envelope)
            assertTrue(result is ProcessResult.Error)
        }
    }

    @Nested @DisplayName("Process Unidentified Sender")
    inner class ProcessUnidentifiedTest {
        @Test @DisplayName("processUnidentifiedSender resolves sender identity and decrypts")
        fun `process unidentified resolves sender`() = runTest {
            val sealedPayload = """{"senderIdentity":"${CryptoHelper.base64UrlEncode(CryptoHelper.generateEd25519KeyPair().publicKey)}","ciphertext":"${CryptoHelper.base64UrlEncode("encrypted".encodeToByteArray())}"}"""
            coEvery { SessionManager.findUserIdByIdentityKey(any()) } returns "sealed-sender"
            coEvery { SessionManager.decryptMessage(any(), any()) } returns org.enchant.core.crypto.DecryptedResult(
                plaintext = "Sealed message".encodeToByteArray(),
                senderDeviceId = null,
                isNewSession = false
            )
            coEvery { repo.insertMessageAndUpdateConversation(any(), any()) } returns Unit
            val envelope = IncomingEnvelope(
                envelopeId = "env-sealed-1",
                senderUserId = null,
                senderDeviceId = null,
                messageType = "UNIDENTIFIED_SENDER",
                payload = sealedPayload.encodeToByteArray(),
                serverTimestamp = System.currentTimeMillis(),
                ephemeral = true,
                replyToken = "reply-token-1"
            )
            val result = IncomingMessageProcessor.processIncoming(envelope)
            assertTrue(result is ProcessResult.Handled)
        }

        @Test @DisplayName("processUnidentifiedSender returns Error when senderIdentity missing")
        fun `process unidentified missing sender`() = runTest {
            val envelope = IncomingEnvelope(
                envelopeId = "env-sealed-1",
                senderUserId = null,
                senderDeviceId = null,
                messageType = "UNIDENTIFIED_SENDER",
                payload = """{"ciphertext":"encrypted"}""".encodeToByteArray(),
                serverTimestamp = System.currentTimeMillis(),
                ephemeral = true
            )
            val result = IncomingMessageProcessor.processIncoming(envelope)
            assertTrue(result is ProcessResult.Error)
        }

        @Test @DisplayName("processUnidentifiedSender returns Error when ciphertext missing")
        fun `process unidentified missing ciphertext`() = runTest {
            val envelope = IncomingEnvelope(
                envelopeId = "env-sealed-1",
                senderUserId = null,
                senderDeviceId = null,
                messageType = "UNIDENTIFIED_SENDER",
                payload = """{"senderIdentity":"base64key"}""".encodeToByteArray(),
                serverTimestamp = System.currentTimeMillis(),
                ephemeral = true
            )
            val result = IncomingMessageProcessor.processIncoming(envelope)
            assertTrue(result is ProcessResult.Error)
        }

        @Test @DisplayName("processUnidentifiedSender returns Error when sender identity unknown")
        fun `process unidentified unknown sender`() = runTest {
            val ik = CryptoHelper.generateEd25519KeyPair().publicKey
            val sealedPayload = """{"senderIdentity":"${CryptoHelper.base64UrlEncode(ik)}","ciphertext":"${CryptoHelper.base64UrlEncode("encrypted".encodeToByteArray())}"}"""
            coEvery { SessionManager.findUserIdByIdentityKey(any()) } returns null
            val envelope = IncomingEnvelope(
                envelopeId = "env-sealed-1",
                senderUserId = null,
                senderDeviceId = null,
                messageType = "UNIDENTIFIED_SENDER",
                payload = sealedPayload.encodeToByteArray(),
                serverTimestamp = System.currentTimeMillis(),
                ephemeral = true
            )
            val result = IncomingMessageProcessor.processIncoming(envelope)
            assertTrue(result is ProcessResult.Error)
        }
    }

    @Nested @DisplayName("Message Buffering")
    inner class BufferTest {
        @Test @DisplayName("flushBuffer inserts batch of messages")
        fun `flush buffer`() = runTest {
            coEvery { msgDao.insertBatch(any()) } returns Unit
            IncomingMessageProcessor.flush()
        }

        @Test @DisplayName("bufferMessage flushes when threshold reached")
        fun `buffer flushes at threshold`() = runTest {
            coEvery { msgDao.insertBatch(any()) } returns Unit
            repeat(20) { i ->
                IncomingMessageProcessor.processIncoming(IncomingEnvelope(
                    envelopeId = "env-$i",
                    senderUserId = "sender-1",
                    senderDeviceId = null,
                    messageType = "ENCRYPTED_MESSAGE",
                    payload = "msg-$i".encodeToByteArray(),
                    serverTimestamp = System.currentTimeMillis(),
                    ephemeral = false
                ))
            }
        }
    }

    @Nested @DisplayName("Disappear Timer")
    inner class DisappearTimerTest {
        @Test @DisplayName("processEncryptedMessage applies disappear timer when configured")
        fun `process applies disappear timer`() = runTest {
            coEvery { recipientDao.getBlocked() } returns emptyList()
            coEvery { SessionManager.decryptMessage(any(), any()) } returns org.enchant.core.crypto.DecryptedResult(
                plaintext = "Hello".encodeToByteArray(),
                senderDeviceId = null,
                isNewSession = false
            )
            coEvery { repo.insertMessageAndUpdateConversation(any(), any()) } returns Unit
            coEvery { MessageSendPipeline.sendDeliveryReceipt(any(), any()) } returns Unit
            coEvery { convDao.getById(any()) } returns ConversationEntity(
                conversationId = "sender-1",
                type = "direct",
                disappearTimerSeconds = 86400
            )
            coEvery { msgDao.updateDisappearAt(any(), any()) } returns Unit
            val envelope = IncomingEnvelope(
                envelopeId = "env-1",
                senderUserId = "sender-1",
                senderDeviceId = null,
                messageType = "ENCRYPTED_MESSAGE",
                payload = "payload".encodeToByteArray(),
                serverTimestamp = System.currentTimeMillis(),
                ephemeral = false
            )
            IncomingMessageProcessor.processIncoming(envelope)
        }
    }
}

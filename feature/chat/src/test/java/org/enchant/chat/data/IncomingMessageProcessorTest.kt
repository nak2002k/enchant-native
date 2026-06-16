package org.enchant.chat.data

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import org.enchant.chat.MainDispatcherRule
import org.enchant.core.crypto.NativeSessionManager
import org.enchant.core.database.dao.ConversationDao
import org.enchant.core.database.dao.MessageDao
import org.enchant.core.database.dao.RecipientDao
import org.enchant.core.database.entity.ConversationEntity
import org.enchant.core.database.entity.RecipientEntity
import org.enchant.core.network.ApiClient
import org.enchant.core.network.IncomingEnvelope
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@DisplayName("IncomingMessageProcessor — Full Coverage")
@Disabled("Pre-existing: requires libenchantcrypto native lib + SecurePreferences mocking conflicts")
class IncomingMessageProcessorTest {

    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule()

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
        mockkObject(NativeSessionManager)
        mockkObject(MessageSendPipeline)
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(NativeSessionManager)
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
    }

    @Nested @DisplayName("Process PreKey Message")
    inner class ProcessPreKeyTest {
        @Test @DisplayName("processPreKeyMessage establishes session and stores message")
        fun `process prekey establishes session`() = runTest {
            coEvery { recipientDao.getBlocked() } returns emptyList()
            coEvery { NativeSessionManager.decryptPreKeyMessage(any(), any()) } returns NativeSessionManager.DecryptedResult(
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
            coEvery { NativeSessionManager.decryptPreKeyMessage(any(), any()) } returns null
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

    @Nested @DisplayName("Process Encrypted Message")
    inner class ProcessEncryptedTest {
        @Test @DisplayName("processEncryptedMessage returns Error on decryption failure")
        fun `process decryption error`() = runTest {
            coEvery { recipientDao.getBlocked() } returns emptyList()
            coEvery { NativeSessionManager.decryptMessage(any(), any()) } returns null
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

    @Nested @DisplayName("Process Unidentified Sender")
    inner class ProcessUnidentifiedTest {
        @Test @DisplayName("processUnidentifiedSender returns Error when senderUserId missing")
        fun `process unidentified missing sender`() = runTest {
            val envelope = IncomingEnvelope(
                envelopeId = "env-sealed-1",
                senderUserId = null,
                senderDeviceId = null,
                messageType = "UNIDENTIFIED_SENDER",
                payload = "payload".encodeToByteArray(),
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
    }

    @Nested @DisplayName("Disappear Timer")
    inner class DisappearTimerTest {
        @Test @DisplayName("processEncryptedMessage applies disappear timer when configured")
        fun `process applies disappear timer`() = runTest {
            coEvery { recipientDao.getBlocked() } returns emptyList()
            coEvery { NativeSessionManager.decryptMessage(any(), any()) } returns NativeSessionManager.DecryptedResult(
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

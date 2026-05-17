package org.enchant.chat.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.enchant.core.crypto.CryptoHelper
import org.enchant.core.crypto.SessionManager
import org.enchant.core.database.dao.ConversationDao
import org.enchant.core.database.dao.MessageDao
import org.enchant.core.database.entity.MessageEntity
import org.enchant.core.database.entity.RecipientEntity
import org.enchant.core.model.MessageStatus
import org.enchant.core.network.IncomingEnvelope
import org.enchant.core.network.WebSocketManager
import org.enchant.core.base.SecurePreferences

sealed class ProcessResult {
    data object Handled : ProcessResult()
    data object Ignored : ProcessResult()
    data class Error(val reason: String) : ProcessResult()
}

sealed class DecryptedContent {
    data class Text(val body: String) : DecryptedContent()
    data class Media(val mediaId: String, val mediaKey: ByteArray, val mimeType: String) : DecryptedContent()
    data class Reaction(val messageId: String, val emoji: String, val remove: Boolean) : DecryptedContent()
    data class Edit(val originalEnvelopeId: String, val newContent: ByteArray) : DecryptedContent()
    data class Delete(val envelopeId: String) : DecryptedContent()
    data class DeliveryReceipt(val envelopeId: String, val senderUserId: String) : DecryptedContent()
    data class ReadReceipt(val envelopeId: String, val senderUserId: String) : DecryptedContent()
    data class Typing(val isTyping: Boolean) : DecryptedContent()
    data class GroupUpdate(val groupId: String, val updateType: String) : DecryptedContent()
    data object Unknown : DecryptedContent()
}

object IncomingMessageProcessor {
    private var repository: ConversationRepository? = null
    private var recipientDao: org.enchant.core.database.dao.RecipientDao? = null
    private var apiClient: org.enchant.core.network.ApiClient? = null
    private var conversationDao: ConversationDao? = null
    private var messageDao: MessageDao? = null
    @Volatile
    private var initialized = false
    private val bufferedMessages = mutableListOf<MessageEntity>()
    private const val BATCH_FLUSH_THRESHOLD = 20

    private suspend fun flushBuffer() {
        if (bufferedMessages.isEmpty()) return
        val batch = bufferedMessages.toList()
        bufferedMessages.clear()
        messageDao?.insertBatch(batch)
    }

    private suspend fun bufferMessage(msg: MessageEntity) {
        bufferedMessages.add(msg)
        if (bufferedMessages.size >= BATCH_FLUSH_THRESHOLD) flushBuffer()
    }

    fun init(
        repo: ConversationRepository,
        recipients: org.enchant.core.database.dao.RecipientDao,
        client: org.enchant.core.network.ApiClient,
        convDao: ConversationDao,
        msgDao: MessageDao
    ) {
        repository = repo
        recipientDao = recipients
        apiClient = client
        conversationDao = convDao
        messageDao = msgDao
        initialized = true
    }

    private fun checkInit() {
        if (!initialized) throw IllegalStateException("IncomingMessageProcessor not initialized")
    }

    suspend fun processIncoming(envelope: IncomingEnvelope): ProcessResult {
        checkInit()
        val repo = repository!!

        return withContext(Dispatchers.Default) {
            try {
                if (envelope.messageType == "UNIDENTIFIED_SENDER") {
                    return@withContext processUnidentifiedSender(envelope, repo)
                }

                val senderId = envelope.senderUserId ?: return@withContext ProcessResult.Ignored

                val blocked = recipientDao!!.getBlocked()
                if (blocked.any { it.recipientId == senderId }) {
                    return@withContext ProcessResult.Ignored
                }

                if (envelope.messageType == "PREKEY_MESSAGE") {
                    return@withContext processPreKeyMessage(envelope, senderId, repo)
                }

                if (envelope.messageType == "SIGNAL_MESSAGE") {
                    return@withContext processSignalMessage(envelope, senderId, repo)
                }

                ProcessResult.Handled
            } catch (e: Exception) {
                ProcessResult.Error(e.message ?: "Unknown error")
            } finally {
                if (bufferedMessages.size >= BATCH_FLUSH_THRESHOLD) flushBuffer()
            }
        }
    }

    suspend fun flush() = flushBuffer()

    private suspend fun processPreKeyMessage(
        envelope: IncomingEnvelope, senderUserId: String, repo: ConversationRepository
    ): ProcessResult {
        return withContext(Dispatchers.Default) {
            try {
                val theirIk = SessionManager.getIdentityKey(senderUserId)
                if (theirIk == null) {
                    val bundleResult = fetchKeyBundle(senderUserId)
                    if (bundleResult == null) return@withContext ProcessResult.Error("No key bundle for $senderUserId")
                }

                val decrypted = SessionManager.decryptPreKeyMessage(senderUserId, envelope.payload)

                if (decrypted == null) return@withContext ProcessResult.Error("Failed to establish session")

                val plaintext = decrypted.plaintext.decodeToString()
                val now = System.currentTimeMillis()

                repo.insertMessageAndUpdateConversation(
                    MessageEntity(
                        conversationId = senderUserId,
                        senderId = senderUserId,
                        messageType = "SIGNAL_MESSAGE",
                        content = plaintext,
                        status = "delivered",
                        timestamp = envelope.serverTimestamp ?: now,
                        serverTs = now,
                        envelopeId = envelope.envelopeId
                    ),
                    conversationType = "direct"
                )

                applyDisappearTimer(senderUserId, envelope.envelopeId, envelope.serverTimestamp)

                MessageSendPipeline.sendDeliveryReceipt(
                    envelopeId = envelope.envelopeId ?: "",
                    senderUserId = senderUserId
                )

                ProcessResult.Handled
            } catch (e: Exception) {
                ProcessResult.Error("PreKey processing failed: ${e.message}")
            }
        }
    }

    private suspend fun processSignalMessage(
        envelope: IncomingEnvelope, senderUserId: String, repo: ConversationRepository
    ): ProcessResult {
        return withContext(Dispatchers.Default) {
            try {
                val decrypted = SessionManager.decryptMessage(senderUserId,
                    org.enchant.core.crypto.EncryptedPayload(
                        messageType = org.enchant.protos.EnvelopeProtos.Envelope.Type.DOUBLE_RATCHET,
                        payload = envelope.payload
                    )
                )

                if (decrypted == null) {
                    return@withContext ProcessResult.Error("Decryption failed")
                }

                val now = System.currentTimeMillis()
                val parsed = MessageProtobufHelper.parseContent(decrypted.plaintext)

                return@withContext when (parsed) {
                    is MessageProtobufHelper.ParsedContent.DataMessage -> {
                        repo.insertMessageAndUpdateConversation(
                            MessageEntity(
                                conversationId = senderUserId,
                                senderId = senderUserId,
                                messageType = "SIGNAL_MESSAGE",
                                content = parsed.body,
                                status = "delivered",
                                timestamp = envelope.serverTimestamp ?: now,
                                serverTs = now,
                                envelopeId = envelope.envelopeId
                            ),
                            conversationType = "direct"
                        )
                        applyDisappearTimer(senderUserId, envelope.envelopeId, envelope.serverTimestamp)
                        MessageSendPipeline.sendDeliveryReceipt(
                            envelopeId = envelope.envelopeId ?: "",
                            senderUserId = senderUserId
                        )
                        ProcessResult.Handled
                    }
                    is MessageProtobufHelper.ParsedContent.Receipt -> {
                        val status = when (parsed.type) {
                            MessageProtobufHelper.ReceiptType.DELIVERY -> MessageStatus.DELIVERED
                            MessageProtobufHelper.ReceiptType.READ -> MessageStatus.READ
                        }
                        parsed.timestamps.forEach { ts ->
                            val envId = messageDao?.getEnvelopeIdByServerTs(ts) ?: ts.toString()
                            repo.updateMessageStatus(envId, status)
                        }
                        ProcessResult.Handled
                    }
                    is MessageProtobufHelper.ParsedContent.Typing -> {
                        ProcessResult.Handled
                    }
                    is MessageProtobufHelper.ParsedContent.Delete -> {
                        ProcessResult.Handled
                    }
                    is MessageProtobufHelper.ParsedContent.Null -> {
                        ProcessResult.Handled
                    }
                    is MessageProtobufHelper.ParsedContent.Unknown -> {
                        ProcessResult.Error("Unknown content type")
                    }
                }
            } catch (e: Exception) {
                ProcessResult.Error("Signal message processing failed: ${e.message}")
            }
        }
    }

    private suspend fun applyDisappearTimer(conversationId: String, envelopeId: String?, serverTs: Long?) {
        val convDao = conversationDao ?: return
        val msgDao = messageDao ?: return
        if (envelopeId == null) return
        val conv = convDao.getById(conversationId) ?: return
        val timer = conv.disappearTimerSeconds
        if (timer > 0) {
            val baseTs = serverTs ?: System.currentTimeMillis()
            msgDao.updateDisappearAt(envelopeId, baseTs + timer * 1000L)
        }
    }

    private suspend fun processUnidentifiedSender(
        envelope: IncomingEnvelope, repo: ConversationRepository
    ): ProcessResult {
        return withContext(Dispatchers.Default) {
            try {
                val payloadStr = envelope.payload.decodeToString()
                val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                val parsed = json.parseToJsonElement(payloadStr).jsonObject
                val senderIdentityB64 = parsed["senderIdentity"]?.jsonPrimitive?.content
                    ?: return@withContext ProcessResult.Error("Missing senderIdentity in sealed payload")
                val ciphertextB64 = parsed["ciphertext"]?.jsonPrimitive?.content
                    ?: return@withContext ProcessResult.Error("Missing ciphertext in sealed payload")

                val senderIdentityKey = CryptoHelper.base64UrlDecode(senderIdentityB64)
                val senderUserId = SessionManager.findUserIdByIdentityKey(senderIdentityKey)
                    ?: return@withContext ProcessResult.Error("Unknown sender identity key")

                val ciphertext = CryptoHelper.base64UrlDecode(ciphertextB64)

                val decrypted = SessionManager.decryptMessage(senderUserId,
                    org.enchant.core.crypto.EncryptedPayload(
                        messageType = org.enchant.protos.EnvelopeProtos.Envelope.Type.DOUBLE_RATCHET,
                        payload = ciphertext
                    )
                )
                if (decrypted == null) {
                    return@withContext ProcessResult.Error("Decryption failed for sealed sender message")
                }

                val now = System.currentTimeMillis()
                val parsedContent = MessageProtobufHelper.parseContent(decrypted.plaintext)

                return@withContext when (parsedContent) {
                    is MessageProtobufHelper.ParsedContent.DataMessage -> {
                        repo.insertMessageAndUpdateConversation(
                            MessageEntity(
                                conversationId = senderUserId,
                                senderId = senderUserId,
                                messageType = "SIGNAL_MESSAGE",
                                content = parsedContent.body,
                                status = "delivered",
                                timestamp = envelope.serverTimestamp ?: now,
                                serverTs = now
                            ),
                            conversationType = "direct"
                        )
                        sendSealedDeliveryReceipt(envelope, senderUserId)
                        ProcessResult.Handled
                    }
                    is MessageProtobufHelper.ParsedContent.Receipt -> {
                        val status = when (parsedContent.type) {
                            MessageProtobufHelper.ReceiptType.DELIVERY -> MessageStatus.DELIVERED
                            MessageProtobufHelper.ReceiptType.READ -> MessageStatus.READ
                        }
                        ProcessResult.Handled
                    }
                    is MessageProtobufHelper.ParsedContent.Typing -> { ProcessResult.Handled }
                    is MessageProtobufHelper.ParsedContent.Delete -> { ProcessResult.Handled }
                    is MessageProtobufHelper.ParsedContent.Null -> { ProcessResult.Handled }
                    is MessageProtobufHelper.ParsedContent.Unknown -> {
                        ProcessResult.Error("Unknown content type in sealed sender message")
                    }
                }
            } catch (e: Exception) {
                ProcessResult.Error("Unidentified sender processing failed: ${e.message}")
            }
        }
    }

    private suspend fun sendSealedDeliveryReceipt(
        envelope: IncomingEnvelope, senderUserId: String
    ) {
        val replyToken = envelope.replyToken ?: return
        val ts = envelope.envelopeId?.toLongOrNull() ?: System.currentTimeMillis()
        val contentBytes = MessageProtobufHelper.buildReceiptContent(
            envelopeIds = listOf(ts.toString()),
            type = MessageProtobufHelper.ReceiptType.DELIVERY
        )
        MessageSendPipeline.sendSealedMessage(
            conversationId = senderUserId,
            recipientUserId = senderUserId,
            plaintext = contentBytes,
            replyToken = replyToken
        )
    }

    private suspend fun fetchKeyBundle(userId: String): Boolean {
        return try {
            val response = apiClient!!.get("/v1/keys/bundle/$userId")
            response.fold(
                onSuccess = { json ->
                    val devices = json["devices"]?.let { array ->
                        @Suppress("UNCHECKED_CAST")
                        (array as? kotlinx.serialization.json.JsonArray)
                    }
                    if (devices != null && devices.isNotEmpty()) {
                        val device = devices[0]
                        val obj = device as? kotlinx.serialization.json.JsonObject ?: return false
                        val ik = obj["identity_key"]?.let { key ->
                            (key as? kotlinx.serialization.json.JsonPrimitive)?.content
                        } ?: return false
                        val ikBytes = CryptoHelper.base64UrlDecode(ik)
                        SessionManager.setIdentityKey(userId, ikBytes)
                        true
                    } else false
                },
                onFailure = { false }
            )
        } catch (_: Exception) { false }
    }
}

package org.enchant.chat.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.enchant.core.crypto.CryptoHelper
import org.enchant.core.crypto.CryptoPrimitives
import org.enchant.core.crypto.NativeSessionManager
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
    private val bufferedMessages = java.util.concurrent.ConcurrentLinkedQueue<MessageEntity>()
    private const val BATCH_FLUSH_THRESHOLD = 20

    private suspend fun flushBuffer() {
        if (bufferedMessages.isEmpty()) return
        val batch = mutableListOf<MessageEntity>()
        while (batch.size < BATCH_FLUSH_THRESHOLD) {
            val msg = bufferedMessages.poll() ?: break
            batch.add(msg)
        }
        if (batch.isNotEmpty()) {
            messageDao?.insertBatch(batch)
        }
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

                if (envelope.sealed) {
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

                if (envelope.messageType == "ENCRYPTED_MESSAGE") {
                    return@withContext processEncryptedMessage(envelope, senderId, repo)
                }

                if (envelope.messageType == "ENVELOPE") {
                    return@withContext processEnvelopeMessage(envelope, senderId, repo)
                }

                if (envelope.messageType == "CALL_OFFER" ||
                    envelope.messageType == "CALL_ANSWER" ||
                    envelope.messageType == "CALL_ICE" ||
                    envelope.messageType == "CALL_END") {
                    return@withContext processCallMessage(envelope, senderId)
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
                val decrypted = NativeSessionManager.decryptPreKeyMessage(senderUserId, envelope.payload)

                if (decrypted == null) return@withContext ProcessResult.Error("Failed to establish session")

                val plaintext = decrypted.plaintext.decodeToString()
                val now = System.currentTimeMillis()

                repo.insertMessageAndUpdateConversation(
                    MessageEntity(
                        conversationId = senderUserId,
                        senderId = senderUserId,
                        messageType = "ENCRYPTED_MESSAGE",
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

    private suspend fun processEncryptedMessage(
        envelope: IncomingEnvelope, senderUserId: String, repo: ConversationRepository
    ): ProcessResult {
        return withContext(Dispatchers.Default) {
            try {
                val decrypted = NativeSessionManager.decryptMessage(senderUserId, envelope.payload)

                if (decrypted == null) {
                    android.util.Log.w("IncomingMsg", "Decryption failed for sender: $senderUserId")
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
                                messageType = "ENCRYPTED_MESSAGE",
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
                        val deleteContent = parsed
                        if (deleteContent.targetTimestamp > 0) {
                            val envId = messageDao?.getEnvelopeIdByServerTs(deleteContent.targetTimestamp)
                            if (envId != null) {
                                messageDao?.markDeleted(envId)
                            }
                        }
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
                ProcessResult.Error("Encrypted message processing failed: ${e.message}")
            }
        }
    }

    private suspend fun processEnvelopeMessage(
        envelope: IncomingEnvelope, senderUserId: String, repo: ConversationRepository
    ): ProcessResult {
        return withContext(Dispatchers.Default) {
            try {
                val decrypted = NativeSessionManager.decryptMessage(senderUserId, envelope.payload)
                    ?: NativeSessionManager.decryptPreKeyMessage(senderUserId, envelope.payload)
                    ?: return@withContext ProcessResult.Error("Decryption failed")

                val now = System.currentTimeMillis()
                val parsed = MessageProtobufHelper.parseContent(decrypted.plaintext)

                return@withContext when (parsed) {
                    is MessageProtobufHelper.ParsedContent.DataMessage -> {
                        repo.insertMessageAndUpdateConversation(
                            MessageEntity(
                                conversationId = senderUserId,
                                senderId = senderUserId,
                                messageType = "ENCRYPTED_MESSAGE",
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
                        if (parsed.targetTimestamp > 0) {
                            val envId = messageDao?.getEnvelopeIdByServerTs(parsed.targetTimestamp)
                            if (envId != null) {
                                messageDao?.markDeleted(envId)
                            }
                        }
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
                ProcessResult.Error("Envelope message processing failed: ${e.message}")
            }
        }
    }

    private suspend fun processCallMessage(
        envelope: IncomingEnvelope, senderUserId: String
    ): ProcessResult {
        return withContext(Dispatchers.Default) {
            try {
                val json = runCatching {
                    kotlinx.serialization.json.Json.parseToJsonElement(envelope.payload.decodeToString())
                }.getOrNull() ?: return@withContext ProcessResult.Error("Invalid call payload")

                val callId = json.jsonObject["call_id"]?.jsonPrimitive?.content
                    ?: envelope.envelopeId
                    ?: senderUserId

                when (envelope.messageType) {
                    "CALL_OFFER" -> {
                        val rawSdp = json.jsonObject["sdp"]?.jsonPrimitive?.content
                            ?: return@withContext ProcessResult.Error("Missing sdp in CALL_OFFER")
                        val sdp = decryptSignal(senderUserId, rawSdp)
                            ?: return@withContext ProcessResult.Error("Failed to decrypt CALL_OFFER sdp")
                        val isVideo = sdp.contains("m=video", ignoreCase = true)
                        org.enchant.core.calls.CallManager.handleReceivedOffer(
                            senderUserId = senderUserId,
                            sdp = sdp,
                            callId = callId,
                            isVideo = isVideo
                        )
                        ProcessResult.Handled
                    }
                    "CALL_ANSWER" -> {
                        val rawSdp = json.jsonObject["sdp"]?.jsonPrimitive?.content
                            ?: return@withContext ProcessResult.Error("Missing sdp in CALL_ANSWER")
                        val sdp = decryptSignal(senderUserId, rawSdp)
                            ?: return@withContext ProcessResult.Error("Failed to decrypt CALL_ANSWER sdp")
                        org.enchant.core.calls.CallManager.handleReceivedAnswer(sdp)
                        ProcessResult.Handled
                    }
                    "CALL_ICE" -> {
                        val rawCandidate = json.jsonObject["candidate"]?.jsonPrimitive?.content
                            ?: return@withContext ProcessResult.Error("Missing candidate in CALL_ICE")
                        val candidate = decryptSignal(senderUserId, rawCandidate)
                            ?: return@withContext ProcessResult.Error("Failed to decrypt CALL_ICE candidate")
                        org.enchant.core.calls.CallManager.handleReceivedIce(candidate)
                        ProcessResult.Handled
                    }
                    "CALL_END" -> {
                        org.enchant.core.calls.CallManager.handleReceivedHangup()
                        ProcessResult.Handled
                    }
                    else -> ProcessResult.Handled
                }
            } catch (e: Exception) {
                ProcessResult.Error("Call message processing failed: ${e.message}")
            }
        }
    }

    /**
     * Decrypts a signaling payload (SDP / ICE candidate) that was wrapped by
     * [WebSocketSignalingClient.encryptSignal]. Returns null when the payload
     * is not a valid encrypted wrapper or decryption fails.
     */
    private suspend fun decryptSignal(senderUserId: String, raw: String): String? {
        val wrapper = runCatching {
            kotlinx.serialization.json.Json.parseToJsonElement(raw).jsonObject
        }.getOrNull() ?: return null

        if (wrapper["c"]?.jsonPrimitive?.content != "1") return null

        val marker = wrapper["mt"]?.jsonPrimitive?.content
        val data = wrapper["d"]?.jsonPrimitive?.content ?: return null
        val ciphertext = runCatching { CryptoPrimitives.base64UrlDecode(data) }.getOrNull() ?: return null

        val decrypted = when (marker) {
            "P" -> NativeSessionManager.decryptPreKeyMessage(senderUserId, ciphertext)
            else -> NativeSessionManager.decryptMessage(senderUserId, ciphertext)
        } ?: return null

        return decrypted.plaintext.toString(Charsets.UTF_8)
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
                val identityKeyPair = org.enchant.core.crypto.KeyManager.getIdentityKeyPair()
                    ?: return@withContext ProcessResult.Error("Local identity key missing")
                val decrypted = org.enchant.core.crypto.SealedSender.decryptSealed(
                    recipientPrivateKey = identityKeyPair.privateKey,
                    recipientPublicKey = identityKeyPair.publicKey,
                    sealedPayload = envelope.payload
                ) ?: return@withContext ProcessResult.Error("Sealed decrypt failed")

                val recoveredSenderKey = decrypted.first
                val wrapper = org.enchant.protos.SignalServiceContentProto.parseFrom(decrypted.second)
                val senderUserId = wrapper.localAddress.uuid.toStringUtf8()
                if (senderUserId.isEmpty()) {
                    return@withContext ProcessResult.Error("Missing sender identity in sealed payload")
                }

                val knownKey = NativeSessionManager.getIdentityKey(senderUserId)
                if (knownKey != null && !knownKey.contentEquals(recoveredSenderKey)) {
                    android.util.Log.w("IncomingMsg", "Sealed sender identity key mismatch for $senderUserId")
                    return@withContext ProcessResult.Error("Sender identity key mismatch")
                }
                if (knownKey == null) {
                    NativeSessionManager.setIdentityKey(senderUserId, recoveredSenderKey)
                }

                val content = wrapper.content
                val now = System.currentTimeMillis()

                return@withContext when {
                    content.hasDataMessage() -> {
                        val dataMsg = content.dataMessage
                        repo.insertMessageAndUpdateConversation(
                            MessageEntity(
                                conversationId = senderUserId,
                                senderId = senderUserId,
                                messageType = "ENCRYPTED_MESSAGE",
                                content = dataMsg.body,
                                status = "delivered",
                                timestamp = envelope.serverTimestamp ?: now,
                                serverTs = now
                            ),
                            conversationType = "direct"
                        )
                        sendSealedDeliveryReceipt(envelope, senderUserId)
                        ProcessResult.Handled
                    }
                    content.hasReceiptMessage() -> {
                        val rm = content.receiptMessage
                        val status = when (rm.type) {
                            org.enchant.protos.ReceiptMessageProtos.ReceiptMessage.Type.DELIVERY -> MessageStatus.DELIVERED
                            org.enchant.protos.ReceiptMessageProtos.ReceiptMessage.Type.READ -> MessageStatus.READ
                            else -> MessageStatus.DELIVERED
                        }
                        rm.timestampList.forEach { ts ->
                            val envId = messageDao?.getEnvelopeIdByServerTs(ts) ?: ts.toString()
                            repo.updateMessageStatus(envId, status)
                        }
                        ProcessResult.Handled
                    }
                    content.hasTypingMessage() -> { ProcessResult.Handled }
                    else -> {
                        ProcessResult.Error("Unknown content type in sealed sender message")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("IncomingMsg", "Unidentified sender processing failed: ${e.message}")
                ProcessResult.Error("Unidentified sender processing failed")
            }
        }
    }

    private suspend fun sendSealedDeliveryReceipt(
        envelope: IncomingEnvelope, senderUserId: String
    ) {
        val replyToken = envelope.replyToken ?: return
        val msg = repository?.getMessage(envelope.envelopeId ?: "")
        val ts = msg?.timestamp ?: System.currentTimeMillis()
        val contentBytes = MessageProtobufHelper.buildReceiptContent(
            timestamps = listOf(ts),
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
                        NativeSessionManager.setIdentityKey(userId, ikBytes)
                        true
                    } else false
                },
                onFailure = { false }
            )
        } catch (_: Exception) { false }
    }
}

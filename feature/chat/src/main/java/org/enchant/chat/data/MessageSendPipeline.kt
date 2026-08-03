package org.enchant.chat.data

import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.Arrays
import java.util.UUID
import org.enchant.core.base.AppConfig
import org.enchant.core.base.SecurePreferences
import org.enchant.core.crypto.CryptoHelper
import org.enchant.core.crypto.KeyManager
import org.enchant.core.crypto.NativeSessionManager
import org.enchant.core.database.entity.MessageEntity
import org.enchant.core.model.MessageStatus
import org.enchant.core.network.ApiClient
import org.enchant.core.network.ConnectivityMonitor
import org.enchant.core.network.OfflineQueue
import org.enchant.core.network.QueuedMessage

sealed class SendResult {
    data class Success(val envelopeId: String) : SendResult()
    data class Queued(val messageId: String) : SendResult()
    data class Failed(val error: SendError) : SendResult()
}

enum class SendError {
    NO_SESSION, KEY_BUNDLE_MISSING, PAYLOAD_TOO_LARGE, RATE_LIMITED, NETWORK, ENCRYPTION_FAILED
}

object MessageSendPipeline {
    private var apiClient: ApiClient? = null
    private var repository: ConversationRepository? = null
    private var scope: CoroutineScope? = null
    private var lastTypingTs = 0L
    private var typingJob: Job? = null

    fun init(client: ApiClient, repo: ConversationRepository) {
        apiClient = client
        repository = repo
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    fun shutdown() {
        scope?.cancel()
        scope = null
        apiClient = null
        repository = null
        typingJob?.cancel()
        typingJob = null
    }

    private fun checkInit() {
        if (apiClient == null || repository == null) throw IllegalStateException("MessageSendPipeline not initialized")
    }

    suspend fun sendMessage(
        conversationId: String,
        recipientUserId: String,
        plaintext: ByteArray,
        replyTo: String? = null,
        useSealedSender: Boolean = false
    ): SendResult {
        checkInit()
        val repo = repository!!

        return withContext(Dispatchers.Default) {
            try {
                if (plaintext.size > 64 * 1024) return@withContext SendResult.Failed(SendError.PAYLOAD_TOO_LARGE)

                if (useSealedSender) {
                    return@withContext sendSealedMessage(conversationId, recipientUserId, plaintext, null)
                }

                val contentBytes = MessageProtobufHelper.buildDataMessageContent(
                    body = plaintext.decodeToString(),
                    timestamp = System.currentTimeMillis()
                )

                val hasSession = NativeSessionManager.hasSession(recipientUserId)
                val encrypted = NativeSessionManager.encryptMessage(recipientUserId, contentBytes)
                if (encrypted == null) return@withContext SendResult.Failed(SendError.ENCRYPTION_FAILED)

                val envelopeId = UUID.randomUUID().toString()
                val now = System.currentTimeMillis()
                val selfId = SecurePreferences.getString("auth.user_id") ?: return@withContext SendResult.Failed(SendError.NETWORK)

                repo.insertMessage(MessageEntity(
                    conversationId = conversationId, senderId = selfId,
                    envelopeId = envelopeId,
                    messageType = if (hasSession) "ENCRYPTED_MESSAGE" else "PREKEY_MESSAGE",
                    content = plaintext.decodeToString(), status = "sending",
                    timestamp = now, replyToEnvelopeId = replyTo
                ))
                Arrays.fill(plaintext, 0)

                if (!ConnectivityMonitor.isOnline.value) {
                    OfflineQueue.enqueue(QueuedMessage(
                        recipientUserId = recipientUserId,
                        recipientDeviceId = encrypted.recipientDeviceId,
                        messageType = if (hasSession) "ENCRYPTED_MESSAGE" else "PREKEY_MESSAGE",
                        payload = encrypted.payload, senderTs = now
                    ))
                    repo.updateMessageStatus(envelopeId, MessageStatus.PENDING)
                    return@withContext SendResult.Queued(envelopeId)
                }

                val client = apiClient!!
                val payloadB64 = CryptoHelper.base64UrlEncode(encrypted.payload)
                val response = client.post("/v1/messages/send", buildJsonObject {
                    put("recipient_user_id", recipientUserId)
                    put("message_type", if (hasSession) "ENCRYPTED_MESSAGE" else "PREKEY_MESSAGE")
                    put("payload", payloadB64)
                    put("sender_ts", System.currentTimeMillis().toString())
                })

                response.fold(
                    onSuccess = { json ->
                        val ids = json["envelope_ids"]?.jsonArray
                        val serverId = ids?.firstOrNull()?.jsonPrimitive?.content ?: envelopeId
                        repo.updateMessageStatus(envelopeId, MessageStatus.SENT)
                        SendResult.Success(serverId)
                    },
                    onFailure = { e ->
                        val isRateLimit = e.message?.contains("429") == true || e.message?.contains("rate", ignoreCase = true) == true
                        if (isRateLimit) {
                            OfflineQueue.enqueue(QueuedMessage(
                                recipientUserId = recipientUserId,
                                recipientDeviceId = encrypted.recipientDeviceId,
                                messageType = if (hasSession) "ENCRYPTED_MESSAGE" else "PREKEY_MESSAGE",
                                payload = encrypted.payload, senderTs = now
                            ))
                            repo.updateMessageStatus(envelopeId, MessageStatus.PENDING)
                            SendResult.Queued(envelopeId)
                        } else {
                            repo.updateMessageStatus(envelopeId, MessageStatus.FAILED)
                            SendResult.Failed(SendError.NETWORK)
                        }
                    }
                )
            } catch (e: Exception) {
                SendResult.Failed(SendError.NETWORK)
            }
        }
    }

    suspend fun sendSealedMessage(
        conversationId: String,
        recipientUserId: String,
        plaintext: ByteArray,
        replyToken: String? = null
    ): SendResult {
        checkInit()
        return withContext(Dispatchers.Default) {
            try {
                if (plaintext.size > 64 * 1024) return@withContext SendResult.Failed(SendError.PAYLOAD_TOO_LARGE)

                val identityKeyPair = KeyManager.getIdentityKeyPair()
                if (identityKeyPair == null) return@withContext SendResult.Failed(SendError.ENCRYPTION_FAILED)

                val recipientPublicKey = NativeSessionManager.getIdentityKey(recipientUserId)
                    ?: fetchRecipientIdentityKey(recipientUserId)
                    ?: return@withContext SendResult.Failed(SendError.KEY_BUNDLE_MISSING)

                val selfId = SecurePreferences.getString("auth.user_id")
                    ?: return@withContext SendResult.Failed(SendError.NETWORK)

                val parsedContent = runCatching {
                    org.enchant.protos.ContentProtos.Content.parseFrom(plaintext)
                }.getOrNull()
                val content = if (parsedContent != null && (
                        parsedContent.hasDataMessage() ||
                        parsedContent.hasReceiptMessage() ||
                        parsedContent.hasTypingMessage() ||
                        parsedContent.hasCallMessage() ||
                        parsedContent.hasNullMessage() ||
                        parsedContent.hasEditMessage() ||
                        parsedContent.hasSyncMessage() ||
                        parsedContent.hasStoryMessage()
                    )) {
                    parsedContent
                } else {
                    MessageProtobufHelper.buildDataMessageContent(
                        body = plaintext.decodeToString(),
                        timestamp = System.currentTimeMillis()
                    )
                }

                val wrapper = org.enchant.protos.SignalServiceContentProto.newBuilder()
                    .setLocalAddress(
                        org.enchant.protos.AddressProto.newBuilder()
                            .setUuid(com.google.protobuf.ByteString.copyFrom(selfId.toByteArray(Charsets.UTF_8)))
                            .build()
                    )
                    .setContent(content)
                    .build()

                val sealedPayload = org.enchant.core.crypto.SealedSender.encryptSealed(
                    recipientPublicKey = recipientPublicKey,
                    senderIdentityPrivate = identityKeyPair.privateKey,
                    senderIdentityPublic = identityKeyPair.publicKey,
                    message = wrapper.toByteArray()
                )

                val ciphertextB64 = CryptoHelper.base64UrlEncode(sealedPayload)

                val client = apiClient!!
                val response = client.postAnonymous("/v1/messages/sealed-send", buildJsonObject {
                    put("recipient_user_id", recipientUserId)
                    put("message_type", "UNIDENTIFIED_SENDER")
                    put("payload", ciphertextB64)
                    if (replyToken != null) put("reply_token", replyToken)
                })

                response.fold(
                    onSuccess = { json ->
                        val ids = json["envelope_ids"]?.jsonArray
                        val serverId = ids?.firstOrNull()?.jsonPrimitive?.content
                        SendResult.Success(serverId ?: java.util.UUID.randomUUID().toString())
                    },
                    onFailure = { SendResult.Failed(SendError.NETWORK) }
                )
            } catch (e: Exception) {
                SendResult.Failed(SendError.NETWORK)
            }
        }
    }

    private suspend fun fetchRecipientIdentityKey(recipientUserId: String): ByteArray? {
        return try {
            val response = apiClient?.get("/v1/keys/bundle/$recipientUserId") ?: return null
            response.fold(
                onSuccess = { json ->
                    val devices = json["devices"] as? kotlinx.serialization.json.JsonArray
                    val device = devices?.firstOrNull()
                    val obj = device as? kotlinx.serialization.json.JsonObject
                    val ik = obj?.get("identity_key")
                        ?.let { it as? kotlinx.serialization.json.JsonPrimitive }
                        ?.content ?: return null
                    val ikBytes = CryptoHelper.base64UrlDecode(ik)
                    NativeSessionManager.setIdentityKey(recipientUserId, ikBytes)
                    ikBytes
                },
                onFailure = { null }
            )
        } catch (_: Exception) {
            null
        }
    }

    suspend fun sendMediaMessage(
        conversationId: String, recipientUserId: String,
        fileUri: Uri, mimeType: String
    ): SendResult = sendFileMessage(conversationId, recipientUserId, fileUri, "file", mimeType)

    suspend fun sendFileMessage(
        conversationId: String, recipientUserId: String,
        fileUri: Uri, fileName: String, mimeType: String,
        isViewOnce: Boolean = false
    ): SendResult {
        checkInit()
        val repo = repository!!

        return withContext(Dispatchers.Default) {
            try {
                val ctx = AppConfig.applicationContext ?: return@withContext SendResult.Failed(SendError.NETWORK)
                val fileBytes = ctx.contentResolver.openInputStream(fileUri)?.use { it.readBytes() }
                    ?: return@withContext SendResult.Failed(SendError.NETWORK)

                val mediaKey = CryptoHelper.generateRandomKey(32)
                val encryptedData = CryptoHelper.encryptXChaCha20Poly1305(fileBytes, mediaKey)
                Arrays.fill(fileBytes, 0)

                val client = apiClient!!
                val uploadResult = client.postRaw("/v1/media/upload", encryptedData, mimeType)
                val uploadJson = uploadResult.getOrNull() ?: return@withContext SendResult.Failed(SendError.NETWORK)
                val mediaId = uploadJson["media_id"]?.jsonPrimitive?.content
                    ?: return@withContext SendResult.Failed(SendError.NETWORK)

                val selfId = SecurePreferences.getString("auth.user_id") ?: return@withContext SendResult.Failed(SendError.NETWORK)
                val envelopeId = UUID.randomUUID().toString()
                val now = System.currentTimeMillis()
                val payloadText = "📎 $fileName"

                repo.insertMessage(MessageEntity(
                    conversationId = conversationId, senderId = selfId,
                    envelopeId = envelopeId, messageType = "ENCRYPTED_MESSAGE",
                    content = payloadText, status = "sending", timestamp = now,
                    mediaKey = CryptoHelper.base64UrlEncode(mediaKey),
                    mediaMimeType = mimeType, mediaSize = fileBytes.size.toLong(),
                    isViewOnce = isViewOnce
                ))

                val encryptedMediaKey = NativeSessionManager.encryptWithSessionKey(recipientUserId, mediaKey)
                if (encryptedMediaKey == null) {
                    repo.updateMessageStatus(envelopeId, MessageStatus.FAILED)
                    return@withContext SendResult.Failed(SendError.ENCRYPTION_FAILED)
                }
                Arrays.fill(mediaKey, 0)

                val mediaPayload = "$mediaId:${CryptoHelper.base64UrlEncode(encryptedMediaKey)}"
                val encrypted = NativeSessionManager.encryptMessage(recipientUserId, mediaPayload.encodeToByteArray())
                if (encrypted == null) {
                    repo.updateMessageStatus(envelopeId, MessageStatus.FAILED)
                    return@withContext SendResult.Failed(SendError.ENCRYPTION_FAILED)
                }

                client.post("/v1/messages/send", buildJsonObject {
                    put("recipient_user_id", recipientUserId)
                    put("message_type", "ENCRYPTED_MESSAGE")
                    put("payload", CryptoHelper.base64UrlEncode(encrypted.payload))
                })
                repo.updateMessageStatus(envelopeId, MessageStatus.SENT)
                SendResult.Success(envelopeId)
            } catch (e: Exception) {
                SendResult.Failed(SendError.NETWORK)
            }
        }
    }

    suspend fun sendReaction(messageId: String, emoji: String, conversationId: String): Result<Unit> {
        checkInit()
        val client = apiClient!!
        return withContext(Dispatchers.Default) {
            try {
                client.put("/v1/reactions/$messageId", buildJsonObject {
                    put("emoji", emoji)
                    put("conversation_id", conversationId)
                })
                    .fold(onSuccess = { Result.success(Unit) }, onFailure = { Result.failure(it) })
            } catch (e: Exception) { Result.failure(e) }
        }
    }

    suspend fun sendDeliveryReceipt(envelopeId: String, senderUserId: String) {
        checkInit()
        val msg = repository?.getMessage(envelopeId)
        val ts = msg?.timestamp ?: System.currentTimeMillis()
        val contentBytes = MessageProtobufHelper.buildReceiptContent(
            timestamps = listOf(ts),
            type = MessageProtobufHelper.ReceiptType.DELIVERY
        )
        val encrypted = NativeSessionManager.encryptMessage(senderUserId, contentBytes) ?: return
        scope?.launch {
            try {
                apiClient?.post("/v1/messages/send", buildJsonObject {
                    put("recipient_user_id", kotlinx.serialization.json.JsonPrimitive(senderUserId))
                    put("message_type", kotlinx.serialization.json.JsonPrimitive("ENCRYPTED_MESSAGE"))
                    put("payload", kotlinx.serialization.json.JsonPrimitive(CryptoHelper.base64UrlEncode(encrypted.payload)))
                })
            } catch (e: Exception) { android.util.Log.w("Enchant", "receipt send failed") }
        }
    }

    suspend fun sendReadReceipt(envelopeId: String, senderUserId: String) {
        checkInit()
        val msg = repository?.getMessage(envelopeId)
        val ts = msg?.timestamp ?: System.currentTimeMillis()
        val contentBytes = MessageProtobufHelper.buildReceiptContent(
            timestamps = listOf(ts),
            type = MessageProtobufHelper.ReceiptType.READ
        )
        val encrypted = NativeSessionManager.encryptMessage(senderUserId, contentBytes) ?: return
        scope?.launch {
            try {
                apiClient?.post("/v1/messages/send", buildJsonObject {
                    put("recipient_user_id", kotlinx.serialization.json.JsonPrimitive(senderUserId))
                    put("message_type", kotlinx.serialization.json.JsonPrimitive("ENCRYPTED_MESSAGE"))
                    put("payload", kotlinx.serialization.json.JsonPrimitive(CryptoHelper.base64UrlEncode(encrypted.payload)))
                })
            } catch (e: Exception) { android.util.Log.w("Enchant", "receipt send failed") }
        }
    }

    suspend fun sendTypingIndicator(recipientUserId: String, isTyping: Boolean) {
        checkInit()
        val now = System.currentTimeMillis()
        if (isTyping && now - lastTypingTs < 3000) return
        if (isTyping) lastTypingTs = now

        val contentBytes = MessageProtobufHelper.buildTypingContent(isTyping)
        val encrypted = NativeSessionManager.encryptMessage(recipientUserId, contentBytes) ?: return

        scope?.launch {
            try {
                apiClient?.post("/v1/messages/send", buildJsonObject {
                    put("recipient_user_id", recipientUserId)
                    put("message_type", "ENCRYPTED_MESSAGE")
                    put("payload", CryptoHelper.base64UrlEncode(encrypted.payload))
                })
            } catch (e: Exception) { android.util.Log.w("Enchant", "silent: ${e.message}") }
        }

        if (isTyping) {
            typingJob?.cancel()
            val currentScope = scope ?: return
            typingJob = currentScope.launch {
                delay(5000)
                sendTypingIndicator(recipientUserId, false)
            }
        } else {
            typingJob?.cancel()
            typingJob = null
        }
    }

    suspend fun editMessage(originalEnvelopeId: String, newPlaintext: ByteArray, recipientUserId: String): Result<Unit> {
        checkInit()
        val repo = repository!!
        return withContext(Dispatchers.Default) {
            try {
                val msg = repo.getMessage(originalEnvelopeId)
                    ?: return@withContext Result.failure(Exception("Message not found"))

                val newEnvelopeId = UUID.randomUUID().toString()
                val encrypted = NativeSessionManager.encryptMessage(recipientUserId, newPlaintext)
                    ?: return@withContext Result.failure(Exception("Encryption failed"))

                apiClient!!.put("/v1/messages/$originalEnvelopeId", buildJsonObject {
                    put("new_envelope_id", newEnvelopeId)
                    put("message_type", "ENCRYPTED_MESSAGE")
                    put("payload", org.enchant.core.crypto.CryptoHelper.base64UrlEncode(encrypted.payload))
                })

                repo.updateMessageContent(originalEnvelopeId, newPlaintext.decodeToString())
                repo.updateEditEnvelopeId(originalEnvelopeId, newEnvelopeId)
                repo.updateEditedAt(originalEnvelopeId, System.currentTimeMillis())
                Result.success(Unit)
            } catch (e: Exception) { Result.failure(e) }
        }
    }

    suspend fun deleteForEveryone(envelopeId: String, recipientUserId: String): Result<Unit> {
        checkInit()
        val repo = repository!!
        return withContext(Dispatchers.Default) {
            try {
                val targetTs = System.currentTimeMillis()
                val contentBytes = MessageProtobufHelper.buildDeleteContent(targetTimestamp = targetTs)
                val encrypted = NativeSessionManager.encryptMessage(recipientUserId, contentBytes)
                    ?: return@withContext Result.failure(Exception("Encryption failed"))
                apiClient!!.post("/v1/messages/send", buildJsonObject {
                    put("recipient_user_id", kotlinx.serialization.json.JsonPrimitive(recipientUserId))
                    put("message_type", kotlinx.serialization.json.JsonPrimitive("ENCRYPTED_MESSAGE"))
                    put("payload", kotlinx.serialization.json.JsonPrimitive(CryptoHelper.base64UrlEncode(encrypted.payload)))
                })
                repo.markMessageDeleted(envelopeId)
                Result.success(Unit)
            } catch (e: Exception) { Result.failure(e) }
        }
    }

    suspend fun deleteMessageOnServer(envelopeId: String): Result<Unit> {
        checkInit()
        return withContext(Dispatchers.Default) {
            try {
                apiClient!!.del("/v1/messages/$envelopeId")
                repository?.markMessageDeleted(envelopeId)
                Result.success(Unit)
            } catch (e: Exception) { Result.failure(e) }
        }
    }

    suspend fun deleteForSelf(envelopeId: String) {
        repository?.markMessageDeleted(envelopeId)
    }

    suspend fun pinMessageRequest(envelopeId: String): Result<Unit> {
        checkInit()
        return withContext(Dispatchers.Default) {
            try {
                apiClient!!.post("/v1/messages/$envelopeId/pin", buildJsonObject { })
                Result.success(Unit)
            } catch (e: Exception) { Result.failure(e) }
        }
    }

    suspend fun unpinMessageRequest(envelopeId: String): Result<Unit> {
        checkInit()
        return withContext(Dispatchers.Default) {
            try {
                apiClient!!.del("/v1/messages/$envelopeId/pin")
                Result.success(Unit)
            } catch (e: Exception) { Result.failure(e) }
        }
    }

    suspend fun forwardMessage(
        originalConversationId: String, originalEnvelopeId: String,
        targetConversationId: String, targetUserId: String
    ): SendResult {
        checkInit()
        val msg = repository!!.getMessage(originalEnvelopeId)
            ?: return SendResult.Failed(SendError.NETWORK)
        return sendMessage(
            conversationId = targetConversationId,
            recipientUserId = targetUserId,
            plaintext = msg.content.encodeToByteArray()
        )
    }

    suspend fun updateMessageStatus(envelopeId: String, status: MessageStatus) {
        repository?.updateMessageStatus(envelopeId, status)
    }
}

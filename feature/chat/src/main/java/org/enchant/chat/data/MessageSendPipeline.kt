package org.enchant.chat.data

import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.UUID
import org.enchant.core.base.AppConfig
import org.enchant.core.base.SecurePreferences
import org.enchant.core.crypto.CryptoHelper
import org.enchant.core.crypto.KeyManager
import org.enchant.core.crypto.SessionManager
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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastTypingTs = 0L
    private var typingJob: Job? = null

    fun init(client: ApiClient, repo: ConversationRepository) {
        apiClient = client
        repository = repo
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
                    return@withContext sendSealedMessage(recipientUserId, plaintext, null)
                }

                val contentBytes = MessageProtobufHelper.buildDataMessageContent(
                    body = plaintext.decodeToString(),
                    timestamp = System.currentTimeMillis()
                )

                val hasSession = SessionManager.hasSession(recipientUserId)
                val encrypted = SessionManager.encryptMessage(recipientUserId, contentBytes)
                if (encrypted == null) return@withContext SendResult.Failed(SendError.ENCRYPTION_FAILED)

                val envelopeId = UUID.randomUUID().toString()
                val now = System.currentTimeMillis()
                val selfId = SecurePreferences.getString("auth.user_id") ?: return@withContext SendResult.Failed(SendError.NETWORK)

                repo.insertMessage(MessageEntity(
                    conversationId = conversationId, senderId = selfId,
                    envelopeId = envelopeId,
                    messageType = if (hasSession) "SIGNAL_MESSAGE" else "PREKEY_MESSAGE",
                    content = plaintext.decodeToString(), status = "sending",
                    timestamp = now, replyToEnvelopeId = replyTo
                ))

                if (!ConnectivityMonitor.isOnline.value) {
                    OfflineQueue.enqueue(QueuedMessage(
                        recipientUserId = recipientUserId,
                        recipientDeviceId = encrypted.recipientDeviceId,
                        messageType = if (hasSession) "SIGNAL_MESSAGE" else "PREKEY_MESSAGE",
                        payload = encrypted.payload, senderTs = now
                    ))
                    repo.updateMessageStatus(envelopeId, MessageStatus.PENDING)
                    return@withContext SendResult.Queued(envelopeId)
                }

                val client = apiClient!!
                val payloadB64 = CryptoHelper.base64UrlEncode(encrypted.payload)
                val response = client.post("/v1/messages/send", buildJsonObject {
                    put("recipient_user_id", recipientUserId)
                    put("message_type", if (hasSession) "SIGNAL_MESSAGE" else "PREKEY_MESSAGE")
                    put("payload", payloadB64)
                    put("sender_ts", Instant.now().toString())
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
                                messageType = if (hasSession) "SIGNAL_MESSAGE" else "PREKEY_MESSAGE",
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
                val senderIdentityB64 = CryptoHelper.base64UrlEncode(identityKeyPair.publicKey)

                val contentBytes = MessageProtobufHelper.buildDataMessageContent(
                    body = plaintext.decodeToString(),
                    timestamp = System.currentTimeMillis()
                )
                val encrypted = SessionManager.encryptMessage(recipientUserId, contentBytes)
                if (encrypted == null) return@withContext SendResult.Failed(SendError.ENCRYPTION_FAILED)

                val ciphertextB64 = CryptoHelper.base64UrlEncode(encrypted.payload)
                val sealedPayload = buildJsonObject {
                    put("senderIdentity", senderIdentityB64)
                    put("ciphertext", ciphertextB64)
                }
                val sealedPayloadStr = kotlinx.serialization.json.Json.encodeToString(
                    kotlinx.serialization.json.JsonObject.serializer(), sealedPayload
                )

                val client = apiClient!!
                val response = client.postAnonymous("/v1/messages/sealed-send", buildJsonObject {
                    put("recipient_user_id", recipientUserId)
                    put("message_type", "UNIDENTIFIED_SENDER")
                    put("payload", sealedPayloadStr)
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

    suspend fun sendMediaMessage(
        conversationId: String, recipientUserId: String,
        fileUri: Uri, mimeType: String
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

                val client = apiClient!!
                val uploadResult = client.postRaw("/v1/media/upload", encryptedData, mimeType)
                val uploadJson = uploadResult.getOrNull() ?: return@withContext SendResult.Failed(SendError.NETWORK)
                val mediaId = uploadJson["media_id"]?.jsonPrimitive?.content
                    ?: return@withContext SendResult.Failed(SendError.NETWORK)

                val selfId = SecurePreferences.getString("auth.user_id") ?: return@withContext SendResult.Failed(SendError.NETWORK)
                val envelopeId = UUID.randomUUID().toString()
                val now = System.currentTimeMillis()
                val payloadText = "📎 $mimeType"

                repo.insertMessage(MessageEntity(
                    conversationId = conversationId, senderId = selfId,
                    envelopeId = envelopeId, messageType = "SIGNAL_MESSAGE",
                    content = payloadText, status = "sending", timestamp = now,
                    mediaKey = CryptoHelper.base64UrlEncode(mediaKey),
                    mediaMimeType = mimeType, mediaSize = fileBytes.size.toLong()
                ))

                val mediaPayload = "$mediaId:${CryptoHelper.base64UrlEncode(mediaKey)}"
                val encrypted = SessionManager.encryptMessage(recipientUserId, mediaPayload.encodeToByteArray())
                if (encrypted == null) {
                    repo.updateMessageStatus(envelopeId, MessageStatus.FAILED)
                    return@withContext SendResult.Failed(SendError.ENCRYPTION_FAILED)
                }

                client.post("/v1/messages/send", buildJsonObject {
                    put("recipient_user_id", recipientUserId)
                    put("message_type", "SIGNAL_MESSAGE")
                    put("payload", CryptoHelper.base64UrlEncode(encrypted.payload))
                })
                repo.updateMessageStatus(envelopeId, MessageStatus.SENT)
                SendResult.Success(envelopeId)
            } catch (e: Exception) {
                SendResult.Failed(SendError.NETWORK)
            }
        }
    }

    suspend fun sendReaction(messageId: String, emoji: String): Result<Unit> {
        checkInit()
        val client = apiClient!!
        return withContext(Dispatchers.Default) {
            try {
                client.put("/v1/reactions/$messageId", buildJsonObject { put("emoji", emoji) })
                    .fold(onSuccess = { Result.success(Unit) }, onFailure = { Result.failure(it) })
            } catch (e: Exception) { Result.failure(e) }
        }
    }

    suspend fun sendDeliveryReceipt(envelopeId: String, senderUserId: String) {
        checkInit()
        val ts = System.currentTimeMillis()
        val contentBytes = MessageProtobufHelper.buildReceiptContent(
            envelopeIds = listOf(ts.toString()),
            type = MessageProtobufHelper.ReceiptType.DELIVERY
        )
        val encrypted = SessionManager.encryptMessage(senderUserId, contentBytes) ?: return
        scope.launch {
            try {
                apiClient?.post("/v1/messages/send", buildJsonObject {
                    put("recipient_user_id", kotlinx.serialization.json.JsonPrimitive(senderUserId))
                    put("message_type", kotlinx.serialization.json.JsonPrimitive("SIGNAL_MESSAGE"))
                    put("payload", kotlinx.serialization.json.JsonPrimitive(CryptoHelper.base64UrlEncode(encrypted.payload)))
                })
            } catch (e: Exception) { android.util.Log.w("Enchant", "silent: ${e.message}") }
        }
    }

    suspend fun sendReadReceipt(envelopeId: String, senderUserId: String) {
        checkInit()
        val ts = System.currentTimeMillis()
        val contentBytes = MessageProtobufHelper.buildReceiptContent(
            envelopeIds = listOf(ts.toString()),
            type = MessageProtobufHelper.ReceiptType.READ
        )
        val encrypted = SessionManager.encryptMessage(senderUserId, contentBytes) ?: return
        scope.launch {
            try {
                apiClient?.post("/v1/messages/send", buildJsonObject {
                    put("recipient_user_id", kotlinx.serialization.json.JsonPrimitive(senderUserId))
                    put("message_type", kotlinx.serialization.json.JsonPrimitive("SIGNAL_MESSAGE"))
                    put("payload", kotlinx.serialization.json.JsonPrimitive(CryptoHelper.base64UrlEncode(encrypted.payload)))
                })
            } catch (e: Exception) { android.util.Log.w("Enchant", "silent: ${e.message}") }
        }
    }

    suspend fun sendTypingIndicator(recipientUserId: String, isTyping: Boolean) {
        checkInit()
        val now = System.currentTimeMillis()
        if (isTyping && now - lastTypingTs < 3000) return
        if (isTyping) lastTypingTs = now

        val contentBytes = MessageProtobufHelper.buildTypingContent(isTyping)
        val encrypted = SessionManager.encryptMessage(recipientUserId, contentBytes) ?: return

        scope.launch {
            try {
                apiClient?.post("/v1/messages/send", buildJsonObject {
                    put("recipient_user_id", recipientUserId)
                    put("message_type", "SIGNAL_MESSAGE")
                    put("payload", CryptoHelper.base64UrlEncode(encrypted.payload))
                })
            } catch (e: Exception) { android.util.Log.w("Enchant", "silent: ${e.message}") }
        }

        if (isTyping) {
            typingJob?.cancel()
            typingJob = scope.launch {
                delay(5000)
                sendTypingIndicator(recipientUserId, false)
            }
        } else {
            typingJob?.cancel()
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
                val encrypted = SessionManager.encryptMessage(recipientUserId, newPlaintext)
                    ?: return@withContext Result.failure(Exception("Encryption failed"))

                apiClient!!.put("/v1/messages/$originalEnvelopeId", buildJsonObject {
                    put("new_envelope_id", newEnvelopeId)
                })

                repo.updateMessageContent(originalEnvelopeId, newPlaintext.decodeToString())
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
                val encrypted = SessionManager.encryptMessage(recipientUserId, contentBytes)
                    ?: return@withContext Result.failure(Exception("Encryption failed"))
                apiClient!!.post("/v1/messages/send", buildJsonObject {
                    put("recipient_user_id", kotlinx.serialization.json.JsonPrimitive(recipientUserId))
                    put("message_type", kotlinx.serialization.json.JsonPrimitive("SIGNAL_MESSAGE"))
                    put("payload", kotlinx.serialization.json.JsonPrimitive(CryptoHelper.base64UrlEncode(encrypted.payload)))
                })
                repo.markMessageDeleted(envelopeId)
                Result.success(Unit)
            } catch (e: Exception) { Result.failure(e) }
        }
    }

    suspend fun deleteForSelf(envelopeId: String) {
        repository?.markMessageDeleted(envelopeId)
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

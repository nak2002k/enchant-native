package org.enchant.chat.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.enchant.core.crypto.CryptoHelper
import org.enchant.core.crypto.SessionManager
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
    private var initialized = false

    fun init(
        repo: ConversationRepository,
        recipients: org.enchant.core.database.dao.RecipientDao,
        client: org.enchant.core.network.ApiClient
    ) {
        repository = repo
        recipientDao = recipients
        apiClient = client
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
            }
        }
    }

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

                val encryptedPayload = org.enchant.core.network.models.SendMessageRequest(
                    recipientUserId = senderUserId,
                    messageType = "PREKEY_MESSAGE",
                    payload = CryptoHelper.base64UrlEncode(envelope.payload)
                )

                val decrypted = SessionManager.decryptMessage(senderUserId,
                    org.enchant.core.crypto.EncryptedPayload(
                        messageType = org.enchant.protos.EnvelopeProtos.Envelope.Type.PREKEY_MESSAGE,
                        payload = envelope.payload
                    )
                )

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
                        serverTs = now
                    ),
                    conversationType = "direct"
                )

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

                val plaintext = decrypted.plaintext.decodeToString()
                val now = System.currentTimeMillis()

                if (plaintext.startsWith("DELIVERY:")) {
                    val envId = plaintext.removePrefix("DELIVERY:")
                    repo.updateMessageStatus(envId.trim(), MessageStatus.DELIVERED)
                    return@withContext ProcessResult.Handled
                }

                if (plaintext.startsWith("READ:")) {
                    val envId = plaintext.removePrefix("READ:")
                    repo.updateMessageStatus(envId.trim(), MessageStatus.READ)
                    return@withContext ProcessResult.Handled
                }

                if (plaintext.startsWith("TYPING_START") || plaintext.startsWith("TYPING_STOP")) {
                    return@withContext ProcessResult.Handled
                }

                if (plaintext.startsWith("DELETE:")) {
                    val envId = plaintext.removePrefix("DELETE:")
                    repo.markMessageDeleted(envId.trim())
                    return@withContext ProcessResult.Handled
                }

                var finalContent = plaintext
                var finalMediaKey: String? = null
                var finalMediaMime: String? = null

                if (plaintext.contains(":") && plaintext.contains("==") || plaintext.contains(":") && plaintext.contains("-")) {
                    val parts = plaintext.split(":", limit = 2)
                    if (parts.size == 2 && parts[0].length == 36) {
                        finalContent = "📎 Media"
                        finalMediaKey = parts[1]
                        finalMediaMime = "application/octet-stream"
                    }
                }

                repo.insertMessageAndUpdateConversation(
                    MessageEntity(
                        conversationId = senderUserId,
                        senderId = senderUserId,
                        messageType = "SIGNAL_MESSAGE",
                        content = finalContent,
                        status = "delivered",
                        timestamp = envelope.serverTimestamp ?: now,
                        serverTs = now,
                        mediaKey = finalMediaKey,
                        mediaMimeType = finalMediaMime
                    ),
                    conversationType = "direct"
                )

                MessageSendPipeline.sendDeliveryReceipt(
                    envelopeId = envelope.envelopeId ?: "",
                    senderUserId = senderUserId
                )

                ProcessResult.Handled
            } catch (e: Exception) {
                ProcessResult.Error("Signal message processing failed: ${e.message}")
            }
        }
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

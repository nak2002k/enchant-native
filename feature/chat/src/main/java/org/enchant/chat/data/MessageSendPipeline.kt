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
        useVeil: Boolean = false
    ): SendResult {
        checkInit()
        val repo = repository!!

        return withContext(Dispatchers.Default) {
            try {
                if (plaintext.size > 64 * 1024) return@withContext SendResult.Failed(SendError.PAYLOAD_TOO_LARGE)

                // Veil (anonymous sender) is the default when enabled and the
                // recipient's identity key is known. Falls back to the normal
                // prekey/encrypted path on any failure.
                val veilEnabled = SecurePreferences.getBoolean("veil_sender_enabled", true)
                if (useVeil || veilEnabled) {
                    val recipientKey = NativeSessionManager.getIdentityKey(recipientUserId)
                        ?: fetchRecipientIdentityKey(recipientUserId)
                    if (recipientKey != null) {
                        val veilResult = sendVeiledMessage(conversationId, recipientUserId, plaintext, replyTo)
                        if (veilResult is SendResult.Success || veilResult is SendResult.Queued) {
                            return@withContext veilResult
                        }
                    }
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

                repo.insertMessageAndUpdateConversation(MessageEntity(
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
                    put("envelope_id", envelopeId)
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
                android.util.Log.e("Pipeline", "sendVeiledMessage failed: ${e.message}", e)
                SendResult.Failed(SendError.NETWORK)
            }
        }
    }

    suspend fun sendVeiledMessage(
        conversationId: String,
        recipientUserId: String,
        plaintext: ByteArray,
        replyTo: String? = null,
        insertLocally: Boolean = true
    ): SendResult {
        checkInit()
        val repo = repository!!
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

                val content = runCatching {
                    org.enchant.protos.ContentProtos.Content.parseFrom(plaintext)
                }.getOrNull()?.let { parsed ->
                    if (parsed.hasDataMessage() || parsed.hasReceiptMessage() ||
                        parsed.hasTypingMessage() || parsed.hasCallMessage() ||
                        parsed.hasNullMessage() || parsed.hasEditMessage() ||
                        parsed.hasSyncMessage() || parsed.hasStoryMessage()
                    ) parsed else null
                } ?: org.enchant.protos.ContentProtos.Content.parseFrom(
                    MessageProtobufHelper.buildDataMessageContent(
                        body = plaintext.decodeToString(),
                        timestamp = System.currentTimeMillis()
                    )
                )

                val wrapper = org.enchant.protos.SignalServiceContentProto.newBuilder()
                    .setLocalAddress(
                        org.enchant.protos.AddressProto.newBuilder()
                            .setUuid(com.google.protobuf.ByteString.copyFrom(selfId.toByteArray(Charsets.UTF_8)))
                            .build()
                    )
                    .setContent(content)
                    .build()
                val contentBytes = wrapper.toByteArray()

                // Forward secrecy: when a session (X3DH + double ratchet)
                // exists or can be started, the content rides the session
                // ciphertext INSIDE the Veil seal (Signal's sealed sender
                // pattern) so long-term-key compromise can't decrypt past
                // messages. Falls back to the direct seal when no session
                // can be established.
                val sessionEncrypted = runCatching {
                    NativeSessionManager.encryptMessage(recipientUserId, contentBytes)
                }.getOrNull()
                val sealInput = sessionEncrypted?.payload ?: contentBytes

                val veiledPayload = org.enchant.core.crypto.VeilSender.encryptVeiled(
                    recipientPublicKey = recipientPublicKey,
                    senderIdentityPrivate = identityKeyPair.privateKey,
                    senderIdentityPublic = identityKeyPair.publicKey,
                    message = sealInput
                )

                val envelopeId = UUID.randomUUID().toString()
                val now = System.currentTimeMillis()
                val replyToken = UUID.randomUUID().toString()

                if (insertLocally) {
                    repo.insertMessageAndUpdateConversation(MessageEntity(
                        conversationId = conversationId, senderId = selfId,
                        envelopeId = envelopeId,
                        messageType = "ENCRYPTED_MESSAGE",
                        content = plaintext.decodeToString(), status = "sending",
                        timestamp = now, replyToEnvelopeId = replyTo
                    ))
                }

                if (!ConnectivityMonitor.isOnline.value) {
                    OfflineQueue.enqueue(QueuedMessage(
                        recipientUserId = recipientUserId,
                        recipientDeviceId = "",
                        messageType = "UNIDENTIFIED_SENDER",
                        payload = veiledPayload, senderTs = now
                    ))
                    repo.updateMessageStatus(envelopeId, MessageStatus.PENDING)
                    return@withContext SendResult.Queued(envelopeId)
                }

                val ciphertextB64 = CryptoHelper.base64UrlEncode(veiledPayload)

                val client = apiClient!!
                val response = client.postAnonymous("/v1/messages/sealed-send", buildJsonObject {
                    put("recipient_user_id", recipientUserId)
                    put("message_type", "UNIDENTIFIED_SENDER")
                    put("payload", ciphertextB64)
                    put("reply_token", replyToken)
                    put("sender_ts", now.toString())
                })

                response.fold(
                    onSuccess = { json ->
                        val ids = json["envelope_ids"]?.jsonArray
                        val serverId = ids?.firstOrNull()?.jsonPrimitive?.content ?: envelopeId
                        repo.updateMessageStatus(envelopeId, MessageStatus.SENT)
                        SendResult.Success(serverId)
                    },
                    onFailure = {
                        repo.updateMessageStatus(envelopeId, MessageStatus.FAILED)
                        SendResult.Failed(SendError.NETWORK)
                    }
                )
            } catch (e: Exception) {
                android.util.Log.e("Pipeline", "sendVeiledMessage failed: ${e.message}", e)
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

    /**
     * Send a group message: seal with the group sender key and fan out one
     * ciphertext to every member via the normal message path.
     */
    suspend fun sendGroupMessage(
        groupId: String,
        members: List<String>,
        plaintext: ByteArray,
        replyTo: String? = null
    ): SendResult {
        checkInit()
        val repo = repository!!
        var envelopeId = ""
        return withContext(Dispatchers.Default) {
            try {
                if (plaintext.size > 64 * 1024) return@withContext SendResult.Failed(SendError.PAYLOAD_TOO_LARGE)
                val selfId = SecurePreferences.getString("auth.user_id")
                    ?: return@withContext SendResult.Failed(SendError.NETWORK)

                val groupIdBytes = groupId.toByteArray(Charsets.UTF_8)
                val groupConversationId = groupId
                envelopeId = UUID.randomUUID().toString()
                val now = System.currentTimeMillis()
                repo.insertMessageAndUpdateConversation(MessageEntity(
                    conversationId = groupConversationId, senderId = selfId,
                    envelopeId = envelopeId,
                    messageType = "GROUP_MESSAGE",
                    content = plaintext.decodeToString(), status = "sending",
                    timestamp = now, replyToEnvelopeId = replyTo
                ), conversationType = "group")

                // Reliable group path: the content carries the group context
                // (groupV2 masterKey) and is Veil-sealed to each member — the
                // same proven machinery as 1:1 messaging. The receiver routes
                // it into the group conversation by the masterKey.
                val targets = if (members.isNotEmpty()) members else fetchGroupMembers(groupId)
                val recipients = targets.filter { it != selfId }
                if (recipients.isEmpty()) {
                    repo.updateMessageStatus(envelopeId, MessageStatus.FAILED)
                    return@withContext SendResult.Failed(SendError.KEY_BUNDLE_MISSING)
                }
                val content = org.enchant.protos.ContentProtos.Content.parseFrom(
                    MessageProtobufHelper.buildDataMessageContent(
                        body = plaintext.decodeToString(),
                        timestamp = now,
                        groupMasterKey = groupIdBytes
                    )
                )
                var allOk = true
                recipients.forEach { memberId ->
                    val result = runCatching {
                        sendVeiledMessage(
                            conversationId = groupConversationId,
                            recipientUserId = memberId,
                            plaintext = content.toByteArray(),
                            insertLocally = false
                        )
                    }.getOrNull()
                    if (result !is SendResult.Success) allOk = false
                }

                repo.updateMessageStatus(envelopeId, if (allOk) MessageStatus.SENT else MessageStatus.PENDING)
                if (allOk) SendResult.Success(envelopeId) else SendResult.Queued(envelopeId)
            } catch (e: Exception) {
                // Never leave the message stuck in "sending".
                if (envelopeId.isNotEmpty()) {
                    runCatching { repo.updateMessageStatus(envelopeId, MessageStatus.FAILED) }
                }
                SendResult.Failed(SendError.NETWORK)
            }
        }
    }

    /**
     * Broadcast the group sender-key distribution message to all members so
     * they can build the sender's chain state. Called after group creation or
     * after members are added.
     */
    suspend fun sendGroupSenderKeyDistribution(
        groupId: String,
        members: List<String>
    ): SendResult {
        checkInit()
        return withContext(Dispatchers.Default) {
            try {
                val selfId = SecurePreferences.getString("auth.user_id")
                    ?: return@withContext SendResult.Failed(SendError.NETWORK)
                val identity = org.enchant.core.crypto.KeyManager.getIdentityKeyPair()
                    ?: return@withContext SendResult.Failed(SendError.ENCRYPTION_FAILED)
                val distribution = org.enchant.core.crypto.GroupCipherManager.createDistribution(groupId, selfId)
                    ?: return@withContext SendResult.Failed(SendError.ENCRYPTION_FAILED)

                val groupIdBytes = groupId.toByteArray(Charsets.UTF_8)
                val signingPublic = org.enchant.core.crypto.CryptoPrimitives.ed25519PubFromSeed(identity.privateKey)
                val wirePayload = groupIdBytes + signingPublic + distribution
                val payloadB64 = CryptoHelper.base64UrlEncode(wirePayload)
                val client = apiClient!!
                val now = System.currentTimeMillis()

                var allOk = true
                members.filter { it != selfId }.forEach { memberId ->
                    val result = client.post("/v1/messages/send", buildJsonObject {
                        put("recipient_user_id", memberId)
                        put("message_type", "GROUP_SENDER_KEY")
                        put("payload", payloadB64)
                        put("sender_ts", now.toString())
                        put("envelope_id", UUID.randomUUID().toString())
                    })
                    if (result.isFailure) allOk = false
                }
                if (allOk) SendResult.Success(UUID.randomUUID().toString()) else SendResult.Failed(SendError.NETWORK)
            } catch (e: Exception) {
                SendResult.Failed(SendError.NETWORK)
            }
        }
    }

    private suspend fun fetchGroupMembers(groupId: String): List<String> {
        return try {
            val response = apiClient?.get("/v1/groups/$groupId/members") ?: return emptyList()
            response.getOrNull()?.get("members")?.jsonArray?.mapNotNull { m ->
                (m as? kotlinx.serialization.json.JsonObject)?.get("user_id")?.jsonPrimitive?.content
            } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Ask a group member to re-broadcast their sender-key distribution. */
    suspend fun requestGroupSenderKey(groupId: String, senderUserId: String) {
        checkInit()
        val client = apiClient ?: return
        val now = System.currentTimeMillis()
        val groupIdBytes = groupId.toByteArray(Charsets.UTF_8)
        val payloadB64 = CryptoHelper.base64UrlEncode(groupIdBytes)
        runCatching {
            client.post("/v1/messages/send", buildJsonObject {
                put("recipient_user_id", senderUserId)
                put("message_type", "GROUP_SENDER_KEY_REQUEST")
                put("payload", payloadB64)
                put("sender_ts", now.toString())
                put("envelope_id", UUID.randomUUID().toString())
            })
        }
    }

    /** Re-broadcast the CURRENT sender-key distribution to the requesting member. */
    suspend fun handleGroupSenderKeyRequest(groupId: String, requesterId: String) {
        checkInit()
        val client = apiClient ?: return
        val selfId = SecurePreferences.getString("auth.user_id") ?: return
        val identity = org.enchant.core.crypto.KeyManager.getIdentityKeyPair() ?: return
        val distribution = org.enchant.core.crypto.GroupCipherManager.createDistribution(groupId, selfId) ?: return
        val signingPublic = org.enchant.core.crypto.CryptoPrimitives.ed25519PubFromSeed(identity.privateKey)
        val content = org.enchant.protos.ContentProtos.Content.newBuilder()
            .setSenderKeyDistributionMessage(
                com.google.protobuf.ByteString.copyFrom(groupId.toByteArray(Charsets.UTF_8) + signingPublic + distribution)
            )
            .build()
        runCatching {
            sendVeiledMessage(
                conversationId = requesterId,
                recipientUserId = requesterId,
                plaintext = content.toByteArray(),
                insertLocally = false
            )
        }
    }

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
                android.util.Log.w("Pipeline", "FILE: reading $fileUri")
                val fileBytes = ctx.contentResolver.openInputStream(fileUri)?.use { it.readBytes() }
                    ?: return@withContext SendResult.Failed(SendError.NETWORK)
                android.util.Log.w("Pipeline", "FILE: read ${fileBytes.size} bytes")

                val mediaKey = CryptoHelper.generateRandomKey(32)
                val encryptedData = CryptoHelper.encryptXChaCha20Poly1305(fileBytes, mediaKey)
                Arrays.fill(fileBytes, 0)

                val client = apiClient!!
                android.util.Log.w("Pipeline", "FILE: uploading ${encryptedData.size} bytes")
                val uploadResult = client.postRaw("/v1/media/upload", encryptedData, mimeType)
                val uploadJson = uploadResult.getOrNull() ?: run {
                    android.util.Log.w("Pipeline", "FILE: upload FAILED ${uploadResult.exceptionOrNull()?.message}")
                    return@withContext SendResult.Failed(SendError.NETWORK)
                }
                val mediaId = uploadJson["media_id"]?.jsonPrimitive?.content
                    ?: run {
                        android.util.Log.w("Pipeline", "FILE: upload OK but no media_id: $uploadJson")
                        return@withContext SendResult.Failed(SendError.NETWORK)
                    }
                android.util.Log.w("Pipeline", "FILE: uploaded media_id=$mediaId")

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
                    mediaId = mediaId,
                    isViewOnce = isViewOnce
                ))

                // Signal pattern: the attachment rides the Content proto
                // (AttachmentPointer: cdnKey=mediaId, key=mediaKey) inside a
                // Veil-sealed message — the same proven path as text.
                val content = org.enchant.protos.ContentProtos.Content.parseFrom(
                    MessageProtobufHelper.buildDataMessageContent(
                        body = if (isViewOnce) "🕶️ 📎 $fileName" else "📎 $fileName",
                        timestamp = now,
                        attachment = org.enchant.protos.AttachmentPointerProtos.AttachmentPointer.newBuilder()
                            .setCdnKey(mediaId)
                            .setKey(com.google.protobuf.ByteString.copyFrom(mediaKey))
                            .setContentType(mimeType)
                            .setFileName(fileName)
                            .setSize(fileBytes.size)
                            .build()
                    )
                )
                val result = sendVeiledMessage(
                    conversationId = conversationId,
                    recipientUserId = recipientUserId,
                    plaintext = content.toByteArray(),
                    insertLocally = false
                )
                repo.updateMessageStatus(
                    envelopeId,
                    if (result is SendResult.Success) MessageStatus.SENT else MessageStatus.PENDING
                )
                SendResult.Success(envelopeId)
            } catch (e: Exception) {
                android.util.Log.e("Pipeline", "sendFileMessage failed: ${e.message}", e)
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
        // Typing frames are E2EE messages. Without an established session
        // there is nothing to encrypt with — sending one would establish a
        // session client-side that the peer can never decrypt (they never
        // received the prekey). Skip until a real message creates the session.
        if (!NativeSessionManager.hasSession(recipientUserId)) return
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

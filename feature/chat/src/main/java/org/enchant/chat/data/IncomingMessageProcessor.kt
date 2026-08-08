package org.enchant.chat.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonArray
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

/**
 * Cross-screen registry of peers currently typing. Written by the incoming
 * processor when a typing message arrives; read by ConversationViewModel to
 * show the "typing…" indicator in the matching conversation.
 */
object TypingRegistry {
    private val _typingUsers = kotlinx.coroutines.flow.MutableStateFlow<Set<String>>(emptySet())
    val typingUsers: kotlinx.coroutines.flow.StateFlow<Set<String>> = _typingUsers.asStateFlow()

    fun set(userId: String, isTyping: Boolean) {
        _typingUsers.value = if (isTyping) _typingUsers.value + userId else _typingUsers.value - userId
    }

    fun clear(userId: String) = set(userId, false)
}

/**
 * The wire protocol base64url-encodes the E2EE payload (see MessageSendPipeline),
 * so an incoming Envelope payload must be decoded before the native decrypt layer
 * can parse the raw 76-byte prekey header / envelope. Falls back to the raw bytes
 * when the payload is not valid base64url (e.g. WS-direct raw frames).
 */
private fun decodeWirePayload(payload: ByteArray): ByteArray {
    if (payload.isEmpty()) return payload
    return runCatching {
        CryptoHelper.base64UrlDecode(payload.decodeToString())
    }.getOrElse { payload }
}

/**
 * The MRS server builds delivery/read receipts with a custom protobuf that
 * doesn't match the app's Content schema. Live wire capture:
 *   Content{ field2 = Receipt{ field2 = string(original_envelope_id) } }
 * Just extract the innermost 36-char UUID string.
 */
private fun extractReceiptEnvelopeId(payload: ByteArray): String? {
    val ascii = payload.decodeToString()
    val match = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
        .find(ascii)
    return match?.value
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
    private val processedGroupIterations = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    // De-dupe: the server redelivers un-acked envelopes; concurrent processing
    // of the same envelope races (e.g. the X3DH one-time prekey gets consumed
    // by the first successful decrypt, then retries fail). Skip envelopes
    // already being processed or already persisted.
    private val inFlightEnvelopes = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    @Volatile private var lastPendingFetchMs = 0L
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
        // Persist immediately: the partial buffer must never linger unflushed
        // (a message that is decrypted but never written is lost forever).
        flushBuffer()
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
        android.util.Log.e("GroupCipher", "INCOMING type=${envelope.messageType} env=${envelope.envelopeId} len=${envelope.payload.size}")

        return withContext(Dispatchers.Default) {
            try {
                val envKey = envelope.envelopeId
                if (envKey != null && !inFlightEnvelopes.add(envKey)) {
                    android.util.Log.w("IncomingMsg", "skip in-flight envelope $envKey")
                    return@withContext ProcessResult.Handled
                }
                if (envKey != null && messageDao?.getByEnvelopeId(envKey) != null) {
                    inFlightEnvelopes.remove(envKey)
                    return@withContext ProcessResult.Handled
                }
                if (envelope.messageType == "UNIDENTIFIED_SENDER") {
                    return@withContext processVeiledSender(envelope, repo)
                }

                if (envelope.sealed) {
                    return@withContext processVeiledSender(envelope, repo)
                }

                if (envelope.messageType == "RECEIPT" || envelope.messageType == "DELIVERY_RECEIPT" ||
                    envelope.messageType == "READ_RECEIPT") {
                    return@withContext processReceiptEnvelope(envelope)
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

                if (envelope.messageType == "GROUP_SENDER_KEY") {
                    return@withContext processGroupSenderKey(envelope, senderId)
                }

                if (envelope.messageType == "GROUP_SENDER_KEY_REQUEST") {
                    return@withContext processGroupSenderKeyRequest(envelope, senderId)
                }

                if (envelope.messageType == "GROUP_MESSAGE") {
                    return@withContext processGroupMessage(envelope, senderId, repo)
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

    /**
     * Standalone delivery/read receipt envelopes. The server routes the
     * original message's server timestamp back to the sender; look up the
     * local envelope by that timestamp and flip SENT -> DELIVERED / READ.
     */
    private suspend fun processReceiptEnvelope(envelope: IncomingEnvelope): ProcessResult {
        return withContext(Dispatchers.Default) {
            try {
                val raw = decodeWirePayload(envelope.payload)
                val parsed = runCatching {
                    MessageProtobufHelper.parseContent(raw)
                }.getOrNull() as? MessageProtobufHelper.ParsedContent.Receipt
                if (parsed != null) {
                    val status = when (parsed.type) {
                        MessageProtobufHelper.ReceiptType.READ -> MessageStatus.READ
                        else -> MessageStatus.DELIVERED
                    }
                    if (!parsed.envelopeId.isNullOrBlank()) {
                        repository!!.updateMessageStatus(parsed.envelopeId!!, status)
                    } else {
                        parsed.timestamps.forEach { ts ->
                            val eid = messageDao?.getEnvelopeIdByServerTs(ts) ?: ts.toString()
                            repository!!.updateMessageStatus(eid, status)
                        }
                    }
                    return@withContext ProcessResult.Handled
                }
                // Server-native receipt: the MRS server builds receipts with a
                // custom proto. Wire format (verified from live captures):
                //   Content{ field2 = Receipt{ field2 = string(original_env_id) } }
                // The app's Content schema has no matching field, so walk the
                // protobuf and pull the 36-char UUID out of the innermost string.
                val envId = extractReceiptEnvelopeId(raw)
                if (!envId.isNullOrBlank()) {
                    repository!!.updateMessageStatus(envId, MessageStatus.DELIVERED)
                    ProcessResult.Handled
                } else {
                    ProcessResult.Error("Receipt parse failed")
                }
            } catch (e: Exception) {
                ProcessResult.Error("Receipt processing failed: ${e.message}")
            }
        }
    }

    private suspend fun processPreKeyMessage(
        envelope: IncomingEnvelope, senderUserId: String, repo: ConversationRepository
    ): ProcessResult {
        return withContext(Dispatchers.Default) {
            try {
                val envId = envelope.envelopeId
                if (envId != null && messageDao?.getByEnvelopeId(envId) != null) {
                    // Duplicate delivery (WS push + pending poll). The envelope was
                    // already decrypted and stored; the retry only fails because the
                    // one-time prekey was consumed by the first attempt.
                    return@withContext ProcessResult.Handled
                }
                android.util.Log.d("IncomingMsg", "processPreKeyMessage: from=$senderUserId ctLen=${envelope.payload.size} envId=$envId")
                val wirePayload = decodeWirePayload(envelope.payload)
                android.util.Log.d("IncomingMsg", "processPreKeyMessage: decoded ctLen=${wirePayload.size} from=$senderUserId")
                val decrypted = NativeSessionManager.decryptPreKeyMessage(senderUserId, wirePayload)

                if (decrypted == null) {
                    // Auto-recovery: the sender may have re-registered, rotated
                    // their keys, or our session cache is stale. Drop the stale
                    // session so the NACK triggers a redelivery that lands on a
                    // freshly-established session.
                    android.util.Log.e("IncomingMsg", "processPreKeyMessage FAILED: could not establish session from=$senderUserId ctLen=${envelope.payload.size}")
                    runCatching { org.enchant.core.crypto.VeilSession.get().deleteSession(senderUserId) }
                    org.enchant.chat.data.MessageSendPipeline.markSessionReset(senderUserId)
                    return@withContext ProcessResult.Error("Failed to establish session")
                }

                android.util.Log.d("IncomingMsg", "processPreKeyMessage OK: from=$senderUserId ptLen=${decrypted.plaintext.size} isNew=${decrypted.isNewSession}")

                val now = System.currentTimeMillis()
                val parsed = MessageProtobufHelper.parseContent(decrypted.plaintext)

                return@withContext when (parsed) {
                    is MessageProtobufHelper.ParsedContent.SenderKeyDistribution -> {
                        val dist = parsed.distribution
                        android.util.Log.e("GroupCipher", "SKDIST group=${parsed.groupId} sender=$senderUserId len=${dist.size}")
                        if (dist.size >= 32) {
                            val signingPublic = dist.copyOfRange(0, 32)
                            val ok = org.enchant.core.crypto.GroupCipherManager.processDistribution(
                                parsed.groupId, senderUserId, signingPublic, dist.copyOfRange(32, dist.size)
                            )
                            android.util.Log.e("GroupCipher", "SKDIST processed ok=$ok")
                            if (!ok) return@withContext ProcessResult.Error("Distribution processing failed")
                            runCatching { fetchPendingMessages() }
                            ProcessResult.Handled
                        } else {
                            ProcessResult.Error("Distribution payload too short")
                        }
                    }
                    is MessageProtobufHelper.ParsedContent.DataMessage -> {
                        // Group-context messages (per-member encrypted, group
                        // V1 style) route into the group conversation.
                        val groupId = parsed.groupMasterKey?.let { mk ->
                            String(mk.let { b -> b.takeWhile { it != 0.toByte() }.toByteArray() }, Charsets.UTF_8)
                                .takeIf { it.isNotBlank() }
                        }
                        val convId = groupId ?: senderUserId
                        val convType = if (groupId != null) "group" else "direct"
                        val replyEnvId = parsed.replyToEnvelopeId
                            ?: parsed.replyToTimestamp?.let { ts ->
                                messageDao?.getEnvelopeIdByServerTs(ts)
                            }
                        repo.insertMessageAndUpdateConversation(
                            MessageEntity(
                                conversationId = convId,
                                senderId = senderUserId,
                                messageType = "ENCRYPTED_MESSAGE",
                                content = parsed.body,
                                status = "delivered",
                                timestamp = envelope.senderTimestamp ?: envelope.serverTimestamp ?: now,
                                serverTs = now,
                                envelopeId = envelope.envelopeId,
                                replyToEnvelopeId = replyEnvId
                            ),
                            conversationType = convType
                        )
                        applyDisappearTimer(convId, envelope.envelopeId, envelope.serverTimestamp)
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
                        if (!parsed.envelopeId.isNullOrBlank()) {
                            repo.updateMessageStatus(parsed.envelopeId!!, status)
                        } else {
                            parsed.timestamps.forEach { ts ->
                                val eid = messageDao?.getEnvelopeIdByServerTs(ts) ?: ts.toString()
                                repo.updateMessageStatus(eid, status)
                            }
                        }
                        ProcessResult.Handled
                    }
                    is MessageProtobufHelper.ParsedContent.Typing -> {
                        TypingRegistry.set(senderUserId, parsed.isTyping)
                        ProcessResult.Handled
                    }
                    is MessageProtobufHelper.ParsedContent.Delete -> {
                        if (parsed.targetTimestamp > 0) {
                            messageDao?.getEnvelopeIdByServerTs(parsed.targetTimestamp)?.let { eid ->
                                messageDao?.markDeleted(eid)
                            }
                        }
                        ProcessResult.Handled
                    }
                    is MessageProtobufHelper.ParsedContent.Null -> ProcessResult.Handled
                    is MessageProtobufHelper.ParsedContent.Unknown -> ProcessResult.Error("Unknown content type")
                }
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
                val envId = envelope.envelopeId
                if (envId != null && messageDao?.getByEnvelopeId(envId) != null) {
                    inFlightEnvelopes.remove(envId)
                    return@withContext ProcessResult.Handled
                }
                val wirePayload = decodeWirePayload(envelope.payload)
                var decrypted = NativeSessionManager.decryptMessage(senderUserId, wirePayload)

                if (decrypted == null) {
                    // Fall back to the prekey path: a freshly-established
                    // session's first reply may arrive labeled ENCRYPTED.
                    decrypted = NativeSessionManager.decryptPreKeyMessage(senderUserId, wirePayload)
                }

                if (decrypted == null) {
                    // The unsealed session-opening message may still carry the
                    // Veil seal (sender identity inside); recover the sender's
                    // identity from the seal, then decrypt the inner session
                    // ciphertext (X3DH prekey or double-ratchet).
                    val veilDecrypted = runCatching {
                        val identityKeyPair = org.enchant.core.crypto.KeyManager.getIdentityKeyPair()
                        if (identityKeyPair == null) null
                        else org.enchant.core.crypto.VeilSender.decryptVeiled(
                            recipientPrivateKey = identityKeyPair.privateKey,
                            recipientPublicKey = identityKeyPair.publicKey,
                            sealedPayload = decodeWirePayload(envelope.payload)
                        )?.let { pair ->
                            val senderForSession = NativeSessionManager.getUserIdForIdentityKey(pair.first)
                                ?: senderUserId
                            NativeSessionManager.decryptMessage(senderForSession, pair.second)
                                ?: NativeSessionManager.decryptPreKeyMessage(senderForSession, pair.second)
                        }
                    }.getOrNull()
                    decrypted = veilDecrypted
                }

                if (decrypted == null) {
                    android.util.Log.w("IncomingMsg", "Decryption failed for sender: $senderUserId ctLen=${envelope.payload.size}")
                    return@withContext ProcessResult.Error("Decryption failed")
                }

                val now = System.currentTimeMillis()
                val parsed = MessageProtobufHelper.parseContent(decrypted.plaintext)

                return@withContext when (parsed) {
                    is MessageProtobufHelper.ParsedContent.SenderKeyDistribution -> {
                        // Signal pattern: the sender-key distribution rides
                        // the 1:1 session; processing it builds the sender's
                        // group chain so subsequent GROUP_MESSAGE frames
                        // decrypt. No user-visible message.
                        val dist = parsed.distribution
                        if (dist.size >= 32) {
                            val signingPublic = dist.copyOfRange(0, 32)
                            val distBytes = dist.copyOfRange(32, dist.size)
                            val ok = org.enchant.core.crypto.GroupCipherManager.processDistribution(
                                parsed.groupId, senderUserId, signingPublic, distBytes
                            )
                            if (!ok) {
                                android.util.Log.w("IncomingMsg", "Distribution processing failed for sender=$senderUserId group=${parsed.groupId}")
                                return@withContext ProcessResult.Error("Distribution processing failed")
                            }
                            runCatching { fetchPendingMessages() }
                            ProcessResult.Handled
                        } else {
                            ProcessResult.Error("Distribution payload too short")
                        }
                    }
                    is MessageProtobufHelper.ParsedContent.DataMessage -> {
                        val replyEnvId = parsed.replyToEnvelopeId
                            ?: parsed.replyToTimestamp?.let { ts ->
                                messageDao?.getEnvelopeIdByServerTs(ts)
                            }
                        repo.insertMessageAndUpdateConversation(
                            MessageEntity(
                                conversationId = senderUserId,
                                senderId = senderUserId,
                                messageType = "ENCRYPTED_MESSAGE",
                                content = parsed.body,
                                status = "delivered",
                                timestamp = envelope.serverTimestamp ?: now,
                                serverTs = now,
                                envelopeId = envelope.envelopeId,
                                replyToEnvelopeId = replyEnvId
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
                        if (!parsed.envelopeId.isNullOrBlank()) {
                            repo.updateMessageStatus(parsed.envelopeId!!, status)
                        } else {
                            parsed.timestamps.forEach { ts ->
                                val envId = messageDao?.getEnvelopeIdByServerTs(ts) ?: ts.toString()
                                repo.updateMessageStatus(envId, status)
                            }
                        }
                        ProcessResult.Handled
                    }
                    is MessageProtobufHelper.ParsedContent.Typing -> {
                        TypingRegistry.set(senderUserId, parsed.isTyping)
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
                val envId = envelope.envelopeId
                if (envId != null && messageDao?.getByEnvelopeId(envId) != null) {
                    return@withContext ProcessResult.Handled
                }
                val wirePayload = decodeWirePayload(envelope.payload)
                val decrypted = NativeSessionManager.decryptMessage(senderUserId, wirePayload)
                    ?: NativeSessionManager.decryptPreKeyMessage(senderUserId, wirePayload)
                    ?: return@withContext ProcessResult.Error("Decryption failed")

                val now = System.currentTimeMillis()
                val parsed = MessageProtobufHelper.parseContent(decrypted.plaintext)

                return@withContext when (parsed) {
                    is MessageProtobufHelper.ParsedContent.SenderKeyDistribution -> {
                        val dist = parsed.distribution
                        if (dist.size >= 32) {
                            val signingPublic = dist.copyOfRange(0, 32)
                            val ok = org.enchant.core.crypto.GroupCipherManager.processDistribution(
                                parsed.groupId, senderUserId, signingPublic, dist.copyOfRange(32, dist.size)
                            )
                            if (!ok) return@withContext ProcessResult.Error("Distribution processing failed")
                            runCatching { fetchPendingMessages() }
                            ProcessResult.Handled
                        } else {
                            ProcessResult.Error("Distribution payload too short")
                        }
                    }
                    is MessageProtobufHelper.ParsedContent.DataMessage -> {
                        val replyEnvId = parsed.replyToEnvelopeId
                            ?: parsed.replyToTimestamp?.let { ts ->
                                messageDao?.getEnvelopeIdByServerTs(ts)
                            }
                        repo.insertMessageAndUpdateConversation(
                            MessageEntity(
                                conversationId = senderUserId,
                                senderId = senderUserId,
                                messageType = "ENCRYPTED_MESSAGE",
                                content = parsed.body,
                                status = "delivered",
                                timestamp = envelope.serverTimestamp ?: now,
                                serverTs = now,
                                envelopeId = envelope.envelopeId,
                                replyToEnvelopeId = replyEnvId
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
                        if (!parsed.envelopeId.isNullOrBlank()) {
                            repo.updateMessageStatus(parsed.envelopeId!!, status)
                        } else {
                            parsed.timestamps.forEach { ts ->
                                val envId = messageDao?.getEnvelopeIdByServerTs(ts) ?: ts.toString()
                                repo.updateMessageStatus(envId, status)
                            }
                        }
                        ProcessResult.Handled
                    }
                    is MessageProtobufHelper.ParsedContent.Typing -> {
                        TypingRegistry.set(senderUserId, parsed.isTyping)
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
        // The wrapper is not JSON: call signaling now rides the Veil seal.
        val payload = runCatching { CryptoPrimitives.base64UrlDecode(raw) }.getOrNull()
            ?: return null
        val identity = org.enchant.core.crypto.KeyManager.getIdentityKeyPair()
            ?: return null
        val decrypted = org.enchant.core.crypto.VeilSender.decryptVeiled(
            recipientPrivateKey = identity.privateKey,
            recipientPublicKey = identity.publicKey,
            sealedPayload = payload
        ) ?: return null

        val recoveredSenderKey = decrypted.first
        val knownKey = NativeSessionManager.getIdentityKey(senderUserId)
        if (knownKey != null && !knownKey.contentEquals(recoveredSenderKey)) {
            android.util.Log.w("IncomingMsg", "Call signaling sender key mismatch for $senderUserId")
            return null
        }

        return decrypted.second.toString(Charsets.UTF_8)
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

    private suspend fun processGroupSenderKey(
        envelope: IncomingEnvelope, senderUserId: String
    ): ProcessResult {
        return withContext(Dispatchers.Default) {
            try {
                val wire = decodeWirePayload(envelope.payload)
                if (wire.size < 68) return@withContext ProcessResult.Error("Group sender key payload too short")
                val groupId = String(wire.copyOfRange(0, 36).let { b ->
                    b.takeWhile { it != 0.toByte() }.toByteArray()
                }, Charsets.UTF_8)
                val senderIdentityPublic = wire.copyOfRange(36, 68)
                val distribution = wire.copyOfRange(68, wire.size)
                val ok = org.enchant.core.crypto.GroupCipherManager.processDistribution(
                    groupId, senderUserId, senderIdentityPublic, distribution
                )
                android.util.Log.i("GroupCipher", "distribution processed for group=$groupId sender=$senderUserId ok=$ok")
                if (ok) {
                    // The chain restarted (or advanced): previously-seen
                    // iterations no longer identify messages. Clear the
                    // dedup set so re-keyed messages with the same
                    // iterations are processed instead of skipped.
                    processedGroupIterations.clear()
                    // Pull any messages that were pushed before the
                    // distribution arrived (they now decrypt), throttled.
                    val now = System.currentTimeMillis()
                    if (now - lastPendingFetchMs > 3000) {
                        lastPendingFetchMs = now
                        runCatching { fetchPendingMessages() }
                    }
                    ProcessResult.Handled
                } else {
                    ProcessResult.Error("Group sender key processing failed")
                }
            } catch (e: Exception) {
                ProcessResult.Error("Group sender key failed: ${e.message}")
            }
        }
    }

    private suspend fun processGroupSenderKeyRequest(
        envelope: IncomingEnvelope, senderUserId: String
    ): ProcessResult {
        return withContext(Dispatchers.Default) {
            try {
                val wire = decodeWirePayload(envelope.payload)
                if (wire.size < 1) return@withContext ProcessResult.Error("Bad request payload")
                val groupId = String(wire.let { b ->
                    b.takeWhile { it != 0.toByte() }.toByteArray()
                }, Charsets.UTF_8)
                org.enchant.chat.data.MessageSendPipeline.handleGroupSenderKeyRequest(groupId, senderUserId)
                ProcessResult.Handled
            } catch (e: Exception) {
                ProcessResult.Error("Group sender key request failed: ${e.message}")
            }
        }
    }

    /** Fetch undelivered messages from the server and process them (used on
     *  WS (re)connect so nothing stays stuck after a disconnect/restart). */
    suspend fun fetchPendingMessagesPublic() {
        fetchPendingMessages()
    }

    private suspend fun fetchPendingMessages() {
        withContext(Dispatchers.Default) {
            try {
                val response = apiClient?.get("/v1/messages/pending") ?: return@withContext
                response.getOrNull()?.get("messages")?.jsonArray?.forEach { raw ->
                    val bytes = raw.jsonArray.mapNotNull { it.jsonPrimitive.content.toIntOrNull() }
                        .map { it.toByte() }.toByteArray()
                    if (bytes.isEmpty()) return@forEach
                    val env = org.enchant.protos.EnvelopeProtos.Envelope.parseFrom(bytes)
                    val envelope = IncomingEnvelope(
                        envelopeId = env.envelopeId.ifEmpty { null },
                        senderUserId = env.senderUserId.ifEmpty { null },
                        senderDeviceId = env.senderDeviceId.ifEmpty { null },
                        messageType = env.messageType.ifEmpty { "ENVELOPE" },
                        payload = env.payload.toByteArray(),
                        serverTimestamp = if (env.hasServerTs()) env.serverTs else null,
                        ephemeral = env.ephemeral,
                        sealed = env.sealed,
                        replyToken = env.replyToken.ifEmpty { null },
                        requestId = null
                    )
                    processIncoming(envelope)
                }
            } catch (e: Exception) {
                android.util.Log.w("GroupCipher", "pending fetch failed: ${e.message}")
            }
        }
    }

    private suspend fun processGroupMessage(
        envelope: IncomingEnvelope, senderUserId: String, repo: ConversationRepository
    ): ProcessResult {
        return withContext(Dispatchers.Default) {
            try {
                // Dedup: the same frame may be invoked by both the WS service
                // and the list refresh. Decrypting twice advances the sender
                // key ratchet and triggers a replay error.
                val envId = envelope.envelopeId
                if (envId != null && messageDao?.getByEnvelopeId(envId) != null) {
                    return@withContext ProcessResult.Handled
                }
                val wire = decodeWirePayload(envelope.payload)
                if (wire.size <= 36) return@withContext ProcessResult.Error("Group message payload too short")
                android.util.Log.e("GroupCipher", "GMSG env=${envId} wire=${wire.size} raw=${envelope.payload.size}")
                val groupId = String(wire.copyOfRange(0, 36).let { b ->
                    b.takeWhile { it != 0.toByte() }.toByteArray()
                }, Charsets.UTF_8)
                // Wire layout: groupId(36) || ciphertext only. The sender-key
                // distribution arrives separately via the Veil-sealed path.
                val ciphertext = wire.copyOfRange(36, wire.size)
                // Serialized GroupCipherMessage: version(1) + chain_id(32) + iteration(4 LE) + ct
                val msgIter = if (ciphertext.size >= 37)
                    (ciphertext[33].toInt() and 0xFF) or ((ciphertext[34].toInt() and 0xFF) shl 8) or
                    ((ciphertext[35].toInt() and 0xFF) shl 16) or ((ciphertext[36].toInt() and 0xFF) shl 24) else -1
                val dedupKey = "$groupId|$senderUserId|$msgIter"
                // Only skip duplicates AFTER the message was stored successfully;
                // marking on failure would ack the frame and lose the message.
                if (msgIter >= 0 && processedGroupIterations.contains(dedupKey)) {
                    return@withContext ProcessResult.Handled
                }

                val rcOut = intArrayOf(0)
                android.util.Log.e("GroupCipher", "GMSG decrypt ctLen=${ciphertext.size} iter=$msgIter")
                val plaintext = org.enchant.core.crypto.GroupCipherManager.decrypt(groupId, senderUserId, ciphertext, rcOut)
                    ?: run {
                        // -9/-5: replay or legacy-format message that can never
                        // decrypt — drop it so it stops redelivering.
                        android.util.Log.e("GroupCipher", "drop rc=${rcOut[0]} ctLen=${ciphertext.size} iter=$msgIter")
                        if (rcOut[0] == -9 || rcOut[0] == -5) {
                            return@withContext ProcessResult.Handled
                        }
                        return@withContext ProcessResult.Error("Group decrypt failed for sender=$senderUserId group=$groupId")
                    }
                val content = org.enchant.protos.ContentProtos.Content.parseFrom(plaintext)
                val now = System.currentTimeMillis()
                return@withContext when {
                    content.hasDataMessage() -> {
                        if (msgIter >= 0) processedGroupIterations.add(dedupKey)
                        val dataMsg = content.dataMessage
                        repo.insertMessageAndUpdateConversation(
                            MessageEntity(
                                conversationId = groupId,
                                senderId = senderUserId,
                                messageType = "GROUP_MESSAGE",
                                content = dataMsg.body,
                                status = "delivered",
                                timestamp = envelope.senderTimestamp ?: envelope.serverTimestamp ?: now,
                                serverTs = now
                            ),
                            conversationType = "group"
                        )
                        // Delivery receipt so the sender sees the double tick.
                        runCatching {
                            org.enchant.chat.data.MessageSendPipeline.sendDeliveryReceipt(
                                envelope.envelopeId ?: "", senderUserId
                            )
                        }
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
                    else -> ProcessResult.Error("Unknown group content type")
                }
            } catch (e: Exception) {
                android.util.Log.e("GroupCipher", "group message failed: ${e.message}")
                ProcessResult.Error("Group message processing failed")
            }
        }
    }

    private suspend fun processVeiledSender(
        envelope: IncomingEnvelope, repo: ConversationRepository
    ): ProcessResult {
        return withContext(Dispatchers.Default) {
            try {
                val envId = envelope.envelopeId
                if (envId != null && messageDao?.getByEnvelopeId(envId) != null) {
                    return@withContext ProcessResult.Handled
                }
                val identityKeyPair = org.enchant.core.crypto.KeyManager.getIdentityKeyPair()
                    ?: return@withContext ProcessResult.Error("Local identity key missing")
                // The wire payload is base64url-encoded text; decode before
                // handing the veil ciphertext to the native decryptor.
                val veiledPayload = decodeWirePayload(envelope.payload)

                // SP6: try the v2 seal first — it embeds a sender certificate so
                // the recipient recovers the sender's user id + device id with
                // NO server-provided hint (full anonymity). Fall back to v1
                // (recover only the identity key) for older peers.
                var recoveredSenderKey: ByteArray
                var plaintext: ByteArray
                var sealedSenderUserId: String? = null
                val v2 = org.enchant.core.crypto.VeilSender.decryptVeiledV2(
                    recipientPrivateKey = identityKeyPair.privateKey,
                    recipientPublicKey = identityKeyPair.publicKey,
                    sealedPayload = veiledPayload
                )
                if (v2 != null) {
                    recoveredSenderKey = v2.first
                    plaintext = v2.fourth
                    sealedSenderUserId = v2.second.takeIf { it.isNotBlank() }
                    if (sealedSenderUserId != null) {
                        // Map the recovered identity key to the recovered user id
                        // so the session decrypt + attribution work without a hint.
                        NativeSessionManager.setIdentityKey(sealedSenderUserId, recoveredSenderKey)
                        if (v2.third > 0) NativeSessionManager.setPeerDeviceId(sealedSenderUserId, v2.third.toString())
                    }
                } else {
                    val v1 = org.enchant.core.crypto.VeilSender.decryptVeiled(
                        recipientPrivateKey = identityKeyPair.privateKey,
                        recipientPublicKey = identityKeyPair.publicKey,
                        sealedPayload = veiledPayload
                    ) ?: return@withContext ProcessResult.Error("Sealed decrypt failed")
                    recoveredSenderKey = v1.first
                    plaintext = v1.second
                }

                // Forward secrecy: the seal may wrap a session ciphertext
                // (X3DH + double ratchet) instead of the raw content. Detect
                // by trying the direct parse first, then the session.
                var wrapper: org.enchant.protos.SignalServiceContentProto? = null
                val parseErr = runCatching {
                    wrapper = org.enchant.protos.SignalServiceContentProto.parseFrom(plaintext)
                }.exceptionOrNull()
                if (parseErr != null) {
                    android.util.Log.e("GroupCipher", "veil wrapper parse failed: ${parseErr.message} firstBytes=${plaintext.take(8).joinToString { "%02x".format(it) }}")
                }
                val directParses = wrapper != null &&
                    (wrapper!!.hasContent() || wrapper!!.hasLocalAddress())
                android.util.Log.e("GroupCipher", "veil directParses=$directParses size=${plaintext.size} sealedSenderId=$sealedSenderUserId")

                if (!directParses) {
                    // The sender id for the session decrypt comes from the
                    // recovered sender identity key (sealed sender). If we have
                    // never seen this identity, fetch the sender's current
                    // bundle from the IKS to learn it, then retry the decrypt.
                    var senderForSession = NativeSessionManager.getUserIdForIdentityKey(recoveredSenderKey)
                        ?: sealedSenderUserId
                    if (senderForSession == null) {
                        val senderHint = envelope.senderUserId
                        runCatching {
                            if (senderHint != null) {
                                val bundle = org.enchant.core.crypto.KeyManager.fetchKeyBundle(senderHint)
                                if (bundle != null) {
                                    android.util.Log.w("IncomingMsg", "Unknown sealed sender; learned identity for $senderHint")
                                    NativeSessionManager.setIdentityKey(senderHint, bundle.identityKey)
                                    NativeSessionManager.setPeerDeviceId(senderHint, bundle.deviceId)
                                }
                            }
                        }
                        senderForSession = NativeSessionManager.getUserIdForIdentityKey(recoveredSenderKey)
                            ?: senderHint
                    }
                    if (senderForSession == null)
                        return@withContext ProcessResult.Error("Unknown sealed sender")
                    val sessionPlaintext = NativeSessionManager.decryptMessage(senderForSession, plaintext)
                        ?: NativeSessionManager.decryptPreKeyMessage(senderForSession, plaintext)
                        ?: return@withContext ProcessResult.Error("Session decrypt failed")
                    try {
                        wrapper = org.enchant.protos.SignalServiceContentProto.parseFrom(sessionPlaintext.plaintext)
                    } catch (_: Exception) {
                        return@withContext ProcessResult.Error("Session content parse failed")
                    }
                }
                val wrapperNonNull = wrapper ?: return@withContext ProcessResult.Error("Sealed content missing")
                val senderUserId = wrapperNonNull.localAddress.uuid.toStringUtf8()
                // Remember the sender's device id (needed for key-transparency
                // leaf verification).
                if (envelope.senderDeviceId?.isNotBlank() == true) {
                    NativeSessionManager.setPeerDeviceId(senderUserId, envelope.senderDeviceId!!)
                }
                if (senderUserId.isEmpty()) {
                    return@withContext ProcessResult.Error("Missing sender identity in sealed payload")
                }

                val knownKey = NativeSessionManager.getIdentityKey(senderUserId)
                if (knownKey != null && !knownKey.contentEquals(recoveredSenderKey)) {
                    // The sender's identity key changed. Verify the new key
                    // against the key-transparency log; a failed audit marks
                    // the peer unverified (Signal behavior).
                    android.util.Log.w("IncomingMsg", "Sealed sender re-keyed for $senderUserId; adopting new identity, resetting verification")
                    NativeSessionManager.setIdentityKey(senderUserId, recoveredSenderKey)
                    runCatching {
                        val pool = org.enchant.core.database.DatabasePool.instance
                        if (pool != null) {
                            org.enchant.core.database.dao.IdentityDao(pool).setVerified(senderUserId, 0)
                        }
                    }
                    // Key transparency audit of the new key.
                    val ktClient = apiClient
                    if (ktClient != null) {
                        val deviceId = envelope.senderDeviceId
                            ?: org.enchant.core.crypto.NativeSessionManager.getPeerDeviceId(senderUserId)
                            ?: ""
                        val audited = runCatching {
                            org.enchant.core.crypto.KeyTransparencyVerifier.verifyServerConsistency(
                                ktClient, senderUserId, deviceId
                            )
                        }.getOrDefault(false)
                        android.util.Log.w("IncomingMsg", "KT audit for $senderUserId: $audited")
                        if (!audited) {
                            org.enchant.core.base.SecurePreferences.putBoolean("kt_warning_$senderUserId", true)
                        }
                    }
                }
                if (knownKey == null) {
                    NativeSessionManager.setIdentityKey(senderUserId, recoveredSenderKey)
                }

                val content = wrapperNonNull.content
                val now = System.currentTimeMillis()
                android.util.Log.e("GroupCipher", "veil content skdm=${content.senderKeyDistributionMessage.size()} data=${content.hasDataMessage()}")

                return@withContext when {
                    content.senderKeyDistributionMessage.size() > 0 -> {
                        // Signal sender-key pattern: the distribution rides the
                        // Veil so the receiver can build the sender's chain.
                        val raw = content.senderKeyDistributionMessage.toByteArray()
                        if (raw.size < 68) {
                            ProcessResult.Error("Distribution payload too short")
                        } else {
                            val gid = String(raw.copyOfRange(0, 36).let { b ->
                                b.takeWhile { it != 0.toByte() }.toByteArray()
                            }, Charsets.UTF_8)
                            val signingPublic = raw.copyOfRange(36, 68)
                            val dist = raw.copyOfRange(68, raw.size)
                            val ok = org.enchant.core.crypto.GroupCipherManager.processDistribution(
                                gid, senderUserId, signingPublic, dist
                            )
                            android.util.Log.e("GroupCipher", "SKDIST veil group=$gid sender=$senderUserId ok=$ok")
                            if (!ok) ProcessResult.Error("Distribution processing failed")
                            else {
                                runCatching { fetchPendingMessages() }
                                ProcessResult.Handled
                            }
                        }
                    }
                    content.hasDataMessage() -> {
                        val dataMsg = content.dataMessage
                        // Group-context messages (per-member encrypted, group
                        // V1 style) route into the group conversation.
                        val groupId = if (dataMsg.hasGroupV2() && dataMsg.groupV2.masterKey.size() > 0) {
                            String(dataMsg.groupV2.masterKey.toByteArray().let { b ->
                                b.takeWhile { it != 0.toByte() }.toByteArray()
                            }, Charsets.UTF_8).takeIf { it.isNotBlank() }
                        } else null
                        val convId = groupId ?: senderUserId
                        val convType = if (groupId != null) "group" else "direct"
                        val attachments = dataMsg.attachmentsList
                        val attachment = attachments.firstOrNull { it.hasCdnKey() }
                        val rawBody = if (attachment != null) "📎 ${attachment.fileName}" else dataMsg.body
                        // The view-once marker rides the message body.
                        val isViewOnceMsg = dataMsg.body.startsWith("🕶️ ")
                        val replyEnvId = if (dataMsg.hasQuote()) {
                            val q = dataMsg.quote
                            if (q.hasEnvelopeId()) q.envelopeId
                            else messageDao?.getEnvelopeIdByServerTs(q.id)
                        } else null
                        repo.insertMessageAndUpdateConversation(
                            MessageEntity(
                                conversationId = convId,
                                senderId = senderUserId,
                                messageType = "ENCRYPTED_MESSAGE",
                                content = if (isViewOnceMsg) rawBody.removePrefix("🕶️ ") else rawBody,
                                isViewOnce = isViewOnceMsg,
                                status = "delivered",
                                timestamp = envelope.senderTimestamp ?: envelope.serverTimestamp ?: now,
                                serverTs = now,
                                envelopeId = envelope.envelopeId,
                                replyToEnvelopeId = replyEnvId,
                                mediaKey = attachment?.takeIf { it.hasKey() }?.key?.toByteArray()?.let {
                                    org.enchant.core.crypto.CryptoPrimitives.base64UrlEncode(it)
                                },
                                mediaMimeType = attachment?.contentType,
                                mediaSize = attachment?.size?.toLong(),
                                mediaId = attachment?.cdnKey,
                                forwardedFromUserId =
                                    if (dataMsg.hasForwardedFromUserId()) dataMsg.forwardedFromUserId else null
                            ),
                            conversationType = convType
                        )
                        sendVeiledDeliveryReceipt(envelope, senderUserId)
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

    private suspend fun sendVeiledDeliveryReceipt(
        envelope: IncomingEnvelope, senderUserId: String
    ) {
        val replyToken = envelope.replyToken ?: return
        val msg = repository?.getMessage(envelope.envelopeId ?: "")
        val ts = msg?.timestamp ?: System.currentTimeMillis()
        val contentBytes = MessageProtobufHelper.buildReceiptContent(
            timestamps = listOf(ts),
            type = MessageProtobufHelper.ReceiptType.DELIVERY
        )
        MessageSendPipeline.sendVeiledMessage(
            conversationId = senderUserId,
            recipientUserId = senderUserId,
            plaintext = contentBytes
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

package org.enchant.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.enchant.chat.data.ChatPagingSource
import org.enchant.chat.data.ConversationRepository
import org.enchant.chat.data.MessageSendPipeline
import org.enchant.chat.data.SendResult
import org.enchant.core.base.SecurePreferences
import kotlinx.coroutines.Job as CoroutineJob
import org.enchant.core.base.AppConfig
import org.enchant.core.calls.CallManager
import org.enchant.core.network.ApiClient
import org.enchant.core.model.Conversation
import org.enchant.core.model.Message
import org.enchant.core.model.MessageStatus

enum class SendState { IDLE, SENDING, UPLOADING, SENT, FAILED }

sealed class ScrollEvent {
    data class ToPosition(val position: Int) : ScrollEvent()
    data object ToBottom : ScrollEvent()
}

class ConversationViewModel(
    private val repo: ConversationRepository = ConversationViewModel.defaultRepo(),
    private val apiClient: ApiClient = ApiClient.getInstance(),
    private val pipeline: MessageSendPipeline = MessageSendPipeline
) : ViewModel() {

    companion object {
        internal fun defaultRepo(): ConversationRepository {
            val pool = org.enchant.core.database.DatabasePool.instance
                ?: error("DatabasePool not initialized")
            return ConversationRepository(
                messageDao = org.enchant.core.database.dao.MessageDao(pool),
                conversationDao = org.enchant.core.database.dao.ConversationDao(pool),
                recipientDao = org.enchant.core.database.dao.RecipientDao(pool),
                pool = pool
            )
        }
    }

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _conversation = MutableStateFlow<Conversation?>(null)
    val conversation: StateFlow<Conversation?> = _conversation.asStateFlow()

    private val _title = MutableStateFlow<String?>(null)
    val title: StateFlow<String?> = _title.asStateFlow()

    private val _typingIndicator = MutableStateFlow(false)
    val typingIndicator: StateFlow<Boolean> = _typingIndicator.asStateFlow()

    private val _sendingState = MutableStateFlow<SendState?>(null)
    val sendingState: StateFlow<SendState?> = _sendingState.asStateFlow()

    private val _scrollToEvent = MutableSharedFlow<ScrollEvent>(extraBufferCapacity = 5)
    val scrollToEvent: SharedFlow<ScrollEvent> = _scrollToEvent.asSharedFlow()

    private val _searchResults = MutableStateFlow<List<Message>>(emptyList())
    val searchResults: StateFlow<List<Message>> = _searchResults.asStateFlow()

    private val _translatedMessage = MutableStateFlow<Pair<String, String>?>(null)
    val translatedMessage: StateFlow<Pair<String, String>?> = _translatedMessage.asStateFlow()

    private var conversationId: String = ""
    private var recipientUserId: String = ""
    private var pagingSource: ChatPagingSource? = null
    private var messageJob: CoroutineJob? = null
    private var searchJob: CoroutineJob? = null
    private var typingJob: CoroutineJob? = null
    private var lastTypingSentAt = 0L

    fun init(convId: String) {
        if (conversationId == convId) return
        conversationId = convId
        recipientUserId = convId
        pagingSource = ChatPagingSource(repo, convId)
        messageJob?.cancel()
        messageJob = viewModelScope.launch {
            repo.getMessages(convId).collect { list ->
                _messages.update { list }
            }
        }
        viewModelScope.launch {
            val conv = repo.getConversation(convId)
            _conversation.value = conv
            // Opening the chat clears its unread count (Signal behavior).
            repo.markConversationRead(convId)
            if (conv?.type == org.enchant.core.model.ConversationType.DIRECT) {
                val members = conv.id.split(":")
                val selfId = SecurePreferences.getString("auth.user_id") ?: ""
                recipientUserId = members.find { it != selfId } ?: conv.id
            }
            _title.value = repo.resolveDisplayName(convId)
        }

        // Live typing indicator for this conversation's peer.
        viewModelScope.launch {
            org.enchant.chat.data.TypingRegistry.typingUsers.collect { typingUsers ->
                _typingIndicator.value = typingUsers.contains(recipientUserId)
            }
        }

        // Pull reactions for visible messages from the server so both sides
        // see them (the backend stores reactions but never pushes them).
        viewModelScope.launch {
            val selfId = SecurePreferences.getString("auth.user_id") ?: ""
            val lastSync = mutableMapOf<String, String>()
            _messages.collect { messages ->
                delay(600)
                messages.takeLast(20).forEach { msg ->
                    val eid = msg.envelopeId ?: return@forEach
                    try {
                        val json = apiClient.get("/v1/reactions/$eid").getOrNull() ?: return@forEach
                        val reactions = json["reactions"]?.jsonObject ?: return@forEach
                        val sig = reactions.entries.joinToString { (e, c) -> "$e:$c" }
                        if (lastSync[eid] == sig) return@forEach
                        lastSync[eid] = sig
                        reactions.forEach { (emoji, agg) ->
                            val count = agg.jsonObject["count"]?.jsonPrimitive?.intOrNull
                                ?: agg.jsonPrimitive.intOrNull ?: 0
                            if (count > 0) {
                                repo.addReaction(convId, msg.localId, emoji, selfId)
                                android.util.Log.e("ReactionSync", "stored $emoji on $eid")
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("ReactionSync", "failed $eid: ${e.message}")
                    }
                }
            }
        }
    }

    /**
     * Called from the composer as the user types. Sends a throttled
     * typing-start and schedules a typing-stop, mirroring Signal.
     */
    fun onComposerTextChanged(text: String) {
        if (recipientUserId.isEmpty()) return
        typingJob?.cancel()
        if (text.isBlank()) {
            typingJob = null
            if (System.currentTimeMillis() - lastTypingSentAt < 3000) {
                viewModelScope.launch { pipeline.sendTypingIndicator(recipientUserId, false) }
            }
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastTypingSentAt >= 3000) {
            lastTypingSentAt = now
            viewModelScope.launch { pipeline.sendTypingIndicator(recipientUserId, true) }
        }
        typingJob = viewModelScope.launch {
            delay(5000)
            pipeline.sendTypingIndicator(recipientUserId, false)
        }
    }

    private fun sendTypingStopped() {
        typingJob?.cancel()
        typingJob = null
        viewModelScope.launch { pipeline.sendTypingIndicator(recipientUserId, false) }
    }

    fun loadMoreMessages() {
        viewModelScope.launch {
            val lastId = _messages.value.lastOrNull()?.localId
            repo.getMessages(conversationId, beforeId = lastId).collect { list ->
                if (list.isNotEmpty()) {
                    _messages.update { current -> current + list }
                }
            }
        }
    }

    fun sendTextMessage(text: String, replyTo: String? = null): Boolean {
        if (text.isBlank()) return false
        sendTypingStopped()
        _sendingState.value = SendState.SENDING
        viewModelScope.launch {
            val selfId = SecurePreferences.getString("auth.user_id") ?: ""
            val conv = _conversation.value
            val role = conv?.let { org.enchant.core.base.SecurePreferences.getString("group_role_${it.id}") }
            if (conv?.type == org.enchant.core.model.ConversationType.GROUP && role == "member") {
                val isAnnouncement = org.enchant.core.base.SecurePreferences.getBoolean("group_announcement_${conv.id}", false)
                if (isAnnouncement) {
                    _sendingState.value = SendState.FAILED
                    return@launch
                }
            }
            val result = if (conv?.type == org.enchant.core.model.ConversationType.GROUP) {
                pipeline.sendGroupMessage(
                    groupId = conversationId,
                    members = emptyList(),
                    plaintext = text.encodeToByteArray(),
                    replyTo = replyTo
                )
            } else {
                pipeline.sendMessage(
                    conversationId = conversationId,
                    recipientUserId = recipientUserId,
                    plaintext = text.encodeToByteArray(),
                    replyTo = replyTo
                )
            }
            _sendingState.value = when (result) {
                is SendResult.Success -> SendState.SENT
                is SendResult.Queued -> SendState.SENT
                is SendResult.Failed -> SendState.FAILED
            }
            if (result is SendResult.Success || result is SendResult.Queued) {
                delay(1000)
                _sendingState.value = SendState.IDLE
            }
        }
        return true
    }

    fun sendMediaMessage(uri: Uri, mimeType: String): Boolean = sendFileMessage(uri, "file", mimeType)

    fun sendFileMessage(uri: Uri, fileName: String, mimeType: String, isViewOnce: Boolean = false): Boolean {
        _sendingState.value = SendState.UPLOADING
        viewModelScope.launch {
            val result = pipeline.sendFileMessage(
                conversationId = conversationId,
                recipientUserId = recipientUserId,
                fileUri = uri,
                fileName = fileName,
                mimeType = mimeType,
                isViewOnce = isViewOnce
            )
            _sendingState.value = when (result) {
                is SendResult.Success -> SendState.SENT
                else -> SendState.FAILED
            }
            if (_sendingState.value != SendState.IDLE) {
                delay(1000)
                _sendingState.value = SendState.IDLE
            }
        }
        return true
    }

    fun sendVoiceMessage(audioFile: java.io.File, duration: Int): Boolean {
        _sendingState.value = SendState.UPLOADING
        viewModelScope.launch {
            val uri = Uri.fromFile(audioFile)
            val result = pipeline.sendMediaMessage(
                conversationId = conversationId,
                recipientUserId = recipientUserId,
                fileUri = uri,
                mimeType = "audio/mp4"
            )
            _sendingState.value = when (result) {
                is SendResult.Success -> SendState.SENT
                else -> SendState.FAILED
            }
            if (_sendingState.value != SendState.IDLE) {
                delay(1000)
                _sendingState.value = SendState.IDLE
            }
        }
        return true
    }

    fun sendLocationMessage(lat: Double, lng: Double, label: String? = null): Boolean {
        _sendingState.value = SendState.SENDING
        viewModelScope.launch {
            val text = "LOCATION_JSON:${lat}:${lng}:${label ?: ""}"
            val result = pipeline.sendMessage(
                conversationId = conversationId,
                recipientUserId = recipientUserId,
                plaintext = text.encodeToByteArray()
            )
            if (result is SendResult.Success) {
                try {
                    apiClient.post("/v1/location", buildJsonObject {
                        put("envelope_id", JsonPrimitive(result.envelopeId))
                    })
                } catch (e: Exception) { android.util.Log.w("Enchant", "location share failed") }
            }
            _sendingState.value = if (result is SendResult.Success || result is SendResult.Queued) SendState.SENT else SendState.FAILED
            if (_sendingState.value != SendState.IDLE) {
                delay(1000)
                _sendingState.value = SendState.IDLE
            }
        }
        return true
    }

    fun sendSticker(packId: String, stickerId: String): Boolean {
        _sendingState.value = SendState.SENDING
        viewModelScope.launch {
            val text = "STICKER_JSON:$packId:$stickerId"
            val result = pipeline.sendMessage(
                conversationId = conversationId,
                recipientUserId = recipientUserId,
                plaintext = text.encodeToByteArray()
            )
            _sendingState.value = if (result is SendResult.Success || result is SendResult.Queued) SendState.SENT else SendState.FAILED
            if (_sendingState.value != SendState.IDLE) {
                delay(1000)
                _sendingState.value = SendState.IDLE
            }
        }
        return true
    }

    fun resendMessage(envelopeId: String) {
        viewModelScope.launch {
            val msg = repo.getMessage(envelopeId) ?: return@launch
            val selfId = SecurePreferences.getString("auth.user_id") ?: return@launch
            val result = pipeline.sendMessage(
                conversationId = conversationId,
                recipientUserId = recipientUserId,
                plaintext = msg.content.encodeToByteArray()
            )
            if (result is SendResult.Success) {
                repo.updateMessageStatus(envelopeId, MessageStatus.SENT)
            }
        }
    }

    fun deleteMessage(envelopeId: String, forEveryone: Boolean) {
        viewModelScope.launch {
            if (forEveryone) {
                pipeline.deleteForEveryone(envelopeId, recipientUserId)
                pipeline.deleteMessageOnServer(envelopeId)
            } else {
                pipeline.deleteForSelf(envelopeId)
            }
        }
    }

    fun deleteMessageForEveryone(envelopeId: String) {
        viewModelScope.launch {
            pipeline.deleteForEveryone(envelopeId, recipientUserId)
            pipeline.deleteMessageOnServer(envelopeId)
        }
    }

    fun editMessage(envelopeId: String, newText: String): Boolean {
        if (newText.isBlank()) return false
        viewModelScope.launch {
            pipeline.editMessage(envelopeId, newText.encodeToByteArray(), recipientUserId)
        }
        return true
    }

    fun forwardMessage(envelopeId: String, targetConversationId: String): Boolean {
        viewModelScope.launch {
            pipeline.forwardMessage(
                originalConversationId = conversationId,
                originalEnvelopeId = envelopeId,
                targetConversationId = targetConversationId,
                targetUserId = targetConversationId
            )
        }
        return true
    }

    fun setDisappearTimer(conversationId: String, seconds: Int) {
        viewModelScope.launch {
            repo.setDisappearTimer(conversationId, seconds)
        }
    }

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    fun loadConversations() {
        viewModelScope.launch {
            repo.getConversations().collect { list ->
                _conversations.value = list
            }
        }
    }

    fun setReaction(messageId: Long, emoji: String) {
        viewModelScope.launch {
            val msg = repo.getMessageByLocalId(messageId) ?: return@launch
            val selfId = SecurePreferences.getString("auth.user_id") ?: return@launch
            repo.addReaction(msg.conversationId, messageId, emoji, selfId)
            pipeline.sendReaction(msg.envelopeId ?: msg.localId.toString(), emoji, msg.conversationId)
        }
    }

    fun starMessage(messageId: Long, starred: Boolean) {
        viewModelScope.launch {
            val msg = repo.getMessageByLocalId(messageId) ?: return@launch
            repo.starMessage(msg.envelopeId ?: msg.localId.toString(), starred)
        }
    }

    fun pinMessage(messageId: Long) {
        viewModelScope.launch {
            val msg = repo.getMessageByLocalId(messageId) ?: return@launch
            pinMessageByEnvelopeId(msg.envelopeId ?: msg.localId.toString())
        }
    }

    fun unpinMessage(messageId: Long) {
        viewModelScope.launch {
            val msg = repo.getMessageByLocalId(messageId) ?: return@launch
            unpinMessageByEnvelopeId(msg.envelopeId ?: msg.localId.toString())
        }
    }

    fun pinMessageByEnvelopeId(envelopeId: String) {
        viewModelScope.launch {
            repo.pinMessage(envelopeId, true)
            pipeline.pinMessageRequest(envelopeId)
            loadPinnedMessages()
        }
    }

    fun unpinMessageByEnvelopeId(envelopeId: String) {
        viewModelScope.launch {
            repo.pinMessage(envelopeId, false)
            pipeline.unpinMessageRequest(envelopeId)
            loadPinnedMessages()
        }
    }

    fun translateMessage(envelopeId: String, targetLanguage: String = "en") {
        viewModelScope.launch {
            val result = repo.translateMessage(envelopeId, targetLanguage)
            result.onSuccess { translatedText ->
                _translatedMessage.value = envelopeId to translatedText
            }.onFailure {
                android.util.Log.w("Enchant", "Translation failed: ${it.message}")
            }
        }
    }

    fun clearTranslation() {
        _translatedMessage.value = null
    }

    fun copyToClipboard(text: String) {
        val ctx = AppConfig.applicationContext ?: return
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        cm.setPrimaryClip(ClipData.newPlainText("message", text))
    }

    fun reportMessage(envelopeId: String) {
        viewModelScope.launch {
            try {
                val msg = repo.getMessage(envelopeId)
                val targetUserId = msg?.senderId ?: conversationId
                apiClient.post("/v1/report", buildJsonObject {
                    put("target_user_id", JsonPrimitive(targetUserId))
                    put("reason", JsonPrimitive("message_report"))
                    put("envelope_id", JsonPrimitive(envelopeId))
                })
            } catch (e: Exception) { android.util.Log.w("Enchant", "report failed") }
        }
    }

    fun searchInConversation(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            repo.searchMessages(query).collect { results ->
                _searchResults.value = results.filter { it.conversationId == conversationId }
            }
        }
    }

    fun jumpToMessage(envelopeId: String) {
        viewModelScope.launch {
            val msg = repo.getMessage(envelopeId) ?: return@launch
            val index = _messages.value.indexOfFirst { it.envelopeId == envelopeId }
            if (index >= 0) {
                _scrollToEvent.emit(ScrollEvent.ToPosition(index))
            }
        }
    }

    fun jumpToDate(timestamp: Long) {
        viewModelScope.launch {
            val messages = _messages.value
            var closestIndex = -1
            var closestDiff = Long.MAX_VALUE
            for (i in messages.indices) {
                val diff = kotlin.math.abs(messages[i].timestamp - timestamp)
                if (diff < closestDiff) {
                    closestDiff = diff
                    closestIndex = i
                }
            }
            if (closestIndex >= 0) {
                _scrollToEvent.emit(ScrollEvent.ToPosition(closestIndex))
            } else {
                _scrollToEvent.emit(ScrollEvent.ToPosition(0))
            }
        }
    }

    fun scheduleMessage(body: String, scheduledDate: Long, replyTo: String? = null) {
        if (body.isBlank()) return
        val messageId = System.currentTimeMillis()
        val delayMs = (scheduledDate - System.currentTimeMillis()).coerceAtLeast(0)
        viewModelScope.launch {
            delay(delayMs)
            sendTextMessage(body, replyTo)
        }
    }

    fun markViewOnceViewed(envelopeId: String) {
        viewModelScope.launch {
            try {
                apiClient.post("/v1/disappear/viewed", buildJsonObject {
                    put("envelope_ids", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive(envelopeId)) })
                })
                deleteViewOnceMedia(envelopeId)
                repo.markMessageDeleted(envelopeId)
            } catch (e: Exception) {
                Log.w("ConversationVM", "ViewOnce failed: ${e.message}")
            }
        }
    }

    fun deleteViewOnceMedia(envelopeId: String) {
        viewModelScope.launch {
            repo.deleteLocalMedia(envelopeId)
            val ctx = AppConfig.applicationContext ?: return@launch
            val file = java.io.File(ctx.cacheDir, "media_downloads/$envelopeId")
            if (file.exists()) file.delete()
        }
    }

    fun sendContactCard(contactUserId: String, targetConversationId: String) {
        viewModelScope.launch {
            val vcard = "BEGIN:VCARD\nVERSION:3.0\nFN:$contactUserId\nUID:$contactUserId\nEND:VCARD"
            val text = "VCARD_JSON:$contactUserId"
            val result = pipeline.sendMessage(
                conversationId = targetConversationId,
                recipientUserId = targetConversationId,
                plaintext = "$text\n$vcard".encodeToByteArray()
            )
            val envelopeId = (result as? SendResult.Success)?.envelopeId ?: return@launch
            try {
                    apiClient.post("/v1/contacts/share", buildJsonObject {
                        put("contact_user_id", JsonPrimitive(contactUserId))
                        put("envelope_id", JsonPrimitive(envelopeId))
                    })
                } catch (e: Exception) { android.util.Log.w("Enchant", "contact share failed") }
        }
    }

    fun startCall(remoteUserId: String, isVideo: Boolean) {
        viewModelScope.launch {
            org.enchant.core.calls.CallManager.startOutgoingCall(remoteUserId, isVideo)
        }
    }

    fun scrollToBottom() {
        viewModelScope.launch {
            _scrollToEvent.emit(ScrollEvent.ToBottom)
        }
    }

    private val _draft = MutableStateFlow("")
    val draft: StateFlow<String> = _draft.asStateFlow()

    fun saveDraft(content: String) {
        _draft.value = content
        viewModelScope.launch {
            if (content.isNotBlank()) {
                repo.saveDraft(conversationId, content)
            } else {
                repo.deleteDraft(conversationId)
            }
        }
    }

    fun loadDraft() {
        viewModelScope.launch {
            val savedDraft = repo.getDraft(conversationId)
            if (savedDraft != null) {
                _draft.value = savedDraft
            }
        }
    }

    fun clearDraft() {
        _draft.value = ""
        viewModelScope.launch { repo.deleteDraft(conversationId) }
    }

    private val _starredMessages = MutableStateFlow<List<Message>>(emptyList())
    val starredMessages: StateFlow<List<Message>> = _starredMessages.asStateFlow()

    fun loadStarredMessages() {
        viewModelScope.launch {
            repo.getStarredMessages().collect { _starredMessages.value = it }
        }
    }

    private val _pinnedMessages = MutableStateFlow<List<Message>>(emptyList())
    val pinnedMessages: StateFlow<List<Message>> = _pinnedMessages.asStateFlow()

    fun loadPinnedMessages() {
        viewModelScope.launch {
            val msgs = repo.getPinnedMessages(conversationId)
            _pinnedMessages.value = msgs
        }
    }

    private val _scheduledMessages = MutableStateFlow<List<ScheduledMessage>>(emptyList())
    val scheduledMessages: StateFlow<List<ScheduledMessage>> = _scheduledMessages.asStateFlow()

    data class ScheduledMessage(
        val id: Long,
        val content: String,
        val scheduledAt: Long,
        val isSent: Boolean
    )

    fun loadScheduledMessages() {
        viewModelScope.launch {
            val msgs = repo.getScheduledMessages(conversationId)
            _scheduledMessages.value = msgs.map {
                ScheduledMessage(it.id, it.content, it.scheduledAt, it.isSent)
            }
        }
    }

    fun scheduleMessagePersisted(body: String, scheduledAt: Long) {
        if (body.isBlank()) return
        viewModelScope.launch {
            repo.scheduleMessage(conversationId, body, scheduledAt)
            loadScheduledMessages()
        }
    }

    fun cancelScheduledMessage(id: Long) {
        viewModelScope.launch {
            repo.deleteScheduledMessage(id)
            loadScheduledMessages()
        }
    }

    override fun onCleared() {
        super.onCleared()
        messageJob?.cancel()
        searchJob?.cancel()
    }
}

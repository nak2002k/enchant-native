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
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.enchant.chat.data.ChatPagingSource
import org.enchant.chat.data.ConversationRepository
import org.enchant.chat.data.MessageSendPipeline
import org.enchant.chat.data.SendResult
import org.enchant.core.base.SecurePreferences
import kotlinx.coroutines.Job as CoroutineJob
import org.enchant.core.base.AppConfig
import org.enchant.core.calls.CallManager
import org.enchant.core.jobmanager.JobManager
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
    private val repo: ConversationRepository,
    private val apiClient: ApiClient,
    private val pipeline: MessageSendPipeline = MessageSendPipeline
) : ViewModel() {
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _conversation = MutableStateFlow<Conversation?>(null)
    val conversation: StateFlow<Conversation?> = _conversation.asStateFlow()

    private val _typingIndicator = MutableStateFlow(false)
    val typingIndicator: StateFlow<Boolean> = _typingIndicator.asStateFlow()

    private val _sendingState = MutableStateFlow<SendState?>(null)
    val sendingState: StateFlow<SendState?> = _sendingState.asStateFlow()

    private val _scrollToEvent = MutableSharedFlow<ScrollEvent>(extraBufferCapacity = 5)
    val scrollToEvent: SharedFlow<ScrollEvent> = _scrollToEvent.asSharedFlow()

    private val _searchResults = MutableStateFlow<List<Message>>(emptyList())
    val searchResults: StateFlow<List<Message>> = _searchResults.asStateFlow()

    private var conversationId: String = ""
    private var pagingSource: ChatPagingSource? = null
    private var messageJob: CoroutineJob? = null
    private var searchJob: CoroutineJob? = null

    fun init(convId: String) {
        if (conversationId == convId) return
        conversationId = convId
        pagingSource = ChatPagingSource(repo, convId)
        messageJob?.cancel()
        messageJob = viewModelScope.launch {
            repo.getMessages(convId).collect { list ->
                _messages.value = list
            }
        }
        viewModelScope.launch {
            val conv = repo.getConversation(convId)
            _conversation.value = conv
        }
    }

    fun loadMoreMessages() {
        viewModelScope.launch {
            val lastId = _messages.value.lastOrNull()?.localId
            repo.getMessages(conversationId, beforeId = lastId).collect { list ->
                if (list.isNotEmpty()) {
                    _messages.value = list + _messages.value
                }
            }
        }
    }

    fun sendTextMessage(text: String, replyTo: String? = null): Boolean {
        if (text.isBlank()) return false
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
            val result = pipeline.sendMessage(
                conversationId = conversationId,
                recipientUserId = conversationId,
                plaintext = text.encodeToByteArray(),
                replyTo = replyTo
            )
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

    fun sendMediaMessage(uri: Uri, mimeType: String): Boolean {
        _sendingState.value = SendState.UPLOADING
        viewModelScope.launch {
            val result = pipeline.sendMediaMessage(
                conversationId = conversationId,
                recipientUserId = conversationId,
                fileUri = uri,
                mimeType = mimeType
            )
            _sendingState.value = when (result) {
                is SendResult.Success -> SendState.SENT
                else -> SendState.FAILED
            }
            delay(1000)
            _sendingState.value = SendState.IDLE
        }
        return true
    }

    fun sendVoiceMessage(audioFile: java.io.File, duration: Int): Boolean {
        _sendingState.value = SendState.UPLOADING
        viewModelScope.launch {
            val uri = Uri.fromFile(audioFile)
            val result = pipeline.sendMediaMessage(
                conversationId = conversationId,
                recipientUserId = conversationId,
                fileUri = uri,
                mimeType = "audio/mp4"
            )
            _sendingState.value = when (result) {
                is SendResult.Success -> SendState.SENT
                else -> SendState.FAILED
            }
            delay(1000)
            _sendingState.value = SendState.IDLE
        }
        return true
    }

    fun sendLocationMessage(lat: Double, lng: Double, label: String? = null): Boolean {
        _sendingState.value = SendState.SENDING
        viewModelScope.launch {
            val text = "📍 ${label ?: "$lat, $lng"}"
            val result = pipeline.sendMessage(
                conversationId = conversationId,
                recipientUserId = conversationId,
                plaintext = text.encodeToByteArray()
            )
            if (result is SendResult.Success || result is SendResult.Queued) {
                try {
                    apiClient.post("/v1/location", buildJsonObject {
                        put("envelope_id", JsonPrimitive((result as? SendResult.Success)?.envelopeId ?: ""))
                    })
                } catch (e: Exception) { android.util.Log.w("Enchant", "silent: ${e.message}") }
            }
            _sendingState.value = if (result is SendResult.Success || result is SendResult.Queued) SendState.SENT else SendState.FAILED
            delay(1000)
            _sendingState.value = SendState.IDLE
        }
        return true
    }

    fun sendSticker(packId: String, stickerId: String): Boolean {
        _sendingState.value = SendState.SENDING
        viewModelScope.launch {
            val text = "🔄 Sticker:$packId:$stickerId"
            val result = pipeline.sendMessage(
                conversationId = conversationId,
                recipientUserId = conversationId,
                plaintext = text.encodeToByteArray()
            )
            _sendingState.value = if (result is SendResult.Success || result is SendResult.Queued) SendState.SENT else SendState.FAILED
            delay(1000)
            _sendingState.value = SendState.IDLE
        }
        return true
    }

    fun resendMessage(envelopeId: String) {
        viewModelScope.launch {
            val msg = repo.getMessage(envelopeId) ?: return@launch
            val selfId = SecurePreferences.getString("auth.user_id") ?: return@launch
            val result = pipeline.sendMessage(
                conversationId = conversationId,
                recipientUserId = conversationId,
                plaintext = msg.content.encodeToByteArray()
            )
            if (result is SendResult.Success) {
                repo.markMessageDeleted(envelopeId)
            }
        }
    }

    fun deleteMessage(envelopeId: String, forEveryone: Boolean) {
        viewModelScope.launch {
            if (forEveryone) {
                pipeline.deleteForEveryone(envelopeId, conversationId)
            } else {
                pipeline.deleteForSelf(envelopeId)
            }
        }
    }

    fun editMessage(envelopeId: String, newText: String): Boolean {
        if (newText.isBlank()) return false
        viewModelScope.launch {
            pipeline.editMessage(envelopeId, newText.encodeToByteArray(), conversationId)
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
            pipeline.sendReaction(msg.envelopeId ?: msg.localId.toString(), emoji)
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
            repo.pinMessage(msg.envelopeId ?: msg.localId.toString(), true)
        }
    }

    fun unpinMessage(messageId: Long) {
        viewModelScope.launch {
            val msg = repo.getMessageByLocalId(messageId) ?: return@launch
            repo.pinMessage(msg.envelopeId ?: msg.localId.toString(), false)
        }
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
            } catch (e: Exception) { android.util.Log.w("Enchant", "silent: ${e.message}") }
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
            _scrollToEvent.emit(ScrollEvent.ToPosition(msg.localId.toInt()))
        }
    }

    fun jumpToDate(timestamp: Long) {
        viewModelScope.launch {
            _scrollToEvent.emit(ScrollEvent.ToPosition(0))
        }
    }

    fun scheduleMessage(body: String, scheduledDate: Long, replyTo: String? = null) {
        if (body.isBlank()) return
        val jobId = "scheduled_${conversationId}_${System.currentTimeMillis()}"
        JobManager.enqueue(
            org.enchant.core.jobmanager.Job(
                id = jobId,
                delayMs = (scheduledDate - System.currentTimeMillis()).coerceAtLeast(0),
                run = {
                    viewModelScope.launch {
                        sendTextMessage(body, replyTo)
                    }
                }
            )
        )
    }

    fun cancelScheduledMessage(messageId: Long) {
        JobManager.cancelJob("scheduled_msg_$messageId")
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

    fun sendContactCard(contactUserId: String, conversationId: String) {
        viewModelScope.launch {
            val vcard = "BEGIN:VCARD\nVERSION:3.0\nFN:$contactUserId\nEND:VCARD"
            val text = "📇 Contact: $contactUserId"
            pipeline.sendMessage(
                conversationId = conversationId,
                recipientUserId = conversationId,
                plaintext = text.encodeToByteArray()
            )
            try {
                apiClient.post("/v1/contacts/share", buildJsonObject {
                    put("contact_user_id", JsonPrimitive(contactUserId))
                    put("envelope_id", JsonPrimitive(conversationId))
                })
            } catch (e: Exception) { android.util.Log.w("Enchant", "silent: ${e.message}") }
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

    override fun onCleared() {
        super.onCleared()
        messageJob?.cancel()
        searchJob?.cancel()
    }
}

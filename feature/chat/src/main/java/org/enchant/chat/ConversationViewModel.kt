package org.enchant.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.enchant.chat.data.ChatPagingSource
import org.enchant.chat.data.ConversationRepository
import org.enchant.chat.data.MessageSendPipeline
import org.enchant.chat.data.SendResult
import org.enchant.core.base.DI
import org.enchant.core.model.Conversation
import org.enchant.core.model.Message
import org.enchant.core.model.MessageStatus

enum class SendState { IDLE, SENDING, UPLOADING, SENT, FAILED }

sealed class ScrollEvent {
    data class ToPosition(val position: Int) : ScrollEvent()
    data object ToBottom : ScrollEvent()
}

class ConversationViewModel : ViewModel() {
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
    private var messageJob: Job? = null
    private var searchJob: Job? = null

    private val repo: ConversationRepository get() = DI.conversationRepository
    private val pipeline: MessageSendPipeline get() = MessageSendPipeline

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
            val more = pagingSource?.loadNext() ?: return@launch
            if (more.isNotEmpty()) {
                _messages.value = _messages.value + more
            }
        }
    }

    fun sendTextMessage(text: String, replyTo: String? = null): Boolean {
        if (text.isBlank()) return false
        _sendingState.value = SendState.SENDING
        viewModelScope.launch {
            val selfId = org.enchant.core.base.SecurePreferences.getString("auth.user_id") ?: ""
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
                    DI.apiClient.post("/v1/location", kotlinx.serialization.json.buildJsonObject {
                        put("envelope_id", (result as? SendResult.Success)?.envelopeId ?: "")
                    })
                } catch (_: Exception) {}
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
            val selfId = org.enchant.core.base.SecurePreferences.getString("auth.user_id") ?: return@launch
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

    fun setReaction(messageId: Long, emoji: String) {
        viewModelScope.launch {
            val msg = repo.getMessageByLocalId(messageId) ?: return@launch
            pipeline.sendReaction(msg.messageType, emoji)
        }
    }

    fun starMessage(messageId: Long, starred: Boolean) {
        viewModelScope.launch {
            val msg = repo.getMessageByLocalId(messageId) ?: return@launch
            repo.starMessage(msg.messageType, starred)
        }
    }

    fun pinMessage(messageId: Long) {
        viewModelScope.launch {
            val msg = repo.getMessageByLocalId(messageId) ?: return@launch
            repo.starMessage(msg.messageType, true)
        }
    }

    fun unpinMessage(messageId: Long) {
        viewModelScope.launch {
            val msg = repo.getMessageByLocalId(messageId) ?: return@launch
            repo.starMessage(msg.messageType, false)
        }
    }

    fun copyToClipboard(text: String) {
        val ctx = org.enchant.core.base.AppConfig.applicationContext ?: return
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("message", text))
    }

    fun reportMessage(envelopeId: String) {
        viewModelScope.launch {
            try {
                DI.apiClient.post("/v1/report", kotlinx.serialization.json.buildJsonObject {
                    put("target_user_id", conversationId)
                    put("reason", "message_report")
                    put("envelope_id", envelopeId)
                })
            } catch (_: Exception) {}
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

    fun startCall(remoteUserId: String, isVideo: Boolean) {
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

package org.enchant.core.notifications

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue

data class QueuedNotification(
    val conversationId: String,
    val displayName: String,
    val senderName: String?,
    val snippet: String,
    val campaignId: String? = null
)

object OptimizedMessageNotifier {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val queue = ConcurrentLinkedQueue<QueuedNotification>()
    @Volatile
    private var flushScheduled = false
    private var lastContext: Context? = null
    private val _queueSize = MutableStateFlow(0)
    val queueSize: StateFlow<Int> = _queueSize.asStateFlow()

    fun onMessageReceived(queueItem: QueuedNotification) {
        queue.add(queueItem)
        _queueSize.value = queue.size
        scheduleFlush()
    }

    suspend fun flush(context: Context) {
        lastContext = context
        flushScheduled = false
        val batch = mutableListOf<QueuedNotification>()
        while (true) {
            val item = queue.poll() ?: break
            batch.add(item)
        }
        _queueSize.value = 0
        batch.groupBy { it.conversationId }.forEach { (convId, items) ->
            val first = items.first()
            val latest = items.last()
            MessageNotifier.onMessageReceived(
                context = context,
                conversationId = convId,
                displayName = first.displayName,
                senderName = first.senderName,
                snippet = latest.snippet
            )
        }
    }

    fun cancelAll(context: Context) {
        queue.clear()
        _queueSize.value = 0
        MessageNotifier.cancelAll(context)
    }

    private fun scheduleFlush() {
        if (flushScheduled) return
        flushScheduled = true
        scope.launch {
            delay(50)
            val ctx = lastContext ?: return@launch
            flush(ctx)
        }
    }
}

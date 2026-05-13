package org.enchant.core.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.enchant.core.base.SecurePreferences
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

data class QueuedMessage(
    val id: String = UUID.randomUUID().toString(),
    val recipientUserId: String,
    val recipientDeviceId: String?,
    val messageType: String,
    val payload: ByteArray,
    val senderTs: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val retryCount: Int = 0
)

object OfflineQueue {
    private val queue = ConcurrentLinkedQueue<QueuedMessage>()
    private var maxEntries = 1000
    private val _pendingCount = MutableStateFlow(0)

    val pendingCount: StateFlow<Int> = _pendingCount.asStateFlow()

    suspend fun enqueue(message: QueuedMessage) {
        if (queue.size >= maxEntries) {
            queue.poll()
        }
        queue.offer(message)
        _pendingCount.value = queue.size
    }

    suspend fun drain() {
        val batch = mutableListOf<QueuedMessage>()
        while (true) {
            val msg = queue.poll() ?: break
            batch.add(msg)
        }
        _pendingCount.value = 0

        for (msg in batch) {
            val result = WebSocketManager.requestRESTFallback(msg)
            if (result.isFailure) {
                val retried = msg.copy(retryCount = msg.retryCount + 1)
                if (retried.retryCount < 5) {
                    queue.offer(retried)
                    _pendingCount.value = queue.size
                }
            }
        }
    }

    fun remove(messageId: String) {
        queue.removeAll { it.id == messageId }
        _pendingCount.value = queue.size
    }

    suspend fun clearAll() {
        queue.clear()
        _pendingCount.value = 0
    }
}

package org.enchant.core.network

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.enchant.core.base.SecurePreferences
import java.util.UUID
import java.util.LinkedList

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
    private val queue = LinkedList<QueuedMessage>()
    @Volatile
    private var maxEntries = 1000
    private val _pendingCount = MutableStateFlow(0)

    val pendingCount: StateFlow<Int> = _pendingCount.asStateFlow()

    suspend fun init() {
        restoreFromDisk()
    }

    private suspend fun restoreFromDisk() {
        val count = SecurePreferences.getInt("offline.queue.count", 0)
        for (i in 0 until count) {
            val data = SecurePreferences.getString("offline.queue.$i") ?: continue
            try {
                val parts = data.split("|", limit = 7)
                if (parts.size == 7) {
                    val msg = QueuedMessage(
                        id = parts[0],
                        recipientUserId = parts[1],
                        recipientDeviceId = parts[2].ifEmpty { null },
                        messageType = parts[3],
                        payload = java.util.Base64.getUrlDecoder().decode(parts[4]),
                        senderTs = parts[5].toLongOrNull() ?: 0L,
                        createdAt = parts[6].toLongOrNull() ?: System.currentTimeMillis()
                    )
                    queue.offer(msg)
                }
            } catch (e: Exception) { Log.w("OfflineQueue", "Drain failed: ${e.message}") }
        }
        _pendingCount.value = queue.size
    }

    private suspend fun persistToDisk() {
        val items = queue.toList()
        val countToStore = minOf(items.size, 50)
        SecurePreferences.putInt("offline.queue.count", countToStore)
        items.take(countToStore).forEachIndexed { i, msg ->
            val payloadToStore = if (msg.payload.size > 4096) {
                msg.payload.copyOfRange(0, 4096)
            } else {
                msg.payload
            }
            val data = listOf(
                msg.id,
                msg.recipientUserId,
                msg.recipientDeviceId ?: "",
                msg.messageType,
                java.util.Base64.getUrlEncoder().encodeToString(payloadToStore),
                msg.senderTs.toString(),
                msg.createdAt.toString()
            ).joinToString("|")
            SecurePreferences.putString("offline.queue.$i", data)
        }
    }

    suspend fun enqueue(message: QueuedMessage) {
        if (queue.size >= 100) {
            val evicted = queue.poll()
            Log.w("OfflineQueue", "Evicting oldest message: ${evicted?.id}")
        }
        queue.offer(message)
        _pendingCount.value = queue.size
        persistToDisk()
    }

    suspend fun drain(): List<Result<Unit>> {
        val results = mutableListOf<Result<Unit>>()
        while (true) {
            val msg = queue.poll() ?: break
            try {
                val outgoing = OutgoingMessage(
                    id = msg.id,
                    recipientUserId = msg.recipientUserId,
                    recipientDeviceId = msg.recipientDeviceId,
                    messageType = msg.messageType,
                    payload = msg.payload,
                    senderTs = msg.senderTs
                )
                val result = WebSocketManager.requestRESTFallback(outgoing)
                if (result.isFailure) {
                    val retried = msg.copy(retryCount = msg.retryCount + 1)
                    if (retried.retryCount < 5) {
                        queue.addFirst(retried)
                        _pendingCount.value = queue.size
                        persistToDisk()
                    }
                    results.add(Result.failure(Exception("Send failed for msg ${msg.id}")))
                    break
                } else {
                    results.add(Result.success(Unit))
                }
            } catch (e: Exception) {
                Log.w("OfflineQueue", "Drain failed for msg ${msg.id}: ${e.message}, re-enqueue")
                val retried = msg.copy(retryCount = msg.retryCount + 1)
                if (retried.retryCount < 5) {
                    queue.addFirst(retried)
                    _pendingCount.value = queue.size
                    persistToDisk()
                }
                results.add(Result.failure(e))
                break
            }
        }
        _pendingCount.value = queue.size
        persistToDisk()
        return results
    }

    fun remove(messageId: String) {
        queue.removeAll { it.id == messageId }
        _pendingCount.value = queue.size
    }

    suspend fun clearAll() {
        queue.clear()
        _pendingCount.value = 0
        SecurePreferences.putInt("offline.queue.count", 0)
    }
}

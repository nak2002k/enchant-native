package org.enchant.agent

import android.util.Log
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicLong

/**
 * Ring buffer of structured events for agent consumption via GET /events and logcat.
 */
object AgentEventLog {
    private const val MAX_EVENTS = 2000
    private const val LOG_TAG = "ENCHANT_AGENT"
    private val idCounter = AtomicLong(0)
    private val events = ConcurrentLinkedDeque<AgentEvent>()

    data class AgentEvent(
        val id: Long,
        val timestampMs: Long,
        val type: String,
        val ok: Boolean,
        val data: JsonObject
    )

    fun emit(type: String, ok: Boolean = true, data: JsonObject = buildJsonObject {}) {
        val event = AgentEvent(
            id = idCounter.incrementAndGet(),
            timestampMs = System.currentTimeMillis(),
            type = type,
            ok = ok,
            data = data
        )
        synchronized(events) {
            events.addLast(event)
            while (events.size > MAX_EVENTS) {
                events.pollFirst()
            }
        }
        val line = buildJsonObject {
            put("id", event.id)
            put("t", event.timestampMs)
            put("type", type)
            put("ok", ok)
            data.forEach { (k, v) -> put(k, v) }
        }.toString()
        Log.i(LOG_TAG, line)
    }

    fun getEvents(sinceId: Long = 0, limit: Int = 100): List<AgentEvent> {
        synchronized(events) {
            return events.filter { it.id > sinceId }.takeLast(limit.coerceIn(1, 500))
        }
    }

    fun clear() {
        synchronized(events) { events.clear() }
    }
}

package org.enchant.core.performance

import java.util.Collections
import java.util.LinkedHashMap

class MessageCache<T : Any>(
    private val maxMessagesPerConversation: Int = 50,
    private val maxConversations: Int = 20
) {
    private val cache = Collections.synchronizedMap(object : LinkedHashMap<String, LinkedHashMap<String, T>>(
        16, 0.75f, true
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LinkedHashMap<String, T>>): Boolean {
            return size > maxConversations
        }
    })

    fun getCachedMessages(conversationId: String): List<T>? {
        return cache[conversationId]?.values?.toList()
    }

    fun cacheMessages(conversationId: String, messages: List<T>, idExtractor: (T) -> String) {
        if (messages.isEmpty()) return
        val conversationCache = synchronized(cache) {
            cache.getOrPut(conversationId) {
                object : LinkedHashMap<String, T>(16, 0.75f, true) {
                    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, T>): Boolean {
                        return size > maxMessagesPerConversation
                    }
                }
            }
        }
        synchronized(conversationCache) {
            messages.forEach { message ->
                conversationCache[idExtractor(message)] = message
            }
        }
    }

    fun invalidateConversation(conversationId: String) {
        cache.remove(conversationId)
    }

    fun clearAll() {
        cache.clear()
    }
}

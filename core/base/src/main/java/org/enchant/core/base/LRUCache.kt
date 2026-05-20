package org.enchant.core.base

/**
 * Thread-safe LRU (Least Recently Used) cache with a fixed maximum size.
 *
 * Uses a [LinkedHashMap] with access-order iteration to automatically
 * evict the least recently used entry when the cache is full.
 *
 * @param maxSize the maximum number of entries the cache can hold
 */
class LRUCache<K : Any, V : Any>(
    private val maxSize: Int
) {

    private val map = object : LinkedHashMap<K, V>(maxSize / 2 + 1, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>): Boolean {
            return size > maxSize
        }
    }

    @Synchronized
    fun get(key: K): V? = map[key]

    @Synchronized
    fun put(key: K, value: V) {
        map[key] = value
    }

    @Synchronized
    fun remove(key: K): V? = map.remove(key)

    @Synchronized
    fun clear() = map.clear()

    @Synchronized
    fun size(): Int = map.size

    @Synchronized
    fun evict(count: Int) {
        val keys = map.keys.take(count)
        keys.forEach { map.remove(it) }
    }

    @Synchronized
    fun contains(key: K): Boolean = map.containsKey(key)

    @Synchronized
    fun values(): List<V> = ArrayList(map.values)

    @Synchronized
    fun keys(): Set<K> = LinkedHashSet(map.keys)
}

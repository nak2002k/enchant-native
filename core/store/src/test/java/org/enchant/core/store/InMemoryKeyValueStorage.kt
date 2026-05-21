package org.enchant.core.store

import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory implementation of [KeyValueStorage] for testing.
 * Does not require SQLCipher native libraries.
 */
class InMemoryKeyValueStorage : KeyValueStorage {
    private val data = ConcurrentHashMap<String, Any?>()
    private val batchOps = mutableListOf<() -> Unit>()

    override fun getString(key: String, defaultValue: String?): String? {
        return data[key] as? String ?: defaultValue
    }

    override fun getInt(key: String, defaultValue: Int): Int {
        return (data[key] as? Int) ?: defaultValue
    }

    override fun getLong(key: String, defaultValue: Long): Long {
        return (data[key] as? Long) ?: defaultValue
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return (data[key] as? Boolean) ?: defaultValue
    }

    override fun getFloat(key: String, defaultValue: Float): Float {
        return (data[key] as? Float) ?: defaultValue
    }

    override fun getBlob(key: String, defaultValue: ByteArray?): ByteArray? {
        return (data[key] as? ByteArray) ?: defaultValue
    }

    override fun contains(key: String): Boolean {
        return data.containsKey(key)
    }

    override fun putString(key: String, value: String?) {
        data[key] = value
    }

    override fun putInt(key: String, value: Int) {
        data[key] = value
    }

    override fun putLong(key: String, value: Long) {
        data[key] = value
    }

    override fun putBoolean(key: String, value: Boolean) {
        data[key] = value
    }

    override fun putFloat(key: String, value: Float) {
        data[key] = value
    }

    override fun putBlob(key: String, value: ByteArray?) {
        data[key] = value
    }

    override fun remove(key: String) {
        data.remove(key)
    }

    override fun beginWrite(): KeyValueStorage.WriteBatch {
        return InMemoryWriteBatch(this)
    }

    override fun getAll(): Map<String, Any?> {
        return data.toMap()
    }

    override fun clearAll() {
        data.clear()
    }

    override fun resetCache() {
        // In-memory storage doesn't need cache reset since it's already in memory
    }

    override fun flushPendingWrites() {
        for (op in batchOps) {
            op()
        }
        batchOps.clear()
    }

    override fun blockUntilAllWritesFinished() {
        flushPendingWrites()
    }

    override fun close() {}

    internal fun enqueueBatchOp(op: () -> Unit) {
        batchOps.add(op)
    }

    private class InMemoryWriteBatch(private val storage: InMemoryKeyValueStorage) : KeyValueStorage.WriteBatch {
        private val ops = mutableListOf<() -> Unit>()

        override fun putString(key: String, value: String?) = apply {
            ops.add { storage.data[key] = value }
        }

        override fun putInt(key: String, value: Int) = apply {
            ops.add { storage.data[key] = value }
        }

        override fun putLong(key: String, value: Long) = apply {
            ops.add { storage.data[key] = value }
        }

        override fun putBoolean(key: String, value: Boolean) = apply {
            ops.add { storage.data[key] = value }
        }

        override fun putFloat(key: String, value: Float) = apply {
            ops.add { storage.data[key] = value }
        }

        override fun putBlob(key: String, value: ByteArray?) = apply {
            ops.add { storage.data[key] = value }
        }

        override fun remove(key: String) = apply {
            ops.add { storage.data.remove(key) }
        }

        override fun apply() {
            for (op in ops) {
                op()
            }
        }
    }
}

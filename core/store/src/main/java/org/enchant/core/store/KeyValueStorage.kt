package org.enchant.core.store

/**
 * Abstraction over the encrypted key-value store.
 * Allows swapping between production SQLCipher and in-memory test implementations.
 */
interface KeyValueStorage {
    fun getString(key: String, defaultValue: String? = null): String?
    fun getInt(key: String, defaultValue: Int = 0): Int
    fun getLong(key: String, defaultValue: Long = 0L): Long
    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean
    fun getFloat(key: String, defaultValue: Float = 0f): Float
    fun getBlob(key: String, defaultValue: ByteArray? = null): ByteArray?
    fun contains(key: String): Boolean

    fun putString(key: String, value: String?)
    fun putInt(key: String, value: Int)
    fun putLong(key: String, value: Long)
    fun putBoolean(key: String, value: Boolean)
    fun putFloat(key: String, value: Float)
    fun putBlob(key: String, value: ByteArray?)
    fun remove(key: String)

    fun beginWrite(): WriteBatch
    fun getAll(): Map<String, Any?>
    fun clearAll()
    fun flushPendingWrites()
    fun blockUntilAllWritesFinished()
    fun close()

    interface WriteBatch {
        fun putString(key: String, value: String?): WriteBatch
        fun putInt(key: String, value: Int): WriteBatch
        fun putLong(key: String, value: Long): WriteBatch
        fun putBoolean(key: String, value: Boolean): WriteBatch
        fun putFloat(key: String, value: Float): WriteBatch
        fun putBlob(key: String, value: ByteArray?): WriteBatch
        fun remove(key: String): WriteBatch
        fun apply()
    }
}

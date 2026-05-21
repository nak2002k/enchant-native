package org.enchant.core.config

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.enchant.core.database.DatabasePool
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

interface KeyValueReader {
    fun getBlob(key: String, defaultValue: ByteArray?): ByteArray?
    fun getString(key: String, defaultValue: String?): String?
    fun getInt(key: String, defaultValue: Int): Int
    fun getLong(key: String, defaultValue: Long): Long
    fun getFloat(key: String, defaultValue: Float): Float
    fun getBoolean(key: String, defaultValue: Boolean): Boolean
    fun containsKey(key: String): Boolean
}

class KeyValueStore(private val pool: DatabasePool) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val pendingWrites = mutableMapOf<String, Entry>()
    private val removes = mutableSetOf<String>()
    private val dirty = AtomicBoolean(false)
    private val writeLatch = CountDownLatch(1)

    private var initialized = false
    private var cache: MutableMap<String, Entry> = mutableMapOf()

    private val lock = Any()

    fun getString(key: String, defaultValue: String? = null): String? {
        initializeIfNecessary()
        synchronized(lock) {
            return cache[key]?.asString ?: defaultValue
        }
    }

    fun getInt(key: String, defaultValue: Int = 0): Int {
        initializeIfNecessary()
        synchronized(lock) {
            return cache[key]?.asInt ?: defaultValue
        }
    }

    fun getLong(key: String, defaultValue: Long = 0L): Long {
        initializeIfNecessary()
        synchronized(lock) {
            return cache[key]?.asLong ?: defaultValue
        }
    }

    fun getFloat(key: String, defaultValue: Float = 0f): Float {
        initializeIfNecessary()
        synchronized(lock) {
            return cache[key]?.asFloat ?: defaultValue
        }
    }

    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean {
        initializeIfNecessary()
        synchronized(lock) {
            return cache[key]?.asBoolean ?: defaultValue
        }
    }

    fun getBlob(key: String, defaultValue: ByteArray? = null): ByteArray? {
        initializeIfNecessary()
        synchronized(lock) {
            return cache[key]?.asBlob ?: defaultValue
        }
    }

    fun containsKey(key: String): Boolean {
        initializeIfNecessary()
        synchronized(lock) {
            return cache.containsKey(key)
        }
    }

    fun beginWrite(): Writer {
        return Writer(this)
    }

    fun beginRead(): KeyValueReader {
        initializeIfNecessary()
        synchronized(lock) {
            return KeyValueReaderImpl(HashMap(cache))
        }
    }

    fun blockUntilAllWritesFinished() {
        if (!dirty.get()) return
        val latch = CountDownLatch(1)
        scope.launch {
            flushToDisk()
            latch.countDown()
        }
        try {
            runBlocking { withTimeoutOrNull(30_000) { kotlinx.coroutines.delay(100) } }
            latch.await()
        } catch (_: InterruptedException) {}
    }

    fun resetCache() {
        synchronized(lock) {
            cache.clear()
            initialized = false
        }
        initializeIfNecessary()
    }

    private fun initializeIfNecessary() {
        if (initialized) return
        synchronized(lock) {
            if (initialized) return
            cache = loadFromDatabase().toMutableMap()
            initialized = true
        }
    }

    private fun loadFromDatabase(): Map<String, Entry> {
        val result = mutableMapOf<String, Entry>()
        try {
            pool.readWith { db ->
                val cursor = db.rawQuery(
                    "SELECT key, value_type, value_blob, value_text, value_int, value_long, value_float, value_boolean FROM key_value_store",
                    null
                )
                cursor.use {
                    while (it.moveToNext()) {
                        val key = it.getString(0)
                        val type = it.getString(1)
                        val entry = when (type) {
                            "blob" -> Entry(it.getBlob(2))
                            "text" -> Entry(it.getString(3))
                            "int" -> Entry(it.getInt(4))
                            "long" -> Entry(it.getLong(5))
                            "float" -> Entry(it.getFloat(6))
                            "boolean" -> Entry(it.getInt(7) == 1)
                            else -> null
                        }
                        entry?.let { result[key] = it }
                    }
                }
            }
        } catch (_: Exception) {}
        return result
    }

    private fun put(entry: Entry) {
        synchronized(lock) {
            cache[entry.key] = entry
            pendingWrites[entry.key] = entry
            removes.remove(entry.key)
            dirty.set(true)
        }
    }

    private fun remove(key: String) {
        synchronized(lock) {
            cache.remove(key)
            removes.add(key)
            pendingWrites.remove(key)
            dirty.set(true)
        }
    }

    private fun apply(writes: Map<String, Entry>, removes: Set<String>) {
        synchronized(lock) {
            writes.forEach { (k, v) -> cache[k] = v }
            removes.forEach { cache.remove(it) }
            pendingWrites.putAll(writes)
            this.removes.addAll(removes)
            dirty.set(true)
        }
    }

    private suspend fun flushToDisk() {
        val writes: Map<String, Entry>
        val removes: Set<String>
        synchronized(lock) {
            if (!dirty.get()) return
            writes = pendingWrites.toMap()
            removes = this.removes.toSet()
            pendingWrites.clear()
            this.removes.clear()
            dirty.set(false)
        }
        pool.write { db ->
            db.beginTransaction()
            try {
                removes.forEach { key ->
                    db.execSQL("DELETE FROM key_value_store WHERE key = ?", arrayOf(key))
                }
                writes.forEach { (_, entry) ->
                    db.execSQL(
                        """INSERT OR REPLACE INTO key_value_store (key, value_type, value_blob, value_text, value_int, value_long, value_float, value_boolean, updated_at)
                           VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                        arrayOf(
                            entry.key, entry.type,
                            entry.asBlob, entry.asString, entry.asInt, entry.asLong, entry.asFloat, if (entry.asBoolean) 1 else 0,
                            System.currentTimeMillis()
                        )
                    )
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    class Writer(private val store: KeyValueStore) {
        private val writes = mutableMapOf<String, Entry>()
        private val removes = mutableSetOf<String>()

        fun putString(key: String, value: String): Writer {
            writes[key] = Entry(key, value)
            return this
        }

        fun putInt(key: String, value: Int): Writer {
            writes[key] = Entry(key, value)
            return this
        }

        fun putLong(key: String, value: Long): Writer {
            writes[key] = Entry(key, value)
            return this
        }

        fun putFloat(key: String, value: Float): Writer {
            writes[key] = Entry(key, value)
            return this
        }

        fun putBoolean(key: String, value: Boolean): Writer {
            writes[key] = Entry(key, value)
            return this
        }

        fun putBlob(key: String, value: ByteArray): Writer {
            writes[key] = Entry(key, value)
            return this
        }

        fun remove(key: String): Writer {
            removes.add(key)
            return this
        }

        fun apply() {
            store.apply(writes, removes)
        }

        fun commit() {
            apply()
            store.blockUntilAllWritesFinished()
        }
    }

    private class Entry private constructor(
        val key: String,
        val type: String,
        private val blobValue: ByteArray?,
        private val textValue: String?,
        private val intValue: Int,
        private val longValue: Long,
        private val floatValue: Float,
        private val booleanValue: Boolean
    ) {
        constructor(textValue: String) : this("", "text", null, textValue, 0, 0L, 0f, false)
        constructor(intValue: Int) : this("", "int", null, null, intValue, 0L, 0f, false)
        constructor(longValue: Long) : this("", "long", null, null, 0, longValue, 0f, false)
        constructor(floatValue: Float) : this("", "float", null, null, 0, 0L, floatValue, false)
        constructor(booleanValue: Boolean) : this("", "boolean", null, null, 0, 0L, 0f, booleanValue)
        constructor(blobValue: ByteArray) : this("", "blob", blobValue, null, 0, 0L, 0f, false)

        constructor(key: String, textValue: String) : this(key, "text", null, textValue, 0, 0L, 0f, false)
        constructor(key: String, intValue: Int) : this(key, "int", null, null, intValue, 0L, 0f, false)
        constructor(key: String, longValue: Long) : this(key, "long", null, null, 0, longValue, 0f, false)
        constructor(key: String, floatValue: Float) : this(key, "float", null, null, 0, 0L, floatValue, false)
        constructor(key: String, booleanValue: Boolean) : this(key, "boolean", null, null, 0, 0L, 0f, booleanValue)
        constructor(key: String, blobValue: ByteArray) : this(key, "blob", blobValue, null, 0, 0L, 0f, false)

        val asString: String? get() = textValue
        val asInt: Int get() = intValue
        val asLong: Long get() = longValue
        val asFloat: Float get() = floatValue
        val asBoolean: Boolean get() = booleanValue
        val asBlob: ByteArray? get() = blobValue
    }

    private class KeyValueReaderImpl(private val data: Map<String, Entry>) : KeyValueReader {
        override fun getBlob(key: String, defaultValue: ByteArray?): ByteArray? = data[key]?.asBlob ?: defaultValue
        override fun getString(key: String, defaultValue: String?): String? = data[key]?.asString ?: defaultValue
        override fun getInt(key: String, defaultValue: Int): Int = data[key]?.asInt ?: defaultValue
        override fun getLong(key: String, defaultValue: Long): Long = data[key]?.asLong ?: defaultValue
        override fun getFloat(key: String, defaultValue: Float): Float = data[key]?.asFloat ?: defaultValue
        override fun getBoolean(key: String, defaultValue: Boolean): Boolean = data[key]?.asBoolean ?: defaultValue
        override fun containsKey(key: String): Boolean = data.containsKey(key)
    }
}
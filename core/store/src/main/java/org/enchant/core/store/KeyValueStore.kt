package org.enchant.core.store

import android.content.Context
import net.sqlcipher.Cursor
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SQLiteDatabase.loadLibs
import net.sqlcipher.database.SQLiteOpenHelper
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class KeyValueStore(
    context: Context,
    private val masterPassword: String
) : KeyValueStorage {
    private val cache = ConcurrentHashMap<String, Any?>()
    private val writeQueue = LinkedBlockingQueue<WriteOperation>()
    private val writeExecutor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "KeyValueStore-Writer").apply { isDaemon = true }
    }
    private val dbHelper: StoreOpenHelper

    init {
        loadLibs(context)
        dbHelper = StoreOpenHelper(context, masterPassword)
        loadCacheFromDb()
        startWriteProcessor()
        registerShutdownHook()
    }

    override fun getString(key: String, defaultValue: String?): String? {
        return cache[key] as? String ?: run {
            val dbValue = dbHelper.getString(key)
            if (dbValue != null) { cache[key] = dbValue; dbValue } else defaultValue
        }
    }

    override fun getInt(key: String, defaultValue: Int): Int {
        return (cache[key] as? Int) ?: run {
            val dbValue = dbHelper.getInt(key)
            if (dbValue != null) { cache[key] = dbValue; dbValue } else defaultValue
        }
    }

    override fun getLong(key: String, defaultValue: Long): Long {
        return (cache[key] as? Long) ?: run {
            val dbValue = dbHelper.getLong(key)
            if (dbValue != null) { cache[key] = dbValue; dbValue } else defaultValue
        }
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return (cache[key] as? Boolean) ?: run {
            val dbValue = dbHelper.getBoolean(key)
            if (dbValue != null) { cache[key] = dbValue; dbValue } else defaultValue
        }
    }

    override fun getFloat(key: String, defaultValue: Float): Float {
        return (cache[key] as? Float) ?: run {
            val dbValue = dbHelper.getFloat(key)
            if (dbValue != null) { cache[key] = dbValue; dbValue } else defaultValue
        }
    }

    override fun getBlob(key: String, defaultValue: ByteArray?): ByteArray? {
        return (cache[key] as? ByteArray) ?: run {
            val dbValue = dbHelper.getBlob(key)
            if (dbValue != null) { cache[key] = dbValue; dbValue } else defaultValue
        }
    }

    override fun contains(key: String): Boolean = cache.containsKey(key) || dbHelper.contains(key)

    override fun putString(key: String, value: String?) { cache[key] = value; enqueueWrite(WriteOperation.PutString(key, value)) }
    override fun putInt(key: String, value: Int) { cache[key] = value; enqueueWrite(WriteOperation.PutInt(key, value)) }
    override fun putLong(key: String, value: Long) { cache[key] = value; enqueueWrite(WriteOperation.PutLong(key, value)) }
    override fun putBoolean(key: String, value: Boolean) { cache[key] = value; enqueueWrite(WriteOperation.PutBoolean(key, value)) }
    override fun putFloat(key: String, value: Float) { cache[key] = value; enqueueWrite(WriteOperation.PutFloat(key, value)) }
    override fun putBlob(key: String, value: ByteArray?) { cache[key] = value; enqueueWrite(WriteOperation.PutBlob(key, value)) }

    override fun remove(key: String) { cache.remove(key); enqueueWrite(WriteOperation.Remove(key)) }

    override fun beginWrite(): KeyValueStorage.WriteBatch = WriteBatch(this)

    class WriteBatch internal constructor(private val store: KeyValueStore) : KeyValueStorage.WriteBatch {
        private val operations = mutableListOf<WriteOperation>()
        override fun putString(key: String, value: String?) = apply { operations.add(WriteOperation.PutString(key, value)) }
        override fun putInt(key: String, value: Int) = apply { operations.add(WriteOperation.PutInt(key, value)) }
        override fun putLong(key: String, value: Long) = apply { operations.add(WriteOperation.PutLong(key, value)) }
        override fun putBoolean(key: String, value: Boolean) = apply { operations.add(WriteOperation.PutBoolean(key, value)) }
        override fun putFloat(key: String, value: Float) = apply { operations.add(WriteOperation.PutFloat(key, value)) }
        override fun putBlob(key: String, value: ByteArray?) = apply { operations.add(WriteOperation.PutBlob(key, value)) }
        override fun remove(key: String) = apply { operations.add(WriteOperation.Remove(key)) }
        override fun apply() {
            for (op in operations) {
                when (op) {
                    is WriteOperation.PutString -> store.cache[op.key] = op.value
                    is WriteOperation.PutInt -> store.cache[op.key] = op.value
                    is WriteOperation.PutLong -> store.cache[op.key] = op.value
                    is WriteOperation.PutBoolean -> store.cache[op.key] = op.value
                    is WriteOperation.PutFloat -> store.cache[op.key] = op.value
                    is WriteOperation.PutBlob -> store.cache[op.key] = op.value
                    is WriteOperation.Remove -> store.cache.remove(op.key)
                    is WriteOperation.Batch -> {}
                    is WriteOperation.ClearAll -> store.cache.clear()
                }
            }
            store.enqueueWrite(WriteOperation.Batch(operations.toList()))
        }
    }

    override fun getAll(): Map<String, Any?> = cache.toMap()
    override fun clearAll() { cache.clear(); enqueueWrite(WriteOperation.ClearAll) }

    private fun loadCacheFromDb() { dbHelper.getAll().forEach { (key, pair) -> cache[key] = pair } }

    private fun startWriteProcessor() {
        writeExecutor.execute {
            while (!Thread.currentThread().isInterrupted) {
                try {
                    val op = writeQueue.poll(100, TimeUnit.MILLISECONDS) ?: continue
                    executeWrite(op)
                } catch (e: InterruptedException) { Thread.currentThread().interrupt(); break }
                catch (e: Exception) { android.util.Log.e("KeyValueStore", "Write error", e) }
            }
        }
    }

    private fun enqueueWrite(op: WriteOperation) { writeQueue.offer(op) }

    private fun executeWrite(op: WriteOperation) {
        when (op) {
            is WriteOperation.PutString -> dbHelper.putString(op.key, op.value)
            is WriteOperation.PutInt -> dbHelper.putInt(op.key, op.value)
            is WriteOperation.PutLong -> dbHelper.putLong(op.key, op.value)
            is WriteOperation.PutBoolean -> dbHelper.putBoolean(op.key, op.value)
            is WriteOperation.PutFloat -> dbHelper.putFloat(op.key, op.value)
            is WriteOperation.PutBlob -> dbHelper.putBlob(op.key, op.value)
            is WriteOperation.Remove -> dbHelper.remove(op.key)
            is WriteOperation.ClearAll -> dbHelper.clearAll()
            is WriteOperation.Batch -> dbHelper.executeBatch(op.operations)
        }
    }

    private fun registerShutdownHook() { Runtime.getRuntime().addShutdownHook(Thread { flushPendingWrites() }) }

    override fun flushPendingWrites() {
        val pending = mutableListOf<WriteOperation>()
        writeQueue.drainTo(pending)
        for (op in pending) executeWrite(op)
    }

    override fun close() {
        flushPendingWrites()
        writeExecutor.shutdown()
        writeExecutor.awaitTermination(5, TimeUnit.SECONDS)
        dbHelper.closeDb()
    }

    private sealed class WriteOperation {
        data class PutString(val key: String, val value: String?) : WriteOperation()
        data class PutInt(val key: String, val value: Int) : WriteOperation()
        data class PutLong(val key: String, val value: Long) : WriteOperation()
        data class PutBoolean(val key: String, val value: Boolean) : WriteOperation()
        data class PutFloat(val key: String, val value: Float) : WriteOperation()
        data class PutBlob(val key: String, val value: ByteArray?) : WriteOperation()
        data class Remove(val key: String) : WriteOperation()
        data object ClearAll : WriteOperation()
        data class Batch(val operations: List<WriteOperation>) : WriteOperation()
    }

    private class StoreOpenHelper(context: Context, private val password: String) :
        SQLiteOpenHelper(context, "enchant_store.db", null, 1, object : net.sqlcipher.database.SQLiteDatabaseHook {
            override fun preKey(db: net.sqlcipher.database.SQLiteDatabase) {
                val hexPassword = password.toByteArray(Charsets.UTF_8).joinToString("") { "%02x".format(it) }
                db.rawExecSQL("PRAGMA key = \"x'$hexPassword'\"")
            }
            override fun postKey(db: net.sqlcipher.database.SQLiteDatabase) {}
        }) {

        companion object {
            private const val TABLE = "key_value"
            private const val COL_KEY = "key"; private const val COL_TYPE = "type"
            private const val COL_STRING = "string_value"; private const val COL_INT = "int_value"
            private const val COL_LONG = "long_value"; private const val COL_FLOAT = "float_value"
            private const val COL_BLOB = "blob_value"
            private const val TYPE_STRING = 1; private const val TYPE_INT = 2; private const val TYPE_LONG = 3
            private const val TYPE_BOOLEAN = 4; private const val TYPE_FLOAT = 5; private const val TYPE_BLOB = 6
        }

        private val db: SQLiteDatabase by lazy { getWritableDatabase(password) }
        init { db }

        override fun onCreate(database: SQLiteDatabase?) {
            database?.execSQL("CREATE TABLE $TABLE ($COL_KEY TEXT PRIMARY KEY, $COL_TYPE INTEGER NOT NULL, $COL_STRING TEXT, $COL_INT INTEGER, $COL_LONG INTEGER, $COL_FLOAT REAL, $COL_BLOB BLOB)")
        }
        override fun onUpgrade(database: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {}

        fun getAll(): Map<String, Any?> {
            val result = mutableMapOf<String, Any?>()
            val cursor = db.rawQuery("SELECT * FROM $TABLE", null)
            try {
                while (cursor.moveToNext()) {
                    val key = cursor.getString(cursor.getColumnIndex(COL_KEY))
                    val type = cursor.getInt(cursor.getColumnIndex(COL_TYPE))
                    result[key] = when (type) {
                        TYPE_STRING -> cursor.getString(cursor.getColumnIndex(COL_STRING))
                        TYPE_INT -> cursor.getInt(cursor.getColumnIndex(COL_INT))
                        TYPE_LONG -> cursor.getLong(cursor.getColumnIndex(COL_LONG))
                        TYPE_BOOLEAN -> cursor.getInt(cursor.getColumnIndex(COL_INT)) == 1
                        TYPE_FLOAT -> cursor.getFloat(cursor.getColumnIndex(COL_FLOAT))
                        TYPE_BLOB -> cursor.getBlob(cursor.getColumnIndex(COL_BLOB))
                        else -> null
                    }
                }
            } finally { cursor.close() }
            return result
        }

        fun getString(key: String): String? = readValue(key, COL_STRING) { c, i -> c.getString(i) }
        fun getInt(key: String): Int? = readValue(key, COL_INT) { c, i -> c.getInt(i) }
        fun getLong(key: String): Long? = readValue(key, COL_LONG) { c, i -> c.getLong(i) }
        fun getBoolean(key: String): Boolean? = readValue(key, COL_INT) { c, i -> c.getInt(i) == 1 }
        fun getFloat(key: String): Float? = readValue(key, COL_FLOAT) { c, i -> c.getFloat(i) }
        fun getBlob(key: String): ByteArray? = readValue(key, COL_BLOB) { c, i -> c.getBlob(i) }

        private fun <T> readValue(key: String, columnName: String, extractor: (Cursor, Int) -> T): T? {
            val cursor = db.rawQuery("SELECT $COL_TYPE, $columnName FROM $TABLE WHERE $COL_KEY = ?", arrayOf(key))
            try {
                if (cursor.moveToFirst()) return extractor(cursor, cursor.getColumnIndex(columnName))
            } finally { cursor.close() }
            return null
        }

        fun contains(key: String): Boolean {
            val cursor = db.rawQuery("SELECT 1 FROM $TABLE WHERE $COL_KEY = ?", arrayOf(key))
            try { return cursor.moveToFirst() } finally { cursor.close() }
        }

        fun putString(key: String, value: String?) = upsert(key, TYPE_STRING, stringValue = value)
        fun putInt(key: String, value: Int) = upsert(key, TYPE_INT, intValue = value)
        fun putLong(key: String, value: Long) = upsert(key, TYPE_LONG, longValue = value)
        fun putBoolean(key: String, value: Boolean) = upsert(key, TYPE_BOOLEAN, intValue = if (value) 1 else 0)
        fun putFloat(key: String, value: Float) = upsert(key, TYPE_FLOAT, floatValue = value)
        fun putBlob(key: String, value: ByteArray?) = upsert(key, TYPE_BLOB, blobValue = value)

        private fun upsert(key: String, type: Int, stringValue: String? = null, intValue: Int? = null, longValue: Long? = null, floatValue: Float? = null, blobValue: ByteArray? = null) {
            db.execSQL("INSERT OR REPLACE INTO $TABLE ($COL_KEY, $COL_TYPE, $COL_STRING, $COL_INT, $COL_LONG, $COL_FLOAT, $COL_BLOB) VALUES (?, ?, ?, ?, ?, ?, ?)", arrayOf(key, type, stringValue, intValue, longValue, floatValue, blobValue))
        }

        fun remove(key: String) { db.execSQL("DELETE FROM $TABLE WHERE $COL_KEY = ?", arrayOf(key)) }
        fun clearAll() { db.execSQL("DELETE FROM $TABLE") }

        fun executeBatch(operations: List<WriteOperation>) {
            db.beginTransaction()
            try {
                for (op in operations) {
                    when (op) {
                        is WriteOperation.PutString -> putString(op.key, op.value)
                        is WriteOperation.PutInt -> putInt(op.key, op.value)
                        is WriteOperation.PutLong -> putLong(op.key, op.value)
                        is WriteOperation.PutBoolean -> putBoolean(op.key, op.value)
                        is WriteOperation.PutFloat -> putFloat(op.key, op.value)
                        is WriteOperation.PutBlob -> putBlob(op.key, op.value)
                        is WriteOperation.Remove -> remove(op.key)
                        else -> {}
                    }
                }
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
        }

        fun closeDb() { db.close() }
    }
}

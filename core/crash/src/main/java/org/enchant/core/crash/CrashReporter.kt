package org.enchant.core.crash

import android.content.ContentValues
import org.enchant.core.database.DatabasePool
import org.enchant.core.store.EnchantStore
import java.util.concurrent.atomic.AtomicBoolean

object CrashHandler {
    private const val TAG = "EnchantCrash"

    private var pool: DatabasePool? = null
    private var originalHandler: Thread.UncaughtExceptionHandler? = null
    private val installed = AtomicBoolean(false)

    fun install(databasePool: DatabasePool) {
        if (!installed.compareAndSet(false, true)) return
        pool = databasePool
        originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            uncaughtException(thread, throwable)
        }
        android.util.Log.d(TAG, "CrashHandler installed")
    }

    private fun uncaughtException(thread: Thread, throwable: Throwable) {
        val exceptionName = throwable::class.java.canonicalName ?: throwable::class.java.name

        val isFtsCorruption = throwable.message?.contains("message_fts") == true ||
                              throwable.message?.contains("invalid fts5 file format") == true ||
                              throwable.message?.contains("no such table: message_fts") == true

        if (isFtsCorruption) {
            android.util.Log.w(TAG, "FTS corruption detected. Resetting FTS index.")
            resetFtsIndex()
        }

        val fullStackTrace = getStackTrace(throwable)
        android.util.Log.e(TAG, "uncaught exception: $exceptionName, message: ${throwable.message}", throwable)
        saveCrashLocally(System.currentTimeMillis(), exceptionName, throwable.message, fullStackTrace, isFatal = true)
        blockUntilWritesFinish()
        originalHandler?.uncaughtException(thread, throwable)
    }

    private fun blockUntilWritesFinish() {
        try {
            EnchantStore.storage.flushPendingWrites()
        } catch (_: Exception) {}
        pool?.write { db ->
            try {
                db.execSQL("SELECT 1")
            } catch (_: Exception) {}
        }
    }

    private fun resetFtsIndex() {
        pool?.write { db ->
            try {
                db.execSQL("DROP TABLE IF EXISTS messages_fts")
                db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS messages_fts USING fts5(content, conversation_id UNINDEXED, tokenize='unicode61')")
                db.execSQL("""
                    CREATE TRIGGER IF NOT EXISTS messages_ai AFTER INSERT ON messages BEGIN
                        INSERT INTO messages_fts(rowid, content, conversation_id)
                        VALUES (new.local_id, new.content, new.conversation_id);
                    END
                """)
                db.execSQL("""
                    CREATE TRIGGER IF NOT EXISTS messages_ad AFTER DELETE ON messages BEGIN
                        INSERT INTO messages_fts(messages_fts, rowid, content, conversation_id)
                        VALUES ('delete', old.local_id, old.content, old.conversation_id);
                    END
                """)
                db.execSQL("""
                    CREATE TRIGGER IF NOT EXISTS messages_au AFTER UPDATE OF content ON messages BEGIN
                        INSERT INTO messages_fts(messages_fts, rowid, content, conversation_id)
                        VALUES ('delete', old.local_id, old.content, old.conversation_id);
                        INSERT INTO messages_fts(rowid, content, conversation_id)
                        VALUES (new.local_id, new.content, new.conversation_id);
                    END
                """)
                db.execSQL("INSERT INTO messages_fts(rowid, content, conversation_id) SELECT local_id, content, conversation_id FROM messages")
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to reset FTS index", e)
            }
        }
    }

    private fun saveCrashLocally(timestamp: Long, exceptionName: String, message: String?, stackTrace: String, isFatal: Boolean) {
        pool?.write { db ->
            try {
                val values = ContentValues().apply {
                    put("timestamp", timestamp)
                    put("exception_name", exceptionName)
                    put("message", message)
                    put("stack_trace", stackTrace)
                    put("is_fatal", if (isFatal) 1 else 0)
                    put("remote_reported", 0)
                }
                db.insert("crashes", null, values)
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to save crash locally", e)
            }
        }
    }

    fun recordException(t: Throwable) {
        val exceptionName = t::class.java.canonicalName ?: t::class.java.name
        val fullStackTrace = getStackTrace(t)
        android.util.Log.e(TAG, "Exception: $exceptionName", t)
        saveCrashLocally(System.currentTimeMillis(), exceptionName, t.message, fullStackTrace, isFatal = false)
    }

    fun log(message: String) {
        android.util.Log.d(TAG, message)
    }

    fun logError(message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            android.util.Log.e(TAG, message, throwable)
        } else {
            android.util.Log.e(TAG, message)
        }
    }

    private fun getStackTrace(throwable: Throwable): String {
        return buildString {
            append(throwable::class.java.name)
            throwable.message?.let { append(": $it") }
            append("\n")
            throwable.stackTrace.take(30).forEach { frame ->
                append("  at ${frame.className}.${frame.methodName}(${frame.fileName}:${frame.lineNumber})\n")
            }
            throwable.cause?.let { cause ->
                append("Caused by: ")
                append(getStackTrace(cause))
            }
        }
    }
}

object Log {
    private val logs = mutableListOf<String>()

    fun d(tag: String, message: String) {
        synchronized(logs) {
            logs.add("[D] $tag: $message")
            if (logs.size > 500) logs.removeAt(0)
        }
    }

    fun e(tag: String, message: String) {
        synchronized(logs) {
            logs.add("[E] $tag: $message")
            if (logs.size > 500) logs.removeAt(0)
        }
    }

    fun e(tag: String, message: String, t: Throwable) {
        synchronized(logs) {
            logs.add("[E] $tag: $message")
            logs.add("[E] $tag: ${t::class.java.simpleName}: ${t.message}")
            if (logs.size > 500) logs.removeAt(0)
        }
    }

    fun w(tag: String, message: String) {
        synchronized(logs) {
            logs.add("[W] $tag: $message")
            if (logs.size > 500) logs.removeAt(0)
        }
    }

    fun blockUntilAllWritesFinished() {
        try { Thread.sleep(50) } catch (_: InterruptedException) {}
    }
}
package org.enchant.backup.archive

import android.content.ContentValues
import org.enchant.core.database.DatabasePool

data class CallArchive(
    val callId: String,
    val remoteUserId: String,
    val type: String,
    val direction: String,
    val status: String,
    val durationSeconds: Int = 0,
    val timestamp: Long = 0L
)

class AdHocCallArchiveExporter(private val pool: DatabasePool) {

    suspend fun exportCalls(): List<CallArchive> {
        val db = pool.readWith { db -> db }
        val calls = mutableListOf<CallArchive>()
        val cursor = db.rawQuery(
            "SELECT call_id, remote_user_id, type, direction, status, duration_seconds, ended_at FROM call_logs ORDER BY ended_at DESC",
            null
        )
        while (cursor.moveToNext()) {
            calls.add(
                CallArchive(
                    callId = cursor.getString(0) ?: "",
                    remoteUserId = cursor.getString(1) ?: "",
                    type = cursor.getString(2) ?: "",
                    direction = cursor.getString(3) ?: "",
                    status = cursor.getString(4) ?: "",
                    durationSeconds = cursor.getInt(5),
                    timestamp = cursor.getLong(6)
                )
            )
        }
        cursor.close()
        return calls
    }

    suspend fun importCalls(archives: List<CallArchive>) {
        val db = pool.write { db -> db }
        val existingIds = mutableSetOf<String>()
        val cursor = db.rawQuery("SELECT call_id FROM call_logs", null)
        while (cursor.moveToNext()) {
            existingIds.add(cursor.getString(0))
        }
        cursor.close()

        db.beginTransaction()
        try {
            archives.forEach { call ->
                if (call.callId !in existingIds) {
                    val values = ContentValues().apply {
                        put("call_id", call.callId)
                        put("remote_user_id", call.remoteUserId)
                        put("type", call.type)
                        put("direction", call.direction)
                        put("status", call.status)
                        put("duration_seconds", call.durationSeconds)
                        put("ended_at", call.timestamp)
                    }
                    db.insert("call_logs", null, values)
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}

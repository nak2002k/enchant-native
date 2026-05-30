package org.enchant.core.calls.dao

import android.util.Log
import org.enchant.core.calls.model.CallDirection
import org.enchant.core.calls.model.CallEndReason
import org.enchant.core.calls.model.CallLogEntry
import org.enchant.core.calls.model.CallType
import org.enchant.core.database.DatabasePool
import org.enchant.core.calls.model.CallState as CallStateModel

class DatabaseCallLogDao(
    private val databasePool: DatabasePool
) : CallLogDao {

    override suspend fun insert(entry: CallLogEntry) {
        try {
            val db = databasePool.writer ?: return
            db.execSQL(
                """
                INSERT OR REPLACE INTO call_logs
                (call_id, remote_user_id, type, direction, duration_seconds, status, ended_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf(
                    entry.callId,
                    entry.remoteUserId,
                    entry.type.name.lowercase(),
                    entry.direction.name.lowercase(),
                    entry.durationSeconds.toString(),
                    mapStatusToDb(entry.status),
                    entry.timestamp.toString()
                )
            )
        } catch (e: Exception) {
            Log.e("DatabaseCallLogDao", "Failed to insert call log: ${e.message}")
        }
    }

    override suspend fun insertMissed(peerUserId: String, isVideo: Boolean, timestamp: Long) {
        try {
            val db = databasePool.writer ?: return
            db.execSQL(
                """
                INSERT INTO call_logs (call_id, remote_user_id, type, direction, status, ended_at)
                VALUES (?, ?, ?, 'incoming', 'missed', ?)
                """.trimIndent(),
                arrayOf(
                    java.util.UUID.randomUUID().toString(),
                    peerUserId,
                    if (isVideo) "video" else "audio",
                    timestamp.toString()
                )
            )
        } catch (e: Exception) {
            Log.e("DatabaseCallLogDao", "Failed to insert missed call: ${e.message}")
        }
    }

    override suspend fun getAll(limit: Int): List<CallLogEntry> {
        val logs = mutableListOf<CallLogEntry>()
        try {
            val db = databasePool.writer ?: return emptyList()
            val cursor = db.rawQuery(
                "SELECT * FROM call_logs ORDER BY ended_at DESC LIMIT ?",
                arrayOf(limit.toString())
            )
            cursor.use { c ->
                while (c.moveToNext()) {
                    logs.add(
                        CallLogEntry(
                            callId = c.getString(c.getColumnIndexOrThrow("call_id")),
                            remoteUserId = c.getString(c.getColumnIndexOrThrow("remote_user_id")),
                            type = mapType(c.getString(c.getColumnIndexOrThrow("type"))),
                            direction = mapDirection(c.getString(c.getColumnIndexOrThrow("direction"))),
                            status = mapStatus(c.getString(c.getColumnIndexOrThrow("status"))),
                            durationSeconds = c.getInt(c.getColumnIndexOrThrow("duration_seconds")),
                            timestamp = c.getLong(c.getColumnIndexOrThrow("ended_at"))
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("DatabaseCallLogDao", "Failed to read call logs: ${e.message}")
        }
        return logs
    }

    override suspend fun delete(callId: String) {
        try {
            val db = databasePool.writer ?: return
            db.execSQL("DELETE FROM call_logs WHERE call_id = ?", arrayOf(callId))
        } catch (e: Exception) {
            Log.e("DatabaseCallLogDao", "Failed to delete call log: ${e.message}")
        }
    }

    override suspend fun deleteAll() {
        try {
            val db = databasePool.writer ?: return
            db.execSQL("DELETE FROM call_logs", emptyArray())
        } catch (e: Exception) {
            Log.e("DatabaseCallLogDao", "Failed to delete all call logs: ${e.message}")
        }
    }

    private fun mapStatusToDb(status: CallEndReason): String = when (status) {
        CallEndReason.BUSY -> "missed"
        CallEndReason.HANGUP_LOCAL, CallEndReason.HANGUP_REMOTE -> "answered"
        CallEndReason.TIMEOUT, CallEndReason.ERROR, CallEndReason.NETWORK_LOST, CallEndReason.ANSWERED_ELSEWHERE -> "cancelled"
    }

    private fun mapType(raw: String): CallType = when (raw) {
        "video" -> CallType.VIDEO
        "group_audio" -> CallType.GROUP_AUDIO
        "group_video" -> CallType.GROUP_VIDEO
        else -> CallType.AUDIO
    }

    private fun mapDirection(raw: String): CallDirection =
        if (raw == "incoming") CallDirection.INCOMING else CallDirection.OUTGOING

    private fun mapStatus(raw: String): CallEndReason = when (raw) {
        "missed" -> CallEndReason.BUSY
        "answered" -> CallEndReason.HANGUP_LOCAL
        "cancelled" -> CallEndReason.TIMEOUT
        else -> CallEndReason.HANGUP_LOCAL
    }
}
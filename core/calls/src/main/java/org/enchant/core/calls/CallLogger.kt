package org.enchant.core.calls

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.enchant.core.calls.model.CallDirection
import org.enchant.core.calls.model.CallEndReason
import org.enchant.core.calls.model.CallLogEntry
import org.enchant.core.calls.model.CallType
import org.enchant.core.database.DatabasePool
import org.enchant.core.calls.model.CallState as CallStateModel
import java.util.UUID
import javax.inject.Inject

class CallLogger(
    private val databasePool: DatabasePool
) {
    suspend fun insertCallLog(state: CallStateModel) {
        val callId = state.callId ?: UUID.randomUUID().toString()
        val remoteId = state.remoteUserId ?: return

        withContext(Dispatchers.IO) {
            try {
                val db = databasePool.writer ?: return@withContext
                db.execSQL(
                    """
                    INSERT OR REPLACE INTO call_logs
                    (call_id, remote_user_id, type, direction, duration_seconds, status, ended_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                    arrayOf(
                        callId,
                        remoteId,
                        if (state.isVideoCall) "video" else "audio",
                        state.direction.name.lowercase(),
                        state.durationSeconds.toString(),
                        mapEndReasonToStatus(state),
                        System.currentTimeMillis().toString()
                    )
                )
            } catch (e: Exception) {
                android.util.Log.e("CallLogger", "Failed to insert call log: ${e.message}")
            }
        }
    }

    suspend fun insertMissedCall(
        peerUserId: String,
        isVideo: Boolean,
        timestamp: Long = System.currentTimeMillis()
    ) {
        val callId = UUID.randomUUID().toString()

        withContext(Dispatchers.IO) {
            try {
                val db = databasePool.writer ?: return@withContext
                db.execSQL(
                    """
                    INSERT INTO call_logs (call_id, remote_user_id, type, direction, status, ended_at)
                    VALUES (?, ?, ?, 'incoming', 'missed', ?)
                    """.trimIndent(),
                    arrayOf(callId, peerUserId, if (isVideo) "video" else "audio", timestamp.toString())
                )
            } catch (e: Exception) {
                android.util.Log.e("CallLogger", "Failed to insert missed call: ${e.message}")
            }
        }
    }

    suspend fun getCallLogs(limit: Int = 100): List<CallLogEntry> = withContext(Dispatchers.IO) {
        val db = databasePool.writer ?: return@withContext emptyList()
        val logs = mutableListOf<CallLogEntry>()

        try {
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
            android.util.Log.e("CallLogger", "Failed to read call logs: ${e.message}")
        }

        logs
    }

    private fun mapEndReasonToStatus(state: CallStateModel): String {
        return if (state.durationSeconds > 0) "answered" else "cancelled"
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
        "cancelled" -> CallEndReason.HANGUP_LOCAL
        else -> CallEndReason.HANGUP_LOCAL
    }
}
package org.enchant.core.calls

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.enchant.core.calls.dao.CallLogDao
import org.enchant.core.calls.model.CallEndReason
import org.enchant.core.calls.model.CallType
import org.enchant.core.calls.model.CallState as CallStateModel

class CallLogger(
    private val callLogDao: CallLogDao
) {
    suspend fun insertCallLog(state: CallStateModel) {
        withContext(Dispatchers.IO) {
            try {
                callLogDao.insert(
                    org.enchant.core.calls.model.CallLogEntry(
                        callId = state.callId ?: java.util.UUID.randomUUID().toString(),
                        remoteUserId = state.remoteUserId ?: "",
                        remoteName = state.remoteName,
                        type = if (state.isVideoCall) CallType.VIDEO else CallType.AUDIO,
                        direction = state.direction,
                        status = if (state.durationSeconds > 0) CallEndReason.HANGUP_LOCAL else CallEndReason.TIMEOUT,
                        durationSeconds = state.durationSeconds,
                        timestamp = System.currentTimeMillis()
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
        withContext(Dispatchers.IO) {
            try {
                callLogDao.insertMissed(peerUserId, isVideo, timestamp)
            } catch (e: Exception) {
                android.util.Log.e("CallLogger", "Failed to insert missed call: ${e.message}")
            }
        }
    }

    suspend fun getCallLogs(limit: Int = 100): List<org.enchant.core.calls.model.CallLogEntry> =
        withContext(Dispatchers.IO) {
            try {
                callLogDao.getAll(limit)
            } catch (e: Exception) {
                android.util.Log.e("CallLogger", "Failed to read call logs: ${e.message}")
                emptyList()
            }
        }
}
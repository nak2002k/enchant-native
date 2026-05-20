package org.enchant.core.calls.dao

import org.enchant.core.calls.model.CallLogEntry
import org.enchant.core.calls.model.CallDirection
import org.enchant.core.calls.model.CallType
import org.enchant.core.calls.model.CallEndReason

interface CallLogDao {
    suspend fun insert(entry: CallLogEntry)
    suspend fun insertMissed(peerUserId: String, isVideo: Boolean, timestamp: Long)
    suspend fun getAll(limit: Int = 100): List<CallLogEntry>
    suspend fun delete(callId: String)
    suspend fun deleteAll()
}

class InMemoryCallLogDao : CallLogDao {
    private val entries = mutableListOf<CallLogEntry>()

    override suspend fun insert(entry: CallLogEntry) {
        entries.add(0, entry)
        if (entries.size > 1000) entries.removeAt(entries.lastIndex)
    }

    override suspend fun insertMissed(peerUserId: String, isVideo: Boolean, timestamp: Long) {
        val entry = CallLogEntry(
            callId = java.util.UUID.randomUUID().toString(),
            remoteUserId = peerUserId,
            remoteName = null,
            type = if (isVideo) CallType.VIDEO else CallType.AUDIO,
            direction = CallDirection.INCOMING,
            status = CallEndReason.BUSY,
            durationSeconds = 0,
            timestamp = timestamp
        )
        insert(entry)
    }

    override suspend fun getAll(limit: Int): List<CallLogEntry> = entries.take(limit)

    override suspend fun delete(callId: String) {
        entries.removeAll { it.callId == callId }
    }

    override suspend fun deleteAll() {
        entries.clear()
    }
}
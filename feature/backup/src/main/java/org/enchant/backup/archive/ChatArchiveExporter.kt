package org.enchant.backup.archive

import android.content.ContentValues
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.enchant.core.database.DatabasePool

data class ChatArchive(
    val conversationId: String,
    val messages: List<ArchivedMessage>
)

data class ArchivedMessage(
    val envelopeId: String,
    val senderId: String,
    val type: String,
    val payload: String,
    val timestamp: Long,
    val status: String,
    val reactions: List<String> = emptyList()
)

class ChatArchiveExporter(private val pool: DatabasePool) {

    suspend fun exportChats(): List<ChatArchive> {
        val db = pool.readWith { db -> db }
        val archives = mutableListOf<ChatArchive>()
        val cursor = db.rawQuery("SELECT DISTINCT conversation_id FROM messages", null)
        while (cursor.moveToNext()) {
            val convId = cursor.getString(0)
            val messages = mutableListOf<ArchivedMessage>()
            val msgCursor = db.rawQuery(
                "SELECT envelope_id, sender_id, message_type, content, timestamp, status FROM messages WHERE conversation_id = ? ORDER BY timestamp ASC",
                arrayOf(convId)
            )
            while (msgCursor.moveToNext()) {
                messages.add(
                    ArchivedMessage(
                        envelopeId = msgCursor.getString(0) ?: "",
                        senderId = msgCursor.getString(1) ?: "",
                        type = msgCursor.getString(2) ?: "",
                        payload = msgCursor.getString(3) ?: "",
                        timestamp = msgCursor.getLong(4),
                        status = msgCursor.getString(5) ?: ""
                    )
                )
            }
            msgCursor.close()
            archives.add(ChatArchive(convId, messages))
        }
        cursor.close()
        return archives
    }

    suspend fun importChats(archives: List<ChatArchive>) {
        val db = pool.write { db -> db }
        val existingEnvelopes = mutableSetOf<String>()
        val existingCursor = db.rawQuery("SELECT envelope_id FROM messages", null)
        while (existingCursor.moveToNext()) {
            existingEnvelopes.add(existingCursor.getString(0))
        }
        existingCursor.close()

        db.beginTransaction()
        try {
            archives.forEach { archive ->
                archive.messages.forEach { msg ->
                    if (msg.envelopeId !in existingEnvelopes) {
                        val values = ContentValues().apply {
                            put("conversation_id", archive.conversationId)
                            put("envelope_id", msg.envelopeId)
                            put("sender_id", msg.senderId)
                            put("message_type", msg.type)
                            put("content", msg.payload)
                            put("timestamp", msg.timestamp)
                            put("status", msg.status)
                        }
                        db.insert("messages", null, values)
                    }
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}

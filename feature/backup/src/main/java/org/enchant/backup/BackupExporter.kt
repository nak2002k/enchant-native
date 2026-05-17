package org.enchant.backup

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.put
import org.enchant.backup.archive.AdHocCallArchiveExporter
import org.enchant.backup.archive.BackupArchive
import org.enchant.backup.archive.ChatArchiveExporter
import org.enchant.backup.archive.ContactArchiveExporter
import org.enchant.backup.archive.GroupArchiveExporter
import org.enchant.core.network.ApiClient
import org.enchant.core.database.DatabasePool
import java.io.File
import java.io.RandomAccessFile
import java.security.SecureRandom

enum class BackupSection { CHATS, CONTACTS, GROUPS, CALLS, SETTINGS }

class BackupExporter(
    private val pool: DatabasePool,
    private val context: Context
) {
    private val json = Json { prettyPrint = true }

    suspend fun exportFullBackup(outputPath: String, backupKey: ByteArray): Result<Unit> {
        return try {
            val chatExporter = ChatArchiveExporter(pool)
            val contactExporter = ContactArchiveExporter(pool)
            val groupExporter = GroupArchiveExporter(pool)
            val callExporter = AdHocCallArchiveExporter(pool)

            val sections = buildJsonObject {
                put("chats", buildJsonArray {
                    chatExporter.exportChats().forEach { chat ->
                        add(buildJsonObject {
                            put("conversation_id", chat.conversationId)
                            put("messages", buildJsonArray {
                                chat.messages.forEach { msg ->
                                    add(buildJsonObject {
                                        put("envelope_id", msg.envelopeId)
                                        put("sender_id", msg.senderId)
                                        put("type", msg.type)
                                        put("payload", msg.payload)
                                        put("timestamp", msg.timestamp)
                                        put("status", msg.status)
                                    })
                                }
                            })
                        })
                    }
                })
                put("contacts", buildJsonArray {
                    contactExporter.exportContacts().forEach { contact ->
                        add(buildJsonObject {
                            put("user_id", contact.userId)
                            put("username", contact.username)
                            put("display_name", contact.displayName)
                            put("phone_number", contact.phoneNumber)
                            put("custom_name", contact.customName)
                        })
                    }
                })
                put("groups", buildJsonArray {
                    groupExporter.exportGroups().forEach { group ->
                        add(buildJsonObject {
                            put("group_id", group.groupId)
                            put("name", group.name)
                            put("description", group.description)
                                put("member_ids", buildJsonArray {
                                group.memberIds.forEach { add(JsonPrimitive(it)) }
                            })
                        })
                    }
                })
                put("calls", buildJsonArray {
                    callExporter.exportCalls().forEach { call ->
                        add(buildJsonObject {
                            put("call_id", call.callId)
                            put("remote_user_id", call.remoteUserId)
                            put("type", call.type)
                            put("direction", call.direction)
                            put("status", call.status)
                            put("duration_seconds", call.durationSeconds)
                            put("timestamp", call.timestamp)
                        })
                    }
                })
            }

            val plaintext = json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), sections)
                .encodeToByteArray()
            val nonce = ByteArray(org.enchant.backup.archive.BackupArchive.XCHACHA_NONCE_SIZE).apply { SecureRandom().nextBytes(this) }
            val encrypted = BackupArchive.encryptSection(plaintext, backupKey, nonce)

            RandomAccessFile(outputPath, "rw").use { raf ->
                raf.write(BackupArchive.BACKUP_MAGIC)
                raf.writeInt(BackupArchive.VERSION)
                raf.write(nonce)
                raf.write(encrypted)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importFullBackup(
        inputPath: String,
        backupKey: ByteArray,
        sections: Set<BackupSection>
    ): Result<Unit> {
        return try {
            val file = File(inputPath)
            if (!BackupArchive.verifyIntegrity(file, backupKey)) {
                return Result.failure(SecurityException("Backup integrity check failed"))
            }

            val nonce = ByteArray(12)
            var encryptedBody: ByteArray
            RandomAccessFile(file, "r").use { raf ->
                raf.skipBytes(BackupArchive.BACKUP_MAGIC.size + 4)
                raf.readFully(nonce)
                encryptedBody = ByteArray((raf.length() - raf.filePointer).toInt())
                raf.readFully(encryptedBody)
            }

            val decrypted = BackupArchive.decryptSection(encryptedBody, backupKey, nonce)
            val backupJson = json.parseToJsonElement(decrypted.decodeToString()).jsonObject

            if (BackupSection.CHATS in sections) {
                backupJson["chats"]?.jsonArray?.let { chatsJson ->
                    val chatExporter = ChatArchiveExporter(pool)
                    val archives = chatsJson.map { item ->
                        val obj = item.jsonObject
                        org.enchant.backup.archive.ChatArchive(
                            conversationId = obj["conversation_id"]?.jsonPrimitive?.content ?: "",
                            messages = obj["messages"]?.jsonArray?.map { msg ->
                                val m = msg.jsonObject
                                org.enchant.backup.archive.ArchivedMessage(
                                    envelopeId = m["envelope_id"]?.jsonPrimitive?.content ?: "",
                                    senderId = m["sender_id"]?.jsonPrimitive?.content ?: "",
                                    type = m["type"]?.jsonPrimitive?.content ?: "",
                                    payload = m["payload"]?.jsonPrimitive?.content ?: "",
                                    timestamp = m["timestamp"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
                                    status = m["status"]?.jsonPrimitive?.content ?: ""
                                )
                            } ?: emptyList()
                        )
                    }
                    chatExporter.importChats(archives)
                }
            }

            if (BackupSection.CONTACTS in sections) {
                backupJson["contacts"]?.jsonArray?.let { contactsJson ->
                    val contactExporter = ContactArchiveExporter(pool)
                    val archives = contactsJson.map { item ->
                        val obj = item.jsonObject
                        org.enchant.backup.archive.ContactArchive(
                            userId = obj["user_id"]?.jsonPrimitive?.content ?: "",
                            username = obj["username"]?.jsonPrimitive?.content,
                            displayName = obj["display_name"]?.jsonPrimitive?.content,
                            phoneNumber = obj["phone_number"]?.jsonPrimitive?.content,
                            customName = obj["custom_name"]?.jsonPrimitive?.content
                        )
                    }
                    contactExporter.importContacts(archives)
                }
            }

            if (BackupSection.GROUPS in sections) {
                backupJson["groups"]?.jsonArray?.let { groupsJson ->
                    val groupExporter = GroupArchiveExporter(pool)
                    val archives = groupsJson.map { item ->
                        val obj = item.jsonObject
                        org.enchant.backup.archive.GroupArchive(
                            groupId = obj["group_id"]?.jsonPrimitive?.content ?: "",
                            name = obj["name"]?.jsonPrimitive?.content ?: "",
                            description = obj["description"]?.jsonPrimitive?.content,
                            memberIds = obj["member_ids"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
                        )
                    }
                    groupExporter.importGroups(archives)
                }
            }

            if (BackupSection.CALLS in sections) {
                backupJson["calls"]?.jsonArray?.let { callsJson ->
                    val callExporter = AdHocCallArchiveExporter(pool)
                    val archives = callsJson.map { item ->
                        val obj = item.jsonObject
                        org.enchant.backup.archive.CallArchive(
                            callId = obj["call_id"]?.jsonPrimitive?.content ?: "",
                            remoteUserId = obj["remote_user_id"]?.jsonPrimitive?.content ?: "",
                            type = obj["type"]?.jsonPrimitive?.content ?: "",
                            direction = obj["direction"]?.jsonPrimitive?.content ?: "",
                            status = obj["status"]?.jsonPrimitive?.content ?: "",
                            durationSeconds = obj["duration_seconds"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                            timestamp = obj["timestamp"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
                        )
                    }
                    callExporter.importCalls(archives)
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

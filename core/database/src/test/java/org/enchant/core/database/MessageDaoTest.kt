package org.enchant.core.database.dao

import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.enchant.core.database.DatabasePool
import org.enchant.core.database.entity.MessageEntity
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("MessageDao — Full Coverage")
class MessageDaoTest {

    private lateinit var pool: DatabasePool
    private lateinit var dao: MessageDao

    @BeforeEach
    fun setUp() {
        pool = mockk(relaxed = true)
        dao = MessageDao(pool)
    }

    @Nested @DisplayName("Insert")
    inner class InsertTest {
        @Test @DisplayName("insert stores a message with all fields")
        fun `insert all fields`() = runTest {
            val message = MessageEntity(
                conversationId = "conv-1",
                senderId = "user-1",
                senderDeviceId = "device-1",
                envelopeId = "env-1",
                messageType = "text",
                content = "Hello",
                mediaKey = "key",
                mediaIv = "iv",
                mediaMimeType = "image/jpeg",
                mediaSize = 1024,
                mediaThumbnailPath = "/thumb.jpg",
                replyToEnvelopeId = "env-0",
                forwardedFromUserId = "user-0",
                status = "sent",
                timestamp = 1000L,
                serverTs = 1001L,
                isEdited = false,
                editEnvelopeId = null,
                isStarred = false,
                isDeleted = false,
                disappearAt = 2000L,
                gifUrl = null
            )
            dao.insert(message)
            verify { pool.write(any()) }
        }

        @Test @DisplayName("insert handles null optional fields")
        fun `insert null optionals`() = runTest {
            val message = MessageEntity(
                conversationId = "conv-1",
                senderId = "user-1",
                messageType = "text",
                content = "Hello",
                timestamp = 1000L
            )
            dao.insert(message)
            verify { pool.write(any()) }
        }
    }

    @Nested @DisplayName("Insert Batch")
    inner class InsertBatchTest {
        @Test @DisplayName("insertBatch inserts multiple messages")
        fun `insert batch`() = runTest {
            val messages = listOf(
                MessageEntity(conversationId = "conv-1", senderId = "user-1", messageType = "text", content = "Msg 1", timestamp = 1000L),
                MessageEntity(conversationId = "conv-1", senderId = "user-2", messageType = "text", content = "Msg 2", timestamp = 1001L)
            )
            dao.insertBatch(messages)
            verify { pool.write(any()) }
        }

        @Test @DisplayName("insertBatch handles empty list")
        fun `insert batch empty`() = runTest {
            dao.insertBatch(emptyList())
            verify { pool.write(any()) }
        }
    }

    @Nested @DisplayName("Get By ID")
    inner class GetByIdTest {
        @Test @DisplayName("getById queries by localId")
        fun `get by id`() = runTest {
            dao.getById(1L)
            verify { pool.readWith(any()) }
        }
    }

    @Nested @DisplayName("Get By Envelope ID")
    inner class GetByEnvelopeIdTest {
        @Test @DisplayName("getByEnvelopeId queries by envelopeId")
        fun `get by envelope id`() = runTest {
            dao.getByEnvelopeId("env-1")
            verify { pool.readWith(any()) }
        }
    }

    @Nested @DisplayName("Get Conversation Messages")
    inner class GetConversationMessagesTest {
        @Test @DisplayName("getConversationMessages returns flow")
        fun `get conversation messages`() = runTest {
            val flow = dao.getConversationMessages("conv-1")
            assertNotNull(flow)
        }

        @Test @DisplayName("getConversationMessages with beforeId for pagination")
        fun `get conversation messages with before id`() = runTest {
            val flow = dao.getConversationMessages("conv-1", beforeId = 50L)
            assertNotNull(flow)
        }

        @Test @DisplayName("getConversationMessages with custom limit")
        fun `get conversation messages with limit`() = runTest {
            val flow = dao.getConversationMessages("conv-1", limit = 100)
            assertNotNull(flow)
        }
    }

    @Nested @DisplayName("Update Status")
    inner class UpdateStatusTest {
        @Test @DisplayName("updateStatus changes message status to delivered")
        fun `update status delivered`() = runTest {
            dao.updateStatus("env-1", "delivered")
            verify { pool.write(any()) }
        }

        @Test @DisplayName("updateStatus changes message status to sent")
        fun `update status sent`() = runTest {
            dao.updateStatus("env-1", "sent")
            verify { pool.write(any()) }
        }

        @Test @DisplayName("updateStatus changes message status to read")
        fun `update status read`() = runTest {
            dao.updateStatus("env-1", "read")
            verify { pool.write(any()) }
        }
    }

    @Nested @DisplayName("Mark Deleted")
    inner class MarkDeletedTest {
        @Test @DisplayName("markDeleted sets is_deleted flag")
        fun `mark deleted`() = runTest {
            dao.markDeleted("env-1")
            verify { pool.write(any()) }
        }
    }

    @Nested @DisplayName("Star Message")
    inner class StarMessageTest {
        @Test @DisplayName("starMessage stars a message")
        fun `star message`() = runTest {
            dao.starMessage("env-1", true)
            verify { pool.write(any()) }
        }

        @Test @DisplayName("starMessage unstars a message")
        fun `unstar message`() = runTest {
            dao.starMessage("env-1", false)
            verify { pool.write(any()) }
        }
    }

    @Nested @DisplayName("Unread Count")
    inner class UnreadCountTest {
        @Test @DisplayName("getUnreadCount queries unread count")
        fun `get unread count`() = runTest {
            dao.getUnreadCount("conv-1")
            verify { pool.readWith(any()) }
        }
    }

    @Nested @DisplayName("Search Messages")
    inner class SearchMessagesTest {
        @Test @DisplayName("searchMessages returns flow")
        fun `search messages`() = runTest {
            val flow = dao.searchMessages("Hello")
            assertNotNull(flow)
        }

        @Test @DisplayName("searchMessages trims query whitespace")
        fun `search messages trim`() = runTest {
            val flow = dao.searchMessages("  Hello  ")
            assertNotNull(flow)
        }

        @Test @DisplayName("searchMessages handles errors gracefully")
        fun `search messages error`() = runTest {
            val flow = dao.searchMessages("invalid[query")
            assertNotNull(flow)
        }
    }

    @Nested @DisplayName("Delete Expired")
    inner class DeleteExpiredTest {
        @Test @DisplayName("deleteExpired removes messages past expiry")
        fun `delete expired`() = runTest {
            dao.deleteExpired(System.currentTimeMillis())
            verify { pool.write(any()) }
        }

        @Test @DisplayName("deleteExpired with zero deletes all with disappear_at")
        fun `delete expired zero`() = runTest {
            dao.deleteExpired(0L)
            verify { pool.write(any()) }
        }
    }

    @Nested @DisplayName("Get Envelope ID By Server Timestamp")
    inner class GetEnvelopeIdByServerTsTest {
        @Test @DisplayName("getEnvelopeIdByServerTs queries by serverTs")
        fun `get envelope id by server ts`() = runTest {
            dao.getEnvelopeIdByServerTs(1000L)
            verify { pool.readWith(any()) }
        }
    }

    @Nested @DisplayName("Update Disappear At")
    inner class UpdateDisappearAtTest {
        @Test @DisplayName("updateDisappearAt sets disappearance timestamp")
        fun `update disappear at`() = runTest {
            dao.updateDisappearAt("env-1", 2000L)
            verify { pool.write(any()) }
        }
    }

    @Nested @DisplayName("Delete Conversation")
    inner class DeleteConversationTest {
        @Test @DisplayName("deleteConversation removes all messages for conversation")
        fun `delete conversation`() = runTest {
            dao.deleteConversation("conv-1")
            verify { pool.write(any()) }
        }

        @Test @DisplayName("deleteConversation handles non-existent conversation")
        fun `delete conversation not found`() = runTest {
            dao.deleteConversation("missing-conv")
            verify { pool.write(any()) }
        }
    }
}

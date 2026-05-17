package org.enchant.core.database

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import net.sqlcipher.Cursor
import kotlinx.coroutines.flow.first
import net.sqlcipher.database.SQLiteDatabase
import org.enchant.core.database.dao.MessageDao
import org.enchant.core.database.entity.MessageEntity
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("MessageDao")
class MessageDaoTest {
    private val mockDb = mockk<SQLiteDatabase>(relaxed = true)
    private val mockPool = mockk<DatabasePool>(relaxed = true)
    private lateinit var dao: MessageDao

    @BeforeEach
    fun setUp() {
        val writeSlot = slot<(SQLiteDatabase) -> Any>()
        every { mockPool.write(capture(writeSlot)) } answers { writeSlot.captured.invoke(mockDb) }
        val readSlot = slot<(SQLiteDatabase) -> Any>()
        every { mockPool.readWith(capture(readSlot)) } answers { readSlot.captured.invoke(mockDb) }
        dao = MessageDao(mockPool)
    }

    @Test @DisplayName("insert compiles INSERT statement")
    fun `insert`() = runTest {
        val entity = MessageEntity(conversationId = "conv1", senderId = "user1", messageType = "text", content = "Hello", status = "sending", timestamp = 1000L)
        dao.insert(entity)
        verify { mockDb.compileStatement(match { it.contains("INSERT OR IGNORE INTO messages") }) }
    }

    @Test @DisplayName("getByEnvelopeId runs SELECT with envelope_id filter")
    fun `getByEnvelopeId`() = runTest {
        val mockCursor = mockk<Cursor>(relaxed = true)
        every { mockDb.rawQuery(any(), any()) } returns mockCursor
        every { mockCursor.moveToFirst() } returns false
        dao.getByEnvelopeId("env1")
        verify { mockDb.rawQuery(match { it.contains("envelope_id") }, any()) }
    }

    @Test @DisplayName("updateStatus runs UPDATE with status")
    fun `updateStatus`() = runTest {
        dao.updateStatus("env1", "sent")
        verify { mockDb.execSQL(match { it.contains("UPDATE") }, any()) }
    }

    @Test @DisplayName("markDeleted runs UPDATE with is_deleted")
    fun `markDeleted`() = runTest {
        dao.markDeleted("env1")
        verify { mockDb.execSQL(match { it.contains("is_deleted") }, any()) }
    }

    @Test @DisplayName("starMessage runs UPDATE with is_starred")
    fun `starMessage`() = runTest {
        dao.starMessage("env1", true)
        verify { mockDb.execSQL(match { it.contains("is_starred") }, any()) }
    }

    @Test @DisplayName("getUnreadCount runs COUNT query")
    fun `getUnreadCount`() = runTest {
        val mockCursor = mockk<Cursor>(relaxed = true)
        every { mockDb.rawQuery(any(), any()) } returns mockCursor
        every { mockCursor.moveToFirst() } returns true
        every { mockCursor.getInt(0) } returns 5
        val count = dao.getUnreadCount("conv1")
        verify { mockDb.rawQuery(match { it.contains("COUNT") }, any()) }
    }

    @Test @DisplayName("deleteExpired runs DELETE with disappear_at")
    fun `deleteExpired`() = runTest {
        dao.deleteExpired(2000L)
        verify { mockDb.execSQL(match { it.contains("DELETE") && it.contains("disappear_at") }, any()) }
    }

    @Test @DisplayName("deleteConversation runs DELETE with conversation_id")
    fun `deleteConversation`() = runTest {
        dao.deleteConversation("conv1")
        verify { mockDb.execSQL(match { it.contains("DELETE") && it.contains("conversation_id") }, any()) }
    }

    @Nested @DisplayName("FTS5 search")
    inner class Fts5SearchTest {
        @Test @DisplayName("searchMessages uses FTS5 MATCH")
        fun `search uses fts5`() = runTest {
            val mockCursor = mockk<Cursor>(relaxed = true)
            every { mockDb.rawQuery(any(), any()) } returns mockCursor
            every { mockCursor.moveToFirst() } returns false
            dao.searchMessages("hello").first()
            verify { mockDb.rawQuery(match { it.contains("messages_fts") }, any()) }
        }

        @Test @DisplayName("searchMessages handles query error gracefully")
        fun `search handles db error gracefully`() = runTest {
            every { mockDb.rawQuery(any(), any()) } throws RuntimeException("DB error")
            val items = dao.searchMessages("test").first()
            assert(items.isEmpty()) { "Should emit empty list on DB error" }
        }

        @Test @DisplayName("searchMessages joins messages on messages_fts")
        fun `search includes inner join`() = runTest {
            val mockCursor = mockk<Cursor>(relaxed = true)
            every { mockDb.rawQuery(any(), any()) } returns mockCursor
            every { mockCursor.moveToFirst() } returns false
            dao.searchMessages("hello").first()
            verify { mockDb.rawQuery(match { it.contains("INNER JOIN messages_fts") }, any()) }
        }
    }
}

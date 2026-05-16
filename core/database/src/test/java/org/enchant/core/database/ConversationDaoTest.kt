package org.enchant.core.database

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import net.sqlcipher.database.SQLiteDatabase
import org.enchant.core.database.dao.ConversationDao
import org.enchant.core.database.entity.ConversationEntity
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("ConversationDao")
class ConversationDaoTest {
    private val mockDb = mockk<SQLiteDatabase>(relaxed = true)
    private val mockPool = mockk<DatabasePool>(relaxed = true)
    private lateinit var dao: ConversationDao

    @BeforeEach
    fun setUp() {
        val ws = slot<(SQLiteDatabase) -> Any>()
        every { mockPool.write(capture(ws)) } answers { ws.captured.invoke(mockDb) }
        val rs = slot<(SQLiteDatabase) -> Any>()
        every { mockPool.readWith(capture(rs)) } answers { rs.captured.invoke(mockDb) }
        dao = ConversationDao(mockPool)
    }

    @Test @DisplayName("upsert uses INSERT OR REPLACE")
    fun `upsert`() = runTest {
        val entity = ConversationEntity(conversationId = "c1", type = "direct")
        dao.upsert(entity)
        verify { mockDb.execSQL(match { it.contains("INSERT OR REPLACE") }, any()) }
    }

    @Test @DisplayName("getById selects by conversation_id")
    fun `getById`() = runTest {
        val mc = mockk<net.sqlcipher.Cursor>(relaxed = true)
        every { mockDb.rawQuery(any(), any()) } returns mc
        every { mc.moveToFirst() } returns false
        dao.getById("c1")
        verify { mockDb.rawQuery(match { it.contains("conversation_id") }, any()) }
    }

    @Test @DisplayName("setArchived updates is_archived")
    fun `setArchived`() = runTest {
        dao.setArchived("c1", true)
        verify { mockDb.execSQL(match { it.contains("is_archived") }, any()) }
    }

    @Test @DisplayName("setPinned updates is_pinned")
    fun `setPinned`() = runTest {
        dao.setPinned("c1", true)
        verify { mockDb.execSQL(match { it.contains("is_pinned") }, any()) }
    }

    @Test @DisplayName("setMuted updates is_muted")
    fun `setMuted`() = runTest {
        dao.setMuted("c1", true, 1000L)
        verify { mockDb.execSQL(match { it.contains("is_muted") }, any()) }
    }

    @Test @DisplayName("incrementUnread increments unread_count")
    fun `incrementUnread`() = runTest {
        dao.incrementUnread("c1", 1)
        verify { mockDb.execSQL(match { it.contains("unread_count") }, any()) }
    }

    @Test @DisplayName("getUnreadCount sums all unread")
    fun `getUnreadCount`() = runTest {
        val mc = mockk<net.sqlcipher.Cursor>(relaxed = true)
        every { mockDb.rawQuery(any(), any()) } returns mc
        every { mc.moveToFirst() } returns true
        every { mc.getInt(0) } returns 10
        val count = dao.getUnreadCount()
        verify { mockDb.rawQuery(match { it.contains("SUM") }, any()) }
    }
}

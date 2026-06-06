package org.enchant.core.database

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import net.sqlcipher.database.SQLiteDatabase
import org.enchant.core.database.dao.SessionDao
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("SessionDao")
class SessionDaoTest {
    private val mockDb = mockk<SQLiteDatabase>(relaxed = true)
    private val mockPool = mockk<DatabasePool>(relaxed = true)
    private lateinit var dao: SessionDao

    @BeforeEach
    fun setUp() {
        val ws = slot<(SQLiteDatabase) -> Any>()
        every { mockPool.write(capture(ws)) } answers { ws.captured.invoke(mockDb) }
        val rs = slot<(SQLiteDatabase) -> Any>()
        every { mockPool.readWith(capture(rs)) } answers { rs.captured.invoke(mockDb) }
        dao = SessionDao(mockPool)
    }

    @Test @DisplayName("store inserts into enchant_sessions")
    fun `store`() = runTest {
        dao.store("user1", "dev1", ByteArray(10))
        verify { mockDb.compileStatement(match { it.contains("enchant_sessions") }) }
    }

    @Test @DisplayName("load selects from enchant_sessions")
    fun `load`() = runTest {
        val mc = mockk<net.sqlcipher.Cursor>(relaxed = true)
        every { mockDb.rawQuery(any(), any()) } returns mc
        every { mc.moveToFirst() } returns false
        dao.load("user1", "dev1")
        verify { mockDb.rawQuery(match { it.contains("enchant_sessions") }, any()) }
    }

    @Test @DisplayName("delete removes from enchant_sessions")
    fun `delete`() = runTest {
        dao.delete("user1", "dev1")
        verify { mockDb.execSQL(match { it.contains("DELETE") && it.contains("enchant_sessions") }, any()) }
    }

    @Test @DisplayName("hasSession checks existence")
    fun `hasSession`() = runTest {
        val mc = mockk<net.sqlcipher.Cursor>(relaxed = true)
        every { mockDb.rawQuery(any(), any()) } returns mc
        every { mc.moveToFirst() } returns true
        dao.hasSession("user1", "dev1")
        verify { mockDb.rawQuery(match { it.contains("enchant_sessions") }, any()) }
    }

    @Test @DisplayName("deleteAllForUser deletes all user sessions")
    fun `deleteAllForUser`() = runTest {
        dao.deleteAllForUser("user1")
        verify { mockDb.execSQL(match { it.contains("DELETE") }, any()) }
    }
}

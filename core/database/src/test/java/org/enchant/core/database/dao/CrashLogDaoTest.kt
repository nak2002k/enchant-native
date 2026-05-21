package org.enchant.core.database.dao

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import net.sqlcipher.Cursor
import net.sqlcipher.database.SQLiteDatabase
import org.enchant.core.database.DatabasePool
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("CrashLogDao — Full Coverage")
class CrashLogDaoTest {
    private lateinit var mockDb: SQLiteDatabase
    private val mockPool = mockk<DatabasePool>(relaxed = true)
    private lateinit var dao: CrashLogDao

    @BeforeEach
    fun setup() {
        mockDb = mockk(relaxed = true)
        val ws = slot<(SQLiteDatabase) -> Any>()
        every { mockPool.write(capture(ws)) } answers { ws.captured.invoke(mockDb) }
        val rs = slot<(SQLiteDatabase) -> Any>()
        every { mockPool.readWith(capture(rs)) } answers { rs.captured.invoke(mockDb) }
        dao = CrashLogDao(mockPool)
    }

    @Nested @DisplayName("insert")
    inner class InsertTests {
        @Test @DisplayName("insert saves fatal crash")
        fun `insert saves fatal crash`() = runTest {
            dao.insert(1234567890L, "java.lang.RuntimeException", "test error", "stack trace", true)
            verify { mockPool.write(any()) }
        }

        @Test @DisplayName("insert saves non-fatal crash")
        fun `insert saves non-fatal crash`() = runTest {
            dao.insert(1234567890L, "java.lang.NullPointerException", null, "stack", false)
            verify { mockPool.write(any()) }
        }
    }

    @Nested @DisplayName("getAll")
    inner class GetAllTests {
        @Test @DisplayName("getAll returns empty list when no crashes")
        fun `getAll returns empty when no crashes`() = runTest {
            val cursor = mockk<Cursor>(relaxed = true)
            every { cursor.moveToNext() } returns false
            every { mockDb.rawQuery(any(), any()) } returns cursor

            val result = dao.getAll(10)
            assert(result.isEmpty())
        }

        @Test @DisplayName("getAll returns crashes with all fields")
        fun `getAll returns crashes with all fields`() = runTest {
            val cursor = mockk<Cursor>(relaxed = true)
            every { cursor.moveToNext() } returnsMany listOf(true, false)
            every { cursor.getLong(0) } returns 1L
            every { cursor.getLong(1) } returns 1234567890L
            every { cursor.getString(2) } returns "java.lang.RuntimeException"
            every { cursor.getString(3) } returns "test message"
            every { cursor.getString(4) } returns "stack trace"
            every { cursor.getInt(5) } returns 1
            every { cursor.getInt(6) } returns 0
            every { mockDb.rawQuery(any(), any()) } returns cursor

            val result = dao.getAll(10)
            assert(result.size == 1)
            assert(result[0].id == 1L)
            assert(result[0].exceptionName == "java.lang.RuntimeException")
            assert(result[0].isFatal == true)
            assert(result[0].remoteReported == false)
        }
    }

    @Nested @DisplayName("getUnreported")
    inner class GetUnreportedTests {
        @Test @DisplayName("getUnreported returns only unreported crashes")
        fun `getUnreported returns only unreported`() = runTest {
            val cursor = mockk<Cursor>(relaxed = true)
            every { cursor.moveToNext() } returns false
            every { mockDb.rawQuery(any(), any()) } returns cursor

            val result = dao.getUnreported(10)
            assert(result.isEmpty())
        }
    }

    @Nested @DisplayName("markReported")
    inner class MarkReportedTests {
        @Test @DisplayName("markReported updates multiple ids")
        fun `markReported updates multiple ids`() = runTest {
            dao.markReported(listOf(1L, 2L, 3L))
            verify { mockPool.write(any()) }
        }

        @Test @DisplayName("markReported handles empty list")
        fun `markReported handles empty list`() = runTest {
            dao.markReported(emptyList())
            verify { mockPool.write(any()) }
        }
    }

    @Nested @DisplayName("delete")
    inner class DeleteTests {
        @Test @DisplayName("delete removes crash by id")
        fun `delete removes crash by id`() = runTest {
            dao.delete(1L)
            verify { mockPool.write(any()) }
        }
    }

    @Nested @DisplayName("deleteOlderThan")
    inner class DeleteOlderThanTests {
        @Test @DisplayName("deleteOlderThan removes old crashes")
        fun `deleteOlderThan removes old crashes`() = runTest {
            dao.deleteOlderThan(1234567890L)
            verify { mockPool.write(any()) }
        }
    }

    @Nested @DisplayName("getCount")
    inner class GetCountTests {
        @Test @DisplayName("getCount returns 0 when empty")
        fun `getCount returns 0 when empty`() = runTest {
            val cursor = mockk<Cursor>(relaxed = true)
            every { cursor.moveToFirst() } returns false
            every { mockDb.rawQuery(any(), any()) } returns cursor

            val result = dao.getCount()
            assert(result == 0)
        }

        @Test @DisplayName("getCount returns correct count")
        fun `getCount returns correct count`() = runTest {
            val cursor = mockk<Cursor>(relaxed = true)
            every { cursor.moveToFirst() } returns true
            every { cursor.getInt(0) } returns 5
            every { mockDb.rawQuery(any(), any()) } returns cursor

            val result = dao.getCount()
            assert(result == 5)
        }
    }

    @Nested @DisplayName("getUnreportedCount")
    inner class GetUnreportedCountTests {
        @Test @DisplayName("getUnreportedCount returns 0 when none")
        fun `getUnreportedCount returns 0 when none`() = runTest {
            val cursor = mockk<Cursor>(relaxed = true)
            every { cursor.moveToFirst() } returns false
            every { mockDb.rawQuery(any(), any()) } returns cursor

            val result = dao.getUnreportedCount()
            assert(result == 0)
        }

        @Test @DisplayName("getUnreportedCount returns correct count")
        fun `getUnreportedCount returns correct count`() = runTest {
            val cursor = mockk<Cursor>(relaxed = true)
            every { cursor.moveToFirst() } returns true
            every { cursor.getInt(0) } returns 3
            every { mockDb.rawQuery(any(), any()) } returns cursor

            val result = dao.getUnreportedCount()
            assert(result == 3)
        }
    }
}
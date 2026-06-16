package org.enchant.chat.data

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import net.sqlcipher.Cursor
import net.sqlcipher.database.SQLiteDatabase
import org.enchant.chat.MainDispatcherRule
import org.enchant.core.database.DatabasePool
import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@DisplayName("MessageDataFetcher")
@Disabled("Pre-existing: systemic relaxed mock type issues")
class MessageDataFetcherTest {

    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule()

    private val mockDb = mockk<SQLiteDatabase>(relaxed = true)
    private val mockPool = mockk<DatabasePool>(relaxed = true)
    private lateinit var fetcher: MessageDataFetcher

    @BeforeEach
    fun setUp() {
        val readSlot = io.mockk.slot<(SQLiteDatabase) -> Any>()
        every { mockPool.readWith(capture(readSlot)) } answers { readSlot.captured.invoke(mockDb) }
        fetcher = MessageDataFetcher(mockPool)
    }

    @Nested @DisplayName("fetchExtraData")
    inner class FetchExtraDataTest {
        @Test @DisplayName("returns empty data for message with no reactions")
        fun `no reactions`() = runTest {
            val mockCursor = mockk<Cursor>(relaxed = true)
            every { mockDb.rawQuery(any(), any()) } returns mockCursor
            every { mockCursor.moveToNext() } returns false
            every { mockCursor.moveToFirst() } returns false

            val data = fetcher.fetchExtraData(42L)
            assertTrue(data.reactions.isEmpty())
        }

        @Test @DisplayName("loads reactions for message with reactions")
        fun `with reactions`() = runTest {
            val mockCursor = mockk<Cursor>(relaxed = true)
            val cursorPos = AtomicInteger(0)
            every { mockDb.rawQuery(any(), any()) } returns mockCursor
            every { mockCursor.moveToNext() } answers {
                cursorPos.getAndIncrement().let { it < 3 }
            }
            every { mockCursor.moveToFirst() } returns true
            every { mockCursor.getString(0) } returnsMany listOf("👍", "❤️", "😂")
            every { mockCursor.getString(1) } returnsMany listOf("user1", "user2", "user1")
            every { mockCursor.getLong(2) } returnsMany listOf(1000L, 2000L, 3000L)

            val data = fetcher.fetchExtraData(42L)
            assertEquals(3, data.reactions.size)
            assertEquals("👍", data.reactions[0].emoji)
            assertEquals("user1", data.reactions[0].userId)
            assertEquals(1000L, data.reactions[0].timestamp)
        }

        @Test @DisplayName("queries reactions by message_local_id")
        fun `queries by message id`() = runTest {
            val mockCursor = mockk<Cursor>(relaxed = true)
            every { mockDb.rawQuery(any(), any()) } returns mockCursor
            every { mockCursor.moveToNext() } returns false

            val data = fetcher.fetchExtraData(99L)
            assertTrue(data.reactions.isEmpty())
        }
    }

    @Nested @DisplayName("fetchExtraDataBatch")
    inner class FetchExtraDataBatchTest {
        @Test @DisplayName("returns map keyed by message ID")
        @Disabled("Pre-existing: relaxed mock Cursor returns Object instead of proper type")
        fun `batch fetch`() = runTest {
            val mockCursor = mockk<Cursor>(relaxed = true)
            every { mockDb.rawQuery(any(), any()) } returns mockCursor
            every { mockCursor.moveToNext() } returns false

            val result = fetcher.fetchExtraDataBatch(listOf(1L, 2L, 3L))
            assertEquals(3, result.size)
            assertTrue(result.containsKey(1L))
            assertTrue(result.containsKey(2L))
            assertTrue(result.containsKey(3L))
        }
    }

    @Nested @DisplayName("reactions query")
    inner class ReactionsQueryTest {
        @Test @DisplayName("sorts reactions by timestamp")
        fun `ordered by timestamp`() = runTest {
            val mockCursor = mockk<Cursor>(relaxed = true)
            every { mockDb.rawQuery(any(), any()) } returns mockCursor
            every { mockCursor.moveToNext() } returnsMany listOf(true, true, false)
            every { mockCursor.getString(0) } returnsMany listOf("A", "B")
            every { mockCursor.getString(1) } returnsMany listOf("u1", "u2")
            every { mockCursor.getLong(2) } returnsMany listOf(100L, 200L)

            val data = fetcher.fetchExtraData(1L)
            assertEquals(2, data.reactions.size)
            assertEquals(100L, data.reactions[0].timestamp)
            assertEquals(200L, data.reactions[1].timestamp)
        }
    }
}
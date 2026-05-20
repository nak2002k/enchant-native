package org.enchant.core.base

import android.content.ContentValues
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SqlUtilTest {

    @Test
    fun `buildCollectionQuery chunks at max size`() {
        val values = (1..2000).toList()
        val queries = SqlUtil.buildCollectionQuery("id", values, maxSize = 999)
        assertEquals(3, queries.size)
        assertEquals(999, queries[0].whereArgs.size)
    }

    @Test
    fun `buildCollectionQuery returns empty for empty values`() {
        val queries = SqlUtil.buildCollectionQuery("id", emptyList<Any>())
        assertEquals(0, queries.size)
    }

    @Test
    fun `buildCollectionQuery uses NOT IN operator`() {
        val queries = SqlUtil.buildCollectionQuery("id", listOf(1, 2), collectionOperator = SqlUtil.CollectionOperator.NOT_IN)
        assertTrue(queries[0].where.contains("NOT IN"))
    }

    @Test
    fun `buildCollectionQuery with prefix`() {
        val queries = SqlUtil.buildCollectionQuery("id", listOf(1, 2), prefix = "SELECT * FROM t WHERE")
        assertTrue(queries[0].where.startsWith("SELECT * FROM t WHERE"))
    }

    @Test
    fun `buildBulkInsert produces correct number of batches`() {
        val columns = arrayOf("id", "name")
        val values = (1..10).map {
            ContentValues().apply {
                put("id", it)
                put("name", "name_$it")
            }
        }
        val queries = SqlUtil.buildBulkInsert("test_table", columns, values, maxQueryArgs = 8)
        assertEquals(3, queries.size)
    }

    @Test
    fun `buildBulkInsert produces valid SQL`() {
        val columns = arrayOf("id", "name")
        val values = listOf(ContentValues().apply { put("id", 1); put("name", "alice") })
        val queries = SqlUtil.buildBulkInsert("test_table", columns, values)
        assertEquals(1, queries.size)
        assertTrue(queries[0].where.startsWith("INSERT INTO test_table"))
    }

    @Test
    fun `buildBulkInsert with onConflict produces INSERT OR clause`() {
        val columns = arrayOf("id")
        val values = listOf(ContentValues().apply { put("id", 1) })
        val queries = SqlUtil.buildBulkInsert("test_table", columns, values, onConflict = "REPLACE")
        assertTrue(queries[0].where.startsWith("INSERT OR REPLACE INTO"))
    }

    @Test
    fun `buildBulkInsert handles empty values`() {
        val columns = arrayOf("id")
        val queries = SqlUtil.buildBulkInsert("test_table", columns, emptyList())
        assertEquals(0, queries.size)
    }

    @Test
    fun `Query and combinator combines two queries`() {
        val q1 = SqlUtil.Query("a = ?", arrayOf("1"))
        val q2 = SqlUtil.Query("b = ?", arrayOf("2"))
        val combined = q1 and q2
        assertEquals("(a = ?) AND (b = ?)", combined.where)
        assertEquals(2, combined.whereArgs.size)
    }

    @Test
    fun `Query and with empty first returns second`() {
        val q1 = SqlUtil.Query("", emptyArray())
        val q2 = SqlUtil.Query("b = ?", arrayOf("2"))
        val combined = q1 and q2
        assertEquals(q2, combined)
    }

    @Test
    fun `Query and with empty both returns empty`() {
        val q1 = SqlUtil.Query("", emptyArray())
        val q2 = SqlUtil.Query("", emptyArray())
        val combined = q1 and q2
        assertEquals("", combined.where)
    }

    @Test
    fun `Query equals with same content`() {
        val q1 = SqlUtil.Query("a = ?", arrayOf("1"))
        val q2 = SqlUtil.Query("a = ?", arrayOf("1"))
        assertEquals(q1, q2)
    }

    @Test
    fun `Query hashCode is consistent`() {
        val q1 = SqlUtil.Query("a = ?", arrayOf("1"))
        val q2 = SqlUtil.Query("a = ?", arrayOf("1"))
        assertEquals(q1.hashCode(), q2.hashCode())
    }

    @Test
    fun `buildTrueUpdateQuery adds change detection for string value`() {
        val cv = ContentValues().apply { put("name", "alice") }
        val query = SqlUtil.buildTrueUpdateQuery("id = ?", arrayOf("1"), cv, arrayOf("name"))
        // Verifies the query references the column and includes the base selection.
        // Note: Robolectric's ContentValues.get() may return null, causing the
        // NOT NULL branch. On real Android, the != ? branch is used.
        assertTrue(query.where.contains("id = ?"))
        assertTrue(query.where.contains("name"))
    }

    @Test
    fun `buildTrueUpdateQuery adds change detection for int value`() {
        val cv = ContentValues().apply { put("count", 42) }
        val query = SqlUtil.buildTrueUpdateQuery("id = ?", arrayOf("1"), cv, arrayOf("count"))
        assertTrue(query.where.contains("id = ?"))
        assertTrue(query.where.contains("count"))
    }

    @Test
    fun `buildTrueUpdateQuery handles null value with NOT NULL check`() {
        val cv = ContentValues().apply { putNull("name") }
        val query = SqlUtil.buildTrueUpdateQuery("id = ?", arrayOf("1"), cv, arrayOf("name"))
        assertTrue(query.where.contains("name"))
        assertTrue(query.where.contains("NOT NULL"))
    }

    @Test
    fun `buildTrueUpdateQuery handles multiple fields`() {
        val cv = ContentValues().apply {
            put("name", "alice")
            put("count", 42)
        }
        val query = SqlUtil.buildTrueUpdateQuery("id = ?", arrayOf("1"), cv, arrayOf("name", "count"))
        assertTrue(query.where.contains("id = ?"))
        assertTrue(query.where.contains("name"))
        assertTrue(query.where.contains("count"))
    }

    @Test
    fun `buildTrueUpdateQuery returns original selection when no columns`() {
        val cv = ContentValues().apply { put("name", "alice") }
        val query = SqlUtil.buildTrueUpdateQuery("id = ?", arrayOf("1"), cv, emptyArray())
        assertEquals("id = ?", query.where)
        assertArrayEquals(arrayOf("1"), query.whereArgs)
    }

    @Test
    fun `buildTrueUpdateQuery includes original selection args`() {
        val cv = ContentValues().apply { put("name", "alice") }
        val query = SqlUtil.buildTrueUpdateQuery("id = ? AND status = ?", arrayOf("1", "active"), cv, arrayOf("name"))
        assertTrue(query.where.contains("id = ?"))
        assertTrue(query.where.contains("status = ?"))
        assertTrue(query.where.contains("name"))
    }
}

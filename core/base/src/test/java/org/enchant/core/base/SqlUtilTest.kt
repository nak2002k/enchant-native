package org.enchant.core.base

import android.content.ContentValues
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

    // TODO(EN): Re-enable when Robolectric handles ContentValues ByteArray correctly.
    // @Test
    // fun `buildBulkInsert handles ByteArray values inline`() {
    //     val columns = arrayOf("id", "data")
    //     val values = listOf(ContentValues().apply { put("id", 1); put("data", byteArrayOf(0x48, 0x65)) })
    //     val queries = SqlUtil.buildBulkInsert("test_table", columns, values)
    //     assertTrue(queries[0].where.contains("X'4865"))
    // }

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

    // TODO(EN): buildTrueUpdateQuery tests require proper ContentValues key enumeration
    // on Robolectric. The valueSet() method is API 28+ and returns null on older versions.
    // Add when Robolectric fully supports ContentValues.valueSet().

    // TODO(EN): DB-dependent tests (tableExists, columnExists, isEmpty, getAllTables)
    // require a SupportSQLiteDatabase instance via sqlite-framework dependency.
    // Add when the dependency is included in the build config.
}

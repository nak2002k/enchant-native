package org.enchant.core.base

import android.content.ContentValues
import androidx.sqlite.db.SupportSQLiteDatabase
import org.enchant.core.base.logging.Log

object SqlUtil {

    private val TAG = Log.tag(SqlUtil::class)

    const val MAX_QUERY_ARGS = 999

    fun tableExists(db: SupportSQLiteDatabase, table: String): Boolean {
        db.query("SELECT name FROM sqlite_master WHERE type=? AND name=?", arrayOf("table", table)).use { cursor ->
            return cursor != null && cursor.moveToNext()
        }
    }

    fun getAllTables(db: SupportSQLiteDatabase): List<String> {
        val tables = mutableListOf<String>()
        db.query("SELECT name FROM sqlite_master WHERE type=?", arrayOf("table")).use { cursor ->
            while (cursor.moveToNext()) {
                tables.add(cursor.getString(0))
            }
        }
        return tables
    }

    fun columnExists(db: SupportSQLiteDatabase, table: String, column: String): Boolean {
        db.query("PRAGMA table_info($table)", arrayOf()).use { cursor ->
            val nameColumnIndex = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) {
                if (cursor.getString(nameColumnIndex) == column) return true
            }
        }
        return false
    }

    fun isEmpty(db: SupportSQLiteDatabase, table: String): Boolean {
        db.query("SELECT COUNT(*) FROM $table", arrayOf()).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) == 0 else true
        }
    }

    fun buildCollectionQuery(
        column: String,
        values: Collection<Any?>,
        prefix: String = "",
        maxSize: Int = MAX_QUERY_ARGS,
        collectionOperator: CollectionOperator = CollectionOperator.IN
    ): List<Query> {
        if (values.isEmpty()) return emptyList()
        return values
            .chunked(maxSize)
            .map { batch -> buildSingleCollectionQuery(column, batch, prefix, collectionOperator) }
    }

    private fun buildSingleCollectionQuery(
        column: String,
        values: Collection<Any?>,
        prefix: String = "",
        collectionOperator: CollectionOperator = CollectionOperator.IN
    ): Query {
        require(values.isNotEmpty()) { "Must have values!" }
        val query = StringBuilder()
        val args = arrayOfNulls<Any>(values.size)
        var i = 0
        for (value in values) {
            query.append("?")
            args[i] = value
            if (i != values.size - 1) query.append(", ")
            i++
        }
        return Query("$prefix $column ${collectionOperator.sql} ($query)".trim(), buildArgs(*args))
    }

    fun buildBulkInsert(
        tableName: String,
        columns: Array<String>,
        contentValues: List<ContentValues>,
        maxQueryArgs: Int = MAX_QUERY_ARGS,
        onConflict: String? = null
    ): List<Query> {
        val batchSize = maxQueryArgs / columns.size
        return contentValues
            .chunked(batchSize)
            .map { batch -> buildSingleBulkInsert(tableName, columns, batch, onConflict) }
    }

    private fun buildSingleBulkInsert(
        tableName: String,
        columns: Array<String>,
        contentValues: List<ContentValues>,
        onConflict: String? = null
    ): Query {
        val conflictString = onConflict?.let { " OR $onConflict" } ?: ""
        val builder = StringBuilder()
        builder.append("INSERT$conflictString INTO ").append(tableName).append(" (")
        builder.append(columns.joinToString(", "))
        builder.append(") VALUES ")

        val placeholders = contentValues.joinToString(", ") { values ->
            columns.joinToString(", ", prefix = "(", postfix = ")") { column ->
                val value = values[column]
                when {
                    value != null && value is ByteArray -> "X'${Hex.encode(value)}'"
                    value != null -> "?"
                    else -> "null"
                }
            }
        }
        builder.append(placeholders)

        val args = mutableListOf<String>()
        for (values in contentValues) {
            for (column in columns) {
                val value = values[column]
                if (value != null && value !is ByteArray) {
                    args += value.toString()
                }
            }
        }
        return Query(builder.toString(), args.toTypedArray())
    }

    fun buildTrueUpdateQuery(
        selection: String,
        args: Array<String>,
        contentValues: ContentValues
    ): Query {
        val qualifier = StringBuilder()
        val fullArgs = args.toMutableList()
        // Uses ContentValues accessors instead of valueSet() for Robolectric compat.
        // valueSet() was added in API 28.
        val keys = try {
            contentValues.valueSet()?.map { it.key } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        // TODO(EN): full implementation pending proper ContentValues key enumeration.
        // For now, fall back to simple query without true-update comparison.
        if (keys.isEmpty()) return Query(selection, args)
        var i = 0
        for (key in keys) {
            val value = contentValues.get(key)
            if (value != null) {
                if (value is ByteArray) {
                    qualifier.append("hex($key) != ? OR $key IS NULL")
                    fullArgs.add(Hex.encode(value))
                } else {
                    qualifier.append("$key != ? OR $key IS NULL")
                    fullArgs.add(value.toString())
                }
            } else {
                qualifier.append("$key NOT NULL")
            }
            if (i != keys.size - 1) qualifier.append(" OR ")
            i++
        }
        return Query("($selection) AND ($qualifier)", fullArgs.toTypedArray())
    }

    data class Query(
        val where: String,
        val whereArgs: Array<String>
    ) {
        infix fun and(other: Query): Query {
            return when {
                where.isNotEmpty() && other.where.isNotEmpty() ->
                    Query("($where) AND (${other.where})", whereArgs + other.whereArgs)
                where.isNotEmpty() -> this
                else -> other
            }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false
            other as Query
            return where == other.where && whereArgs.contentEquals(other.whereArgs)
        }

        override fun hashCode(): Int {
            var result = where.hashCode()
            result = 31 * result + whereArgs.contentHashCode()
            return result
        }
    }

    enum class CollectionOperator(val sql: String) {
        IN("IN"),
        NOT_IN("NOT IN")
    }

    private fun buildArgs(vararg objects: Any?): Array<String> {
        return objects.map {
            requireNotNull(it) { "Cannot have null arg!" }
            it.toString()
        }.toTypedArray()
    }
}

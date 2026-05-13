package org.enchant.core.database.util

import android.database.Cursor
import kotlin.reflect.KParameter
import kotlin.reflect.full.primaryConstructor

object CursorMapper {
    inline fun <reified T : Any> mapTo(cursor: Cursor): T? {
        if (cursor.isBeforeFirst && !cursor.moveToFirst()) return null
        if (cursor.isAfterLast) return null
        return mapCurrent<T>(cursor)
    }

    inline fun <reified T : Any> mapToList(cursor: Cursor): List<T> {
        val result = mutableListOf<T>()
        while (cursor.moveToNext()) {
            mapCurrent<T>(cursor)?.let { result.add(it) }
        }
        return result
    }

    private inline fun <reified T : Any> mapCurrent(cursor: Cursor): T? {
        return try {
            val constructor = T::class.primaryConstructor ?: return null
            val args = mutableMapOf<KParameter, Any?>()
            constructor.parameters.forEach { param ->
                val colName = toSnakeCase(param.name ?: return@forEach)
                val colIndex = cursor.getColumnIndexOrThrow(colName)
                args[param] = when (param.type.classifier) {
                    String::class -> cursor.getString(colIndex)
                    Int::class -> cursor.getInt(colIndex)
                    Long::class -> cursor.getLong(colIndex)
                    Boolean::class -> cursor.getInt(colIndex) == 1
                    ByteArray::class -> cursor.getBlob(colIndex)
                    else -> cursor.getString(colIndex)
                }
            }
            constructor.callBy(args)
        } catch (_: Exception) {
            null
        }
    }

    private fun toSnakeCase(camel: String): String {
        return camel.replace(Regex("([a-z])([A-Z])")) { "${it.group(1)}_${it.group(2)}" }.lowercase()
    }
}

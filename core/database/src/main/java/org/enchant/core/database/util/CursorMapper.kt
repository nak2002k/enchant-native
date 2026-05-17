package org.enchant.core.database.util

import android.database.Cursor
import kotlin.reflect.KFunction1
import kotlin.reflect.KParameter
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible

object CursorMapper {
    inline fun <reified T : Any> mapTo(cursor: Cursor): T? {
        if (!cursor.moveToFirst()) return null
        return mapCurrentRow<T>(cursor)
    }

    inline fun <reified T : Any> mapToList(cursor: Cursor): List<T> {
        val result = mutableListOf<T>()
        if (cursor.moveToFirst()) {
            do {
                result.add(mapCurrentRow<T>(cursor))
            } while (cursor.moveToNext())
        }
        return result
    }

    @PublishedApi
    internal inline fun <reified T : Any> mapCurrentRow(cursor: Cursor): T {
        val constructor = T::class.primaryConstructor
            ?: throw IllegalArgumentException("${T::class.simpleName} has no primary constructor")
        val args = mutableMapOf<KParameter, Any?>()
        for (param in constructor.parameters) {
            val columnName = param.name
            ?.let { name ->
                name.replace(Regex("([a-z])([A-Z])"), "$1_$2").lowercase()
            }
            val columnIndex = cursor.getColumnIndex(columnName ?: continue)
            if (columnIndex < 0) {
                args[param] = null
                continue
            }
            val value = try {
                when (param.type.classifier) {
                    String::class -> cursor.getString(columnIndex)
                    Int::class -> runCatching { cursor.getInt(columnIndex) }.getOrNull()
                    Long::class -> runCatching { cursor.getLong(columnIndex) }.getOrNull()
                    Boolean::class -> runCatching { cursor.getInt(columnIndex) != 0 }.getOrNull()
                    ByteArray::class -> cursor.getBlob(columnIndex)
                    Double::class -> runCatching { cursor.getDouble(columnIndex) }.getOrNull()
                    Float::class -> runCatching { cursor.getFloat(columnIndex) }.getOrNull()
                    else -> cursor.getString(columnIndex)
                }
            } catch (_: Exception) {
                null
            }
            args[param] = if (cursor.isNull(columnIndex)) null else value
        }
        return constructor.callBy(args)
    }
}

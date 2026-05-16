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
        while (cursor.moveToNext()) {
            result.add(mapCurrentRow<T>(cursor))
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
            val value = when (param.type.classifier) {
                String::class -> cursor.getString(columnIndex)
                Int::class -> cursor.getInt(columnIndex)
                Long::class -> cursor.getLong(columnIndex)
                Boolean::class -> cursor.getInt(columnIndex) != 0
                ByteArray::class -> cursor.getBlob(columnIndex)
                Double::class -> cursor.getDouble(columnIndex)
                Float::class -> cursor.getFloat(columnIndex)
                else -> cursor.getString(columnIndex)
            }
            args[param] = if (cursor.isNull(columnIndex)) null else value
        }
        return constructor.callBy(args)
    }
}

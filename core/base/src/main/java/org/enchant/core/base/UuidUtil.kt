package org.enchant.core.base

import java.nio.ByteBuffer
import java.util.UUID
import java.util.regex.Pattern

object UuidUtil {

    val UNKNOWN_UUID: UUID = UUID(0, 0)

    private val UUID_PATTERN: Pattern = Pattern.compile(
        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}",
        Pattern.CASE_INSENSITIVE
    )

    fun parseOrNull(uuid: String?): UUID? {
        if (uuid == null) return null
        if (!isUuid(uuid)) return null
        return try {
            UUID.fromString(uuid)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    fun parseOrThrow(uuid: String): UUID {
        return UUID.fromString(uuid)
    }

    fun parseOrThrow(bytes: ByteArray): UUID {
        val buffer = ByteBuffer.wrap(bytes)
        val high = buffer.long
        val low = buffer.long
        return UUID(high, low)
    }

    fun isUuid(uuid: String?): Boolean {
        return uuid != null && UUID_PATTERN.matcher(uuid).matches()
    }

    fun toByteArray(uuid: UUID): ByteArray {
        val buffer = ByteBuffer.wrap(ByteArray(16))
        buffer.putLong(uuid.mostSignificantBits)
        buffer.putLong(uuid.leastSignificantBits)
        return buffer.array()
    }

    fun fromByteArray(bytes: ByteArray): UUID {
        return parseOrThrow(bytes)
    }
}

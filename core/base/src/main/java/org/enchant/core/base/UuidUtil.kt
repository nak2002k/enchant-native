package org.enchant.core.base

import java.nio.ByteBuffer
import java.util.UUID
import java.util.regex.Pattern

/**
 * UUID parsing, formatting, and conversion utilities.
 *
 * Provides safe parsing methods, byte array conversion, and collection
 * helpers for working with UUIDs in a messaging context.
 */
object UuidUtil {

    val UNKNOWN_UUID: UUID = UUID(0, 0)
    val UNKNOWN_UUID_STRING: String = "00000000-0000-0000-0000-000000000000"

    private val UUID_PATTERN: Pattern = Pattern.compile(
        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}",
        Pattern.CASE_INSENSITIVE
    )

    /**
     * Parses a UUID string, returning null for invalid or null input.
     */
    fun parseOrNull(uuid: String?): UUID? {
        if (uuid == null) return null
        if (!isUuid(uuid)) return null
        return try {
            UUID.fromString(uuid)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /**
     * Parses a UUID string, returning [UNKNOWN_UUID] for invalid or null input.
     */
    fun parseOrUnknown(uuid: String?): UUID {
        return parseOrNull(uuid) ?: UNKNOWN_UUID
    }

    /**
     * Parses a UUID string, throwing [IllegalArgumentException] on failure.
     */
    fun parseOrThrow(uuid: String): UUID {
        return UUID.fromString(uuid)
    }

    /**
     * Parses a 16-byte array into a UUID.
     *
     * @throws IllegalArgumentException if the array is not exactly 16 bytes
     */
    fun parseOrThrow(bytes: ByteArray): UUID {
        require(bytes.size == 16) { "UUID byte array must be 16 bytes, got ${bytes.size}" }
        val buffer = ByteBuffer.wrap(bytes)
        val high = buffer.long
        val low = buffer.long
        return UUID(high, low)
    }

    /**
     * Parses a nullable 16-byte array into a UUID, returning null for null or wrong-length input.
     */
    fun parseOrNull(bytes: ByteArray?): UUID? {
        if (bytes == null || bytes.size != 16) return null
        return try {
            parseOrThrow(bytes)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /**
     * Checks whether a string is a valid UUID format.
     */
    fun isUuid(uuid: String?): Boolean {
        return uuid != null && UUID_PATTERN.matcher(uuid).matches()
    }

    /**
     * Converts a UUID to a 16-byte array (big-endian).
     */
    fun toByteArray(uuid: UUID): ByteArray {
        val buffer = ByteBuffer.wrap(ByteArray(16))
        buffer.putLong(uuid.mostSignificantBits)
        buffer.putLong(uuid.leastSignificantBits)
        return buffer.array()
    }

    /**
     * Converts a UUID to a 16-byte array. Alias for [toByteArray].
     */
    fun fromByteArray(bytes: ByteArray): UUID {
        return parseOrThrow(bytes)
    }

    /**
     * Converts a collection of UUIDs to a list of byte arrays.
     */
    fun toByteArrays(uuids: Collection<UUID>): List<ByteArray> {
        return uuids.map { toByteArray(it) }
    }

    /**
     * Converts a collection of byte arrays to a list of UUIDs, skipping invalid entries.
     */
    fun fromByteArrays(byteArrays: Collection<ByteArray>): List<UUID> {
        return byteArrays.mapNotNull { parseOrNull(it) }
    }

    /**
     * Filters out [UNKNOWN_UUID] from a mutable collection in-place.
     *
     * @return the same collection with unknown UUIDs removed
     */
    fun filterKnown(uuids: MutableCollection<UUID?>): MutableCollection<UUID?> {
        uuids.removeAll { it == null || it == UNKNOWN_UUID }
        return uuids
    }
}

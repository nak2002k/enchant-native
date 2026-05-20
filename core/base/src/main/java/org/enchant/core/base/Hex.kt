package org.enchant.core.base

/**
 * Hexadecimal encoding and decoding utilities.
 *
 * Provides uppercase encoding by default, with lowercase and hexdump
 * variants for debugging cryptographic operations.
 */
object Hex {

    private val HEX_CHARS_UPPER = "0123456789ABCDEF".toCharArray()
    private val HEX_CHARS_LOWER = "0123456789abcdef".toCharArray()

    /**
     * Encodes bytes to uppercase hexadecimal.
     */
    fun encode(bytes: ByteArray): String {
        return encode(bytes, 0, bytes.size, HEX_CHARS_UPPER)
    }

    /**
     * Encodes a slice of bytes to uppercase hexadecimal.
     */
    fun encode(bytes: ByteArray, offset: Int, length: Int): String {
        return encode(bytes, offset, length, HEX_CHARS_UPPER)
    }

    /**
     * Encodes bytes to lowercase hexadecimal.
     */
    fun encodeLower(bytes: ByteArray): String {
        return encode(bytes, 0, bytes.size, HEX_CHARS_LOWER)
    }

    private fun encode(bytes: ByteArray, offset: Int, length: Int, hexChars: CharArray): String {
        val result = CharArray(length * 2)
        for (i in 0 until length) {
            val b = bytes[offset + i].toInt() and 0xFF
            result[i * 2] = hexChars[b ushr 4]
            result[i * 2 + 1] = hexChars[b and 0x0F]
        }
        return String(result)
    }

    /**
     * Decodes a hexadecimal string to bytes.
     *
     * Spaces and newlines are stripped before decoding.
     *
     * @throws IllegalArgumentException if the string has odd length or contains invalid hex chars
     */
    fun decode(hex: String): ByteArray {
        val cleaned = hex.replace(" ", "").replace("\n", "")
        require(cleaned.length % 2 == 0) { "Hex string must have even length" }
        val len = cleaned.length / 2
        val result = ByteArray(len)
        for (i in 0 until len) {
            val pos = i * 2
            result[i] = ((Character.digit(cleaned[pos], 16) shl 4) + Character.digit(cleaned[pos + 1], 16)).toByte()
        }
        return result
    }

    /**
     * Decodes a hexadecimal string, throwing [AssertionError] on failure.
     */
    fun fromStringOrThrow(hex: String): ByteArray {
        return try {
            decode(hex)
        } catch (e: IllegalArgumentException) {
            throw AssertionError("Invalid hex: $hex", e)
        }
    }

    /**
     * Produces a hexdump string with ASCII sidebar (like `xxd` output).
     *
     * Example output:
     * ```
     * 00000000  48 65 6c 6c 6f 20 57 6f  72 6c 64 21              |Hello World!|
     * ```
     *
     * Useful for debugging cryptographic operations and binary protocols.
     */
    fun dump(bytes: ByteArray): String {
        return dump(bytes, 0, bytes.size)
    }

    /**
     * Produces a hexdump of a slice of bytes.
     */
    fun dump(bytes: ByteArray, offset: Int, length: Int): String {
        val sb = StringBuilder()
        val end = offset + length
        var i = offset
        var lineOffset = 0
        while (i < end) {
            sb.append(String.format("%08x  ", lineOffset))
            val lineEnd = minOf(i + 16, end)

            for (j in i until lineEnd) {
                if (j == i + 8) sb.append(' ')
                sb.append(String.format("%02x ", bytes[j]))
            }

            if (lineEnd - i < 16) {
                val padding = (16 - (lineEnd - i)) * 3
                if (lineEnd - i < 8) sb.append(' ')
                for (k in 0 until padding) sb.append(' ')
            }

            sb.append(" |")
            for (j in i until lineEnd) {
                val c = bytes[j].toInt() and 0xFF
                sb.append(if (c in 0x20..0x7E) c.toChar() else '.')
            }
            sb.append("|\n")

            i = lineEnd
            lineOffset += 16
        }
        return sb.toString()
    }
}

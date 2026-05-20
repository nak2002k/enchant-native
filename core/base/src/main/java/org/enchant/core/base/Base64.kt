package org.enchant.core.base

import java.io.IOException

/**
 * Base64 encoding and decoding utilities supporting standard and URL-safe
 * alphabets, with and without padding.
 *
 * All decode methods automatically handle missing padding by restoring it
 * before decoding.
 */
object Base64 {

    /**
     * Encodes bytes to standard Base64 with padding.
     */
    @JvmStatic
    fun encodeWithPadding(bytes: ByteArray): String {
        return java.util.Base64.getEncoder().encodeToString(bytes)
    }

    /**
     * Encodes a slice of bytes to standard Base64 with padding.
     */
    @JvmStatic
    fun encodeWithPadding(bytes: ByteArray, offset: Int, length: Int): String {
        return java.util.Base64.getEncoder().encodeToString(bytes.copyOfRange(offset, offset + length))
    }

    /**
     * Encodes bytes to standard Base64 without padding.
     */
    @JvmStatic
    fun encodeWithoutPadding(bytes: ByteArray): String {
        return java.util.Base64.getEncoder().encodeToString(bytes).replace("=", "")
    }

    /**
     * Encodes bytes to URL-safe Base64 with padding.
     */
    @JvmStatic
    fun encodeUrlSafeWithPadding(bytes: ByteArray): String {
        return java.util.Base64.getUrlEncoder().encodeToString(bytes)
    }

    /**
     * Encodes bytes to URL-safe Base64 without padding.
     */
    @JvmStatic
    fun encodeUrlSafeWithoutPadding(bytes: ByteArray): String {
        return java.util.Base64.getUrlEncoder().encodeToString(bytes).replace("=", "")
    }

    /**
     * Decodes a Base64 string (standard or URL-safe) to bytes.
     * Automatically restores missing padding.
     *
     * @throws IOException if the input is not valid Base64
     */
    @Throws(IOException::class)
    @JvmStatic
    fun decode(value: String): ByteArray {
        return try {
            if (value.contains('-') || value.contains('_')) {
                java.util.Base64.getUrlDecoder().decode(value.withPaddingIfNeeded())
            } else {
                java.util.Base64.getDecoder().decode(value.withPaddingIfNeeded())
            }
        } catch (e: IllegalArgumentException) {
            throw IOException("Invalid Base64: ${e.message}", e)
        }
    }

    /**
     * Decodes a Base64-encoded byte array to raw bytes.
     */
    @Throws(IOException::class)
    @JvmStatic
    fun decode(value: ByteArray): ByteArray {
        return decode(String(value, Charsets.US_ASCII))
    }

    /**
     * Decodes a Base64 string, returning null on failure.
     */
    @JvmStatic
    fun decodeOrNull(value: String?): ByteArray? {
        if (value == null) return null
        return try {
            decode(value)
        } catch (_: IOException) {
            null
        }
    }

    /**
     * Decodes a Base64 string, throwing [AssertionError] on failure.
     */
    @JvmStatic
    fun decodeOrThrow(value: String): ByteArray {
        return try {
            decode(value)
        } catch (e: IOException) {
            throw AssertionError("Invalid Base64: $value", e)
        }
    }

    /**
     * Decodes a nullable Base64 string, throwing [AssertionError] if non-null and invalid.
     */
    @JvmStatic
    fun decodeNullableOrThrow(value: String?): ByteArray? {
        if (value == null) return null
        return decodeOrThrow(value)
    }

    private fun String.withPaddingIfNeeded(): String {
        return when (length % 4) {
            2 -> "$this=="
            3 -> "$this="
            else -> this
        }
    }
}

fun String.decodeBase64OrThrow(): ByteArray {
    return Base64.decodeOrThrow(this)
}

fun String?.decodeBase64(): ByteArray? {
    return Base64.decodeOrNull(this)
}

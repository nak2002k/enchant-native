package org.enchant.core.base

import java.io.IOException

object Base64 {

    @JvmStatic
    fun encodeWithPadding(bytes: ByteArray): String {
        return java.util.Base64.getEncoder().encodeToString(bytes)
    }

    @JvmStatic
    fun encodeWithoutPadding(bytes: ByteArray): String {
        return java.util.Base64.getEncoder().encodeToString(bytes).replace("=", "")
    }

    @JvmStatic
    fun encodeUrlSafeWithPadding(bytes: ByteArray): String {
        return java.util.Base64.getUrlEncoder().encodeToString(bytes)
    }

    @JvmStatic
    fun encodeUrlSafeWithoutPadding(bytes: ByteArray): String {
        return java.util.Base64.getUrlEncoder().encodeToString(bytes).replace("=", "")
    }

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

    @JvmStatic
    fun decodeOrNull(value: String?): ByteArray? {
        if (value == null) return null
        return try {
            decode(value)
        } catch (_: IOException) {
            null
        }
    }

    @JvmStatic
    fun decodeOrThrow(value: String): ByteArray {
        return try {
            decode(value)
        } catch (e: IOException) {
            throw AssertionError("Invalid Base64: $value", e)
        }
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

package org.enchant.core.base

import org.enchant.core.crypto.CryptoPrimitives

/**
 * Returns the hexadecimal representation of this byte array using uppercase digits.
 */
fun ByteArray.toHexString(): String = Hex.encode(this)

/**
 * Returns the hexadecimal representation of this byte array using lowercase digits.
 */
fun ByteArray.toHexStringLower(): String = Hex.encodeLower(this)

/**
 * Returns the standard Base64 encoding of this byte array with padding.
 */
fun ByteArray.toBase64(): String = Base64.encodeWithPadding(this)

/**
 * Returns the URL-safe Base64 encoding of this byte array without padding.
 */
fun ByteArray.toBase64UrlSafe(): String = Base64.encodeUrlSafeWithoutPadding(this)

/**
 * Zeros out all bytes in this array in-place.
 *
 * Call this on sensitive data (keys, plaintext) after use to prevent
 * memory scraping attacks.
 */
fun ByteArray.zero() {
    for (i in indices) {
        this[i] = 0
    }
}

/**
 * Constant-time comparison of two byte arrays using libenchantcrypto's
 * [enchant_constant_time_equals] primitive.
 *
 * This method prevents timing attacks by ensuring the comparison takes the
 * same amount of time regardless of where the first difference occurs.
 * Arrays of different lengths return false immediately (length is not secret).
 *
 * @param other the byte array to compare against
 * @return true if both arrays have the same length and content
 */
infix fun ByteArray.constantTimeEquals(other: ByteArray): Boolean {
    if (this === other) return true
    if (this.size != other.size) return false
    return CryptoPrimitives.constantTimeEquals(this, other)
}

/**
 * Returns the SHA-256 hash of this byte array.
 */
fun ByteArray.sha256(): ByteArray = CryptoPrimitives.sha256(this)

/**
 * Returns a new byte array containing the XOR of this array and [other].
 * Both arrays must have the same length.
 */
infix fun ByteArray.xor(other: ByteArray): ByteArray {
    require(this.size == other.size) { "Arrays must have the same length" }
    return ByteArray(this.size) { i -> (this[i].toInt() xor other[i].toInt()).toByte() }
}

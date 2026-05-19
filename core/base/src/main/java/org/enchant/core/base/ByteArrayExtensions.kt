package org.enchant.core.base

import java.security.MessageDigest

fun ByteArray.toHexString(): String = Hex.encode(this)

fun ByteArray.toHexStringLower(): String = Hex.encodeLower(this)

fun ByteArray.toBase64(): String = Base64.encodeWithPadding(this)

fun ByteArray.toBase64UrlSafe(): String = Base64.encodeUrlSafeWithoutPadding(this)

fun ByteArray.zero() {
    for (i in indices) {
        this[i] = 0
    }
}

infix fun ByteArray.constantTimeEquals(other: ByteArray): Boolean {
    if (this === other) return true
    if (this.size != other.size) return false
    var result = 0
    for (i in indices) {
        result = result or (this[i].toInt() xor other[i].toInt())
    }
    return result == 0
}

fun ByteArray.sha256(): ByteArray {
    return MessageDigest.getInstance("SHA-256").digest(this)
}



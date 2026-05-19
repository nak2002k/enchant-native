package org.enchant.core.base

fun String.truncate(maxLength: Int, ellipsis: String = "..."): String {
    if (length <= maxLength) return this
    return take(maxLength - ellipsis.length) + ellipsis
}

fun String?.isBlankOrEmpty(): Boolean = this == null || isBlank()

fun String?.nullIfBlank(): String? = if (isNullOrBlank()) null else this

fun String.decodeHex(): ByteArray = Hex.decode(this)



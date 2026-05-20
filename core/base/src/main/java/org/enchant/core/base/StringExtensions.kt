package org.enchant.core.base

/**
 * Truncates a string to [maxLength], appending [ellipsis] if truncated.
 */
fun String.truncate(maxLength: Int, ellipsis: String = "..."): String {
    if (length <= maxLength) return this
    return take(maxLength - ellipsis.length) + ellipsis
}

/**
 * Returns true if this string is null, empty, or contains only whitespace.
 */
fun String?.isBlankOrEmpty(): Boolean = this == null || isBlank()

/**
 * Returns null if this string is null or blank, otherwise returns itself.
 */
fun String?.nullIfBlank(): String? = if (isNullOrBlank()) null else this

/**
 * Decodes this string as hexadecimal bytes.
 */
fun String.decodeHex(): ByteArray = Hex.decode(this)

/**
 * Strips all non-digit characters from a phone number string.
 */
fun String.numbersOnly(): String = filter { it.isDigit() }

/**
 * Strips characters that are not valid in E164-style identifiers
 * (digits, plus, hyphen, space, parentheses).
 */
fun String.e164CharsOnly(): String = filter { it.isDigit() || it in "+- ()" }

/**
 * Strips leading zeros from a numeric string.
 */
fun String.stripLeadingZeros(): String = trimStart('0').ifEmpty { "0" }

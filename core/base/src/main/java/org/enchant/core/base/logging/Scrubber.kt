package org.enchant.core.base.logging

import org.enchant.core.base.SecurePreferences
import java.security.MessageDigest
import java.util.regex.Pattern

/**
 * PII scrubber for log messages.
 *
 * Replaces sensitive data (phone numbers, emails, UUIDs, IP addresses, URLs)
 * with consistent hashed tokens so that log entries can be correlated without
 * exposing raw personal data.
 *
 * Uses HMAC-style hashing: `SHA-256(salt + value)` truncated to 8 hex chars.
 * The salt is fixed at compile time so that the same value always produces
 * the same token within a single app build.
 *
 * Usage:
 * ```
 * val clean = Scrubber.scrub("User +15551234567 sent message")
 * // → "User [PHONE:a1b2c3d4] sent message"
 * ```
 */
object Scrubber {

    private val TAG = Log.tag(Scrubber::class)

    private val SALT: String by lazy {
        val stored = SecurePreferences.getString("scrubber_salt")
        if (stored != null) {
            stored
        } else {
            val bytes = ByteArray(16)
            java.security.SecureRandom().nextBytes(bytes)
            val hex = bytes.joinToString("") { "%02x".format(it) }
            SecurePreferences.putString("scrubber_salt", hex)
            hex
        }
    }

    private val PHONE_PATTERN = Pattern.compile(
        "\\+?[0-9]{7,15}"
    )
    private val EMAIL_PATTERN = Pattern.compile(
        "[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}"
    )
    private val UUID_PATTERN = Pattern.compile(
        "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
    )
    private val IPV4_PATTERN = Pattern.compile(
        "\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b"
    )
    private val URL_PATTERN = Pattern.compile(
        "https?://[^\\s<>\"']+"
    )

    /**
     * Scrubs all PII patterns from [message] and returns the cleaned string.
     *
     * Each replaced value is substituted with a consistent 8-character hex
     * token so that log correlation is possible without exposing raw data.
     */
    fun scrub(message: String?): String? {
        if (message == null) return null
        var result = message
        result = replaceAll(result, PHONE_PATTERN, "PHONE")
        result = replaceAll(result, EMAIL_PATTERN, "EMAIL")
        result = replaceAll(result, UUID_PATTERN, "UUID")
        result = replaceAll(result, IPV4_PATTERN, "IP")
        result = replaceAll(result, URL_PATTERN, "URL")
        return result
    }

    /**
     * Scrubs PII from [message] but preserves values that match [allowedPatterns].
     *
     * Use this when certain patterns (e.g., internal URLs) should not be scrubbed.
     */
    fun scrub(message: String?, vararg allowedPatterns: Pattern): String? {
        if (message == null) return null
        var result = message
        result = replaceAllExcept(result, PHONE_PATTERN, "PHONE", *allowedPatterns)
        result = replaceAllExcept(result, EMAIL_PATTERN, "EMAIL", *allowedPatterns)
        result = replaceAllExcept(result, UUID_PATTERN, "UUID", *allowedPatterns)
        result = replaceAllExcept(result, IPV4_PATTERN, "IP", *allowedPatterns)
        result = replaceAllExcept(result, URL_PATTERN, "URL", *allowedPatterns)
        return result
    }

    private fun replaceAll(input: String, pattern: Pattern, label: String): String {
        val matcher = pattern.matcher(input)
        val sb = StringBuffer()
        while (matcher.find()) {
            val value = matcher.group()
            val token = hashToken(value)
            matcher.appendReplacement(sb, "[$label:$token]")
        }
        matcher.appendTail(sb)
        return sb.toString()
    }

    private fun replaceAllExcept(
        input: String,
        pattern: Pattern,
        label: String,
        vararg allowed: Pattern
    ): String {
        val matcher = pattern.matcher(input)
        val sb = StringBuffer()
        while (matcher.find()) {
            val value = matcher.group()
            if (allowed.any { it.matcher(value).matches() }) {
                matcher.appendReplacement(sb, value)
            } else {
                val token = hashToken(value)
                matcher.appendReplacement(sb, "[$label:$token]")
            }
        }
        matcher.appendTail(sb)
        return sb.toString()
    }

    private fun hashToken(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update((SALT + value).toByteArray(Charsets.UTF_8))
        val hash = digest.digest()
        return hash.take(4).joinToString("") { "%02x".format(it) }
    }
}

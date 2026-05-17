package org.enchant.auth.screens

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.security.MessageDigest

@DisplayName("AppLockScreen")
class AppLockScreenTest {

    @Test
    @DisplayName("SHA-256 hash produces consistent results")
    fun `sha256 hash is consistent`() {
        val pin = "123456"
        val hash1 = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val hash2 = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray())
            .joinToString("") { "%02x".format(it) }
        assertEquals(hash1, hash2)
    }

    @Test
    @DisplayName("Different PINs produce different hashes")
    fun `different pins different hashes`() {
        val hash1 = MessageDigest.getInstance("SHA-256").digest("123456".toByteArray())
            .joinToString("") { "%02x".format(it) }
        val hash2 = MessageDigest.getInstance("SHA-256").digest("654321".toByteArray())
            .joinToString("") { "%02x".format(it) }
        assertNotEquals(hash1, hash2)
    }

    @Test
    @DisplayName("Empty PIN produces valid hash")
    fun `empty pin hash`() {
        val hash = MessageDigest.getInstance("SHA-256").digest(ByteArray(0))
            .joinToString("") { "%02x".format(it) }
        assertEquals(64, hash.length)
    }

    @Test
    @DisplayName("PIN hash is 64 hex characters")
    fun `pin hash length`() {
        val hash = MessageDigest.getInstance("SHA-256").digest("000000".toByteArray())
            .joinToString("") { "%02x".format(it) }
        assertEquals(64, hash.length)
    }
}

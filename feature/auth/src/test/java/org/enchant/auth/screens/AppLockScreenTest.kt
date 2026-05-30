package org.enchant.auth.screens

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

@DisplayName("AppLockScreen")
class AppLockScreenTest {

    @Test
    @DisplayName("isLegacySha256Hash correctly identifies legacy hashes")
    fun `identifies legacy sha256 hash`() {
        assertTrue(isLegacySha256Hash("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"))
        assertTrue(isLegacySha256Hash("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"))
        assertFalse(isLegacySha256Hash("\$argon2id\$v=19\$m=65536,t=3,p=4\$..."))
        assertFalse(isLegacySha256Hash("short"))
    }

    @Test
    @DisplayName("legacySha256Hash produces consistent results")
    fun `legacy sha256 hash is consistent`() {
        val pin = "123456"
        val hash1 = legacySha256Hash(pin)
        val hash2 = legacySha256Hash(pin)
        assertEquals(hash1, hash2)
    }

    @Test
    @DisplayName("Different PINs produce different hashes")
    fun `different pins different legacy hashes`() {
        val hash1 = legacySha256Hash("123456")
        val hash2 = legacySha256Hash("654321")
        assertNotEquals(hash1, hash2)
    }

    @Test
    @DisplayName("Empty PIN produces valid 64-char hash")
    fun `empty pin legacy hash length`() {
        val hash = legacySha256Hash("")
        assertEquals(64, hash.length)
        assertTrue(hash.matches(Regex("[0-9a-f]{64}")))
    }
}
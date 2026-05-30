package org.enchant.auth.screens

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

@DisplayName("TwoStepPinScreen")
class TwoStepPinScreenTest {

    @Test
    @DisplayName("isLegacySha256Hash correctly identifies legacy hashes")
    fun `identifies legacy sha256 hash`() {
        assertTrue(isLegacySha256Hash("a665a45920422f9d417e4867efdc4fb8a04a1f3fff1fa07e998e86f7f7a27ae3"))
        assertTrue(isLegacySha256Hash("0000000000000000000000000000000000000000000000000000000000000000"))
        assertFalse(isLegacySha256Hash("nota64charhash"))
        assertFalse(isLegacySha256Hash("\$argon2id\$v=19\$m=65536,t=3,p=4\$..."))
    }

    @Test
    @DisplayName("legacySha256Hash produces consistent 64-char hex")
    fun `legacy sha256 hash is consistent`() {
        val pin = "123456"
        val hash1 = legacySha256Hash(pin)
        val hash2 = legacySha256Hash(pin)
        assertEquals(hash1, hash2)
        assertEquals(64, hash1.length)
        assertTrue(hash1.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    @DisplayName("Different PINs produce different legacy hashes")
    fun `different pins different legacy hashes`() {
        val hash1 = legacySha256Hash("123456")
        val hash2 = legacySha256Hash("654321")
        assertNotEquals(hash1, hash2)
    }
}
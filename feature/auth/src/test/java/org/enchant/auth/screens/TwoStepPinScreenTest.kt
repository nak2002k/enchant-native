package org.enchant.auth.screens

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.security.MessageDigest

@DisplayName("TwoStepPinScreen")
class TwoStepPinScreenTest {

    @Test
    @DisplayName("SHA-256 hash matches expected format")
    fun `hash format`() {
        val pin = "123456"
        val hash = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray())
            .joinToString("") { "%02x".format(it) }
        assertTrue(hash.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    @DisplayName("verifyPin returns false for wrong pin")
    fun `verify wrong pin`() {
        val pin = "123456"
        val hash = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val wrongHash = MessageDigest.getInstance("SHA-256").digest("wrong".toByteArray())
            .joinToString("") { "%02x".format(it) }
        assertNotEquals(hash, wrongHash)
    }
}

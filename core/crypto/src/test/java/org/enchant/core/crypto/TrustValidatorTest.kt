package org.enchant.core.crypto

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("TrustValidator — Native Integration Tests")
class TrustValidatorTest {

    private lateinit var validator: TrustValidator

    @BeforeEach
    fun setUp() {
        validator = TrustValidator.create()
        validator.setOwnIdentity("local-uuid")
    }

    @AfterEach
    fun tearDown() {
        validator.close()
    }

    @Test
    fun `create and close validator`() {
        val v = TrustValidator.create()
        v.close()
    }

    @Test
    fun `set own identity`() {
        validator.setOwnIdentity("new-uuid")
    }

    @Test
    fun `aggregated trust for unknown sender is zero`() {
        val level = validator.getAggregatedTrust("unknown")
        assertEquals(0, level)
    }

    @Test
    fun `verify identity for unknown sender returns false`() {
        val keyPair = CryptoPrimitives.generateEd25519KeyPair()
        val verified = validator.verifyIdentity(
            senderUuid = "unknown",
            senderIdentityKey = keyPair.publicKey,
            senderDeviceId = 1,
            currentTimestamp = System.currentTimeMillis()
        )
        assertFalse(verified)
    }

    @Test
    fun `establish trust then verify identity returns true`() {
        val keyPair = CryptoPrimitives.generateEd25519KeyPair()
        validator.establishTrust(
            senderUuid = "sender-1",
            senderIdentityKey = keyPair.publicKey,
            senderDeviceId = 1,
            trustLevel = 100,
            currentTimestamp = System.currentTimeMillis()
        )

        val verified = validator.verifyIdentity(
            senderUuid = "sender-1",
            senderIdentityKey = keyPair.publicKey,
            senderDeviceId = 1,
            currentTimestamp = System.currentTimeMillis()
        )

        assertTrue(verified)
        assertTrue(validator.getAggregatedTrust("sender-1") > 0)
    }

    @Test
    fun `different identity key fails verification`() {
        val keyPair = CryptoPrimitives.generateEd25519KeyPair()
        val otherKeyPair = CryptoPrimitives.generateEd25519KeyPair()

        validator.establishTrust(
            senderUuid = "sender-1",
            senderIdentityKey = keyPair.publicKey,
            senderDeviceId = 1,
            trustLevel = 100,
            currentTimestamp = System.currentTimeMillis()
        )

        val verified = validator.verifyIdentity(
            senderUuid = "sender-1",
            senderIdentityKey = otherKeyPair.publicKey,
            senderDeviceId = 1,
            currentTimestamp = System.currentTimeMillis()
        )

        assertFalse(verified)
    }

    @Test
    fun `add revoked server key`() {
        validator.addRevokedServerKey(42)
    }

    @Test
    fun `validateSender rejects invalid certificate`() {
        assertThrows(IllegalStateException::class.java) {
            validator.validateSender(
                certData = ByteArray(64) { it.toByte() },
                validationTimestamp = System.currentTimeMillis()
            )
        }
    }
}

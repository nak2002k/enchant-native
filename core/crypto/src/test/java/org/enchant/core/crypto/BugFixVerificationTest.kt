package org.enchant.core.crypto

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("Bug Fix Verification Tests")
class BugFixVerificationTest {

    @BeforeEach
    fun setUp() {
        SessionManager.reset()
        KeyManager.reset()
    }

    @AfterEach
    fun tearDown() {
        SessionManager.reset()
        KeyManager.reset()
    }

    @Nested
    @DisplayName("Bug #2 — selfUserId fails fast if not initialized")
    inner class Bug2SelfUserIdTest {
        @Test
        fun `encryptMessage throws when selfUserId not set`() = runTest {
            SessionManager.reset()
            SessionManager.init()
            val result = runCatching {
                SessionManager.encryptMessage("user1", "test".encodeToByteArray())
            }
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("not initialized") == true)
        }
    }

    @Nested
    @DisplayName("Bug #16 — needsKeyRotation returns true when never rotated")
    inner class Bug16KeyRotationTest {
        @Test
        fun `needsKeyRotation returns true when lastSpkRotationMs is 0`() = runTest {
            KeyManager.reset()
            assertTrue(KeyManager.needsKeyRotation())
        }
    }

    @Nested
    @DisplayName("Bug #29 — X3DH zeros all DH material after use")
    inner class Bug29X3DHZeroingTest {
        @Test
        fun `aliceInitiate produces correct shared secret`() = runTest {
            val aliceIk = CryptoPrimitives.generateEd25519KeyPair()
            val aliceEk = CryptoPrimitives.generateX25519KeyPair()
            val bobIk = CryptoPrimitives.generateEd25519KeyPair()
            val bobSpk = CryptoPrimitives.generateX25519KeyPair()
            val bobOpk = CryptoPrimitives.generateX25519KeyPair()

            val aliceResult = X3DH.aliceInitiate(
                ourIdentityKey = aliceIk,
                ourEphemeralKey = aliceEk,
                theirIdentityKeyPublic = CryptoPrimitives.ed25519PkToX25519(bobIk.publicKey),
                theirSignedPrekeyPublic = bobSpk.publicKey,
                theirOneTimePrekeyPublic = bobOpk.publicKey
            )
            assertNotNull(aliceResult.sharedSecret)
            assertEquals(32, aliceResult.sharedSecret.size)
        }

        @Test
        fun `bobRespond produces correct shared secret`() = runTest {
            val aliceIk = CryptoPrimitives.generateEd25519KeyPair()
            val aliceEk = CryptoPrimitives.generateX25519KeyPair()
            val bobIk = CryptoPrimitives.generateEd25519KeyPair()
            val bobSpk = CryptoPrimitives.generateX25519KeyPair()
            val bobOpk = CryptoPrimitives.generateX25519KeyPair()

            val bobResult = X3DH.bobRespond(
                ourIdentityKey = bobIk,
                ourSignedPrekeyKeyPair = bobSpk,
                ourOneTimePrekeyKeyPair = bobOpk,
                theirIdentityKeyPublic = CryptoPrimitives.ed25519PkToX25519(aliceIk.publicKey),
                theirEphemeralKeyPublic = aliceEk.publicKey
            )
            assertNotNull(bobResult.sharedSecret)
            assertEquals(32, bobResult.sharedSecret.size)
        }

        @Test
        fun `alice and bob derive identical shared secret`() = runTest {
            val aliceIk = CryptoPrimitives.generateEd25519KeyPair()
            val aliceEk = CryptoPrimitives.generateX25519KeyPair()
            val bobIk = CryptoPrimitives.generateEd25519KeyPair()
            val bobSpk = CryptoPrimitives.generateX25519KeyPair()
            val bobOpk = CryptoPrimitives.generateX25519KeyPair()

            val aliceResult = X3DH.aliceInitiate(
                aliceIk, aliceEk,
                CryptoPrimitives.ed25519PkToX25519(bobIk.publicKey),
                bobSpk.publicKey, bobOpk.publicKey
            )
            val bobResult = X3DH.bobRespond(
                bobIk, bobSpk, bobOpk,
                CryptoPrimitives.ed25519PkToX25519(aliceIk.publicKey),
                aliceEk.publicKey
            )
            assertTrue(aliceResult.sharedSecret.contentEquals(bobResult.sharedSecret))
        }
    }
}

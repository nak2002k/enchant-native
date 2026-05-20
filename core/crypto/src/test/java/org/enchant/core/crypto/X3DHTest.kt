package org.enchant.core.crypto

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("X3DH — Extended Triple Diffie-Hellman")
class X3DHTest {

    @Nested @DisplayName("Alice Initiate + Bob Respond")
    inner class AliceBobTest {
        @Test @DisplayName("Alice and Bob derive identical shared secret with OPK")
        fun `alice and bob agree with opk`() = runTest {
            val aliceIk = CryptoPrimitives.generateEd25519KeyPair()
            val bobIk = CryptoPrimitives.generateEd25519KeyPair()
            val bobSpk = CryptoPrimitives.generateX25519KeyPair()
            val bobOpk = CryptoPrimitives.generateX25519KeyPair()
            val aliceEk = CryptoPrimitives.generateX25519KeyPair()

            val bobIkX = CryptoPrimitives.ed25519PkToX25519(bobIk.publicKey)

            val aliceResult = X3DH.aliceInitiate(
                ourIdentityKey = aliceIk,
                ourEphemeralKey = aliceEk,
                theirIdentityKeyPublic = bobIk.publicKey,
                theirSignedPrekeyPublic = bobSpk.publicKey,
                theirOneTimePrekeyPublic = bobOpk.publicKey,
                theirSignedPrekeyId = 1,
                theirOneTimePrekeyId = 2
            )

            val bobResult = X3DH.bobRespond(
                ourIdentityKey = bobIk,
                ourSignedPrekeyKeyPair = bobSpk,
                ourOneTimePrekeyKeyPair = bobOpk,
                theirIdentityKeyPublic = aliceIk.publicKey,
                theirEphemeralKeyPublic = aliceEk.publicKey,
                ourSignedPrekeyId = 1,
                ourOneTimePrekeyId = 2
            )

            assertArrayEquals(aliceResult.sharedSecret, bobResult.sharedSecret)
            assertArrayEquals(aliceResult.rootKey, bobResult.rootKey)
            assertArrayEquals(aliceResult.sendingChainKey, bobResult.sendingChainKey)
            assertArrayEquals(aliceResult.receivingChainKey, bobResult.receivingChainKey)

            aliceResult.zero()
            bobResult.zero()
        }

        @Test @DisplayName("Alice and Bob derive identical shared secret without OPK")
        fun `alice and bob agree without opk`() = runTest {
            val aliceIk = CryptoPrimitives.generateEd25519KeyPair()
            val bobIk = CryptoPrimitives.generateEd25519KeyPair()
            val bobSpk = CryptoPrimitives.generateX25519KeyPair()
            val aliceEk = CryptoPrimitives.generateX25519KeyPair()

            val aliceResult = X3DH.aliceInitiate(
                ourIdentityKey = aliceIk,
                ourEphemeralKey = aliceEk,
                theirIdentityKeyPublic = bobIk.publicKey,
                theirSignedPrekeyPublic = bobSpk.publicKey,
                theirOneTimePrekeyPublic = null
            )

            val bobResult = X3DH.bobRespond(
                ourIdentityKey = bobIk,
                ourSignedPrekeyKeyPair = bobSpk,
                ourOneTimePrekeyKeyPair = null,
                theirIdentityKeyPublic = aliceIk.publicKey,
                theirEphemeralKeyPublic = aliceEk.publicKey
            )

            assertArrayEquals(aliceResult.sharedSecret, bobResult.sharedSecret)

            aliceResult.zero()
            bobResult.zero()
        }

        @Test @DisplayName("shared secret is 32 bytes")
        fun `shared secret 32 bytes`() = runTest {
            val aliceIk = CryptoPrimitives.generateEd25519KeyPair()
            val bobIk = CryptoPrimitives.generateEd25519KeyPair()
            val bobSpk = CryptoPrimitives.generateX25519KeyPair()
            val aliceEk = CryptoPrimitives.generateX25519KeyPair()

            val result = X3DH.aliceInitiate(
                ourIdentityKey = aliceIk,
                ourEphemeralKey = aliceEk,
                theirIdentityKeyPublic = bobIk.publicKey,
                theirSignedPrekeyPublic = bobSpk.publicKey
            )

            assertEquals(32, result.sharedSecret.size)
            assertEquals(32, result.rootKey.size)
            assertEquals(32, result.sendingChainKey.size)
            assertEquals(32, result.receivingChainKey.size)

            result.zero()
        }

        @Test @DisplayName("header contains correct keys and IDs")
        fun `header correct`() = runTest {
            val aliceIk = CryptoPrimitives.generateEd25519KeyPair()
            val bobIk = CryptoPrimitives.generateEd25519KeyPair()
            val bobSpk = CryptoPrimitives.generateX25519KeyPair()
            val bobOpk = CryptoPrimitives.generateX25519KeyPair()
            val aliceEk = CryptoPrimitives.generateX25519KeyPair()

            val result = X3DH.aliceInitiate(
                ourIdentityKey = aliceIk,
                ourEphemeralKey = aliceEk,
                theirIdentityKeyPublic = bobIk.publicKey,
                theirSignedPrekeyPublic = bobSpk.publicKey,
                theirOneTimePrekeyPublic = bobOpk.publicKey,
                theirSignedPrekeyId = 5,
                theirOneTimePrekeyId = 7
            )

            assertArrayEquals(aliceIk.publicKey, result.header.identityKey)
            assertArrayEquals(aliceEk.publicKey, result.header.ephemeralKey)
            assertEquals(5, result.header.signedPrekeyId)
            assertEquals(7, result.header.oneTimePrekeyId)

            result.zero()
        }

        @Test @DisplayName("different ephemeral keys produce different shared secrets")
        fun `different ephemeral different secrets`() = runTest {
            val aliceIk = CryptoPrimitives.generateEd25519KeyPair()
            val bobIk = CryptoPrimitives.generateEd25519KeyPair()
            val bobSpk = CryptoPrimitives.generateX25519KeyPair()
            val aliceEk1 = CryptoPrimitives.generateX25519KeyPair()
            val aliceEk2 = CryptoPrimitives.generateX25519KeyPair()

            val r1 = X3DH.aliceInitiate(
                ourIdentityKey = aliceIk,
                ourEphemeralKey = aliceEk1,
                theirIdentityKeyPublic = bobIk.publicKey,
                theirSignedPrekeyPublic = bobSpk.publicKey
            )
            val r2 = X3DH.aliceInitiate(
                ourIdentityKey = aliceIk,
                ourEphemeralKey = aliceEk2,
                theirIdentityKeyPublic = bobIk.publicKey,
                theirSignedPrekeyPublic = bobSpk.publicKey
            )

            assertFalse(r1.sharedSecret.contentEquals(r2.sharedSecret))

            r1.zero()
            r2.zero()
        }
    }

    @Nested @DisplayName("Signed Prekey Verification")
    inner class SpkVerificationTest {
        @Test @DisplayName("valid signature verifies")
        fun `valid signature verifies`() {
            val ik = CryptoPrimitives.generateEd25519KeyPair()
            val spk = CryptoPrimitives.generateX25519KeyPair()
            val sig = CryptoPrimitives.signEd25519(spk.publicKey, ik.privateKey)

            assertTrue(X3DH.verifySignedPrekey(spk.publicKey, sig, ik.publicKey))
        }

        @Test @DisplayName("wrong signature fails verification")
        fun `wrong signature fails`() {
            val ik = CryptoPrimitives.generateEd25519KeyPair()
            val spk = CryptoPrimitives.generateX25519KeyPair()
            val wrongIk = CryptoPrimitives.generateEd25519KeyPair()
            val sig = CryptoPrimitives.signEd25519(spk.publicKey, wrongIk.privateKey)

            assertFalse(X3DH.verifySignedPrekey(spk.publicKey, sig, ik.publicKey))
        }

        @Test @DisplayName("corrupted data fails verification")
        fun `corrupted data fails`() {
            val ik = CryptoPrimitives.generateEd25519KeyPair()
            val spk = CryptoPrimitives.generateX25519KeyPair()
            val sig = CryptoPrimitives.signEd25519(spk.publicKey, ik.privateKey)
            sig[0] = (sig[0].toInt() xor 0xFF).toByte()

            assertFalse(X3DH.verifySignedPrekey(spk.publicKey, sig, ik.publicKey))
        }
    }

    @Nested @DisplayName("Zeroing")
    inner class ZeroingTest {
        @Test @DisplayName("zero() clears all secret material")
        fun `zero clears secrets`() = runTest {
            val aliceIk = CryptoPrimitives.generateEd25519KeyPair()
            val bobIk = CryptoPrimitives.generateEd25519KeyPair()
            val bobSpk = CryptoPrimitives.generateX25519KeyPair()
            val aliceEk = CryptoPrimitives.generateX25519KeyPair()

            val result = X3DH.aliceInitiate(
                ourIdentityKey = aliceIk,
                ourEphemeralKey = aliceEk,
                theirIdentityKeyPublic = bobIk.publicKey,
                theirSignedPrekeyPublic = bobSpk.publicKey
            )

            val sharedSecretCopy = result.sharedSecret.copyOf()
            result.zero()

            assertFalse(result.sharedSecret.contentEquals(sharedSecretCopy))
            assertTrue(result.sharedSecret.all { it == 0.toByte() })
            assertTrue(result.rootKey.all { it == 0.toByte() })
            assertTrue(result.sendingChainKey.all { it == 0.toByte() })
            assertTrue(result.receivingChainKey.all { it == 0.toByte() })
        }
    }
}

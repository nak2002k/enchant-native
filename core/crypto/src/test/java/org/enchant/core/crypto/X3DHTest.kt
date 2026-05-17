package org.enchant.core.crypto

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.assertThrows

@DisplayName("X3DH Key Agreement — Full Coverage")
class X3DHTest {

    @Nested @DisplayName("Alice Initiate + Bob Respond")
    inner class FullProtocolTest {
        @Test @DisplayName("Alice and Bob derive identical shared secret with OPK")
        fun `identical shared secret with opk`() = runTest {
            val aliceIk = CryptoHelper.generateEd25519KeyPair()
            val aliceEk = CryptoHelper.generateX25519KeyPair()
            val bobIk = CryptoHelper.generateEd25519KeyPair()
            val bobSpk = CryptoHelper.generateX25519KeyPair()
            val bobOpk = CryptoHelper.generateX25519KeyPair()

            val aliceResult = X3DH.aliceInitiate(
                ourIdentityKey = aliceIk,
                ourEphemeralKey = aliceEk,
                theirIdentityKeyPublic = CryptoHelper.ed25519PkToX25519(bobIk.publicKey),
                theirSignedPrekeyPublic = bobSpk.publicKey,
                theirOneTimePrekeyPublic = bobOpk.publicKey
            )

            val bobResult = X3DH.bobRespond(
                ourIdentityKey = bobIk,
                ourSignedPrekeyKeyPair = bobSpk,
                ourOneTimePrekeyKeyPair = bobOpk,
                theirIdentityKeyPublic = CryptoHelper.ed25519PkToX25519(aliceIk.publicKey),
                theirEphemeralKeyPublic = aliceEk.publicKey
            )

            assertTrue(aliceResult.sharedSecret.contentEquals(bobResult.sharedSecret))
            assertTrue(aliceResult.rootKey.contentEquals(bobResult.rootKey))
            assertTrue(aliceResult.sendingChainKey.contentEquals(bobResult.sendingChainKey))
        }

        @Test @DisplayName("Alice and Bob derive identical shared secret without OPK")
        fun `identical shared secret without opk`() = runTest {
            val aliceIk = CryptoHelper.generateEd25519KeyPair()
            val aliceEk = CryptoHelper.generateX25519KeyPair()
            val bobIk = CryptoHelper.generateEd25519KeyPair()
            val bobSpk = CryptoHelper.generateX25519KeyPair()

            val aliceResult = X3DH.aliceInitiate(
                ourIdentityKey = aliceIk,
                ourEphemeralKey = aliceEk,
                theirIdentityKeyPublic = CryptoHelper.ed25519PkToX25519(bobIk.publicKey),
                theirSignedPrekeyPublic = bobSpk.publicKey,
                theirOneTimePrekeyPublic = null
            )

            val bobResult = X3DH.bobRespond(
                ourIdentityKey = bobIk,
                ourSignedPrekeyKeyPair = bobSpk,
                ourOneTimePrekeyKeyPair = null,
                theirIdentityKeyPublic = CryptoHelper.ed25519PkToX25519(aliceIk.publicKey),
                theirEphemeralKeyPublic = aliceEk.publicKey
            )

            assertTrue(aliceResult.sharedSecret.contentEquals(bobResult.sharedSecret))
        }

        @Test @DisplayName("shared secret is 32 bytes")
        fun `shared secret 32 bytes`() = runTest {
            val aliceIk = CryptoHelper.generateEd25519KeyPair()
            val aliceEk = CryptoHelper.generateX25519KeyPair()
            val bobIk = CryptoHelper.generateEd25519KeyPair()
            val bobSpk = CryptoHelper.generateX25519KeyPair()

            val aliceResult = X3DH.aliceInitiate(
                ourIdentityKey = aliceIk,
                ourEphemeralKey = aliceEk,
                theirIdentityKeyPublic = CryptoHelper.ed25519PkToX25519(bobIk.publicKey),
                theirSignedPrekeyPublic = bobSpk.publicKey
            )

            assertEquals(32, aliceResult.sharedSecret.size)
        }

        @Test @DisplayName("root key is 32 bytes")
        fun `root key 32 bytes`() = runTest {
            val aliceIk = CryptoHelper.generateEd25519KeyPair()
            val aliceEk = CryptoHelper.generateX25519KeyPair()
            val bobIk = CryptoHelper.generateEd25519KeyPair()
            val bobSpk = CryptoHelper.generateX25519KeyPair()

            val aliceResult = X3DH.aliceInitiate(
                ourIdentityKey = aliceIk,
                ourEphemeralKey = aliceEk,
                theirIdentityKeyPublic = CryptoHelper.ed25519PkToX25519(bobIk.publicKey),
                theirSignedPrekeyPublic = bobSpk.publicKey
            )

            assertEquals(32, aliceResult.rootKey.size)
        }

        @Test @DisplayName("sending chain key is 32 bytes")
        fun `sending chain key 32 bytes`() = runTest {
            val aliceIk = CryptoHelper.generateEd25519KeyPair()
            val aliceEk = CryptoHelper.generateX25519KeyPair()
            val bobIk = CryptoHelper.generateEd25519KeyPair()
            val bobSpk = CryptoHelper.generateX25519KeyPair()

            val aliceResult = X3DH.aliceInitiate(
                ourIdentityKey = aliceIk,
                ourEphemeralKey = aliceEk,
                theirIdentityKeyPublic = CryptoHelper.ed25519PkToX25519(bobIk.publicKey),
                theirSignedPrekeyPublic = bobSpk.publicKey
            )

            assertEquals(32, aliceResult.sendingChainKey.size)
        }
    }

    @Nested @DisplayName("Header Generation")
    inner class HeaderTest {
        @Test @DisplayName("Alice header contains identity key")
        fun `alice header has identity key`() = runTest {
            val aliceIk = CryptoHelper.generateEd25519KeyPair()
            val aliceEk = CryptoHelper.generateX25519KeyPair()
            val bobIk = CryptoHelper.generateEd25519KeyPair()
            val bobSpk = CryptoHelper.generateX25519KeyPair()

            val result = X3DH.aliceInitiate(
                ourIdentityKey = aliceIk,
                ourEphemeralKey = aliceEk,
                theirIdentityKeyPublic = CryptoHelper.ed25519PkToX25519(bobIk.publicKey),
                theirSignedPrekeyPublic = bobSpk.publicKey
            )

            assertTrue(result.header.identityKey.isNotEmpty())
            assertEquals(32, result.header.identityKey.size)
        }

        @Test @DisplayName("Alice header contains ephemeral key")
        fun `alice header has ephemeral key`() = runTest {
            val aliceIk = CryptoHelper.generateEd25519KeyPair()
            val aliceEk = CryptoHelper.generateX25519KeyPair()
            val bobIk = CryptoHelper.generateEd25519KeyPair()
            val bobSpk = CryptoHelper.generateX25519KeyPair()

            val result = X3DH.aliceInitiate(
                ourIdentityKey = aliceIk,
                ourEphemeralKey = aliceEk,
                theirIdentityKeyPublic = CryptoHelper.ed25519PkToX25519(bobIk.publicKey),
                theirSignedPrekeyPublic = bobSpk.publicKey
            )

            assertTrue(result.header.ephemeralKey.isNotEmpty())
            assertEquals(32, result.header.ephemeralKey.size)
        }

        @Test @DisplayName("Bob header contains identity key")
        fun `bob header has identity key`() = runTest {
            val aliceIk = CryptoHelper.generateEd25519KeyPair()
            val aliceEk = CryptoHelper.generateX25519KeyPair()
            val bobIk = CryptoHelper.generateEd25519KeyPair()
            val bobSpk = CryptoHelper.generateX25519KeyPair()

            val result = X3DH.bobRespond(
                ourIdentityKey = bobIk,
                ourSignedPrekeyKeyPair = bobSpk,
                theirIdentityKeyPublic = CryptoHelper.ed25519PkToX25519(aliceIk.publicKey),
                theirEphemeralKeyPublic = aliceEk.publicKey
            )

            assertTrue(result.header.identityKey.isNotEmpty())
        }

        @Test @DisplayName("Bob header contains ephemeral key")
        fun `bob header has ephemeral key`() = runTest {
            val aliceIk = CryptoHelper.generateEd25519KeyPair()
            val aliceEk = CryptoHelper.generateX25519KeyPair()
            val bobIk = CryptoHelper.generateEd25519KeyPair()
            val bobSpk = CryptoHelper.generateX25519KeyPair()

            val result = X3DH.bobRespond(
                ourIdentityKey = bobIk,
                ourSignedPrekeyKeyPair = bobSpk,
                theirIdentityKeyPublic = CryptoHelper.ed25519PkToX25519(aliceIk.publicKey),
                theirEphemeralKeyPublic = aliceEk.publicKey
            )

            assertTrue(result.header.ephemeralKey.isNotEmpty())
        }

        @Test @DisplayName("headers differ between Alice and Bob")
        fun `headers differ`() = runTest {
            val aliceIk = CryptoHelper.generateEd25519KeyPair()
            val aliceEk = CryptoHelper.generateX25519KeyPair()
            val bobIk = CryptoHelper.generateEd25519KeyPair()
            val bobSpk = CryptoHelper.generateX25519KeyPair()

            val aliceResult = X3DH.aliceInitiate(
                ourIdentityKey = aliceIk,
                ourEphemeralKey = aliceEk,
                theirIdentityKeyPublic = CryptoHelper.ed25519PkToX25519(bobIk.publicKey),
                theirSignedPrekeyPublic = bobSpk.publicKey
            )

            val bobResult = X3DH.bobRespond(
                ourIdentityKey = bobIk,
                ourSignedPrekeyKeyPair = bobSpk,
                theirIdentityKeyPublic = CryptoHelper.ed25519PkToX25519(aliceIk.publicKey),
                theirEphemeralKeyPublic = aliceEk.publicKey
            )

            assertFalse(aliceResult.header.identityKey.contentEquals(bobResult.header.identityKey))
            assertFalse(aliceResult.header.ephemeralKey.contentEquals(bobResult.header.ephemeralKey))
        }
    }

    @Nested @DisplayName("Security Properties")
    inner class SecurityTest {
        @Test @DisplayName("different ephemeral keys produce different shared secrets")
        fun `different ek different secret`() = runTest {
            val aliceIk = CryptoHelper.generateEd25519KeyPair()
            val aliceEk1 = CryptoHelper.generateX25519KeyPair()
            val aliceEk2 = CryptoHelper.generateX25519KeyPair()
            val bobIk = CryptoHelper.generateEd25519KeyPair()
            val bobSpk = CryptoHelper.generateX25519KeyPair()
            val bobOpk = CryptoHelper.generateX25519KeyPair()

            val r1 = X3DH.aliceInitiate(
                aliceIk, aliceEk1,
                CryptoHelper.ed25519PkToX25519(bobIk.publicKey),
                bobSpk.publicKey, bobOpk.publicKey
            )
            val r2 = X3DH.aliceInitiate(
                aliceIk, aliceEk2,
                CryptoHelper.ed25519PkToX25519(bobIk.publicKey),
                bobSpk.publicKey, bobOpk.publicKey
            )

            assertFalse(r1.sharedSecret.contentEquals(r2.sharedSecret))
        }

        @Test @DisplayName("different SPK produces different shared secret")
        fun `different spk different secret`() = runTest {
            val aliceIk = CryptoHelper.generateEd25519KeyPair()
            val aliceEk = CryptoHelper.generateX25519KeyPair()
            val bobIk = CryptoHelper.generateEd25519KeyPair()
            val bobSpk1 = CryptoHelper.generateX25519KeyPair()
            val bobSpk2 = CryptoHelper.generateX25519KeyPair()
            val bobOpk = CryptoHelper.generateX25519KeyPair()

            val r1 = X3DH.aliceInitiate(
                aliceIk, aliceEk,
                CryptoHelper.ed25519PkToX25519(bobIk.publicKey),
                bobSpk1.publicKey, bobOpk.publicKey
            )
            val r2 = X3DH.aliceInitiate(
                aliceIk, aliceEk,
                CryptoHelper.ed25519PkToX25519(bobIk.publicKey),
                bobSpk2.publicKey, bobOpk.publicKey
            )

            assertFalse(r1.sharedSecret.contentEquals(r2.sharedSecret))
        }

        @Test @DisplayName("different OPK produces different shared secret")
        fun `different opk different secret`() = runTest {
            val aliceIk = CryptoHelper.generateEd25519KeyPair()
            val aliceEk = CryptoHelper.generateX25519KeyPair()
            val bobIk = CryptoHelper.generateEd25519KeyPair()
            val bobSpk = CryptoHelper.generateX25519KeyPair()
            val bobOpk1 = CryptoHelper.generateX25519KeyPair()
            val bobOpk2 = CryptoHelper.generateX25519KeyPair()

            val r1 = X3DH.aliceInitiate(
                aliceIk, aliceEk,
                CryptoHelper.ed25519PkToX25519(bobIk.publicKey),
                bobSpk.publicKey, bobOpk1.publicKey
            )
            val r2 = X3DH.aliceInitiate(
                aliceIk, aliceEk,
                CryptoHelper.ed25519PkToX25519(bobIk.publicKey),
                bobSpk.publicKey, bobOpk2.publicKey
            )

            assertFalse(r1.sharedSecret.contentEquals(r2.sharedSecret))
        }

        @Test @DisplayName("shared secret is not all zeros")
        fun `shared secret not zero`() = runTest {
            val aliceIk = CryptoHelper.generateEd25519KeyPair()
            val aliceEk = CryptoHelper.generateX25519KeyPair()
            val bobIk = CryptoHelper.generateEd25519KeyPair()
            val bobSpk = CryptoHelper.generateX25519KeyPair()

            val result = X3DH.aliceInitiate(
                aliceIk, aliceEk,
                CryptoHelper.ed25519PkToX25519(bobIk.publicKey),
                bobSpk.publicKey
            )

            assertFalse(result.sharedSecret.all { it == 0.toByte() })
        }

        @Test @DisplayName("100 sessions produce unique shared secrets")
        fun `unique secrets across sessions`() = runTest {
            val secrets = mutableListOf<ByteArray>()
            repeat(100) {
                val aliceIk = CryptoHelper.generateEd25519KeyPair()
                val aliceEk = CryptoHelper.generateX25519KeyPair()
                val bobIk = CryptoHelper.generateEd25519KeyPair()
                val bobSpk = CryptoHelper.generateX25519KeyPair()
                val result = X3DH.aliceInitiate(
                    aliceIk, aliceEk,
                    CryptoHelper.ed25519PkToX25519(bobIk.publicKey),
                    bobSpk.publicKey
                )
                secrets.add(result.sharedSecret)
            }
            for (i in secrets.indices) {
                for (j in i + 1 until secrets.size) {
                    assertFalse(secrets[i].contentEquals(secrets[j]))
                }
            }
        }
    }

    @Nested @DisplayName("Edge Cases")
    inner class EdgeCaseTest {
        @Test @DisplayName("Bob respond without OPK works correctly")
        fun `bob without opk`() = runTest {
            val aliceIk = CryptoHelper.generateEd25519KeyPair()
            val aliceEk = CryptoHelper.generateX25519KeyPair()
            val bobIk = CryptoHelper.generateEd25519KeyPair()
            val bobSpk = CryptoHelper.generateX25519KeyPair()

            val result = X3DH.bobRespond(
                ourIdentityKey = bobIk,
                ourSignedPrekeyKeyPair = bobSpk,
                ourOneTimePrekeyKeyPair = null,
                theirIdentityKeyPublic = CryptoHelper.ed25519PkToX25519(aliceIk.publicKey),
                theirEphemeralKeyPublic = aliceEk.publicKey
            )

            assertNotNull(result.sharedSecret)
            assertEquals(32, result.sharedSecret.size)
        }
    }
}

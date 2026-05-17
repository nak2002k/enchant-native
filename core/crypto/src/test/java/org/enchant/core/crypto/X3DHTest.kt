package org.enchant.core.crypto

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("X3DH Key Agreement")
class X3DHTest {

    @Test
    @DisplayName("Alice and Bob derive identical shared secret with OPK")
    fun `alice and bob derive identical SK with OPK`() = runTest {
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

    @Test
    @DisplayName("Alice and Bob derive identical shared secret without OPK")
    fun `alice and bob derive identical SK without OPK`() = runTest {
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

    @Test
    @DisplayName("BobRespond returns non-empty header identity and ephemeral keys")
    fun `bobRespond returns valid header`() = runTest {
        val aliceIk = CryptoHelper.generateEd25519KeyPair()
        val aliceEk = CryptoHelper.generateX25519KeyPair()
        val bobIk = CryptoHelper.generateEd25519KeyPair()
        val bobSpk = CryptoHelper.generateX25519KeyPair()

        val aliceResult = X3DH.aliceInitiate(
            ourIdentityKey = aliceIk, ourEphemeralKey = aliceEk,
            theirIdentityKeyPublic = CryptoHelper.ed25519PkToX25519(bobIk.publicKey),
            theirSignedPrekeyPublic = bobSpk.publicKey
        )

        val bobResult = X3DH.bobRespond(
            ourIdentityKey = bobIk, ourSignedPrekeyKeyPair = bobSpk,
            theirIdentityKeyPublic = CryptoHelper.ed25519PkToX25519(aliceIk.publicKey),
            theirEphemeralKeyPublic = aliceEk.publicKey
        )

        assertTrue(bobResult.header.identityKey.isNotEmpty())
        assertTrue(bobResult.header.ephemeralKey.isNotEmpty())
        assertTrue(aliceResult.header.identityKey.isNotEmpty())
        assertTrue(aliceResult.header.ephemeralKey.isNotEmpty())
    }
}

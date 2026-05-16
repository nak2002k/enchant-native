package org.enchant.core.crypto

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("X3DH")
class X3DHTest {
    private lateinit var bobIk: CryptoHelper.KeyPair
    private lateinit var bobSpk: CryptoHelper.KeyPair
    private lateinit var bobOpk: CryptoHelper.KeyPair

    @BeforeEach
    fun setUp() {
        bobIk = CryptoHelper.generateEd25519KeyPair()
        bobSpk = CryptoHelper.generateX25519KeyPair()
        bobOpk = CryptoHelper.generateX25519KeyPair()
    }

    @Test @DisplayName("aliceInitiate returns result with all fields populated")
    fun `alice init produces valid result`() = runTest {
        val aliceIk = CryptoHelper.generateEd25519KeyPair()
        val aliceEk = CryptoHelper.generateX25519KeyPair()
        val result = X3DH.aliceInitiate(
            ourIdentityKey = aliceIk,
            ourEphemeralKey = CryptoHelper.KeyPair(aliceEk.publicKey, aliceEk.privateKey),
            theirIdentityKeyPublic = CryptoHelper.ed25519PkToX25519(bobIk.publicKey),
            theirSignedPrekeyPublic = bobSpk.publicKey
        )
        assertEquals(32, result.sharedSecret.size)
        assertEquals(32, result.rootKey.size)
        assertEquals(32, result.sendingChainKey.size)
        assertEquals(32, result.receivingChainKey.size)
    }

    @Test @DisplayName("aliceInitiate with OPK returns valid result")
    fun `alice init with opk`() = runTest {
        val aliceIk = CryptoHelper.generateEd25519KeyPair()
        val aliceEk = CryptoHelper.generateX25519KeyPair()
        val result = X3DH.aliceInitiate(
            ourIdentityKey = aliceIk,
            ourEphemeralKey = CryptoHelper.KeyPair(aliceEk.publicKey, aliceEk.privateKey),
            theirIdentityKeyPublic = CryptoHelper.ed25519PkToX25519(bobIk.publicKey),
            theirSignedPrekeyPublic = bobSpk.publicKey,
            theirOneTimePrekeyPublic = bobOpk.publicKey
        )
        assertEquals(32, result.sharedSecret.size)
        assertNotNull(result.header)
    }

    @Test @DisplayName("bobRespond returns result with all fields populated")
    fun `bob respond produces valid result`() = runTest {
        val aliceIk = CryptoHelper.generateEd25519KeyPair()
        val aliceEk = CryptoHelper.generateX25519KeyPair()
        val result = X3DH.bobRespond(
            ourIdentityKey = bobIk,
            ourSignedPrekeyKeyPair = bobSpk,
            ourOneTimePrekeyKeyPair = bobOpk,
            theirIdentityKeyPublic = CryptoHelper.ed25519PkToX25519(aliceIk.publicKey),
            theirEphemeralKeyPublic = aliceEk.publicKey
        )
        assertEquals(32, result.sharedSecret.size)
        assertEquals(32, result.rootKey.size)
    }

    @Test @DisplayName("Different ephemeral key produces different shared secret")
    fun `different ephemeral key`() = runTest {
        val aliceIk = CryptoHelper.generateEd25519KeyPair()
        val ek1 = CryptoHelper.generateX25519KeyPair()
        val ek2 = CryptoHelper.generateX25519KeyPair()
        val r1 = X3DH.aliceInitiate(aliceIk, CryptoHelper.KeyPair(ek1.publicKey, ek1.privateKey),
            CryptoHelper.ed25519PkToX25519(bobIk.publicKey), bobSpk.publicKey)
        val r2 = X3DH.aliceInitiate(aliceIk, CryptoHelper.KeyPair(ek2.publicKey, ek2.privateKey),
            CryptoHelper.ed25519PkToX25519(bobIk.publicKey), bobSpk.publicKey)
        assertFalse(r1.sharedSecret.contentEquals(r2.sharedSecret))
    }

    @Test @DisplayName("Different OPK produces different shared secret")
    fun `different opk`() = runTest {
        val aliceIk = CryptoHelper.generateEd25519KeyPair()
        val ek = CryptoHelper.generateX25519KeyPair()
        val opk2 = CryptoHelper.generateX25519KeyPair()
        val r1 = X3DH.aliceInitiate(aliceIk, CryptoHelper.KeyPair(ek.publicKey, ek.privateKey),
            CryptoHelper.ed25519PkToX25519(bobIk.publicKey), bobSpk.publicKey, bobOpk.publicKey)
        val r2 = X3DH.aliceInitiate(aliceIk, CryptoHelper.KeyPair(ek.publicKey, ek.privateKey),
            CryptoHelper.ed25519PkToX25519(bobIk.publicKey), bobSpk.publicKey, opk2.publicKey)
        assertFalse(r1.sharedSecret.contentEquals(r2.sharedSecret))
    }

    @Test @DisplayName("X3dhResult header contains identity and ephemeral keys")
    fun `header contains keys`() = runTest {
        val aliceIk = CryptoHelper.generateEd25519KeyPair()
        val aliceEk = CryptoHelper.generateX25519KeyPair()
        val result = X3DH.aliceInitiate(
            ourIdentityKey = aliceIk,
            ourEphemeralKey = CryptoHelper.KeyPair(aliceEk.publicKey, aliceEk.privateKey),
            theirIdentityKeyPublic = CryptoHelper.ed25519PkToX25519(bobIk.publicKey),
            theirSignedPrekeyPublic = bobSpk.publicKey
        )
        assertArrayEquals(aliceIk.publicKey, result.header.identityKey)
        assertArrayEquals(aliceEk.publicKey, result.header.ephemeralKey)
    }

    @Test @DisplayName("Same inputs produce same shared secret (deterministic)")
    fun `deterministic results`() = runTest {
        val aliceIk = CryptoHelper.generateEd25519KeyPair()
        val ek = CryptoHelper.generateX25519KeyPair()
        val kp = CryptoHelper.KeyPair(ek.publicKey, ek.privateKey)
        val bobIkPubX = CryptoHelper.ed25519PkToX25519(bobIk.publicKey)
        val r1 = X3DH.aliceInitiate(aliceIk, kp, bobIkPubX, bobSpk.publicKey)
        val r2 = X3DH.aliceInitiate(aliceIk, kp, bobIkPubX, bobSpk.publicKey)
        assertArrayEquals(r1.sharedSecret, r2.sharedSecret)
    }
}

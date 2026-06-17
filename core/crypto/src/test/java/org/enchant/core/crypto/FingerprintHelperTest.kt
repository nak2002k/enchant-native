package org.enchant.core.crypto

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("FingerprintHelper — Native Integration Tests")
class FingerprintHelperTest {

    @Test
    fun `generate returns displayable string and scannable bytes`() {
        val alice = CryptoPrimitives.generateEd25519KeyPair()
        val bob = CryptoPrimitives.generateEd25519KeyPair()

        val (displayable, scannable) = FingerprintHelper.generate(
            localName = "alice",
            localKey = alice.publicKey,
            remoteName = "bob",
            remoteKey = bob.publicKey
        )

        assertTrue(displayable.isNotEmpty())
        assertTrue(scannable.isNotEmpty())
    }

    @Test
    fun `same keys produce matching fingerprints`() {
        val alice = CryptoPrimitives.generateEd25519KeyPair()
        val bob = CryptoPrimitives.generateEd25519KeyPair()

        val (_, scannableA) = FingerprintHelper.generate(
            localName = "alice",
            localKey = alice.publicKey,
            remoteName = "bob",
            remoteKey = bob.publicKey
        )

        val (_, scannableB) = FingerprintHelper.generate(
            localName = "alice",
            localKey = alice.publicKey,
            remoteName = "bob",
            remoteKey = bob.publicKey
        )

        assertTrue(FingerprintHelper.compare(scannableA, scannableB))
    }

    @Test
    fun `different keys produce non-matching fingerprints`() {
        val alice = CryptoPrimitives.generateEd25519KeyPair()
        val bob = CryptoPrimitives.generateEd25519KeyPair()
        val carol = CryptoPrimitives.generateEd25519KeyPair()

        val (_, scannableAB) = FingerprintHelper.generate(
            localName = "alice",
            localKey = alice.publicKey,
            remoteName = "bob",
            remoteKey = bob.publicKey
        )

        val (_, scannableAC) = FingerprintHelper.generate(
            localName = "alice",
            localKey = alice.publicKey,
            remoteName = "carol",
            remoteKey = carol.publicKey
        )

        assertFalse(FingerprintHelper.compare(scannableAB, scannableAC))
    }

    @Test
    fun `order matters for scannable fingerprint`() {
        val alice = CryptoPrimitives.generateEd25519KeyPair()
        val bob = CryptoPrimitives.generateEd25519KeyPair()

        val (_, ab) = FingerprintHelper.generate(
            localName = "alice",
            localKey = alice.publicKey,
            remoteName = "bob",
            remoteKey = bob.publicKey
        )

        val (_, ba) = FingerprintHelper.generate(
            localName = "bob",
            localKey = bob.publicKey,
            remoteName = "alice",
            remoteKey = alice.publicKey
        )

        // Scannable fingerprints are ordered, so AB != BA
        assertFalse(FingerprintHelper.compare(ab, ba))
    }

    @Test
    fun `compare rejects different length fingerprints`() {
        assertFalse(FingerprintHelper.compare(ByteArray(10), ByteArray(20)))
    }
}

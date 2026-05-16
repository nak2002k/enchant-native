package org.enchant.core.crypto

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Double Ratchet")
class DoubleRatchetTest {

    @Test
    @DisplayName("encrypt then decrypt returns original plaintext")
    fun `encrypt decrypt roundtrip`() {
        val bobSpk = CryptoHelper.generateX25519KeyPair()
        val sharedSecret = CryptoHelper.generateRandomKey(32)

        val aliceState = DoubleRatchet.initializeAsAlice(
            sharedSecret = sharedSecret,
            theirSignedPrekeyPublic = bobSpk.publicKey
        )

        val plaintext = "Hello, Bob!".encodeToByteArray()
        val (aliceState2, message) = DoubleRatchet.encrypt(aliceState, plaintext)

        val bobState = DoubleRatchet.initializeAsBob(
            sharedSecret = sharedSecret,
            theirRatchetKeyPublic = aliceState.sendingRatchetKeyPublic ?: ByteArray(32),
            ourSignedPrekeyPrivate = bobSpk.privateKey
        )

        val (bobState2, decrypted) = DoubleRatchet.decrypt(bobState, message)
        assertTrue(decrypted.contentEquals(plaintext), "Decrypted text should match original")
    }

    @Test
    @DisplayName("10 messages in sequence all decrypt correctly")
    fun `ten message sequence`() {
        val sharedSecret = CryptoHelper.generateRandomKey(32)
        val bobSpk = CryptoHelper.generateX25519KeyPair()

        var aliceState = DoubleRatchet.initializeAsAlice(
            sharedSecret = sharedSecret,
            theirSignedPrekeyPublic = bobSpk.publicKey
        )

        val messages = mutableListOf<RatchetMessage>()
        for (i in 0 until 10) {
            val (newState, msg) = DoubleRatchet.encrypt(aliceState, "Message $i".encodeToByteArray())
            aliceState = newState
            messages.add(msg)
        }

        val firstRatchetPub = messages[0].header.copyOfRange(8, 40)
        var bobState = DoubleRatchet.initializeAsBob(
            sharedSecret = sharedSecret,
            theirRatchetKeyPublic = firstRatchetPub,
            ourSignedPrekeyPrivate = bobSpk.privateKey
        )

        for (i in 0 until 10) {
            val (newState, decrypted) = DoubleRatchet.decrypt(bobState, messages[i])
            bobState = newState
            assertEquals("Message $i", decrypted.decodeToString())
        }
    }

    @Test
    @DisplayName("replaying the same message fails (replay protection)")
    fun `replay protection`() {
        val sharedSecret = CryptoHelper.generateRandomKey(32)
        val bobSpk = CryptoHelper.generateX25519KeyPair()

        val aliceState = DoubleRatchet.initializeAsAlice(
            sharedSecret = sharedSecret,
            theirSignedPrekeyPublic = bobSpk.publicKey
        )

        val (_, message) = DoubleRatchet.encrypt(aliceState, "Secret".encodeToByteArray())

        val firstRatchetPub = message.header.copyOfRange(8, 40)
        var bobState = DoubleRatchet.initializeAsBob(
            sharedSecret = sharedSecret,
            theirRatchetKeyPublic = firstRatchetPub,
            ourSignedPrekeyPrivate = bobSpk.privateKey
        )

        val (bobState2, firstDecrypt) = DoubleRatchet.decrypt(bobState, message)
        assertTrue(firstDecrypt.isNotEmpty(), "First decrypt should succeed")

        val (_, secondDecrypt) = DoubleRatchet.decrypt(bobState2, message)
        assertTrue(secondDecrypt.isEmpty(), "Replay should return empty bytes")
    }

    @Test
    @DisplayName("serialize and deserialize preserves state")
    fun `serialization roundtrip`() {
        val sharedSecret = CryptoHelper.generateRandomKey(32)
        val bobSpk = CryptoHelper.generateX25519KeyPair()

        val state = DoubleRatchet.initializeAsAlice(
            sharedSecret = sharedSecret,
            theirSignedPrekeyPublic = bobSpk.publicKey
        )

        val serialized = DoubleRatchet.serializeState(state)
        assertTrue(serialized.isNotEmpty())

        val deserialized = DoubleRatchet.deserializeState(serialized)
        assertNotNull(deserialized)
        assertTrue(state.rootKey.contentEquals(deserialized!!.rootKey))
    }
}

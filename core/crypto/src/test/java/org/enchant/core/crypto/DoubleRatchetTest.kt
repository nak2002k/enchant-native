package org.enchant.core.crypto

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("DoubleRatchet")
class DoubleRatchetTest {
    private lateinit var aliceIk: CryptoHelper.KeyPair
    private lateinit var bobIk: CryptoHelper.KeyPair
    private lateinit var bobSpk: CryptoHelper.KeyPair
    private val sharedSecret = ByteArray(32) { it.toByte() }

    @BeforeEach
    fun setUp() {
        aliceIk = CryptoHelper.generateEd25519KeyPair()
        bobIk = CryptoHelper.generateEd25519KeyPair()
        bobSpk = CryptoHelper.generateX25519KeyPair()
    }

    private fun aliceInit(): RatchetState = DoubleRatchet.initializeAsAlice(
        sharedSecret, bobSpk.publicKey, aliceIk.publicKey,
        CryptoHelper.ed25519PkToX25519(bobIk.publicKey)
    )

    @Nested @DisplayName("State initialization")
    inner class InitTest {
        @Test @DisplayName("initializeAsAlice creates state with non-null keys")
        fun `alice init produces valid state`() {
            val state = aliceInit()
            assertEquals(32, state.rootKey.size)
            assertNotNull(state.sendingChainKey)
            assertNotNull(state.sendingRatchetKeyPublic)
            assertNotNull(state.sendingRatchetKeyPrivate)
        }

        @Test @DisplayName("initializeAsAlice sets initial message numbers to 0")
        fun `alice init message numbers`() {
            val state = aliceInit()
            assertEquals(0, state.sendingMessageNumber)
            assertEquals(0, state.receivingMessageNumber)
        }
    }

    @Nested @DisplayName("Encrypt")
    inner class EncryptTest {
        @Test @DisplayName("encrypt returns non-empty message")
        fun `encrypt produces output`() {
            val state = aliceInit()
            val (_, msg) = DoubleRatchet.encrypt(state, "Hello".encodeToByteArray())
            assertNotNull(msg.header)
            assertNotNull(msg.ciphertext)
            assertTrue(msg.ciphertext.isNotEmpty())
        }

        @Test @DisplayName("encrypt increments message number")
        fun `encrypt increments msg number`() {
            val state = aliceInit()
            val (s1, _) = DoubleRatchet.encrypt(state, "msg1".encodeToByteArray())
            assertEquals(1, s1.sendingMessageNumber)
            val (s2, _) = DoubleRatchet.encrypt(s1, "msg2".encodeToByteArray())
            assertEquals(2, s2.sendingMessageNumber)
        }

        @Test @DisplayName("encrypt with null sending chain triggers ratchet")
        fun `encrypt triggers ratchet when no sending chain`() {
            val state = aliceInit().copy(sendingChainKey = null)
            val (s1, msg) = DoubleRatchet.encrypt(state, "test".encodeToByteArray())
            assertNotNull(s1.sendingChainKey)
            assertTrue(msg.ciphertext.isNotEmpty())
        }
    }

    @Nested @DisplayName("State management")
    inner class StateTest {
        @Test @DisplayName("serialize roundtrip preserves root key and version")
        fun `serialize roundtrip`() {
            val state = aliceInit()
            val serialized = DoubleRatchet.serializeState(state)
            val deserialized = DoubleRatchet.deserializeState(serialized)!!
            assertEquals(state.version, deserialized.version)
            assertArrayEquals(state.rootKey, deserialized.rootKey)
        }

        @Test @DisplayName("deserialize corrupted data returns null")
        fun `corrupted deserialize`() {
            val result = DoubleRatchet.deserializeState(ByteArray(4))
            assertEquals(null, result)
        }
    }

    @Nested @DisplayName("Error handling")
    inner class ErrorTest {
        @Test @DisplayName("decrypt with wrong message returns empty")
        fun `wrong message`() {
            val state = aliceInit().copy(
                receivingRatchetKeyPrivate = CryptoHelper.generateX25519KeyPair().privateKey
            )
            val msg = RatchetMessage(ByteArray(128) { 1 }, ByteArray(16) { 2 })
            val (_, pt) = DoubleRatchet.decrypt(state, msg)
            assertEquals(0, pt.size)
        }

        @Test @DisplayName("decrypt with empty header returns empty")
        fun `empty header`() {
            val state = aliceInit().copy(
                receivingRatchetKeyPrivate = CryptoHelper.generateX25519KeyPair().privateKey
            )
            val msg = RatchetMessage(ByteArray(0), ByteArray(16))
            val (_, pt) = DoubleRatchet.decrypt(state, msg)
            assertEquals(0, pt.size)
        }
    }
}

private fun assertTrue(value: Boolean) = org.junit.jupiter.api.Assertions.assertTrue(value)

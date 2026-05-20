package org.enchant.core.crypto

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("DoubleRatchet — Signal-compatible Double Ratchet")
class DoubleRatchetTest {

    @Nested @DisplayName("Initialization")
    inner class InitTest {
        @Test @DisplayName("Alice initialization creates valid state")
        fun `alice init valid state`() {
            val sharedSecret = ByteArray(32) { 1 }
            val bobSpk = CryptoPrimitives.generateX25519KeyPair()
            val aliceEk = CryptoPrimitives.generateX25519KeyPair()

            val state = DoubleRatchet.initializeAsAlice(sharedSecret, bobSpk.publicKey, aliceEk)

            assertEquals(32, state.rootKey.size)
            assertEquals(32, state.sendingChainKey!!.size)
            assertArrayEquals(aliceEk.publicKey, state.sendingRatchetKeyPublic)
            assertArrayEquals(aliceEk.privateKey, state.sendingRatchetKeyPrivate)
            assertEquals(0, state.sendingMessageNumber)
            assertArrayEquals(bobSpk.publicKey, state.receivingRatchetKeyPublic)
            assertNull(state.receivingChainKey)
            assertEquals(0, state.receivingMessageNumber)
        }

        @Test @DisplayName("Bob initialization creates valid state")
        fun `bob init valid state`() {
            val sharedSecret = ByteArray(32) { 1 }
            val aliceEk = CryptoPrimitives.generateX25519KeyPair()
            val bobSpk = CryptoPrimitives.generateX25519KeyPair()

            val state = DoubleRatchet.initializeAsBob(sharedSecret, aliceEk.publicKey, bobSpk.privateKey)

            assertEquals(32, state.rootKey.size)
            assertEquals(32, state.receivingChainKey!!.size)
            assertArrayEquals(aliceEk.publicKey, state.receivingRatchetKeyPublic)
            assertNull(state.sendingChainKey)
            assertEquals(0, state.receivingMessageNumber)
        }
    }

    @Nested @DisplayName("Encrypt/Decrypt Roundtrip")
    inner class EncryptDecryptTest {
        @Test @DisplayName("single message encrypt/decrypt roundtrip")
        fun `single message roundtrip`() {
            val sharedSecret = ByteArray(32) { 1 }
            val bobSpk = CryptoPrimitives.generateX25519KeyPair()
            val aliceEk = CryptoPrimitives.generateX25519KeyPair()
            val bobSpkForBob = CryptoPrimitives.generateX25519KeyPair()

            var aliceState = DoubleRatchet.initializeAsAlice(sharedSecret, bobSpk.publicKey, aliceEk)
            var bobState = DoubleRatchet.initializeAsBob(sharedSecret, aliceEk.publicKey, bobSpk.privateKey)

            val plaintext = "Hello, World!".encodeToByteArray()
            val (newAliceState, message) = DoubleRatchet.encrypt(aliceState, plaintext)
            val (newBobState, decrypted) = DoubleRatchet.decrypt(bobState, message)

            assertArrayEquals(plaintext, decrypted)
            aliceState = newAliceState
            bobState = newBobState
        }

        @Test @DisplayName("10 consecutive messages without ratchet step")
        fun `10 messages no ratchet`() {
            val sharedSecret = ByteArray(32) { 1 }
            val bobSpk = CryptoPrimitives.generateX25519KeyPair()
            val aliceEk = CryptoPrimitives.generateX25519KeyPair()

            var aliceState = DoubleRatchet.initializeAsAlice(sharedSecret, bobSpk.publicKey, aliceEk)
            var bobState = DoubleRatchet.initializeAsBob(sharedSecret, aliceEk.publicKey, bobSpk.privateKey)

            repeat(10) { i ->
                val plaintext = "Message $i".encodeToByteArray()
                val (newAlice, message) = DoubleRatchet.encrypt(aliceState, plaintext)
                val (newBob, decrypted) = DoubleRatchet.decrypt(bobState, message)
                assertArrayEquals(plaintext, decrypted)
                aliceState = newAlice
                bobState = newBob
            }
        }

        @Test @DisplayName("empty plaintext encrypts and decrypts")
        fun `empty plaintext roundtrip`() {
            val sharedSecret = ByteArray(32) { 1 }
            val bobSpk = CryptoPrimitives.generateX25519KeyPair()
            val aliceEk = CryptoPrimitives.generateX25519KeyPair()

            var aliceState = DoubleRatchet.initializeAsAlice(sharedSecret, bobSpk.publicKey, aliceEk)
            var bobState = DoubleRatchet.initializeAsBob(sharedSecret, aliceEk.publicKey, bobSpk.privateKey)

            val (newAlice, message) = DoubleRatchet.encrypt(aliceState, ByteArray(0))
            val (newBob, decrypted) = DoubleRatchet.decrypt(bobState, message)

            assertArrayEquals(ByteArray(0), decrypted)
        }

        @Test @DisplayName("large plaintext (64KB) roundtrips")
        fun `large plaintext roundtrip`() {
            val sharedSecret = ByteArray(32) { 1 }
            val bobSpk = CryptoPrimitives.generateX25519KeyPair()
            val aliceEk = CryptoPrimitives.generateX25519KeyPair()

            var aliceState = DoubleRatchet.initializeAsAlice(sharedSecret, bobSpk.publicKey, aliceEk)
            var bobState = DoubleRatchet.initializeAsBob(sharedSecret, aliceEk.publicKey, bobSpk.privateKey)

            val plaintext = ByteArray(65536) { (it % 256).toByte() }
            val (newAlice, message) = DoubleRatchet.encrypt(aliceState, plaintext)
            val (newBob, decrypted) = DoubleRatchet.decrypt(bobState, message)

            assertArrayEquals(plaintext, decrypted)
        }

        @Test @DisplayName("binary data roundtrips")
        fun `binary data roundtrip`() {
            val sharedSecret = ByteArray(32) { 1 }
            val bobSpk = CryptoPrimitives.generateX25519KeyPair()
            val aliceEk = CryptoPrimitives.generateX25519KeyPair()

            var aliceState = DoubleRatchet.initializeAsAlice(sharedSecret, bobSpk.publicKey, aliceEk)
            var bobState = DoubleRatchet.initializeAsBob(sharedSecret, aliceEk.publicKey, bobSpk.privateKey)

            val plaintext = ByteArray(256) { it.toByte() }
            val (newAlice, message) = DoubleRatchet.encrypt(aliceState, plaintext)
            val (newBob, decrypted) = DoubleRatchet.decrypt(bobState, message)

            assertArrayEquals(plaintext, decrypted)
        }
    }

    @Nested @DisplayName("DH Ratchet Step")
    inner class DhRatchetTest {
        @Test @DisplayName("Bob sends back, Alice decrypts (DH ratchet)")
        fun `bob replies alice decrypts`() {
            val sharedSecret = ByteArray(32) { 1 }
            val bobSpk = CryptoPrimitives.generateX25519KeyPair()
            val aliceEk = CryptoPrimitives.generateX25519KeyPair()

            var aliceState = DoubleRatchet.initializeAsAlice(sharedSecret, bobSpk.publicKey, aliceEk)
            var bobState = DoubleRatchet.initializeAsBob(sharedSecret, aliceEk.publicKey, bobSpk.privateKey)

            // Alice → Bob
            val (newAlice, msg1) = DoubleRatchet.encrypt(aliceState, "Alice says hi".encodeToByteArray())
            val (newBob, decrypted1) = DoubleRatchet.decrypt(bobState, msg1)
            assertArrayEquals("Alice says hi".encodeToByteArray(), decrypted1)
            aliceState = newAlice
            bobState = newBob

            // Bob → Alice (triggers DH ratchet on Alice's side)
            val (newBob2, msg2) = DoubleRatchet.encrypt(bobState, "Bob replies".encodeToByteArray())
            val (newAlice2, decrypted2) = DoubleRatchet.decrypt(aliceState, msg2)
            assertArrayEquals("Bob replies".encodeToByteArray(), decrypted2)
            bobState = newBob2
            aliceState = newAlice2

            // Continue conversation
            val (newAlice3, msg3) = DoubleRatchet.encrypt(aliceState, "Alice again".encodeToByteArray())
            val (newBob3, decrypted3) = DoubleRatchet.decrypt(bobState, msg3)
            assertArrayEquals("Alice again".encodeToByteArray(), decrypted3)
        }

        @Test @DisplayName("multiple ratchet steps maintain correctness")
        fun `multiple ratchet steps`() {
            val sharedSecret = ByteArray(32) { 1 }
            val bobSpk = CryptoPrimitives.generateX25519KeyPair()
            val aliceEk = CryptoPrimitives.generateX25519KeyPair()

            var aliceState = DoubleRatchet.initializeAsAlice(sharedSecret, bobSpk.publicKey, aliceEk)
            var bobState = DoubleRatchet.initializeAsBob(sharedSecret, aliceEk.publicKey, bobSpk.privateKey)

            repeat(5) { round ->
                // Alice → Bob
                val (newAlice, msgA) = DoubleRatchet.encrypt(aliceState, "A->$round".encodeToByteArray())
                val (newBob, decA) = DoubleRatchet.decrypt(bobState, msgA)
                assertArrayEquals("A->$round".encodeToByteArray(), decA)
                aliceState = newAlice
                bobState = newBob

                // Bob → Alice
                val (newBob2, msgB) = DoubleRatchet.encrypt(bobState, "B->$round".encodeToByteArray())
                val (newAlice2, decB) = DoubleRatchet.decrypt(aliceState, msgB)
                assertArrayEquals("B->$round".encodeToByteArray(), decB)
                bobState = newBob2
                aliceState = newAlice2
            }
        }
    }

    @Nested @DisplayName("Out-of-Order Messages")
    inner class OutOfOrderTest {
        @Test @DisplayName("decrypt message 2 before message 1, then decrypt message 1")
        fun `out of order decrypt`() {
            val sharedSecret = ByteArray(32) { 1 }
            val bobSpk = CryptoPrimitives.generateX25519KeyPair()
            val aliceEk = CryptoPrimitives.generateX25519KeyPair()

            var aliceState = DoubleRatchet.initializeAsAlice(sharedSecret, bobSpk.publicKey, aliceEk)
            var bobState = DoubleRatchet.initializeAsBob(sharedSecret, aliceEk.publicKey, bobSpk.privateKey)

            // Alice sends messages 0 and 1
            val (aliceAfter0, msg0) = DoubleRatchet.encrypt(aliceState, "msg0".encodeToByteArray())
            val (aliceAfter1, msg1) = DoubleRatchet.encrypt(aliceAfter0, "msg1".encodeToByteArray())

            // Bob receives msg1 first (out of order)
            val (bobAfter1, decrypted1) = DoubleRatchet.decrypt(bobState, msg1)
            assertArrayEquals("msg1".encodeToByteArray(), decrypted1)

            // Bob then receives msg0
            val (bobAfter0, decrypted0) = DoubleRatchet.decrypt(bobAfter1, msg0)
            assertArrayEquals("msg0".encodeToByteArray(), decrypted0)
        }

        @Test @DisplayName("skipped keys are bounded to MAX_SKIPPED_KEYS")
        fun `skipped keys bounded`() {
            val sharedSecret = ByteArray(32) { 1 }
            val bobSpk = CryptoPrimitives.generateX25519KeyPair()
            val aliceEk = CryptoPrimitives.generateX25519KeyPair()

            var aliceState = DoubleRatchet.initializeAsAlice(sharedSecret, bobSpk.publicKey, aliceEk)
            var bobState = DoubleRatchet.initializeAsBob(sharedSecret, aliceEk.publicKey, bobSpk.privateKey)

            // Alice sends 1001 messages
            val messages = mutableListOf<DoubleRatchet.RatchetMessage>()
            repeat(1001) {
                val (newState, msg) = DoubleRatchet.encrypt(aliceState, "msg$it".encodeToByteArray())
                messages.add(msg)
                aliceState = newState
            }

            // Bob receives the last one first
            val (bobAfterLast, _) = DoubleRatchet.decrypt(bobState, messages.last())
            // The skipped keys map should be bounded
            assertTrue(bobAfterLast.skippedMessageKeys.size <= 1000)
        }
    }

    @Nested @DisplayName("Replay Protection")
    inner class ReplayTest {
        @Test @DisplayName("replayed message throws ReplayException")
        fun `replay throws`() {
            val sharedSecret = ByteArray(32) { 1 }
            val bobSpk = CryptoPrimitives.generateX25519KeyPair()
            val aliceEk = CryptoPrimitives.generateX25519KeyPair()

            var aliceState = DoubleRatchet.initializeAsAlice(sharedSecret, bobSpk.publicKey, aliceEk)
            var bobState = DoubleRatchet.initializeAsBob(sharedSecret, aliceEk.publicKey, bobSpk.privateKey)

            val (newAlice, msg) = DoubleRatchet.encrypt(aliceState, "replay me".encodeToByteArray())
            val (newBob, _) = DoubleRatchet.decrypt(bobState, msg)

            assertThrows<DoubleRatchet.ReplayException> {
                DoubleRatchet.decrypt(newBob, msg)
            }
        }
    }

    @Nested @DisplayName("Serialization")
    inner class SerializationTest {
        @Test @DisplayName("serialize/deserialize roundtrip")
        fun `serialize deserialize roundtrip`() {
            val sharedSecret = ByteArray(32) { 1 }
            val bobSpk = CryptoPrimitives.generateX25519KeyPair()
            val aliceEk = CryptoPrimitives.generateX25519KeyPair()

            var aliceState = DoubleRatchet.initializeAsAlice(sharedSecret, bobSpk.publicKey, aliceEk)
            var bobState = DoubleRatchet.initializeAsBob(sharedSecret, aliceEk.publicKey, bobSpk.privateKey)

            // Exchange some messages
            repeat(5) {
                val (newAlice, msg) = DoubleRatchet.encrypt(aliceState, "msg$it".encodeToByteArray())
                val (newBob, _) = DoubleRatchet.decrypt(bobState, msg)
                aliceState = newAlice
                bobState = newBob
            }

            // Serialize and deserialize
            val serialized = DoubleRatchet.serializeState(bobState)
            val deserialized = DoubleRatchet.deserializeState(serialized)

            assertNotNull(deserialized)
            assertArrayEquals(bobState.rootKey, deserialized!!.rootKey)
            assertEquals(bobState.sendingMessageNumber, deserialized.sendingMessageNumber)
            assertEquals(bobState.receivingMessageNumber, deserialized.receivingMessageNumber)
            assertEquals(bobState.skippedMessageKeys.size, deserialized.skippedMessageKeys.size)
        }

        @Test @DisplayName("corrupted data returns null")
        fun `corrupted data returns null`() {
            val corrupted = ByteArray(10) { 0xFF.toByte() }
            assertNull(DoubleRatchet.deserializeState(corrupted))
        }

        @Test @DisplayName("empty data returns null")
        fun `empty data returns null`() {
            assertNull(DoubleRatchet.deserializeState(ByteArray(0)))
        }

        @Test @DisplayName("state survives app restart (serialize → decrypt after deserialize)")
        fun `state survives restart`() {
            val sharedSecret = ByteArray(32) { 1 }
            val bobSpk = CryptoPrimitives.generateX25519KeyPair()
            val aliceEk = CryptoPrimitives.generateX25519KeyPair()

            var aliceState = DoubleRatchet.initializeAsAlice(sharedSecret, bobSpk.publicKey, aliceEk)
            var bobState = DoubleRatchet.initializeAsBob(sharedSecret, aliceEk.publicKey, bobSpk.privateKey)

            val (newAlice, msg) = DoubleRatchet.encrypt(aliceState, "before restart".encodeToByteArray())
            val (newBob, _) = DoubleRatchet.decrypt(bobState, msg)

            // Simulate app restart
            val serialized = DoubleRatchet.serializeState(newBob)
            val restored = DoubleRatchet.deserializeState(serialized)!!

            // Alice sends another message
            val (newAlice2, msg2) = DoubleRatchet.encrypt(newAlice, "after restart".encodeToByteArray())

            // Restored Bob can decrypt
            val (_, decrypted) = DoubleRatchet.decrypt(restored, msg2)
            assertArrayEquals("after restart".encodeToByteArray(), decrypted)
        }
    }

    @Nested @DisplayName("Memory Zeroing")
    inner class ZeroingTest {
        @Test @DisplayName("zero() clears all secret material")
        fun `zero clears secrets`() {
            val sharedSecret = ByteArray(32) { 1 }
            val bobSpk = CryptoPrimitives.generateX25519KeyPair()
            val aliceEk = CryptoPrimitives.generateX25519KeyPair()

            val state = DoubleRatchet.initializeAsAlice(sharedSecret, bobSpk.publicKey, aliceEk)
            val rootKeyCopy = state.rootKey.copyOf()

            state.zero()

            assertTrue(state.rootKey.all { it == 0.toByte() })
            assertFalse(state.rootKey.contentEquals(rootKeyCopy))
        }

        @Test @DisplayName("deepCopy creates independent copy")
        fun `deep copy independent`() {
            val sharedSecret = ByteArray(32) { 1 }
            val bobSpk = CryptoPrimitives.generateX25519KeyPair()
            val aliceEk = CryptoPrimitives.generateX25519KeyPair()

            val state = DoubleRatchet.initializeAsAlice(sharedSecret, bobSpk.publicKey, aliceEk)
            val copy = state.deepCopy()

            state.zero()

            assertFalse(copy.rootKey.all { it == 0.toByte() })
            assertArrayEquals(state.rootKey, ByteArray(32))
        }
    }
}

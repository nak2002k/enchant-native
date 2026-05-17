package org.enchant.core.crypto

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("DoubleRatchet — Full Coverage")
class DoubleRatchetTest {

    private fun createAliceBobStates(): Triple<RatchetState, RatchetState, CryptoHelper.KeyPair> {
        val sharedSecret = CryptoHelper.generateRandomKey(32)
        val bobSpk = CryptoHelper.generateX25519KeyPair()
        val aliceState = DoubleRatchet.initializeAsAlice(
            sharedSecret = sharedSecret,
            theirSignedPrekeyPublic = bobSpk.publicKey
        )
        val bobState = DoubleRatchet.initializeAsBob(
            sharedSecret = sharedSecret,
            theirRatchetKeyPublic = aliceState.sendingRatchetKeyPublic!!,
            ourSignedPrekeyPrivate = bobSpk.privateKey
        )
        return Triple(aliceState, bobState, bobSpk)
    }

    @Nested @DisplayName("Initialization")
    inner class InitTest {
        @Test @DisplayName("initializeAsAlice produces non-null root key")
        fun `alice init has root key`() {
            val sharedSecret = CryptoHelper.generateRandomKey(32)
            val bobSpk = CryptoHelper.generateX25519KeyPair()
            val state = DoubleRatchet.initializeAsAlice(sharedSecret, bobSpk.publicKey)
            assertEquals(32, state.rootKey.size)
            assertNotNull(state.sendingChainKey)
            assertNotNull(state.sendingRatchetKeyPublic)
            assertNotNull(state.sendingRatchetKeyPrivate)
            assertNull(state.receivingChainKey)
            assertEquals(0, state.sendingMessageNumber)
        }

        @Test @DisplayName("initializeAsBob produces non-null root key")
        fun `bob init has root key`() {
            val sharedSecret = CryptoHelper.generateRandomKey(32)
            val bobSpk = CryptoHelper.generateX25519KeyPair()
            val aliceState = DoubleRatchet.initializeAsAlice(sharedSecret, bobSpk.publicKey)
            val bobState = DoubleRatchet.initializeAsBob(
                sharedSecret,
                aliceState.sendingRatchetKeyPublic!!,
                bobSpk.privateKey
            )
            assertEquals(32, bobState.rootKey.size)
            assertNotNull(bobState.receivingChainKey)
            assertNotNull(bobState.receivingRatchetKeyPublic)
            assertNull(bobState.sendingChainKey)
            assertEquals(0, bobState.receivingMessageNumber)
        }

        @Test @DisplayName("Alice and Bob initial root keys match")
        fun `alice bob root keys match`() {
            val sharedSecret = CryptoHelper.generateRandomKey(32)
            val bobSpk = CryptoHelper.generateX25519KeyPair()
            val aliceState = DoubleRatchet.initializeAsAlice(sharedSecret, bobSpk.publicKey)
            val bobState = DoubleRatchet.initializeAsBob(
                sharedSecret,
                aliceState.sendingRatchetKeyPublic!!,
                bobSpk.privateKey
            )
            assertTrue(aliceState.rootKey.contentEquals(bobState.rootKey))
        }

        @Test @DisplayName("different shared secrets produce different states")
        fun `different secrets different states`() {
            val bobSpk = CryptoHelper.generateX25519KeyPair()
            val s1 = CryptoHelper.generateRandomKey(32)
            val s2 = CryptoHelper.generateRandomKey(32)
            val a1 = DoubleRatchet.initializeAsAlice(s1, bobSpk.publicKey)
            val a2 = DoubleRatchet.initializeAsAlice(s2, bobSpk.publicKey)
            assertFalse(a1.rootKey.contentEquals(a2.rootKey))
        }

        @Test @DisplayName("different SPK produces different receiving ratchet key")
        fun `different spk different states`() {
            val sharedSecret = CryptoHelper.generateRandomKey(32)
            val bobSpk1 = CryptoHelper.generateX25519KeyPair()
            val bobSpk2 = CryptoHelper.generateX25519KeyPair()
            val a1 = DoubleRatchet.initializeAsAlice(sharedSecret, bobSpk1.publicKey)
            val a2 = DoubleRatchet.initializeAsAlice(sharedSecret, bobSpk2.publicKey)
            assertTrue(a1.rootKey.contentEquals(a2.rootKey))
            assertFalse(a1.receivingRatchetKeyPublic!!.contentEquals(a2.receivingRatchetKeyPublic!!))
        }
    }

    @Nested @DisplayName("Encrypt/Decrypt Roundtrip")
    inner class EncryptDecryptTest {
        @Test @DisplayName("single message encrypt/decrypt roundtrip")
        fun `single message roundtrip`() {
            val (aliceState, bobState, _) = createAliceBobStates()
            val plaintext = "Hello, Bob!".encodeToByteArray()
            val (aliceState2, message) = DoubleRatchet.encrypt(aliceState, plaintext)
            val (bobState2, decrypted) = DoubleRatchet.decrypt(bobState, message)
            assertArrayEquals(plaintext, decrypted)
        }

        @Test @DisplayName("empty message encrypt/decrypt roundtrip")
        fun `empty message roundtrip`() {
            val (aliceState, bobState, _) = createAliceBobStates()
            val plaintext = ByteArray(0)
            val (aliceState2, message) = DoubleRatchet.encrypt(aliceState, plaintext)
            val (bobState2, decrypted) = DoubleRatchet.decrypt(bobState, message)
            assertArrayEquals(plaintext, decrypted)
        }

        @Test @DisplayName("large message (10KB) encrypt/decrypt roundtrip")
        fun `large message roundtrip`() {
            val (aliceState, bobState, _) = createAliceBobStates()
            val plaintext = ByteArray(10240) { (it % 256).toByte() }
            val (aliceState2, message) = DoubleRatchet.encrypt(aliceState, plaintext)
            val (bobState2, decrypted) = DoubleRatchet.decrypt(bobState, message)
            assertArrayEquals(plaintext, decrypted)
        }

        @Test @DisplayName("message header contains DH public key")
        fun `message header has dh key`() {
            val (aliceState, _, _) = createAliceBobStates()
            val (_, message) = DoubleRatchet.encrypt(aliceState, "test".encodeToByteArray())
            assertTrue(message.header.isNotEmpty())
            assertTrue(message.ciphertext.isNotEmpty())
        }

        @Test @DisplayName("encrypted message ciphertext is not plaintext")
        fun `ciphertext differs from plaintext`() {
            val (aliceState, _, _) = createAliceBobStates()
            val plaintext = "secret message".encodeToByteArray()
            val (_, message) = DoubleRatchet.encrypt(aliceState, plaintext)
            assertFalse(message.ciphertext.contentEquals(plaintext))
        }
    }

    @Nested @DisplayName("Message Sequences")
    inner class SequenceTest {
        @Test @DisplayName("10 messages in sequence all decrypt correctly")
        fun `ten message sequence`() {
            val (aliceState, bobState, _) = createAliceBobStates()
            var alice = aliceState
            val messages = mutableListOf<RatchetMessage>()
            for (i in 0 until 10) {
                val (newState, msg) = DoubleRatchet.encrypt(alice, "Message $i".encodeToByteArray())
                alice = newState
                messages.add(msg)
            }
            var bob = bobState
            for (i in 0 until 10) {
                val (newState, decrypted) = DoubleRatchet.decrypt(bob, messages[i])
                bob = newState
                assertEquals("Message $i", decrypted.decodeToString())
            }
        }

        @Test @DisplayName("100 messages in sequence all decrypt correctly")
        fun `hundred message sequence`() {
            val (aliceState, bobState, _) = createAliceBobStates()
            var alice = aliceState
            val messages = mutableListOf<RatchetMessage>()
            for (i in 0 until 100) {
                val (newState, msg) = DoubleRatchet.encrypt(alice, "Msg$i".encodeToByteArray())
                alice = newState
                messages.add(msg)
            }
            var bob = bobState
            for (i in 0 until 100) {
                val (newState, decrypted) = DoubleRatchet.decrypt(bob, messages[i])
                bob = newState
                assertEquals("Msg$i", decrypted.decodeToString())
            }
        }

        @Test @DisplayName("bidirectional message exchange works")
        fun `bidirectional messages`() {
            val (aliceState, bobState, bobSpk) = createAliceBobStates()
            var alice = aliceState
            var bob = bobState

            val (alice2, msgA1) = DoubleRatchet.encrypt(alice, "A->B 1".encodeToByteArray())
            alice = alice2
            val (bob2, decA1) = DoubleRatchet.decrypt(bob, msgA1)
            bob = bob2
            assertEquals("A->B 1", decA1.decodeToString())

            val (bob3, msgB1) = DoubleRatchet.encrypt(bob, "B->A 1".encodeToByteArray())
            bob = bob3
            val (alice3, decB1) = DoubleRatchet.decrypt(alice, msgB1)
            alice = alice3
            assertEquals("B->A 1", decB1.decodeToString())

            val (alice4, msgA2) = DoubleRatchet.encrypt(alice, "A->B 2".encodeToByteArray())
            alice = alice4
            val (bob4, decA2) = DoubleRatchet.decrypt(bob, msgA2)
            bob = bob4
            assertEquals("A->B 2", decA2.decodeToString())
        }

        @Test @DisplayName("out-of-order delivery: message 2 arrives before message 1")
        fun `out of order delivery`() {
            val (aliceState, bobState, _) = createAliceBobStates()
            var alice = aliceState

            val (alice2, msg1) = DoubleRatchet.encrypt(alice, "first".encodeToByteArray())
            alice = alice2
            val (alice3, msg2) = DoubleRatchet.encrypt(alice, "second".encodeToByteArray())
            alice = alice3

            var bob = bobState
            val (bob2, dec2) = DoubleRatchet.decrypt(bob, msg2)
            bob = bob2
            assertEquals("second", dec2.decodeToString())

            val (bob3, dec1) = DoubleRatchet.decrypt(bob, msg1)
            bob = bob3
            assertEquals("first", dec1.decodeToString())
        }

        @Test @DisplayName("skipped messages are stored for later decryption")
        fun `skipped messages stored`() {
            val (aliceState, bobState, _) = createAliceBobStates()
            var alice = aliceState
            val messages = mutableListOf<RatchetMessage>()
            for (i in 0 until 5) {
                val (newState, msg) = DoubleRatchet.encrypt(alice, "skip-$i".encodeToByteArray())
                alice = newState
                messages.add(msg)
            }
            var bob = bobState
            val (bob2, dec4) = DoubleRatchet.decrypt(bob, messages[4])
            bob = bob2
            assertEquals("skip-4", dec4.decodeToString())
            assertTrue(bob.skippedMessageKeys.isNotEmpty())
        }
    }

    @Nested @DisplayName("Replay Protection")
    inner class ReplayTest {
        @Test @DisplayName("replaying the same message returns empty bytes")
        fun `replay returns empty`() {
            val (aliceState, bobState, _) = createAliceBobStates()
            val (_, message) = DoubleRatchet.encrypt(aliceState, "Secret".encodeToByteArray())
            val (bobState2, firstDecrypt) = DoubleRatchet.decrypt(bobState, message)
            assertTrue(firstDecrypt.isNotEmpty())
            val (_, secondDecrypt) = DoubleRatchet.decrypt(bobState2, message)
            assertTrue(secondDecrypt.isEmpty())
        }

        @Test @DisplayName("replaying after other messages still fails")
        fun `replay after other messages`() {
            val (aliceState, bobState, _) = createAliceBobStates()
            var alice = aliceState
            val (_, msg1) = DoubleRatchet.encrypt(alice, "msg1".encodeToByteArray())
            alice = aliceState
            val (alice2, msg2) = DoubleRatchet.encrypt(alice, "msg2".encodeToByteArray())
            alice = alice2

            var bob = bobState
            val (bob2, _) = DoubleRatchet.decrypt(bob, msg2)
            bob = bob2
            val (bob3, _) = DoubleRatchet.decrypt(bob, msg1)
            bob = bob3

            val (_, replay) = DoubleRatchet.decrypt(bob, msg1)
            assertTrue(replay.isEmpty())
        }
    }

    @Nested @DisplayName("Ratchet Step (DH Ratchet)")
    inner class RatchetStepTest {
        @Test @DisplayName("Bob sending triggers DH ratchet step")
        fun `bob send triggers ratchet`() {
            val (aliceState, bobState, bobSpk) = createAliceBobStates()
            val (_, msgA) = DoubleRatchet.encrypt(aliceState, "A->B".encodeToByteArray())
            val (bob2, _) = DoubleRatchet.decrypt(bobState, msgA)
            assertNull(bob2.sendingChainKey)

            val (bob3, msgB) = DoubleRatchet.encrypt(bob2, "B->A".encodeToByteArray())
            assertNotNull(bob3.sendingChainKey)
            assertNotNull(bob3.sendingRatchetKeyPublic)
        }

        @Test @DisplayName("ratchet step produces new DH key pair")
        fun `ratchet step new dh key`() {
            val (aliceState, bobState, _) = createAliceBobStates()
            val (_, msgA) = DoubleRatchet.encrypt(aliceState, "A->B".encodeToByteArray())
            val (bob2, _) = DoubleRatchet.decrypt(bobState, msgA)
            val (bob3, msgB) = DoubleRatchet.encrypt(bob2, "B->A".encodeToByteArray())

            val (alice2, _) = DoubleRatchet.decrypt(aliceState, msgB)
            assertNotNull(alice2.sendingChainKey)
        }

        @Test @DisplayName("ratcheted messages decrypt correctly")
        fun `ratcheted messages decrypt`() {
            val (aliceState, bobState, _) = createAliceBobStates()
            var alice = aliceState
            var bob = bobState

            val (a2, mA) = DoubleRatchet.encrypt(alice, "A1".encodeToByteArray())
            alice = a2
            val (b2, dA) = DoubleRatchet.decrypt(bob, mA)
            bob = b2
            assertEquals("A1", dA.decodeToString())

            val (b3, mB) = DoubleRatchet.encrypt(bob, "B1".encodeToByteArray())
            bob = b3
            val (a3, dB) = DoubleRatchet.decrypt(alice, mB)
            alice = a3
            assertEquals("B1", dB.decodeToString())

            val (a4, mA2) = DoubleRatchet.encrypt(alice, "A2".encodeToByteArray())
            alice = a4
            val (b4, dA2) = DoubleRatchet.decrypt(bob, mA2)
            bob = b4
            assertEquals("A2", dA2.decodeToString())
        }
    }

    @Nested @DisplayName("Serialization")
    inner class SerializationTest {
        @Test @DisplayName("serialize and deserialize preserves root key")
        fun `serialize preserves root key`() {
            val (aliceState, _, _) = createAliceBobStates()
            val serialized = DoubleRatchet.serializeState(aliceState)
            assertTrue(serialized.isNotEmpty())
            val deserialized = DoubleRatchet.deserializeState(serialized)
            assertNotNull(deserialized)
            assertTrue(aliceState.rootKey.contentEquals(deserialized!!.rootKey))
        }

        @Test @DisplayName("serialize and deserialize preserves sending chain key")
        fun `serialize preserves sending chain`() {
            val (aliceState, _, _) = createAliceBobStates()
            val serialized = DoubleRatchet.serializeState(aliceState)
            val deserialized = DoubleRatchet.deserializeState(serialized)!!
            assertTrue(aliceState.sendingChainKey!!.contentEquals(deserialized.sendingChainKey!!))
        }

        @Test @DisplayName("serialize and deserialize preserves message numbers")
        fun `serialize preserves message numbers`() {
            val (aliceState, _, _) = createAliceBobStates()
            var alice = aliceState
            for (i in 0 until 5) {
                val (newState, _) = DoubleRatchet.encrypt(alice, "msg".encodeToByteArray())
                alice = newState
            }
            val serialized = DoubleRatchet.serializeState(alice)
            val deserialized = DoubleRatchet.deserializeState(serialized)!!
            assertEquals(alice.sendingMessageNumber, deserialized.sendingMessageNumber)
        }

        @Test @DisplayName("serialize and deserialize preserves skipped keys")
        fun `serialize preserves skipped keys`() {
            val (aliceState, bobState, _) = createAliceBobStates()
            var alice = aliceState
            val messages = mutableListOf<RatchetMessage>()
            for (i in 0 until 3) {
                val (newState, msg) = DoubleRatchet.encrypt(alice, "skip-$i".encodeToByteArray())
                alice = newState
                messages.add(msg)
            }
            var bob = bobState
            val (bob2, _) = DoubleRatchet.decrypt(bob, messages[2])
            bob = bob2
            assertTrue(bob.skippedMessageKeys.isNotEmpty())

            val serialized = DoubleRatchet.serializeState(bob)
            val deserialized = DoubleRatchet.deserializeState(serialized)!!
            assertEquals(bob.skippedMessageKeys.size, deserialized.skippedMessageKeys.size)
        }

        @Test @DisplayName("serialize and deserialize preserves consumed keys")
        fun `serialize preserves consumed keys`() {
            val (aliceState, bobState, _) = createAliceBobStates()
            var alice = aliceState
            val messages = mutableListOf<RatchetMessage>()
            for (i in 0 until 3) {
                val (newState, msg) = DoubleRatchet.encrypt(alice, "msg-$i".encodeToByteArray())
                alice = newState
                messages.add(msg)
            }
            var bob = bobState
            for (i in 0 until 3) {
                val (newState, _) = DoubleRatchet.decrypt(bob, messages[i])
                bob = newState
            }
            assertTrue(bob.consumedKeys.isNotEmpty())

            val serialized = DoubleRatchet.serializeState(bob)
            val deserialized = DoubleRatchet.deserializeState(serialized)!!
            assertEquals(bob.consumedKeys.size, deserialized.consumedKeys.size)
        }

        @Test @DisplayName("deserialize corrupted data returns null")
        fun `deserialize corrupted returns null`() {
            val (aliceState, _, _) = createAliceBobStates()
            val serialized = DoubleRatchet.serializeState(aliceState)
            serialized[0] = (serialized[0].toInt() xor 0xFF).toByte()
            val deserialized = DoubleRatchet.deserializeState(serialized)
            assertNull(deserialized)
        }

        @Test @DisplayName("deserialize truncated data returns null")
        fun `deserialize truncated returns null`() {
            val (aliceState, _, _) = createAliceBobStates()
            val serialized = DoubleRatchet.serializeState(aliceState)
            val truncated = serialized.copyOf(serialized.size / 2)
            val deserialized = DoubleRatchet.deserializeState(truncated)
            assertNull(deserialized)
        }

        @Test @DisplayName("deserialize empty data returns null")
        fun `deserialize empty returns null`() {
            val deserialized = DoubleRatchet.deserializeState(ByteArray(0))
            assertNull(deserialized)
        }

        @Test @DisplayName("deserialized state can continue encrypt/decrypt")
        fun `deserialized state usable`() {
            val (aliceState, bobState, _) = createAliceBobStates()
            var alice = aliceState
            for (i in 0 until 5) {
                val (newState, _) = DoubleRatchet.encrypt(alice, "msg".encodeToByteArray())
                alice = newState
            }
            val serialized = DoubleRatchet.serializeState(alice)
            val restored = DoubleRatchet.deserializeState(serialized)!!

            val (alice2, msg) = DoubleRatchet.encrypt(restored, "after restore".encodeToByteArray())
            var bob = bobState
            for (i in 0 until 5) {
                val (newState, _) = DoubleRatchet.decrypt(bob, RatchetMessage(ByteArray(0), ByteArray(0)))
                bob = newState
            }
        }
    }

    @Nested @DisplayName("Security Invariants")
    inner class SecurityTest {
        @Test @DisplayName("each message produces different ciphertext (nonce uniqueness)")
        fun `unique ciphertexts`() {
            val (aliceState, _, _) = createAliceBobStates()
            var alice = aliceState
            val plaintext = "same text".encodeToByteArray()
            val (alice2, msg1) = DoubleRatchet.encrypt(alice, plaintext)
            alice = alice2
            val (alice3, msg2) = DoubleRatchet.encrypt(alice, plaintext)
            assertFalse(msg1.ciphertext.contentEquals(msg2.ciphertext))
        }

        @Test @DisplayName("zeroBytes clears all key material in state")
        fun `zero clears keys`() {
            val (aliceState, _, _) = createAliceBobStates()
            val rootKeyCopy = aliceState.rootKey.copyOf()
            aliceState.zero()
            assertTrue(aliceState.rootKey.all { it == 0.toByte() })
        }

        @Test @DisplayName("C04: zero() uses deep copies so original state is not corrupted")
        fun `zero uses deep copies`() {
            val (aliceState, _, _) = createAliceBobStates()
            val rootKeyCopy = aliceState.rootKey.copyOf()
            val sendingChainCopy = aliceState.sendingChainKey?.copyOf()
            aliceState.zero()
            assertTrue(aliceState.rootKey.all { it == 0.toByte() })
            assertTrue(rootKeyCopy.any { it != 0.toByte() })
            sendingChainCopy?.let { assertTrue(it.any { b -> b != 0.toByte() }) }
        }

        @Test @DisplayName("C04: zero() zeros all nullable key fields")
        fun `zero zeros all nullable fields`() {
            val (aliceState, _, _) = createAliceBobStates()
            aliceState.zero()
            assertTrue(aliceState.rootKey.all { it == 0.toByte() })
            aliceState.sendingChainKey?.let { assertTrue(it.all { b -> b == 0.toByte() }) }
            aliceState.sendingRatchetKeyPublic?.let { assertTrue(it.all { b -> b == 0.toByte() }) }
            aliceState.sendingRatchetKeyPrivate?.let { assertTrue(it.all { b -> b == 0.toByte() }) }
        }

        @Test @DisplayName("C05: zero() clears skippedMessageKeys map")
        fun `zero clears skipped keys map`() {
            val (aliceState, bobState, _) = createAliceBobStates()
            var alice = aliceState
            val messages = mutableListOf<RatchetMessage>()
            for (i in 0 until 3) {
                val (newState, msg) = DoubleRatchet.encrypt(alice, "skip-$i".encodeToByteArray())
                alice = newState
                messages.add(msg)
            }
            var bob = bobState
            val (bob2, _) = DoubleRatchet.decrypt(bob, messages[2])
            bob = bob2
            assertTrue(bob.skippedMessageKeys.isNotEmpty())
            bob.zero()
            assertTrue(bob.skippedMessageKeys.isEmpty())
        }

        @Test @DisplayName("C05: zero() clears consumedKeys set")
        fun `zero clears consumed keys set`() {
            val (aliceState, bobState, _) = createAliceBobStates()
            var alice = aliceState
            val (alice2, msg) = DoubleRatchet.encrypt(alice, "test".encodeToByteArray())
            alice = alice2
            var bob = bobState
            val (bob2, _) = DoubleRatchet.decrypt(bob, msg)
            bob = bob2
            assertTrue(bob.consumedKeys.isNotEmpty())
            bob.zero()
            assertTrue(bob.consumedKeys.isEmpty())
        }

        @Test @DisplayName("C05: zero() zeros skipped MessageKey values before clearing")
        fun `zero zeros skipped message keys`() {
            val (aliceState, bobState, _) = createAliceBobStates()
            var alice = aliceState
            val messages = mutableListOf<RatchetMessage>()
            for (i in 0 until 2) {
                val (newState, msg) = DoubleRatchet.encrypt(alice, "skip-$i".encodeToByteArray())
                alice = newState
                messages.add(msg)
            }
            var bob = bobState
            val (bob2, _) = DoubleRatchet.decrypt(bob, messages[1])
            bob = bob2
            val keyBefore = bob.skippedMessageKeys.values.first().key.copyOf()
            bob.zero()
            assertTrue(keyBefore.any { it != 0.toByte() })
        }

        @Test @DisplayName("corrupted message header returns empty plaintext")
        fun `corrupted header returns empty`() {
            val (aliceState, bobState, _) = createAliceBobStates()
            val (_, message) = DoubleRatchet.encrypt(aliceState, "test".encodeToByteArray())
            message.header[0] = (message.header[0].toInt() xor 0xFF).toByte()
            message.header[1] = (message.header[1].toInt() xor 0xFF).toByte()
            message.header[2] = (message.header[2].toInt() xor 0xFF).toByte()
            message.header[3] = (message.header[3].toInt() xor 0xFF).toByte()
            val (_, decrypted) = DoubleRatchet.decrypt(bobState, message)
            assertTrue(decrypted.isEmpty())
        }

        @Test @DisplayName("truncated message returns empty plaintext")
        fun `truncated message returns empty`() {
            val (aliceState, bobState, _) = createAliceBobStates()
            val (_, message) = DoubleRatchet.encrypt(aliceState, "test".encodeToByteArray())
            val truncated = RatchetMessage(message.header.copyOf(4), message.ciphertext)
            val (_, decrypted) = DoubleRatchet.decrypt(bobState, truncated)
            assertTrue(decrypted.isEmpty())
        }

        @Test @DisplayName("message with wrong key fails decryption")
        fun `wrong key fails`() {
            val sharedSecret = CryptoHelper.generateRandomKey(32)
            val bobSpk = CryptoHelper.generateX25519KeyPair()
            val aliceState = DoubleRatchet.initializeAsAlice(sharedSecret, bobSpk.publicKey)
            val wrongBobSpk = CryptoHelper.generateX25519KeyPair()
            val wrongBobState = DoubleRatchet.initializeAsBob(
                sharedSecret,
                aliceState.sendingRatchetKeyPublic!!,
                wrongBobSpk.privateKey
            )
            val (_, message) = DoubleRatchet.encrypt(aliceState, "secret".encodeToByteArray())
            val (_, decrypted) = DoubleRatchet.decrypt(wrongBobState, message)
            assertTrue(decrypted.isEmpty())
        }
    }

    @Nested @DisplayName("Edge Cases")
    inner class EdgeCaseTest {
        @Test @DisplayName("encrypt with no receiving key triggers ratchet step")
        fun `encrypt triggers ratchet`() {
            val (aliceState, _, _) = createAliceBobStates()
            val (_, msgA) = DoubleRatchet.encrypt(aliceState, "A->B".encodeToByteArray())
            val (bobState, _, bobSpk) = createAliceBobStates()
            val (bob2, _) = DoubleRatchet.decrypt(bobState, msgA)
            assertNotNull(bob2.sendingChainKey)
            val (bob3, msgB) = DoubleRatchet.encrypt(bob2, "B->A".encodeToByteArray())
            assertNotNull(bob3.sendingChainKey)
        }

        @Test @DisplayName("max skipped keys limit is enforced")
        fun `max skipped keys limit`() {
            val (aliceState, bobState, _) = createAliceBobStates()
            var alice = aliceState
            val messages = mutableListOf<RatchetMessage>()
            for (i in 0 until 1001) {
                val (newState, msg) = DoubleRatchet.encrypt(alice, "skip-$i".encodeToByteArray())
                alice = newState
                messages.add(msg)
            }
            var bob = bobState
            val (bob2, _) = DoubleRatchet.decrypt(bob, messages[1000])
            bob = bob2
            assertTrue(bob.skippedMessageKeys.size <= 1000)
        }

        @Test @DisplayName("version field is preserved through serialization")
        fun `version preserved`() {
            val (aliceState, _, _) = createAliceBobStates()
            val serialized = DoubleRatchet.serializeState(aliceState)
            val deserialized = DoubleRatchet.deserializeState(serialized)!!
            assertEquals(aliceState.version, deserialized.version)
        }
    }
}

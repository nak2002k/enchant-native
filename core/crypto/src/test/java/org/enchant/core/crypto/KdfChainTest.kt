package org.enchant.core.crypto

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("KdfChain — Signal-compatible KDF")
class KdfChainTest {

    private fun hexToBytes(hex: String) = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Nested @DisplayName("Root and Chain Key Derivation")
    inner class RootChainDerivationTest {
        @Test @DisplayName("derives 32-byte root key and 32-byte chain key")
        fun `derives correct sizes`() {
            val rootKey = ByteArray(32) { 1 }
            val dhOutput = ByteArray(32) { 2 }
            val (newRoot, newChain) = KdfChain.deriveRootAndChainKey(rootKey, dhOutput)
            assertEquals(32, newRoot.size)
            assertEquals(32, newChain.size)
        }

        @Test @DisplayName("same inputs produce same outputs (deterministic)")
        fun `deterministic`() {
            val rootKey = ByteArray(32) { 1 }
            val dhOutput = ByteArray(32) { 2 }
            val (r1, c1) = KdfChain.deriveRootAndChainKey(rootKey, dhOutput)
            val (r2, c2) = KdfChain.deriveRootAndChainKey(rootKey, dhOutput)
            assertArrayEquals(r1, r2)
            assertArrayEquals(c1, c2)
        }

        @Test @DisplayName("different DH outputs produce different keys")
        fun `different dh outputs different keys`() {
            val rootKey = ByteArray(32) { 1 }
            val dh1 = ByteArray(32) { 2 }
            val dh2 = ByteArray(32) { 3 }
            val (_, c1) = KdfChain.deriveRootAndChainKey(rootKey, dh1)
            val (_, c2) = KdfChain.deriveRootAndChainKey(rootKey, dh2)
            assertFalse(c1.contentEquals(c2))
        }
    }

    @Nested @DisplayName("Message Key Derivation")
    inner class MessageKeyDerivationTest {
        @Test @DisplayName("derives 32-byte message key seed and 32-byte next chain key")
        fun `derives correct sizes`() {
            val chainKey = ByteArray(32) { 1 }
            val (msgKeySeed, nextChain) = KdfChain.deriveMessageKeyAndNextChain(chainKey)
            assertEquals(32, msgKeySeed.size)
            assertEquals(32, nextChain.size)
        }

        @Test @DisplayName("same chain key produces same outputs")
        fun `deterministic`() {
            val chainKey = ByteArray(32) { 1 }
            val (m1, n1) = KdfChain.deriveMessageKeyAndNextChain(chainKey)
            val (m2, n2) = KdfChain.deriveMessageKeyAndNextChain(chainKey)
            assertArrayEquals(m1, m2)
            assertArrayEquals(n1, n2)
        }

        @Test @DisplayName("message key seed and next chain key are different")
        fun `msg key and next chain are different`() {
            val chainKey = ByteArray(32) { 1 }
            val (msgKeySeed, nextChain) = KdfChain.deriveMessageKeyAndNextChain(chainKey)
            assertFalse(msgKeySeed.contentEquals(nextChain))
        }

        @Test @DisplayName("iterating chain produces unique keys")
        fun `chain iteration produces unique keys`() {
            var chainKey = ByteArray(32) { 1 }
            val keys = mutableListOf<ByteArray>()
            repeat(10) {
                val (msgKeySeed, nextChain) = KdfChain.deriveMessageKeyAndNextChain(chainKey)
                keys.add(msgKeySeed)
                chainKey = nextChain
            }
            for (i in keys.indices) {
                for (j in i + 1 until keys.size) {
                    assertFalse(keys[i].contentEquals(keys[j]))
                }
            }
        }
    }

    @Nested @DisplayName("Message Key and Nonce Derivation")
    inner class MsgKeyNonceTest {
        @Test @DisplayName("derives 32-byte encryption key and 12-byte nonce")
        fun `derives correct sizes`() {
            val msgKeySeed = ByteArray(32) { 1 }
            val (encKey, nonce) = KdfChain.deriveMessageKeyAndNonce(msgKeySeed)
            assertEquals(32, encKey.size)
            assertEquals(12, nonce.size)
        }

        @Test @DisplayName("same seed produces same key and nonce")
        fun `deterministic`() {
            val msgKeySeed = ByteArray(32) { 1 }
            val (e1, n1) = KdfChain.deriveMessageKeyAndNonce(msgKeySeed)
            val (e2, n2) = KdfChain.deriveMessageKeyAndNonce(msgKeySeed)
            assertArrayEquals(e1, e2)
            assertArrayEquals(n1, n2)
        }

        @Test @DisplayName("encryption key and nonce are different")
        fun `key and nonce different`() {
            val msgKeySeed = ByteArray(32) { 1 }
            val (encKey, nonce) = KdfChain.deriveMessageKeyAndNonce(msgKeySeed)
            assertFalse(encKey.contentEquals(nonce))
        }

        @Test @DisplayName("full chain: root→chain→msgKey→encKey+nonce produces unique per-message keys")
        fun `full chain produces unique keys`() {
            val rootKey = ByteArray(32) { 1 }
            val dhOutput = ByteArray(32) { 2 }
            val (_, chainKey) = KdfChain.deriveRootAndChainKey(rootKey, dhOutput)

            var ck = chainKey
            val encKeys = mutableListOf<ByteArray>()
            repeat(5) {
                val (msgKeySeed, nextCk) = KdfChain.deriveMessageKeyAndNextChain(ck)
                val (encKey, _) = KdfChain.deriveMessageKeyAndNonce(msgKeySeed)
                encKeys.add(encKey)
                ck = nextCk
            }
            for (i in encKeys.indices) {
                for (j in i + 1 until encKeys.size) {
                    assertFalse(encKeys[i].contentEquals(encKeys[j]))
                }
            }
        }
    }

    @Nested @DisplayName("Ratchet Key Derivation")
    inner class RatchetKeyTest {
        @Test @DisplayName("deriveRatchetKeys is alias for deriveRootAndChainKey")
        fun `ratchet keys alias`() {
            val rootKey = ByteArray(32) { 1 }
            val dhOutput = ByteArray(32) { 2 }
            val (r1, c1) = KdfChain.deriveRatchetKeys(rootKey, dhOutput)
            val (r2, c2) = KdfChain.deriveRootAndChainKey(rootKey, dhOutput)
            assertArrayEquals(r1, r2)
            assertArrayEquals(c1, c2)
        }
    }
}

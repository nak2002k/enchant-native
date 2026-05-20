package org.enchant.core.crypto

/**
 * Signal-compatible KDF chain for root key → sending/receiving chain keys → message keys.
 *
 * Uses HMAC-SHA256 as the KDF (matching the Signal protocol spec). Each step derives
 * the next key in the chain and zeroes intermediate material.
 *
 * The chain works as follows:
 * - Root key + DH output → new root key + chain key (via HKDF)
 * - Chain key → message key + next chain key (via HMAC-SHA256)
 * - Message key → encryption key + nonce (via HKDF)
 */
object KdfChain {
    private const val HKDF_INFO_ROOT = "WhisperRatchet"
    private const val HKDF_INFO_MSG = "WhisperMessageKey"
    private val HMAC_DERIVE_0 = byteArrayOf(0x01)
    private val HMAC_DERIVE_1 = byteArrayOf(0x02)

    /**
     * Derive a new root key and chain key from the current root key and DH shared secret.
     * This is the "ratchet step" in the Double Ratchet protocol.
     *
     * @param rootKey current root key (32 bytes)
     * @param dhOutput X25519 shared secret (32 bytes)
     * @return Pair(newRootKey, newChainKey) — both 32 bytes
     */
    fun deriveRootAndChainKey(rootKey: ByteArray, dhOutput: ByteArray): Pair<ByteArray, ByteArray> {
        val material = rootKey + dhOutput
        val derived = CryptoPrimitives.hkdfSha256(
            input = material,
            salt = ByteArray(32),
            info = HKDF_INFO_ROOT.encodeToByteArray(),
            length = 64
        )
        val newRootKey = derived.copyOfRange(0, 32)
        val newChainKey = derived.copyOfRange(32, 64)
        CryptoPrimitives.zeroBytes(derived)
        return Pair(newRootKey, newChainKey)
    }

    /**
     * Derive a message key and the next chain key from the current chain key.
     *
     * chain_key → HMAC(chain_key, 0x01) = message_key_seed
     * chain_key → HMAC(chain_key, 0x02) = next_chain_key
     *
     * @param chainKey current chain key (32 bytes)
     * @return Pair(messageKeySeed, nextChainKey) — both 32 bytes
     */
    fun deriveMessageKeyAndNextChain(chainKey: ByteArray): Pair<ByteArray, ByteArray> {
        val msgKeySeed = CryptoPrimitives.hmacSha256(chainKey, HMAC_DERIVE_0)
        val nextChainKey = CryptoPrimitives.hmacSha256(chainKey, HMAC_DERIVE_1)
        return Pair(msgKeySeed, nextChainKey)
    }

    /**
     * Derive encryption key (32 bytes) and nonce (12 bytes) from a message key seed.
     * Uses HKDF-SHA256 with the WhisperMessageKey info string.
     *
     * @param msgKeySeed 32-byte message key seed from deriveMessageKeyAndNextChain
     * @return Pair(encryptionKey(32), nonce(12))
     */
    fun deriveMessageKeyAndNonce(msgKeySeed: ByteArray): Pair<ByteArray, ByteArray> {
        val derived = CryptoPrimitives.hkdfSha256(
            input = msgKeySeed,
            salt = ByteArray(32),
            info = HKDF_INFO_MSG.encodeToByteArray(),
            length = 44
        )
        val encKey = derived.copyOfRange(0, 32)
        val nonce = derived.copyOfRange(32, 44)
        CryptoPrimitives.zeroBytes(derived)
        return Pair(encKey, nonce)
    }

    /**
     * Derive a sending chain key from a root key and ephemeral DH output.
     * This is used when Alice starts a new sending chain after receiving Bob's ratchet key.
     *
     * @param rootKey current root key
     * @param dhOutput DH shared secret from ephemeral keys
     * @return Pair(newRootKey, sendingChainKey)
     */
    fun deriveRatchetKeys(rootKey: ByteArray, dhOutput: ByteArray): Pair<ByteArray, ByteArray> {
        return deriveRootAndChainKey(rootKey, dhOutput)
    }

    private operator fun ByteArray.plus(other: ByteArray): ByteArray {
        val result = ByteArray(this.size + other.size)
        System.arraycopy(this, 0, result, 0, this.size)
        System.arraycopy(other, 0, result, this.size, other.size)
        return result
    }
}

package org.enchant.core.crypto

/**
 * Agent session manager wrapping enchant_agent_* FFI functions.
 *
 * Manages agent identity creation, DH key exchange, and symmetric
 * encryption/decryption for AI agent E2EE sessions.
 *
 * Usage:
 *   val agent = AgentSessionManager.create()
 *   val (pub, priv) = agent.generateIdentity()
 *   val (sharedSecret, ephemeral) = agent.initiateSession(agentPriv, serverPub)
 *   val ciphertext = agent.encrypt(sharedSecret, plaintext)
 *   val plaintext = agent.decrypt(sharedSecret, ciphertext)
 *   agent.close()
 */
class AgentSessionManager private constructor() {

    private var closed = false

    fun close() {
        if (!closed) {
            closed = true
        }
    }

    /**
     * Generate an agent identity keypair (X25519).
     * Returns Pair(publicKey, privateKey), each 32 bytes.
     */
    fun generateIdentity(): Pair<ByteArray, ByteArray> {
        check(!closed) { "AgentSessionManager is closed" }
        val publicKey = ByteArray(EnchantCrypto.X25519_PUBLIC_KEY_SIZE)
        val privateKey = ByteArray(EnchantCrypto.X25519_PRIVATE_KEY_SIZE)
        val rc = EnchantCrypto.enchant_agent_identity_create(publicKey, privateKey)
        if (rc != EnchantCrypto.SUCCESS) {
            throw IllegalStateException("enchant_agent_identity_create failed: $rc")
        }
        return Pair(publicKey, privateKey)
    }

    /**
     * Initiate an agent session (DH key exchange — initiator side).
     *
     * @param agentPrivateKey Our X25519 private key (32 bytes)
     * @param serverPublicKey Peer's X25519 public key (32 bytes)
     * @return Pair(sharedSecret, ephemeralPublic) — shared secret (32 bytes) and our ephemeral public (32 bytes)
     */
    fun initiateSession(
        agentPrivateKey: ByteArray,
        serverPublicKey: ByteArray
    ): Pair<ByteArray, ByteArray> {
        check(!closed) { "AgentSessionManager is closed" }
        require(agentPrivateKey.size == EnchantCrypto.X25519_PRIVATE_KEY_SIZE) {
            "agentPrivateKey must be ${EnchantCrypto.X25519_PRIVATE_KEY_SIZE} bytes, got ${agentPrivateKey.size}"
        }
        require(serverPublicKey.size == EnchantCrypto.X25519_PUBLIC_KEY_SIZE) {
            "serverPublicKey must be ${EnchantCrypto.X25519_PUBLIC_KEY_SIZE} bytes, got ${serverPublicKey.size}"
        }
        val sharedSecret = ByteArray(EnchantCrypto.X25519_PUBLIC_KEY_SIZE)
        val ephemeralPublic = ByteArray(EnchantCrypto.X25519_PUBLIC_KEY_SIZE)
        val rc = EnchantCrypto.enchant_agent_session_initiate(
            agentPrivateKey, serverPublicKey, sharedSecret, ephemeralPublic
        )
        if (rc != EnchantCrypto.SUCCESS) {
            throw IllegalStateException("enchant_agent_session_initiate failed: $rc")
        }
        return Pair(sharedSecret, ephemeralPublic)
    }

    /**
     * Respond to an agent session (DH key exchange — responder side).
     *
     * @param serverPrivateKey Our X25519 private key (32 bytes)
     * @param agentPublicKey Peer's X25519 public key (32 bytes)
     * @param agentEphemeralPublic Peer's ephemeral public from initiateSession (32 bytes)
     * @return sharedSecret (32 bytes)
     */
    fun respondSession(
        serverPrivateKey: ByteArray,
        agentPublicKey: ByteArray,
        agentEphemeralPublic: ByteArray
    ): ByteArray {
        check(!closed) { "AgentSessionManager is closed" }
        require(serverPrivateKey.size == EnchantCrypto.X25519_PRIVATE_KEY_SIZE) {
            "serverPrivateKey must be ${EnchantCrypto.X25519_PRIVATE_KEY_SIZE} bytes, got ${serverPrivateKey.size}"
        }
        require(agentPublicKey.size == EnchantCrypto.X25519_PUBLIC_KEY_SIZE) {
            "agentPublicKey must be ${EnchantCrypto.X25519_PUBLIC_KEY_SIZE} bytes, got ${agentPublicKey.size}"
        }
        require(agentEphemeralPublic.size == EnchantCrypto.X25519_PUBLIC_KEY_SIZE) {
            "agentEphemeralPublic must be ${EnchantCrypto.X25519_PUBLIC_KEY_SIZE} bytes, got ${agentEphemeralPublic.size}"
        }
        val sharedSecret = ByteArray(EnchantCrypto.X25519_PUBLIC_KEY_SIZE)
        val rc = EnchantCrypto.enchant_agent_session_respond(
            serverPrivateKey, agentPublicKey, agentEphemeralPublic, sharedSecret
        )
        if (rc != EnchantCrypto.SUCCESS) {
            throw IllegalStateException("enchant_agent_session_respond failed: $rc")
        }
        return sharedSecret
    }

    /**
     * Encrypt plaintext with the shared secret using XChaCha20-Poly1305.
     * Output: 24-byte nonce + ciphertext + 16-byte tag.
     *
     * @param sharedSecret 32-byte shared secret from session exchange
     * @param plaintext Data to encrypt
     * @return Ciphertext with prepended nonce
     */
    fun encrypt(sharedSecret: ByteArray, plaintext: ByteArray): ByteArray {
        check(!closed) { "AgentSessionManager is closed" }
        require(sharedSecret.size == EnchantCrypto.X25519_PUBLIC_KEY_SIZE) {
            "sharedSecret must be ${EnchantCrypto.X25519_PUBLIC_KEY_SIZE} bytes, got ${sharedSecret.size}"
        }
        val ciphertextSize = plaintext.size + EnchantCrypto.XCHACHA20_NONCE_SIZE + EnchantCrypto.XCHACHA20_TAG_SIZE
        val ciphertext = ByteArray(ciphertextSize)
        val rc = EnchantCrypto.enchant_agent_encrypt(
            sharedSecret, sharedSecret.size.toLong(),
            plaintext, plaintext.size.toLong(),
            ciphertext, ciphertextSize.toLong()
        )
        if (rc != EnchantCrypto.SUCCESS) {
            throw IllegalStateException("enchant_agent_encrypt failed: $rc")
        }
        return ciphertext
    }

    /**
     * Decrypt ciphertext with the shared secret.
     *
     * @param sharedSecret 32-byte shared secret from session exchange
     * @param ciphertext Encrypted data (nonce prepended: 24-byte nonce + ciphertext + 16-byte tag)
     * @return Decrypted plaintext
     */
    fun decrypt(sharedSecret: ByteArray, ciphertext: ByteArray): ByteArray {
        check(!closed) { "AgentSessionManager is closed" }
        require(sharedSecret.size == EnchantCrypto.X25519_PUBLIC_KEY_SIZE) {
            "sharedSecret must be ${EnchantCrypto.X25519_PUBLIC_KEY_SIZE} bytes, got ${sharedSecret.size}"
        }
        val plaintextSize = ciphertext.size - EnchantCrypto.XCHACHA20_NONCE_SIZE - EnchantCrypto.XCHACHA20_TAG_SIZE
        val plaintext = ByteArray(plaintextSize)
        val rc = EnchantCrypto.enchant_agent_decrypt(
            sharedSecret, sharedSecret.size.toLong(),
            ciphertext, ciphertext.size.toLong(),
            plaintext, plaintextSize.toLong()
        )
        if (rc != EnchantCrypto.SUCCESS) {
            throw IllegalStateException("enchant_agent_decrypt failed: $rc")
        }
        return plaintext
    }

    companion object {
        /**
         * Create a new AgentSessionManager instance.
         */
        fun create(): AgentSessionManager {
            return AgentSessionManager()
        }
    }
}

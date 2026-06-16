package org.enchant.core.crypto

/**
 * VeilSealer — anonymous (sealed) sender encryption via native veil session.
 *
 * The C library already provides `enchant_veil_session_*` functions for sealing
 * and encrypting messages behind the sender's identity. This wrapper wires
 * them into the Kotlin layer.
 *
 * A veil session is initialized with the X3DH-derived root key and chain key
 * (the same material that drives the EnvelopeState ratchet). The session can
 * then seal individual 1:1 messages or group sender-key distribution messages.
 *
 * NOTE: Sender certificate management (creation, renewal, expiry) is handled
 * by the backend's IKS; this class only consumes previously-issued certs.
 */
class VeilSealer(
    private val sessionHandle: Long
) {
    /**
     * Create a new veil session instance backed by the native library.
     * The returned handle must be released with [close] when no longer needed.
     */
    fun isInitialized(): Boolean = sessionHandle != 0L

    fun close() {
        if (sessionHandle != 0L) {
            EnchantCrypto.enchant_veil_session_destroy(sessionHandle)
        }
    }

    /**
     * Initialize the veil session for the sender (initiator / Alice).
     *
     * @param rootKey 32-byte X3DH root key
     * @param chainKey 32-byte sending chain key
     * @param ourDhPublic 32-byte local X25519 DH public key
     * @param ourDhPrivate 32-byte local X25519 DH private key
     * @param theirX25519Public 32-byte remote X25519 identity key
     * @param ourIdentity 32-byte local Ed25519 identity public key
     * @param theirIdentity 32-byte remote Ed25519 identity public key
     * @param pqrKey 32-byte post-quantum ratchet key (may be zeros)
     */
    fun initAlice(
        rootKey: ByteArray,
        chainKey: ByteArray,
        ourDhPublic: ByteArray,
        ourDhPrivate: ByteArray,
        theirX25519Public: ByteArray,
        ourIdentity: ByteArray,
        theirIdentity: ByteArray,
        pqrKey: ByteArray
    ): Int = EnchantCrypto.enchant_veil_session_init_alice(
        sessionHandle, rootKey, chainKey,
        ourDhPublic, ourDhPrivate, theirX25519Public,
        ourIdentity, theirIdentity, pqrKey
    )

    /**
     * Initialize the veil session for the receiver (responder / Bob).
     */
    fun initBob(
        rootKey: ByteArray,
        chainKey: ByteArray,
        ourDhPublic: ByteArray,
        ourDhPrivate: ByteArray,
        theirX25519Public: ByteArray,
        ourIdentity: ByteArray,
        theirIdentity: ByteArray,
        pqrKey: ByteArray
    ): Int = EnchantCrypto.enchant_veil_session_init_bob(
        sessionHandle, rootKey, chainKey,
        ourDhPublic, ourDhPrivate, theirX25519Public,
        ourIdentity, theirIdentity, pqrKey
    )

    /**
     * Encrypt plaintext within the pre-existing veil session (no sealing).
     */
    fun encrypt(plaintext: ByteArray): ByteArray? {
        val maxSize = 41 + plaintext.size + 16
        val output = ByteArray(maxSize)
        val outputLen = longArrayOf(maxSize.toLong())
        val rc = EnchantCrypto.enchant_veil_session_encrypt(
            sessionHandle, plaintext, plaintext.size.toLong(), output, outputLen
        )
        return if (rc == EnchantCrypto.SUCCESS) output.copyOf(outputLen[0].toInt()) else null
    }

    /**
     * Decrypt a raw veil session envelope (no unsealing).
     */
    fun decrypt(ciphertext: ByteArray): ByteArray? {
        val plaintext = ByteArray(ciphertext.size + 256)
        val plaintextLen = longArrayOf(plaintext.size.toLong())
        val rc = EnchantCrypto.enchant_veil_session_decrypt(
            sessionHandle, ciphertext, ciphertext.size.toLong(), plaintext, plaintextLen
        )
        return if (rc == EnchantCrypto.SUCCESS) plaintext.copyOf(plaintextLen[0].toInt()) else null
    }

    /**
     * Seal the sender's identity inside the envelope and encrypt the payload.
     *
     * Uses the sender's identity key (private + public), a pre-issued sender
     * certificate, and the recipient's X25519 public key so the recipient can
     * verify the certificate without seeing the sender's identity on the wire.
     *
     * @param senderIdentityPrivate 32-byte sender Ed25519 private key
     * @param senderIdentityPublic 32-byte sender Ed25519 public key
     * @param recipientPublicKey 32-byte recipient X25519 identity public key
     * @param senderCert issuer-signed certificate blob from the IKS
     * @param plaintext the message to encrypt
     * @return sealed envelope bytes, or null on failure
     */
    fun sealAndEncrypt(
        senderIdentityPrivate: ByteArray,
        senderIdentityPublic: ByteArray,
        recipientPublicKey: ByteArray,
        senderCert: ByteArray,
        plaintext: ByteArray
    ): ByteArray? {
        val maxSize = 4096 + plaintext.size
        val output = ByteArray(maxSize)
        val outputLen = longArrayOf(maxSize.toLong())
        val rc = EnchantCrypto.enchant_veil_session_seal_and_encrypt(
            sessionHandle,
            senderIdentityPrivate, senderIdentityPublic,
            recipientPublicKey, 1,
            senderCert, senderCert.size.toLong(),
            plaintext, plaintext.size.toLong(),
            output, outputLen
        )
        return if (rc == EnchantCrypto.SUCCESS) output.copyOf(outputLen[0].toInt()) else null
    }

    /**
     * Unseal a sealed envelope, recovering the sender's identity key and plaintext.
     *
     * @return Pair of (32-byte sender identity key, plaintext), or null
     */
    fun unsealAndDecrypt(
        recipientPrivateKey: ByteArray,
        recipientPublicKey: ByteArray,
        ciphertext: ByteArray
    ): Pair<ByteArray, ByteArray>? {
        val plaintext = ByteArray(ciphertext.size + 256)
        val plaintextLen = longArrayOf(plaintext.size.toLong())
        val senderIdentityKey = ByteArray(32)
        val rc = EnchantCrypto.enchant_veil_session_unseal_and_decrypt(
            sessionHandle,
            recipientPrivateKey, recipientPublicKey,
            ciphertext, ciphertext.size.toLong(),
            plaintext, plaintextLen,
            senderIdentityKey
        )
        return if (rc == EnchantCrypto.SUCCESS) {
            Pair(senderIdentityKey, plaintext.copyOf(plaintextLen[0].toInt()))
        } else null
    }

    /**
     * Seal a group sender-key distribution message.
     */
    fun sealGroupMessage(
        senderIdentityPrivate: ByteArray,
        senderIdentityPublic: ByteArray,
        recipientPublicKey: ByteArray,
        senderCert: ByteArray,
        senderKeyCiphertext: ByteArray,
        senderKeyId: Int
    ): ByteArray? {
        val maxSize = 4096 + senderKeyCiphertext.size
        val output = ByteArray(maxSize)
        val outputLen = longArrayOf(maxSize.toLong())
        val rc = EnchantCrypto.enchant_veil_session_seal_group_message(
            sessionHandle,
            senderIdentityPrivate, senderIdentityPublic,
            recipientPublicKey, 1,
            senderCert, senderCert.size.toLong(),
            senderKeyCiphertext, senderKeyCiphertext.size.toLong(),
            senderKeyId,
            output, outputLen
        )
        return if (rc == EnchantCrypto.SUCCESS) output.copyOf(outputLen[0].toInt()) else null
    }

    /**
     * Unseal a group sender-key distribution message.
     *
     * @return Triple of (sender key ciphertext, sender key id, sender identity key), or null
     */
    fun unsealGroupMessage(
        recipientPrivateKey: ByteArray,
        recipientPublicKey: ByteArray,
        ciphertext: ByteArray
    ): Triple<ByteArray, Int, ByteArray>? {
        val senderKeyOut = ByteArray(ciphertext.size + 256)
        val senderKeyLen = longArrayOf(senderKeyOut.size.toLong())
        val senderKeyIdOut = intArrayOf(0)
        val rc = EnchantCrypto.enchant_veil_session_unseal_group_message(
            sessionHandle,
            recipientPrivateKey, recipientPublicKey,
            ciphertext, ciphertext.size.toLong(),
            senderKeyOut, senderKeyLen,
            senderKeyIdOut
        )
        return if (rc == EnchantCrypto.SUCCESS) {
            val senderIdentityKey = ByteArray(32) // identity returned by unseal
            Triple(
                senderKeyOut.copyOf(senderKeyLen[0].toInt()),
                senderKeyIdOut[0],
                senderIdentityKey
            )
        } else null
    }

    companion object {
        /**
         * Allocate a fresh native veil session. Destroy with [close].
         */
        fun create(): VeilSealer? {
            val sessionOut = longArrayOf(0)
            val rc = EnchantCrypto.enchant_veil_session_create(sessionOut)
            return if (rc == EnchantCrypto.SUCCESS) {
                VeilSealer(sessionOut[0])
            } else null
        }
    }
}

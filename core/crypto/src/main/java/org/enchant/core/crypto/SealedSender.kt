package org.enchant.core.crypto

/**
 * Sealed Sender (anonymous sender) encryption.
 *
 * Implements the sealed sender protocol: the sender's identity is encrypted
 * inside the message payload using a key derived from the recipient's profile key.
 * The server sees only an opaque blob and the recipient — it cannot determine who
 * sent the message.
 *
 * Flow:
 * 1. Sender derives an access key from the recipient's profile key
 * 2. Sender encrypts their identity + message with the access key
 * 3. Server relays the sealed envelope without knowing the sender
 * 4. Recipient decrypts using their profile key to discover who sent it
 *
 * NOTE: Profile key derivation and certificate management are expected to be
 * handled by the :core:auth or :core:profile module.
 */
object SealedSender {

    private const val SEALED_SENDER_INFO = "SealedSender"

    /**
     * Derive an unidentified access key from a profile key.
     *
     * The access key is used to encrypt the sender's identity inside the sealed
     * envelope. This is AES-256-GCM encryption of 32 zero bytes with the profile key.
     *
     * @param profileKey the recipient's 32-byte profile key
     * @param senderIdentityKey the sender's 32-byte identity public key
     * @return 16-byte access key
     */
    fun deriveAccessKey(profileKey: ByteArray, senderIdentityKey: ByteArray): ByteArray {
        require(profileKey.size == 32) { "Profile key must be 32 bytes" }
        require(senderIdentityKey.size == 32) { "Identity key must be 32 bytes" }

        val nonce = CryptoPrimitives.sha256(senderIdentityKey).copyOfRange(0, 12)
        val encrypted = CryptoPrimitives.encryptAesGcmRaw(
            plaintext = ByteArray(32),
            key = profileKey,
            nonce = nonce
        )
        return encrypted.copyOfRange(0, 16)
    }

    /**
     * Encrypt a message for sealed sender delivery.
     *
     * The result contains the sender's identity encrypted with the access key,
     * followed by the message encrypted with the same key.
     *
     * @param accessKey derived access key (16 bytes, padded to 32)
     * @param senderIdentityKey sender's identity public key
     * @param message the encrypted message payload
     * @return sealed sender payload
     */
    fun encryptSealed(
        accessKey: ByteArray,
        senderIdentityKey: ByteArray,
        message: ByteArray
    ): ByteArray {
        require(accessKey.size == 16) { "Access key must be 16 bytes" }

        val fullKey = ByteArray(32).apply {
            accessKey.copyInto(this)
            CryptoPrimitives.sha256(accessKey).copyInto(this, 16, 0, 16)
        }

        val nonce = CryptoPrimitives.generateRandomKey(CryptoPrimitives.AES_GCM_NONCE_SIZE)
        val identityCiphertext = CryptoPrimitives.encryptAesGcmRaw(
            plaintext = senderIdentityKey,
            key = fullKey,
            nonce = nonce
        )

        val msgNonce = CryptoPrimitives.generateRandomKey(CryptoPrimitives.XCHACHA20_NONCE_SIZE)
        val msgCiphertext = CryptoPrimitives.encryptXChaCha20Poly1305Raw(
            plaintext = message,
            key = fullKey,
            nonce = msgNonce
        )

        CryptoPrimitives.zeroBytes(fullKey)

        return ByteArray(
            nonce.size + identityCiphertext.size + msgNonce.size + msgCiphertext.size
        ).apply {
            var offset = 0
            nonce.copyInto(this, offset); offset += nonce.size
            identityCiphertext.copyInto(this, offset); offset += identityCiphertext.size
            msgNonce.copyInto(this, offset); offset += msgNonce.size
            msgCiphertext.copyInto(this, offset)
        }
    }

    /**
     * Decrypt a sealed sender message.
     *
     * @param accessKey derived access key (16 bytes)
     * @param sealedPayload the sealed sender payload
     * @return Pair(senderIdentityKey, message) or null on failure
     */
    fun decryptSealed(
        accessKey: ByteArray,
        sealedPayload: ByteArray
    ): Pair<ByteArray, ByteArray>? {
        require(accessKey.size == 16) { "Access key must be 16 bytes" }

        val fullKey = ByteArray(32).apply {
            accessKey.copyInto(this)
            CryptoPrimitives.sha256(accessKey).copyInto(this, 16)
        }

        return try {
            var offset = 0
            val nonce = sealedPayload.copyOfRange(offset, offset + CryptoPrimitives.AES_GCM_NONCE_SIZE)
            offset += CryptoPrimitives.AES_GCM_NONCE_SIZE

            val identityCiphertext = sealedPayload.copyOfRange(offset, offset + 32 + CryptoPrimitives.AES_GCM_TAG_SIZE)
            offset += identityCiphertext.size

            val senderIdentityKey = CryptoPrimitives.decryptAesGcmRaw(
                ciphertext = identityCiphertext,
                key = fullKey,
                nonce = nonce
            )

            val msgNonce = sealedPayload.copyOfRange(offset, offset + CryptoPrimitives.XCHACHA20_NONCE_SIZE)
            offset += CryptoPrimitives.XCHACHA20_NONCE_SIZE

            val msgCiphertext = sealedPayload.copyOfRange(offset, sealedPayload.size)

            val message = CryptoPrimitives.decryptXChaCha20Poly1305Raw(
                ciphertext = msgCiphertext,
                key = fullKey,
                nonce = msgNonce
            )

            CryptoPrimitives.zeroBytes(fullKey)
            Pair(senderIdentityKey, message)
        } catch (e: Exception) {
            CryptoPrimitives.zeroBytes(fullKey)
            null
        }
    }

    /**
     * Encrypt a message with a profile key for profile data.
     *
     * Used for encrypting profile fields (name, about, avatar) before uploading
     * to the server.
     *
     * @param profileKey 32-byte profile key
     * @param data data to encrypt
     * @return encrypted data [nonce(12) | ciphertext | tag(16)]
     */
    fun encryptProfileData(profileKey: ByteArray, data: ByteArray): ByteArray {
        require(profileKey.size == 32) { "Profile key must be 32 bytes" }
        return CryptoPrimitives.encryptAesGcm(data, profileKey)
    }

    /**
     * Decrypt profile data encrypted with a profile key.
     *
     * @param profileKey 32-byte profile key
     * @param encryptedData encrypted data
     * @return decrypted data, or null on failure
     */
    fun decryptProfileData(profileKey: ByteArray, encryptedData: ByteArray): ByteArray? {
        require(profileKey.size == 32) { "Profile key must be 32 bytes" }
        return try {
            CryptoPrimitives.decryptAesGcm(encryptedData, profileKey)
        } catch (e: Exception) {
            null
        }
    }
}

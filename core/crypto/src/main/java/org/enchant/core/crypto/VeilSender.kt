package org.enchant.core.crypto

/**
 * Veil (anonymous sender) encryption.
 *
 * Replaces the previous Kotlin-only sealed sender implementation with native
 * libenchantcrypto FFI calls. All key derivation, DH key agreement, and
 * authenticated encryption run in the C++ library.
 *
 * Flow:
 * 1. Sender uses the recipient's public key and their own identity key pair
 *    to veil-encrypt a message. The resulting payload hides the sender's
 *    identity from the relay server.
 * 2. Server relays the opaque blob.
 * 3. Recipient uses their private key to decrypt and recover the sender's
 *    identity public key.
 *
 * Profile data encryption uses native AES-256-GCM directly.
 */
object VeilSender {

    private const val VEIL_ACCESS_KEY_INFO = "EnchantVeilSenderAccessKey"

    /**
     * Derive an unidentified access key from a profile key and sender identity.
     *
     * Uses HKDF-SHA256 in the native library. The result can be used by the
     * server to authorize anonymous delivery without learning the sender.
     *
     * @param profileKey the recipient's 32-byte profile key
     * @param senderIdentityKey the sender's 32-byte identity public key
     * @return 16-byte access key
     */
    fun deriveAccessKey(profileKey: ByteArray, senderIdentityKey: ByteArray): ByteArray {
        require(profileKey.size == 32) { "Profile key must be 32 bytes" }
        require(senderIdentityKey.size == 32) { "Identity key must be 32 bytes" }

        val okm = ByteArray(16)
        val info = VEIL_ACCESS_KEY_INFO.toByteArray(Charsets.UTF_8)
        val rc = EnchantCrypto.enchant_hkdf_sha256(
            senderIdentityKey, senderIdentityKey.size.toLong(),
            profileKey, profileKey.size.toLong(),
            info, info.size.toLong(),
            okm, okm.size.toLong()
        )
        if (rc != EnchantCrypto.SUCCESS) {
            throw IllegalStateException("deriveAccessKey failed: $rc")
        }
        return okm
    }

    /**
     * Veil-encrypt a message for a recipient.
     *
     * The sender's identity is hidden inside the ciphertext using ephemeral and
     * static X25519 DH. The server sees only an opaque blob.
     *
     * @param recipientPublicKey recipient's 32-byte X25519 public key
     * @param senderIdentityPrivate sender's 32-byte X25519 private key
     * @param senderIdentityPublic sender's 32-byte X25519 public key
     * @param message plaintext message
     * @return opaque veil ciphertext
     */
    fun encryptVeiled(
        recipientPublicKey: ByteArray,
        senderIdentityPrivate: ByteArray,
        senderIdentityPublic: ByteArray,
        message: ByteArray
    ): ByteArray {
        require(recipientPublicKey.size == 32) { "Recipient public key must be 32 bytes" }
        require(senderIdentityPrivate.size == 32) { "Sender private key must be 32 bytes" }
        require(senderIdentityPublic.size == 32) { "Sender public key must be 32 bytes" }

        val output = ByteArray(veilV1CiphertextSize(message.size))
        val outputLen = longArrayOf(output.size.toLong())
        val rc = EnchantCrypto.enchant_veil_encrypt_v1(
            recipientPublicKey,
            senderIdentityPrivate,
            senderIdentityPublic,
            message, message.size.toLong(),
            output, outputLen
        )
        if (rc != EnchantCrypto.SUCCESS) {
            throw IllegalStateException("enchant_veil_encrypt_v1 failed: $rc")
        }
        return output.copyOf(outputLen[0].toInt())
    }

    /**
     * SP6: veil-encrypt a message using a Signal-style UnidentifiedSenderMessage
     * (USMC). The sender's certificate (user id + device + identity key) rides
     * INSIDE the encrypted payload so only the recipient can read who sent it —
     * the relay server stays blind.
     *
     * @param recipientPublicKey recipient's 32-byte X25519 public key
     * @param senderIdentityPrivate sender's 32-byte X25519 private key
     * @param senderIdentityPublic sender's 32-byte X25519 public key
     * @param senderCertificate the sender certificate (canonical protobuf bytes)
     * @param message plaintext message
     * @return opaque veil v2 ciphertext
     */
    fun encryptVeiledV2(
        recipientPublicKey: ByteArray,
        senderIdentityPrivate: ByteArray,
        senderIdentityPublic: ByteArray,
        senderCertificate: ByteArray,
        message: ByteArray
    ): ByteArray {
        require(recipientPublicKey.size == 32) { "Recipient public key must be 32 bytes" }
        require(senderIdentityPrivate.size == 32) { "Sender private key must be 32 bytes" }
        require(senderIdentityPublic.size == 32) { "Sender public key must be 32 bytes" }
        require(senderCertificate.isNotEmpty()) { "Sender certificate required" }

        // Wrap content in a USMC with the sender certificate.
        val usmc = ByteArray(4096)
        val usmcLen = longArrayOf(usmc.size.toLong())
        var rc = EnchantCrypto.enchant_usmc_create(
            2, // MESSAGE
            senderCertificate, senderCertificate.size.toLong(),
            message, message.size.toLong(),
            2, // IMPLICIT content hint
            ByteArray(0), 0L,
            usmc, usmcLen
        )
        if (rc != EnchantCrypto.SUCCESS) {
            throw IllegalStateException("enchant_usmc_create failed: $rc")
        }
        val usmcBytes = usmc.copyOf(usmcLen[0].toInt())

        // Veil v2 encrypt to the recipient.
        val output = ByteArray(4096 + message.size * 2)
        val outputLen = longArrayOf(output.size.toLong())
        rc = EnchantCrypto.enchant_veil_encrypt_v2(
            senderIdentityPrivate, senderIdentityPublic,
            recipientPublicKey, 1L,
            usmcBytes, usmcBytes.size.toLong(),
            output, outputLen
        )
        if (rc != EnchantCrypto.SUCCESS) {
            throw IllegalStateException("enchant_veil_encrypt_v2 failed: $rc")
        }
        return output.copyOf(outputLen[0].toInt())
    }

    /**
     * Decrypt a veil ciphertext and recover the sender's identity key.
     *
     * @param recipientPrivateKey recipient's 32-byte X25519 private key
     * @param recipientPublicKey recipient's 32-byte X25519 public key
     * @param sealedPayload veil ciphertext from [encryptVeiled]
     * @return Pair(senderIdentityKey, plaintext) or null on failure
     */
    fun decryptVeiled(
        recipientPrivateKey: ByteArray,
        recipientPublicKey: ByteArray,
        sealedPayload: ByteArray
    ): Pair<ByteArray, ByteArray>? {
        require(recipientPrivateKey.size == 32) { "Recipient private key must be 32 bytes" }
        require(recipientPublicKey.size == 32) { "Recipient public key must be 32 bytes" }

        val plaintext = ByteArray(sealedPayload.size)
        val senderIdentityKeyOut = ByteArray(32)
        val plaintextLen = longArrayOf(plaintext.size.toLong())
        val rc = EnchantCrypto.enchant_veil_decrypt_v1(
            recipientPrivateKey,
            recipientPublicKey,
            sealedPayload, sealedPayload.size.toLong(),
            plaintext, plaintextLen,
            senderIdentityKeyOut
        )
        if (rc != EnchantCrypto.SUCCESS) {
            return null
        }
        return Pair(senderIdentityKeyOut, plaintext.copyOf(plaintextLen[0].toInt()))
    }

    /**
     * SP6: decrypt a veil v2 (UnidentifiedSenderMessage) payload and recover
     * the sender's user id + device id from the embedded sender certificate, so
     * the recipient can attribute the message with NO server-provided hint.
     *
     * @return (senderIdentityKey, senderUserId, senderDeviceId, plaintext) or null
     */
    fun decryptVeiledV2(
        recipientPrivateKey: ByteArray,
        recipientPublicKey: ByteArray,
        sealedPayload: ByteArray
    ): Quad<ByteArray, String, Int, ByteArray>? {
        require(recipientPrivateKey.size == 32) { "Recipient private key must be 32 bytes" }
        require(recipientPublicKey.size == 32) { "Recipient public key must be 32 bytes" }

        val plaintext = ByteArray(sealedPayload.size)
        val plaintextLen = longArrayOf(plaintext.size.toLong())
        val senderIdentityKeyOut = ByteArray(32)
        val senderUuidOut = ByteArray(64)
        val senderUuidLen = longArrayOf(senderUuidOut.size.toLong())
        val senderDeviceIdOut = intArrayOf(0)
        val rc = EnchantCrypto.enchant_veil_decrypt_v2_recipient(
            recipientPrivateKey, recipientPublicKey,
            sealedPayload, sealedPayload.size.toLong(),
            plaintext, plaintextLen,
            senderIdentityKeyOut,
            senderUuidOut, senderUuidLen,
            senderDeviceIdOut
        )
        if (rc != EnchantCrypto.SUCCESS) {
            return null
        }
        val uuid = String(senderUuidOut.copyOf(senderUuidLen[0].toInt()), Charsets.UTF_8)
        return Quad(
            senderIdentityKeyOut,
            uuid,
            senderDeviceIdOut[0],
            plaintext.copyOf(plaintextLen[0].toInt())
        )
    }

    data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    /**
     * Encrypt profile data with a profile key.
     *
     * Used for encrypting profile fields (name, about, avatar) before uploading
     * to the server. Uses native AES-256-GCM.
     *
     * @param profileKey 32-byte profile key
     * @param data data to encrypt
     * @return encrypted data [nonce(12) | ciphertext | tag(16)]
     */
    fun encryptProfileData(profileKey: ByteArray, data: ByteArray): ByteArray {
        require(profileKey.size == 32) { "Profile key must be 32 bytes" }

        val nonce = CryptoPrimitives.generateRandomKey(EnchantCrypto.AES_GCM_NONCE_SIZE)
        val ciphertext = ByteArray(data.size + EnchantCrypto.AES_GCM_TAG_SIZE)
        val ciphertextLen = LongArray(1)
        val rc = EnchantCrypto.enchant_aes_256_gcm_encrypt(
            profileKey, nonce,
            data, data.size.toLong(),
            ByteArray(0), 0L,
            ciphertext, ciphertextLen
        )
        if (rc != EnchantCrypto.SUCCESS) {
            throw IllegalStateException("encryptProfileData failed: $rc")
        }

        return ByteArray(nonce.size + ciphertextLen[0].toInt()).apply {
            nonce.copyInto(this)
            ciphertext.copyInto(this, nonce.size, 0, ciphertextLen[0].toInt())
        }
    }

    /**
     * Decrypt profile data encrypted with a profile key.
     *
     * @param profileKey 32-byte profile key
     * @param encryptedData encrypted data [nonce(12) | ciphertext | tag(16)]
     * @return decrypted data, or null on failure
     */
    fun decryptProfileData(profileKey: ByteArray, encryptedData: ByteArray): ByteArray? {
        require(profileKey.size == 32) { "Profile key must be 32 bytes" }
        if (encryptedData.size < EnchantCrypto.AES_GCM_NONCE_SIZE + EnchantCrypto.AES_GCM_TAG_SIZE) {
            return null
        }

        val nonce = encryptedData.copyOfRange(0, EnchantCrypto.AES_GCM_NONCE_SIZE)
        val ciphertext = encryptedData.copyOfRange(
            EnchantCrypto.AES_GCM_NONCE_SIZE, encryptedData.size
        )
        val plaintext = ByteArray(ciphertext.size)
        val plaintextLen = LongArray(1)

        return try {
            val rc = EnchantCrypto.enchant_aes_256_gcm_decrypt(
                profileKey, nonce,
                ciphertext, ciphertext.size.toLong(),
                ByteArray(0), 0L,
                plaintext, plaintextLen
            )
            if (rc != EnchantCrypto.SUCCESS) null
            else plaintext.copyOf(plaintextLen[0].toInt())
        } catch (_: Exception) {
            null
        }
    }

    private fun veilV1CiphertextSize(plaintextLen: Int): Int {
        // Header: eph_pub(32) + enc_sender(48) + enc_sender_mac(16) + enc_sender_nonce(24) + msg_nonce(24) + msg_mac(16) = 160
        // Message: plaintext + 16-byte Poly1305 tag
        return 160 + plaintextLen + EnchantCrypto.XCHACHA20_TAG_SIZE
    }
}

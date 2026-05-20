package org.enchant.core.crypto

/**
 * X3DH (Extended Triple Diffie-Hellman) key agreement.
 *
 * Implements the Signal protocol's X3DH for establishing a shared secret between
 * two parties who have never communicated before. Uses Ed25519 identity keys
 * (converted to X25519 for DH) and X25519 signed/one-time prekeys.
 *
 * Algorithm:
 *   DH1 = DH(IK_A_private, SPK_B_public)
 *   DH2 = DH(EK_private, IK_B_public)
 *   DH3 = DH(EK_private, SPK_B_public)
 *   DH4 = DH(EK_private, OPK_B_public)  // optional
 *   SK = KDF(DH1 || DH2 || DH3 || DH4)
 *
 * The shared secret is then used to initialize the Double Ratchet.
 */
object X3DH {

    private const val X3DH_INFO = "X3DH"
    private const val ROOT_CHAIN_INFO = "WhisperRatchet"

    data class X3dhResult(
        val sharedSecret: ByteArray,
        val rootKey: ByteArray,
        val sendingChainKey: ByteArray,
        val receivingChainKey: ByteArray,
        val header: X3dhHeader
    ) {
        /** Zero all secret material. */
        fun zero() {
            CryptoPrimitives.zeroBytes(sharedSecret)
            CryptoPrimitives.zeroBytes(rootKey)
            CryptoPrimitives.zeroBytes(sendingChainKey)
            CryptoPrimitives.zeroBytes(receivingChainKey)
        }
    }

    data class X3dhHeader(
        val identityKey: ByteArray,
        val ephemeralKey: ByteArray,
        val signedPrekeyId: Int,
        val oneTimePrekeyId: Int? = null
    )

    /**
     * Alice's side of X3DH: initiate a session with Bob.
     *
     * @param ourIdentityKey Ed25519 key pair (will be converted to X25519 internally)
     * @param ourEphemeralKey X25519 ephemeral key pair
     * @param theirIdentityKeyPublic Bob's Ed25519 identity public key
     * @param theirSignedPrekeyPublic Bob's X25519 signed prekey public key
     * @param theirOneTimePrekeyPublic Bob's X25519 one-time prekey (optional)
     * @param theirSignedPrekeyId ID of Bob's signed prekey
     * @param theirOneTimePrekeyId ID of Bob's one-time prekey (if used)
     * @return X3DH result with shared secret and chain keys
     */
    suspend fun aliceInitiate(
        ourIdentityKey: CryptoPrimitives.KeyPair,
        ourEphemeralKey: CryptoPrimitives.KeyPair,
        theirIdentityKeyPublic: ByteArray,
        theirSignedPrekeyPublic: ByteArray,
        theirOneTimePrekeyPublic: ByteArray? = null,
        theirSignedPrekeyId: Int = 0,
        theirOneTimePrekeyId: Int? = null
    ): X3dhResult {
        val ikPrivX = CryptoPrimitives.ed25519SkToX25519(ourIdentityKey.privateKey)

        val dh1 = CryptoPrimitives.x25519DiffieHellman(ikPrivX, theirSignedPrekeyPublic)
        val dh2 = CryptoPrimitives.x25519DiffieHellman(ourEphemeralKey.privateKey, theirIdentityKeyPublic)
        val dh3 = CryptoPrimitives.x25519DiffieHellman(ourEphemeralKey.privateKey, theirSignedPrekeyPublic)

        val dh4 = if (theirOneTimePrekeyPublic != null) {
            CryptoPrimitives.x25519DiffieHellman(ourEphemeralKey.privateKey, theirOneTimePrekeyPublic)
        } else null

        val dhInput = concatDhOutputs(dh1, dh2, dh3, dh4)
        val sk = CryptoPrimitives.hkdfSha256(
            input = dhInput,
            salt = ByteArray(32),
            info = X3DH_INFO.encodeToByteArray(),
            length = 32
        )

        val rootMaterial = CryptoPrimitives.hkdfSha256(
            input = sk,
            salt = ByteArray(32),
            info = ROOT_CHAIN_INFO.encodeToByteArray(),
            length = 64
        )
        val rootKey = rootMaterial.copyOfRange(0, 32)
        val chainKey = rootMaterial.copyOfRange(32, 64)
        CryptoPrimitives.zeroBytes(rootMaterial)

        zeroAll(dh1, dh2, dh3, dh4, dhInput, ikPrivX, sk)

        return X3dhResult(
            sharedSecret = sk,
            rootKey = rootKey,
            sendingChainKey = chainKey,
            receivingChainKey = chainKey,
            header = X3dhHeader(
                identityKey = ourIdentityKey.publicKey,
                ephemeralKey = ourEphemeralKey.publicKey,
                signedPrekeyId = theirSignedPrekeyId,
                oneTimePrekeyId = theirOneTimePrekeyId
            )
        )
    }

    /**
     * Bob's side of X3DH: respond to Alice's pre-key message.
     *
     * @param ourIdentityKey Ed25519 key pair
     * @param ourSignedPrekeyKeyPair X25519 signed prekey key pair
     * @param ourOneTimePrekeyKeyPair X25519 one-time prekey (optional, consumed if used)
     * @param theirIdentityKeyPublic Alice's Ed25519 identity public key
     * @param theirEphemeralKeyPublic Alice's X25519 ephemeral public key
     * @param ourSignedPrekeyId ID of our signed prekey
     * @param ourOneTimePrekeyId ID of our one-time prekey (if used)
     * @return X3DH result with shared secret and chain keys
     */
    suspend fun bobRespond(
        ourIdentityKey: CryptoPrimitives.KeyPair,
        ourSignedPrekeyKeyPair: CryptoPrimitives.KeyPair,
        ourOneTimePrekeyKeyPair: CryptoPrimitives.KeyPair? = null,
        theirIdentityKeyPublic: ByteArray,
        theirEphemeralKeyPublic: ByteArray,
        ourSignedPrekeyId: Int = 0,
        ourOneTimePrekeyId: Int? = null
    ): X3dhResult {
        val ikPrivX = CryptoPrimitives.ed25519SkToX25519(ourIdentityKey.privateKey)

        val dh1 = CryptoPrimitives.x25519DiffieHellman(ourSignedPrekeyKeyPair.privateKey, theirIdentityKeyPublic)
        val dh2 = CryptoPrimitives.x25519DiffieHellman(ikPrivX, theirEphemeralKeyPublic)
        val dh3 = CryptoPrimitives.x25519DiffieHellman(ourSignedPrekeyKeyPair.privateKey, theirEphemeralKeyPublic)

        val dh4 = if (ourOneTimePrekeyKeyPair != null) {
            CryptoPrimitives.x25519DiffieHellman(ourOneTimePrekeyKeyPair.privateKey, theirEphemeralKeyPublic)
        } else null

        val dhInput = concatDhOutputs(dh1, dh2, dh3, dh4)
        val sk = CryptoPrimitives.hkdfSha256(
            input = dhInput,
            salt = ByteArray(32),
            info = X3DH_INFO.encodeToByteArray(),
            length = 32
        )

        val rootMaterial = CryptoPrimitives.hkdfSha256(
            input = sk,
            salt = ByteArray(32),
            info = ROOT_CHAIN_INFO.encodeToByteArray(),
            length = 64
        )
        val rootKey = rootMaterial.copyOfRange(0, 32)
        val chainKey = rootMaterial.copyOfRange(32, 64)
        CryptoPrimitives.zeroBytes(rootMaterial)

        zeroAll(dh1, dh2, dh3, dh4, dhInput, ikPrivX, sk)

        return X3dhResult(
            sharedSecret = sk,
            rootKey = rootKey,
            sendingChainKey = chainKey,
            receivingChainKey = chainKey,
            header = X3dhHeader(
                identityKey = ourIdentityKey.publicKey,
                ephemeralKey = theirEphemeralKeyPublic,
                signedPrekeyId = ourSignedPrekeyId,
                oneTimePrekeyId = ourOneTimePrekeyId
            )
        )
    }

    /**
     * Verify a signed prekey signature.
     *
     * @param signedPrekeyPublic the X25519 public key of the signed prekey
     * @param signature Ed25519 signature over the signed prekey public key
     * @param identityKeyPublic Ed25519 identity public key that should have signed
     * @return true if the signature is valid
     */
    fun verifySignedPrekey(
        signedPrekeyPublic: ByteArray,
        signature: ByteArray,
        identityKeyPublic: ByteArray
    ): Boolean {
        return CryptoPrimitives.verifyEd25519(signedPrekeyPublic, signature, identityKeyPublic)
    }

    private fun concatDhOutputs(dh1: ByteArray, dh2: ByteArray, dh3: ByteArray, dh4: ByteArray?): ByteArray {
        return if (dh4 != null) {
            ByteArray(dh1.size + dh2.size + dh3.size + dh4.size).apply {
                var offset = 0
                dh1.copyInto(this, offset); offset += dh1.size
                dh2.copyInto(this, offset); offset += dh2.size
                dh3.copyInto(this, offset); offset += dh3.size
                dh4.copyInto(this, offset)
            }
        } else {
            ByteArray(dh1.size + dh2.size + dh3.size).apply {
                var offset = 0
                dh1.copyInto(this, offset); offset += dh1.size
                dh2.copyInto(this, offset); offset += dh2.size
                dh3.copyInto(this, offset)
            }
        }
    }

    private fun zeroAll(vararg arrays: ByteArray?) {
        arrays.forEach { it?.let { CryptoPrimitives.zeroBytes(it) } }
    }
}

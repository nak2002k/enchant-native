package org.enchant.core.crypto

data class X3dhResult(
    val sharedSecret: ByteArray,
    val rootKey: ByteArray,
    val sendingChainKey: ByteArray,
    val receivingChainKey: ByteArray,
    val header: X3dhHeader
)

data class X3dhHeader(
    val identityKey: ByteArray,
    val ephemeralKey: ByteArray,
    val signedPrekeyId: Int,
    val oneTimePrekeyId: Int? = null
)

object X3DH {

    suspend fun aliceInitiate(
        ourIdentityKey: CryptoHelper.KeyPair,
        ourEphemeralKey: CryptoHelper.KeyPair,
        theirIdentityKeyPublic: ByteArray,
        theirSignedPrekeyPublic: ByteArray,
        theirOneTimePrekeyPublic: ByteArray? = null
    ): X3dhResult {
        val ikPrivX = CryptoHelper.ed25519SkToX25519(ourIdentityKey.privateKey)

        val dh1 = CryptoHelper.x25519DiffieHellman(ikPrivX, theirSignedPrekeyPublic)
        val dh2 = CryptoHelper.x25519DiffieHellman(ourEphemeralKey.privateKey, theirIdentityKeyPublic)
        val dh3 = CryptoHelper.x25519DiffieHellman(ourEphemeralKey.privateKey, theirSignedPrekeyPublic)

        val dh4 = if (theirOneTimePrekeyPublic != null) {
            val key = CryptoHelper.x25519DiffieHellman(ourEphemeralKey.privateKey, theirOneTimePrekeyPublic)
            key
        } else null

        val dhInput = if (dh4 != null) {
            dh1 + dh2 + dh3 + dh4
        } else {
            dh1 + dh2 + dh3
        }

        val salt = ByteArray(32)
        val sk = CryptoHelper.hkdfSha256(dhInput, salt, "EnchantX3DH".encodeToByteArray(), 32)
        val rootMaterial = CryptoHelper.hkdfSha256(sk, salt, "EnchantRoot".encodeToByteArray(), 64)
        val rootKey = rootMaterial.copyOfRange(0, 32)
        val chainKey = rootMaterial.copyOfRange(32, 64)

        CryptoHelper.zeroBytes(ikPrivX)
        CryptoHelper.zeroBytes(dh1)
        CryptoHelper.zeroBytes(dh2)
        CryptoHelper.zeroBytes(dh3)
        dh4?.let { CryptoHelper.zeroBytes(it) }
        CryptoHelper.zeroBytes(dhInput)

        return X3dhResult(
            sharedSecret = sk,
            rootKey = rootKey,
            sendingChainKey = chainKey,
            receivingChainKey = chainKey,
            header = X3dhHeader(
                identityKey = ourIdentityKey.publicKey,
                ephemeralKey = ourEphemeralKey.publicKey,
                signedPrekeyId = 0,
                oneTimePrekeyId = null
            )
        )
    }

    suspend fun bobRespond(
        ourIdentityKey: CryptoHelper.KeyPair,
        ourSignedPrekeyKeyPair: CryptoHelper.KeyPair,
        ourOneTimePrekeyKeyPair: CryptoHelper.KeyPair? = null,
        theirIdentityKeyPublic: ByteArray,
        theirEphemeralKeyPublic: ByteArray
    ): X3dhResult {
        val ikPrivX = CryptoHelper.ed25519SkToX25519(ourIdentityKey.privateKey)

        val dh1 = CryptoHelper.x25519DiffieHellman(ourSignedPrekeyKeyPair.privateKey, theirIdentityKeyPublic)
        val dh2 = CryptoHelper.x25519DiffieHellman(ikPrivX, theirEphemeralKeyPublic)
        val dh3 = CryptoHelper.x25519DiffieHellman(ourSignedPrekeyKeyPair.privateKey, theirEphemeralKeyPublic)

        val dh4 = if (ourOneTimePrekeyKeyPair != null) {
            val key = CryptoHelper.x25519DiffieHellman(ourOneTimePrekeyKeyPair.privateKey, theirEphemeralKeyPublic)
            key
        } else null

        val dhInput = if (dh4 != null) {
            dh1 + dh2 + dh3 + dh4
        } else {
            dh1 + dh2 + dh3
        }

        val salt = ByteArray(32)
        val sk = CryptoHelper.hkdfSha256(dhInput, salt, "EnchantX3DH".encodeToByteArray(), 32)
        val rootMaterial = CryptoHelper.hkdfSha256(sk, salt, "EnchantRoot".encodeToByteArray(), 64)
        val rootKey = rootMaterial.copyOfRange(0, 32)
        val chainKey = rootMaterial.copyOfRange(32, 64)

        CryptoHelper.zeroBytes(ikPrivX)
        CryptoHelper.zeroBytes(dh1)
        CryptoHelper.zeroBytes(dh2)
        CryptoHelper.zeroBytes(dh3)
        dh4?.let { CryptoHelper.zeroBytes(it) }
        CryptoHelper.zeroBytes(dhInput)

        return X3dhResult(
            sharedSecret = sk,
            rootKey = rootKey,
            sendingChainKey = chainKey,
            receivingChainKey = chainKey,
            header = X3dhHeader(
                identityKey = ourIdentityKey.publicKey,
                ephemeralKey = theirEphemeralKeyPublic,
                signedPrekeyId = 0,
                oneTimePrekeyId = null
            )
        )
    }

    private operator fun ByteArray.plus(other: ByteArray): ByteArray {
        val result = ByteArray(this.size + other.size)
        System.arraycopy(this, 0, result, 0, this.size)
        System.arraycopy(other, 0, result, this.size, other.size)
        return result
    }
}

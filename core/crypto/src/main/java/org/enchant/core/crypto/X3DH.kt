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
    ): X3dhResult? {
        return null
    }

    suspend fun bobRespond(
        ourIdentityKey: CryptoHelper.KeyPair,
        ourSignedPrekeyKeyPair: CryptoHelper.KeyPair,
        ourOneTimePrekeyKeyPair: CryptoHelper.KeyPair? = null,
        theirIdentityKeyPublic: ByteArray,
        theirEphemeralKeyPublic: ByteArray
    ): X3dhResult? {
        return null
    }
}

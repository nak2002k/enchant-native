package org.enchant.core.crypto

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object NativeX3DH {

    init {
        System.loadLibrary("enchantcrypto_jni")
    }

    const val SUCCESS = 0
    const val ERROR_NULL_POINTER = -1
    const val ERROR_BUFFER_TOO_SMALL = -2
    const val ERROR_INVALID_KEY_SIZE = -3
    const val ERROR_DECRYPTION_FAILED = -6
    const val ERROR_INVALID_FORMAT = -11
    const val ERROR_INTERNAL = -99

    const val X3DH_SHARED_SECRET_SIZE = 32
    const val X3DH_ROOT_KEY_SIZE = 32
    const val X3DH_CHAIN_KEY_SIZE = 32
    const val KYBER_PUBLIC_KEY_SIZE = 1568
    const val KYBER_SECRET_KEY_SIZE = 3168
    const val KYBER_CIPHERTEXT_SIZE = 1088
    const val KYBER_SHARED_SECRET_SIZE = 32

    data class X3DHResult(
        val sharedSecret: ByteArray,
        val rootKey: ByteArray,
        val sendingChainKey: ByteArray,
        val receivingChainKey: ByteArray,
        val pqrKey: ByteArray
    ) {
        fun zero() {
            CryptoPrimitives.zeroBytes(sharedSecret)
            CryptoPrimitives.zeroBytes(rootKey)
            CryptoPrimitives.zeroBytes(sendingChainKey)
            CryptoPrimitives.zeroBytes(receivingChainKey)
            CryptoPrimitives.zeroBytes(pqrKey)
        }
    }

    data class PQXDHResult(
        val rootKey: ByteArray,
        val chainKey: ByteArray,
        val pqrKey: ByteArray,
        val kyberCiphertext: ByteArray,
        val sharedSecret: ByteArray
    ) {
        fun zero() {
            CryptoPrimitives.zeroBytes(rootKey)
            CryptoPrimitives.zeroBytes(chainKey)
            CryptoPrimitives.zeroBytes(pqrKey)
            CryptoPrimitives.zeroBytes(kyberCiphertext)
            CryptoPrimitives.zeroBytes(sharedSecret)
        }
    }

    suspend fun x3dhInitiate(
        ourIdentityPrivate: ByteArray,
        ourEphemeralPrivate: ByteArray,
        theirIdentityPublic: ByteArray,
        theirSignedPrekey: ByteArray,
        theirOneTimePrekey: ByteArray?
    ): Result<X3DHResult> = withContext(Dispatchers.IO) {
        require(ourIdentityPrivate.size == EnchantCrypto.ED25519_SEED_SIZE) { "Invalid identity private key size" }
        require(ourEphemeralPrivate.size == EnchantCrypto.X25519_PRIVATE_KEY_SIZE) { "Invalid ephemeral private key size" }
        require(theirIdentityPublic.size == EnchantCrypto.ED25519_PUBLIC_KEY_SIZE) { "Invalid identity public key size" }
        require(theirSignedPrekey.size == EnchantCrypto.X25519_PUBLIC_KEY_SIZE) { "Invalid signed prekey size" }

        val sharedSecret = ByteArray(X3DH_SHARED_SECRET_SIZE)
        val rootKey = ByteArray(X3DH_ROOT_KEY_SIZE)
        val sendingChainKey = ByteArray(X3DH_CHAIN_KEY_SIZE)
        val receivingChainKey = ByteArray(X3DH_CHAIN_KEY_SIZE)
        val pqrKey = ByteArray(X3DH_SHARED_SECRET_SIZE)

        val rc = x3dhInitiateNative(
            ourIdentityPrivate,
            ourEphemeralPrivate,
            theirIdentityPublic,
            theirSignedPrekey,
            theirOneTimePrekey,
            sharedSecret,
            rootKey,
            sendingChainKey,
            receivingChainKey,
            pqrKey
        )

        if (rc != SUCCESS) {
            return@withContext Result.failure(EnchantCryptoException("x3dhInitiate failed: $rc"))
        }

        Result.success(X3DHResult(sharedSecret, rootKey, sendingChainKey, receivingChainKey, pqrKey))
    }

    suspend fun x3dhRespond(
        ourIdentityPrivate: ByteArray,
        ourSignedPrekeyPrivate: ByteArray,
        ourOneTimePrekeyPrivate: ByteArray?,
        theirIdentityPublic: ByteArray,
        theirEphemeralPublic: ByteArray
    ): Result<X3DHResult> = withContext(Dispatchers.IO) {
        require(ourIdentityPrivate.size == EnchantCrypto.ED25519_SEED_SIZE) { "Invalid identity private key size" }
        require(ourSignedPrekeyPrivate.size == EnchantCrypto.X25519_PRIVATE_KEY_SIZE) { "Invalid signed prekey private size" }
        require(theirIdentityPublic.size == EnchantCrypto.ED25519_PUBLIC_KEY_SIZE) { "Invalid identity public key size" }
        require(theirEphemeralPublic.size == EnchantCrypto.X25519_PUBLIC_KEY_SIZE) { "Invalid ephemeral public key size" }

        val sharedSecret = ByteArray(X3DH_SHARED_SECRET_SIZE)
        val rootKey = ByteArray(X3DH_ROOT_KEY_SIZE)
        val sendingChainKey = ByteArray(X3DH_CHAIN_KEY_SIZE)
        val receivingChainKey = ByteArray(X3DH_CHAIN_KEY_SIZE)
        val pqrKey = ByteArray(X3DH_SHARED_SECRET_SIZE)

        val rc = x3dhRespondNative(
            ourIdentityPrivate,
            ourSignedPrekeyPrivate,
            ourOneTimePrekeyPrivate,
            theirIdentityPublic,
            theirEphemeralPublic,
            sharedSecret,
            rootKey,
            sendingChainKey,
            receivingChainKey,
            pqrKey
        )

        if (rc != SUCCESS) {
            return@withContext Result.failure(EnchantCryptoException("x3dhRespond failed: $rc"))
        }

        Result.success(X3DHResult(sharedSecret, rootKey, sendingChainKey, receivingChainKey, pqrKey))
    }

    suspend fun pqxdhInitiate(
        ourIdentityPrivate: ByteArray,
        ourEphemeralPrivate: ByteArray,
        theirIdentityPublic: ByteArray,
        theirSignedPrekey: ByteArray,
        theirKyberPrekey: ByteArray,
        theirOneTimePrekey: ByteArray?
    ): Result<PQXDHResult> = withContext(Dispatchers.IO) {
        require(ourIdentityPrivate.size == EnchantCrypto.ED25519_SEED_SIZE) { "Invalid identity private key size" }
        require(ourEphemeralPrivate.size == EnchantCrypto.X25519_PRIVATE_KEY_SIZE) { "Invalid ephemeral private key size" }
        require(theirIdentityPublic.size == EnchantCrypto.ED25519_PUBLIC_KEY_SIZE) { "Invalid identity public key size" }
        require(theirSignedPrekey.size == EnchantCrypto.X25519_PUBLIC_KEY_SIZE) { "Invalid signed prekey size" }
        require(theirKyberPrekey.size == KYBER_PUBLIC_KEY_SIZE) { "Invalid Kyber prekey size" }

        val rootKey = ByteArray(X3DH_ROOT_KEY_SIZE)
        val chainKey = ByteArray(X3DH_CHAIN_KEY_SIZE)
        val pqrKey = ByteArray(KYBER_SHARED_SECRET_SIZE)
        val kyberCiphertext = ByteArray(KYBER_CIPHERTEXT_SIZE)
        val sharedSecret = ByteArray(KYBER_SHARED_SECRET_SIZE)

        val rc = pqxdhInitiateNative(
            ourIdentityPrivate,
            ourEphemeralPrivate,
            theirIdentityPublic,
            theirSignedPrekey,
            theirKyberPrekey,
            theirOneTimePrekey,
            rootKey,
            chainKey,
            pqrKey,
            kyberCiphertext,
            sharedSecret
        )

        if (rc != SUCCESS) {
            return@withContext Result.failure(EnchantCryptoException("pqxdhInitiate failed: $rc"))
        }

        Result.success(PQXDHResult(rootKey, chainKey, pqrKey, kyberCiphertext, sharedSecret))
    }

    suspend fun pqxdhRespond(
        ourIdentityPrivate: ByteArray,
        ourSignedPrekeyPrivate: ByteArray,
        ourOneTimePrekeyPrivate: ByteArray?,
        ourKyberPrekeyPrivate: ByteArray,
        theirIdentityPublic: ByteArray,
        theirEphemeralPublic: ByteArray,
        theirKyberCiphertext: ByteArray
    ): Result<PQXDHResult> = withContext(Dispatchers.IO) {
        require(ourIdentityPrivate.size == EnchantCrypto.ED25519_SEED_SIZE) { "Invalid identity private key size" }
        require(ourSignedPrekeyPrivate.size == EnchantCrypto.X25519_PRIVATE_KEY_SIZE) { "Invalid signed prekey private size" }
        require(ourKyberPrekeyPrivate.size == KYBER_SECRET_KEY_SIZE) { "Invalid Kyber prekey private size" }
        require(theirIdentityPublic.size == EnchantCrypto.ED25519_PUBLIC_KEY_SIZE) { "Invalid identity public key size" }
        require(theirEphemeralPublic.size == EnchantCrypto.X25519_PUBLIC_KEY_SIZE) { "Invalid ephemeral public key size" }
        require(theirKyberCiphertext.size == KYBER_CIPHERTEXT_SIZE) { "Invalid Kyber ciphertext size" }

        val rootKey = ByteArray(X3DH_ROOT_KEY_SIZE)
        val chainKey = ByteArray(X3DH_CHAIN_KEY_SIZE)
        val pqrKey = ByteArray(KYBER_SHARED_SECRET_SIZE)
        val sharedSecret = ByteArray(KYBER_SHARED_SECRET_SIZE)

        val rc = pqxdhRespondNative(
            ourIdentityPrivate,
            ourSignedPrekeyPrivate,
            ourOneTimePrekeyPrivate,
            ourKyberPrekeyPrivate,
            theirIdentityPublic,
            theirEphemeralPublic,
            theirKyberCiphertext,
            rootKey,
            chainKey,
            pqrKey,
            sharedSecret
        )

        if (rc != SUCCESS) {
            return@withContext Result.failure(EnchantCryptoException("pqxdhRespond failed: $rc"))
        }

        Result.success(PQXDHResult(rootKey, chainKey, pqrKey, ByteArray(0), sharedSecret))
    }

    private external fun x3dhInitiateNative(
        ourIdentityPrivate: ByteArray,
        ourEphemeralPrivate: ByteArray,
        theirIdentityPublic: ByteArray,
        theirSignedPrekey: ByteArray,
        theirOneTimePrekey: ByteArray?,
        sharedSecret: ByteArray,
        rootKey: ByteArray,
        sendingChainKey: ByteArray,
        receivingChainKey: ByteArray,
        pqrKey: ByteArray
    ): Int

    private external fun x3dhRespondNative(
        ourIdentityPrivate: ByteArray,
        ourSignedPrekeyPrivate: ByteArray,
        ourOneTimePrekeyPrivate: ByteArray?,
        theirIdentityPublic: ByteArray,
        theirEphemeralPublic: ByteArray,
        sharedSecret: ByteArray,
        rootKey: ByteArray,
        sendingChainKey: ByteArray,
        receivingChainKey: ByteArray,
        pqrKey: ByteArray
    ): Int

    private external fun pqxdhInitiateNative(
        ourIdentityPrivate: ByteArray,
        ourEphemeralPrivate: ByteArray,
        theirIdentityPublic: ByteArray,
        theirSignedPrekey: ByteArray,
        theirKyberPrekey: ByteArray,
        theirOneTimePrekey: ByteArray?,
        rootKey: ByteArray,
        chainKey: ByteArray,
        pqrKey: ByteArray,
        kyberCiphertext: ByteArray,
        sharedSecret: ByteArray
    ): Int

    private external fun pqxdhRespondNative(
        ourIdentityPrivate: ByteArray,
        ourSignedPrekeyPrivate: ByteArray,
        ourOneTimePrekeyPrivate: ByteArray?,
        ourKyberPrekeyPrivate: ByteArray,
        theirIdentityPublic: ByteArray,
        theirEphemeralPublic: ByteArray,
        theirKyberCiphertext: ByteArray,
        rootKey: ByteArray,
        chainKey: ByteArray,
        pqrKey: ByteArray,
        sharedSecret: ByteArray
    ): Int

    class EnchantCryptoException(message: String) : Exception(message)
}
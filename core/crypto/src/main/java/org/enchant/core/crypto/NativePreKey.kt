package org.enchant.core.crypto

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object NativePreKey {

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

    const val PREKEY_BATCH_SIZE = 100
    const val PREKEY_TOPUP_THRESHOLD = 10
    const val KYBER_PUBLIC_KEY_SIZE = 1568
    const val KYBER_SECRET_KEY_SIZE = 3168

    data class PreKeyRecord(
        val id: Int,
        val publicKey: ByteArray,
        val privateKey: ByteArray,
        val isLastResort: Boolean = false
    ) {
        fun zero() {
            CryptoPrimitives.zeroBytes(publicKey)
            CryptoPrimitives.zeroBytes(privateKey)
        }
    }

    data class SignedPreKeyRecord(
        val id: Int,
        val publicKey: ByteArray,
        val privateKey: ByteArray,
        val signature: ByteArray
    ) {
        fun zero() {
            CryptoPrimitives.zeroBytes(publicKey)
            CryptoPrimitives.zeroBytes(privateKey)
            CryptoPrimitives.zeroBytes(signature)
        }
    }

    data class KyberPreKeyRecord(
        val id: Int,
        val publicKey: ByteArray,
        val privateKey: ByteArray,
        val signature: ByteArray
    ) {
        fun zero() {
            CryptoPrimitives.zeroBytes(publicKey)
            CryptoPrimitives.zeroBytes(privateKey)
            CryptoPrimitives.zeroBytes(signature)
        }
    }

    data class PreKeyBundle(
        val registrationId: Int,
        val deviceId: Int,
        val preKeyId: Int,
        val preKeyPublic: ByteArray,
        val signedPreKeyId: Int,
        val signedPreKeyPublic: ByteArray,
        val signedPreKeySignature: ByteArray,
        val identityKey: ByteArray,
        val kyberPreKeyId: Int?,
        val kyberPreKeyPublic: ByteArray?,
        val kyberPreKeySignature: ByteArray?
    )

    suspend fun createPreKeyBundle(
        registrationId: Int,
        deviceId: Int,
        preKeyId: Int,
        identityPrivate: ByteArray
    ): Result<PreKeyBundle> = withContext(Dispatchers.IO) {
        require(identityPrivate.size == EnchantCrypto.ED25519_SEED_SIZE) { "Invalid identity private key size" }

        val preKeyPublic = ByteArray(EnchantCrypto.X25519_PUBLIC_KEY_SIZE)
        val preKeyPrivate = ByteArray(EnchantCrypto.X25519_PRIVATE_KEY_SIZE)
        val signedPreKeyPublic = ByteArray(EnchantCrypto.X25519_PUBLIC_KEY_SIZE)
        val signedPreKeyPrivate = ByteArray(EnchantCrypto.X25519_PRIVATE_KEY_SIZE)
        val signedPreKeySignature = ByteArray(EnchantCrypto.ED25519_SIGNATURE_SIZE)
        val identityPublic = ByteArray(EnchantCrypto.ED25519_PUBLIC_KEY_SIZE)

        val rc = createPreKeyBundleNative(
            registrationId,
            deviceId,
            preKeyId,
            identityPrivate,
            preKeyPublic,
            preKeyPrivate,
            signedPreKeyPublic,
            signedPreKeyPrivate,
            signedPreKeySignature,
            identityPublic
        )

        if (rc != SUCCESS) {
            return@withContext Result.failure(EnchantCryptoException("createPreKeyBundle failed: $rc"))
        }

        Result.success(PreKeyBundle(
            registrationId = registrationId,
            deviceId = deviceId,
            preKeyId = preKeyId,
            preKeyPublic = preKeyPublic,
            signedPreKeyId = preKeyId,
            signedPreKeyPublic = signedPreKeyPublic,
            signedPreKeySignature = signedPreKeySignature,
            identityKey = identityPublic,
            kyberPreKeyId = null,
            kyberPreKeyPublic = null,
            kyberPreKeySignature = null
        ))
    }

    suspend fun createKyberPreKeyBundle(
        registrationId: Int,
        deviceId: Int,
        preKeyId: Int,
        kyberPreKeyId: Int,
        identityPrivate: ByteArray
    ): Result<PreKeyBundle> = withContext(Dispatchers.IO) {
        require(identityPrivate.size == EnchantCrypto.ED25519_SEED_SIZE) { "Invalid identity private key size" }

        val preKeyPublic = ByteArray(EnchantCrypto.X25519_PUBLIC_KEY_SIZE)
        val preKeyPrivate = ByteArray(EnchantCrypto.X25519_PRIVATE_KEY_SIZE)
        val signedPreKeyPublic = ByteArray(EnchantCrypto.X25519_PUBLIC_KEY_SIZE)
        val signedPreKeyPrivate = ByteArray(EnchantCrypto.X25519_PRIVATE_KEY_SIZE)
        val signedPreKeySignature = ByteArray(EnchantCrypto.ED25519_SIGNATURE_SIZE)
        val identityPublic = ByteArray(EnchantCrypto.ED25519_PUBLIC_KEY_SIZE)
        val kyberPreKeyPublic = ByteArray(KYBER_PUBLIC_KEY_SIZE)
        val kyberPreKeyPrivate = ByteArray(KYBER_SECRET_KEY_SIZE)
        val kyberPreKeySignature = ByteArray(EnchantCrypto.ED25519_SIGNATURE_SIZE)

        val rc = createKyberPreKeyBundleNative(
            registrationId,
            deviceId,
            preKeyId,
            kyberPreKeyId,
            identityPrivate,
            preKeyPublic,
            preKeyPrivate,
            signedPreKeyPublic,
            signedPreKeyPrivate,
            signedPreKeySignature,
            identityPublic,
            kyberPreKeyPublic,
            kyberPreKeyPrivate,
            kyberPreKeySignature
        )

        if (rc != SUCCESS) {
            return@withContext Result.failure(EnchantCryptoException("createKyberPreKeyBundle failed: $rc"))
        }

        Result.success(PreKeyBundle(
            registrationId = registrationId,
            deviceId = deviceId,
            preKeyId = preKeyId,
            preKeyPublic = preKeyPublic,
            signedPreKeyId = preKeyId,
            signedPreKeyPublic = signedPreKeyPublic,
            signedPreKeySignature = signedPreKeySignature,
            identityKey = identityPublic,
            kyberPreKeyId = kyberPreKeyId,
            kyberPreKeyPublic = kyberPreKeyPublic,
            kyberPreKeySignature = kyberPreKeySignature
        ))
    }

    suspend fun serializePreKeyRecord(record: PreKeyRecord): Result<ByteArray> = withContext(Dispatchers.IO) {
        require(record.publicKey.size == EnchantCrypto.X25519_PUBLIC_KEY_SIZE) { "Invalid public key size" }
        require(record.privateKey.size == EnchantCrypto.X25519_PRIVATE_KEY_SIZE) { "Invalid private key size" }

        val output = ByteArray(4 + EnchantCrypto.X25519_PUBLIC_KEY_SIZE + EnchantCrypto.X25519_PRIVATE_KEY_SIZE + 1)
        val outputLen = IntArray(1)

        val rc = serializePreKeyRecordNative(
            record.id,
            record.publicKey,
            record.privateKey,
            if (record.isLastResort) 1 else 0,
            output,
            outputLen
        )

        if (rc != SUCCESS) {
            return@withContext Result.failure(EnchantCryptoException("serializePreKeyRecord failed: $rc"))
        }

        Result.success(output.copyOf(outputLen[0]))
    }

    suspend fun deserializePreKeyRecord(data: ByteArray): Result<PreKeyRecord> = withContext(Dispatchers.IO) {
        require(data.size >= 4 + EnchantCrypto.X25519_PUBLIC_KEY_SIZE + EnchantCrypto.X25519_PRIVATE_KEY_SIZE + 1) { "Data too short" }

        val id = IntArray(1)
        val publicKey = ByteArray(EnchantCrypto.X25519_PUBLIC_KEY_SIZE)
        val privateKey = ByteArray(EnchantCrypto.X25519_PRIVATE_KEY_SIZE)
        val isLastResort = IntArray(1)

        val rc = deserializePreKeyRecordNative(
            data,
            data.size,
            id,
            publicKey,
            privateKey,
            isLastResort
        )

        if (rc != SUCCESS) {
            return@withContext Result.failure(EnchantCryptoException("deserializePreKeyRecord failed: $rc"))
        }

        Result.success(PreKeyRecord(id[0], publicKey, privateKey, isLastResort[0] == 1))
    }

    suspend fun serializeSignedPreKeyRecord(record: SignedPreKeyRecord): Result<ByteArray> = withContext(Dispatchers.IO) {
        require(record.publicKey.size == EnchantCrypto.X25519_PUBLIC_KEY_SIZE) { "Invalid public key size" }
        require(record.privateKey.size == EnchantCrypto.X25519_PRIVATE_KEY_SIZE) { "Invalid private key size" }
        require(record.signature.size == EnchantCrypto.ED25519_SIGNATURE_SIZE) { "Invalid signature size" }

        val output = ByteArray(4 + EnchantCrypto.X25519_PUBLIC_KEY_SIZE + EnchantCrypto.X25519_PRIVATE_KEY_SIZE + EnchantCrypto.ED25519_SIGNATURE_SIZE)
        val outputLen = IntArray(1)

        val rc = serializeSignedPreKeyRecordNative(
            record.id,
            record.publicKey,
            record.privateKey,
            record.signature,
            output,
            outputLen
        )

        if (rc != SUCCESS) {
            return@withContext Result.failure(EnchantCryptoException("serializeSignedPreKeyRecord failed: $rc"))
        }

        Result.success(output.copyOf(outputLen[0]))
    }

    suspend fun deserializeSignedPreKeyRecord(data: ByteArray): Result<SignedPreKeyRecord> = withContext(Dispatchers.IO) {
        require(data.size >= 4 + EnchantCrypto.X25519_PUBLIC_KEY_SIZE + EnchantCrypto.X25519_PRIVATE_KEY_SIZE + EnchantCrypto.ED25519_SIGNATURE_SIZE) { "Data too short" }

        val id = IntArray(1)
        val publicKey = ByteArray(EnchantCrypto.X25519_PUBLIC_KEY_SIZE)
        val privateKey = ByteArray(EnchantCrypto.X25519_PRIVATE_KEY_SIZE)
        val signature = ByteArray(EnchantCrypto.ED25519_SIGNATURE_SIZE)

        val rc = deserializeSignedPreKeyRecordNative(
            data,
            data.size,
            id,
            publicKey,
            privateKey,
            signature
        )

        if (rc != SUCCESS) {
            return@withContext Result.failure(EnchantCryptoException("deserializeSignedPreKeyRecord failed: $rc"))
        }

        Result.success(SignedPreKeyRecord(id[0], publicKey, privateKey, signature))
    }

    private external fun createPreKeyBundleNative(
        registrationId: Int,
        deviceId: Int,
        preKeyId: Int,
        identityPrivate: ByteArray,
        preKeyPublic: ByteArray,
        preKeyPrivate: ByteArray,
        signedPreKeyPublic: ByteArray,
        signedPreKeyPrivate: ByteArray,
        signedPreKeySignature: ByteArray,
        identityPublic: ByteArray
    ): Int

    private external fun createKyberPreKeyBundleNative(
        registrationId: Int,
        deviceId: Int,
        preKeyId: Int,
        kyberPreKeyId: Int,
        identityPrivate: ByteArray,
        preKeyPublic: ByteArray,
        preKeyPrivate: ByteArray,
        signedPreKeyPublic: ByteArray,
        signedPreKeyPrivate: ByteArray,
        signedPreKeySignature: ByteArray,
        identityPublic: ByteArray,
        kyberPreKeyPublic: ByteArray,
        kyberPreKeyPrivate: ByteArray,
        kyberPreKeySignature: ByteArray
    ): Int

    private external fun serializePreKeyRecordNative(
        id: Int,
        publicKey: ByteArray,
        privateKey: ByteArray,
        isLastResort: Int,
        output: ByteArray,
        outputLen: IntArray
    ): Int

    private external fun deserializePreKeyRecordNative(
        data: ByteArray,
        dataLen: Int,
        id: IntArray,
        publicKey: ByteArray,
        privateKey: ByteArray,
        isLastResort: IntArray
    ): Int

    private external fun serializeSignedPreKeyRecordNative(
        id: Int,
        publicKey: ByteArray,
        privateKey: ByteArray,
        signature: ByteArray,
        output: ByteArray,
        outputLen: IntArray
    ): Int

    private external fun deserializeSignedPreKeyRecordNative(
        data: ByteArray,
        dataLen: Int,
        id: IntArray,
        publicKey: ByteArray,
        privateKey: ByteArray,
        signature: ByteArray
    ): Int

    class EnchantCryptoException(message: String) : Exception(message)
}
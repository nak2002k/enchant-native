package org.enchant.core.crypto

/**
 * JNI bridge to libenchantcrypto native library.
 *
 * Wraps the C API defined in enchant/api.h, built on libsodium.
 * The native .so file is loaded from jniLibs/ for each ABI.
 *
 * All return values follow the api.h convention:
 *   0 = ENCHANT_SUCCESS, negative = error code
 */
object EnchantCrypto {

    init {
        System.loadLibrary("enchantcrypto_jni")
    }

    // --- Error codes ---
    const val SUCCESS = 0
    const val ERROR_NULL_POINTER = -1
    const val ERROR_BUFFER_TOO_SMALL = -2
    const val ERROR_INVALID_KEY_SIZE = -3
    const val ERROR_DECRYPTION_FAILED = -6
    const val ERROR_SIGNATURE_INVALID = -7
    const val ERROR_INVALID_FORMAT = -11
    const val ERROR_INTERNAL = -99

    // --- Sizes ---
    const val X25519_PUBLIC_KEY_SIZE = 32
    const val X25519_PRIVATE_KEY_SIZE = 32
    const val ED25519_PUBLIC_KEY_SIZE = 32
    const val ED25519_SEED_SIZE = 32
    const val ED25519_SIGNATURE_SIZE = 64
    const val XCHACHA20_KEY_SIZE = 32
    const val XCHACHA20_NONCE_SIZE = 24
    const val XCHACHA20_TAG_SIZE = 16
    const val SHA256_SIZE = 32
    const val HMAC_SHA256_SIZE = 32
    const val HKDF_MAX_OUTPUT = 8160
    const val ARGON2_STRBYTES = 128

    // --- Native functions ---
    external fun enchant_init(): Int
    external fun enchant_random_bytes(buf: ByteArray, len: Int)
    external fun enchant_x25519_keypair(publicKey: ByteArray, privateKey: ByteArray): Int
    external fun enchant_x25519_dh(privateKey: ByteArray, publicKey: ByteArray, sharedSecret: ByteArray): Int
    external fun enchant_ed25519_keypair(publicKey: ByteArray, privateSeed: ByteArray): Int
    external fun enchant_ed25519_sign(message: ByteArray, message_len: Int, privateSeed: ByteArray, signature: ByteArray): Int
    external fun enchant_ed25519_verify(message: ByteArray, message_len: Int, signature: ByteArray, publicKey: ByteArray): Int
    external fun enchant_xchacha20_encrypt(plaintext: ByteArray, plaintext_len: Int, key: ByteArray, nonce: ByteArray, ciphertext: ByteArray): Int
    external fun enchant_xchacha20_decrypt(ciphertext: ByteArray, ciphertext_len: Int, key: ByteArray, nonce: ByteArray, plaintext: ByteArray): Int
    external fun enchant_hkdf_sha256(ikm: ByteArray, ikm_len: Int, salt: ByteArray, salt_len: Int, info: ByteArray, info_len: Int, okm: ByteArray, okm_len: Int): Int
    external fun enchant_sha256(data: ByteArray, len: Int, hash: ByteArray): Int
    external fun enchant_hmac_sha256(key: ByteArray, key_len: Int, data: ByteArray, data_len: Int, mac: ByteArray): Int
    external fun enchant_base64_encode(data: ByteArray, len: Int, output: ByteArray, output_len: Int): Int
    external fun enchant_base64_decode(input: String, output: ByteArray, output_len: Int): Int
    external fun enchant_argon2id_hash(plaintext: String, plaintext_len: Int, output: ByteArray, output_len: Int): Int
    external fun enchant_argon2id_hash_with_params(plaintext: ByteArray, plaintext_len: Int, salt: ByteArray, iterations: Int, memory_kb: Int, parallelism: Int, output: ByteArray, output_len: Int): Int
    external fun enchant_argon2id_verify(hash: String, hash_len: Int, plaintext: String, plaintext_len: Int): Int
    external fun enchant_secure_zero(ptr: Long, len: Int)
    external fun enchant_secure_alloc(len: Int): Long
    external fun enchant_secure_free(ptr: Long, len: Int)
}

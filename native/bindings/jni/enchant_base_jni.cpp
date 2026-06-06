#include <jni.h>
#include <enchant/api.h>
#include <enchant/error.h>
#include <cstring>

extern "C" {

/* ═══════════════════════════════════════════════════════════════
   EnchantCrypto — Base C API JNI bindings
   Maps Java_org_enchant_core_crypto_EnchantCrypto_* to enchant_* C functions
   ═══════════════════════════════════════════════════════════════ */

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1init(
    JNIEnv* env, jclass clazz) {
    (void)env; (void)clazz;
    return enchant_init();
}

JNIEXPORT void JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1random_1bytes(
    JNIEnv* env, jclass clazz, jbyteArray buf, jint len) {
    (void)clazz;
    jbyte* data = env->GetByteArrayElements(buf, nullptr);
    enchant_random_bytes(reinterpret_cast<uint8_t*>(data), static_cast<size_t>(len));
    env->ReleaseByteArrayElements(buf, data, 0);
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1x25519_1keypair(
    JNIEnv* env, jclass clazz, jbyteArray publicKey, jbyteArray privateKey) {
    (void)clazz;
    jbyte* pub = env->GetByteArrayElements(publicKey, nullptr);
    jbyte* priv = env->GetByteArrayElements(privateKey, nullptr);
    int rc = enchant_x25519_keypair(
        reinterpret_cast<uint8_t*>(pub),
        reinterpret_cast<uint8_t*>(priv));
    env->ReleaseByteArrayElements(publicKey, pub, 0);
    env->ReleaseByteArrayElements(privateKey, priv, 0);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1x25519_1dh(
    JNIEnv* env, jclass clazz, jbyteArray privateKey, jbyteArray publicKey, jbyteArray sharedSecret) {
    (void)clazz;
    jbyte* priv = env->GetByteArrayElements(privateKey, nullptr);
    jbyte* pub = env->GetByteArrayElements(publicKey, nullptr);
    jbyte* secret = env->GetByteArrayElements(sharedSecret, nullptr);
    int rc = enchant_x25519_dh(
        reinterpret_cast<const uint8_t*>(priv),
        reinterpret_cast<const uint8_t*>(pub),
        reinterpret_cast<uint8_t*>(secret));
    env->ReleaseByteArrayElements(privateKey, priv, JNI_ABORT);
    env->ReleaseByteArrayElements(publicKey, pub, JNI_ABORT);
    env->ReleaseByteArrayElements(sharedSecret, secret, 0);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1ed25519_1keypair(
    JNIEnv* env, jclass clazz, jbyteArray publicKey, jbyteArray privateSeed) {
    (void)clazz;
    jbyte* pub = env->GetByteArrayElements(publicKey, nullptr);
    jbyte* seed = env->GetByteArrayElements(privateSeed, nullptr);
    int rc = enchant_ed25519_keypair(
        reinterpret_cast<uint8_t*>(pub),
        reinterpret_cast<uint8_t*>(seed));
    env->ReleaseByteArrayElements(publicKey, pub, 0);
    env->ReleaseByteArrayElements(privateSeed, seed, 0);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1ed25519_1sign(
    JNIEnv* env, jclass clazz, jbyteArray message, jint messageLen,
    jbyteArray privateSeed, jbyteArray signature) {
    (void)clazz;
    jbyte* msg = env->GetByteArrayElements(message, nullptr);
    jbyte* seed = env->GetByteArrayElements(privateSeed, nullptr);
    jbyte* sig = env->GetByteArrayElements(signature, nullptr);
    int rc = enchant_ed25519_sign(
        reinterpret_cast<const uint8_t*>(msg),
        static_cast<size_t>(messageLen),
        reinterpret_cast<const uint8_t*>(seed),
        reinterpret_cast<uint8_t*>(sig));
    env->ReleaseByteArrayElements(message, msg, JNI_ABORT);
    env->ReleaseByteArrayElements(privateSeed, seed, JNI_ABORT);
    env->ReleaseByteArrayElements(signature, sig, 0);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1ed25519_1verify(
    JNIEnv* env, jclass clazz, jbyteArray message, jint messageLen,
    jbyteArray signature, jbyteArray publicKey) {
    (void)clazz;
    jbyte* msg = env->GetByteArrayElements(message, nullptr);
    jbyte* sig = env->GetByteArrayElements(signature, nullptr);
    jbyte* pub = env->GetByteArrayElements(publicKey, nullptr);
    int rc = enchant_ed25519_verify(
        reinterpret_cast<const uint8_t*>(msg),
        static_cast<size_t>(messageLen),
        reinterpret_cast<const uint8_t*>(sig),
        reinterpret_cast<const uint8_t*>(pub));
    env->ReleaseByteArrayElements(message, msg, JNI_ABORT);
    env->ReleaseByteArrayElements(signature, sig, JNI_ABORT);
    env->ReleaseByteArrayElements(publicKey, pub, JNI_ABORT);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1xchacha20_1encrypt(
    JNIEnv* env, jclass clazz, jbyteArray plaintext, jint plaintextLen,
    jbyteArray key, jbyteArray nonce, jbyteArray ciphertext) {
    (void)clazz;
    jbyte* pt = env->GetByteArrayElements(plaintext, nullptr);
    jbyte* k = env->GetByteArrayElements(key, nullptr);
    jbyte* n = env->GetByteArrayElements(nonce, nullptr);
    jbyte* ct = env->GetByteArrayElements(ciphertext, nullptr);
    int rc = enchant_xchacha20_encrypt(
        reinterpret_cast<const uint8_t*>(pt),
        static_cast<size_t>(plaintextLen),
        reinterpret_cast<const uint8_t*>(k),
        reinterpret_cast<const uint8_t*>(n),
        reinterpret_cast<uint8_t*>(ct),
        static_cast<size_t>(env->GetArrayLength(ciphertext)));
    env->ReleaseByteArrayElements(plaintext, pt, JNI_ABORT);
    env->ReleaseByteArrayElements(key, k, JNI_ABORT);
    env->ReleaseByteArrayElements(nonce, n, JNI_ABORT);
    env->ReleaseByteArrayElements(ciphertext, ct, 0);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1xchacha20_1decrypt(
    JNIEnv* env, jclass clazz, jbyteArray ciphertext, jint ciphertextLen,
    jbyteArray key, jbyteArray nonce, jbyteArray plaintext) {
    (void)clazz;
    jbyte* ct = env->GetByteArrayElements(ciphertext, nullptr);
    jbyte* k = env->GetByteArrayElements(key, nullptr);
    jbyte* n = env->GetByteArrayElements(nonce, nullptr);
    jbyte* pt = env->GetByteArrayElements(plaintext, nullptr);
    int rc = enchant_xchacha20_decrypt(
        reinterpret_cast<const uint8_t*>(ct),
        static_cast<size_t>(ciphertextLen),
        reinterpret_cast<const uint8_t*>(k),
        reinterpret_cast<const uint8_t*>(n),
        reinterpret_cast<uint8_t*>(pt),
        static_cast<size_t>(env->GetArrayLength(plaintext)));
    env->ReleaseByteArrayElements(ciphertext, ct, JNI_ABORT);
    env->ReleaseByteArrayElements(key, k, JNI_ABORT);
    env->ReleaseByteArrayElements(nonce, n, JNI_ABORT);
    env->ReleaseByteArrayElements(plaintext, pt, 0);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1hkdf_1sha256(
    JNIEnv* env, jclass clazz, jbyteArray ikm, jint ikmLen,
    jbyteArray salt, jint saltLen, jbyteArray info, jint infoLen,
    jbyteArray okm, jint okmLen) {
    (void)clazz;
    jbyte* ik = env->GetByteArrayElements(ikm, nullptr);
    jbyte* s = env->GetByteArrayElements(salt, nullptr);
    jbyte* i = env->GetByteArrayElements(info, nullptr);
    jbyte* o = env->GetByteArrayElements(okm, nullptr);
    int rc = enchant_hkdf_sha256(
        reinterpret_cast<const uint8_t*>(ik), static_cast<size_t>(ikmLen),
        reinterpret_cast<const uint8_t*>(s), static_cast<size_t>(saltLen),
        reinterpret_cast<const uint8_t*>(i), static_cast<size_t>(infoLen),
        reinterpret_cast<uint8_t*>(o), static_cast<size_t>(okmLen));
    env->ReleaseByteArrayElements(ikm, ik, JNI_ABORT);
    env->ReleaseByteArrayElements(salt, s, JNI_ABORT);
    env->ReleaseByteArrayElements(info, i, JNI_ABORT);
    env->ReleaseByteArrayElements(okm, o, 0);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1sha256(
    JNIEnv* env, jclass clazz, jbyteArray data, jint len, jbyteArray hash) {
    (void)clazz;
    jbyte* d = env->GetByteArrayElements(data, nullptr);
    jbyte* h = env->GetByteArrayElements(hash, nullptr);
    int rc = enchant_sha256(
        reinterpret_cast<const uint8_t*>(d),
        static_cast<size_t>(len),
        reinterpret_cast<uint8_t*>(h));
    env->ReleaseByteArrayElements(data, d, JNI_ABORT);
    env->ReleaseByteArrayElements(hash, h, 0);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1hmac_1sha256(
    JNIEnv* env, jclass clazz, jbyteArray key, jint keyLen,
    jbyteArray data, jint dataLen, jbyteArray mac) {
    (void)clazz;
    jbyte* k = env->GetByteArrayElements(key, nullptr);
    jbyte* d = env->GetByteArrayElements(data, nullptr);
    jbyte* m = env->GetByteArrayElements(mac, nullptr);
    int rc = enchant_hmac_sha256(
        reinterpret_cast<const uint8_t*>(k), static_cast<size_t>(keyLen),
        reinterpret_cast<const uint8_t*>(d), static_cast<size_t>(dataLen),
        reinterpret_cast<uint8_t*>(m));
    env->ReleaseByteArrayElements(key, k, JNI_ABORT);
    env->ReleaseByteArrayElements(data, d, JNI_ABORT);
    env->ReleaseByteArrayElements(mac, m, 0);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1base64_1encode(
    JNIEnv* env, jclass clazz, jbyteArray data, jint len,
    jbyteArray output, jint outputLen) {
    (void)clazz;
    jbyte* d = env->GetByteArrayElements(data, nullptr);
    jbyte* o = env->GetByteArrayElements(output, nullptr);
    int rc = enchant_base64_encode(
        reinterpret_cast<const uint8_t*>(d),
        static_cast<size_t>(len),
        reinterpret_cast<char*>(o),
        static_cast<size_t>(outputLen));
    env->ReleaseByteArrayElements(data, d, JNI_ABORT);
    env->ReleaseByteArrayElements(output, o, 0);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1base64_1decode(
    JNIEnv* env, jclass clazz, jstring input, jbyteArray output, jint outputLen) {
    (void)clazz;
    const char* in = env->GetStringUTFChars(input, nullptr);
    jbyte* o = env->GetByteArrayElements(output, nullptr);
    int rc = enchant_base64_decode(
        in,
        reinterpret_cast<uint8_t*>(o),
        static_cast<size_t>(outputLen));
    env->ReleaseStringUTFChars(input, in);
    env->ReleaseByteArrayElements(output, o, 0);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1argon2id_1hash(
    JNIEnv* env, jclass clazz, jstring plaintext, jint plaintextLen,
    jbyteArray output, jint outputLen) {
    (void)clazz;
    const char* pt = env->GetStringUTFChars(plaintext, nullptr);
    jbyte* o = env->GetByteArrayElements(output, nullptr);
    int rc = enchant_argon2id_hash(
        pt,
        static_cast<size_t>(plaintextLen),
        reinterpret_cast<char*>(o),
        static_cast<size_t>(outputLen));
    env->ReleaseStringUTFChars(plaintext, pt);
    env->ReleaseByteArrayElements(output, o, 0);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1argon2id_1hash_1with_1params(
    JNIEnv* env, jclass clazz, jbyteArray plaintext, jint plaintextLen,
    jbyteArray salt, jint saltLen, jint iterations, jint memoryKb, jint parallelism,
    jbyteArray output, jint outputLen) {
    (void)clazz;
    (void)saltLen;
    jbyte* pt = env->GetByteArrayElements(plaintext, nullptr);
    jbyte* s = env->GetByteArrayElements(salt, nullptr);
    jbyte* o = env->GetByteArrayElements(output, nullptr);
    int rc = enchant_argon2id_hash(
        reinterpret_cast<const char*>(pt),
        static_cast<size_t>(plaintextLen),
        reinterpret_cast<char*>(o),
        static_cast<size_t>(outputLen));
    env->ReleaseByteArrayElements(plaintext, pt, JNI_ABORT);
    env->ReleaseByteArrayElements(salt, s, JNI_ABORT);
    env->ReleaseByteArrayElements(output, o, 0);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1argon2id_1verify(
    JNIEnv* env, jclass clazz, jstring hash, jint hashLen,
    jstring plaintext, jint plaintextLen) {
    (void)clazz;
    const char* h = env->GetStringUTFChars(hash, nullptr);
    const char* pt = env->GetStringUTFChars(plaintext, nullptr);
    int rc = enchant_argon2id_verify(
        h, static_cast<size_t>(hashLen),
        pt, static_cast<size_t>(plaintextLen));
    env->ReleaseStringUTFChars(hash, h);
    env->ReleaseStringUTFChars(plaintext, pt);
    return rc;
}

JNIEXPORT void JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1secure_1zero(
    JNIEnv* env, jclass clazz, jlong ptr, jint len) {
    (void)env; (void)clazz;
    enchant_secure_zero(reinterpret_cast<void*>(static_cast<uintptr_t>(ptr)),
                        static_cast<size_t>(len));
}

JNIEXPORT jlong JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1secure_1alloc(
    JNIEnv* env, jclass clazz, jint len) {
    (void)clazz;
    void* ptr = nullptr;
    int rc = enchant_secure_alloc(&ptr, static_cast<size_t>(len));
    if (rc != ENCHANT_SUCCESS) return 0;
    return static_cast<jlong>(reinterpret_cast<uintptr_t>(ptr));
}

JNIEXPORT void JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1secure_1free(
    JNIEnv* env, jclass clazz, jlong ptr, jint len) {
    (void)env; (void)clazz;
    enchant_secure_free(reinterpret_cast<void*>(static_cast<uintptr_t>(ptr)),
                        static_cast<size_t>(len));
}

} /* extern "C" */

#include <jni.h>
#include <enchant/api.h>
#include <enchant/error.h>
#include <cstring>

extern "C" {

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1init(
    JNIEnv* env, jclass clazz) {
    (void)clazz;

    // Get input arrays

    // Declare output variables

    // Call native function
    int rc = enchant_init();

    // Release and copy back

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jstring JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1version(
    JNIEnv* env, jclass clazz) {
    (void)clazz;

    // Get input arrays

    // Declare output variables

    // Call native function
    const char* result = enchant_version();
    jstring jresult = result ? env->NewStringUTF(result) : nullptr;

    // Release and copy back

    // Copy output values back to Java
    return jresult;
}

JNIEXPORT void JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1random_1bytes(
    JNIEnv* env, jclass clazz, jbyteArray buf, jlong len) {
    (void)clazz;

    // Get input arrays
    jbyte* buf_jbyte = env->GetByteArrayElements(buf, nullptr);
    uint8_t* buf_ptr = reinterpret_cast<uint8_t*>(buf_jbyte);

    // Declare output variables

    // Call native function
    enchant_random_bytes(buf_ptr, len);

    // Release and copy back
    env->ReleaseByteArrayElements(buf, buf_jbyte, 0);

    // Copy output values back to Java
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1x25519_1keypair(
    JNIEnv* env, jclass clazz, jbyteArray public_key, jbyteArray private_key) {
    (void)clazz;

    // Get input arrays
    jbyte* public_key_jbyte = env->GetByteArrayElements(public_key, nullptr);
    uint8_t* public_key_ptr = reinterpret_cast<uint8_t*>(public_key_jbyte);
    jbyte* private_key_jbyte = env->GetByteArrayElements(private_key, nullptr);
    uint8_t* private_key_ptr = reinterpret_cast<uint8_t*>(private_key_jbyte);

    // Declare output variables

    // Call native function
    int rc = enchant_x25519_keypair(public_key_ptr, private_key_ptr);

    // Release and copy back
    env->ReleaseByteArrayElements(public_key, public_key_jbyte, 0);
    env->ReleaseByteArrayElements(private_key, private_key_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1x25519_1dh(
    JNIEnv* env, jclass clazz, jbyteArray private_key, jbyteArray public_key, jbyteArray shared_secret) {
    (void)clazz;

    // Get input arrays
    jbyte* private_key_jbyte = env->GetByteArrayElements(private_key, nullptr);
    jbyte* public_key_jbyte = env->GetByteArrayElements(public_key, nullptr);
    jbyte* shared_secret_jbyte = env->GetByteArrayElements(shared_secret, nullptr);
    uint8_t* shared_secret_ptr = reinterpret_cast<uint8_t*>(shared_secret_jbyte);

    // Declare output variables

    // Call native function
    int rc = enchant_x25519_dh(reinterpret_cast<const uint8_t*>(private_key_jbyte), reinterpret_cast<const uint8_t*>(public_key_jbyte), shared_secret_ptr);

    // Release and copy back
    env->ReleaseByteArrayElements(private_key, private_key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(public_key, public_key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(shared_secret, shared_secret_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1ed25519_1keypair(
    JNIEnv* env, jclass clazz, jbyteArray public_key, jbyteArray private_seed) {
    (void)clazz;

    // Get input arrays
    jbyte* public_key_jbyte = env->GetByteArrayElements(public_key, nullptr);
    uint8_t* public_key_ptr = reinterpret_cast<uint8_t*>(public_key_jbyte);
    jbyte* private_seed_jbyte = env->GetByteArrayElements(private_seed, nullptr);
    uint8_t* private_seed_ptr = reinterpret_cast<uint8_t*>(private_seed_jbyte);

    // Declare output variables

    // Call native function
    int rc = enchant_ed25519_keypair(public_key_ptr, private_seed_ptr);

    // Release and copy back
    env->ReleaseByteArrayElements(public_key, public_key_jbyte, 0);
    env->ReleaseByteArrayElements(private_seed, private_seed_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1ed25519_1sign(
    JNIEnv* env, jclass clazz, jbyteArray message, jlong message_len, jbyteArray private_seed, jbyteArray signature) {
    (void)clazz;

    // Get input arrays
    jbyte* message_jbyte = env->GetByteArrayElements(message, nullptr);
    jbyte* private_seed_jbyte = env->GetByteArrayElements(private_seed, nullptr);
    jbyte* signature_jbyte = env->GetByteArrayElements(signature, nullptr);
    uint8_t* signature_ptr = reinterpret_cast<uint8_t*>(signature_jbyte);

    // Declare output variables

    // Call native function
    int rc = enchant_ed25519_sign(reinterpret_cast<const uint8_t*>(message_jbyte), message_len, reinterpret_cast<const uint8_t*>(private_seed_jbyte), signature_ptr);

    // Release and copy back
    env->ReleaseByteArrayElements(message, message_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(private_seed, private_seed_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(signature, signature_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1ed25519_1verify(
    JNIEnv* env, jclass clazz, jbyteArray message, jlong message_len, jbyteArray signature, jbyteArray public_key) {
    (void)clazz;

    // Get input arrays
    jbyte* message_jbyte = env->GetByteArrayElements(message, nullptr);
    jbyte* signature_jbyte = env->GetByteArrayElements(signature, nullptr);
    jbyte* public_key_jbyte = env->GetByteArrayElements(public_key, nullptr);

    // Declare output variables

    // Call native function
    int rc = enchant_ed25519_verify(reinterpret_cast<const uint8_t*>(message_jbyte), message_len, reinterpret_cast<const uint8_t*>(signature_jbyte), reinterpret_cast<const uint8_t*>(public_key_jbyte));

    // Release and copy back
    env->ReleaseByteArrayElements(message, message_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(signature, signature_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(public_key, public_key_jbyte, JNI_ABORT);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1xchacha20_1encrypt(
    JNIEnv* env, jclass clazz, jbyteArray plaintext, jlong plaintext_len, jbyteArray key, jbyteArray nonce, jbyteArray ciphertext, jlong ciphertext_capacity) {
    (void)clazz;

    // Get input arrays
    jbyte* plaintext_jbyte = env->GetByteArrayElements(plaintext, nullptr);
    jbyte* key_jbyte = env->GetByteArrayElements(key, nullptr);
    jbyte* nonce_jbyte = env->GetByteArrayElements(nonce, nullptr);
    jbyte* ciphertext_jbyte = env->GetByteArrayElements(ciphertext, nullptr);
    uint8_t* ciphertext_ptr = reinterpret_cast<uint8_t*>(ciphertext_jbyte);

    // Declare output variables

    // Call native function
    int rc = enchant_xchacha20_encrypt(reinterpret_cast<const uint8_t*>(plaintext_jbyte), plaintext_len, reinterpret_cast<const uint8_t*>(key_jbyte), reinterpret_cast<const uint8_t*>(nonce_jbyte), ciphertext_ptr, ciphertext_capacity);

    // Release and copy back
    env->ReleaseByteArrayElements(plaintext, plaintext_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(key, key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(nonce, nonce_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(ciphertext, ciphertext_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1xchacha20_1decrypt(
    JNIEnv* env, jclass clazz, jbyteArray ciphertext, jlong ciphertext_len, jbyteArray key, jbyteArray nonce, jbyteArray plaintext, jlong plaintext_capacity) {
    (void)clazz;

    // Get input arrays
    jbyte* ciphertext_jbyte = env->GetByteArrayElements(ciphertext, nullptr);
    jbyte* key_jbyte = env->GetByteArrayElements(key, nullptr);
    jbyte* nonce_jbyte = env->GetByteArrayElements(nonce, nullptr);
    jbyte* plaintext_jbyte = env->GetByteArrayElements(plaintext, nullptr);
    uint8_t* plaintext_ptr = reinterpret_cast<uint8_t*>(plaintext_jbyte);

    // Declare output variables

    // Call native function
    int rc = enchant_xchacha20_decrypt(reinterpret_cast<const uint8_t*>(ciphertext_jbyte), ciphertext_len, reinterpret_cast<const uint8_t*>(key_jbyte), reinterpret_cast<const uint8_t*>(nonce_jbyte), plaintext_ptr, plaintext_capacity);

    // Release and copy back
    env->ReleaseByteArrayElements(ciphertext, ciphertext_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(key, key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(nonce, nonce_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(plaintext, plaintext_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1hkdf_1sha256(
    JNIEnv* env, jclass clazz, jbyteArray ikm, jlong ikm_len, jbyteArray salt, jlong salt_len, jbyteArray info, jlong info_len, jbyteArray okm, jlong okm_len) {
    (void)clazz;

    // Get input arrays
    jbyte* ikm_jbyte = env->GetByteArrayElements(ikm, nullptr);
    jbyte* salt_jbyte = env->GetByteArrayElements(salt, nullptr);
    jbyte* info_jbyte = env->GetByteArrayElements(info, nullptr);
    jbyte* okm_jbyte = env->GetByteArrayElements(okm, nullptr);
    uint8_t* okm_ptr = reinterpret_cast<uint8_t*>(okm_jbyte);

    // Declare output variables

    // Call native function
    int rc = enchant_hkdf_sha256(reinterpret_cast<const uint8_t*>(ikm_jbyte), ikm_len, reinterpret_cast<const uint8_t*>(salt_jbyte), salt_len, reinterpret_cast<const uint8_t*>(info_jbyte), info_len, okm_ptr, okm_len);

    // Release and copy back
    env->ReleaseByteArrayElements(ikm, ikm_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(salt, salt_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(info, info_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(okm, okm_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1sha256(
    JNIEnv* env, jclass clazz, jbyteArray data, jlong len, jbyteArray hash) {
    (void)clazz;

    // Get input arrays
    jbyte* data_jbyte = env->GetByteArrayElements(data, nullptr);
    jbyte* hash_jbyte = env->GetByteArrayElements(hash, nullptr);
    uint8_t* hash_ptr = reinterpret_cast<uint8_t*>(hash_jbyte);

    // Declare output variables

    // Call native function
    int rc = enchant_sha256(reinterpret_cast<const uint8_t*>(data_jbyte), len, hash_ptr);

    // Release and copy back
    env->ReleaseByteArrayElements(data, data_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(hash, hash_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1hmac_1sha256(
    JNIEnv* env, jclass clazz, jbyteArray key, jlong key_len, jbyteArray data, jlong data_len, jbyteArray mac) {
    (void)clazz;

    // Get input arrays
    jbyte* key_jbyte = env->GetByteArrayElements(key, nullptr);
    jbyte* data_jbyte = env->GetByteArrayElements(data, nullptr);
    jbyte* mac_jbyte = env->GetByteArrayElements(mac, nullptr);
    uint8_t* mac_ptr = reinterpret_cast<uint8_t*>(mac_jbyte);

    // Declare output variables

    // Call native function
    int rc = enchant_hmac_sha256(reinterpret_cast<const uint8_t*>(key_jbyte), key_len, reinterpret_cast<const uint8_t*>(data_jbyte), data_len, mac_ptr);

    // Release and copy back
    env->ReleaseByteArrayElements(key, key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(data, data_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(mac, mac_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1base64_1encode(
    JNIEnv* env, jclass clazz, jbyteArray data, jlong len, jbyteArray output, jlong output_len) {
    (void)clazz;

    // Get input arrays
    jbyte* data_jbyte = env->GetByteArrayElements(data, nullptr);
    jbyte* output_jbyte = env->GetByteArrayElements(output, nullptr);
    char* output_ptr = reinterpret_cast<char*>(output_jbyte);

    // Declare output variables

    // Call native function
    int rc = enchant_base64_encode(reinterpret_cast<const uint8_t*>(data_jbyte), len, output_ptr, output_len);

    // Release and copy back
    env->ReleaseByteArrayElements(data, data_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(output, output_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1base64_1decode(
    JNIEnv* env, jclass clazz, jstring input, jlong input_len, jbyteArray output, jlong output_len, jlongArray decoded_len) {
    (void)clazz;

    // Get input arrays
    const char* input_str = env->GetStringUTFChars(input, nullptr);
    jbyte* output_jbyte = env->GetByteArrayElements(output, nullptr);
    uint8_t* output_ptr = reinterpret_cast<uint8_t*>(output_jbyte);
    jlong* decoded_len_elems = env->GetLongArrayElements(decoded_len, nullptr);

    // Declare output variables
    size_t decoded_len_val = 0;

    // Call native function
    int rc = enchant_base64_decode(input_str, static_cast<size_t>(input_len), output_ptr, static_cast<size_t>(output_len), &decoded_len_val);

    // Release and copy back
    env->ReleaseStringUTFChars(input, input_str);
    env->ReleaseByteArrayElements(output, output_jbyte, 0);

    // Copy output values back to Java
    decoded_len_elems[0] = static_cast<jlong>(decoded_len_val);
    env->ReleaseLongArrayElements(decoded_len, decoded_len_elems, 0);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1argon2id_1hash(
    JNIEnv* env, jclass clazz, jstring plaintext, jlong plaintext_len, jstring output, jlong output_len) {
    (void)clazz;

    // Get input arrays
    const char* plaintext_str = env->GetStringUTFChars(plaintext, nullptr);

    // Declare output variables
    char* output_ptr = nullptr;

    // Call native function
    int rc = enchant_argon2id_hash(plaintext_str, plaintext_len, output_ptr, output_len);

    // Release and copy back
    env->ReleaseStringUTFChars(plaintext, plaintext_str);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1argon2id_1hash_1with_1params(
    JNIEnv* env, jclass clazz, jbyteArray plaintext, jlong plaintext_len, jbyteArray salt, jlong salt_len, jint iterations, jint memory_kb, jint parallelism, jbyteArray output, jlong output_len) {
    (void)clazz;

    // Get input arrays
    jbyte* plaintext_jbyte = env->GetByteArrayElements(plaintext, nullptr);
    jbyte* salt_jbyte = env->GetByteArrayElements(salt, nullptr);
    jbyte* output_jbyte = env->GetByteArrayElements(output, nullptr);
    uint8_t* output_ptr = reinterpret_cast<uint8_t*>(output_jbyte);

    // Call native function
    int rc = enchant_argon2id_hash_with_params(reinterpret_cast<const uint8_t*>(plaintext_jbyte), plaintext_len, reinterpret_cast<const uint8_t*>(salt_jbyte), salt_len, iterations, memory_kb, parallelism, output_ptr, output_len);

    // Release and copy back
    env->ReleaseByteArrayElements(plaintext, plaintext_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(salt, salt_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(output, output_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1argon2id_1verify(
    JNIEnv* env, jclass clazz, jstring hash, jlong hash_len, jstring plaintext, jlong plaintext_len) {
    (void)clazz;

    // Get input arrays
    const char* hash_str = env->GetStringUTFChars(hash, nullptr);
    const char* plaintext_str = env->GetStringUTFChars(plaintext, nullptr);

    // Declare output variables

    // Call native function
    int rc = enchant_argon2id_verify(hash_str, hash_len, plaintext_str, plaintext_len);

    // Release and copy back
    env->ReleaseStringUTFChars(hash, hash_str);
    env->ReleaseStringUTFChars(plaintext, plaintext_str);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1aes_1256_1keygen(
    JNIEnv* env, jclass clazz, jbyteArray key) {
    (void)clazz;

    // Get input arrays
    jbyte* key_jbyte = env->GetByteArrayElements(key, nullptr);
    uint8_t* key_ptr = reinterpret_cast<uint8_t*>(key_jbyte);

    // Declare output variables

    // Call native function
    int rc = enchant_aes_256_keygen(key_ptr);

    // Release and copy back
    env->ReleaseByteArrayElements(key, key_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1aes_1192_1keygen(
    JNIEnv* env, jclass clazz, jbyteArray key) {
    (void)clazz;

    // Get input arrays
    jbyte* key_jbyte = env->GetByteArrayElements(key, nullptr);
    uint8_t* key_ptr = reinterpret_cast<uint8_t*>(key_jbyte);

    // Declare output variables

    // Call native function
    int rc = enchant_aes_192_keygen(key_ptr);

    // Release and copy back
    env->ReleaseByteArrayElements(key, key_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1aes_1128_1keygen(
    JNIEnv* env, jclass clazz, jbyteArray key) {
    (void)clazz;

    // Get input arrays
    jbyte* key_jbyte = env->GetByteArrayElements(key, nullptr);
    uint8_t* key_ptr = reinterpret_cast<uint8_t*>(key_jbyte);

    // Declare output variables

    // Call native function
    int rc = enchant_aes_128_keygen(key_ptr);

    // Release and copy back
    env->ReleaseByteArrayElements(key, key_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1aes_1is_1available(
    JNIEnv* env, jclass clazz) {
    (void)clazz;

    // Get input arrays

    // Declare output variables

    // Call native function
    int rc = enchant_aes_is_available();

    // Release and copy back

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1aes_1init(
    JNIEnv* env, jclass clazz) {
    (void)clazz;

    // Get input arrays

    // Declare output variables

    // Call native function
    int rc = enchant_aes_init();

    // Release and copy back

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1aes_1256_1gcm_1encrypt(
    JNIEnv* env, jclass clazz, jbyteArray key, jbyteArray nonce, jbyteArray plaintext, jlong plaintext_len, jbyteArray aad, jlong aad_len, jbyteArray ciphertext, jlongArray ciphertext_len_out) {
    (void)clazz;

    // Get input arrays
    jbyte* key_jbyte = env->GetByteArrayElements(key, nullptr);
    jbyte* nonce_jbyte = env->GetByteArrayElements(nonce, nullptr);
    jbyte* plaintext_jbyte = env->GetByteArrayElements(plaintext, nullptr);
    jbyte* aad_jbyte = env->GetByteArrayElements(aad, nullptr);
    jbyte* ciphertext_jbyte = env->GetByteArrayElements(ciphertext, nullptr);
    uint8_t* ciphertext_ptr = reinterpret_cast<uint8_t*>(ciphertext_jbyte);
    jlong* ciphertext_len_out_elems = env->GetLongArrayElements(ciphertext_len_out, nullptr);

    // Declare output variables
    size_t ciphertext_len_val = 0;

    // Call native function
    int rc = enchant_aes_256_gcm_encrypt(reinterpret_cast<const uint8_t*>(key_jbyte), reinterpret_cast<const uint8_t*>(nonce_jbyte), reinterpret_cast<const uint8_t*>(plaintext_jbyte), plaintext_len, reinterpret_cast<const uint8_t*>(aad_jbyte), aad_len, ciphertext_ptr, &ciphertext_len_val);

    // Release and copy back
    env->ReleaseByteArrayElements(key, key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(nonce, nonce_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(plaintext, plaintext_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(aad, aad_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(ciphertext, ciphertext_jbyte, 0);

    // Copy output values back to Java
    ciphertext_len_out_elems[0] = ciphertext_len_val;
    env->ReleaseLongArrayElements(ciphertext_len_out, ciphertext_len_out_elems, 0);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1aes_1256_1gcm_1decrypt(
    JNIEnv* env, jclass clazz, jbyteArray key, jbyteArray nonce, jbyteArray ciphertext, jlong ciphertext_len, jbyteArray aad, jlong aad_len, jbyteArray plaintext, jlongArray plaintext_len_out) {
    (void)clazz;

    // Get input arrays
    jbyte* key_jbyte = env->GetByteArrayElements(key, nullptr);
    jbyte* nonce_jbyte = env->GetByteArrayElements(nonce, nullptr);
    jbyte* ciphertext_jbyte = env->GetByteArrayElements(ciphertext, nullptr);
    jbyte* aad_jbyte = env->GetByteArrayElements(aad, nullptr);
    jbyte* plaintext_jbyte = env->GetByteArrayElements(plaintext, nullptr);
    uint8_t* plaintext_ptr = reinterpret_cast<uint8_t*>(plaintext_jbyte);
    jlong* plaintext_len_out_elems = env->GetLongArrayElements(plaintext_len_out, nullptr);

    // Declare output variables
    size_t plaintext_len_val = 0;

    // Call native function
    int rc = enchant_aes_256_gcm_decrypt(reinterpret_cast<const uint8_t*>(key_jbyte), reinterpret_cast<const uint8_t*>(nonce_jbyte), reinterpret_cast<const uint8_t*>(ciphertext_jbyte), ciphertext_len, reinterpret_cast<const uint8_t*>(aad_jbyte), aad_len, plaintext_ptr, &plaintext_len_val);

    // Release and copy back
    env->ReleaseByteArrayElements(key, key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(nonce, nonce_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(ciphertext, ciphertext_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(aad, aad_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(plaintext, plaintext_jbyte, 0);

    // Copy output values back to Java
    plaintext_len_out_elems[0] = plaintext_len_val;
    env->ReleaseLongArrayElements(plaintext_len_out, plaintext_len_out_elems, 0);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1aes_1256_1ctr_1encrypt(
    JNIEnv* env, jclass clazz, jbyteArray key, jbyteArray nonce, jbyteArray plaintext, jlong plaintext_len, jbyteArray ciphertext) {
    (void)clazz;

    // Get input arrays
    jbyte* key_jbyte = env->GetByteArrayElements(key, nullptr);
    jbyte* nonce_jbyte = env->GetByteArrayElements(nonce, nullptr);
    jbyte* plaintext_jbyte = env->GetByteArrayElements(plaintext, nullptr);
    jbyte* ciphertext_jbyte = env->GetByteArrayElements(ciphertext, nullptr);
    uint8_t* ciphertext_ptr = reinterpret_cast<uint8_t*>(ciphertext_jbyte);

    // Declare output variables

    // Call native function
    int rc = enchant_aes_256_ctr_encrypt(reinterpret_cast<const uint8_t*>(key_jbyte), reinterpret_cast<const uint8_t*>(nonce_jbyte), reinterpret_cast<const uint8_t*>(plaintext_jbyte), plaintext_len, ciphertext_ptr);

    // Release and copy back
    env->ReleaseByteArrayElements(key, key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(nonce, nonce_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(plaintext, plaintext_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(ciphertext, ciphertext_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1aes_1256_1ctr_1decrypt(
    JNIEnv* env, jclass clazz, jbyteArray key, jbyteArray nonce, jbyteArray ciphertext, jlong ciphertext_len, jbyteArray plaintext) {
    (void)clazz;

    // Get input arrays
    jbyte* key_jbyte = env->GetByteArrayElements(key, nullptr);
    jbyte* nonce_jbyte = env->GetByteArrayElements(nonce, nullptr);
    jbyte* ciphertext_jbyte = env->GetByteArrayElements(ciphertext, nullptr);
    jbyte* plaintext_jbyte = env->GetByteArrayElements(plaintext, nullptr);
    uint8_t* plaintext_ptr = reinterpret_cast<uint8_t*>(plaintext_jbyte);

    // Declare output variables

    // Call native function
    int rc = enchant_aes_256_ctr_decrypt(reinterpret_cast<const uint8_t*>(key_jbyte), reinterpret_cast<const uint8_t*>(nonce_jbyte), reinterpret_cast<const uint8_t*>(ciphertext_jbyte), ciphertext_len, plaintext_ptr);

    // Release and copy back
    env->ReleaseByteArrayElements(key, key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(nonce, nonce_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(ciphertext, ciphertext_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(plaintext, plaintext_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1aes_1256_1cbc_1encrypt(
    JNIEnv* env, jclass clazz, jbyteArray key, jbyteArray iv, jbyteArray plaintext, jlong plaintext_len, jbyteArray ciphertext, jlongArray ciphertext_len_out) {
    (void)clazz;

    // Get input arrays
    jbyte* key_jbyte = env->GetByteArrayElements(key, nullptr);
    jbyte* iv_jbyte = env->GetByteArrayElements(iv, nullptr);
    jbyte* plaintext_jbyte = env->GetByteArrayElements(plaintext, nullptr);
    jbyte* ciphertext_jbyte = env->GetByteArrayElements(ciphertext, nullptr);
    uint8_t* ciphertext_ptr = reinterpret_cast<uint8_t*>(ciphertext_jbyte);
    jlong* ciphertext_len_out_elems = env->GetLongArrayElements(ciphertext_len_out, nullptr);

    // Declare output variables
    size_t ciphertext_len_val = 0;

    // Call native function
    int rc = enchant_aes_256_cbc_encrypt(reinterpret_cast<const uint8_t*>(key_jbyte), reinterpret_cast<const uint8_t*>(iv_jbyte), reinterpret_cast<const uint8_t*>(plaintext_jbyte), plaintext_len, ciphertext_ptr, &ciphertext_len_val);

    // Release and copy back
    env->ReleaseByteArrayElements(key, key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(iv, iv_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(plaintext, plaintext_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(ciphertext, ciphertext_jbyte, 0);

    // Copy output values back to Java
    ciphertext_len_out_elems[0] = ciphertext_len_val;
    env->ReleaseLongArrayElements(ciphertext_len_out, ciphertext_len_out_elems, 0);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1aes_1256_1cbc_1decrypt(
    JNIEnv* env, jclass clazz, jbyteArray key, jbyteArray iv, jbyteArray ciphertext, jlong ciphertext_len, jbyteArray plaintext, jlongArray plaintext_len_out) {
    (void)clazz;

    // Get input arrays
    jbyte* key_jbyte = env->GetByteArrayElements(key, nullptr);
    jbyte* iv_jbyte = env->GetByteArrayElements(iv, nullptr);
    jbyte* ciphertext_jbyte = env->GetByteArrayElements(ciphertext, nullptr);
    jbyte* plaintext_jbyte = env->GetByteArrayElements(plaintext, nullptr);
    uint8_t* plaintext_ptr = reinterpret_cast<uint8_t*>(plaintext_jbyte);
    jlong* plaintext_len_out_elems = env->GetLongArrayElements(plaintext_len_out, nullptr);

    // Declare output variables
    size_t plaintext_len_val = 0;

    // Call native function
    int rc = enchant_aes_256_cbc_decrypt(reinterpret_cast<const uint8_t*>(key_jbyte), reinterpret_cast<const uint8_t*>(iv_jbyte), reinterpret_cast<const uint8_t*>(ciphertext_jbyte), ciphertext_len, plaintext_ptr, &plaintext_len_val);

    // Release and copy back
    env->ReleaseByteArrayElements(key, key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(iv, iv_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(ciphertext, ciphertext_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(plaintext, plaintext_jbyte, 0);

    // Copy output values back to Java
    plaintext_len_out_elems[0] = plaintext_len_val;
    env->ReleaseLongArrayElements(plaintext_len_out, plaintext_len_out_elems, 0);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1aes_1cmac(
    JNIEnv* env, jclass clazz, jbyteArray key, jlong key_len, jbyteArray data, jlong data_len, jbyteArray mac) {
    (void)clazz;

    // Get input arrays
    jbyte* key_jbyte = env->GetByteArrayElements(key, nullptr);
    jbyte* data_jbyte = env->GetByteArrayElements(data, nullptr);
    jbyte* mac_jbyte = env->GetByteArrayElements(mac, nullptr);
    uint8_t* mac_ptr = reinterpret_cast<uint8_t*>(mac_jbyte);

    // Declare output variables

    // Call native function
    int rc = enchant_aes_cmac(reinterpret_cast<const uint8_t*>(key_jbyte), key_len, reinterpret_cast<const uint8_t*>(data_jbyte), data_len, mac_ptr);

    // Release and copy back
    env->ReleaseByteArrayElements(key, key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(data, data_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(mac, mac_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1aes_1256_1siv_1encrypt(
    JNIEnv* env, jclass clazz, jbyteArray key, jbyteArray plaintext, jlong plaintext_len, jbyteArray aad, jlong aad_len, jbyteArray nonce, jlong nonce_len, jbyteArray ciphertext, jlongArray ciphertext_len_out) {
    (void)clazz;

    // Get input arrays
    jbyte* key_jbyte = env->GetByteArrayElements(key, nullptr);
    jbyte* plaintext_jbyte = env->GetByteArrayElements(plaintext, nullptr);
    jbyte* aad_jbyte = env->GetByteArrayElements(aad, nullptr);
    jbyte* nonce_jbyte = env->GetByteArrayElements(nonce, nullptr);
    jbyte* ciphertext_jbyte = env->GetByteArrayElements(ciphertext, nullptr);
    uint8_t* ciphertext_ptr = reinterpret_cast<uint8_t*>(ciphertext_jbyte);
    jlong* ciphertext_len_out_elems = env->GetLongArrayElements(ciphertext_len_out, nullptr);

    // Declare output variables
    size_t ciphertext_len_val = 0;

    // Call native function
    int rc = enchant_aes_256_siv_encrypt(reinterpret_cast<const uint8_t*>(key_jbyte), reinterpret_cast<const uint8_t*>(plaintext_jbyte), plaintext_len, reinterpret_cast<const uint8_t*>(aad_jbyte), aad_len, reinterpret_cast<const uint8_t*>(nonce_jbyte), nonce_len, ciphertext_ptr, &ciphertext_len_val);

    // Release and copy back
    env->ReleaseByteArrayElements(key, key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(plaintext, plaintext_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(aad, aad_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(nonce, nonce_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(ciphertext, ciphertext_jbyte, 0);

    // Copy output values back to Java
    ciphertext_len_out_elems[0] = ciphertext_len_val;
    env->ReleaseLongArrayElements(ciphertext_len_out, ciphertext_len_out_elems, 0);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1aes_1256_1siv_1decrypt(
    JNIEnv* env, jclass clazz, jbyteArray key, jbyteArray ciphertext, jlong ciphertext_len, jbyteArray aad, jlong aad_len, jbyteArray nonce, jlong nonce_len, jbyteArray plaintext, jlongArray plaintext_len_out) {
    (void)clazz;

    // Get input arrays
    jbyte* key_jbyte = env->GetByteArrayElements(key, nullptr);
    jbyte* ciphertext_jbyte = env->GetByteArrayElements(ciphertext, nullptr);
    jbyte* aad_jbyte = env->GetByteArrayElements(aad, nullptr);
    jbyte* nonce_jbyte = env->GetByteArrayElements(nonce, nullptr);
    jbyte* plaintext_jbyte = env->GetByteArrayElements(plaintext, nullptr);
    uint8_t* plaintext_ptr = reinterpret_cast<uint8_t*>(plaintext_jbyte);
    jlong* plaintext_len_out_elems = env->GetLongArrayElements(plaintext_len_out, nullptr);

    // Declare output variables
    size_t plaintext_len_val = 0;

    // Call native function
    int rc = enchant_aes_256_siv_decrypt(reinterpret_cast<const uint8_t*>(key_jbyte), reinterpret_cast<const uint8_t*>(ciphertext_jbyte), ciphertext_len, reinterpret_cast<const uint8_t*>(aad_jbyte), aad_len, reinterpret_cast<const uint8_t*>(nonce_jbyte), nonce_len, plaintext_ptr, &plaintext_len_val);

    // Release and copy back
    env->ReleaseByteArrayElements(key, key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(ciphertext, ciphertext_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(aad, aad_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(nonce, nonce_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(plaintext, plaintext_jbyte, 0);

    // Copy output values back to Java
    plaintext_len_out_elems[0] = plaintext_len_val;
    env->ReleaseLongArrayElements(plaintext_len_out, plaintext_len_out_elems, 0);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1chacha20_1poly1305_1ietf_1encrypt(
    JNIEnv* env, jclass clazz, jbyteArray plaintext, jlong plaintext_len, jbyteArray aad, jlong aad_len, jbyteArray key, jbyteArray nonce, jbyteArray ciphertext, jlongArray ciphertext_len_out) {
    (void)clazz;

    // Get input arrays
    jbyte* plaintext_jbyte = env->GetByteArrayElements(plaintext, nullptr);
    jbyte* aad_jbyte = env->GetByteArrayElements(aad, nullptr);
    jbyte* key_jbyte = env->GetByteArrayElements(key, nullptr);
    jbyte* nonce_jbyte = env->GetByteArrayElements(nonce, nullptr);
    jbyte* ciphertext_jbyte = env->GetByteArrayElements(ciphertext, nullptr);
    uint8_t* ciphertext_ptr = reinterpret_cast<uint8_t*>(ciphertext_jbyte);
    jlong* ciphertext_len_out_elems = env->GetLongArrayElements(ciphertext_len_out, nullptr);

    // Declare output variables
    size_t ciphertext_len_val = 0;

    // Call native function
    int rc = enchant_chacha20_poly1305_ietf_encrypt(reinterpret_cast<const uint8_t*>(plaintext_jbyte), plaintext_len, reinterpret_cast<const uint8_t*>(aad_jbyte), aad_len, reinterpret_cast<const uint8_t*>(key_jbyte), reinterpret_cast<const uint8_t*>(nonce_jbyte), ciphertext_ptr, &ciphertext_len_val);

    // Release and copy back
    env->ReleaseByteArrayElements(plaintext, plaintext_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(aad, aad_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(key, key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(nonce, nonce_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(ciphertext, ciphertext_jbyte, 0);

    // Copy output values back to Java
    ciphertext_len_out_elems[0] = ciphertext_len_val;
    env->ReleaseLongArrayElements(ciphertext_len_out, ciphertext_len_out_elems, 0);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1chacha20_1poly1305_1ietf_1decrypt(
    JNIEnv* env, jclass clazz, jbyteArray ciphertext, jlong ciphertext_len, jbyteArray aad, jlong aad_len, jbyteArray key, jbyteArray nonce, jbyteArray plaintext, jlongArray plaintext_len_out) {
    (void)clazz;

    // Get input arrays
    jbyte* ciphertext_jbyte = env->GetByteArrayElements(ciphertext, nullptr);
    jbyte* aad_jbyte = env->GetByteArrayElements(aad, nullptr);
    jbyte* key_jbyte = env->GetByteArrayElements(key, nullptr);
    jbyte* nonce_jbyte = env->GetByteArrayElements(nonce, nullptr);
    jbyte* plaintext_jbyte = env->GetByteArrayElements(plaintext, nullptr);
    uint8_t* plaintext_ptr = reinterpret_cast<uint8_t*>(plaintext_jbyte);
    jlong* plaintext_len_out_elems = env->GetLongArrayElements(plaintext_len_out, nullptr);

    // Declare output variables
    size_t plaintext_len_val = 0;

    // Call native function
    int rc = enchant_chacha20_poly1305_ietf_decrypt(reinterpret_cast<const uint8_t*>(ciphertext_jbyte), ciphertext_len, reinterpret_cast<const uint8_t*>(aad_jbyte), aad_len, reinterpret_cast<const uint8_t*>(key_jbyte), reinterpret_cast<const uint8_t*>(nonce_jbyte), plaintext_ptr, &plaintext_len_val);

    // Release and copy back
    env->ReleaseByteArrayElements(ciphertext, ciphertext_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(aad, aad_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(key, key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(nonce, nonce_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(plaintext, plaintext_jbyte, 0);

    // Copy output values back to Java
    plaintext_len_out_elems[0] = plaintext_len_val;
    env->ReleaseLongArrayElements(plaintext_len_out, plaintext_len_out_elems, 0);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1hpke_1seal(
    JNIEnv* env, jclass clazz, jbyteArray recipient_public_key, jbyteArray info, jlong info_len, jbyteArray aad, jlong aad_len, jbyteArray plaintext, jlong plaintext_len, jbyteArray ciphertext, jlongArray ciphertext_len_out) {
    (void)clazz;

    // Get input arrays
    jbyte* recipient_public_key_jbyte = env->GetByteArrayElements(recipient_public_key, nullptr);
    jbyte* info_jbyte = env->GetByteArrayElements(info, nullptr);
    jbyte* aad_jbyte = env->GetByteArrayElements(aad, nullptr);
    jbyte* plaintext_jbyte = env->GetByteArrayElements(plaintext, nullptr);
    jbyte* ciphertext_jbyte = env->GetByteArrayElements(ciphertext, nullptr);
    uint8_t* ciphertext_ptr = reinterpret_cast<uint8_t*>(ciphertext_jbyte);
    jlong* ciphertext_len_out_elems = env->GetLongArrayElements(ciphertext_len_out, nullptr);

    // Declare output variables
    size_t ciphertext_len_val = 0;

    // Call native function
    int rc = enchant_hpke_seal(reinterpret_cast<const uint8_t*>(recipient_public_key_jbyte), reinterpret_cast<const uint8_t*>(info_jbyte), info_len, reinterpret_cast<const uint8_t*>(aad_jbyte), aad_len, reinterpret_cast<const uint8_t*>(plaintext_jbyte), plaintext_len, ciphertext_ptr, &ciphertext_len_val);

    // Release and copy back
    env->ReleaseByteArrayElements(recipient_public_key, recipient_public_key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(info, info_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(aad, aad_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(plaintext, plaintext_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(ciphertext, ciphertext_jbyte, 0);

    // Copy output values back to Java
    ciphertext_len_out_elems[0] = ciphertext_len_val;
    env->ReleaseLongArrayElements(ciphertext_len_out, ciphertext_len_out_elems, 0);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1hpke_1open(
    JNIEnv* env, jclass clazz, jbyteArray recipient_private_key, jbyteArray info, jlong info_len, jbyteArray aad, jlong aad_len, jbyteArray ciphertext, jlong ciphertext_len, jbyteArray plaintext, jlongArray plaintext_len_out) {
    (void)clazz;

    // Get input arrays
    jbyte* recipient_private_key_jbyte = env->GetByteArrayElements(recipient_private_key, nullptr);
    jbyte* info_jbyte = env->GetByteArrayElements(info, nullptr);
    jbyte* aad_jbyte = env->GetByteArrayElements(aad, nullptr);
    jbyte* ciphertext_jbyte = env->GetByteArrayElements(ciphertext, nullptr);
    jbyte* plaintext_jbyte = env->GetByteArrayElements(plaintext, nullptr);
    uint8_t* plaintext_ptr = reinterpret_cast<uint8_t*>(plaintext_jbyte);
    jlong* plaintext_len_out_elems = env->GetLongArrayElements(plaintext_len_out, nullptr);

    // Declare output variables
    size_t plaintext_len_val = 0;

    // Call native function
    int rc = enchant_hpke_open(reinterpret_cast<const uint8_t*>(recipient_private_key_jbyte), reinterpret_cast<const uint8_t*>(info_jbyte), info_len, reinterpret_cast<const uint8_t*>(aad_jbyte), aad_len, reinterpret_cast<const uint8_t*>(ciphertext_jbyte), ciphertext_len, plaintext_ptr, &plaintext_len_val);

    // Release and copy back
    env->ReleaseByteArrayElements(recipient_private_key, recipient_private_key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(info, info_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(aad, aad_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(ciphertext, ciphertext_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(plaintext, plaintext_jbyte, 0);

    // Copy output values back to Java
    plaintext_len_out_elems[0] = plaintext_len_val;
    env->ReleaseLongArrayElements(plaintext_len_out, plaintext_len_out_elems, 0);
    return rc;
}

JNIEXPORT void JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1secure_1zero(
    JNIEnv* env, jclass clazz, jlong ptr, jlong len) {
    (void)clazz;

    // Get input arrays

    // Declare output variables

    // Call native function
    enchant_secure_zero((void*)(long)(ptr), len);

    // Release and copy back

    // Copy output values back to Java
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1secure_1alloc(
    JNIEnv* env, jclass clazz, jlongArray ptr, jlong len) {
    (void)clazz;

    // Get input arrays
    jlong* ptr_elems = env->GetLongArrayElements(ptr, nullptr);

    // Declare output variables
    void* ptr_val = nullptr;

    // Call native function
    int rc = enchant_secure_alloc(&ptr_val, len);

    // Release and copy back

    // Copy output values back to Java
    ptr_elems[0] = (jlong)(long)ptr_val;
    env->ReleaseLongArrayElements(ptr, ptr_elems, 0);
    return rc;
}

JNIEXPORT void JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1secure_1free(
    JNIEnv* env, jclass clazz, jlong ptr, jlong len) {
    (void)clazz;

    // Get input arrays

    // Declare output variables

    // Call native function
    enchant_secure_free((void*)(long)(ptr), len);

    // Release and copy back

    // Copy output values back to Java
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1sha384(
    JNIEnv* env, jclass clazz, jbyteArray data, jlong len, jbyteArray hash) {
    (void)clazz;

    // Get input arrays
    jbyte* data_jbyte = env->GetByteArrayElements(data, nullptr);
    jbyte* hash_jbyte = env->GetByteArrayElements(hash, nullptr);
    uint8_t* hash_ptr = reinterpret_cast<uint8_t*>(hash_jbyte);

    // Declare output variables

    // Call native function
    int rc = enchant_sha384(reinterpret_cast<const uint8_t*>(data_jbyte), len, hash_ptr);

    // Release and copy back
    env->ReleaseByteArrayElements(data, data_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(hash, hash_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1sha512(
    JNIEnv* env, jclass clazz, jbyteArray data, jlong len, jbyteArray hash) {
    (void)clazz;

    // Get input arrays
    jbyte* data_jbyte = env->GetByteArrayElements(data, nullptr);
    jbyte* hash_jbyte = env->GetByteArrayElements(hash, nullptr);
    uint8_t* hash_ptr = reinterpret_cast<uint8_t*>(hash_jbyte);

    // Declare output variables

    // Call native function
    int rc = enchant_sha512(reinterpret_cast<const uint8_t*>(data_jbyte), len, hash_ptr);

    // Release and copy back
    env->ReleaseByteArrayElements(data, data_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(hash, hash_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1hmac_1sha512(
    JNIEnv* env, jclass clazz, jbyteArray key, jlong key_len, jbyteArray data, jlong data_len, jbyteArray mac) {
    (void)clazz;

    // Get input arrays
    jbyte* key_jbyte = env->GetByteArrayElements(key, nullptr);
    jbyte* data_jbyte = env->GetByteArrayElements(data, nullptr);
    jbyte* mac_jbyte = env->GetByteArrayElements(mac, nullptr);
    uint8_t* mac_ptr = reinterpret_cast<uint8_t*>(mac_jbyte);

    // Declare output variables

    // Call native function
    int rc = enchant_hmac_sha512(reinterpret_cast<const uint8_t*>(key_jbyte), key_len, reinterpret_cast<const uint8_t*>(data_jbyte), data_len, mac_ptr);

    // Release and copy back
    env->ReleaseByteArrayElements(key, key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(data, data_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(mac, mac_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1constant_1time_1equals(
    JNIEnv* env, jclass clazz, jbyteArray a, jbyteArray b, jlong len, jintArray result) {
    (void)clazz;

    // Get input arrays
    jbyte* a_jbyte = env->GetByteArrayElements(a, nullptr);
    jbyte* b_jbyte = env->GetByteArrayElements(b, nullptr);

    // Declare output variables
    int result_val = 0;

    // Call native function
    int rc = enchant_constant_time_equals(reinterpret_cast<const uint8_t*>(a_jbyte), reinterpret_cast<const uint8_t*>(b_jbyte), len, &result_val);

    // Release and copy back
    env->ReleaseByteArrayElements(a, a_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(b, b_jbyte, JNI_ABORT);

    // Copy output values back to Java
    jint* result_elems = env->GetIntArrayElements(result, nullptr);
    result_elems[0] = result_val;
    env->ReleaseIntArrayElements(result, result_elems, 0);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1ed25519_1sk_1to_1x25519(
    JNIEnv* env, jclass clazz, jbyteArray ed25519_sk, jbyteArray x25519_sk) {
    (void)clazz;

    // Get input arrays
    jbyte* ed25519_sk_jbyte = env->GetByteArrayElements(ed25519_sk, nullptr);
    jbyte* x25519_sk_jbyte = env->GetByteArrayElements(x25519_sk, nullptr);
    uint8_t* x25519_sk_ptr = reinterpret_cast<uint8_t*>(x25519_sk_jbyte);

    // Declare output variables

    // Call native function
    int rc = enchant_ed25519_sk_to_x25519(reinterpret_cast<const uint8_t*>(ed25519_sk_jbyte), x25519_sk_ptr);

    // Release and copy back
    env->ReleaseByteArrayElements(ed25519_sk, ed25519_sk_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(x25519_sk, x25519_sk_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1ed25519_1pk_1to_1x25519(
    JNIEnv* env, jclass clazz, jbyteArray ed25519_pk, jbyteArray x25519_pk) {
    (void)clazz;

    // Get input arrays
    jbyte* ed25519_pk_jbyte = env->GetByteArrayElements(ed25519_pk, nullptr);
    jbyte* x25519_pk_jbyte = env->GetByteArrayElements(x25519_pk, nullptr);
    uint8_t* x25519_pk_ptr = reinterpret_cast<uint8_t*>(x25519_pk_jbyte);

    // Declare output variables

    // Call native function
    int rc = enchant_ed25519_pk_to_x25519(reinterpret_cast<const uint8_t*>(ed25519_pk_jbyte), x25519_pk_ptr);

    // Release and copy back
    env->ReleaseByteArrayElements(ed25519_pk, ed25519_pk_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(x25519_pk, x25519_pk_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1attachment_1encrypt(
    JNIEnv* env, jclass clazz, jbyteArray plaintext, jlong plaintext_len, jbyteArray key, jlong key_len, jbyteArray ciphertext, jlong ciphertext_capacity, jlongArray ciphertext_len_out, jbyteArray mac) {
    (void)clazz;

    // Get input arrays
    jbyte* plaintext_jbyte = env->GetByteArrayElements(plaintext, nullptr);
    jbyte* key_jbyte = env->GetByteArrayElements(key, nullptr);
    jbyte* ciphertext_jbyte = env->GetByteArrayElements(ciphertext, nullptr);
    uint8_t* ciphertext_ptr = reinterpret_cast<uint8_t*>(ciphertext_jbyte);
    jlong* ciphertext_len_out_elems = env->GetLongArrayElements(ciphertext_len_out, nullptr);
    jbyte* mac_jbyte = env->GetByteArrayElements(mac, nullptr);
    uint8_t* mac_ptr = reinterpret_cast<uint8_t*>(mac_jbyte);

    // Declare output variables
    size_t ciphertext_len_val = 0;

    // Call native function
    int rc = enchant_attachment_encrypt(reinterpret_cast<const uint8_t*>(plaintext_jbyte), plaintext_len, reinterpret_cast<const uint8_t*>(key_jbyte), key_len, ciphertext_ptr, ciphertext_capacity, &ciphertext_len_val, mac_ptr);

    // Release and copy back
    env->ReleaseByteArrayElements(plaintext, plaintext_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(key, key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(ciphertext, ciphertext_jbyte, 0);
    env->ReleaseByteArrayElements(mac, mac_jbyte, 0);

    // Copy output values back to Java
    ciphertext_len_out_elems[0] = ciphertext_len_val;
    env->ReleaseLongArrayElements(ciphertext_len_out, ciphertext_len_out_elems, 0);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1attachment_1decrypt(
    JNIEnv* env, jclass clazz, jbyteArray ciphertext, jlong ciphertext_len, jbyteArray key, jlong key_len, jbyteArray mac, jbyteArray plaintext, jlong plaintext_capacity, jlongArray plaintext_len_out) {
    (void)clazz;

    // Get input arrays
    jbyte* ciphertext_jbyte = env->GetByteArrayElements(ciphertext, nullptr);
    jbyte* key_jbyte = env->GetByteArrayElements(key, nullptr);
    jbyte* mac_jbyte = env->GetByteArrayElements(mac, nullptr);
    jbyte* plaintext_jbyte = env->GetByteArrayElements(plaintext, nullptr);
    uint8_t* plaintext_ptr = reinterpret_cast<uint8_t*>(plaintext_jbyte);
    jlong* plaintext_len_out_elems = env->GetLongArrayElements(plaintext_len_out, nullptr);

    // Declare output variables
    size_t plaintext_len_val = 0;

    // Call native function
    int rc = enchant_attachment_decrypt(reinterpret_cast<const uint8_t*>(ciphertext_jbyte), ciphertext_len, reinterpret_cast<const uint8_t*>(key_jbyte), key_len, reinterpret_cast<const uint8_t*>(mac_jbyte), plaintext_ptr, plaintext_capacity, &plaintext_len_val);

    // Release and copy back
    env->ReleaseByteArrayElements(ciphertext, ciphertext_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(key, key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(mac, mac_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(plaintext, plaintext_jbyte, 0);

    // Copy output values back to Java
    plaintext_len_out_elems[0] = plaintext_len_val;
    env->ReleaseLongArrayElements(plaintext_len_out, plaintext_len_out_elems, 0);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1aes_1gcm_1siv_1encrypt(
    JNIEnv* env, jclass clazz, jbyteArray key, jbyteArray nonce, jbyteArray plaintext, jlong plaintext_len, jbyteArray aad, jlong aad_len, jbyteArray ciphertext, jlongArray ciphertext_len_out, jbyteArray tag) {
    (void)clazz;

    // Get input arrays
    jbyte* key_jbyte = env->GetByteArrayElements(key, nullptr);
    jbyte* nonce_jbyte = env->GetByteArrayElements(nonce, nullptr);
    jbyte* plaintext_jbyte = env->GetByteArrayElements(plaintext, nullptr);
    jbyte* aad_jbyte = env->GetByteArrayElements(aad, nullptr);
    jbyte* ciphertext_jbyte = env->GetByteArrayElements(ciphertext, nullptr);
    uint8_t* ciphertext_ptr = reinterpret_cast<uint8_t*>(ciphertext_jbyte);
    jlong* ciphertext_len_out_elems = env->GetLongArrayElements(ciphertext_len_out, nullptr);
    jbyte* tag_jbyte = env->GetByteArrayElements(tag, nullptr);
    uint8_t* tag_ptr = reinterpret_cast<uint8_t*>(tag_jbyte);

    // Declare output variables
    size_t ciphertext_len_val = 0;

    // Call native function
    int rc = enchant_aes_gcm_siv_encrypt(reinterpret_cast<const uint8_t*>(key_jbyte), reinterpret_cast<const uint8_t*>(nonce_jbyte), reinterpret_cast<const uint8_t*>(plaintext_jbyte), plaintext_len, reinterpret_cast<const uint8_t*>(aad_jbyte), aad_len, ciphertext_ptr, &ciphertext_len_val, tag_ptr);

    // Release and copy back
    env->ReleaseByteArrayElements(key, key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(nonce, nonce_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(plaintext, plaintext_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(aad, aad_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(ciphertext, ciphertext_jbyte, 0);
    env->ReleaseByteArrayElements(tag, tag_jbyte, 0);

    // Copy output values back to Java
    ciphertext_len_out_elems[0] = ciphertext_len_val;
    env->ReleaseLongArrayElements(ciphertext_len_out, ciphertext_len_out_elems, 0);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1aes_1gcm_1siv_1decrypt(
    JNIEnv* env, jclass clazz, jbyteArray key, jbyteArray nonce, jbyteArray ciphertext, jlong ciphertext_len, jbyteArray aad, jlong aad_len, jbyteArray tag, jbyteArray plaintext, jlongArray plaintext_len_out) {
    (void)clazz;

    // Get input arrays
    jbyte* key_jbyte = env->GetByteArrayElements(key, nullptr);
    jbyte* nonce_jbyte = env->GetByteArrayElements(nonce, nullptr);
    jbyte* ciphertext_jbyte = env->GetByteArrayElements(ciphertext, nullptr);
    jbyte* aad_jbyte = env->GetByteArrayElements(aad, nullptr);
    jbyte* tag_jbyte = env->GetByteArrayElements(tag, nullptr);
    jbyte* plaintext_jbyte = env->GetByteArrayElements(plaintext, nullptr);
    uint8_t* plaintext_ptr = reinterpret_cast<uint8_t*>(plaintext_jbyte);
    jlong* plaintext_len_out_elems = env->GetLongArrayElements(plaintext_len_out, nullptr);

    // Declare output variables
    size_t plaintext_len_val = 0;

    // Call native function
    int rc = enchant_aes_gcm_siv_decrypt(reinterpret_cast<const uint8_t*>(key_jbyte), reinterpret_cast<const uint8_t*>(nonce_jbyte), reinterpret_cast<const uint8_t*>(ciphertext_jbyte), ciphertext_len, reinterpret_cast<const uint8_t*>(aad_jbyte), aad_len, reinterpret_cast<const uint8_t*>(tag_jbyte), plaintext_ptr, &plaintext_len_val);

    // Release and copy back
    env->ReleaseByteArrayElements(key, key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(nonce, nonce_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(ciphertext, ciphertext_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(aad, aad_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(tag, tag_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(plaintext, plaintext_jbyte, 0);

    // Copy output values back to Java
    plaintext_len_out_elems[0] = plaintext_len_val;
    env->ReleaseLongArrayElements(plaintext_len_out, plaintext_len_out_elems, 0);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1rsa_1generate_1keypair(
    JNIEnv* env, jclass clazz, jint key_size_bits, jbyteArray private_key, jlong private_key_len, jbyteArray public_key, jlong public_key_len) {
    (void)clazz;

    // Get input arrays
    jbyte* private_key_jbyte = env->GetByteArrayElements(private_key, nullptr);
    uint8_t* private_key_ptr = reinterpret_cast<uint8_t*>(private_key_jbyte);
    jbyte* public_key_jbyte = env->GetByteArrayElements(public_key, nullptr);
    uint8_t* public_key_ptr = reinterpret_cast<uint8_t*>(public_key_jbyte);

    // Declare output variables
    size_t private_key_len_val = 0;
    size_t public_key_len_val = 0;

    // Call native function
    int rc = enchant_rsa_generate_keypair(key_size_bits, private_key_ptr, &private_key_len_val, public_key_ptr, &public_key_len_val);

    // Release and copy back
    env->ReleaseByteArrayElements(private_key, private_key_jbyte, 0);
    env->ReleaseByteArrayElements(public_key, public_key_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1x509_1create_1self_1signed(
    JNIEnv* env, jclass clazz, jbyteArray private_key, jlong private_key_len, jbyteArray public_key, jlong public_key_len, jstring subject_name, jlong validity_seconds, jbyteArray cert_der, jlong cert_der_len) {
    (void)clazz;

    // Get input arrays
    jbyte* private_key_jbyte = env->GetByteArrayElements(private_key, nullptr);
    jbyte* public_key_jbyte = env->GetByteArrayElements(public_key, nullptr);
    const char* subject_name_str = env->GetStringUTFChars(subject_name, nullptr);
    jbyte* cert_der_jbyte = env->GetByteArrayElements(cert_der, nullptr);
    uint8_t* cert_der_ptr = reinterpret_cast<uint8_t*>(cert_der_jbyte);

    // Declare output variables
    size_t cert_der_len_val = 0;

    // Call native function
    int rc = enchant_x509_create_self_signed(reinterpret_cast<const uint8_t*>(private_key_jbyte), private_key_len, reinterpret_cast<const uint8_t*>(public_key_jbyte), public_key_len, subject_name_str, validity_seconds, cert_der_ptr, &cert_der_len_val);

    // Release and copy back
    env->ReleaseByteArrayElements(private_key, private_key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(public_key, public_key_jbyte, JNI_ABORT);
    env->ReleaseStringUTFChars(subject_name, subject_name_str);
    env->ReleaseByteArrayElements(cert_der, cert_der_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1x509_1validate(
    JNIEnv* env, jclass clazz, jbyteArray cert_der, jlong cert_der_len, jbyteArray trusted_ca_der, jlong ca_der_len, jintArray valid_out) {
    (void)clazz;

    // Get input arrays
    jbyte* cert_der_jbyte = env->GetByteArrayElements(cert_der, nullptr);
    jbyte* trusted_ca_der_jbyte = env->GetByteArrayElements(trusted_ca_der, nullptr);

    // Declare output variables
    int valid_out_val = 0;

    // Call native function
    int rc = enchant_x509_validate(reinterpret_cast<const uint8_t*>(cert_der_jbyte), cert_der_len, reinterpret_cast<const uint8_t*>(trusted_ca_der_jbyte), ca_der_len, &valid_out_val);

    // Release and copy back
    env->ReleaseByteArrayElements(cert_der, cert_der_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(trusted_ca_der, trusted_ca_der_jbyte, JNI_ABORT);

    // Copy output values back to Java
    jint* valid_out_elems = env->GetIntArrayElements(valid_out, nullptr);
    valid_out_elems[0] = valid_out_val;
    env->ReleaseIntArrayElements(valid_out, valid_out_elems, 0);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1rsa_1wrap_1key(
    JNIEnv* env, jclass clazz, jbyteArray recipient_public_key, jlong key_len, jbyteArray plaintext_key, jlong plaintext_key_len, jbyteArray wrapped_key, jlong wrapped_key_len) {
    (void)clazz;

    // Get input arrays
    jbyte* recipient_public_key_jbyte = env->GetByteArrayElements(recipient_public_key, nullptr);
    jbyte* plaintext_key_jbyte = env->GetByteArrayElements(plaintext_key, nullptr);
    jbyte* wrapped_key_jbyte = env->GetByteArrayElements(wrapped_key, nullptr);
    uint8_t* wrapped_key_ptr = reinterpret_cast<uint8_t*>(wrapped_key_jbyte);

    // Declare output variables
    size_t wrapped_key_len_val = 0;

    // Call native function
    int rc = enchant_rsa_wrap_key(reinterpret_cast<const uint8_t*>(recipient_public_key_jbyte), key_len, reinterpret_cast<const uint8_t*>(plaintext_key_jbyte), plaintext_key_len, wrapped_key_ptr, &wrapped_key_len_val);

    // Release and copy back
    env->ReleaseByteArrayElements(recipient_public_key, recipient_public_key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(plaintext_key, plaintext_key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(wrapped_key, wrapped_key_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1rsa_1unwrap_1key(
    JNIEnv* env, jclass clazz, jbyteArray private_key, jlong key_len, jbyteArray wrapped_key, jlong wrapped_key_len, jbyteArray plaintext_key, jlong plaintext_key_len) {
    (void)clazz;

    // Get input arrays
    jbyte* private_key_jbyte = env->GetByteArrayElements(private_key, nullptr);
    jbyte* wrapped_key_jbyte = env->GetByteArrayElements(wrapped_key, nullptr);
    jbyte* plaintext_key_jbyte = env->GetByteArrayElements(plaintext_key, nullptr);
    uint8_t* plaintext_key_ptr = reinterpret_cast<uint8_t*>(plaintext_key_jbyte);

    // Declare output variables
    size_t plaintext_key_len_val = 0;

    // Call native function
    int rc = enchant_rsa_unwrap_key(reinterpret_cast<const uint8_t*>(private_key_jbyte), key_len, reinterpret_cast<const uint8_t*>(wrapped_key_jbyte), wrapped_key_len, plaintext_key_ptr, &plaintext_key_len_val);

    // Release and copy back
    env->ReleaseByteArrayElements(private_key, private_key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(wrapped_key, wrapped_key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(plaintext_key, plaintext_key_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1device_1transfer_1create(
    JNIEnv* env, jclass clazz, jbyteArray transfer_key, jint source_device_id, jstring source_device_name, jbyteArray key_data, jlong key_data_len, jbyteArray package_out, jlong package_out_len) {
    (void)clazz;

    // Get input arrays
    jbyte* transfer_key_jbyte = env->GetByteArrayElements(transfer_key, nullptr);
    const char* source_device_name_str = env->GetStringUTFChars(source_device_name, nullptr);
    jbyte* key_data_jbyte = env->GetByteArrayElements(key_data, nullptr);
    jbyte* package_out_jbyte = env->GetByteArrayElements(package_out, nullptr);
    uint8_t* package_out_ptr = reinterpret_cast<uint8_t*>(package_out_jbyte);

    // Declare output variables
    size_t package_out_len_val = 0;

    // Call native function
    int rc = enchant_device_transfer_create(reinterpret_cast<const uint8_t*>(transfer_key_jbyte), source_device_id, source_device_name_str, reinterpret_cast<const uint8_t*>(key_data_jbyte), key_data_len, package_out_ptr, &package_out_len_val);

    // Release and copy back
    env->ReleaseByteArrayElements(transfer_key, transfer_key_jbyte, JNI_ABORT);
    env->ReleaseStringUTFChars(source_device_name, source_device_name_str);
    env->ReleaseByteArrayElements(key_data, key_data_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(package_out, package_out_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1device_1transfer_1import(
    JNIEnv* env, jclass clazz, jbyteArray package, jlong package_len, jbyteArray transfer_key, jbyteArray key_data, jlong key_data_len) {
    (void)clazz;

    // Get input arrays
    jbyte* package_jbyte = env->GetByteArrayElements(package, nullptr);
    jbyte* transfer_key_jbyte = env->GetByteArrayElements(transfer_key, nullptr);
    jbyte* key_data_jbyte = env->GetByteArrayElements(key_data, nullptr);
    uint8_t* key_data_ptr = reinterpret_cast<uint8_t*>(key_data_jbyte);

    // Declare output variables
    size_t key_data_len_val = 0;

    // Call native function
    int rc = enchant_device_transfer_import(reinterpret_cast<const uint8_t*>(package_jbyte), package_len, reinterpret_cast<const uint8_t*>(transfer_key_jbyte), key_data_ptr, &key_data_len_val);

    // Release and copy back
    env->ReleaseByteArrayElements(package, package_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(transfer_key, transfer_key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(key_data, key_data_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1device_1transfer_1rsa_1create(
    JNIEnv* env, jclass clazz, jint source_device_id, jstring source_device_name, jbyteArray key_data, jlong key_data_len, jbyteArray recipient_public_key, jlong recipient_key_len, jbyteArray sender_cert_der, jlong sender_cert_len, jbyteArray package_out, jlong package_out_len) {
    (void)clazz;

    // Get input arrays
    const char* source_device_name_str = env->GetStringUTFChars(source_device_name, nullptr);
    jbyte* key_data_jbyte = env->GetByteArrayElements(key_data, nullptr);
    jbyte* recipient_public_key_jbyte = env->GetByteArrayElements(recipient_public_key, nullptr);
    jbyte* sender_cert_der_jbyte = env->GetByteArrayElements(sender_cert_der, nullptr);
    jbyte* package_out_jbyte = env->GetByteArrayElements(package_out, nullptr);
    uint8_t* package_out_ptr = reinterpret_cast<uint8_t*>(package_out_jbyte);

    // Declare output variables
    size_t package_out_len_val = 0;

    // Call native function
    int rc = enchant_device_transfer_rsa_create(source_device_id, source_device_name_str, reinterpret_cast<const uint8_t*>(key_data_jbyte), key_data_len, reinterpret_cast<const uint8_t*>(recipient_public_key_jbyte), recipient_key_len, reinterpret_cast<const uint8_t*>(sender_cert_der_jbyte), sender_cert_len, package_out_ptr, &package_out_len_val);

    // Release and copy back
    env->ReleaseStringUTFChars(source_device_name, source_device_name_str);
    env->ReleaseByteArrayElements(key_data, key_data_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(recipient_public_key, recipient_public_key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(sender_cert_der, sender_cert_der_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(package_out, package_out_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1device_1transfer_1rsa_1import(
    JNIEnv* env, jclass clazz, jbyteArray package, jlong package_len, jbyteArray private_key, jlong private_key_len, jbyteArray trusted_ca_der, jlong ca_der_len, jbyteArray key_data, jlong key_data_len, jintArray attestation_valid) {
    (void)clazz;

    // Get input arrays
    jbyte* package_jbyte = env->GetByteArrayElements(package, nullptr);
    jbyte* private_key_jbyte = env->GetByteArrayElements(private_key, nullptr);
    jbyte* trusted_ca_der_jbyte = env->GetByteArrayElements(trusted_ca_der, nullptr);
    jbyte* key_data_jbyte = env->GetByteArrayElements(key_data, nullptr);
    uint8_t* key_data_ptr = reinterpret_cast<uint8_t*>(key_data_jbyte);

    // Declare output variables
    size_t key_data_len_val = 0;
    int attestation_valid_val = 0;

    // Call native function
    int rc = enchant_device_transfer_rsa_import(reinterpret_cast<const uint8_t*>(package_jbyte), package_len, reinterpret_cast<const uint8_t*>(private_key_jbyte), private_key_len, reinterpret_cast<const uint8_t*>(trusted_ca_der_jbyte), ca_der_len, key_data_ptr, &key_data_len_val, &attestation_valid_val);

    // Release and copy back
    env->ReleaseByteArrayElements(package, package_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(private_key, private_key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(trusted_ca_der, trusted_ca_der_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(key_data, key_data_jbyte, 0);

    // Copy output values back to Java
    jint* attestation_valid_elems = env->GetIntArrayElements(attestation_valid, nullptr);
    attestation_valid_elems[0] = attestation_valid_val;
    env->ReleaseIntArrayElements(attestation_valid, attestation_valid_elems, 0);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1backup_1encrypt_1frame(
    JNIEnv* env, jclass clazz, jbyteArray master_key, jbyteArray plaintext, jlong plaintext_len, jint frame_type, jint frame_number, jbyteArray frame_out, jlongArray frame_out_len_out) {
    (void)clazz;

    // Get input arrays
    jbyte* master_key_jbyte = env->GetByteArrayElements(master_key, nullptr);
    jbyte* plaintext_jbyte = env->GetByteArrayElements(plaintext, nullptr);
    jbyte* frame_out_jbyte = env->GetByteArrayElements(frame_out, nullptr);
    uint8_t* frame_out_ptr = reinterpret_cast<uint8_t*>(frame_out_jbyte);
    jlong* frame_out_len_out_elems = env->GetLongArrayElements(frame_out_len_out, nullptr);

    // Declare output variables
    size_t frame_out_len_val = 0;

    // Call native function
    int rc = enchant_backup_encrypt_frame(reinterpret_cast<const uint8_t*>(master_key_jbyte), reinterpret_cast<const uint8_t*>(plaintext_jbyte), plaintext_len, frame_type, frame_number, frame_out_ptr, &frame_out_len_val);

    // Release and copy back
    env->ReleaseByteArrayElements(master_key, master_key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(plaintext, plaintext_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(frame_out, frame_out_jbyte, 0);

    // Copy output values back to Java
    frame_out_len_out_elems[0] = frame_out_len_val;
    env->ReleaseLongArrayElements(frame_out_len_out, frame_out_len_out_elems, 0);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1backup_1decrypt_1frame(
    JNIEnv* env, jclass clazz, jbyteArray master_key, jbyteArray frame, jlong frame_len, jbyteArray plaintext, jlongArray plaintext_len_out) {
    (void)clazz;

    // Get input arrays
    jbyte* master_key_jbyte = env->GetByteArrayElements(master_key, nullptr);
    jbyte* frame_jbyte = env->GetByteArrayElements(frame, nullptr);
    jbyte* plaintext_jbyte = env->GetByteArrayElements(plaintext, nullptr);
    uint8_t* plaintext_ptr = reinterpret_cast<uint8_t*>(plaintext_jbyte);
    jlong* plaintext_len_out_elems = env->GetLongArrayElements(plaintext_len_out, nullptr);

    // Declare output variables
    size_t plaintext_len_val = 0;

    // Call native function
    int rc = enchant_backup_decrypt_frame(reinterpret_cast<const uint8_t*>(master_key_jbyte), reinterpret_cast<const uint8_t*>(frame_jbyte), frame_len, plaintext_ptr, &plaintext_len_val);

    // Release and copy back
    env->ReleaseByteArrayElements(master_key, master_key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(frame, frame_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(plaintext, plaintext_jbyte, 0);

    // Copy output values back to Java
    plaintext_len_out_elems[0] = plaintext_len_val;
    env->ReleaseLongArrayElements(plaintext_len_out, plaintext_len_out_elems, 0);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1backup_1derive_1key(
    JNIEnv* env, jclass clazz, jbyteArray account_key, jbyteArray backup_key) {
    (void)clazz;

    // Get input arrays
    jbyte* account_key_jbyte = env->GetByteArrayElements(account_key, nullptr);
    jbyte* backup_key_jbyte = env->GetByteArrayElements(backup_key, nullptr);
    uint8_t* backup_key_ptr = reinterpret_cast<uint8_t*>(backup_key_jbyte);

    // Declare output variables

    // Call native function
    int rc = enchant_backup_derive_key(reinterpret_cast<const uint8_t*>(account_key_jbyte), backup_key_ptr);

    // Release and copy back
    env->ReleaseByteArrayElements(account_key, account_key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(backup_key, backup_key_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1backup_1derive_1media_1key(
    JNIEnv* env, jclass clazz, jbyteArray backup_key, jstring media_id, jbyteArray media_key) {
    (void)clazz;

    // Get input arrays
    jbyte* backup_key_jbyte = env->GetByteArrayElements(backup_key, nullptr);
    const char* media_id_str = env->GetStringUTFChars(media_id, nullptr);
    jbyte* media_key_jbyte = env->GetByteArrayElements(media_key, nullptr);
    uint8_t* media_key_ptr = reinterpret_cast<uint8_t*>(media_key_jbyte);

    // Declare output variables

    // Call native function
    int rc = enchant_backup_derive_media_key(reinterpret_cast<const uint8_t*>(backup_key_jbyte), media_id_str, media_key_ptr);

    // Release and copy back
    env->ReleaseByteArrayElements(backup_key, backup_key_jbyte, JNI_ABORT);
    env->ReleaseStringUTFChars(media_id, media_id_str);
    env->ReleaseByteArrayElements(media_key, media_key_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1backup_1create_1transfer(
    JNIEnv* env, jclass clazz, jbyteArray backup_key, jbyteArray target_public_key, jbyteArray package_out, jlong package_out_len) {
    (void)clazz;

    // Get input arrays
    jbyte* backup_key_jbyte = env->GetByteArrayElements(backup_key, nullptr);
    jbyte* target_public_key_jbyte = env->GetByteArrayElements(target_public_key, nullptr);
    jbyte* package_out_jbyte = env->GetByteArrayElements(package_out, nullptr);
    uint8_t* package_out_ptr = reinterpret_cast<uint8_t*>(package_out_jbyte);

    // Declare output variables
    size_t package_out_len_val = 0;

    // Call native function
    int rc = enchant_backup_create_transfer(reinterpret_cast<const uint8_t*>(backup_key_jbyte), reinterpret_cast<const uint8_t*>(target_public_key_jbyte), package_out_ptr, &package_out_len_val);

    // Release and copy back
    env->ReleaseByteArrayElements(backup_key, backup_key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(target_public_key, target_public_key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(package_out, package_out_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1backup_1receive_1transfer(
    JNIEnv* env, jclass clazz, jbyteArray package, jlong package_len, jbyteArray device_private_key, jbyteArray backup_key) {
    (void)clazz;

    // Get input arrays
    jbyte* package_jbyte = env->GetByteArrayElements(package, nullptr);
    jbyte* device_private_key_jbyte = env->GetByteArrayElements(device_private_key, nullptr);
    jbyte* backup_key_jbyte = env->GetByteArrayElements(backup_key, nullptr);
    uint8_t* backup_key_ptr = reinterpret_cast<uint8_t*>(backup_key_jbyte);

    // Declare output variables

    // Call native function
    int rc = enchant_backup_receive_transfer(reinterpret_cast<const uint8_t*>(package_jbyte), package_len, reinterpret_cast<const uint8_t*>(device_private_key_jbyte), backup_key_ptr);

    // Release and copy back
    env->ReleaseByteArrayElements(package, package_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(device_private_key, device_private_key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(backup_key, backup_key_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1sesame_1create_1validator(
    JNIEnv* env, jclass clazz, jlongArray validator_out) {
    (void)clazz;

    // Get input arrays
    jlong* validator_out_elems = env->GetLongArrayElements(validator_out, nullptr);

    // Declare output variables
    void* validator_out_val = nullptr;

    // Call native function
    int rc = enchant_sesame_create_validator(&validator_out_val);

    // Release and copy back

    // Copy output values back to Java
    validator_out_elems[0] = (jlong)(long)validator_out_val;
    env->ReleaseLongArrayElements(validator_out, validator_out_elems, 0);
    return rc;
}

JNIEXPORT void JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1sesame_1destroy_1validator(
    JNIEnv* env, jclass clazz, jlong validator) {
    (void)clazz;

    // Get input arrays

    // Declare output variables

    // Call native function
    enchant_sesame_destroy_validator((void*)(long)(validator));

    // Release and copy back

    // Copy output values back to Java
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1sesame_1add_1trust_1root(
    JNIEnv* env, jclass clazz, jlong validator, jbyteArray trust_root, jlong root_len) {
    (void)clazz;

    // Get input arrays
    jbyte* trust_root_jbyte = env->GetByteArrayElements(trust_root, nullptr);

    // Declare output variables

    // Call native function
    int rc = enchant_sesame_add_trust_root((void*)(long)(validator), reinterpret_cast<const uint8_t*>(trust_root_jbyte), root_len);

    // Release and copy back
    env->ReleaseByteArrayElements(trust_root, trust_root_jbyte, JNI_ABORT);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1sesame_1set_1own_1identity(
    JNIEnv* env, jclass clazz, jlong validator, jstring own_uuid) {
    (void)clazz;

    // Get input arrays
    const char* own_uuid_str = env->GetStringUTFChars(own_uuid, nullptr);

    // Declare output variables

    // Call native function
    int rc = enchant_sesame_set_own_identity((void*)(long)(validator), own_uuid_str);

    // Release and copy back
    env->ReleaseStringUTFChars(own_uuid, own_uuid_str);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1sesame_1validate_1sender(
    JNIEnv* env, jclass clazz, jlong validator, jbyteArray cert_data, jlong cert_len, jlong validation_timestamp, jintArray trust_level_out, jintArray certificate_valid_out, jintArray key_changed_out) {
    (void)clazz;

    // Get input arrays
    jbyte* cert_data_jbyte = env->GetByteArrayElements(cert_data, nullptr);

    // Declare output variables
    int trust_level_out_val = 0;
    int certificate_valid_out_val = 0;
    int key_changed_out_val = 0;

    // Call native function
    int rc = enchant_sesame_validate_sender((void*)(long)(validator), reinterpret_cast<const uint8_t*>(cert_data_jbyte), cert_len, validation_timestamp, &trust_level_out_val, &certificate_valid_out_val, &key_changed_out_val);

    // Release and copy back
    env->ReleaseByteArrayElements(cert_data, cert_data_jbyte, JNI_ABORT);

    // Copy output values back to Java
    jint* trust_level_out_elems = env->GetIntArrayElements(trust_level_out, nullptr);
    trust_level_out_elems[0] = trust_level_out_val;
    env->ReleaseIntArrayElements(trust_level_out, trust_level_out_elems, 0);
    jint* certificate_valid_out_elems = env->GetIntArrayElements(certificate_valid_out, nullptr);
    certificate_valid_out_elems[0] = certificate_valid_out_val;
    env->ReleaseIntArrayElements(certificate_valid_out, certificate_valid_out_elems, 0);
    jint* key_changed_out_elems = env->GetIntArrayElements(key_changed_out, nullptr);
    key_changed_out_elems[0] = key_changed_out_val;
    env->ReleaseIntArrayElements(key_changed_out, key_changed_out_elems, 0);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1sesame_1establish_1trust(
    JNIEnv* env, jclass clazz, jlong validator, jstring sender_uuid, jbyteArray sender_identity_key, jint sender_device_id, jint trust_level, jlong current_timestamp) {
    (void)clazz;

    // Get input arrays
    const char* sender_uuid_str = env->GetStringUTFChars(sender_uuid, nullptr);
    jbyte* sender_identity_key_jbyte = env->GetByteArrayElements(sender_identity_key, nullptr);

    // Declare output variables

    // Call native function
    int rc = enchant_sesame_establish_trust((void*)(long)(validator), sender_uuid_str, reinterpret_cast<const uint8_t*>(sender_identity_key_jbyte), sender_device_id, trust_level, current_timestamp);

    // Release and copy back
    env->ReleaseStringUTFChars(sender_uuid, sender_uuid_str);
    env->ReleaseByteArrayElements(sender_identity_key, sender_identity_key_jbyte, JNI_ABORT);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1sesame_1verify_1identity(
    JNIEnv* env, jclass clazz, jlong validator, jstring sender_uuid, jbyteArray sender_identity_key, jint sender_device_id, jlong current_timestamp, jintArray verified_out) {
    (void)clazz;

    // Get input arrays
    const char* sender_uuid_str = env->GetStringUTFChars(sender_uuid, nullptr);
    jbyte* sender_identity_key_jbyte = env->GetByteArrayElements(sender_identity_key, nullptr);

    // Declare output variables
    int verified_out_val = 0;

    // Call native function
    int rc = enchant_sesame_verify_identity((void*)(long)(validator), sender_uuid_str, reinterpret_cast<const uint8_t*>(sender_identity_key_jbyte), sender_device_id, current_timestamp, &verified_out_val);

    // Release and copy back
    env->ReleaseStringUTFChars(sender_uuid, sender_uuid_str);
    env->ReleaseByteArrayElements(sender_identity_key, sender_identity_key_jbyte, JNI_ABORT);

    // Copy output values back to Java
    jint* verified_out_elems = env->GetIntArrayElements(verified_out, nullptr);
    verified_out_elems[0] = verified_out_val;
    env->ReleaseIntArrayElements(verified_out, verified_out_elems, 0);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1sesame_1compute_1safety_1number(
    JNIEnv* env, jclass clazz, jbyteArray sender_identity_key, jbyteArray recipient_identity_key, jstring sender_uuid, jstring recipient_uuid, jbyteArray safety_number_out) {
    (void)clazz;

    // Get input arrays
    jbyte* sender_identity_key_jbyte = env->GetByteArrayElements(sender_identity_key, nullptr);
    jbyte* recipient_identity_key_jbyte = env->GetByteArrayElements(recipient_identity_key, nullptr);
    const char* sender_uuid_str = env->GetStringUTFChars(sender_uuid, nullptr);
    const char* recipient_uuid_str = env->GetStringUTFChars(recipient_uuid, nullptr);
    jbyte* safety_number_out_jbyte = env->GetByteArrayElements(safety_number_out, nullptr);
    uint8_t* safety_number_out_ptr = reinterpret_cast<uint8_t*>(safety_number_out_jbyte);

    // Declare output variables

    // Call native function
    int rc = enchant_sesame_compute_safety_number(reinterpret_cast<const uint8_t*>(sender_identity_key_jbyte), reinterpret_cast<const uint8_t*>(recipient_identity_key_jbyte), sender_uuid_str, recipient_uuid_str, safety_number_out_ptr);

    // Release and copy back
    env->ReleaseByteArrayElements(sender_identity_key, sender_identity_key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(recipient_identity_key, recipient_identity_key_jbyte, JNI_ABORT);
    env->ReleaseStringUTFChars(sender_uuid, sender_uuid_str);
    env->ReleaseStringUTFChars(recipient_uuid, recipient_uuid_str);
    env->ReleaseByteArrayElements(safety_number_out, safety_number_out_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1sesame_1generate_1trust_1token(
    JNIEnv* env, jclass clazz, jbyteArray identity_seed, jbyteArray sender_identity_key, jstring sender_uuid, jlong expiration, jbyteArray token_out, jlong token_out_len) {
    (void)clazz;

    // Get input arrays
    jbyte* identity_seed_jbyte = env->GetByteArrayElements(identity_seed, nullptr);
    jbyte* sender_identity_key_jbyte = env->GetByteArrayElements(sender_identity_key, nullptr);
    const char* sender_uuid_str = env->GetStringUTFChars(sender_uuid, nullptr);
    jbyte* token_out_jbyte = env->GetByteArrayElements(token_out, nullptr);
    uint8_t* token_out_ptr = reinterpret_cast<uint8_t*>(token_out_jbyte);

    // Declare output variables
    size_t token_out_len_val = 0;

    // Call native function
    int rc = enchant_sesame_generate_trust_token(reinterpret_cast<const uint8_t*>(identity_seed_jbyte), reinterpret_cast<const uint8_t*>(sender_identity_key_jbyte), sender_uuid_str, expiration, token_out_ptr, &token_out_len_val);

    // Release and copy back
    env->ReleaseByteArrayElements(identity_seed, identity_seed_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(sender_identity_key, sender_identity_key_jbyte, JNI_ABORT);
    env->ReleaseStringUTFChars(sender_uuid, sender_uuid_str);
    env->ReleaseByteArrayElements(token_out, token_out_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1sesame_1verify_1trust_1token(
    JNIEnv* env, jclass clazz, jbyteArray identity_public_key, jbyteArray token, jlong token_len, jstring expected_sender_uuid, jlong validation_time, jintArray valid_out) {
    (void)clazz;

    // Get input arrays
    jbyte* identity_public_key_jbyte = env->GetByteArrayElements(identity_public_key, nullptr);
    jbyte* token_jbyte = env->GetByteArrayElements(token, nullptr);
    const char* expected_sender_uuid_str = env->GetStringUTFChars(expected_sender_uuid, nullptr);

    // Declare output variables
    int valid_out_val = 0;

    // Call native function
    int rc = enchant_sesame_verify_trust_token(reinterpret_cast<const uint8_t*>(identity_public_key_jbyte), reinterpret_cast<const uint8_t*>(token_jbyte), token_len, expected_sender_uuid_str, validation_time, &valid_out_val);

    // Release and copy back
    env->ReleaseByteArrayElements(identity_public_key, identity_public_key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(token, token_jbyte, JNI_ABORT);
    env->ReleaseStringUTFChars(expected_sender_uuid, expected_sender_uuid_str);

    // Copy output values back to Java
    jint* valid_out_elems = env->GetIntArrayElements(valid_out, nullptr);
    valid_out_elems[0] = valid_out_val;
    env->ReleaseIntArrayElements(valid_out, valid_out_elems, 0);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1sesame_1add_1revoked_1server_1key(
    JNIEnv* env, jclass clazz, jlong validator, jint key_id) {
    (void)clazz;

    // Get input arrays

    // Declare output variables

    // Call native function
    int rc = enchant_sesame_add_revoked_server_key((void*)(long)(validator), key_id);

    // Release and copy back

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1sesame_1get_1aggregated_1trust(
    JNIEnv* env, jclass clazz, jlong validator, jstring sender_uuid, jintArray trust_level_out) {
    (void)clazz;

    // Get input arrays
    const char* sender_uuid_str = env->GetStringUTFChars(sender_uuid, nullptr);

    // Declare output variables
    int trust_level_out_val = 0;

    // Call native function
    int rc = enchant_sesame_get_aggregated_trust((void*)(long)(validator), sender_uuid_str, &trust_level_out_val);

    // Release and copy back
    env->ReleaseStringUTFChars(sender_uuid, sender_uuid_str);

    // Copy output values back to Java
    jint* trust_level_out_elems = env->GetIntArrayElements(trust_level_out, nullptr);
    trust_level_out_elems[0] = trust_level_out_val;
    env->ReleaseIntArrayElements(trust_level_out, trust_level_out_elems, 0);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1group_1send_1endorsement_1keygen(
    JNIEnv* env, jclass clazz, jbyteArray private_key, jbyteArray public_key) {
    (void)clazz;

    // Get input arrays
    jbyte* private_key_jbyte = env->GetByteArrayElements(private_key, nullptr);
    uint8_t* private_key_ptr = reinterpret_cast<uint8_t*>(private_key_jbyte);
    jbyte* public_key_jbyte = env->GetByteArrayElements(public_key, nullptr);
    uint8_t* public_key_ptr = reinterpret_cast<uint8_t*>(public_key_jbyte);

    // Declare output variables

    // Call native function
    int rc = enchant_group_send_endorsement_keygen(private_key_ptr, public_key_ptr);

    // Release and copy back
    env->ReleaseByteArrayElements(private_key, private_key_jbyte, 0);
    env->ReleaseByteArrayElements(public_key, public_key_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1group_1send_1endorsement_1sign(
    JNIEnv* env, jclass clazz, jbyteArray private_key, jbyteArray group_id, jbyteArray endorsement_out, jlong endorsement_len) {
    (void)clazz;

    // Get input arrays
    jbyte* private_key_jbyte = env->GetByteArrayElements(private_key, nullptr);
    jbyte* group_id_jbyte = env->GetByteArrayElements(group_id, nullptr);
    jbyte* endorsement_out_jbyte = env->GetByteArrayElements(endorsement_out, nullptr);
    uint8_t* endorsement_out_ptr = reinterpret_cast<uint8_t*>(endorsement_out_jbyte);

    // Declare output variables
    size_t endorsement_len_val = 0;

    // Call native function
    int rc = enchant_group_send_endorsement_sign(reinterpret_cast<const uint8_t*>(private_key_jbyte), reinterpret_cast<const uint8_t*>(group_id_jbyte), endorsement_out_ptr, &endorsement_len_val);

    // Release and copy back
    env->ReleaseByteArrayElements(private_key, private_key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(group_id, group_id_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(endorsement_out, endorsement_out_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1group_1send_1endorsement_1verify(
    JNIEnv* env, jclass clazz, jbyteArray public_key, jbyteArray endorsement, jlong endorsement_len, jbyteArray group_id, jintArray valid_out) {
    (void)clazz;

    // Get input arrays
    jbyte* public_key_jbyte = env->GetByteArrayElements(public_key, nullptr);
    jbyte* endorsement_jbyte = env->GetByteArrayElements(endorsement, nullptr);
    jbyte* group_id_jbyte = env->GetByteArrayElements(group_id, nullptr);

    // Declare output variables
    int valid_out_val = 0;

    // Call native function
    int rc = enchant_group_send_endorsement_verify(reinterpret_cast<const uint8_t*>(public_key_jbyte), reinterpret_cast<const uint8_t*>(endorsement_jbyte), endorsement_len, reinterpret_cast<const uint8_t*>(group_id_jbyte), &valid_out_val);

    // Release and copy back
    env->ReleaseByteArrayElements(public_key, public_key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(endorsement, endorsement_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(group_id, group_id_jbyte, JNI_ABORT);

    // Copy output values back to Java
    jint* valid_out_elems = env->GetIntArrayElements(valid_out, nullptr);
    valid_out_elems[0] = valid_out_val;
    env->ReleaseIntArrayElements(valid_out, valid_out_elems, 0);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1call_1link_1params_1generate(
    JNIEnv* env, jclass clazz, jbyteArray secret_params, jbyteArray public_params) {
    (void)clazz;

    // Get input arrays
    jbyte* secret_params_jbyte = env->GetByteArrayElements(secret_params, nullptr);
    uint8_t* secret_params_ptr = reinterpret_cast<uint8_t*>(secret_params_jbyte);
    jbyte* public_params_jbyte = env->GetByteArrayElements(public_params, nullptr);
    uint8_t* public_params_ptr = reinterpret_cast<uint8_t*>(public_params_jbyte);

    // Declare output variables

    // Call native function
    int rc = enchant_call_link_params_generate(secret_params_ptr, public_params_ptr);

    // Release and copy back
    env->ReleaseByteArrayElements(secret_params, secret_params_jbyte, 0);
    env->ReleaseByteArrayElements(public_params, public_params_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1call_1link_1credential_1issue(
    JNIEnv* env, jclass clazz, jbyteArray secret_params, jbyteArray group_id, jbyteArray credential_out, jlong credential_len) {
    (void)clazz;

    // Get input arrays
    jbyte* secret_params_jbyte = env->GetByteArrayElements(secret_params, nullptr);
    jbyte* group_id_jbyte = env->GetByteArrayElements(group_id, nullptr);
    jbyte* credential_out_jbyte = env->GetByteArrayElements(credential_out, nullptr);
    uint8_t* credential_out_ptr = reinterpret_cast<uint8_t*>(credential_out_jbyte);

    // Declare output variables
    size_t credential_len_val = 0;

    // Call native function
    int rc = enchant_call_link_credential_issue(reinterpret_cast<const uint8_t*>(secret_params_jbyte), reinterpret_cast<const uint8_t*>(group_id_jbyte), credential_out_ptr, &credential_len_val);

    // Release and copy back
    env->ReleaseByteArrayElements(secret_params, secret_params_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(group_id, group_id_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(credential_out, credential_out_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1call_1link_1credential_1present(
    JNIEnv* env, jclass clazz, jbyteArray secret_params, jbyteArray credential, jlong credential_len, jbyteArray group_id, jbyteArray presentation_out, jlong presentation_len) {
    (void)clazz;

    // Get input arrays
    jbyte* secret_params_jbyte = env->GetByteArrayElements(secret_params, nullptr);
    jbyte* credential_jbyte = env->GetByteArrayElements(credential, nullptr);
    jbyte* group_id_jbyte = env->GetByteArrayElements(group_id, nullptr);
    jbyte* presentation_out_jbyte = env->GetByteArrayElements(presentation_out, nullptr);
    uint8_t* presentation_out_ptr = reinterpret_cast<uint8_t*>(presentation_out_jbyte);

    // Declare output variables
    size_t presentation_len_val = 0;

    // Call native function
    int rc = enchant_call_link_credential_present(reinterpret_cast<const uint8_t*>(secret_params_jbyte), reinterpret_cast<const uint8_t*>(credential_jbyte), credential_len, reinterpret_cast<const uint8_t*>(group_id_jbyte), presentation_out_ptr, &presentation_len_val);

    // Release and copy back
    env->ReleaseByteArrayElements(secret_params, secret_params_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(credential, credential_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(group_id, group_id_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(presentation_out, presentation_out_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1call_1link_1credential_1verify(
    JNIEnv* env, jclass clazz, jbyteArray public_params, jbyteArray group_id, jbyteArray presentation, jlong presentation_len, jintArray valid_out) {
    (void)clazz;

    // Get input arrays
    jbyte* public_params_jbyte = env->GetByteArrayElements(public_params, nullptr);
    jbyte* group_id_jbyte = env->GetByteArrayElements(group_id, nullptr);
    jbyte* presentation_jbyte = env->GetByteArrayElements(presentation, nullptr);

    // Declare output variables
    int valid_out_val = 0;

    // Call native function
    int rc = enchant_call_link_credential_verify(reinterpret_cast<const uint8_t*>(public_params_jbyte), reinterpret_cast<const uint8_t*>(group_id_jbyte), reinterpret_cast<const uint8_t*>(presentation_jbyte), presentation_len, &valid_out_val);

    // Release and copy back
    env->ReleaseByteArrayElements(public_params, public_params_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(group_id, group_id_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(presentation, presentation_jbyte, JNI_ABORT);

    // Copy output values back to Java
    jint* valid_out_elems = env->GetIntArrayElements(valid_out, nullptr);
    valid_out_elems[0] = valid_out_val;
    env->ReleaseIntArrayElements(valid_out, valid_out_elems, 0);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1x25519_1keygen(
    JNIEnv* env, jclass clazz, jbyteArray private_key, jbyteArray public_key) {
    (void)clazz;

    // Get input arrays
    jbyte* private_key_jbyte = env->GetByteArrayElements(private_key, nullptr);
    uint8_t* private_key_ptr = reinterpret_cast<uint8_t*>(private_key_jbyte);
    jbyte* public_key_jbyte = env->GetByteArrayElements(public_key, nullptr);
    uint8_t* public_key_ptr = reinterpret_cast<uint8_t*>(public_key_jbyte);

    // Declare output variables

    // Call native function
    int rc = enchant_x25519_keygen(private_key_ptr, public_key_ptr);

    // Release and copy back
    env->ReleaseByteArrayElements(private_key, private_key_jbyte, 0);
    env->ReleaseByteArrayElements(public_key, public_key_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1x25519_1dh_1pairwise(
    JNIEnv* env, jclass clazz, jbyteArray private_key_1, jbyteArray public_key_1, jbyteArray private_key_2, jbyteArray public_key_2, jbyteArray shared_secret_1_out, jbyteArray shared_secret_2_out) {
    (void)clazz;

    // Get input arrays
    jbyte* private_key_1_jbyte = env->GetByteArrayElements(private_key_1, nullptr);
    jbyte* public_key_1_jbyte = env->GetByteArrayElements(public_key_1, nullptr);
    jbyte* private_key_2_jbyte = env->GetByteArrayElements(private_key_2, nullptr);
    jbyte* public_key_2_jbyte = env->GetByteArrayElements(public_key_2, nullptr);
    jbyte* shared_secret_1_out_jbyte = env->GetByteArrayElements(shared_secret_1_out, nullptr);
    uint8_t* shared_secret_1_out_ptr = reinterpret_cast<uint8_t*>(shared_secret_1_out_jbyte);
    jbyte* shared_secret_2_out_jbyte = env->GetByteArrayElements(shared_secret_2_out, nullptr);
    uint8_t* shared_secret_2_out_ptr = reinterpret_cast<uint8_t*>(shared_secret_2_out_jbyte);

    // Declare output variables

    // Call native function
    int rc = enchant_x25519_dh_pairwise(reinterpret_cast<const uint8_t*>(private_key_1_jbyte), reinterpret_cast<const uint8_t*>(public_key_1_jbyte), reinterpret_cast<const uint8_t*>(private_key_2_jbyte), reinterpret_cast<const uint8_t*>(public_key_2_jbyte), shared_secret_1_out_ptr, shared_secret_2_out_ptr);

    // Release and copy back
    env->ReleaseByteArrayElements(private_key_1, private_key_1_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(public_key_1, public_key_1_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(private_key_2, private_key_2_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(public_key_2, public_key_2_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(shared_secret_1_out, shared_secret_1_out_jbyte, 0);
    env->ReleaseByteArrayElements(shared_secret_2_out, shared_secret_2_out_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1blind_1keygen(
    JNIEnv* env, jclass clazz, jbyteArray private_key, jbyteArray public_key) {
    (void)clazz;

    // Get input arrays
    jbyte* private_key_jbyte = env->GetByteArrayElements(private_key, nullptr);
    uint8_t* private_key_ptr = reinterpret_cast<uint8_t*>(private_key_jbyte);
    jbyte* public_key_jbyte = env->GetByteArrayElements(public_key, nullptr);
    uint8_t* public_key_ptr = reinterpret_cast<uint8_t*>(public_key_jbyte);

    // Declare output variables

    // Call native function
    int rc = enchant_blind_keygen(private_key_ptr, public_key_ptr);

    // Release and copy back
    env->ReleaseByteArrayElements(private_key, private_key_jbyte, 0);
    env->ReleaseByteArrayElements(public_key, public_key_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1blind_1point(
    JNIEnv* env, jclass clazz, jbyteArray private_key, jbyteArray point, jbyteArray blinded_out) {
    (void)clazz;

    // Get input arrays
    jbyte* private_key_jbyte = env->GetByteArrayElements(private_key, nullptr);
    jbyte* point_jbyte = env->GetByteArrayElements(point, nullptr);
    jbyte* blinded_out_jbyte = env->GetByteArrayElements(blinded_out, nullptr);
    uint8_t* blinded_out_ptr = reinterpret_cast<uint8_t*>(blinded_out_jbyte);

    // Declare output variables

    // Call native function
    int rc = enchant_blind_point(reinterpret_cast<const uint8_t*>(private_key_jbyte), reinterpret_cast<const uint8_t*>(point_jbyte), blinded_out_ptr);

    // Release and copy back
    env->ReleaseByteArrayElements(private_key, private_key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(point, point_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(blinded_out, blinded_out_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1unblind_1point(
    JNIEnv* env, jclass clazz, jbyteArray private_key, jbyteArray blinded_point, jbyteArray unblinded_out) {
    (void)clazz;

    // Get input arrays
    jbyte* private_key_jbyte = env->GetByteArrayElements(private_key, nullptr);
    jbyte* blinded_point_jbyte = env->GetByteArrayElements(blinded_point, nullptr);
    jbyte* unblinded_out_jbyte = env->GetByteArrayElements(unblinded_out, nullptr);
    uint8_t* unblinded_out_ptr = reinterpret_cast<uint8_t*>(unblinded_out_jbyte);

    // Declare output variables

    // Call native function
    int rc = enchant_unblind_point(reinterpret_cast<const uint8_t*>(private_key_jbyte), reinterpret_cast<const uint8_t*>(blinded_point_jbyte), unblinded_out_ptr);

    // Release and copy back
    env->ReleaseByteArrayElements(private_key, private_key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(blinded_point, blinded_point_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(unblinded_out, unblinded_out_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1poksho_1statement_1add(
    JNIEnv* env, jclass clazz, jlongArray statement_out, jstring name, jbyteArray group_element, jlong group_element_len) {
    (void)clazz;

    // Get input arrays
    jlong* statement_out_elems = env->GetLongArrayElements(statement_out, nullptr);
    const char* name_str = env->GetStringUTFChars(name, nullptr);
    jbyte* group_element_jbyte = env->GetByteArrayElements(group_element, nullptr);

    // Declare output variables
    void* statement_out_val = nullptr;

    // Call native function
    int rc = enchant_poksho_statement_add(&statement_out_val, name_str, reinterpret_cast<const uint8_t*>(group_element_jbyte), group_element_len);

    // Release and copy back
    env->ReleaseStringUTFChars(name, name_str);
    env->ReleaseByteArrayElements(group_element, group_element_jbyte, JNI_ABORT);

    // Copy output values back to Java
    statement_out_elems[0] = (jlong)(long)statement_out_val;
    env->ReleaseLongArrayElements(statement_out, statement_out_elems, 0);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1poksho_1statement_1add_1scalar(
    JNIEnv* env, jclass clazz, jlong statement, jstring name, jbyteArray scalar, jlong scalar_len) {
    (void)clazz;

    // Get input arrays
    const char* name_str = env->GetStringUTFChars(name, nullptr);
    jbyte* scalar_jbyte = env->GetByteArrayElements(scalar, nullptr);

    // Declare output variables

    // Call native function
    int rc = enchant_poksho_statement_add_scalar((void*)(long)(statement), name_str, reinterpret_cast<const uint8_t*>(scalar_jbyte), scalar_len);

    // Release and copy back
    env->ReleaseStringUTFChars(name, name_str);
    env->ReleaseByteArrayElements(scalar, scalar_jbyte, JNI_ABORT);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1poksho_1prove(
    JNIEnv* env, jclass clazz, jlong statement, jbyteArray witness, jlong witness_len, jbyteArray msg, jlong msg_len, jbyteArray proof_out, jlong proof_len) {
    (void)clazz;

    // Get input arrays
    jbyte* witness_jbyte = env->GetByteArrayElements(witness, nullptr);
    jbyte* msg_jbyte = env->GetByteArrayElements(msg, nullptr);
    jbyte* proof_out_jbyte = env->GetByteArrayElements(proof_out, nullptr);
    uint8_t* proof_out_ptr = reinterpret_cast<uint8_t*>(proof_out_jbyte);

    // Declare output variables
    size_t proof_len_val = 0;

    // Call native function
    int rc = enchant_poksho_prove((void*)(long)(statement), reinterpret_cast<const uint8_t*>(witness_jbyte), witness_len, reinterpret_cast<const uint8_t*>(msg_jbyte), msg_len, proof_out_ptr, &proof_len_val);

    // Release and copy back
    env->ReleaseByteArrayElements(witness, witness_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(msg, msg_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(proof_out, proof_out_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1poksho_1verify(
    JNIEnv* env, jclass clazz, jlong statement, jbyteArray proof, jlong proof_len, jbyteArray msg, jlong msg_len, jintArray valid_out) {
    (void)clazz;

    // Get input arrays
    jbyte* proof_jbyte = env->GetByteArrayElements(proof, nullptr);
    jbyte* msg_jbyte = env->GetByteArrayElements(msg, nullptr);

    // Declare output variables
    int valid_out_val = 0;

    // Call native function
    int rc = enchant_poksho_verify((void*)(long)(statement), reinterpret_cast<const uint8_t*>(proof_jbyte), proof_len, reinterpret_cast<const uint8_t*>(msg_jbyte), msg_len, &valid_out_val);

    // Release and copy back
    env->ReleaseByteArrayElements(proof, proof_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(msg, msg_jbyte, JNI_ABORT);

    // Copy output values back to Java
    jint* valid_out_elems = env->GetIntArrayElements(valid_out, nullptr);
    valid_out_elems[0] = valid_out_val;
    env->ReleaseIntArrayElements(valid_out, valid_out_elems, 0);
    return rc;
}

JNIEXPORT void JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1poksho_1statement_1destroy(
    JNIEnv* env, jclass clazz, jlong statement) {
    (void)clazz;

    // Get input arrays

    // Declare output variables

    // Call native function
    enchant_poksho_statement_destroy((void*)(long)(statement));

    // Release and copy back

    // Copy output values back to Java
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1identity_1store_1create(
    JNIEnv* env, jclass clazz, jlongArray store_out) {
    (void)clazz;

    // Get input arrays
    jlong* store_out_elems = env->GetLongArrayElements(store_out, nullptr);
    enchant_identity_store_t* store_out_val = nullptr;

    // Declare output variables

    // Call native function
    int rc = enchant_identity_store_create(&store_out_val);

    // Release and copy back

    // Copy output values back to Java
    store_out_elems[0] = (jlong)(long)store_out_val;
    env->ReleaseLongArrayElements(store_out, store_out_elems, 0);
    return rc;
}

JNIEXPORT void JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1identity_1store_1destroy(
    JNIEnv* env, jclass clazz, jlong store) {
    (void)clazz;

    // Get input arrays

    // Declare output variables

    // Call native function
    enchant_identity_store_destroy((enchant_identity_store_t*)(long)(store));

    // Release and copy back

    // Copy output values back to Java
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1session_1store_1create(
    JNIEnv* env, jclass clazz, jlongArray store_out) {
    (void)clazz;

    // Get input arrays
    jlong* store_out_elems = env->GetLongArrayElements(store_out, nullptr);
    enchant_session_store_t* store_out_val = nullptr;

    // Declare output variables

    // Call native function
    int rc = enchant_session_store_create(&store_out_val);

    // Release and copy back

    // Copy output values back to Java
    store_out_elems[0] = (jlong)(long)store_out_val;
    env->ReleaseLongArrayElements(store_out, store_out_elems, 0);
    return rc;
}

JNIEXPORT void JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1session_1store_1destroy(
    JNIEnv* env, jclass clazz, jlong store) {
    (void)clazz;

    // Get input arrays

    // Declare output variables

    // Call native function
    enchant_session_store_destroy((enchant_session_store_t*)(long)(store));

    // Release and copy back

    // Copy output values back to Java
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1identity_1store_1get_1key_1pair(
    JNIEnv* env, jclass clazz, jlong store, jbyteArray public_key, jbyteArray private_key) {
    (void)clazz;

    // Get input arrays
    jbyte* public_key_jbyte = env->GetByteArrayElements(public_key, nullptr);
    uint8_t* public_key_ptr = reinterpret_cast<uint8_t*>(public_key_jbyte);
    jbyte* private_key_jbyte = env->GetByteArrayElements(private_key, nullptr);
    uint8_t* private_key_ptr = reinterpret_cast<uint8_t*>(private_key_jbyte);

    // Declare output variables

    // Call native function
    int rc = enchant_identity_store_get_key_pair((enchant_identity_store_t*)(long)(store), public_key_ptr, private_key_ptr);

    // Release and copy back
    env->ReleaseByteArrayElements(public_key, public_key_jbyte, 0);
    env->ReleaseByteArrayElements(private_key, private_key_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1identity_1store_1set_1registration_1id(
    JNIEnv* env, jclass clazz, jlong store, jint registration_id) {
    (void)clazz;

    // Get input arrays

    // Declare output variables

    // Call native function
    int rc = enchant_identity_store_set_registration_id((enchant_identity_store_t*)(long)(store), registration_id);

    // Release and copy back

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1identity_1store_1get_1registration_1id(
    JNIEnv* env, jclass clazz, jlong store, jintArray registration_id_out) {
    (void)clazz;

    // Get input arrays

    // Declare output variables
    uint32_t registration_id_out_val = 0;

    // Call native function
    int rc = enchant_identity_store_get_registration_id((enchant_identity_store_t*)(long)(store), &registration_id_out_val);

    // Release and copy back

    // Copy output values back to Java
    jint* registration_id_out_elems = env->GetIntArrayElements(registration_id_out, nullptr);
    registration_id_out_elems[0] = registration_id_out_val;
    env->ReleaseIntArrayElements(registration_id_out, registration_id_out_elems, 0);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1identity_1store_1save_1identity(
    JNIEnv* env, jclass clazz, jlong store, jstring address_name, jint device_id, jbyteArray identity_key) {
    (void)clazz;

    // Get input arrays
    const char* address_name_str = env->GetStringUTFChars(address_name, nullptr);
    jbyte* identity_key_jbyte = env->GetByteArrayElements(identity_key, nullptr);

    // Declare output variables

    // Call native function
    int rc = enchant_identity_store_save_identity((enchant_identity_store_t*)(long)(store), address_name_str, device_id, reinterpret_cast<const uint8_t*>(identity_key_jbyte));

    // Release and copy back
    env->ReleaseStringUTFChars(address_name, address_name_str);
    env->ReleaseByteArrayElements(identity_key, identity_key_jbyte, JNI_ABORT);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1identity_1store_1is_1trusted(
    JNIEnv* env, jclass clazz, jlong store, jstring address_name, jint device_id, jbyteArray identity_key, jint direction, jintArray trusted_out) {
    (void)clazz;

    // Get input arrays
    const char* address_name_str = env->GetStringUTFChars(address_name, nullptr);
    jbyte* identity_key_jbyte = env->GetByteArrayElements(identity_key, nullptr);

    // Declare output variables
    int trusted_out_val = 0;

    // Call native function
    int rc = enchant_identity_store_is_trusted((enchant_identity_store_t*)(long)(store), address_name_str, device_id, reinterpret_cast<const uint8_t*>(identity_key_jbyte), direction, &trusted_out_val);

    // Release and copy back
    env->ReleaseStringUTFChars(address_name, address_name_str);
    env->ReleaseByteArrayElements(identity_key, identity_key_jbyte, JNI_ABORT);

    // Copy output values back to Java
    jint* trusted_out_elems = env->GetIntArrayElements(trusted_out, nullptr);
    trusted_out_elems[0] = trusted_out_val;
    env->ReleaseIntArrayElements(trusted_out, trusted_out_elems, 0);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1identity_1store_1set_1trust(
    JNIEnv* env, jclass clazz, jlong store, jstring address_name, jint device_id, jint trusted) {
    (void)clazz;

    // Get input arrays
    const char* address_name_str = env->GetStringUTFChars(address_name, nullptr);

    // Declare output variables

    // Call native function
    int rc = enchant_identity_store_set_trust((enchant_identity_store_t*)(long)(store), address_name_str, device_id, trusted);

    // Release and copy back
    env->ReleaseStringUTFChars(address_name, address_name_str);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1identity_1store_1store_1signed_1prekey(
    JNIEnv* env, jclass clazz, jlong store, jint prekey_id, jbyteArray private_key, jlong key_len) {
    (void)clazz;
    jbyte* pk = env->GetByteArrayElements(private_key, nullptr);
    int rc = enchant_identity_store_store_signed_prekey(
        reinterpret_cast<enchant_identity_store_t*>(store),
        static_cast<uint32_t>(prekey_id),
        reinterpret_cast<const uint8_t*>(pk), static_cast<size_t>(key_len));
    env->ReleaseByteArrayElements(private_key, pk, JNI_ABORT);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1identity_1store_1store_1one_1time_1prekey(
    JNIEnv* env, jclass clazz, jlong store, jint prekey_id, jbyteArray private_key, jlong key_len) {
    (void)clazz;
    jbyte* pk = env->GetByteArrayElements(private_key, nullptr);
    int rc = enchant_identity_store_store_one_time_prekey(
        reinterpret_cast<enchant_identity_store_t*>(store),
        static_cast<uint32_t>(prekey_id),
        reinterpret_cast<const uint8_t*>(pk), static_cast<size_t>(key_len));
    env->ReleaseByteArrayElements(private_key, pk, JNI_ABORT);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1session_1manager_1decrypt_1prekey(
    JNIEnv* env, jclass clazz, jlong manager,
    jstring address_name, jint device_id,
    jbyteArray ciphertext, jlong ciphertext_len,
    jint our_signed_prekey_id, jint our_one_time_prekey_id,
    jbyteArray plaintext, jlongArray plaintext_len) {
    (void)clazz;
    const char* addr = env->GetStringUTFChars(address_name, nullptr);
    jbyte* ct = env->GetByteArrayElements(ciphertext, nullptr);
    jbyte* pt = env->GetByteArrayElements(plaintext, nullptr);
    jlong* pl = env->GetLongArrayElements(plaintext_len, nullptr);
    size_t plen = static_cast<size_t>(pl[0]);
    int rc = enchant_session_manager_decrypt_prekey(
        reinterpret_cast<enchant_session_manager_t*>(manager),
        addr, static_cast<uint32_t>(device_id),
        reinterpret_cast<const uint8_t*>(ct), static_cast<size_t>(ciphertext_len),
        static_cast<uint32_t>(our_signed_prekey_id),
        static_cast<uint32_t>(our_one_time_prekey_id),
        reinterpret_cast<uint8_t*>(pt), &plen);
    env->ReleaseStringUTFChars(address_name, addr);
    env->ReleaseByteArrayElements(ciphertext, ct, JNI_ABORT);
    pl[0] = static_cast<jlong>(plen);
    env->ReleaseLongArrayElements(plaintext_len, pl, 0);
    env->ReleaseByteArrayElements(plaintext, pt, 0);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1session_1manager_1create(
    JNIEnv* env, jclass clazz, jlong identity_store, jlong session_store, jlongArray manager_out) {
    (void)clazz;

    // Get input arrays
    jlong* manager_out_elems = env->GetLongArrayElements(manager_out, nullptr);
    enchant_session_manager_t* manager_out_val = nullptr;

    // Declare output variables

    // Call native function
    int rc = enchant_session_manager_create((enchant_identity_store_t*)(long)(identity_store), (enchant_session_store_t*)(long)(session_store), &manager_out_val);

    // Release and copy back

    // Copy output values back to Java
    manager_out_elems[0] = (jlong)(long)manager_out_val;
    env->ReleaseLongArrayElements(manager_out, manager_out_elems, 0);
    return rc;
}

JNIEXPORT void JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1session_1manager_1destroy(
    JNIEnv* env, jclass clazz, jlong manager) {
    (void)clazz;

    // Get input arrays

    // Declare output variables

    // Call native function
    enchant_session_manager_destroy((enchant_session_manager_t*)(long)(manager));

    // Release and copy back

    // Copy output values back to Java
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1session_1manager_1establish(
    JNIEnv* env, jclass clazz, jlong manager, jstring address_name, jint device_id, jbyteArray identity_key, jint signed_prekey_id, jbyteArray signed_prekey, jbyteArray signed_prekey_sig, jlong signed_prekey_sig_len, jint one_time_prekey_id, jbyteArray one_time_prekey, jint registration_id) {
    (void)clazz;

    // Get input arrays
    const char* address_name_str = env->GetStringUTFChars(address_name, nullptr);
    jbyte* identity_key_jbyte = env->GetByteArrayElements(identity_key, nullptr);
    jbyte* signed_prekey_jbyte = env->GetByteArrayElements(signed_prekey, nullptr);
    jbyte* signed_prekey_sig_jbyte = env->GetByteArrayElements(signed_prekey_sig, nullptr);
    jbyte* one_time_prekey_jbyte = env->GetByteArrayElements(one_time_prekey, nullptr);

    // Declare output variables

    // Call native function
    int rc = enchant_session_manager_establish((enchant_session_manager_t*)(long)(manager), address_name_str, device_id, reinterpret_cast<const uint8_t*>(identity_key_jbyte), static_cast<uint32_t>(signed_prekey_id), reinterpret_cast<const uint8_t*>(signed_prekey_jbyte), reinterpret_cast<const uint8_t*>(signed_prekey_sig_jbyte), static_cast<size_t>(signed_prekey_sig_len), static_cast<uint32_t>(one_time_prekey_id), reinterpret_cast<const uint8_t*>(one_time_prekey_jbyte), static_cast<uint32_t>(registration_id));

    // Release and copy back
    env->ReleaseStringUTFChars(address_name, address_name_str);
    env->ReleaseByteArrayElements(identity_key, identity_key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(signed_prekey, signed_prekey_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(signed_prekey_sig, signed_prekey_sig_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(one_time_prekey, one_time_prekey_jbyte, JNI_ABORT);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1session_1manager_1establish_1with_1ephemeral(
    JNIEnv* env, jclass clazz, jlong manager, jstring address_name, jint device_id, jbyteArray identity_key, jint signed_prekey_id, jbyteArray signed_prekey, jbyteArray signed_prekey_sig, jlong signed_prekey_sig_len, jint one_time_prekey_id, jbyteArray one_time_prekey, jint registration_id, jbyteArray our_ephemeral_private, jlong our_ephemeral_private_len) {
    (void)clazz;

    const char* address_name_str = env->GetStringUTFChars(address_name, nullptr);
    jbyte* identity_key_jbyte = env->GetByteArrayElements(identity_key, nullptr);
    jbyte* signed_prekey_jbyte = env->GetByteArrayElements(signed_prekey, nullptr);
    jbyte* signed_prekey_sig_jbyte = env->GetByteArrayElements(signed_prekey_sig, nullptr);
    jbyte* one_time_prekey_jbyte = env->GetByteArrayElements(one_time_prekey, nullptr);
    jbyte* our_ephemeral_private_jbyte = our_ephemeral_private ? env->GetByteArrayElements(our_ephemeral_private, nullptr) : nullptr;

    int rc = enchant_session_manager_establish_with_ephemeral(
        (enchant_session_manager_t*)(long)(manager),
        address_name_str, device_id,
        reinterpret_cast<const uint8_t*>(identity_key_jbyte),
        static_cast<uint32_t>(signed_prekey_id),
        reinterpret_cast<const uint8_t*>(signed_prekey_jbyte),
        reinterpret_cast<const uint8_t*>(signed_prekey_sig_jbyte),
        static_cast<size_t>(signed_prekey_sig_len),
        static_cast<uint32_t>(one_time_prekey_id),
        reinterpret_cast<const uint8_t*>(one_time_prekey_jbyte),
        static_cast<uint32_t>(registration_id),
        reinterpret_cast<const uint8_t*>(our_ephemeral_private_jbyte),
        static_cast<size_t>(our_ephemeral_private_len));

    env->ReleaseStringUTFChars(address_name, address_name_str);
    env->ReleaseByteArrayElements(identity_key, identity_key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(signed_prekey, signed_prekey_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(signed_prekey_sig, signed_prekey_sig_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(one_time_prekey, one_time_prekey_jbyte, JNI_ABORT);
    if (our_ephemeral_private_jbyte) {
        env->ReleaseByteArrayElements(our_ephemeral_private, our_ephemeral_private_jbyte, JNI_ABORT);
    }
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1session_1manager_1encrypt(
    JNIEnv* env, jclass clazz, jlong manager, jstring address_name, jint device_id, jbyteArray plaintext, jlong plaintext_len, jbyteArray ciphertext, jlongArray ciphertext_len, jintArray message_type_out) {
    (void)clazz;

    // Get input arrays
    const char* address_name_str = env->GetStringUTFChars(address_name, nullptr);
    jbyte* plaintext_jbyte = env->GetByteArrayElements(plaintext, nullptr);
    jbyte* ciphertext_jbyte = env->GetByteArrayElements(ciphertext, nullptr);
    uint8_t* ciphertext_ptr = reinterpret_cast<uint8_t*>(ciphertext_jbyte);
    jlong* ciphertext_len_elems = env->GetLongArrayElements(ciphertext_len, nullptr);

    // Declare output variables
    size_t ciphertext_len_val = static_cast<size_t>(ciphertext_len_elems[0]);
    int message_type_out_val = 0;

    // Call native function
    int rc = enchant_session_manager_encrypt((enchant_session_manager_t*)(long)(manager), address_name_str, device_id, reinterpret_cast<const uint8_t*>(plaintext_jbyte), plaintext_len, ciphertext_ptr, &ciphertext_len_val, &message_type_out_val);

    // Release and copy back
    env->ReleaseStringUTFChars(address_name, address_name_str);
    env->ReleaseByteArrayElements(plaintext, plaintext_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(ciphertext, ciphertext_jbyte, 0);

    // Copy output values back to Java
    ciphertext_len_elems[0] = static_cast<jlong>(ciphertext_len_val);
    env->ReleaseLongArrayElements(ciphertext_len, ciphertext_len_elems, 0);
    jint* message_type_out_elems = env->GetIntArrayElements(message_type_out, nullptr);
    message_type_out_elems[0] = message_type_out_val;
    env->ReleaseIntArrayElements(message_type_out, message_type_out_elems, 0);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1session_1manager_1decrypt(
    JNIEnv* env, jclass clazz, jlong manager, jstring address_name, jint device_id, jbyteArray ciphertext, jlong ciphertext_len, jint message_type, jbyteArray plaintext, jlongArray plaintext_len) {
    (void)clazz;

    // Get input arrays
    const char* address_name_str = env->GetStringUTFChars(address_name, nullptr);
    jbyte* ciphertext_jbyte = env->GetByteArrayElements(ciphertext, nullptr);
    jbyte* plaintext_jbyte = env->GetByteArrayElements(plaintext, nullptr);
    uint8_t* plaintext_ptr = reinterpret_cast<uint8_t*>(plaintext_jbyte);
    jlong* plaintext_len_elems = env->GetLongArrayElements(plaintext_len, nullptr);

    // Declare output variables
    size_t plaintext_len_val = static_cast<size_t>(plaintext_len_elems[0]);

    // Call native function
    int rc = enchant_session_manager_decrypt((enchant_session_manager_t*)(long)(manager), address_name_str, device_id, reinterpret_cast<const uint8_t*>(ciphertext_jbyte), ciphertext_len, message_type, plaintext_ptr, &plaintext_len_val);

    // Release and copy back
    env->ReleaseStringUTFChars(address_name, address_name_str);
    env->ReleaseByteArrayElements(ciphertext, ciphertext_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(plaintext, plaintext_jbyte, 0);

    // Copy output values back to Java
    plaintext_len_elems[0] = static_cast<jlong>(plaintext_len_val);
    env->ReleaseLongArrayElements(plaintext_len, plaintext_len_elems, 0);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1session_1manager_1has_1session(
    JNIEnv* env, jclass clazz, jlong manager, jstring address_name, jint device_id, jintArray has_session_out) {
    (void)clazz;

    // Get input arrays
    const char* address_name_str = env->GetStringUTFChars(address_name, nullptr);

    // Declare output variables
    int has_session_out_val = 0;

    // Call native function
    int rc = enchant_session_manager_has_session((enchant_session_manager_t*)(long)(manager), address_name_str, device_id, &has_session_out_val);

    // Release and copy back
    env->ReleaseStringUTFChars(address_name, address_name_str);

    // Copy output values back to Java
    jint* has_session_out_elems = env->GetIntArrayElements(has_session_out, nullptr);
    has_session_out_elems[0] = has_session_out_val;
    env->ReleaseIntArrayElements(has_session_out, has_session_out_elems, 0);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1session_1manager_1archive_1session(
    JNIEnv* env, jclass clazz, jlong manager, jstring address_name, jint device_id) {
    (void)clazz;

    // Get input arrays
    const char* address_name_str = env->GetStringUTFChars(address_name, nullptr);

    // Declare output variables

    // Call native function
    int rc = enchant_session_manager_archive_session((enchant_session_manager_t*)(long)(manager), address_name_str, device_id);

    // Release and copy back
    env->ReleaseStringUTFChars(address_name, address_name_str);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1session_1builder_1create(
    JNIEnv* env, jclass clazz, jlong identity_store, jlong session_store, jlongArray builder_out) {
    (void)clazz;

    // Get input arrays
    jlong* builder_out_elems = env->GetLongArrayElements(builder_out, nullptr);
    enchant_session_builder_t* builder_out_val = nullptr;

    // Declare output variables

    // Call native function
    int rc = enchant_session_builder_create((enchant_identity_store_t*)(long)(identity_store), (enchant_session_store_t*)(long)(session_store), &builder_out_val);

    // Release and copy back

    // Copy output values back to Java
    builder_out_elems[0] = (jlong)(long)builder_out_val;
    env->ReleaseLongArrayElements(builder_out, builder_out_elems, 0);
    return rc;
}

JNIEXPORT void JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1session_1builder_1destroy(
    JNIEnv* env, jclass clazz, jlong builder) {
    (void)clazz;

    // Get input arrays

    // Declare output variables

    // Call native function
    enchant_session_builder_destroy((enchant_session_builder_t*)(long)(builder));

    // Release and copy back

    // Copy output values back to Java
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1session_1builder_1process_1bundle(
    JNIEnv* env, jclass clazz, jlong builder, jstring address_name, jint device_id, jbyteArray identity_key, jbyteArray signed_prekey, jbyteArray signed_prekey_sig, jlong signed_prekey_sig_len, jbyteArray one_time_prekey, jint registration_id) {
    (void)clazz;

    // Get input arrays
    const char* address_name_str = env->GetStringUTFChars(address_name, nullptr);
    jbyte* identity_key_jbyte = env->GetByteArrayElements(identity_key, nullptr);
    jbyte* signed_prekey_jbyte = env->GetByteArrayElements(signed_prekey, nullptr);
    jbyte* signed_prekey_sig_jbyte = env->GetByteArrayElements(signed_prekey_sig, nullptr);
    jbyte* one_time_prekey_jbyte = env->GetByteArrayElements(one_time_prekey, nullptr);

    // Declare output variables

    // Call native function
    int rc = enchant_session_builder_process_bundle((enchant_session_builder_t*)(long)(builder), address_name_str, device_id, reinterpret_cast<const uint8_t*>(identity_key_jbyte), reinterpret_cast<const uint8_t*>(signed_prekey_jbyte), reinterpret_cast<const uint8_t*>(signed_prekey_sig_jbyte), signed_prekey_sig_len, reinterpret_cast<const uint8_t*>(one_time_prekey_jbyte), registration_id);

    // Release and copy back
    env->ReleaseStringUTFChars(address_name, address_name_str);
    env->ReleaseByteArrayElements(identity_key, identity_key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(signed_prekey, signed_prekey_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(signed_prekey_sig, signed_prekey_sig_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(one_time_prekey, one_time_prekey_jbyte, JNI_ABORT);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1session_1cipher_1create(
    JNIEnv* env, jclass clazz, jlong identity_store, jlong session_store, jlongArray cipher_out) {
    (void)clazz;

    // Get input arrays
    jlong* cipher_out_elems = env->GetLongArrayElements(cipher_out, nullptr);
    enchant_session_cipher_t* cipher_out_val = nullptr;

    // Declare output variables

    // Call native function
    int rc = enchant_session_cipher_create((enchant_identity_store_t*)(long)(identity_store), (enchant_session_store_t*)(long)(session_store), &cipher_out_val);

    // Release and copy back

    // Copy output values back to Java
    cipher_out_elems[0] = (jlong)(long)cipher_out_val;
    env->ReleaseLongArrayElements(cipher_out, cipher_out_elems, 0);
    return rc;
}

JNIEXPORT void JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1session_1cipher_1destroy(
    JNIEnv* env, jclass clazz, jlong cipher) {
    (void)clazz;

    // Get input arrays

    // Declare output variables

    // Call native function
    enchant_session_cipher_destroy((enchant_session_cipher_t*)(long)(cipher));

    // Release and copy back

    // Copy output values back to Java
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1session_1cipher_1encrypt(
    JNIEnv* env, jclass clazz, jlong cipher, jstring address_name, jint device_id, jbyteArray plaintext, jlong plaintext_len, jbyteArray ciphertext, jlongArray ciphertext_len) {
    (void)clazz;

    // Get input arrays
    const char* address_name_str = env->GetStringUTFChars(address_name, nullptr);
    jbyte* plaintext_jbyte = env->GetByteArrayElements(plaintext, nullptr);
    jbyte* ciphertext_jbyte = env->GetByteArrayElements(ciphertext, nullptr);
    uint8_t* ciphertext_ptr = reinterpret_cast<uint8_t*>(ciphertext_jbyte);
    jlong* ciphertext_len_elems = env->GetLongArrayElements(ciphertext_len, nullptr);

    // Declare output variables
    size_t ciphertext_len_val = static_cast<size_t>(ciphertext_len_elems[0]);

    // Call native function
    int rc = enchant_session_cipher_encrypt((enchant_session_cipher_t*)(long)(cipher), address_name_str, device_id, reinterpret_cast<const uint8_t*>(plaintext_jbyte), plaintext_len, ciphertext_ptr, &ciphertext_len_val);

    // Release and copy back
    env->ReleaseStringUTFChars(address_name, address_name_str);
    env->ReleaseByteArrayElements(plaintext, plaintext_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(ciphertext, ciphertext_jbyte, 0);

    // Copy output values back to Java
    ciphertext_len_elems[0] = static_cast<jlong>(ciphertext_len_val);
    env->ReleaseLongArrayElements(ciphertext_len, ciphertext_len_elems, 0);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1session_1cipher_1decrypt(
    JNIEnv* env, jclass clazz, jlong cipher, jstring address_name, jint device_id, jbyteArray ciphertext, jlong ciphertext_len, jbyteArray plaintext, jlongArray plaintext_len) {
    (void)clazz;

    // Get input arrays
    const char* address_name_str = env->GetStringUTFChars(address_name, nullptr);
    jbyte* ciphertext_jbyte = env->GetByteArrayElements(ciphertext, nullptr);
    jbyte* plaintext_jbyte = env->GetByteArrayElements(plaintext, nullptr);
    uint8_t* plaintext_ptr = reinterpret_cast<uint8_t*>(plaintext_jbyte);
    jlong* plaintext_len_elems = env->GetLongArrayElements(plaintext_len, nullptr);

    // Declare output variables
    size_t plaintext_len_val = static_cast<size_t>(plaintext_len_elems[0]);

    // Call native function
    int rc = enchant_session_cipher_decrypt((enchant_session_cipher_t*)(long)(cipher), address_name_str, device_id, reinterpret_cast<const uint8_t*>(ciphertext_jbyte), ciphertext_len, plaintext_ptr, &plaintext_len_val);

    // Release and copy back
    env->ReleaseStringUTFChars(address_name, address_name_str);
    env->ReleaseByteArrayElements(ciphertext, ciphertext_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(plaintext, plaintext_jbyte, 0);

    // Copy output values back to Java
    plaintext_len_elems[0] = static_cast<jlong>(plaintext_len_val);
    env->ReleaseLongArrayElements(plaintext_len, plaintext_len_elems, 0);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1session_1record_1serialize(
    JNIEnv* env, jclass clazz, jbyteArray session_state_data, jlong session_state_len, jbyteArray output, jlongArray output_len) {
    (void)clazz;

    // Get input arrays
    jbyte* session_state_data_jbyte = env->GetByteArrayElements(session_state_data, nullptr);
    jbyte* output_jbyte = env->GetByteArrayElements(output, nullptr);
    uint8_t* output_ptr = reinterpret_cast<uint8_t*>(output_jbyte);
    jlong* output_len_elems = env->GetLongArrayElements(output_len, nullptr);

    // Declare output variables
    size_t output_len_val = static_cast<size_t>(output_len_elems[0]);

    // Call native function
    int rc = enchant_session_record_serialize(reinterpret_cast<const uint8_t*>(session_state_data_jbyte), session_state_len, output_ptr, &output_len_val);

    // Release and copy back
    env->ReleaseByteArrayElements(session_state_data, session_state_data_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(output, output_jbyte, 0);

    // Copy output values back to Java
    output_len_elems[0] = static_cast<jlong>(output_len_val);
    env->ReleaseLongArrayElements(output_len, output_len_elems, 0);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1session_1record_1deserialize(
    JNIEnv* env, jclass clazz, jbyteArray data, jlong data_len, jbyteArray session_state_output, jlongArray session_state_len) {
    (void)clazz;

    // Get input arrays
    jbyte* data_jbyte = env->GetByteArrayElements(data, nullptr);
    jbyte* session_state_output_jbyte = env->GetByteArrayElements(session_state_output, nullptr);
    uint8_t* session_state_output_ptr = reinterpret_cast<uint8_t*>(session_state_output_jbyte);
    jlong* session_state_len_elems = env->GetLongArrayElements(session_state_len, nullptr);

    // Declare output variables
    size_t session_state_len_val = static_cast<size_t>(session_state_len_elems[0]);

    // Call native function
    int rc = enchant_session_record_deserialize(reinterpret_cast<const uint8_t*>(data_jbyte), data_len, session_state_output_ptr, &session_state_len_val);

    // Release and copy back
    env->ReleaseByteArrayElements(data, data_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(session_state_output, session_state_output_jbyte, 0);

    // Copy output values back to Java
    session_state_len_elems[0] = static_cast<jlong>(session_state_len_val);
    env->ReleaseLongArrayElements(session_state_len, session_state_len_elems, 0);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1kem_1keypair(
    JNIEnv* env, jclass clazz, jint kem_type, jbyteArray public_key, jlong public_key_len, jbyteArray secret_key, jlong secret_key_len) {
    (void)clazz;

    // Get input arrays
    jbyte* public_key_jbyte = env->GetByteArrayElements(public_key, nullptr);
    uint8_t* public_key_ptr = reinterpret_cast<uint8_t*>(public_key_jbyte);
    jbyte* secret_key_jbyte = env->GetByteArrayElements(secret_key, nullptr);
    uint8_t* secret_key_ptr = reinterpret_cast<uint8_t*>(secret_key_jbyte);

    // Declare output variables
    size_t public_key_len_val = 0;
    size_t secret_key_len_val = 0;

    // Call native function
    int rc = enchant_kem_keypair(kem_type, public_key_ptr, &public_key_len_val, secret_key_ptr, &secret_key_len_val);

    // Release and copy back
    env->ReleaseByteArrayElements(public_key, public_key_jbyte, 0);
    env->ReleaseByteArrayElements(secret_key, secret_key_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1kem_1encapsulate(
    JNIEnv* env, jclass clazz, jint kem_type, jbyteArray public_key, jlong public_key_len, jbyteArray ciphertext, jlong ciphertext_len, jbyteArray shared_secret) {
    (void)clazz;

    // Get input arrays
    jbyte* public_key_jbyte = env->GetByteArrayElements(public_key, nullptr);
    jbyte* ciphertext_jbyte = env->GetByteArrayElements(ciphertext, nullptr);
    uint8_t* ciphertext_ptr = reinterpret_cast<uint8_t*>(ciphertext_jbyte);
    jbyte* shared_secret_jbyte = env->GetByteArrayElements(shared_secret, nullptr);
    uint8_t* shared_secret_ptr = reinterpret_cast<uint8_t*>(shared_secret_jbyte);

    // Declare output variables
    size_t ciphertext_len_val = 0;

    // Call native function
    int rc = enchant_kem_encapsulate(kem_type, reinterpret_cast<const uint8_t*>(public_key_jbyte), public_key_len, ciphertext_ptr, &ciphertext_len_val, shared_secret_ptr);

    // Release and copy back
    env->ReleaseByteArrayElements(public_key, public_key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(ciphertext, ciphertext_jbyte, 0);
    env->ReleaseByteArrayElements(shared_secret, shared_secret_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1kem_1decapsulate(
    JNIEnv* env, jclass clazz, jint kem_type, jbyteArray secret_key, jlong secret_key_len, jbyteArray ciphertext, jlong ciphertext_len, jbyteArray shared_secret) {
    (void)clazz;

    // Get input arrays
    jbyte* secret_key_jbyte = env->GetByteArrayElements(secret_key, nullptr);
    jbyte* ciphertext_jbyte = env->GetByteArrayElements(ciphertext, nullptr);
    jbyte* shared_secret_jbyte = env->GetByteArrayElements(shared_secret, nullptr);
    uint8_t* shared_secret_ptr = reinterpret_cast<uint8_t*>(shared_secret_jbyte);

    // Declare output variables

    // Call native function
    int rc = enchant_kem_decapsulate(kem_type, reinterpret_cast<const uint8_t*>(secret_key_jbyte), secret_key_len, reinterpret_cast<const uint8_t*>(ciphertext_jbyte), ciphertext_len, shared_secret_ptr);

    // Release and copy back
    env->ReleaseByteArrayElements(secret_key, secret_key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(ciphertext, ciphertext_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(shared_secret, shared_secret_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1kem_1public_1key_1size(
    JNIEnv* env, jclass clazz, jint kem_type, jlong size_out) {
    (void)clazz;

    // Get input arrays

    // Declare output variables
    size_t size_out_val = 0;

    // Call native function
    int rc = enchant_kem_public_key_size(kem_type, &size_out_val);

    // Release and copy back

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1kem_1secret_1key_1size(
    JNIEnv* env, jclass clazz, jint kem_type, jlong size_out) {
    (void)clazz;

    // Get input arrays

    // Declare output variables
    size_t size_out_val = 0;

    // Call native function
    int rc = enchant_kem_secret_key_size(kem_type, &size_out_val);

    // Release and copy back

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1kem_1ciphertext_1size(
    JNIEnv* env, jclass clazz, jint kem_type, jlong size_out) {
    (void)clazz;

    // Get input arrays

    // Declare output variables
    size_t size_out_val = 0;

    // Call native function
    int rc = enchant_kem_ciphertext_size(kem_type, &size_out_val);

    // Release and copy back

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1kem_1is_1available(
    JNIEnv* env, jclass clazz, jint kem_type, jintArray available_out) {
    (void)clazz;

    // Get input arrays

    // Declare output variables
    int available_out_val = 0;

    // Call native function
    int rc = enchant_kem_is_available(kem_type, &available_out_val);

    // Release and copy back

    // Copy output values back to Java
    jint* available_out_elems = env->GetIntArrayElements(available_out, nullptr);
    available_out_elems[0] = available_out_val;
    env->ReleaseIntArrayElements(available_out, available_out_elems, 0);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1kem_1serialize_1public_1key(
    JNIEnv* env, jclass clazz, jint kem_type, jbyteArray raw_key, jlong raw_len, jbyteArray output, jlong output_len) {
    (void)clazz;

    // Get input arrays
    jbyte* raw_key_jbyte = env->GetByteArrayElements(raw_key, nullptr);
    jbyte* output_jbyte = env->GetByteArrayElements(output, nullptr);
    uint8_t* output_ptr = reinterpret_cast<uint8_t*>(output_jbyte);

    // Declare output variables
    size_t output_len_val = 0;

    // Call native function
    int rc = enchant_kem_serialize_public_key(kem_type, reinterpret_cast<const uint8_t*>(raw_key_jbyte), raw_len, output_ptr, &output_len_val);

    // Release and copy back
    env->ReleaseByteArrayElements(raw_key, raw_key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(output, output_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1kem_1deserialize_1public_1key(
    JNIEnv* env, jclass clazz, jbyteArray data, jlong data_len, jintArray kem_type_out, jbyteArray raw_key, jlong raw_len) {
    (void)clazz;

    // Get input arrays
    jbyte* data_jbyte = env->GetByteArrayElements(data, nullptr);
    jbyte* raw_key_jbyte = env->GetByteArrayElements(raw_key, nullptr);
    uint8_t* raw_key_ptr = reinterpret_cast<uint8_t*>(raw_key_jbyte);

    // Declare output variables
    int kem_type_out_val = 0;
    size_t raw_len_val = 0;

    // Call native function
    int rc = enchant_kem_deserialize_public_key(reinterpret_cast<const uint8_t*>(data_jbyte), data_len, &kem_type_out_val, raw_key_ptr, &raw_len_val);

    // Release and copy back
    env->ReleaseByteArrayElements(data, data_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(raw_key, raw_key_jbyte, 0);

    // Copy output values back to Java
    jint* kem_type_out_elems = env->GetIntArrayElements(kem_type_out, nullptr);
    kem_type_out_elems[0] = kem_type_out_val;
    env->ReleaseIntArrayElements(kem_type_out, kem_type_out_elems, 0);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1kem_1serialize_1secret_1key(
    JNIEnv* env, jclass clazz, jint kem_type, jbyteArray raw_key, jlong raw_len, jbyteArray output, jlong output_len) {
    (void)clazz;

    // Get input arrays
    jbyte* raw_key_jbyte = env->GetByteArrayElements(raw_key, nullptr);
    jbyte* output_jbyte = env->GetByteArrayElements(output, nullptr);
    uint8_t* output_ptr = reinterpret_cast<uint8_t*>(output_jbyte);

    // Declare output variables
    size_t output_len_val = 0;

    // Call native function
    int rc = enchant_kem_serialize_secret_key(kem_type, reinterpret_cast<const uint8_t*>(raw_key_jbyte), raw_len, output_ptr, &output_len_val);

    // Release and copy back
    env->ReleaseByteArrayElements(raw_key, raw_key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(output, output_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1kem_1deserialize_1secret_1key(
    JNIEnv* env, jclass clazz, jbyteArray data, jlong data_len, jintArray kem_type_out, jbyteArray raw_key, jlong raw_len) {
    (void)clazz;

    // Get input arrays
    jbyte* data_jbyte = env->GetByteArrayElements(data, nullptr);
    jbyte* raw_key_jbyte = env->GetByteArrayElements(raw_key, nullptr);
    uint8_t* raw_key_ptr = reinterpret_cast<uint8_t*>(raw_key_jbyte);

    // Declare output variables
    int kem_type_out_val = 0;
    size_t raw_len_val = 0;

    // Call native function
    int rc = enchant_kem_deserialize_secret_key(reinterpret_cast<const uint8_t*>(data_jbyte), data_len, &kem_type_out_val, raw_key_ptr, &raw_len_val);

    // Release and copy back
    env->ReleaseByteArrayElements(data, data_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(raw_key, raw_key_jbyte, 0);

    // Copy output values back to Java
    jint* kem_type_out_elems = env->GetIntArrayElements(kem_type_out, nullptr);
    kem_type_out_elems[0] = kem_type_out_val;
    env->ReleaseIntArrayElements(kem_type_out, kem_type_out_elems, 0);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1kem_1serialize_1ciphertext(
    JNIEnv* env, jclass clazz, jint kem_type, jbyteArray raw_ct, jlong raw_len, jbyteArray output, jlong output_len) {
    (void)clazz;

    // Get input arrays
    jbyte* raw_ct_jbyte = env->GetByteArrayElements(raw_ct, nullptr);
    jbyte* output_jbyte = env->GetByteArrayElements(output, nullptr);
    uint8_t* output_ptr = reinterpret_cast<uint8_t*>(output_jbyte);

    // Declare output variables
    size_t output_len_val = 0;

    // Call native function
    int rc = enchant_kem_serialize_ciphertext(kem_type, reinterpret_cast<const uint8_t*>(raw_ct_jbyte), raw_len, output_ptr, &output_len_val);

    // Release and copy back
    env->ReleaseByteArrayElements(raw_ct, raw_ct_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(output, output_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1kem_1deserialize_1ciphertext(
    JNIEnv* env, jclass clazz, jbyteArray data, jlong data_len, jintArray kem_type_out, jbyteArray raw_ct, jlong raw_len) {
    (void)clazz;

    // Get input arrays
    jbyte* data_jbyte = env->GetByteArrayElements(data, nullptr);
    jbyte* raw_ct_jbyte = env->GetByteArrayElements(raw_ct, nullptr);
    uint8_t* raw_ct_ptr = reinterpret_cast<uint8_t*>(raw_ct_jbyte);

    // Declare output variables
    int kem_type_out_val = 0;
    size_t raw_len_val = 0;

    // Call native function
    int rc = enchant_kem_deserialize_ciphertext(reinterpret_cast<const uint8_t*>(data_jbyte), data_len, &kem_type_out_val, raw_ct_ptr, &raw_len_val);

    // Release and copy back
    env->ReleaseByteArrayElements(data, data_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(raw_ct, raw_ct_jbyte, 0);

    // Copy output values back to Java
    jint* kem_type_out_elems = env->GetIntArrayElements(kem_type_out, nullptr);
    kem_type_out_elems[0] = kem_type_out_val;
    env->ReleaseIntArrayElements(kem_type_out, kem_type_out_elems, 0);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1fingerprint_1generate(
    JNIEnv* env, jclass clazz, jint version, jint iterations, jstring local_name, jbyteArray local_key, jstring remote_name, jbyteArray remote_key, jbyteArray displayable_out, jlong displayable_out_len, jbyteArray scannable_out, jlong scannable_len) {
    (void)clazz;

    // Get input arrays
    const char* local_name_str = env->GetStringUTFChars(local_name, nullptr);
    jbyte* local_key_jbyte = env->GetByteArrayElements(local_key, nullptr);
    const char* remote_name_str = env->GetStringUTFChars(remote_name, nullptr);
    jbyte* remote_key_jbyte = env->GetByteArrayElements(remote_key, nullptr);
    jbyte* displayable_out_jbyte = env->GetByteArrayElements(displayable_out, nullptr);
    uint8_t* displayable_out_ptr = reinterpret_cast<uint8_t*>(displayable_out_jbyte);
    jbyte* scannable_out_jbyte = env->GetByteArrayElements(scannable_out, nullptr);
    uint8_t* scannable_out_ptr = reinterpret_cast<uint8_t*>(scannable_out_jbyte);

    // Declare output variables
    size_t scannable_len_val = 0;

    // Call native function
    int rc = enchant_fingerprint_generate(version, iterations, local_name_str, reinterpret_cast<const uint8_t*>(local_key_jbyte), remote_name_str, reinterpret_cast<const uint8_t*>(remote_key_jbyte), displayable_out_ptr, displayable_out_len, scannable_out_ptr, &scannable_len_val);

    // Release and copy back
    env->ReleaseStringUTFChars(local_name, local_name_str);
    env->ReleaseByteArrayElements(local_key, local_key_jbyte, JNI_ABORT);
    env->ReleaseStringUTFChars(remote_name, remote_name_str);
    env->ReleaseByteArrayElements(remote_key, remote_key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(displayable_out, displayable_out_jbyte, 0);
    env->ReleaseByteArrayElements(scannable_out, scannable_out_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1fingerprint_1compare(
    JNIEnv* env, jclass clazz, jbyteArray scannable_a, jlong len_a, jbyteArray scannable_b, jlong len_b, jintArray match_out) {
    (void)clazz;

    // Get input arrays
    jbyte* scannable_a_jbyte = env->GetByteArrayElements(scannable_a, nullptr);
    jbyte* scannable_b_jbyte = env->GetByteArrayElements(scannable_b, nullptr);

    // Declare output variables
    int match_out_val = 0;

    // Call native function
    int rc = enchant_fingerprint_compare(reinterpret_cast<const uint8_t*>(scannable_a_jbyte), len_a, reinterpret_cast<const uint8_t*>(scannable_b_jbyte), len_b, &match_out_val);

    // Release and copy back
    env->ReleaseByteArrayElements(scannable_a, scannable_a_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(scannable_b, scannable_b_jbyte, JNI_ABORT);

    // Copy output values back to Java
    jint* match_out_elems = env->GetIntArrayElements(match_out, nullptr);
    match_out_elems[0] = match_out_val;
    env->ReleaseIntArrayElements(match_out, match_out_elems, 0);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1safety_1number_1generate(
    JNIEnv* env, jclass clazz, jbyteArray sender_identity_key, jbyteArray recipient_identity_key, jstring sender_uuid, jstring recipient_uuid, jbyteArray safety_number_out, jlongArray safety_number_len) {
    (void)clazz;

    // Get input arrays
    jbyte* sender_identity_key_jbyte = env->GetByteArrayElements(sender_identity_key, nullptr);
    jbyte* recipient_identity_key_jbyte = env->GetByteArrayElements(recipient_identity_key, nullptr);
    const char* sender_uuid_str = env->GetStringUTFChars(sender_uuid, nullptr);
    const char* recipient_uuid_str = env->GetStringUTFChars(recipient_uuid, nullptr);
    jbyte* safety_number_out_jbyte = env->GetByteArrayElements(safety_number_out, nullptr);
    uint8_t* safety_number_out_ptr = reinterpret_cast<uint8_t*>(safety_number_out_jbyte);
    jlong* safety_number_len_elems = env->GetLongArrayElements(safety_number_len, nullptr);

    // Declare output variables
    size_t safety_number_len_val = static_cast<size_t>(safety_number_len_elems[0]);

    // Call native function
    int rc = enchant_safety_number_generate(reinterpret_cast<const uint8_t*>(sender_identity_key_jbyte), reinterpret_cast<const uint8_t*>(recipient_identity_key_jbyte), sender_uuid_str, recipient_uuid_str, safety_number_out_ptr, &safety_number_len_val);

    // Release and copy back
    env->ReleaseByteArrayElements(sender_identity_key, sender_identity_key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(recipient_identity_key, recipient_identity_key_jbyte, JNI_ABORT);
    env->ReleaseStringUTFChars(sender_uuid, sender_uuid_str);
    env->ReleaseStringUTFChars(recipient_uuid, recipient_uuid_str);
    env->ReleaseByteArrayElements(safety_number_out, safety_number_out_jbyte, 0);

    // Copy output values back to Java
    safety_number_len_elems[0] = static_cast<jlong>(safety_number_len_val);
    env->ReleaseLongArrayElements(safety_number_len, safety_number_len_elems, 0);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1safety_1number_1compare(
    JNIEnv* env, jclass clazz, jbyteArray safety_number_a, jlong len_a, jbyteArray safety_number_b, jlong len_b, jintArray match_out) {
    (void)clazz;

    // Get input arrays
    jbyte* safety_number_a_jbyte = env->GetByteArrayElements(safety_number_a, nullptr);
    jbyte* safety_number_b_jbyte = env->GetByteArrayElements(safety_number_b, nullptr);

    // Declare output variables
    int match_out_val = 0;

    // Call native function
    int rc = enchant_safety_number_compare(reinterpret_cast<const uint8_t*>(safety_number_a_jbyte), len_a, reinterpret_cast<const uint8_t*>(safety_number_b_jbyte), len_b, &match_out_val);

    // Release and copy back
    env->ReleaseByteArrayElements(safety_number_a, safety_number_a_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(safety_number_b, safety_number_b_jbyte, JNI_ABORT);

    // Copy output values back to Java
    jint* match_out_elems = env->GetIntArrayElements(match_out, nullptr);
    match_out_elems[0] = match_out_val;
    env->ReleaseIntArrayElements(match_out, match_out_elems, 0);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1sign_1alternate_1identity(
    JNIEnv* env, jclass clazz, jbyteArray private_key, jbyteArray other_identity_key, jbyteArray signature_out) {
    (void)clazz;

    // Get input arrays
    jbyte* private_key_jbyte = env->GetByteArrayElements(private_key, nullptr);
    jbyte* other_identity_key_jbyte = env->GetByteArrayElements(other_identity_key, nullptr);
    jbyte* signature_out_jbyte = env->GetByteArrayElements(signature_out, nullptr);
    uint8_t* signature_out_ptr = reinterpret_cast<uint8_t*>(signature_out_jbyte);

    // Declare output variables

    // Call native function
    int rc = enchant_sign_alternate_identity(reinterpret_cast<const uint8_t*>(private_key_jbyte), reinterpret_cast<const uint8_t*>(other_identity_key_jbyte), signature_out_ptr);

    // Release and copy back
    env->ReleaseByteArrayElements(private_key, private_key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(other_identity_key, other_identity_key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(signature_out, signature_out_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1verify_1alternate_1identity(
    JNIEnv* env, jclass clazz, jbyteArray public_key, jbyteArray other_identity_key, jbyteArray signature, jintArray valid_out) {
    (void)clazz;

    // Get input arrays
    jbyte* public_key_jbyte = env->GetByteArrayElements(public_key, nullptr);
    jbyte* other_identity_key_jbyte = env->GetByteArrayElements(other_identity_key, nullptr);
    jbyte* signature_jbyte = env->GetByteArrayElements(signature, nullptr);

    // Declare output variables
    int valid_out_val = 0;

    // Call native function
    int rc = enchant_verify_alternate_identity(reinterpret_cast<const uint8_t*>(public_key_jbyte), reinterpret_cast<const uint8_t*>(other_identity_key_jbyte), reinterpret_cast<const uint8_t*>(signature_jbyte), &valid_out_val);

    // Release and copy back
    env->ReleaseByteArrayElements(public_key, public_key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(other_identity_key, other_identity_key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(signature, signature_jbyte, JNI_ABORT);

    // Copy output values back to Java
    jint* valid_out_elems = env->GetIntArrayElements(valid_out, nullptr);
    valid_out_elems[0] = valid_out_val;
    env->ReleaseIntArrayElements(valid_out, valid_out_elems, 0);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1calculate_1chunk_1size(
    JNIEnv* env, jclass clazz, jlong data_size) {
    (void)clazz;

    // Get input arrays

    // Declare output variables

    // Call native function
    int rc = enchant_calculate_chunk_size(data_size);

    // Release and copy back

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1incremental_1mac_1create(
    JNIEnv* env, jclass clazz, jbyteArray key, jlong key_len, jlong data_size, jlongArray mac_out) {
    (void)clazz;

    // Get input arrays
    jbyte* key_jbyte = env->GetByteArrayElements(key, nullptr);
    jlong* mac_out_elems = env->GetLongArrayElements(mac_out, nullptr);
    enchant_incremental_mac_t* mac_out_val = nullptr;

    // Declare output variables

    // Call native function
    int rc = enchant_incremental_mac_create(reinterpret_cast<const uint8_t*>(key_jbyte), key_len, data_size, &mac_out_val);

    // Release and copy back
    env->ReleaseByteArrayElements(key, key_jbyte, JNI_ABORT);

    // Copy output values back to Java
    mac_out_elems[0] = (jlong)(long)mac_out_val;
    env->ReleaseLongArrayElements(mac_out, mac_out_elems, 0);
    return rc;
}

JNIEXPORT void JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1incremental_1mac_1destroy(
    JNIEnv* env, jclass clazz, jlong mac) {
    (void)clazz;

    // Get input arrays

    // Declare output variables

    // Call native function
    enchant_incremental_mac_destroy((enchant_incremental_mac_t*)(long)(mac));

    // Release and copy back

    // Copy output values back to Java
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1incremental_1mac_1update(
    JNIEnv* env, jclass clazz, jlong mac, jbyteArray data, jlong data_len, jbyteArray mac_out, jlong mac_len) {
    (void)clazz;

    // Get input arrays
    jbyte* data_jbyte = env->GetByteArrayElements(data, nullptr);
    jbyte* mac_out_jbyte = env->GetByteArrayElements(mac_out, nullptr);
    uint8_t* mac_out_ptr = reinterpret_cast<uint8_t*>(mac_out_jbyte);

    // Declare output variables
    size_t mac_len_val = 0;

    // Call native function
    int rc = enchant_incremental_mac_update((enchant_incremental_mac_t*)(long)(mac), reinterpret_cast<const uint8_t*>(data_jbyte), data_len, mac_out_ptr, &mac_len_val);

    // Release and copy back
    env->ReleaseByteArrayElements(data, data_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(mac_out, mac_out_jbyte, 0);

    // Copy output values back to Java
    return rc;
}


JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1incremental_1mac_1finalize(
    JNIEnv* env, jclass clazz, jlong mac, jbyteArray final_mac, jlong mac_len) {
    (void)clazz;

    // Get input arrays
    jbyte* final_mac_jbyte = env->GetByteArrayElements(final_mac, nullptr);
    uint8_t* final_mac_ptr = reinterpret_cast<uint8_t*>(final_mac_jbyte);

    // Declare output variables
    size_t mac_len_val = 0;

    // Call native function
    int rc = enchant_incremental_mac_finalize((enchant_incremental_mac_t*)(long)(mac), final_mac_ptr, &mac_len_val);

    // Release and copy back
    env->ReleaseByteArrayElements(final_mac, final_mac_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1incremental_1mac_1validator_1create(
    JNIEnv* env, jclass clazz, jbyteArray key, jlong key_len, jlong data_size, jbyteArray expected_macs, jlong expected_macs_len, jlong mac_size, jlongArray validator_out) {
    (void)clazz;

    // Get input arrays
    jbyte* key_jbyte = env->GetByteArrayElements(key, nullptr);
    jbyte* expected_macs_jbyte = env->GetByteArrayElements(expected_macs, nullptr);
    jlong* validator_out_elems = env->GetLongArrayElements(validator_out, nullptr);
    enchant_incremental_mac_validator_t* validator_out_val = nullptr;

    // Declare output variables

    // Call native function
    int rc = enchant_incremental_mac_validator_create(reinterpret_cast<const uint8_t*>(key_jbyte), key_len, data_size, reinterpret_cast<const uint8_t*>(expected_macs_jbyte), expected_macs_len, mac_size, &validator_out_val);

    // Release and copy back
    env->ReleaseByteArrayElements(key, key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(expected_macs, expected_macs_jbyte, JNI_ABORT);

    // Copy output values back to Java
    validator_out_elems[0] = (jlong)(long)validator_out_val;
    env->ReleaseLongArrayElements(validator_out, validator_out_elems, 0);
    return rc;
}

JNIEXPORT void JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1incremental_1mac_1validator_1destroy(
    JNIEnv* env, jclass clazz, jlong validator) {
    (void)clazz;

    // Get input arrays

    // Declare output variables

    // Call native function
    enchant_incremental_mac_validator_destroy((enchant_incremental_mac_validator_t*)(long)(validator));

    // Release and copy back

    // Copy output values back to Java
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1incremental_1mac_1validator_1update(
    JNIEnv* env, jclass clazz, jlong validator, jbyteArray data, jlong data_len, jlong bytes_validated) {
    (void)clazz;

    // Get input arrays
    jbyte* data_jbyte = env->GetByteArrayElements(data, nullptr);

    // Declare output variables
    size_t bytes_validated_val = 0;

    // Call native function
    int rc = enchant_incremental_mac_validator_update((enchant_incremental_mac_validator_t*)(long)(validator), reinterpret_cast<const uint8_t*>(data_jbyte), data_len, &bytes_validated_val);

    // Release and copy back
    env->ReleaseByteArrayElements(data, data_jbyte, JNI_ABORT);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1incremental_1mac_1validator_1finalize(
    JNIEnv* env, jclass clazz, jlong validator, jlong bytes_validated) {
    (void)clazz;

    // Get input arrays

    // Declare output variables
    size_t bytes_validated_val = 0;

    // Call native function
    int rc = enchant_incremental_mac_validator_finalize((enchant_incremental_mac_validator_t*)(long)(validator), &bytes_validated_val);

    // Release and copy back

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1veil_1encrypt_1v1(
    JNIEnv* env, jclass clazz, jbyteArray recipient_public_key, jbyteArray sender_identity_private, jbyteArray sender_identity_public, jbyteArray plaintext, jlong plaintext_len, jbyteArray output, jlong output_len) {
    (void)clazz;

    // Get input arrays
    jbyte* recipient_public_key_jbyte = env->GetByteArrayElements(recipient_public_key, nullptr);
    jbyte* sender_identity_private_jbyte = env->GetByteArrayElements(sender_identity_private, nullptr);
    jbyte* sender_identity_public_jbyte = env->GetByteArrayElements(sender_identity_public, nullptr);
    jbyte* plaintext_jbyte = env->GetByteArrayElements(plaintext, nullptr);
    jbyte* output_jbyte = env->GetByteArrayElements(output, nullptr);
    uint8_t* output_ptr = reinterpret_cast<uint8_t*>(output_jbyte);

    // Declare output variables
    size_t output_len_val = 0;

    // Call native function
    int rc = enchant_veil_encrypt_v1(reinterpret_cast<const uint8_t*>(recipient_public_key_jbyte), reinterpret_cast<const uint8_t*>(sender_identity_private_jbyte), reinterpret_cast<const uint8_t*>(sender_identity_public_jbyte), reinterpret_cast<const uint8_t*>(plaintext_jbyte), plaintext_len, output_ptr, &output_len_val);

    // Release and copy back
    env->ReleaseByteArrayElements(recipient_public_key, recipient_public_key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(sender_identity_private, sender_identity_private_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(sender_identity_public, sender_identity_public_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(plaintext, plaintext_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(output, output_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1veil_1decrypt_1v1(
    JNIEnv* env, jclass clazz, jbyteArray recipient_private_key, jbyteArray recipient_public_key, jbyteArray ciphertext, jlong ciphertext_len, jbyteArray plaintext, jlong plaintext_len, jbyteArray sender_identity_key_out) {
    (void)clazz;

    // Get input arrays
    jbyte* recipient_private_key_jbyte = env->GetByteArrayElements(recipient_private_key, nullptr);
    jbyte* recipient_public_key_jbyte = env->GetByteArrayElements(recipient_public_key, nullptr);
    jbyte* ciphertext_jbyte = env->GetByteArrayElements(ciphertext, nullptr);
    jbyte* plaintext_jbyte = env->GetByteArrayElements(plaintext, nullptr);
    uint8_t* plaintext_ptr = reinterpret_cast<uint8_t*>(plaintext_jbyte);
    jbyte* sender_identity_key_out_jbyte = env->GetByteArrayElements(sender_identity_key_out, nullptr);
    uint8_t* sender_identity_key_out_ptr = reinterpret_cast<uint8_t*>(sender_identity_key_out_jbyte);

    // Declare output variables
    size_t plaintext_len_val = 0;

    // Call native function
    int rc = enchant_veil_decrypt_v1(reinterpret_cast<const uint8_t*>(recipient_private_key_jbyte), reinterpret_cast<const uint8_t*>(recipient_public_key_jbyte), reinterpret_cast<const uint8_t*>(ciphertext_jbyte), ciphertext_len, plaintext_ptr, &plaintext_len_val, sender_identity_key_out_ptr);

    // Release and copy back
    env->ReleaseByteArrayElements(recipient_private_key, recipient_private_key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(recipient_public_key, recipient_public_key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(ciphertext, ciphertext_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(plaintext, plaintext_jbyte, 0);
    env->ReleaseByteArrayElements(sender_identity_key_out, sender_identity_key_out_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1server_1certificate_1create(
    JNIEnv* env, jclass clazz, jint key_id, jbyteArray public_key, jbyteArray private_key, jbyteArray cert_out, jlong cert_len) {
    (void)clazz;

    // Get input arrays
    jbyte* public_key_jbyte = env->GetByteArrayElements(public_key, nullptr);
    jbyte* private_key_jbyte = env->GetByteArrayElements(private_key, nullptr);
    jbyte* cert_out_jbyte = env->GetByteArrayElements(cert_out, nullptr);
    uint8_t* cert_out_ptr = reinterpret_cast<uint8_t*>(cert_out_jbyte);

    // Declare output variables
    size_t cert_len_val = 0;

    // Call native function
    int rc = enchant_server_certificate_create(key_id, reinterpret_cast<const uint8_t*>(public_key_jbyte), reinterpret_cast<const uint8_t*>(private_key_jbyte), cert_out_ptr, &cert_len_val);

    // Release and copy back
    env->ReleaseByteArrayElements(public_key, public_key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(private_key, private_key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(cert_out, cert_out_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1server_1certificate_1validate(
    JNIEnv* env, jclass clazz, jbyteArray cert_data, jlong cert_len, jbyteArray trust_root, jintArray valid_out) {
    (void)clazz;

    // Get input arrays
    jbyte* cert_data_jbyte = env->GetByteArrayElements(cert_data, nullptr);
    jbyte* trust_root_jbyte = env->GetByteArrayElements(trust_root, nullptr);

    // Declare output variables
    int valid_out_val = 0;

    // Call native function
    int rc = enchant_server_certificate_validate(reinterpret_cast<const uint8_t*>(cert_data_jbyte), cert_len, reinterpret_cast<const uint8_t*>(trust_root_jbyte), &valid_out_val);

    // Release and copy back
    env->ReleaseByteArrayElements(cert_data, cert_data_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(trust_root, trust_root_jbyte, JNI_ABORT);

    // Copy output values back to Java
    jint* valid_out_elems = env->GetIntArrayElements(valid_out, nullptr);
    valid_out_elems[0] = valid_out_val;
    env->ReleaseIntArrayElements(valid_out, valid_out_elems, 0);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1sender_1certificate_1create(
    JNIEnv* env, jclass clazz, jstring sender_uuid, jbyteArray sender_e164, jint sender_device_id, jlong expiration, jbyteArray identity_key, jbyteArray server_cert_data, jlong server_cert_len, jbyteArray server_private_key, jbyteArray cert_out, jlong cert_len) {
    (void)clazz;

    // Get input arrays
    const char* sender_uuid_str = env->GetStringUTFChars(sender_uuid, nullptr);
    jbyte* sender_e164_jbyte = env->GetByteArrayElements(sender_e164, nullptr);
    jbyte* identity_key_jbyte = env->GetByteArrayElements(identity_key, nullptr);
    jbyte* server_cert_data_jbyte = env->GetByteArrayElements(server_cert_data, nullptr);
    jbyte* server_private_key_jbyte = env->GetByteArrayElements(server_private_key, nullptr);
    jbyte* cert_out_jbyte = env->GetByteArrayElements(cert_out, nullptr);
    uint8_t* cert_out_ptr = reinterpret_cast<uint8_t*>(cert_out_jbyte);

    // Declare output variables
    size_t cert_len_val = 0;

    // Call native function
    int rc = enchant_sender_certificate_create(sender_uuid_str, reinterpret_cast<const uint8_t*>(sender_e164_jbyte), sender_device_id, expiration, reinterpret_cast<const uint8_t*>(identity_key_jbyte), reinterpret_cast<const uint8_t*>(server_cert_data_jbyte), server_cert_len, reinterpret_cast<const uint8_t*>(server_private_key_jbyte), cert_out_ptr, &cert_len_val);

    // Release and copy back
    env->ReleaseStringUTFChars(sender_uuid, sender_uuid_str);
    env->ReleaseByteArrayElements(sender_e164, sender_e164_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(identity_key, identity_key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(server_cert_data, server_cert_data_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(server_private_key, server_private_key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(cert_out, cert_out_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1sender_1certificate_1validate(
    JNIEnv* env, jclass clazz, jbyteArray cert_data, jlong cert_len, jbyteArray server_trust_root, jlong validation_time, jintArray valid_out) {
    (void)clazz;

    // Get input arrays
    jbyte* cert_data_jbyte = env->GetByteArrayElements(cert_data, nullptr);
    jbyte* server_trust_root_jbyte = env->GetByteArrayElements(server_trust_root, nullptr);

    // Declare output variables
    int valid_out_val = 0;

    // Call native function
    int rc = enchant_sender_certificate_validate(reinterpret_cast<const uint8_t*>(cert_data_jbyte), cert_len, reinterpret_cast<const uint8_t*>(server_trust_root_jbyte), validation_time, &valid_out_val);

    // Release and copy back
    env->ReleaseByteArrayElements(cert_data, cert_data_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(server_trust_root, server_trust_root_jbyte, JNI_ABORT);

    // Copy output values back to Java
    jint* valid_out_elems = env->GetIntArrayElements(valid_out, nullptr);
    valid_out_elems[0] = valid_out_val;
    env->ReleaseIntArrayElements(valid_out, valid_out_elems, 0);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1usmc_1create(
    JNIEnv* env, jclass clazz, jint msg_type, jbyteArray sender_cert_data, jlong sender_cert_len, jbyteArray plaintext, jlong plaintext_len, jint content_hint, jbyteArray group_id, jlong group_id_len, jbyteArray usmc_out, jlong usmc_len) {
    (void)clazz;

    // Get input arrays
    jbyte* sender_cert_data_jbyte = env->GetByteArrayElements(sender_cert_data, nullptr);
    jbyte* plaintext_jbyte = env->GetByteArrayElements(plaintext, nullptr);
    jbyte* group_id_jbyte = env->GetByteArrayElements(group_id, nullptr);
    jbyte* usmc_out_jbyte = env->GetByteArrayElements(usmc_out, nullptr);
    uint8_t* usmc_out_ptr = reinterpret_cast<uint8_t*>(usmc_out_jbyte);

    // Declare output variables
    size_t usmc_len_val = 0;

    // Call native function
    int rc = enchant_usmc_create(msg_type, reinterpret_cast<const uint8_t*>(sender_cert_data_jbyte), sender_cert_len, reinterpret_cast<const uint8_t*>(plaintext_jbyte), plaintext_len, content_hint, reinterpret_cast<const uint8_t*>(group_id_jbyte), group_id_len, usmc_out_ptr, &usmc_len_val);

    // Release and copy back
    env->ReleaseByteArrayElements(sender_cert_data, sender_cert_data_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(plaintext, plaintext_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(group_id, group_id_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(usmc_out, usmc_out_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1usmc_1serialize(
    JNIEnv* env, jclass clazz, jbyteArray usmc_data, jlong usmc_len, jbyteArray output, jlong output_len) {
    (void)clazz;

    // Get input arrays
    jbyte* usmc_data_jbyte = env->GetByteArrayElements(usmc_data, nullptr);
    jbyte* output_jbyte = env->GetByteArrayElements(output, nullptr);
    uint8_t* output_ptr = reinterpret_cast<uint8_t*>(output_jbyte);

    // Declare output variables
    size_t output_len_val = 0;

    // Call native function
    int rc = enchant_usmc_serialize(reinterpret_cast<const uint8_t*>(usmc_data_jbyte), usmc_len, output_ptr, &output_len_val);

    // Release and copy back
    env->ReleaseByteArrayElements(usmc_data, usmc_data_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(output, output_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1usmc_1deserialize(
    JNIEnv* env, jclass clazz, jbyteArray data, jlong data_len, jbyteArray usmc_out, jlong usmc_len, jintArray msg_type_out) {
    (void)clazz;

    // Get input arrays
    jbyte* data_jbyte = env->GetByteArrayElements(data, nullptr);
    jbyte* usmc_out_jbyte = env->GetByteArrayElements(usmc_out, nullptr);
    uint8_t* usmc_out_ptr = reinterpret_cast<uint8_t*>(usmc_out_jbyte);

    // Declare output variables
    size_t usmc_len_val = 0;
    int msg_type_out_val = 0;

    // Call native function
    int rc = enchant_usmc_deserialize(reinterpret_cast<const uint8_t*>(data_jbyte), data_len, usmc_out_ptr, &usmc_len_val, &msg_type_out_val);

    // Release and copy back
    env->ReleaseByteArrayElements(data, data_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(usmc_out, usmc_out_jbyte, 0);

    // Copy output values back to Java
    jint* msg_type_out_elems = env->GetIntArrayElements(msg_type_out, nullptr);
    msg_type_out_elems[0] = msg_type_out_val;
    env->ReleaseIntArrayElements(msg_type_out, msg_type_out_elems, 0);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1prekey_1generate_1batch(
    JNIEnv* env, jclass clazz, jint count, jint start_id, jbyteArray keys_out, jlong keys_len) {
    (void)clazz;

    // Get input arrays
    jbyte* keys_out_jbyte = env->GetByteArrayElements(keys_out, nullptr);
    uint8_t* keys_out_ptr = reinterpret_cast<uint8_t*>(keys_out_jbyte);

    // Declare output variables
    size_t keys_len_val = 0;

    // Call native function
    int rc = enchant_prekey_generate_batch(count, start_id, keys_out_ptr, &keys_len_val);

    // Release and copy back
    env->ReleaseByteArrayElements(keys_out, keys_out_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1prekey_1generate_1signed(
    JNIEnv* env, jclass clazz, jint prekey_id, jbyteArray identity_private_key, jbyteArray signed_prekey_public, jbyteArray signed_prekey_private, jbyteArray signature, jlong signature_len) {
    (void)clazz;

    // Get input arrays
    jbyte* identity_private_key_jbyte = env->GetByteArrayElements(identity_private_key, nullptr);
    jbyte* signed_prekey_public_jbyte = env->GetByteArrayElements(signed_prekey_public, nullptr);
    uint8_t* signed_prekey_public_ptr = reinterpret_cast<uint8_t*>(signed_prekey_public_jbyte);
    jbyte* signed_prekey_private_jbyte = env->GetByteArrayElements(signed_prekey_private, nullptr);
    uint8_t* signed_prekey_private_ptr = reinterpret_cast<uint8_t*>(signed_prekey_private_jbyte);
    jbyte* signature_jbyte = env->GetByteArrayElements(signature, nullptr);
    uint8_t* signature_ptr = reinterpret_cast<uint8_t*>(signature_jbyte);

    // Declare output variables
    size_t signature_len_val = 0;

    // Call native function
    int rc = enchant_prekey_generate_signed(prekey_id, reinterpret_cast<const uint8_t*>(identity_private_key_jbyte), signed_prekey_public_ptr, signed_prekey_private_ptr, signature_ptr, &signature_len_val);

    // Release and copy back
    env->ReleaseByteArrayElements(identity_private_key, identity_private_key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(signed_prekey_public, signed_prekey_public_jbyte, 0);
    env->ReleaseByteArrayElements(signed_prekey_private, signed_prekey_private_jbyte, 0);
    env->ReleaseByteArrayElements(signature, signature_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1prekey_1generate_1kyber_1batch(
    JNIEnv* env, jclass clazz, jint count, jint start_id, jint kem_type, jbyteArray keys_out, jlong keys_len) {
    (void)clazz;

    // Get input arrays
    jbyte* keys_out_jbyte = env->GetByteArrayElements(keys_out, nullptr);
    uint8_t* keys_out_ptr = reinterpret_cast<uint8_t*>(keys_out_jbyte);

    // Declare output variables
    size_t keys_len_val = 0;

    // Call native function
    int rc = enchant_prekey_generate_kyber_batch(count, start_id, kem_type, keys_out_ptr, &keys_len_val);

    // Release and copy back
    env->ReleaseByteArrayElements(keys_out, keys_out_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1mls_1group_1create(
    JNIEnv* env, jclass clazz, jbyteArray group_id, jlong group_id_len, jbyteArray epoch_secret, jbyteArray group_state_out, jlong group_state_len) {
    (void)clazz;

    // Get input arrays
    jbyte* group_id_jbyte = env->GetByteArrayElements(group_id, nullptr);
    jbyte* epoch_secret_jbyte = env->GetByteArrayElements(epoch_secret, nullptr);
    jbyte* group_state_out_jbyte = env->GetByteArrayElements(group_state_out, nullptr);
    uint8_t* group_state_out_ptr = reinterpret_cast<uint8_t*>(group_state_out_jbyte);

    // Declare output variables
    size_t group_state_len_val = 0;

    // Call native function
    int rc = enchant_mls_group_create(reinterpret_cast<const uint8_t*>(group_id_jbyte), group_id_len, reinterpret_cast<const uint8_t*>(epoch_secret_jbyte), group_state_out_ptr, &group_state_len_val);

    // Release and copy back
    env->ReleaseByteArrayElements(group_id, group_id_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(epoch_secret, epoch_secret_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(group_state_out, group_state_out_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_encrypt_mls_message(
    JNIEnv* env, jclass clazz, jbyteArray group_state, jlong group_state_len, jbyteArray plaintext, jlong plaintext_len, jbyteArray ciphertext, jlong ciphertext_len) {
    (void)clazz;

    // Get input arrays
    jbyte* group_state_jbyte = env->GetByteArrayElements(group_state, nullptr);
    jbyte* plaintext_jbyte = env->GetByteArrayElements(plaintext, nullptr);
    jbyte* ciphertext_jbyte = env->GetByteArrayElements(ciphertext, nullptr);
    uint8_t* ciphertext_ptr = reinterpret_cast<uint8_t*>(ciphertext_jbyte);

    // Declare output variables
    size_t ciphertext_len_val = 0;

    // Call native function
    int rc = encrypt_mls_message(reinterpret_cast<const uint8_t*>(group_state_jbyte), group_state_len, reinterpret_cast<const uint8_t*>(plaintext_jbyte), plaintext_len, ciphertext_ptr, &ciphertext_len_val);

    // Release and copy back
    env->ReleaseByteArrayElements(group_state, group_state_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(plaintext, plaintext_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(ciphertext, ciphertext_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_decrypt_mls_message(
    JNIEnv* env, jclass clazz, jbyteArray group_state, jlong group_state_len, jbyteArray ciphertext, jlong ciphertext_len, jbyteArray plaintext, jlong plaintext_len) {
    (void)clazz;

    // Get input arrays
    jbyte* group_state_jbyte = env->GetByteArrayElements(group_state, nullptr);
    jbyte* ciphertext_jbyte = env->GetByteArrayElements(ciphertext, nullptr);
    jbyte* plaintext_jbyte = env->GetByteArrayElements(plaintext, nullptr);
    uint8_t* plaintext_ptr = reinterpret_cast<uint8_t*>(plaintext_jbyte);

    // Declare output variables
    size_t plaintext_len_val = 0;

    // Call native function
    int rc = decrypt_mls_message(reinterpret_cast<const uint8_t*>(group_state_jbyte), group_state_len, reinterpret_cast<const uint8_t*>(ciphertext_jbyte), ciphertext_len, plaintext_ptr, &plaintext_len_val);

    // Release and copy back
    env->ReleaseByteArrayElements(group_state, group_state_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(ciphertext, ciphertext_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(plaintext, plaintext_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1agent_1identity_1create(
    JNIEnv* env, jclass clazz, jbyteArray public_key, jbyteArray private_key) {
    (void)clazz;

    // Get input arrays
    jbyte* public_key_jbyte = env->GetByteArrayElements(public_key, nullptr);
    uint8_t* public_key_ptr = reinterpret_cast<uint8_t*>(public_key_jbyte);
    jbyte* private_key_jbyte = env->GetByteArrayElements(private_key, nullptr);
    uint8_t* private_key_ptr = reinterpret_cast<uint8_t*>(private_key_jbyte);

    // Declare output variables

    // Call native function
    int rc = enchant_agent_identity_create(public_key_ptr, private_key_ptr);

    // Release and copy back
    env->ReleaseByteArrayElements(public_key, public_key_jbyte, 0);
    env->ReleaseByteArrayElements(private_key, private_key_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1agent_1session_1initiate(
    JNIEnv* env, jclass clazz, jbyteArray agent_private_key, jbyteArray server_public_key, jbyteArray shared_secret, jbyteArray ephemeral_public) {
    (void)clazz;

    // Get input arrays
    jbyte* agent_private_key_jbyte = env->GetByteArrayElements(agent_private_key, nullptr);
    jbyte* server_public_key_jbyte = env->GetByteArrayElements(server_public_key, nullptr);
    jbyte* shared_secret_jbyte = env->GetByteArrayElements(shared_secret, nullptr);
    uint8_t* shared_secret_ptr = reinterpret_cast<uint8_t*>(shared_secret_jbyte);
    jbyte* ephemeral_public_jbyte = env->GetByteArrayElements(ephemeral_public, nullptr);
    uint8_t* ephemeral_public_ptr = reinterpret_cast<uint8_t*>(ephemeral_public_jbyte);

    // Declare output variables

    // Call native function
    int rc = enchant_agent_session_initiate(reinterpret_cast<const uint8_t*>(agent_private_key_jbyte), reinterpret_cast<const uint8_t*>(server_public_key_jbyte), shared_secret_ptr, ephemeral_public_ptr);

    // Release and copy back
    env->ReleaseByteArrayElements(agent_private_key, agent_private_key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(server_public_key, server_public_key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(shared_secret, shared_secret_jbyte, 0);
    env->ReleaseByteArrayElements(ephemeral_public, ephemeral_public_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1agent_1session_1respond(
    JNIEnv* env, jclass clazz, jbyteArray server_private_key, jbyteArray agent_public_key, jbyteArray agent_ephemeral_public, jbyteArray shared_secret) {
    (void)clazz;

    // Get input arrays
    jbyte* server_private_key_jbyte = env->GetByteArrayElements(server_private_key, nullptr);
    jbyte* agent_public_key_jbyte = env->GetByteArrayElements(agent_public_key, nullptr);
    jbyte* agent_ephemeral_public_jbyte = env->GetByteArrayElements(agent_ephemeral_public, nullptr);
    jbyte* shared_secret_jbyte = env->GetByteArrayElements(shared_secret, nullptr);
    uint8_t* shared_secret_ptr = reinterpret_cast<uint8_t*>(shared_secret_jbyte);

    // Declare output variables

    // Call native function
    int rc = enchant_agent_session_respond(reinterpret_cast<const uint8_t*>(server_private_key_jbyte), reinterpret_cast<const uint8_t*>(agent_public_key_jbyte), reinterpret_cast<const uint8_t*>(agent_ephemeral_public_jbyte), shared_secret_ptr);

    // Release and copy back
    env->ReleaseByteArrayElements(server_private_key, server_private_key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(agent_public_key, agent_public_key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(agent_ephemeral_public, agent_ephemeral_public_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(shared_secret, shared_secret_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1agent_1encrypt(
    JNIEnv* env, jclass clazz, jbyteArray key, jlong key_len, jbyteArray plaintext, jlong plaintext_len, jbyteArray ciphertext, jlong ciphertext_len) {
    (void)clazz;

    // Get input arrays
    jbyte* key_jbyte = env->GetByteArrayElements(key, nullptr);
    jbyte* plaintext_jbyte = env->GetByteArrayElements(plaintext, nullptr);
    jbyte* ciphertext_jbyte = env->GetByteArrayElements(ciphertext, nullptr);
    uint8_t* ciphertext_ptr = reinterpret_cast<uint8_t*>(ciphertext_jbyte);

    // Declare output variables
    size_t ciphertext_len_val = 0;

    // Call native function
    int rc = enchant_agent_encrypt(reinterpret_cast<const uint8_t*>(key_jbyte), key_len, reinterpret_cast<const uint8_t*>(plaintext_jbyte), plaintext_len, ciphertext_ptr, &ciphertext_len_val);

    // Release and copy back
    env->ReleaseByteArrayElements(key, key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(plaintext, plaintext_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(ciphertext, ciphertext_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1agent_1decrypt(
    JNIEnv* env, jclass clazz, jbyteArray key, jlong key_len, jbyteArray ciphertext, jlong ciphertext_len, jbyteArray plaintext, jlong plaintext_len) {
    (void)clazz;

    // Get input arrays
    jbyte* key_jbyte = env->GetByteArrayElements(key, nullptr);
    jbyte* ciphertext_jbyte = env->GetByteArrayElements(ciphertext, nullptr);
    jbyte* plaintext_jbyte = env->GetByteArrayElements(plaintext, nullptr);
    uint8_t* plaintext_ptr = reinterpret_cast<uint8_t*>(plaintext_jbyte);

    // Declare output variables
    size_t plaintext_len_val = 0;

    // Call native function
    int rc = enchant_agent_decrypt(reinterpret_cast<const uint8_t*>(key_jbyte), key_len, reinterpret_cast<const uint8_t*>(ciphertext_jbyte), ciphertext_len, plaintext_ptr, &plaintext_len_val);

    // Release and copy back
    env->ReleaseByteArrayElements(key, key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(ciphertext, ciphertext_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(plaintext, plaintext_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1multi_1recipient_1encrypt(
    JNIEnv* env, jclass clazz, jbyteArray plaintext, jlong plaintext_len, jlong recipient_keys, jlong num_recipients, jbyteArray ciphertext, jlong ciphertext_len) {
    (void)clazz;

    // Get input arrays
    jbyte* plaintext_jbyte = env->GetByteArrayElements(plaintext, nullptr);
    jlong recipient_keys_val = 0;
    jbyte* ciphertext_jbyte = env->GetByteArrayElements(ciphertext, nullptr);
    uint8_t* ciphertext_ptr = reinterpret_cast<uint8_t*>(ciphertext_jbyte);

    // Declare output variables
    size_t ciphertext_len_val = 0;

    // Call native function
    int rc = enchant_multi_recipient_encrypt(reinterpret_cast<const uint8_t*>(plaintext_jbyte), plaintext_len, (const uint8_t* const*)(long)(recipient_keys_val), num_recipients, ciphertext_ptr, &ciphertext_len_val);

    // Release and copy back
    env->ReleaseByteArrayElements(plaintext, plaintext_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(ciphertext, ciphertext_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1multi_1recipient_1decrypt(
    JNIEnv* env, jclass clazz, jbyteArray ciphertext, jlong ciphertext_len, jbyteArray private_key, jbyteArray plaintext, jlong plaintext_len) {
    (void)clazz;

    // Get input arrays
    jbyte* ciphertext_jbyte = env->GetByteArrayElements(ciphertext, nullptr);
    jbyte* private_key_jbyte = env->GetByteArrayElements(private_key, nullptr);
    jbyte* plaintext_jbyte = env->GetByteArrayElements(plaintext, nullptr);
    uint8_t* plaintext_ptr = reinterpret_cast<uint8_t*>(plaintext_jbyte);

    // Declare output variables
    size_t plaintext_len_val = 0;

    // Call native function
    int rc = enchant_multi_recipient_decrypt(reinterpret_cast<const uint8_t*>(ciphertext_jbyte), ciphertext_len, reinterpret_cast<const uint8_t*>(private_key_jbyte), plaintext_ptr, &plaintext_len_val);

    // Release and copy back
    env->ReleaseByteArrayElements(ciphertext, ciphertext_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(private_key, private_key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(plaintext, plaintext_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1delete_1message_1key(
    JNIEnv* env, jclass clazz, jbyteArray chain_key, jlong chain_key_len, jint message_number) {
    (void)clazz;

    // Get input arrays
    jbyte* chain_key_jbyte = env->GetByteArrayElements(chain_key, nullptr);
    uint8_t* chain_key_ptr = reinterpret_cast<uint8_t*>(chain_key_jbyte);

    // Declare output variables

    // Call native function
    int rc = enchant_delete_message_key(chain_key_ptr, chain_key_len, message_number);

    // Release and copy back
    env->ReleaseByteArrayElements(chain_key, chain_key_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1clear_1consumed_1keys(
    JNIEnv* env, jclass clazz, jbyteArray skipped_keys, jlong skipped_keys_len, jint max_keys) {
    (void)clazz;

    // Get input arrays
    jbyte* skipped_keys_jbyte = env->GetByteArrayElements(skipped_keys, nullptr);
    uint8_t* skipped_keys_ptr = reinterpret_cast<uint8_t*>(skipped_keys_jbyte);

    // Declare output variables

    // Call native function
    int rc = enchant_clear_consumed_keys(skipped_keys_ptr, skipped_keys_len, max_keys);

    // Release and copy back
    env->ReleaseByteArrayElements(skipped_keys, skipped_keys_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1profile_1encrypt(
    JNIEnv* env, jclass clazz, jbyteArray profile_key, jbyteArray plaintext, jlong plaintext_len, jbyteArray ciphertext, jlong ciphertext_len) {
    (void)clazz;

    // Get input arrays
    jbyte* profile_key_jbyte = env->GetByteArrayElements(profile_key, nullptr);
    jbyte* plaintext_jbyte = env->GetByteArrayElements(plaintext, nullptr);
    jbyte* ciphertext_jbyte = env->GetByteArrayElements(ciphertext, nullptr);
    uint8_t* ciphertext_ptr = reinterpret_cast<uint8_t*>(ciphertext_jbyte);

    // Declare output variables
    size_t ciphertext_len_val = 0;

    // Call native function
    int rc = enchant_profile_encrypt(reinterpret_cast<const uint8_t*>(profile_key_jbyte), reinterpret_cast<const uint8_t*>(plaintext_jbyte), plaintext_len, ciphertext_ptr, &ciphertext_len_val);

    // Release and copy back
    env->ReleaseByteArrayElements(profile_key, profile_key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(plaintext, plaintext_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(ciphertext, ciphertext_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1profile_1decrypt(
    JNIEnv* env, jclass clazz, jbyteArray profile_key, jbyteArray ciphertext, jlong ciphertext_len, jbyteArray plaintext, jlong plaintext_len) {
    (void)clazz;

    // Get input arrays
    jbyte* profile_key_jbyte = env->GetByteArrayElements(profile_key, nullptr);
    jbyte* ciphertext_jbyte = env->GetByteArrayElements(ciphertext, nullptr);
    jbyte* plaintext_jbyte = env->GetByteArrayElements(plaintext, nullptr);
    uint8_t* plaintext_ptr = reinterpret_cast<uint8_t*>(plaintext_jbyte);

    // Declare output variables
    size_t plaintext_len_val = 0;

    // Call native function
    int rc = enchant_profile_decrypt(reinterpret_cast<const uint8_t*>(profile_key_jbyte), reinterpret_cast<const uint8_t*>(ciphertext_jbyte), ciphertext_len, plaintext_ptr, &plaintext_len_val);

    // Release and copy back
    env->ReleaseByteArrayElements(profile_key, profile_key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(ciphertext, ciphertext_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(plaintext, plaintext_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1svr_1create_1backup(
    JNIEnv* env, jclass clazz, jbyteArray pin, jlong pin_len, jbyteArray master_key, jbyteArray backup_out, jlong backup_len) {
    (void)clazz;

    // Get input arrays
    jbyte* pin_jbyte = env->GetByteArrayElements(pin, nullptr);
    jbyte* master_key_jbyte = env->GetByteArrayElements(master_key, nullptr);
    jbyte* backup_out_jbyte = env->GetByteArrayElements(backup_out, nullptr);
    uint8_t* backup_out_ptr = reinterpret_cast<uint8_t*>(backup_out_jbyte);

    // Declare output variables
    size_t backup_len_val = 0;

    // Call native function
    int rc = enchant_svr_create_backup(reinterpret_cast<const uint8_t*>(pin_jbyte), pin_len, reinterpret_cast<const uint8_t*>(master_key_jbyte), backup_out_ptr, &backup_len_val);

    // Release and copy back
    env->ReleaseByteArrayElements(pin, pin_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(master_key, master_key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(backup_out, backup_out_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1svr_1restore_1backup(
    JNIEnv* env, jclass clazz, jbyteArray pin, jlong pin_len, jbyteArray backup, jlong backup_len, jbyteArray master_key_out) {
    (void)clazz;

    // Get input arrays
    jbyte* pin_jbyte = env->GetByteArrayElements(pin, nullptr);
    jbyte* backup_jbyte = env->GetByteArrayElements(backup, nullptr);
    jbyte* master_key_out_jbyte = env->GetByteArrayElements(master_key_out, nullptr);
    uint8_t* master_key_out_ptr = reinterpret_cast<uint8_t*>(master_key_out_jbyte);

    // Declare output variables

    // Call native function
    int rc = enchant_svr_restore_backup(reinterpret_cast<const uint8_t*>(pin_jbyte), pin_len, reinterpret_cast<const uint8_t*>(backup_jbyte), backup_len, master_key_out_ptr);

    // Release and copy back
    env->ReleaseByteArrayElements(pin, pin_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(backup, backup_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(master_key_out, master_key_out_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1svr_1change_1pin(
    JNIEnv* env, jclass clazz, jbyteArray old_pin, jlong old_pin_len, jbyteArray new_pin, jlong new_pin_len, jbyteArray backup, jlong backup_len, jbyteArray new_backup_out, jlong new_backup_len) {
    (void)clazz;

    // Get input arrays
    jbyte* old_pin_jbyte = env->GetByteArrayElements(old_pin, nullptr);
    jbyte* new_pin_jbyte = env->GetByteArrayElements(new_pin, nullptr);
    jbyte* backup_jbyte = env->GetByteArrayElements(backup, nullptr);
    jbyte* new_backup_out_jbyte = env->GetByteArrayElements(new_backup_out, nullptr);
    uint8_t* new_backup_out_ptr = reinterpret_cast<uint8_t*>(new_backup_out_jbyte);

    // Declare output variables
    size_t new_backup_len_val = 0;

    // Call native function
    int rc = enchant_svr_change_pin(reinterpret_cast<const uint8_t*>(old_pin_jbyte), old_pin_len, reinterpret_cast<const uint8_t*>(new_pin_jbyte), new_pin_len, reinterpret_cast<const uint8_t*>(backup_jbyte), backup_len, new_backup_out_ptr, &new_backup_len_val);

    // Release and copy back
    env->ReleaseByteArrayElements(old_pin, old_pin_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(new_pin, new_pin_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(backup, backup_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(new_backup_out, new_backup_out_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1svr_1generate_1auth_1proof(
    JNIEnv* env, jclass clazz, jbyteArray server_private_key, jbyteArray client_id, jlong client_id_len, jbyteArray proof_out, jbyteArray nonce_out, jlong timestamp_out) {
    (void)clazz;

    // Get input arrays
    jbyte* server_private_key_jbyte = env->GetByteArrayElements(server_private_key, nullptr);
    jbyte* client_id_jbyte = env->GetByteArrayElements(client_id, nullptr);
    jbyte* proof_out_jbyte = env->GetByteArrayElements(proof_out, nullptr);
    uint8_t* proof_out_ptr = reinterpret_cast<uint8_t*>(proof_out_jbyte);
    jbyte* nonce_out_jbyte = env->GetByteArrayElements(nonce_out, nullptr);
    uint8_t* nonce_out_ptr = reinterpret_cast<uint8_t*>(nonce_out_jbyte);

    // Declare output variables
    uint64_t timestamp_out_val = 0;

    // Call native function
    int rc = enchant_svr_generate_auth_proof(reinterpret_cast<const uint8_t*>(server_private_key_jbyte), reinterpret_cast<const uint8_t*>(client_id_jbyte), client_id_len, proof_out_ptr, nonce_out_ptr, &timestamp_out_val);

    // Release and copy back
    env->ReleaseByteArrayElements(server_private_key, server_private_key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(client_id, client_id_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(proof_out, proof_out_jbyte, 0);
    env->ReleaseByteArrayElements(nonce_out, nonce_out_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1svr_1verify_1auth_1proof(
    JNIEnv* env, jclass clazz, jbyteArray server_private_key, jbyteArray client_id, jlong client_id_len, jbyteArray proof, jbyteArray nonce, jlong timestamp) {
    (void)clazz;

    // Get input arrays
    jbyte* server_private_key_jbyte = env->GetByteArrayElements(server_private_key, nullptr);
    jbyte* client_id_jbyte = env->GetByteArrayElements(client_id, nullptr);
    jbyte* proof_jbyte = env->GetByteArrayElements(proof, nullptr);
    jbyte* nonce_jbyte = env->GetByteArrayElements(nonce, nullptr);

    // Declare output variables

    // Call native function
    int rc = enchant_svr_verify_auth_proof(reinterpret_cast<const uint8_t*>(server_private_key_jbyte), reinterpret_cast<const uint8_t*>(client_id_jbyte), client_id_len, reinterpret_cast<const uint8_t*>(proof_jbyte), reinterpret_cast<const uint8_t*>(nonce_jbyte), timestamp);

    // Release and copy back
    env->ReleaseByteArrayElements(server_private_key, server_private_key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(client_id, client_id_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(proof, proof_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(nonce, nonce_jbyte, JNI_ABORT);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1account_1entropy_1create(
    JNIEnv* env, jclass clazz, jbyteArray entropy_out) {
    (void)clazz;

    // Get input arrays
    jbyte* entropy_out_jbyte = env->GetByteArrayElements(entropy_out, nullptr);
    uint8_t* entropy_out_ptr = reinterpret_cast<uint8_t*>(entropy_out_jbyte);

    // Declare output variables

    // Call native function
    int rc = enchant_account_entropy_create(entropy_out_ptr);

    // Release and copy back
    env->ReleaseByteArrayElements(entropy_out, entropy_out_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1account_1entropy_1from_1passphrase(
    JNIEnv* env, jclass clazz, jstring passphrase, jlong passphrase_len, jbyteArray entropy_out) {
    (void)clazz;

    // Get input arrays
    const char* passphrase_str = env->GetStringUTFChars(passphrase, nullptr);
    jbyte* entropy_out_jbyte = env->GetByteArrayElements(entropy_out, nullptr);
    uint8_t* entropy_out_ptr = reinterpret_cast<uint8_t*>(entropy_out_jbyte);

    // Declare output variables

    // Call native function
    int rc = enchant_account_entropy_from_passphrase(passphrase_str, passphrase_len, entropy_out_ptr);

    // Release and copy back
    env->ReleaseStringUTFChars(passphrase, passphrase_str);
    env->ReleaseByteArrayElements(entropy_out, entropy_out_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1account_1key_1derive(
    JNIEnv* env, jclass clazz, jbyteArray entropy, jbyteArray account_key_out) {
    (void)clazz;

    // Get input arrays
    jbyte* entropy_jbyte = env->GetByteArrayElements(entropy, nullptr);
    jbyte* account_key_out_jbyte = env->GetByteArrayElements(account_key_out, nullptr);
    uint8_t* account_key_out_ptr = reinterpret_cast<uint8_t*>(account_key_out_jbyte);

    // Declare output variables

    // Call native function
    int rc = enchant_account_key_derive(reinterpret_cast<const uint8_t*>(entropy_jbyte), account_key_out_ptr);

    // Release and copy back
    env->ReleaseByteArrayElements(entropy, entropy_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(account_key_out, account_key_out_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1backup_1key_1derive(
    JNIEnv* env, jclass clazz, jbyteArray account_key, jbyteArray backup_key_out) {
    (void)clazz;

    // Get input arrays
    jbyte* account_key_jbyte = env->GetByteArrayElements(account_key, nullptr);
    jbyte* backup_key_out_jbyte = env->GetByteArrayElements(backup_key_out, nullptr);
    uint8_t* backup_key_out_ptr = reinterpret_cast<uint8_t*>(backup_key_out_jbyte);

    // Declare output variables

    // Call native function
    int rc = enchant_backup_key_derive(reinterpret_cast<const uint8_t*>(account_key_jbyte), backup_key_out_ptr);

    // Release and copy back
    env->ReleaseByteArrayElements(account_key, account_key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(backup_key_out, backup_key_out_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1media_1key_1derive(
    JNIEnv* env, jclass clazz, jbyteArray account_key, jstring media_id, jbyteArray media_key_out) {
    (void)clazz;

    // Get input arrays
    jbyte* account_key_jbyte = env->GetByteArrayElements(account_key, nullptr);
    const char* media_id_str = env->GetStringUTFChars(media_id, nullptr);
    jbyte* media_key_out_jbyte = env->GetByteArrayElements(media_key_out, nullptr);
    uint8_t* media_key_out_ptr = reinterpret_cast<uint8_t*>(media_key_out_jbyte);

    // Declare output variables

    // Call native function
    int rc = enchant_media_key_derive(reinterpret_cast<const uint8_t*>(account_key_jbyte), media_id_str, media_key_out_ptr);

    // Release and copy back
    env->ReleaseByteArrayElements(account_key, account_key_jbyte, JNI_ABORT);
    env->ReleaseStringUTFChars(media_id, media_id_str);
    env->ReleaseByteArrayElements(media_key_out, media_key_out_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1auth_1credential_1present(
    JNIEnv* env, jclass clazz, jbyteArray credential_data, jlong credential_len, jbyteArray server_params, jbyteArray presentation_out, jlong presentation_len) {
    (void)clazz;

    // Get input arrays
    jbyte* credential_data_jbyte = env->GetByteArrayElements(credential_data, nullptr);
    jbyte* server_params_jbyte = env->GetByteArrayElements(server_params, nullptr);
    jbyte* presentation_out_jbyte = env->GetByteArrayElements(presentation_out, nullptr);
    uint8_t* presentation_out_ptr = reinterpret_cast<uint8_t*>(presentation_out_jbyte);

    // Declare output variables
    size_t presentation_len_val = 0;

    // Call native function
    int rc = enchant_auth_credential_present(reinterpret_cast<const uint8_t*>(credential_data_jbyte), credential_len, reinterpret_cast<const uint8_t*>(server_params_jbyte), presentation_out_ptr, &presentation_len_val);

    // Release and copy back
    env->ReleaseByteArrayElements(credential_data, credential_data_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(server_params, server_params_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(presentation_out, presentation_out_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1profile_1key_1credential_1present(
    JNIEnv* env, jclass clazz, jbyteArray credential_data, jlong credential_len, jbyteArray server_params, jbyteArray presentation_out, jlong presentation_len) {
    (void)clazz;

    // Get input arrays
    jbyte* credential_data_jbyte = env->GetByteArrayElements(credential_data, nullptr);
    jbyte* server_params_jbyte = env->GetByteArrayElements(server_params, nullptr);
    jbyte* presentation_out_jbyte = env->GetByteArrayElements(presentation_out, nullptr);
    uint8_t* presentation_out_ptr = reinterpret_cast<uint8_t*>(presentation_out_jbyte);

    // Declare output variables
    size_t presentation_len_val = 0;

    // Call native function
    int rc = enchant_profile_key_credential_present(reinterpret_cast<const uint8_t*>(credential_data_jbyte), credential_len, reinterpret_cast<const uint8_t*>(server_params_jbyte), presentation_out_ptr, &presentation_len_val);

    // Release and copy back
    env->ReleaseByteArrayElements(credential_data, credential_data_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(server_params, server_params_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(presentation_out, presentation_out_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1group_1credential_1present(
    JNIEnv* env, jclass clazz, jbyteArray credential_data, jlong credential_len, jbyteArray server_params, jbyteArray presentation_out, jlong presentation_len) {
    (void)clazz;

    // Get input arrays
    jbyte* credential_data_jbyte = env->GetByteArrayElements(credential_data, nullptr);
    jbyte* server_params_jbyte = env->GetByteArrayElements(server_params, nullptr);
    jbyte* presentation_out_jbyte = env->GetByteArrayElements(presentation_out, nullptr);
    uint8_t* presentation_out_ptr = reinterpret_cast<uint8_t*>(presentation_out_jbyte);

    // Declare output variables
    size_t presentation_len_val = 0;

    // Call native function
    int rc = enchant_group_credential_present(reinterpret_cast<const uint8_t*>(credential_data_jbyte), credential_len, reinterpret_cast<const uint8_t*>(server_params_jbyte), presentation_out_ptr, &presentation_len_val);

    // Release and copy back
    env->ReleaseByteArrayElements(credential_data, credential_data_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(server_params, server_params_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(presentation_out, presentation_out_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1key_1transparency_1generate_1keypair(
    JNIEnv* env, jclass clazz, jbyteArray public_key, jbyteArray private_key) {
    (void)clazz;

    // Get input arrays
    jbyte* public_key_jbyte = env->GetByteArrayElements(public_key, nullptr);
    uint8_t* public_key_ptr = reinterpret_cast<uint8_t*>(public_key_jbyte);
    jbyte* private_key_jbyte = env->GetByteArrayElements(private_key, nullptr);
    uint8_t* private_key_ptr = reinterpret_cast<uint8_t*>(private_key_jbyte);

    // Declare output variables

    // Call native function
    int rc = enchant_key_transparency_generate_keypair(public_key_ptr, private_key_ptr);

    // Release and copy back
    env->ReleaseByteArrayElements(public_key, public_key_jbyte, 0);
    env->ReleaseByteArrayElements(private_key, private_key_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1key_1transparency_1prove(
    JNIEnv* env, jclass clazz, jbyteArray private_key, jbyteArray commitment, jbyteArray proof_out, jlong proof_len) {
    (void)clazz;

    // Get input arrays
    jbyte* private_key_jbyte = env->GetByteArrayElements(private_key, nullptr);
    jbyte* commitment_jbyte = env->GetByteArrayElements(commitment, nullptr);
    jbyte* proof_out_jbyte = env->GetByteArrayElements(proof_out, nullptr);
    uint8_t* proof_out_ptr = reinterpret_cast<uint8_t*>(proof_out_jbyte);

    // Declare output variables
    size_t proof_len_val = 0;

    // Call native function
    int rc = enchant_key_transparency_prove(reinterpret_cast<const uint8_t*>(private_key_jbyte), reinterpret_cast<const uint8_t*>(commitment_jbyte), proof_out_ptr, &proof_len_val);

    // Release and copy back
    env->ReleaseByteArrayElements(private_key, private_key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(commitment, commitment_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(proof_out, proof_out_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1key_1transparency_1verify(
    JNIEnv* env, jclass clazz, jbyteArray public_key, jbyteArray commitment, jbyteArray proof, jlong proof_len, jintArray valid_out) {
    (void)clazz;

    // Get input arrays
    jbyte* public_key_jbyte = env->GetByteArrayElements(public_key, nullptr);
    jbyte* commitment_jbyte = env->GetByteArrayElements(commitment, nullptr);
    jbyte* proof_jbyte = env->GetByteArrayElements(proof, nullptr);

    // Declare output variables
    int valid_out_val = 0;

    // Call native function
    int rc = enchant_key_transparency_verify(reinterpret_cast<const uint8_t*>(public_key_jbyte), reinterpret_cast<const uint8_t*>(commitment_jbyte), reinterpret_cast<const uint8_t*>(proof_jbyte), proof_len, &valid_out_val);

    // Release and copy back
    env->ReleaseByteArrayElements(public_key, public_key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(commitment, commitment_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(proof, proof_jbyte, JNI_ABORT);

    // Copy output values back to Java
    jint* valid_out_elems = env->GetIntArrayElements(valid_out, nullptr);
    valid_out_elems[0] = valid_out_val;
    env->ReleaseIntArrayElements(valid_out, valid_out_elems, 0);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1key_1transparency_1vrf_1prove(
    JNIEnv* env, jclass clazz, jbyteArray private_key, jbyteArray message, jlong message_len, jbyteArray proof_out) {
    (void)clazz;

    // Get input arrays
    jbyte* private_key_jbyte = env->GetByteArrayElements(private_key, nullptr);
    jbyte* message_jbyte = env->GetByteArrayElements(message, nullptr);
    jbyte* proof_out_jbyte = env->GetByteArrayElements(proof_out, nullptr);
    uint8_t* proof_out_ptr = reinterpret_cast<uint8_t*>(proof_out_jbyte);

    // Declare output variables

    // Call native function
    int rc = enchant_key_transparency_vrf_prove(reinterpret_cast<const uint8_t*>(private_key_jbyte), reinterpret_cast<const uint8_t*>(message_jbyte), message_len, proof_out_ptr);

    // Release and copy back
    env->ReleaseByteArrayElements(private_key, private_key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(message, message_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(proof_out, proof_out_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1key_1transparency_1vrf_1verify(
    JNIEnv* env, jclass clazz, jbyteArray public_key, jbyteArray message, jlong message_len, jbyteArray proof, jintArray valid_out) {
    (void)clazz;

    // Get input arrays
    jbyte* public_key_jbyte = env->GetByteArrayElements(public_key, nullptr);
    jbyte* message_jbyte = env->GetByteArrayElements(message, nullptr);
    jbyte* proof_jbyte = env->GetByteArrayElements(proof, nullptr);

    // Declare output variables
    int valid_out_val = 0;

    // Call native function
    int rc = enchant_key_transparency_vrf_verify(reinterpret_cast<const uint8_t*>(public_key_jbyte), reinterpret_cast<const uint8_t*>(message_jbyte), message_len, reinterpret_cast<const uint8_t*>(proof_jbyte), &valid_out_val);

    // Release and copy back
    env->ReleaseByteArrayElements(public_key, public_key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(message, message_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(proof, proof_jbyte, JNI_ABORT);

    // Copy output values back to Java
    jint* valid_out_elems = env->GetIntArrayElements(valid_out, nullptr);
    valid_out_elems[0] = valid_out_val;
    env->ReleaseIntArrayElements(valid_out, valid_out_elems, 0);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1key_1transparency_1vrf_1proof_1to_1hash(
    JNIEnv* env, jclass clazz, jbyteArray proof, jbyteArray hash_out) {
    (void)clazz;

    // Get input arrays
    jbyte* proof_jbyte = env->GetByteArrayElements(proof, nullptr);
    jbyte* hash_out_jbyte = env->GetByteArrayElements(hash_out, nullptr);
    uint8_t* hash_out_ptr = reinterpret_cast<uint8_t*>(hash_out_jbyte);

    // Declare output variables

    // Call native function
    int rc = enchant_key_transparency_vrf_proof_to_hash(reinterpret_cast<const uint8_t*>(proof_jbyte), hash_out_ptr);

    // Release and copy back
    env->ReleaseByteArrayElements(proof, proof_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(hash_out, hash_out_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1key_1transparency_1verify_1search(
    JNIEnv* env, jclass clazz, jbyteArray vrf_public_key, jbyteArray user_id, jlong user_id_len, jbyteArray expected_value, jlong expected_value_len, jbyteArray vrf_proof, jlong vrf_proof_len, jintArray valid_out) {
    (void)clazz;

    // Get input arrays
    jbyte* vrf_public_key_jbyte = env->GetByteArrayElements(vrf_public_key, nullptr);
    jbyte* user_id_jbyte = env->GetByteArrayElements(user_id, nullptr);
    jbyte* expected_value_jbyte = env->GetByteArrayElements(expected_value, nullptr);
    jbyte* vrf_proof_jbyte = env->GetByteArrayElements(vrf_proof, nullptr);

    // Declare output variables
    int valid_out_val = 0;

    // Call native function
    int rc = enchant_key_transparency_verify_search(reinterpret_cast<const uint8_t*>(vrf_public_key_jbyte), reinterpret_cast<const uint8_t*>(user_id_jbyte), user_id_len, reinterpret_cast<const uint8_t*>(expected_value_jbyte), expected_value_len, reinterpret_cast<const uint8_t*>(vrf_proof_jbyte), vrf_proof_len, &valid_out_val);

    // Release and copy back
    env->ReleaseByteArrayElements(vrf_public_key, vrf_public_key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(user_id, user_id_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(expected_value, expected_value_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(vrf_proof, vrf_proof_jbyte, JNI_ABORT);

    // Copy output values back to Java
    jint* valid_out_elems = env->GetIntArrayElements(valid_out, nullptr);
    valid_out_elems[0] = valid_out_val;
    env->ReleaseIntArrayElements(valid_out, valid_out_elems, 0);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1streaming_1aead_1encrypt_1init(
    JNIEnv* env, jclass clazz, jlongArray handle_out, jbyteArray key, jbyteArray nonce, jbyteArray aad, jlong aad_len) {
    (void)clazz;

    // Get input arrays
    jlong* handle_out_elems = env->GetLongArrayElements(handle_out, nullptr);
    enchant_streaming_aead_t handle_out_val = nullptr;
    jbyte* key_jbyte = env->GetByteArrayElements(key, nullptr);
    jbyte* nonce_jbyte = env->GetByteArrayElements(nonce, nullptr);
    jbyte* aad_jbyte = env->GetByteArrayElements(aad, nullptr);

    // Declare output variables

    // Call native function
    int rc = enchant_streaming_aead_encrypt_init(&handle_out_val, reinterpret_cast<const uint8_t*>(key_jbyte), reinterpret_cast<const uint8_t*>(nonce_jbyte), reinterpret_cast<const uint8_t*>(aad_jbyte), aad_len);

    // Release and copy back
    env->ReleaseByteArrayElements(key, key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(nonce, nonce_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(aad, aad_jbyte, JNI_ABORT);

    // Copy output values back to Java
    handle_out_elems[0] = (jlong)(long)handle_out_val;
    env->ReleaseLongArrayElements(handle_out, handle_out_elems, 0);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1streaming_1aead_1encrypt_1update(
    JNIEnv* env, jclass clazz, jlong handle, jbyteArray plaintext, jlong plaintext_len, jbyteArray ciphertext, jlong ciphertext_len) {
    (void)clazz;

    // Get input arrays
    jbyte* plaintext_jbyte = env->GetByteArrayElements(plaintext, nullptr);
    jbyte* ciphertext_jbyte = env->GetByteArrayElements(ciphertext, nullptr);
    uint8_t* ciphertext_ptr = reinterpret_cast<uint8_t*>(ciphertext_jbyte);

    // Declare output variables
    size_t ciphertext_len_val = 0;

    // Call native function
    int rc = enchant_streaming_aead_encrypt_update((enchant_streaming_aead_t)(long)(handle), reinterpret_cast<const uint8_t*>(plaintext_jbyte), plaintext_len, ciphertext_ptr, &ciphertext_len_val);

    // Release and copy back
    env->ReleaseByteArrayElements(plaintext, plaintext_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(ciphertext, ciphertext_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1streaming_1aead_1encrypt_1finalize(
    JNIEnv* env, jclass clazz, jlong handle, jbyteArray tag_out) {
    (void)clazz;

    // Get input arrays
    jbyte* tag_out_jbyte = env->GetByteArrayElements(tag_out, nullptr);
    uint8_t* tag_out_ptr = reinterpret_cast<uint8_t*>(tag_out_jbyte);

    // Declare output variables

    // Call native function
    int rc = enchant_streaming_aead_encrypt_finalize((enchant_streaming_aead_t)(long)(handle), tag_out_ptr);

    // Release and copy back
    env->ReleaseByteArrayElements(tag_out, tag_out_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT void JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1streaming_1aead_1encrypt_1free(
    JNIEnv* env, jclass clazz, jlong handle) {
    (void)clazz;

    // Get input arrays

    // Declare output variables

    // Call native function
    enchant_streaming_aead_encrypt_free((enchant_streaming_aead_t)(long)(handle));

    // Release and copy back

    // Copy output values back to Java
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1streaming_1aead_1decrypt_1init(
    JNIEnv* env, jclass clazz, jlongArray handle_out, jbyteArray key, jbyteArray nonce, jbyteArray aad, jlong aad_len) {
    (void)clazz;

    // Get input arrays
    jlong* handle_out_elems = env->GetLongArrayElements(handle_out, nullptr);
    enchant_streaming_aead_t handle_out_val = nullptr;
    jbyte* key_jbyte = env->GetByteArrayElements(key, nullptr);
    jbyte* nonce_jbyte = env->GetByteArrayElements(nonce, nullptr);
    jbyte* aad_jbyte = env->GetByteArrayElements(aad, nullptr);

    // Declare output variables

    // Call native function
    int rc = enchant_streaming_aead_decrypt_init(&handle_out_val, reinterpret_cast<const uint8_t*>(key_jbyte), reinterpret_cast<const uint8_t*>(nonce_jbyte), reinterpret_cast<const uint8_t*>(aad_jbyte), aad_len);

    // Release and copy back
    env->ReleaseByteArrayElements(key, key_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(nonce, nonce_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(aad, aad_jbyte, JNI_ABORT);

    // Copy output values back to Java
    handle_out_elems[0] = (jlong)(long)handle_out_val;
    env->ReleaseLongArrayElements(handle_out, handle_out_elems, 0);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1streaming_1aead_1decrypt_1update(
    JNIEnv* env, jclass clazz, jlong handle, jbyteArray ciphertext, jlong ciphertext_len, jbyteArray plaintext, jlong plaintext_len) {
    (void)clazz;

    // Get input arrays
    jbyte* ciphertext_jbyte = env->GetByteArrayElements(ciphertext, nullptr);
    jbyte* plaintext_jbyte = env->GetByteArrayElements(plaintext, nullptr);
    uint8_t* plaintext_ptr = reinterpret_cast<uint8_t*>(plaintext_jbyte);

    // Declare output variables
    size_t plaintext_len_val = 0;

    // Call native function
    int rc = enchant_streaming_aead_decrypt_update((enchant_streaming_aead_t)(long)(handle), reinterpret_cast<const uint8_t*>(ciphertext_jbyte), ciphertext_len, plaintext_ptr, &plaintext_len_val);

    // Release and copy back
    env->ReleaseByteArrayElements(ciphertext, ciphertext_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(plaintext, plaintext_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1streaming_1aead_1decrypt_1finalize(
    JNIEnv* env, jclass clazz, jlong handle, jbyteArray tag) {
    (void)clazz;

    // Get input arrays
    jbyte* tag_jbyte = env->GetByteArrayElements(tag, nullptr);

    // Declare output variables

    // Call native function
    int rc = enchant_streaming_aead_decrypt_finalize((enchant_streaming_aead_t)(long)(handle), reinterpret_cast<const uint8_t*>(tag_jbyte));

    // Release and copy back
    env->ReleaseByteArrayElements(tag, tag_jbyte, JNI_ABORT);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT void JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1streaming_1aead_1decrypt_1free(
    JNIEnv* env, jclass clazz, jlong handle) {
    (void)clazz;

    // Get input arrays

    // Declare output variables

    // Call native function
    enchant_streaming_aead_decrypt_free((enchant_streaming_aead_t)(long)(handle));

    // Release and copy back

    // Copy output values back to Java
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1media_1sanitize_1mp4(
    JNIEnv* env, jclass clazz, jbyteArray data, jlong data_len, jbyteArray output, jlong output_len) {
    (void)clazz;

    // Get input arrays
    jbyte* data_jbyte = env->GetByteArrayElements(data, nullptr);
    jbyte* output_jbyte = env->GetByteArrayElements(output, nullptr);
    uint8_t* output_ptr = reinterpret_cast<uint8_t*>(output_jbyte);

    // Declare output variables
    size_t output_len_val = 0;

    // Call native function
    int rc = enchant_media_sanitize_mp4(reinterpret_cast<const uint8_t*>(data_jbyte), data_len, output_ptr, &output_len_val);

    // Release and copy back
    env->ReleaseByteArrayElements(data, data_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(output, output_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1media_1sanitize_1webp(
    JNIEnv* env, jclass clazz, jbyteArray data, jlong data_len, jbyteArray output, jlong output_len) {
    (void)clazz;

    // Get input arrays
    jbyte* data_jbyte = env->GetByteArrayElements(data, nullptr);
    jbyte* output_jbyte = env->GetByteArrayElements(output, nullptr);
    uint8_t* output_ptr = reinterpret_cast<uint8_t*>(output_jbyte);

    // Declare output variables
    size_t output_len_val = 0;

    // Call native function
    int rc = enchant_media_sanitize_webp(reinterpret_cast<const uint8_t*>(data_jbyte), data_len, output_ptr, &output_len_val);

    // Release and copy back
    env->ReleaseByteArrayElements(data, data_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(output, output_jbyte, 0);

    env->ReleaseByteArrayElements(data, data_jbyte, JNI_ABORT);
    env->ReleaseByteArrayElements(output, output_jbyte, 0);

    // Copy output values back to Java
    return rc;
}

/* ═══════════════════════════════════════════════════════════════════
   VEIL SESSION (SEALED SENDER)
   ═══════════════════════════════════════════════════════════════════ */

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1veil_1session_1create(
    JNIEnv* env, jclass clazz, jlongArray session_out) {
    (void)clazz;
    jlong* session_out_ptr = env->GetLongArrayElements(session_out, nullptr);
    enchant_veil_session_t* session = nullptr;
    int rc = enchant_veil_session_create(&session);
    if (rc == ENCHANT_SUCCESS) {
        session_out_ptr[0] = reinterpret_cast<jlong>(session);
    }
    env->ReleaseLongArrayElements(session_out, session_out_ptr, 0);
    return rc;
}

JNIEXPORT void JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1veil_1session_1destroy(
    JNIEnv* env, jclass clazz, jlong session) {
    (void)env; (void)clazz;
    enchant_veil_session_destroy(reinterpret_cast<enchant_veil_session_t*>(session));
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1veil_1session_1init_1alice(
    JNIEnv* env, jclass clazz, jlong session,
    jbyteArray root_key, jbyteArray chain_key,
    jbyteArray our_dh_public, jbyteArray our_dh_private,
    jbyteArray their_x25519_public,
    jbyteArray our_identity, jbyteArray their_identity,
    jbyteArray pqr_key) {
    (void)clazz;
    jbyte* rk = env->GetByteArrayElements(root_key, nullptr);
    jbyte* ck = env->GetByteArrayElements(chain_key, nullptr);
    jbyte* odp = env->GetByteArrayElements(our_dh_public, nullptr);
    jbyte* odv = env->GetByteArrayElements(our_dh_private, nullptr);
    jbyte* txp = env->GetByteArrayElements(their_x25519_public, nullptr);
    jbyte* oi  = env->GetByteArrayElements(our_identity, nullptr);
    jbyte* ti  = env->GetByteArrayElements(their_identity, nullptr);
    jbyte* pq  = env->GetByteArrayElements(pqr_key, nullptr);
    int rc = enchant_veil_session_init_alice(
        reinterpret_cast<enchant_veil_session_t*>(session),
        reinterpret_cast<const uint8_t*>(rk),
        reinterpret_cast<const uint8_t*>(ck),
        reinterpret_cast<const uint8_t*>(odp),
        reinterpret_cast<const uint8_t*>(odv),
        reinterpret_cast<const uint8_t*>(txp),
        reinterpret_cast<const uint8_t*>(oi),
        reinterpret_cast<const uint8_t*>(ti),
        reinterpret_cast<const uint8_t*>(pq));
    env->ReleaseByteArrayElements(root_key, rk, JNI_ABORT);
    env->ReleaseByteArrayElements(chain_key, ck, JNI_ABORT);
    env->ReleaseByteArrayElements(our_dh_public, odp, JNI_ABORT);
    env->ReleaseByteArrayElements(our_dh_private, odv, JNI_ABORT);
    env->ReleaseByteArrayElements(their_x25519_public, txp, JNI_ABORT);
    env->ReleaseByteArrayElements(our_identity, oi, JNI_ABORT);
    env->ReleaseByteArrayElements(their_identity, ti, JNI_ABORT);
    env->ReleaseByteArrayElements(pqr_key, pq, JNI_ABORT);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1veil_1session_1init_1bob(
    JNIEnv* env, jclass clazz, jlong session,
    jbyteArray root_key, jbyteArray chain_key,
    jbyteArray our_dh_public, jbyteArray our_dh_private,
    jbyteArray their_x25519_public,
    jbyteArray our_identity, jbyteArray their_identity,
    jbyteArray pqr_key) {
    (void)clazz;
    jbyte* rk = env->GetByteArrayElements(root_key, nullptr);
    jbyte* ck = env->GetByteArrayElements(chain_key, nullptr);
    jbyte* odp = env->GetByteArrayElements(our_dh_public, nullptr);
    jbyte* odv = env->GetByteArrayElements(our_dh_private, nullptr);
    jbyte* txp = env->GetByteArrayElements(their_x25519_public, nullptr);
    jbyte* oi  = env->GetByteArrayElements(our_identity, nullptr);
    jbyte* ti  = env->GetByteArrayElements(their_identity, nullptr);
    jbyte* pq  = env->GetByteArrayElements(pqr_key, nullptr);
    int rc = enchant_veil_session_init_bob(
        reinterpret_cast<enchant_veil_session_t*>(session),
        reinterpret_cast<const uint8_t*>(rk),
        reinterpret_cast<const uint8_t*>(ck),
        reinterpret_cast<const uint8_t*>(odp),
        reinterpret_cast<const uint8_t*>(odv),
        reinterpret_cast<const uint8_t*>(txp),
        reinterpret_cast<const uint8_t*>(oi),
        reinterpret_cast<const uint8_t*>(ti),
        reinterpret_cast<const uint8_t*>(pq));
    env->ReleaseByteArrayElements(root_key, rk, JNI_ABORT);
    env->ReleaseByteArrayElements(chain_key, ck, JNI_ABORT);
    env->ReleaseByteArrayElements(our_dh_public, odp, JNI_ABORT);
    env->ReleaseByteArrayElements(our_dh_private, odv, JNI_ABORT);
    env->ReleaseByteArrayElements(their_x25519_public, txp, JNI_ABORT);
    env->ReleaseByteArrayElements(our_identity, oi, JNI_ABORT);
    env->ReleaseByteArrayElements(their_identity, ti, JNI_ABORT);
    env->ReleaseByteArrayElements(pqr_key, pq, JNI_ABORT);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1veil_1session_1encrypt(
    JNIEnv* env, jclass clazz, jlong session,
    jbyteArray plaintext, jlong plaintext_len,
    jbyteArray output, jlongArray output_len) {
    (void)clazz;
    jbyte* pt = env->GetByteArrayElements(plaintext, nullptr);
    jbyte* out = env->GetByteArrayElements(output, nullptr);
    jlong* out_len = env->GetLongArrayElements(output_len, nullptr);
    size_t olen = static_cast<size_t>(out_len[0]);
    int rc = enchant_veil_session_encrypt(
        reinterpret_cast<enchant_veil_session_t*>(session),
        reinterpret_cast<const uint8_t*>(pt), static_cast<size_t>(plaintext_len),
        reinterpret_cast<uint8_t*>(out), &olen);
    env->ReleaseByteArrayElements(plaintext, pt, JNI_ABORT);
    out_len[0] = static_cast<jlong>(olen);
    env->ReleaseLongArrayElements(output_len, out_len, 0);
    env->ReleaseByteArrayElements(output, out, 0);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1veil_1session_1decrypt(
    JNIEnv* env, jclass clazz, jlong session,
    jbyteArray input, jlong input_len,
    jbyteArray plaintext, jlongArray plaintext_len) {
    (void)clazz;
    jbyte* in = env->GetByteArrayElements(input, nullptr);
    jbyte* pt = env->GetByteArrayElements(plaintext, nullptr);
    jlong* pt_len = env->GetLongArrayElements(plaintext_len, nullptr);
    size_t plen = static_cast<size_t>(pt_len[0]);
    int rc = enchant_veil_session_decrypt(
        reinterpret_cast<enchant_veil_session_t*>(session),
        reinterpret_cast<const uint8_t*>(in), static_cast<size_t>(input_len),
        reinterpret_cast<uint8_t*>(pt), &plen);
    env->ReleaseByteArrayElements(input, in, JNI_ABORT);
    pt_len[0] = static_cast<jlong>(plen);
    env->ReleaseLongArrayElements(plaintext_len, pt_len, 0);
    env->ReleaseByteArrayElements(plaintext, pt, 0);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1veil_1session_1seal_1and_1encrypt(
    JNIEnv* env, jclass clazz, jlong session,
    jbyteArray sender_identity_private,
    jbyteArray sender_identity_public,
    jbyteArray recipient_public_keys, jlong num_recipients,
    jbyteArray sender_cert_data, jlong sender_cert_len,
    jbyteArray plaintext, jlong plaintext_len,
    jbyteArray output, jlongArray output_len) {
    (void)clazz;
    jbyte* sip = env->GetByteArrayElements(sender_identity_private, nullptr);
    jbyte* spub = env->GetByteArrayElements(sender_identity_public, nullptr);
    jbyte* rpk = env->GetByteArrayElements(recipient_public_keys, nullptr);
    jbyte* scd = env->GetByteArrayElements(sender_cert_data, nullptr);
    jbyte* pt = env->GetByteArrayElements(plaintext, nullptr);
    jbyte* out = env->GetByteArrayElements(output, nullptr);
    jlong* out_len = env->GetLongArrayElements(output_len, nullptr);
    size_t olen = static_cast<size_t>(out_len[0]);
    int rc = enchant_veil_session_seal_and_encrypt(
        reinterpret_cast<enchant_veil_session_t*>(session),
        reinterpret_cast<const uint8_t*>(sip),
        reinterpret_cast<const uint8_t*>(spub),
        reinterpret_cast<const uint8_t*>(rpk),
        static_cast<size_t>(num_recipients),
        reinterpret_cast<const uint8_t*>(scd),
        static_cast<size_t>(sender_cert_len),
        reinterpret_cast<const uint8_t*>(pt),
        static_cast<size_t>(plaintext_len),
        reinterpret_cast<uint8_t*>(out), &olen);
    env->ReleaseByteArrayElements(sender_identity_private, sip, JNI_ABORT);
    env->ReleaseByteArrayElements(sender_identity_public, spub, JNI_ABORT);
    env->ReleaseByteArrayElements(recipient_public_keys, rpk, JNI_ABORT);
    env->ReleaseByteArrayElements(sender_cert_data, scd, JNI_ABORT);
    env->ReleaseByteArrayElements(plaintext, pt, JNI_ABORT);
    out_len[0] = static_cast<jlong>(olen);
    env->ReleaseLongArrayElements(output_len, out_len, 0);
    env->ReleaseByteArrayElements(output, out, 0);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1veil_1session_1unseal_1and_1decrypt(
    JNIEnv* env, jclass clazz, jlong session,
    jbyteArray recipient_private_key,
    jbyteArray recipient_public_key,
    jbyteArray ciphertext, jlong ciphertext_len,
    jbyteArray plaintext, jlongArray plaintext_len,
    jbyteArray sender_identity_key_out) {
    (void)clazz;
    jbyte* rpk = env->GetByteArrayElements(recipient_private_key, nullptr);
    jbyte* rpub = env->GetByteArrayElements(recipient_public_key, nullptr);
    jbyte* ct = env->GetByteArrayElements(ciphertext, nullptr);
    jbyte* pt = env->GetByteArrayElements(plaintext, nullptr);
    jlong* pt_len = env->GetLongArrayElements(plaintext_len, nullptr);
    jbyte* sik = env->GetByteArrayElements(sender_identity_key_out, nullptr);
    size_t plen = static_cast<size_t>(pt_len[0]);
    int rc = enchant_veil_session_unseal_and_decrypt(
        reinterpret_cast<enchant_veil_session_t*>(session),
        reinterpret_cast<const uint8_t*>(rpk),
        reinterpret_cast<const uint8_t*>(rpub),
        reinterpret_cast<const uint8_t*>(ct),
        static_cast<size_t>(ciphertext_len),
        reinterpret_cast<uint8_t*>(pt), &plen,
        reinterpret_cast<uint8_t*>(sik));
    env->ReleaseByteArrayElements(recipient_private_key, rpk, JNI_ABORT);
    env->ReleaseByteArrayElements(recipient_public_key, rpub, JNI_ABORT);
    env->ReleaseByteArrayElements(ciphertext, ct, JNI_ABORT);
    pt_len[0] = static_cast<jlong>(plen);
    env->ReleaseLongArrayElements(plaintext_len, pt_len, 0);
    env->ReleaseByteArrayElements(plaintext, pt, 0);
    env->ReleaseByteArrayElements(sender_identity_key_out, sik, 0);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1veil_1session_1seal_1group_1message(
    JNIEnv* env, jclass clazz, jlong session,
    jbyteArray sender_identity_private,
    jbyteArray sender_identity_public,
    jbyteArray recipient_public_keys, jlong num_recipients,
    jbyteArray sender_cert_data, jlong sender_cert_len,
    jbyteArray sender_key_ciphertext, jlong sender_key_len,
    jint sender_key_id,
    jbyteArray output, jlongArray output_len) {
    (void)clazz;
    jbyte* sip = env->GetByteArrayElements(sender_identity_private, nullptr);
    jbyte* spub = env->GetByteArrayElements(sender_identity_public, nullptr);
    jbyte* rpk = env->GetByteArrayElements(recipient_public_keys, nullptr);
    jbyte* scd = env->GetByteArrayElements(sender_cert_data, nullptr);
    jbyte* skc = env->GetByteArrayElements(sender_key_ciphertext, nullptr);
    jbyte* out = env->GetByteArrayElements(output, nullptr);
    jlong* out_len = env->GetLongArrayElements(output_len, nullptr);
    size_t olen = static_cast<size_t>(out_len[0]);
    int rc = enchant_veil_session_seal_group_message(
        reinterpret_cast<enchant_veil_session_t*>(session),
        reinterpret_cast<const uint8_t*>(sip),
        reinterpret_cast<const uint8_t*>(spub),
        reinterpret_cast<const uint8_t*>(rpk),
        static_cast<size_t>(num_recipients),
        reinterpret_cast<const uint8_t*>(scd),
        static_cast<size_t>(sender_cert_len),
        reinterpret_cast<const uint8_t*>(skc),
        static_cast<size_t>(sender_key_len),
        static_cast<uint32_t>(sender_key_id),
        reinterpret_cast<uint8_t*>(out), &olen);
    env->ReleaseByteArrayElements(sender_identity_private, sip, JNI_ABORT);
    env->ReleaseByteArrayElements(sender_identity_public, spub, JNI_ABORT);
    env->ReleaseByteArrayElements(recipient_public_keys, rpk, JNI_ABORT);
    env->ReleaseByteArrayElements(sender_cert_data, scd, JNI_ABORT);
    env->ReleaseByteArrayElements(sender_key_ciphertext, skc, JNI_ABORT);
    out_len[0] = static_cast<jlong>(olen);
    env->ReleaseLongArrayElements(output_len, out_len, 0);
    env->ReleaseByteArrayElements(output, out, 0);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_enchant_core_crypto_EnchantCrypto_enchant_1veil_1session_1unseal_1group_1message(
    JNIEnv* env, jclass clazz, jlong session,
    jbyteArray recipient_private_key,
    jbyteArray recipient_public_key,
    jbyteArray ciphertext, jlong ciphertext_len,
    jbyteArray sender_key_out, jlongArray sender_key_len,
    jintArray sender_key_id_out) {
    (void)clazz;
    jbyte* rpk = env->GetByteArrayElements(recipient_private_key, nullptr);
    jbyte* rpub = env->GetByteArrayElements(recipient_public_key, nullptr);
    jbyte* ct = env->GetByteArrayElements(ciphertext, nullptr);
    jbyte* sk = env->GetByteArrayElements(sender_key_out, nullptr);
    jlong* sk_len = env->GetLongArrayElements(sender_key_len, nullptr);
    jint* sk_id = env->GetIntArrayElements(sender_key_id_out, nullptr);
    size_t skl = static_cast<size_t>(sk_len[0]);
    uint32_t kid = 0;
    int rc = enchant_veil_session_unseal_group_message(
        reinterpret_cast<enchant_veil_session_t*>(session),
        reinterpret_cast<const uint8_t*>(rpk),
        reinterpret_cast<const uint8_t*>(rpub),
        reinterpret_cast<const uint8_t*>(ct),
        static_cast<size_t>(ciphertext_len),
        reinterpret_cast<uint8_t*>(sk), &skl, &kid);
    env->ReleaseByteArrayElements(recipient_private_key, rpk, JNI_ABORT);
    env->ReleaseByteArrayElements(recipient_public_key, rpub, JNI_ABORT);
    env->ReleaseByteArrayElements(ciphertext, ct, JNI_ABORT);
    sk_len[0] = static_cast<jlong>(skl);
    env->ReleaseLongArrayElements(sender_key_len, sk_len, 0);
    sk_id[0] = static_cast<jint>(kid);
    env->ReleaseIntArrayElements(sender_key_id_out, sk_id, 0);
    env->ReleaseByteArrayElements(sender_key_out, sk, 0);
    return rc;
}

} /* extern "C" */
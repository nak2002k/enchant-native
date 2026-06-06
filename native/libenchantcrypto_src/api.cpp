#include "enchant/api.h"
#include <sodium.h>
#include "primitives/x25519.hpp"
#include "primitives/ed25519.hpp"
#include "primitives/xchacha20.hpp"
#include "primitives/hkdf.hpp"
#include "primitives/hash.hpp"
#include "primitives/hmac.hpp"
#include "primitives/random.hpp"
#include "primitives/base64.hpp"
#include "primitives/aes.hpp"
#include "primitives/hpke.hpp"

extern "C" {

int enchant_init(void) {
    if (sodium_init() < 0) {
        return ENCHANT_ERROR_INTERNAL;
    }
    return ENCHANT_SUCCESS;
}

void enchant_random_bytes(uint8_t* buf, size_t len) {
    if (buf && len > 0) {
        randombytes_buf(buf, len);
    }
}

int enchant_x25519_keypair(uint8_t* public_key, uint8_t* private_key) {
    if (!public_key || !private_key) return ENCHANT_ERROR_NULL_POINTER;
    return enchant::primitives::x25519_keypair(public_key, private_key);
}

int enchant_x25519_dh(const uint8_t* private_key, const uint8_t* public_key,
                      uint8_t* shared_secret) {
    if (!private_key || !public_key || !shared_secret) return ENCHANT_ERROR_NULL_POINTER;
    return enchant::primitives::x25519_dh(private_key, public_key, shared_secret);
}

int enchant_ed25519_keypair(uint8_t* public_key, uint8_t* private_seed) {
    if (!public_key || !private_seed) return ENCHANT_ERROR_NULL_POINTER;
    return enchant::primitives::ed25519_keypair(public_key, private_seed);
}

int enchant_ed25519_sign(const uint8_t* message, size_t message_len,
                         const uint8_t* private_seed, uint8_t* signature) {
    if (!message || !private_seed || !signature) return ENCHANT_ERROR_NULL_POINTER;
    return enchant::primitives::ed25519_sign(message, message_len, private_seed, signature);
}

int enchant_ed25519_verify(const uint8_t* message, size_t message_len,
                            const uint8_t* signature, const uint8_t* public_key) {
    if (!message || !signature || !public_key) return ENCHANT_ERROR_NULL_POINTER;
    return enchant::primitives::ed25519_verify(message, message_len, signature, public_key);
}

int enchant_xchacha20_encrypt(const uint8_t* plaintext, size_t plaintext_len,
                              const uint8_t* key, const uint8_t* nonce,
                              uint8_t* ciphertext, size_t ciphertext_capacity) {
    if (!key || !nonce || !ciphertext) return ENCHANT_ERROR_NULL_POINTER;
    if (!plaintext && plaintext_len > 0) return ENCHANT_ERROR_NULL_POINTER;
    return enchant::primitives::xchacha20_encrypt(plaintext, plaintext_len, key, nonce, ciphertext, ciphertext_capacity);
}

int enchant_xchacha20_decrypt(const uint8_t* ciphertext, size_t ciphertext_len,
                              const uint8_t* key, const uint8_t* nonce,
                              uint8_t* plaintext, size_t plaintext_capacity) {
    if (!ciphertext || !key || !nonce || !plaintext) return ENCHANT_ERROR_NULL_POINTER;
    return enchant::primitives::xchacha20_decrypt(ciphertext, ciphertext_len, key, nonce, plaintext, plaintext_capacity);
}

int enchant_hkdf_sha256(const uint8_t* ikm, size_t ikm_len,
                        const uint8_t* salt, size_t salt_len,
                        const uint8_t* info, size_t info_len,
                        uint8_t* okm, size_t okm_len) {
    if (!ikm || !salt || !info || !okm) return ENCHANT_ERROR_NULL_POINTER;
    return enchant::primitives::hkdf_derive(ikm, ikm_len, salt, salt_len, info, info_len, okm, okm_len);
}

int enchant_sha256(const uint8_t* data, size_t len, uint8_t* hash) {
    if (!data || !hash) return ENCHANT_ERROR_NULL_POINTER;
    return enchant::primitives::sha256(data, len, hash);
}

int enchant_hmac_sha256(const uint8_t* key, size_t key_len,
                        const uint8_t* data, size_t data_len,
                        uint8_t* mac) {
    if (!key || !data || !mac) return ENCHANT_ERROR_NULL_POINTER;
    return enchant::primitives::hmac_sha256(key, key_len, data, data_len, mac);
}

int enchant_base64_encode(const uint8_t* data, size_t len,
                          char* output, size_t output_len) {
    if (!data || !output) return ENCHANT_ERROR_NULL_POINTER;
    return enchant::primitives::base64_encode(data, len, output, output_len);
}

int enchant_base64_decode(const char* input, uint8_t* output, size_t output_len) {
    if (!input || !output) return ENCHANT_ERROR_NULL_POINTER;
    return enchant::primitives::base64_decode(input, output, output_len);
}

int enchant_argon2id_hash(const char* plaintext, size_t plaintext_len,
                          char* output, size_t output_len) {
    if (!plaintext || !output) return ENCHANT_ERROR_NULL_POINTER;
    if (output_len < crypto_pwhash_STRBYTES) return ENCHANT_ERROR_BUFFER_TOO_SMALL;
    if (crypto_pwhash_str(output, plaintext, plaintext_len,
                          crypto_pwhash_OPSLIMIT_SENSITIVE,
                          crypto_pwhash_MEMLIMIT_SENSITIVE) != 0) {
        return ENCHANT_ERROR_INTERNAL;
    }
    return ENCHANT_SUCCESS;
}

int enchant_argon2id_verify(const char* hash, size_t /*hash_len*/,
                            const char* plaintext, size_t plaintext_len) {
    if (!hash || !plaintext) return ENCHANT_ERROR_NULL_POINTER;
    if (crypto_pwhash_str_verify(hash, plaintext, plaintext_len) == 0) {
        return ENCHANT_SUCCESS;
    }
    return ENCHANT_ERROR_INVALID_FORMAT;
}

void enchant_secure_zero(void* ptr, size_t len) {
    if (ptr && len > 0) {
        sodium_memzero(ptr, len);
    }
}

int enchant_secure_alloc(void** ptr, size_t len) {
    if (!ptr || len == 0) return ENCHANT_ERROR_NULL_POINTER;
    *ptr = sodium_malloc(len);
    if (!*ptr) return ENCHANT_ERROR_INTERNAL;
    sodium_memzero(*ptr, len);
    return ENCHANT_SUCCESS;
}

void enchant_secure_free(void* ptr, size_t len) {
    if (ptr) {
        if (len > 0) sodium_memzero(ptr, len);
        sodium_free(ptr);
    }
}

int enchant_aes_init(void) {
    return enchant::primitives::aes_init();
}

int enchant_aes_is_available(void) {
    return enchant::primitives::aes_is_available();
}

int enchant_aes_128_keygen(uint8_t* key) {
    if (!key) return ENCHANT_ERROR_NULL_POINTER;
    return enchant::primitives::aes_128_keygen(key);
}

int enchant_aes_192_keygen(uint8_t* key) {
    if (!key) return ENCHANT_ERROR_NULL_POINTER;
    return enchant::primitives::aes_192_keygen(key);
}

int enchant_aes_256_keygen(uint8_t* key) {
    if (!key) return ENCHANT_ERROR_NULL_POINTER;
    return enchant::primitives::aes_256_keygen(key);
}

int enchant_aes_256_gcm_encrypt(const uint8_t* key,
                                 const uint8_t* nonce,
                                 const uint8_t* plaintext, size_t plaintext_len,
                                 const uint8_t* aad, size_t aad_len,
                                 uint8_t* ciphertext, size_t* ciphertext_len) {
    if (!key || !nonce || !plaintext || !ciphertext || !ciphertext_len) return ENCHANT_ERROR_NULL_POINTER;
    return enchant::primitives::aes_256_gcm_encrypt(key, nonce, plaintext, plaintext_len, aad, aad_len, ciphertext, ciphertext_len);
}

int enchant_aes_256_gcm_decrypt(const uint8_t* key,
                                 const uint8_t* nonce,
                                 const uint8_t* ciphertext, size_t ciphertext_len,
                                 const uint8_t* aad, size_t aad_len,
                                 uint8_t* plaintext, size_t* plaintext_len) {
    if (!key || !nonce || !ciphertext || !plaintext || !plaintext_len) return ENCHANT_ERROR_NULL_POINTER;
    return enchant::primitives::aes_256_gcm_decrypt(key, nonce, ciphertext, ciphertext_len, aad, aad_len, plaintext, plaintext_len);
}

int enchant_aes_256_ctr_encrypt(const uint8_t* key,
                                 const uint8_t* nonce,
                                 const uint8_t* plaintext, size_t plaintext_len,
                                 uint8_t* ciphertext) {
    if (!key || !nonce || !plaintext || !ciphertext) return ENCHANT_ERROR_NULL_POINTER;
    return enchant::primitives::aes_256_ctr_encrypt(key, nonce, plaintext, plaintext_len, ciphertext);
}

int enchant_aes_256_ctr_decrypt(const uint8_t* key,
                                 const uint8_t* nonce,
                                 const uint8_t* ciphertext, size_t ciphertext_len,
                                 uint8_t* plaintext) {
    if (!key || !nonce || !ciphertext || !plaintext) return ENCHANT_ERROR_NULL_POINTER;
    return enchant::primitives::aes_256_ctr_decrypt(key, nonce, ciphertext, ciphertext_len, plaintext);
}

int enchant_aes_256_cbc_encrypt(const uint8_t* key,
                                 const uint8_t* iv,
                                 const uint8_t* plaintext, size_t plaintext_len,
                                 uint8_t* ciphertext, size_t* ciphertext_len) {
    if (!key || !iv || !plaintext || !ciphertext || !ciphertext_len) return ENCHANT_ERROR_NULL_POINTER;
    return enchant::primitives::aes_256_cbc_encrypt(key, iv, plaintext, plaintext_len, ciphertext, ciphertext_len);
}

int enchant_aes_256_cbc_decrypt(const uint8_t* key,
                                 const uint8_t* iv,
                                 const uint8_t* ciphertext, size_t ciphertext_len,
                                 uint8_t* plaintext, size_t* plaintext_len) {
    if (!key || !iv || !ciphertext || !plaintext || !plaintext_len) return ENCHANT_ERROR_NULL_POINTER;
    return enchant::primitives::aes_256_cbc_decrypt(key, iv, ciphertext, ciphertext_len, plaintext, plaintext_len);
}

int enchant_hpke_seal(const uint8_t* recipient_public_key,
                      const uint8_t* info, size_t info_len,
                      const uint8_t* aad, size_t aad_len,
                      const uint8_t* plaintext, size_t plaintext_len,
                      uint8_t* ciphertext, size_t* ciphertext_len) {
    if (!recipient_public_key || !plaintext || !ciphertext || !ciphertext_len) return ENCHANT_ERROR_NULL_POINTER;
    return enchant::primitives::hpke_seal(recipient_public_key, info, info_len, aad, aad_len, plaintext, plaintext_len, ciphertext, ciphertext_len);
}

int enchant_hpke_open(const uint8_t* recipient_private_key,
                      const uint8_t* info, size_t info_len,
                      const uint8_t* aad, size_t aad_len,
                      const uint8_t* ciphertext, size_t ciphertext_len,
                      uint8_t* plaintext, size_t* plaintext_len) {
    if (!recipient_private_key || !ciphertext || !plaintext || !plaintext_len) return ENCHANT_ERROR_NULL_POINTER;
    return enchant::primitives::hpke_open(recipient_private_key, info, info_len, aad, aad_len, ciphertext, ciphertext_len, plaintext, plaintext_len);
}

} /* extern "C" */

#ifndef ENCHANT_API_H
#define ENCHANT_API_H

#include <stdint.h>
#include <stddef.h>
#include "enchant/error.h"

#ifdef __cplusplus
extern "C" {
#endif

#if defined(_WIN32) || defined(__CYGWIN__)
    #define ENCHANT_API __declspec(dllexport)
#elif defined(__GNUC__) && __GNUC__ >= 4
    #define ENCHANT_API __attribute__((visibility("default")))
#else
    #define ENCHANT_API
#endif

#define ENCHANT_X25519_PUBLIC_KEY_SIZE  32
#define ENCHANT_X25519_PRIVATE_KEY_SIZE 32
#define ENCHANT_ED25519_PUBLIC_KEY_SIZE 32
#define ENCHANT_ED25519_SEED_SIZE       32
#define ENCHANT_ED25519_SIGNATURE_SIZE  64
#define ENCHANT_XCHACHA20_KEY_SIZE      32
#define ENCHANT_XCHACHA20_NONCE_SIZE    24
#define ENCHANT_XCHACHA20_TAG_SIZE      16
#define ENCHANT_SHA256_SIZE             32
#define ENCHANT_HMAC_SHA256_SIZE        32
#define ENCHANT_HKDF_MAX_OUTPUT         8160
#define ENCHANT_ARGON2_STRBYTES         128
#define ENCHANT_AES_128_KEY_SIZE        16
#define ENCHANT_AES_192_KEY_SIZE        24
#define ENCHANT_AES_256_KEY_SIZE        32
#define ENCHANT_AES_BLOCK_SIZE          16
#define ENCHANT_AES_GCM_NONCE_SIZE      12
#define ENCHANT_AES_GCM_TAG_SIZE        16
#define ENCHANT_HPKE_SHARED_SECRET_SIZE 32
#define ENCHANT_HPKE_KEY_SIZE           32

ENCHANT_API int enchant_init(void);

ENCHANT_API void enchant_random_bytes(uint8_t* buf, size_t len);

ENCHANT_API int enchant_x25519_keypair(uint8_t* public_key, uint8_t* private_key);

ENCHANT_API int enchant_x25519_dh(const uint8_t* private_key,
                                    const uint8_t* public_key,
                                    uint8_t* shared_secret);

ENCHANT_API int enchant_ed25519_keypair(uint8_t* public_key, uint8_t* private_seed);

ENCHANT_API int enchant_ed25519_sign(const uint8_t* message, size_t message_len,
                                       const uint8_t* private_seed,
                                       uint8_t* signature);

ENCHANT_API int enchant_ed25519_verify(const uint8_t* message, size_t message_len,
                                          const uint8_t* signature,
                                          const uint8_t* public_key);

ENCHANT_API int enchant_xchacha20_encrypt(const uint8_t* plaintext, size_t plaintext_len,
                                            const uint8_t* key,
                                            const uint8_t* nonce,
                                            uint8_t* ciphertext, size_t ciphertext_capacity);

ENCHANT_API int enchant_xchacha20_decrypt(const uint8_t* ciphertext, size_t ciphertext_len,
                                            const uint8_t* key,
                                            const uint8_t* nonce,
                                            uint8_t* plaintext, size_t plaintext_capacity);

ENCHANT_API int enchant_hkdf_sha256(const uint8_t* ikm, size_t ikm_len,
                                      const uint8_t* salt, size_t salt_len,
                                      const uint8_t* info, size_t info_len,
                                      uint8_t* okm, size_t okm_len);

ENCHANT_API int enchant_sha256(const uint8_t* data, size_t len, uint8_t* hash);

ENCHANT_API int enchant_hmac_sha256(const uint8_t* key, size_t key_len,
                                      const uint8_t* data, size_t data_len,
                                      uint8_t* mac);

ENCHANT_API int enchant_base64_encode(const uint8_t* data, size_t len,
                                        char* output, size_t output_len);

ENCHANT_API int enchant_base64_decode(const char* input, uint8_t* output, size_t output_len);

ENCHANT_API int enchant_argon2id_hash(const char* plaintext, size_t plaintext_len,
                                      char* output, size_t output_len);

ENCHANT_API int enchant_argon2id_verify(const char* hash, size_t hash_len,
                                        const char* plaintext, size_t plaintext_len);

ENCHANT_API int enchant_aes_256_keygen(uint8_t* key);

ENCHANT_API int enchant_aes_192_keygen(uint8_t* key);

ENCHANT_API int enchant_aes_128_keygen(uint8_t* key);

ENCHANT_API int enchant_aes_is_available(void);

ENCHANT_API int enchant_aes_init(void);

ENCHANT_API int enchant_aes_256_gcm_encrypt(const uint8_t* key,
                                              const uint8_t* nonce,
                                              const uint8_t* plaintext, size_t plaintext_len,
                                              const uint8_t* aad, size_t aad_len,
                                              uint8_t* ciphertext, size_t* ciphertext_len);

ENCHANT_API int enchant_aes_256_gcm_decrypt(const uint8_t* key,
                                              const uint8_t* nonce,
                                              const uint8_t* ciphertext, size_t ciphertext_len,
                                              const uint8_t* aad, size_t aad_len,
                                              uint8_t* plaintext, size_t* plaintext_len);

ENCHANT_API int enchant_aes_256_ctr_encrypt(const uint8_t* key,
                                              const uint8_t* nonce,
                                              const uint8_t* plaintext, size_t plaintext_len,
                                              uint8_t* ciphertext);

ENCHANT_API int enchant_aes_256_ctr_decrypt(const uint8_t* key,
                                              const uint8_t* nonce,
                                              const uint8_t* ciphertext, size_t ciphertext_len,
                                              uint8_t* plaintext);

ENCHANT_API int enchant_aes_256_cbc_encrypt(const uint8_t* key,
                                              const uint8_t* iv,
                                              const uint8_t* plaintext, size_t plaintext_len,
                                              uint8_t* ciphertext, size_t* ciphertext_len);

ENCHANT_API int enchant_aes_256_cbc_decrypt(const uint8_t* key,
                                              const uint8_t* iv,
                                              const uint8_t* ciphertext, size_t ciphertext_len,
                                              uint8_t* plaintext, size_t* plaintext_len);

ENCHANT_API int enchant_hpke_seal(const uint8_t* recipient_public_key,
                                   const uint8_t* info, size_t info_len,
                                   const uint8_t* aad, size_t aad_len,
                                   const uint8_t* plaintext, size_t plaintext_len,
                                   uint8_t* ciphertext, size_t* ciphertext_len);

ENCHANT_API int enchant_hpke_open(const uint8_t* recipient_private_key,
                                   const uint8_t* info, size_t info_len,
                                   const uint8_t* aad, size_t aad_len,
                                   const uint8_t* ciphertext, size_t ciphertext_len,
                                   uint8_t* plaintext, size_t* plaintext_len);

ENCHANT_API void enchant_secure_zero(void* ptr, size_t len);

ENCHANT_API int enchant_secure_alloc(void** ptr, size_t len);

ENCHANT_API void enchant_secure_free(void* ptr, size_t len);

#ifdef __cplusplus
}
#endif

#endif /* ENCHANT_API_H */
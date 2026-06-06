#ifndef ENCHANT_PRIMITIVES_AES_HPP
#define ENCHANT_PRIMITIVES_AES_HPP

#include <cstdint>
#include <cstddef>
#include "enchant/error.h"

namespace enchant {
namespace primitives {

constexpr size_t AES_128_KEY_SIZE = 16;
constexpr size_t AES_192_KEY_SIZE = 24;
constexpr size_t AES_256_KEY_SIZE = 32;

constexpr size_t AES_BLOCK_SIZE = 16;
constexpr size_t AES_IV_SIZE = 16;
constexpr size_t AES_GCM_NONCE_SIZE = 12;
constexpr size_t AES_GCM_TAG_SIZE = 16;
constexpr size_t AES_SIV_SIZE = 16;

constexpr size_t AES_GCM_CIPHERTEXT_OVERHEAD = AES_GCM_TAG_SIZE;

int aes_init(void);
int aes_is_available(void);

int aes_128_keygen(uint8_t* key);
int aes_192_keygen(uint8_t* key);
int aes_256_keygen(uint8_t* key);

int aes_256_gcm_encrypt(const uint8_t* key,
                        const uint8_t* nonce,
                        const uint8_t* plaintext,
                        size_t plaintext_len,
                        const uint8_t* additional_data,
                        size_t additional_data_len,
                        uint8_t* ciphertext,
                        size_t* ciphertext_len);

int aes_256_gcm_decrypt(const uint8_t* key,
                        const uint8_t* nonce,
                        const uint8_t* ciphertext,
                        size_t ciphertext_len,
                        const uint8_t* additional_data,
                        size_t additional_data_len,
                        uint8_t* plaintext,
                        size_t* plaintext_len);

int aes_256_gcm_encrypt_detached(const uint8_t* key,
                                 const uint8_t* nonce,
                                 const uint8_t* plaintext,
                                 size_t plaintext_len,
                                 const uint8_t* additional_data,
                                 size_t additional_data_len,
                                 uint8_t* ciphertext,
                                 size_t ciphertext_capacity,
                                 uint8_t* tag);

int aes_256_gcm_decrypt_detached(const uint8_t* key,
                                  const uint8_t* nonce,
                                  const uint8_t* ciphertext,
                                  size_t ciphertext_len,
                                  const uint8_t* tag,
                                  const uint8_t* additional_data,
                                  size_t additional_data_len,
                                  uint8_t* plaintext,
                                  size_t plaintext_capacity);

int aes_256_ctr_encrypt(const uint8_t* key,
                        const uint8_t* nonce,
                        const uint8_t* plaintext,
                        size_t plaintext_len,
                        uint8_t* ciphertext);

int aes_256_ctr_decrypt(const uint8_t* key,
                        const uint8_t* nonce,
                        const uint8_t* ciphertext,
                        size_t ciphertext_len,
                        uint8_t* plaintext);

int aes_256_cbc_encrypt(const uint8_t* key,
                        const uint8_t* iv,
                        const uint8_t* plaintext,
                        size_t plaintext_len,
                        uint8_t* ciphertext,
                        size_t* ciphertext_len);

int aes_256_cbc_decrypt(const uint8_t* key,
                        const uint8_t* iv,
                        const uint8_t* ciphertext,
                        size_t ciphertext_len,
                        uint8_t* plaintext,
                        size_t* plaintext_len);

int aes_256_gcm_siv_encrypt(const uint8_t* key,
                            const uint8_t* nonce,
                            const uint8_t* plaintext,
                            size_t plaintext_len,
                            const uint8_t* additional_data,
                            size_t additional_data_len,
                            uint8_t* ciphertext,
                            size_t* ciphertext_len);

int aes_256_gcm_siv_decrypt(const uint8_t* key,
                            const uint8_t* nonce,
                            const uint8_t* ciphertext,
                            size_t ciphertext_len,
                            const uint8_t* additional_data,
                            size_t additional_data_len,
                            uint8_t* plaintext,
                            size_t* plaintext_len);

} // namespace primitives
} // namespace enchant

#endif

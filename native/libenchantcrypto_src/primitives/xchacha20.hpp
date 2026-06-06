#ifndef ENCHANT_PRIMITIVES_XCHACHA20_HPP
#define ENCHANT_PRIMITIVES_XCHACHA20_HPP

#include <cstdint>
#include <cstddef>
#include "enchant/error.h"

namespace enchant {
namespace primitives {

constexpr size_t XCHACHA20_KEY_SIZE = 32;
constexpr size_t XCHACHA20_NONCE_SIZE = 24;
constexpr size_t XCHACHA20_TAG_SIZE = 16;

int xchacha20_encrypt(const uint8_t* plaintext, size_t plaintext_len,
                      const uint8_t* key, const uint8_t* nonce,
                      uint8_t* ciphertext, size_t ciphertext_capacity);

int xchacha20_decrypt(const uint8_t* ciphertext, size_t ciphertext_len,
                      const uint8_t* key, const uint8_t* nonce,
                      uint8_t* plaintext, size_t plaintext_capacity);

int xchacha20_encrypt_ad(const uint8_t* plaintext, size_t plaintext_len,
                         const uint8_t* ad, size_t ad_len,
                         const uint8_t* key, const uint8_t* nonce,
                         uint8_t* ciphertext, size_t* ciphertext_len);

int xchacha20_decrypt_ad(const uint8_t* ciphertext, size_t ciphertext_len,
                         const uint8_t* ad, size_t ad_len,
                         const uint8_t* key, const uint8_t* nonce,
                         uint8_t* plaintext, size_t* plaintext_len);

} // namespace primitives
} // namespace enchant

#endif
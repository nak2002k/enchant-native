#ifndef ENCHANT_PRIMITIVES_HPKE_HPP
#define ENCHANT_PRIMITIVES_HPKE_HPP

#include <cstdint>
#include <cstddef>
#include "enchant/error.h"

namespace enchant {
namespace primitives {

constexpr size_t HPKE_ENC_LENGTH = 32;
constexpr size_t HPKE_KEY_LENGTH = 32;
constexpr size_t HPKE_TAG_LENGTH = 16;

int hpke_seal(const uint8_t* recipient_public_key,
              const uint8_t* info, size_t info_len,
              const uint8_t* aad, size_t aad_len,
              const uint8_t* plaintext, size_t plaintext_len,
              uint8_t* ciphertext, size_t* ciphertext_len);

int hpke_open(const uint8_t* recipient_private_key,
              const uint8_t* info, size_t info_len,
              const uint8_t* aad, size_t aad_len,
              const uint8_t* ciphertext, size_t ciphertext_len,
              uint8_t* plaintext, size_t* plaintext_len);

} // namespace primitives
} // namespace enchant

#endif

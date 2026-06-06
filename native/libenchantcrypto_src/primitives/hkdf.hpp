#ifndef ENCHANT_PRIMITIVES_HKDF_HPP
#define ENCHANT_PRIMITIVES_HKDF_HPP

#include <cstdint>
#include <cstddef>
#include "enchant/error.h"

namespace enchant {
namespace primitives {

constexpr size_t HKDF_SALT_SIZE = 32;
constexpr size_t HKDF_MAX_OUTPUT = 8160;
constexpr size_t HKDF_HASH_LEN = 32;

int hkdf_derive(const uint8_t* ikm, size_t ikm_len,
                const uint8_t* salt, size_t salt_len,
                const uint8_t* info, size_t info_len,
                uint8_t* okm, size_t okm_len);

int hkdf_extract(const uint8_t* salt, size_t salt_len,
                 const uint8_t* ikm, size_t ikm_len,
                 uint8_t* prk);

int hkdf_expand(const uint8_t* prk, size_t prk_len,
                const uint8_t* info, size_t info_len,
                uint8_t* okm, size_t okm_len);

int hkdf_expand_label(const uint8_t* prk, size_t prk_len,
                      const uint8_t* info, size_t info_len,
                      uint8_t* okm, size_t okm_len);

} // namespace primitives
} // namespace enchant

#endif

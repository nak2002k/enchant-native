#ifndef ENCHANT_PRIMITIVES_HMAC_HPP
#define ENCHANT_PRIMITIVES_HMAC_HPP

#include <cstdint>
#include <cstddef>
#include "enchant/error.h"

namespace enchant {
namespace primitives {

constexpr size_t HMAC_SHA256_SIZE = 32;
constexpr size_t HMAC_SHA256_KEY_SIZE = 32;
constexpr size_t HMAC_SHA512_SIZE = 64;
constexpr size_t HMAC_SHA512_KEY_SIZE = 64;

int hmac_sha256(const uint8_t* key, size_t key_len,
                const uint8_t* data, size_t data_len,
                uint8_t* mac);

int hmac_sha512(const uint8_t* key, size_t key_len,
                const uint8_t* data, size_t data_len,
                uint8_t* mac);

} // namespace primitives
} // namespace enchant

#endif
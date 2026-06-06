#ifndef ENCHANT_PRIMITIVES_HASH_HPP
#define ENCHANT_PRIMITIVES_HASH_HPP

#include <cstdint>
#include <cstddef>
#include "enchant/error.h"

namespace enchant {
namespace primitives {

constexpr size_t SHA256_SIZE = 32;
constexpr size_t SHA512_SIZE = 64;

int sha256(const uint8_t* data, size_t len, uint8_t* hash);

int sha512(const uint8_t* data, size_t len, uint8_t* hash);

} // namespace primitives
} // namespace enchant

#endif
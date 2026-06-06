#ifndef ENCHANT_PRIMITIVES_XEDDSA_HPP
#define ENCHANT_PRIMITIVES_XEDDSA_HPP

#include <cstdint>
#include <cstddef>
#include "enchant/error.h"

namespace enchant {
namespace primitives {

constexpr size_t XEDDSA_SIGNATURE_SIZE = 64;
constexpr size_t XEDDSA_PUBLIC_KEY_SIZE = 32;
constexpr size_t XEDDSA_PRIVATE_KEY_SIZE = 32;

int xeddsa_sign(const uint8_t* message, size_t message_len,
                const uint8_t* x25519_private_key, uint8_t* signature);

int xeddsa_verify(const uint8_t* message, size_t message_len,
                  const uint8_t* signature,
                  const uint8_t* x25519_public_key);

int xeddsa_derive_public_key(const uint8_t* x25519_private_key,
                             uint8_t* xeddsa_public_key);

} // namespace primitives
} // namespace enchant

#endif

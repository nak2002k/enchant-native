#ifndef ENCHANT_PRIMITIVES_X25519_HPP
#define ENCHANT_PRIMITIVES_X25519_HPP

#include <cstdint>
#include <cstddef>
#include "enchant/error.h"

namespace enchant {
namespace primitives {

constexpr size_t X25519_PUBLIC_KEY_SIZE = 32;
constexpr size_t X25519_PRIVATE_KEY_SIZE = 32;

constexpr size_t X25519_SHARED_SECRET_SIZE = 32;

int x25519_keypair(uint8_t* public_key, uint8_t* private_key);

int x25519_dh(const uint8_t* private_key, const uint8_t* public_key,
              uint8_t* shared_secret);

int x25519_pubkey_from_priv(const uint8_t* private_key, uint8_t* public_key);

int is_canonical_x25519_public_key(const uint8_t* public_key);

int validate_x25519_public_key(const uint8_t* public_key);

int x25519_validate_private_key(const uint8_t* private_key);

int x25519_compute_public_key(const uint8_t* private_key, uint8_t* public_key);

int x25519_shared_secret(const uint8_t* private_key, const uint8_t* public_key,
                        uint8_t* shared_secret);

} // namespace primitives
} // namespace enchant

#endif

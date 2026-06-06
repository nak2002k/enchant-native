#ifndef ENCHANT_PRIMITIVES_ED25519_HPP
#define ENCHANT_PRIMITIVES_ED25519_HPP

#include <cstdint>
#include <cstddef>
#include <vector>
#include "enchant/error.h"

namespace enchant {
namespace primitives {

constexpr size_t ED25519_PUBLIC_KEY_SIZE = 32;
constexpr size_t ED25519_SEED_SIZE = 32;
constexpr size_t ED25519_SIGNATURE_SIZE = 64;

constexpr size_t ED25519_KEYPAIR_SIZE = ED25519_PUBLIC_KEY_SIZE + ED25519_SEED_SIZE;

int ed25519_keypair(uint8_t* public_key, uint8_t* private_seed);

int ed25519_sign(const uint8_t* message, size_t message_len,
                 const uint8_t* private_seed, uint8_t* signature);

int ed25519_verify(const uint8_t* message, size_t message_len,
                   const uint8_t* signature, const uint8_t* public_key);

int ed25519_pk_to_x25519(const uint8_t* ed25519_pk, uint8_t* x25519_pk);

int ed25519_sk_to_x25519(const uint8_t* ed25519_sk, uint8_t* x25519_sk);

int ed25519_validate_public_key(const uint8_t* public_key);

int ed25519_validate_private_key(const uint8_t* private_seed);

int ed25519_validate_signature(const uint8_t* signature);

int ed25519_keypair_from_x25519(const uint8_t* x25519_private_key,
                                 uint8_t* ed25519_seed, uint8_t* ed25519_public_key);

int ed25519_public_key_from_x25519(const uint8_t* x25519_public_key,
                                    uint8_t* ed25519_public_key);

} // namespace primitives
} // namespace enchant

#endif

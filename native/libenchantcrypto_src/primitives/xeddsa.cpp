#include "primitives/xeddsa.hpp"
#include "primitives/ed25519.hpp"
#include "primitives/x25519.hpp"
#include "primitives/constant_time.hpp"
#include "secure/buffer.hpp"
#include <sodium.h>
#include <cstring>

namespace enchant {
namespace primitives {

int xeddsa_derive_public_key(const uint8_t* x25519_private_key,
                             uint8_t* xeddsa_public_key) {
    if (!x25519_private_key || !xeddsa_public_key) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    uint8_t h[64];
    int rc = crypto_hash_sha512(h, x25519_private_key, 32);
    if (rc != 0) {
        sodium_memzero(h, sizeof(h));
        return ENCHANT_ERROR_INTERNAL;
    }

    uint8_t ed_seed[32];
    ed_seed[0] = h[0] & 248;
    for (int i = 1; i < 31; i++) {
        ed_seed[i] = h[i];
    }
    ed_seed[31] = (h[31] & 127) | 64;
    sodium_memzero(h, sizeof(h));

    uint8_t ed_pk[32];
    uint8_t ed_sk[64];
    rc = crypto_sign_ed25519_seed_keypair(ed_pk, ed_sk, ed_seed);
    sodium_memzero(ed_seed, 32);
    sodium_memzero(ed_sk, 64);

    if (rc != 0) {
        std::memset(xeddsa_public_key, 0, XEDDSA_PUBLIC_KEY_SIZE);
        return ENCHANT_ERROR_INTERNAL;
    }

    std::memcpy(xeddsa_public_key, ed_pk, 32);
    sodium_memzero(ed_pk, 32);
    return ENCHANT_SUCCESS;
}

int xeddsa_sign(const uint8_t* message, size_t message_len,
                const uint8_t* x25519_private_key, uint8_t* signature) {
    if (!x25519_private_key || !signature) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    uint8_t ed_seed[32];
    int rc = ed25519_keypair_from_x25519(x25519_private_key, ed_seed, nullptr);
    if (rc != ENCHANT_SUCCESS) {
        return rc;
    }

    rc = ed25519_sign(message, message_len, ed_seed, signature);
    sodium_memzero(ed_seed, 32);
    return rc;
}

int xeddsa_verify(const uint8_t* message, size_t message_len,
                  const uint8_t* signature,
                  const uint8_t* ed25519_public_key) {
    if (!message || !signature || !ed25519_public_key) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    return ed25519_verify(message, message_len, signature, ed25519_public_key);
}

} // namespace primitives
} // namespace enchant

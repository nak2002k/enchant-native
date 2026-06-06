#include "primitives/x25519.hpp"
#include "primitives/constant_time.hpp"
#include <sodium.h>
#include <cstring>

namespace enchant {
namespace primitives {

int is_canonical_x25519_public_key(const uint8_t* public_key) {
    if (!public_key) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    uint8_t zero[32] = {0};
    if (sodium_memcmp(public_key, zero, 32) == 0) {
        return 0;
    }

    uint8_t identity[32] = {0};
    identity[0] = 0x01;
    if (sodium_memcmp(public_key, identity, 32) == 0) {
        return 0;
    }

    uint8_t field_p[32] = {
        0xed, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
        0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
        0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
        0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0x7f
    };
    if (sodium_memcmp(public_key, field_p, 32) == 0) {
        return 0;
    }

    if ((public_key[31] & 0x80) != 0) {
        return 0;
    }

    return 1;
}

int validate_x25519_public_key(const uint8_t* public_key) {
    if (!public_key) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    int rc = is_canonical_x25519_public_key(public_key);
    if (rc <= 0) {
        return ENCHANT_ERROR_INVALID_KEY_SIZE;
    }

    return ENCHANT_SUCCESS;
}

int x25519_keypair(uint8_t* public_key, uint8_t* private_key) {
    if (!public_key || !private_key) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    randombytes_buf(private_key, X25519_PRIVATE_KEY_SIZE);

    private_key[0] &= 0xF8;
    private_key[31] &= 0x7F;
    private_key[31] |= 0x40;

    int rc = crypto_scalarmult_curve25519_base(public_key, private_key);
    if (rc != 0) {
        sodium_memzero(private_key, X25519_PRIVATE_KEY_SIZE);
        memset(public_key, 0, X25519_PUBLIC_KEY_SIZE);
        return ENCHANT_ERROR_INTERNAL;
    }

    return ENCHANT_SUCCESS;
}

int x25519_dh(const uint8_t* private_key, const uint8_t* public_key,
              uint8_t* shared_secret) {
    if (!private_key || !public_key || !shared_secret) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    int crc = is_canonical_x25519_public_key(public_key);
    if (crc <= 0) {
        sodium_memzero(shared_secret, X25519_PUBLIC_KEY_SIZE);
        return ENCHANT_ERROR_INVALID_KEY_SIZE;
    }

    uint8_t private_clamped[32];
    memcpy(private_clamped, private_key, 32);
    private_clamped[0] &= 0xF8;
    private_clamped[31] &= 0x7F;
    private_clamped[31] |= 0x40;

    int rc = crypto_scalarmult_curve25519(shared_secret, private_clamped, public_key);
    sodium_memzero(private_clamped, 32);

    if (rc != 0) {
        memset(shared_secret, 0, X25519_PUBLIC_KEY_SIZE);
        return ENCHANT_ERROR_INTERNAL;
    }

    if (sodium_is_zero(shared_secret, X25519_PUBLIC_KEY_SIZE)) {
        memset(shared_secret, 0, X25519_PUBLIC_KEY_SIZE);
        return ENCHANT_ERROR_INVALID_KEY_SIZE;
    }

    return ENCHANT_SUCCESS;
}

int x25519_pubkey_from_priv(const uint8_t* private_key, uint8_t* public_key) {
    if (!private_key || !public_key) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    uint8_t private_clamped[32];
    memcpy(private_clamped, private_key, 32);
    private_clamped[0] &= 0xF8;
    private_clamped[31] &= 0x7F;
    private_clamped[31] |= 0x40;

    int rc = crypto_scalarmult_curve25519_base(public_key, private_clamped);
    sodium_memzero(private_clamped, 32);

    if (rc != 0) {
        memset(public_key, 0, X25519_PUBLIC_KEY_SIZE);
        return ENCHANT_ERROR_INTERNAL;
    }

    return ENCHANT_SUCCESS;
}

int x25519_validate_private_key(const uint8_t* private_key) {
    if (!private_key) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    if ((private_key[31] & 0xF8) != 0) {
        return ENCHANT_ERROR_INVALID_KEY_SIZE;
    }

    if ((private_key[31] & 0x40) == 0) {
        return ENCHANT_ERROR_INVALID_KEY_SIZE;
    }

    uint8_t zero[32] = {0};
    if (sodium_memcmp(private_key, zero, 32) == 0) {
        return ENCHANT_ERROR_INVALID_KEY_SIZE;
    }

    return ENCHANT_SUCCESS;
}

int x25519_compute_public_key(const uint8_t* private_key, uint8_t* public_key) {
    if (!private_key || !public_key) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    int rc = x25519_validate_private_key(private_key);
    if (rc != ENCHANT_SUCCESS) {
        memset(public_key, 0, X25519_PUBLIC_KEY_SIZE);
        return rc;
    }

    uint8_t private_clamped[32];
    memcpy(private_clamped, private_key, 32);
    private_clamped[0] &= 0xF8;
    private_clamped[31] &= 0x7F;
    private_clamped[31] |= 0x40;

    rc = crypto_scalarmult_curve25519_base(public_key, private_clamped);
    sodium_memzero(private_clamped, 32);

    if (rc != 0) {
        memset(public_key, 0, X25519_PUBLIC_KEY_SIZE);
        return ENCHANT_ERROR_INTERNAL;
    }

    return ENCHANT_SUCCESS;
}

int x25519_shared_secret(const uint8_t* private_key, const uint8_t* public_key,
                        uint8_t* shared_secret) {
    return x25519_dh(private_key, public_key, shared_secret);
}

} // namespace primitives
} // namespace enchant

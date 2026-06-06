#include "primitives/ed25519.hpp"
#include "primitives/x25519.hpp"
#include "primitives/constant_time.hpp"
#include <sodium.h>
#include <cstring>

namespace enchant {
namespace primitives {

int ed25519_keypair(uint8_t* public_key, uint8_t* private_seed) {
    if (!public_key || !private_seed) {
        return ENCHANT_ERROR_NULL_POINTER;
    }
    randombytes_buf(private_seed, ED25519_SEED_SIZE);
    uint8_t sk[64];
    int rc = crypto_sign_ed25519_seed_keypair(public_key, sk, private_seed);
    sodium_memzero(sk, sizeof(sk));
    if (rc != 0) {
        memset(public_key, 0, ED25519_PUBLIC_KEY_SIZE);
        return ENCHANT_ERROR_INTERNAL;
    }
    return ENCHANT_SUCCESS;
}

int ed25519_sign(const uint8_t* message, size_t message_len,
                 const uint8_t* private_seed, uint8_t* signature) {
    if (!message || !private_seed || !signature) {
        return ENCHANT_ERROR_NULL_POINTER;
    }
    uint8_t pk[32];
    uint8_t sk[64];
    int rc = crypto_sign_ed25519_seed_keypair(pk, sk, private_seed);
    if (rc != 0) {
        sodium_memzero(sk, sizeof(sk));
        sodium_memzero(pk, sizeof(pk));
        memset(signature, 0, ED25519_SIGNATURE_SIZE);
        return ENCHANT_ERROR_INTERNAL;
    }
    unsigned long long sig_len = 0;
    rc = crypto_sign_ed25519_detached(signature, &sig_len, message, message_len, sk);
    sodium_memzero(sk, sizeof(sk));
    sodium_memzero(pk, sizeof(pk));
    if (rc != 0 || sig_len != ED25519_SIGNATURE_SIZE) {
        memset(signature, 0, ED25519_SIGNATURE_SIZE);
        return ENCHANT_ERROR_INTERNAL;
    }
    return ENCHANT_SUCCESS;
}

int ed25519_verify(const uint8_t* message, size_t message_len,
                   const uint8_t* signature, const uint8_t* public_key) {
    if (!message || !signature || !public_key) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    if ((signature[63] & 0xE0) != 0) {
        return ENCHANT_ERROR_SIGNATURE_INVALID;
    }

    std::vector<unsigned char> sm(64 + message_len);
    memcpy(sm.data(), signature, 64);
    memcpy(sm.data() + 64, message, message_len);
    std::vector<unsigned char> m(message_len);
    unsigned long long mlen = 0;
    int rc = crypto_sign_ed25519_open(m.data(), &mlen, sm.data(), sm.size(), public_key);
    sodium_memzero(m.data(), m.size());
    sodium_memzero(sm.data(), sm.size());

    if (rc != 0) {
        return ENCHANT_ERROR_SIGNATURE_INVALID;
    }
    return ENCHANT_SUCCESS;
}

int ed25519_pk_to_x25519(const uint8_t* ed25519_pk, uint8_t* x25519_pk) {
    if (!ed25519_pk || !x25519_pk) {
        return ENCHANT_ERROR_NULL_POINTER;
    }
    int rc = crypto_sign_ed25519_pk_to_curve25519(x25519_pk, ed25519_pk);
    if (rc != 0) {
        memset(x25519_pk, 0, X25519_PUBLIC_KEY_SIZE);
        return ENCHANT_ERROR_INVALID_KEY_SIZE;
    }
    return ENCHANT_SUCCESS;
}

int ed25519_sk_to_x25519(const uint8_t* ed25519_sk, uint8_t* x25519_sk) {
    if (!ed25519_sk || !x25519_sk) {
        return ENCHANT_ERROR_NULL_POINTER;
    }
    int rc = crypto_sign_ed25519_sk_to_curve25519(x25519_sk, ed25519_sk);
    if (rc != 0) {
        memset(x25519_sk, 0, X25519_PRIVATE_KEY_SIZE);
        return ENCHANT_ERROR_INTERNAL;
    }
    return ENCHANT_SUCCESS;
}

int ed25519_validate_public_key(const uint8_t* public_key) {
    if (!public_key) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    uint8_t dummy[64];
    unsigned long long dummylen = 0;
    std::vector<unsigned char> sig_and_msg(64 + 1);
    memset(sig_and_msg.data(), 0, 64);
    sig_and_msg[64] = 0;

    int rc = crypto_sign_ed25519_open(dummy, &dummylen, sig_and_msg.data(), sig_and_msg.size(), public_key);

    if (rc == 0) {
        sodium_memzero(dummy, sizeof(dummy));
        return ENCHANT_ERROR_SIGNATURE_INVALID;
    }

    return ENCHANT_SUCCESS;
}

int ed25519_validate_private_key(const uint8_t* private_seed) {
    if (!private_seed) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    uint8_t zero[32] = {0};
    if (sodium_memcmp(private_seed, zero, 32) == 0) {
        return ENCHANT_ERROR_INVALID_KEY_SIZE;
    }

    return ENCHANT_SUCCESS;
}

int ed25519_validate_signature(const uint8_t* signature) {
    if (!signature) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    if ((signature[63] & 0xE0) != 0) {
        return ENCHANT_ERROR_SIGNATURE_INVALID;
    }

    uint8_t s[32];
    memcpy(s, signature + 32, 32);
    uint8_t zero[32] = {0};
    if (sodium_memcmp(s, zero, 32) == 0) {
        return ENCHANT_ERROR_SIGNATURE_INVALID;
    }

    return ENCHANT_SUCCESS;
}

int ed25519_keypair_from_x25519(const uint8_t* x25519_private_key,
                                 uint8_t* ed25519_seed, uint8_t* ed25519_public_key) {
    if (!x25519_private_key || !ed25519_seed) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    uint8_t h[64];
    int rc = crypto_hash_sha512(h, x25519_private_key, 32);
    if (rc != 0) {
        sodium_memzero(h, sizeof(h));
        return ENCHANT_ERROR_INTERNAL;
    }

    ed25519_seed[0] = h[0] & 248;
    for (int i = 1; i < 31; i++) {
        ed25519_seed[i] = h[i];
    }
    ed25519_seed[31] = (h[31] & 127) | 64;
    sodium_memzero(h, sizeof(h));

    if (ed25519_public_key) {
        uint8_t ed_sk[64];
        rc = crypto_sign_ed25519_seed_keypair(ed25519_public_key, ed_sk, ed25519_seed);
        sodium_memzero(ed_sk, 64);
        if (rc != 0) {
            memset(ed25519_public_key, 0, ED25519_PUBLIC_KEY_SIZE);
            return ENCHANT_ERROR_INTERNAL;
        }
    }

    return ENCHANT_SUCCESS;
}

int ed25519_public_key_from_x25519(const uint8_t* x25519_public_key,
                                    uint8_t* ed25519_public_key) {
    if (!x25519_public_key || !ed25519_public_key) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    int rc = ed25519_pk_to_x25519(x25519_public_key, ed25519_public_key);
    if (rc == 0) {
        return ENCHANT_SUCCESS;
    }

    uint8_t u[32];
    memcpy(u, x25519_public_key, 32);
    u[31] &= 0x7F;

    uint8_t y_bytes[32];
    memcpy(y_bytes, u, 32);

    uint8_t one[32] = {0};
    one[0] = 1;

    uint8_t u_minus_one[32];
    for (int i = 0; i < 32; i++) {
        u_minus_one[i] = u[i] - one[i];
    }

    uint8_t u_plus_one[32];
    for (int i = 0; i < 32; i++) {
        u_plus_one[i] = u[i] + one[i];
    }

    (void)y_bytes;
    (void)u_minus_one;
    (void)u_plus_one;

    return ENCHANT_ERROR_INTERNAL;
}

} // namespace primitives
} // namespace enchant

#include "primitives/hkdf.hpp"
#include "primitives/hmac.hpp"
#include <sodium.h>
#include <cstring>
#include <vector>

namespace enchant {
namespace primitives {

int hkdf_extract(const uint8_t* salt, size_t salt_len,
                 const uint8_t* ikm, size_t ikm_len,
                 uint8_t* prk) {
    if (!prk) return ENCHANT_ERROR_NULL_POINTER;
    if (!ikm) return ENCHANT_ERROR_NULL_POINTER;

    uint8_t default_salt[HKDF_HASH_LEN] = {0};
    if (!salt || salt_len == 0) {
        salt = default_salt;
        salt_len = HKDF_HASH_LEN;
    }

    crypto_auth_hmacsha256_state state;
    int rc = crypto_auth_hmacsha256_init(&state, salt, salt_len);
    if (rc != 0) {
        sodium_memzero(&state, sizeof(state));
        return ENCHANT_ERROR_INTERNAL;
    }
    rc = crypto_auth_hmacsha256_update(&state, ikm, ikm_len);
    if (rc != 0) {
        sodium_memzero(&state, sizeof(state));
        return ENCHANT_ERROR_INTERNAL;
    }
    rc = crypto_auth_hmacsha256_final(&state, prk);
    sodium_memzero(&state, sizeof(state));
    if (rc != 0) {
        return ENCHANT_ERROR_INTERNAL;
    }
    return ENCHANT_SUCCESS;
}

int hkdf_expand(const uint8_t* prk, size_t prk_len,
                const uint8_t* info, size_t info_len,
                uint8_t* okm, size_t okm_len) {
    if (!prk || !okm) return ENCHANT_ERROR_NULL_POINTER;
    if (prk_len == 0) return ENCHANT_ERROR_INVALID_KEY_SIZE;
    if (okm_len == 0 || okm_len > HKDF_MAX_OUTPUT) {
        return ENCHANT_ERROR_INVALID_KEY_SIZE;
    }

    uint8_t T[HKDF_HASH_LEN];
    size_t generated = 0;
    uint8_t counter = 1;

    while (generated < okm_len) {
        crypto_auth_hmacsha256_state state;
        int rc = crypto_auth_hmacsha256_init(&state, prk, prk_len);
        if (rc != 0) {
            sodium_memzero(&state, sizeof(state));
            sodium_memzero(T, sizeof(T));
            return ENCHANT_ERROR_INTERNAL;
        }
        if (generated > 0) {
            rc = crypto_auth_hmacsha256_update(&state, T, HKDF_HASH_LEN);
            if (rc != 0) {
                sodium_memzero(&state, sizeof(state));
                sodium_memzero(T, sizeof(T));
                return ENCHANT_ERROR_INTERNAL;
            }
        }
        rc = crypto_auth_hmacsha256_update(&state, info, info_len);
        if (rc != 0) {
            sodium_memzero(&state, sizeof(state));
            sodium_memzero(T, sizeof(T));
            return ENCHANT_ERROR_INTERNAL;
        }
        rc = crypto_auth_hmacsha256_update(&state, &counter, 1);
        if (rc != 0) {
            sodium_memzero(&state, sizeof(state));
            sodium_memzero(T, sizeof(T));
            return ENCHANT_ERROR_INTERNAL;
        }
        rc = crypto_auth_hmacsha256_final(&state, T);
        sodium_memzero(&state, sizeof(state));
        if (rc != 0) {
            sodium_memzero(T, sizeof(T));
            return ENCHANT_ERROR_INTERNAL;
        }

        size_t copy_len = HKDF_HASH_LEN;
        if (generated + copy_len > okm_len) {
            copy_len = okm_len - generated;
        }
        std::memcpy(okm + generated, T, copy_len);
        generated += copy_len;
        ++counter;
    }

    sodium_memzero(T, sizeof(T));
    return ENCHANT_SUCCESS;
}

int hkdf_derive(const uint8_t* ikm, size_t ikm_len,
                const uint8_t* salt, size_t salt_len,
                const uint8_t* info, size_t info_len,
                uint8_t* okm, size_t okm_len) {
    if (!ikm || !okm) return ENCHANT_ERROR_NULL_POINTER;
    if (okm_len == 0 || okm_len > HKDF_MAX_OUTPUT) {
        return ENCHANT_ERROR_INVALID_KEY_SIZE;
    }

    uint8_t prk[HKDF_HASH_LEN];
    int rc = hkdf_extract(salt, salt_len, ikm, ikm_len, prk);
    if (rc != ENCHANT_SUCCESS) {
        sodium_memzero(prk, sizeof(prk));
        return rc;
    }

    rc = hkdf_expand(prk, sizeof(prk), info, info_len, okm, okm_len);
    sodium_memzero(prk, sizeof(prk));
    return rc;
}

int hkdf_expand_label(const uint8_t* prk, size_t prk_len,
                      const uint8_t* info, size_t info_len,
                      uint8_t* okm, size_t okm_len) {
    if (!prk || !okm) return ENCHANT_ERROR_NULL_POINTER;
    if (okm_len == 0 || okm_len > HKDF_MAX_OUTPUT) {
        return ENCHANT_ERROR_INVALID_KEY_SIZE;
    }

    std::vector<uint8_t> labeled_info;
    labeled_info.push_back(static_cast<uint8_t>(okm_len >> 8));
    labeled_info.push_back(static_cast<uint8_t>(okm_len & 0xFF));
    labeled_info.push_back(0x07);  // Len("HPKE-v1") = 7
    labeled_info.push_back('H');
    labeled_info.push_back('P');
    labeled_info.push_back('K');
    labeled_info.push_back('E');
    labeled_info.push_back('-');
    labeled_info.push_back('v');
    labeled_info.push_back('1');
    labeled_info.push_back(0x00);
    labeled_info.push_back(0x20);
    labeled_info.push_back(0x00);
    labeled_info.push_back(0x01);
    labeled_info.push_back(0x00);
    labeled_info.push_back(0x02);
    labeled_info.push_back(static_cast<uint8_t>(info_len >> 8));
    labeled_info.push_back(static_cast<uint8_t>(info_len & 0xFF));
    if (info && info_len > 0) {
        labeled_info.insert(labeled_info.end(), info, info + info_len);
    }

    int rc = hkdf_expand(prk, prk_len, labeled_info.data(), labeled_info.size(), okm, okm_len);
    sodium_memzero(labeled_info.data(), labeled_info.size());
    return rc;
}

} // namespace primitives
} // namespace enchant

#include "primitives/hmac.hpp"
#include <sodium.h>

namespace enchant {
namespace primitives {

int hmac_sha256(const uint8_t* key, size_t key_len,
                const uint8_t* data, size_t data_len,
                uint8_t* mac) {
    if (!key || !data || !mac) return ENCHANT_ERROR_NULL_POINTER;
    if (key_len == 0 || key_len > crypto_auth_hmacsha256_KEYBYTES) {
        return ENCHANT_ERROR_INVALID_KEY_SIZE;
    }
    int rc = crypto_auth_hmacsha256(mac, data, data_len, key);
    return (rc == 0) ? ENCHANT_SUCCESS : ENCHANT_ERROR_INTERNAL;
}

int hmac_sha512(const uint8_t* key, size_t key_len,
                const uint8_t* data, size_t data_len,
                uint8_t* mac) {
    if (!key || !data || !mac) return ENCHANT_ERROR_NULL_POINTER;
    if (key_len == 0) {
        return ENCHANT_ERROR_INVALID_KEY_SIZE;
    }
    crypto_auth_hmacsha512_state state;
    if (crypto_auth_hmacsha512_init(&state, key, key_len) != 0) {
        return ENCHANT_ERROR_INTERNAL;
    }
    if (crypto_auth_hmacsha512_update(&state, data, data_len) != 0) {
        sodium_memzero(&state, sizeof(state));
        return ENCHANT_ERROR_INTERNAL;
    }
    if (crypto_auth_hmacsha512_final(&state, mac) != 0) {
        sodium_memzero(&state, sizeof(state));
        return ENCHANT_ERROR_INTERNAL;
    }
    sodium_memzero(&state, sizeof(state));
    return ENCHANT_SUCCESS;
}

} // namespace primitives
} // namespace enchant
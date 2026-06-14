#include "primitives/xchacha20.hpp"
#include <sodium.h>

namespace enchant {
namespace primitives {

int xchacha20_encrypt(const uint8_t* plaintext, size_t plaintext_len,
                      const uint8_t* key, const uint8_t* nonce,
                      uint8_t* ciphertext, size_t ciphertext_capacity) {
    if (!key || !nonce || !ciphertext) {
        return ENCHANT_ERROR_NULL_POINTER;
    }
    if (!plaintext && plaintext_len > 0) {
        return ENCHANT_ERROR_NULL_POINTER;
    }
    size_t min_capacity = plaintext_len + XCHACHA20_TAG_SIZE;
    if (ciphertext_capacity < min_capacity) {
        return ENCHANT_ERROR_BUFFER_TOO_SMALL;
    }
    unsigned long long out_len = 0;
    int rc = crypto_aead_xchacha20poly1305_ietf_encrypt(
        ciphertext, &out_len,
        plaintext, plaintext_len,
        nullptr, 0,
        nullptr, nonce, key);
    (void)out_len;
    return (rc == 0) ? ENCHANT_SUCCESS : ENCHANT_ERROR_INTERNAL;
}

int xchacha20_decrypt(const uint8_t* ciphertext, size_t ciphertext_len,
                      const uint8_t* key, const uint8_t* nonce,
                      uint8_t* plaintext, size_t plaintext_capacity) {
    if (!ciphertext || !key || !nonce || !plaintext) {
        return ENCHANT_ERROR_NULL_POINTER;
    }
    if (ciphertext_len < XCHACHA20_TAG_SIZE) {
        return ENCHANT_ERROR_CIPHERTEXT_TOO_SHORT;
    }
    if (plaintext_capacity < ciphertext_len - XCHACHA20_TAG_SIZE) {
        return ENCHANT_ERROR_BUFFER_TOO_SMALL;
    }
    unsigned long long pt_len = 0;
    int rc = crypto_aead_xchacha20poly1305_ietf_decrypt(
        plaintext, &pt_len,
        nullptr,
        ciphertext, ciphertext_len,
        nullptr, 0,
        nonce, key);
    return (rc == 0) ? ENCHANT_SUCCESS : ENCHANT_ERROR_DECRYPTION_FAILED;
}

int xchacha20_encrypt_ad(const uint8_t* plaintext, size_t plaintext_len,
                         const uint8_t* ad, size_t ad_len,
                         const uint8_t* key, const uint8_t* nonce,
                         uint8_t* ciphertext, size_t* ciphertext_len) {
    if (!key || !nonce || !ciphertext || !ciphertext_len) {
        return ENCHANT_ERROR_NULL_POINTER;
    }
    if (!plaintext && plaintext_len > 0) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    // Verify output buffer has enough capacity for ciphertext + tag
    if (*ciphertext_len < plaintext_len + XCHACHA20_TAG_SIZE) {
        return ENCHANT_ERROR_BUFFER_TOO_SMALL;
    }

    unsigned long long out_len = 0;
    int rc = crypto_aead_xchacha20poly1305_ietf_encrypt(
        ciphertext, &out_len,
        plaintext, plaintext_len,
        ad, ad_len,
        nullptr, nonce, key
    );
    if (rc != 0) {
        return ENCHANT_ERROR_INTERNAL;
    }
    *ciphertext_len = out_len;
    return ENCHANT_SUCCESS;
}

int xchacha20_decrypt_ad(const uint8_t* ciphertext, size_t ciphertext_len,
                         const uint8_t* ad, size_t ad_len,
                         const uint8_t* key, const uint8_t* nonce,
                         uint8_t* plaintext, size_t* plaintext_len) {
    if (!ciphertext || !key || !nonce || !plaintext || !plaintext_len) {
        return ENCHANT_ERROR_NULL_POINTER;
    }
    if (ciphertext_len < XCHACHA20_TAG_SIZE) {
        return ENCHANT_ERROR_CIPHERTEXT_TOO_SHORT;
    }
    unsigned long long out_len = 0;
    int rc = crypto_aead_xchacha20poly1305_ietf_decrypt(
        plaintext, &out_len,
        nullptr,
        ciphertext, ciphertext_len,
        ad, ad_len,
        nonce, key
    );
    if (rc != 0) {
        return ENCHANT_ERROR_DECRYPTION_FAILED;
    }
    *plaintext_len = out_len;
    return ENCHANT_SUCCESS;
}

} // namespace primitives
} // namespace enchant
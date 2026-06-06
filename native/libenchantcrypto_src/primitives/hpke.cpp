#include "primitives/hpke.hpp"
#include <openssl/hpke.h>
#include <openssl/curve25519.h>
#include <sodium.h>
#include <cstring>

namespace enchant {
namespace primitives {

int hpke_seal(const uint8_t* recipient_public_key,
              const uint8_t* info, size_t info_len,
              const uint8_t* aad, size_t aad_len,
              const uint8_t* plaintext, size_t plaintext_len,
              uint8_t* ciphertext, size_t* ciphertext_len) {
    if (!recipient_public_key || !info || !plaintext ||
        !ciphertext || !ciphertext_len) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    size_t needed = HPKE_ENC_LENGTH + plaintext_len + HPKE_TAG_LENGTH;
    if (*ciphertext_len < needed) {
        *ciphertext_len = needed;
        return ENCHANT_ERROR_BUFFER_TOO_SMALL;
    }

    EVP_HPKE_CTX ctx;
    EVP_HPKE_CTX_zero(&ctx);

    size_t enc_len = 0;
    int rc = EVP_HPKE_CTX_setup_sender(
        &ctx,
        ciphertext, &enc_len, HPKE_ENC_LENGTH,
        EVP_hpke_x25519_hkdf_sha256(),
        EVP_hpke_hkdf_sha256(),
        EVP_hpke_aes_256_gcm(),
        recipient_public_key, HPKE_ENC_LENGTH,
        info, info_len);

    if (rc != 1) {
        sodium_memzero(&ctx, sizeof(EVP_HPKE_CTX));
        EVP_HPKE_CTX_cleanup(&ctx);
        return ENCHANT_ERROR_INTERNAL;
    }

    size_t max_ct = *ciphertext_len - enc_len;
    size_t ct_len = 0;
    rc = EVP_HPKE_CTX_seal(
        &ctx,
        ciphertext + enc_len, &ct_len, max_ct,
        plaintext, plaintext_len,
        aad, aad_len);

    sodium_memzero(&ctx, sizeof(EVP_HPKE_CTX));
    EVP_HPKE_CTX_cleanup(&ctx);

    if (rc != 1) {
        std::memset(ciphertext, 0, enc_len + max_ct);
        return ENCHANT_ERROR_INTERNAL;
    }

    *ciphertext_len = enc_len + ct_len;
    return ENCHANT_SUCCESS;
}

int hpke_open(const uint8_t* recipient_private_key,
              const uint8_t* info, size_t info_len,
              const uint8_t* aad, size_t aad_len,
              const uint8_t* ciphertext, size_t ciphertext_len,
              uint8_t* plaintext, size_t* plaintext_len) {
    if (!recipient_private_key || !info || !ciphertext ||
        !plaintext || !plaintext_len) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    if (ciphertext_len < HPKE_ENC_LENGTH + HPKE_TAG_LENGTH) {
        return ENCHANT_ERROR_CIPHERTEXT_TOO_SHORT;
    }

    size_t enc_len = HPKE_ENC_LENGTH;
    const size_t sealed_len = ciphertext_len - enc_len;
    const size_t max_pt = sealed_len - HPKE_TAG_LENGTH;

    if (*plaintext_len < max_pt) {
        *plaintext_len = max_pt;
        return ENCHANT_ERROR_BUFFER_TOO_SMALL;
    }

    EVP_HPKE_KEY key;
    EVP_HPKE_KEY_zero(&key);

    int rc = EVP_HPKE_KEY_init(
        &key,
        EVP_hpke_x25519_hkdf_sha256(),
        recipient_private_key, HPKE_ENC_LENGTH);

    if (rc != 1) {
        EVP_HPKE_KEY_cleanup(&key);
        return ENCHANT_ERROR_INVALID_KEY_SIZE;
    }

    EVP_HPKE_CTX ctx;
    EVP_HPKE_CTX_zero(&ctx);

    rc = EVP_HPKE_CTX_setup_recipient(
        &ctx,
        &key,
        EVP_hpke_hkdf_sha256(),
        EVP_hpke_aes_256_gcm(),
        ciphertext, enc_len,
        info, info_len);

    if (rc != 1) {
        sodium_memzero(&ctx, sizeof(EVP_HPKE_CTX));
        EVP_HPKE_CTX_cleanup(&ctx);
        EVP_HPKE_KEY_cleanup(&key);
        return ENCHANT_ERROR_INTERNAL;
    }

    size_t pt_len = 0;
    rc = EVP_HPKE_CTX_open(
        &ctx,
        plaintext, &pt_len, *plaintext_len,
        ciphertext + enc_len, sealed_len,
        aad, aad_len);

    sodium_memzero(&ctx, sizeof(EVP_HPKE_CTX));
    sodium_memzero(&key, sizeof(EVP_HPKE_KEY));
    EVP_HPKE_CTX_cleanup(&ctx);
    EVP_HPKE_KEY_cleanup(&key);

    if (rc != 1) {
        std::memset(plaintext, 0, *plaintext_len);
        return ENCHANT_ERROR_DECRYPTION_FAILED;
    }

    *plaintext_len = pt_len;
    return ENCHANT_SUCCESS;
}

} // namespace primitives
} // namespace enchant

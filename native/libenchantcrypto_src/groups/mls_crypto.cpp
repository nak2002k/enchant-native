#include "groups/mls_crypto.hpp"
#include "primitives/hkdf.hpp"
#include "primitives/xchacha20.hpp"
#include "primitives/random.hpp"
#include "secure/buffer.hpp"
#include <cstring>
#include <vector>

namespace enchant {
namespace groups {

int group_derive_key(
    const uint8_t* epoch_secret, size_t epoch_secret_len,
    const uint8_t* const* member_identity_keys, size_t member_count,
    uint8_t* group_key
) {
    if (!epoch_secret || !member_identity_keys || !group_key)
        return ENCHANT_ERROR_NULL_POINTER;
    if (epoch_secret_len != EPOCH_SECRET_SIZE)
        return ENCHANT_ERROR_INVALID_KEY_SIZE;

    size_t ikm_len = EPOCH_SECRET_SIZE + (member_count * MEMBER_IK_SIZE);
    std::vector<uint8_t> ikm(ikm_len);

    for (size_t i = 0; i < member_count; i++) {
        if (!member_identity_keys[i]) {
            return ENCHANT_ERROR_NULL_POINTER;
        }
    }
    memcpy(ikm.data(), epoch_secret, EPOCH_SECRET_SIZE);
    for (size_t i = 0; i < member_count; i++) {
        memcpy(ikm.data() + EPOCH_SECRET_SIZE + (i * MEMBER_IK_SIZE),
               member_identity_keys[i], MEMBER_IK_SIZE);
    }

    uint8_t salt[32] = {0};
    const uint8_t info[] = "EnchantGroup";
    int rc = primitives::hkdf_derive(
        ikm.data(), ikm_len,
        salt, 32,
        info, sizeof(info) - 1,
        group_key, GROUP_KEY_SIZE
    );

    sodium_memzero(ikm.data(), ikm.size());
    sodium_memzero(salt, sizeof(salt));
    return rc;
}

int group_encrypt(
    const uint8_t* plaintext, size_t plaintext_len,
    const uint8_t* group_key,
    uint8_t* output, size_t output_capacity, size_t* output_len
) {
    if (!group_key || !output || !output_len)
        return ENCHANT_ERROR_NULL_POINTER;
    if (!plaintext && plaintext_len > 0)
        return ENCHANT_ERROR_NULL_POINTER;

    size_t needed = GROUP_NONCE_SIZE + plaintext_len + GROUP_TAG_SIZE;
    if (output_capacity < needed) {
        *output_len = needed;
        return ENCHANT_ERROR_BUFFER_TOO_SMALL;
    }

    uint8_t nonce[GROUP_NONCE_SIZE];
    primitives::random_bytes(nonce, GROUP_NONCE_SIZE);

    memcpy(output, nonce, GROUP_NONCE_SIZE);

    int rc = primitives::xchacha20_encrypt(
        plaintext, plaintext_len,
        group_key, nonce,
        output + GROUP_NONCE_SIZE, needed - GROUP_NONCE_SIZE
    );
    if (rc != ENCHANT_SUCCESS) return rc;

    *output_len = needed;
    return ENCHANT_SUCCESS;
}

int group_decrypt(
    const uint8_t* input, size_t input_len,
    const uint8_t* group_key,
    uint8_t* plaintext, size_t* plaintext_len
) {
    if (!input || !group_key || !plaintext || !plaintext_len)
        return ENCHANT_ERROR_NULL_POINTER;

    if (input_len < GROUP_NONCE_SIZE + GROUP_TAG_SIZE)
        return ENCHANT_ERROR_CIPHERTEXT_TOO_SHORT;

    const uint8_t* nonce = input;
    const uint8_t* ciphertext = input + GROUP_NONCE_SIZE;
    size_t ciphertext_len = input_len - GROUP_NONCE_SIZE;

    int rc = primitives::xchacha20_decrypt(
        ciphertext, ciphertext_len,
        group_key, nonce,
        plaintext, ciphertext_len
    );
    if (rc != ENCHANT_SUCCESS) return rc;

    *plaintext_len = ciphertext_len - GROUP_TAG_SIZE;
    return ENCHANT_SUCCESS;
}

} // namespace groups
} // namespace enchant
#ifndef ENCHANT_GROUPS_MLS_CRYPTO_HPP
#define ENCHANT_GROUPS_MLS_CRYPTO_HPP

#include <cstdint>
#include <cstddef>
#include "enchant/error.h"

namespace enchant {
namespace groups {

constexpr size_t GROUP_KEY_SIZE = 32;
constexpr size_t GROUP_NONCE_SIZE = 24;
constexpr size_t GROUP_TAG_SIZE = 16;
constexpr size_t EPOCH_SECRET_SIZE = 32;
constexpr size_t MEMBER_IK_SIZE = 32;

int group_derive_key(
    const uint8_t* epoch_secret, size_t epoch_secret_len,
    const uint8_t* const* member_identity_keys, size_t member_count,
    uint8_t* group_key
);

int group_encrypt(
    const uint8_t* plaintext, size_t plaintext_len,
    const uint8_t* group_key,
    uint8_t* output, size_t output_capacity, size_t* output_len
);

int group_decrypt(
    const uint8_t* input, size_t input_len,
    const uint8_t* group_key,
    uint8_t* plaintext, size_t* plaintext_len
);

} // namespace groups
} // namespace enchant

#endif
#include "veil_v2.hpp"
#include "primitives/x25519.hpp"
#include "primitives/hkdf.hpp"
#include "primitives/xchacha20.hpp"
#include "primitives/random.hpp"
#include "secure/buffer.hpp"
#include "sealed_sender.pb.h"
#include <sodium.h>
#include <cstring>
#include <string>

namespace enchant {
namespace veil {

static void xor_32(uint8_t* dest, const uint8_t* src) {
    for (size_t i = 0; i < 32; i++) {
        dest[i] ^= src[i];
    }
}

DerivedKeys::DerivedKeys(const uint8_t* m, size_t m_len) {
    if (m && m_len == 32) {
        memcpy(m_, m, 32);
    } else {
        memset(m_, 0, 32);
    }
}

int DerivedKeys::derive_e(uint8_t* private_key, uint8_t* public_key) {
    uint8_t salt[32] = {0};
    uint8_t r[32];
    int rc = primitives::hkdf_derive(m_, 32, salt, 32,
                                     LABEL_R, sizeof(LABEL_R) - 1, r, 32);
    if (rc != ENCHANT_SUCCESS) {
        sodium_memzero(r, sizeof(r));
        return rc;
    }

    memcpy(private_key, r, 32);
    sodium_memzero(r, sizeof(r));
    rc = primitives::x25519_pubkey_from_priv(private_key, public_key);
    if (rc != ENCHANT_SUCCESS) return rc;

    return ENCHANT_VEIL_SUCCESS;
}

int DerivedKeys::derive_k(uint8_t* k) {
    uint8_t salt[32] = {0};
    int rc = primitives::hkdf_derive(m_, 32, salt, 32,
                                     LABEL_K, sizeof(LABEL_K) - 1, k, 32);
    if (rc != ENCHANT_SUCCESS) return rc;
    return ENCHANT_VEIL_SUCCESS;
}

int apply_agreement_xor(const uint8_t* our_private, const uint8_t* our_public,
                        const uint8_t* their_public,
                        Direction direction,
                        const uint8_t* input,
                        uint8_t* output) {
    uint8_t agreement[32];
    int rc = primitives::x25519_dh(our_private, their_public, agreement);
    if (rc != ENCHANT_SUCCESS) {
        sodium_memzero(agreement, sizeof(agreement));
        return rc;
    }

    std::vector<uint8_t> agreement_key_input;
    agreement_key_input.insert(agreement_key_input.end(), agreement, agreement + 32);
    sodium_memzero(agreement, sizeof(agreement));

    if (direction == Direction::Sending) {
        agreement_key_input.insert(agreement_key_input.end(), our_public, our_public + 32);
        agreement_key_input.insert(agreement_key_input.end(), their_public, their_public + 32);
    } else {
        agreement_key_input.insert(agreement_key_input.end(), their_public, their_public + 32);
        agreement_key_input.insert(agreement_key_input.end(), our_public, our_public + 32);
    }

    uint8_t salt[32] = {0};
    uint8_t derived[32];
    rc = primitives::hkdf_derive(agreement_key_input.data(), agreement_key_input.size(),
                                  salt, 32,
                                  LABEL_DH, sizeof(LABEL_DH) - 1,
                                  derived, 32);
    sodium_memzero(agreement_key_input.data(), agreement_key_input.size());
    if (rc != ENCHANT_SUCCESS) {
        sodium_memzero(derived, sizeof(derived));
        return rc;
    }

    memcpy(output, derived, 32);
    sodium_memzero(derived, sizeof(derived));
    xor_32(output, input);

    return ENCHANT_VEIL_SUCCESS;
}

int compute_authentication_tag(const uint8_t* our_private,
                                const uint8_t* our_public,
                                const uint8_t* their_identity_public,
                                Direction direction,
                                const uint8_t* ephemeral_public,
                                const uint8_t* encrypted_message_key,
                                uint8_t* tag) {
    uint8_t agreement[32];
    int rc = primitives::x25519_dh(our_private, their_identity_public, agreement);
    if (rc != ENCHANT_SUCCESS) {
        sodium_memzero(agreement, sizeof(agreement));
        return rc;
    }

    std::vector<uint8_t> at_input;
    at_input.insert(at_input.end(), agreement, agreement + 32);
    sodium_memzero(agreement, sizeof(agreement));
    at_input.insert(at_input.end(), ephemeral_public, ephemeral_public + 32);
    at_input.insert(at_input.end(), encrypted_message_key, encrypted_message_key + 32);

    if (direction == Direction::Sending) {
        at_input.insert(at_input.end(), our_public, our_public + 32);
        at_input.insert(at_input.end(), their_identity_public, their_identity_public + 32);
    } else {
        at_input.insert(at_input.end(), their_identity_public, their_identity_public + 32);
        at_input.insert(at_input.end(), our_public, our_public + 32);
    }

    uint8_t salt[32] = {0};
    rc = primitives::hkdf_derive(at_input.data(), at_input.size(),
                                   salt, 32,
                                   LABEL_DH_S, sizeof(LABEL_DH_S) - 1,
                                   tag, AUTH_TAG_LEN);
    sodium_memzero(at_input.data(), at_input.size());
    if (rc != ENCHANT_SUCCESS) return rc;
    return ENCHANT_VEIL_SUCCESS;
}

int sealed_sender_v2_encrypt(const uint8_t* sender_identity_private,
                              const uint8_t* sender_identity_public,
                              const std::vector<std::pair<uint32_t, secure::SecureBuffer>>& recipients,
                              const UnidentifiedSenderMessageContent& usmc,
                              uint8_t* output, size_t* output_len) {
    if (!sender_identity_private || !sender_identity_public || !output || !output_len)
        return ENCHANT_VEIL_ERROR_NULL_POINTER;
    if (recipients.empty())
        return ENCHANT_VEIL_ERROR_PREKEY_NOT_FOUND;
    if (recipients.size() > 1000)
        return ENCHANT_VEIL_ERROR_INVALID_FORMAT;

    size_t num_recipients = recipients.size();

    uint8_t m[32];
    primitives::random_bytes(m, 32);

    DerivedKeys keys(m, 32);

    uint8_t ephemeral_private[32], ephemeral_public[32];
    int rc = keys.derive_e(ephemeral_private, ephemeral_public);
    if (rc != ENCHANT_VEIL_SUCCESS) return rc;

    uint8_t k[32];
    rc = keys.derive_k(k);
    if (rc != ENCHANT_VEIL_SUCCESS) {
        sodium_memzero(ephemeral_private, sizeof(ephemeral_private));
        return rc;
    }

    const auto& usmc_data = usmc.serialized();
    secure::SecureBuffer ct(usmc_data.size() + AUTH_TAG_LEN);
    uint8_t message_nonce[24];
    primitives::random_bytes(message_nonce, 24);
    rc = primitives::xchacha20_encrypt(
        usmc_data.data(), usmc_data.size(),
        k, message_nonce, ct.data(), ct.size());
    sodium_memzero(k, sizeof(k));
    if (rc != ENCHANT_SUCCESS) {
        sodium_memzero(ephemeral_private, sizeof(ephemeral_private));
        return rc;
    }

    size_t recipient_entry_size = 4 + 32 + MESSAGE_KEY_LEN + AUTH_TAG_LEN;
    size_t total_len = 1 + 4 + num_recipients * recipient_entry_size + PUBLIC_KEY_LEN + 24 + ct.size();

    if (*output_len < total_len) {
        *output_len = total_len;
        sodium_memzero(ephemeral_private, sizeof(ephemeral_private));
        return ENCHANT_VEIL_ERROR_BUFFER_TOO_SMALL;
    }

    size_t offset = 0;
    output[offset++] = SEALED_SENDER_V2_SERVICE_ID_FULL_VERSION;

    output[offset++] = static_cast<uint8_t>(num_recipients & 0xFF);
    output[offset++] = static_cast<uint8_t>((num_recipients >> 8) & 0xFF);
    output[offset++] = static_cast<uint8_t>((num_recipients >> 16) & 0xFF);
    output[offset++] = static_cast<uint8_t>((num_recipients >> 24) & 0xFF);

    for (const auto& recipient : recipients) {
        memcpy(output + offset, &recipient.first, 4);
        offset += 4;

        memcpy(output + offset, recipient.second.data(), 32);
        offset += 32;

        uint8_t c_i[32];
        rc = apply_agreement_xor(ephemeral_private, ephemeral_public,
                                  recipient.second.data(),
                                  Direction::Sending,
                                  m, c_i);
        if (rc != ENCHANT_VEIL_SUCCESS) {
            sodium_memzero(c_i, sizeof(c_i));
            sodium_memzero(ephemeral_private, sizeof(ephemeral_private));
            return rc;
        }
        memcpy(output + offset, c_i, 32);
        offset += 32;

        uint8_t at_i[AUTH_TAG_LEN];
        rc = compute_authentication_tag(sender_identity_private, sender_identity_public,
                                         recipient.second.data(),
                                         Direction::Sending,
                                         ephemeral_public, c_i, at_i);
        sodium_memzero(c_i, sizeof(c_i));
        if (rc != ENCHANT_VEIL_SUCCESS) {
            sodium_memzero(at_i, sizeof(at_i));
            sodium_memzero(ephemeral_private, sizeof(ephemeral_private));
            return rc;
        }
        memcpy(output + offset, at_i, AUTH_TAG_LEN);
        offset += AUTH_TAG_LEN;
    }

    memcpy(output + offset, ephemeral_public, PUBLIC_KEY_LEN);
    offset += PUBLIC_KEY_LEN;

    memcpy(output + offset, message_nonce, 24);
    offset += 24;

    memcpy(output + offset, ct.data(), ct.size());
    offset += ct.size();

    sodium_memzero(ephemeral_private, sizeof(ephemeral_private));
    *output_len = offset;
    return ENCHANT_VEIL_SUCCESS;
}

int sealed_sender_v2_decrypt_to_usmc(const uint8_t* recipient_private_key,
                                      const uint8_t* recipient_public,
                                      const uint8_t* data, size_t data_len,
                                      UnidentifiedSenderMessageContent& out_usmc) {
    if (!recipient_private_key || !recipient_public || !data)
        return ENCHANT_VEIL_ERROR_NULL_POINTER;

    size_t min_possible = 1 + 4 + 4 + 32 + 32 + 16 + 32 + 24 + 16;
    if (data_len < min_possible)
        return ENCHANT_VEIL_ERROR_CIPHERTEXT_TOO_SHORT;

    size_t offset = 0;
    uint8_t version = data[offset++];
    if (version != SEALED_SENDER_V2_SERVICE_ID_FULL_VERSION &&
        version != SEALED_SENDER_V2_UUID_FULL_VERSION)
        return ENCHANT_VEIL_ERROR_UNKNOWN_VERSION;

    uint32_t num_recipients = 0;
    num_recipients |= static_cast<uint32_t>(data[offset++]);
    num_recipients |= static_cast<uint32_t>(data[offset++]) << 8;
    num_recipients |= static_cast<uint32_t>(data[offset++]) << 16;
    num_recipients |= static_cast<uint32_t>(data[offset++]) << 24;

    if (num_recipients > 1000 || num_recipients == 0)
        return ENCHANT_VEIL_ERROR_INVALID_FORMAT;

    size_t entry_size = 4 + 32 + MESSAGE_KEY_LEN + AUTH_TAG_LEN;
    size_t entries_end = 1 + 4 + num_recipients * entry_size;
    size_t e_pub_offset = entries_end;
    size_t min_len = e_pub_offset + PUBLIC_KEY_LEN + 24 + AUTH_TAG_LEN;
    if (data_len < min_len)
        return ENCHANT_VEIL_ERROR_CIPHERTEXT_TOO_SHORT;

    const uint8_t* ephemeral_public = data + e_pub_offset;
    const uint8_t* message_nonce = ephemeral_public + PUBLIC_KEY_LEN;
    const uint8_t* ciphertext = message_nonce + 24;
    size_t ciphertext_len = data_len - (ciphertext - data);

    int found_index = -1;
    const uint8_t* found_c = nullptr;
    const uint8_t* found_at = nullptr;

    for (uint32_t i = 0; i < num_recipients; i++) {
        size_t entry_off = 1 + 4 + i * entry_size;
        const uint8_t* entry_c = data + entry_off + 4 + 32;
        const uint8_t* entry_at = data + entry_off + 4 + 32 + 32;

        uint8_t m_try[32];
        int rc = apply_agreement_xor(recipient_private_key, recipient_public,
                                      ephemeral_public,
                                      Direction::Receiving,
                                      entry_c, m_try);
        if (rc != ENCHANT_VEIL_SUCCESS) {
            sodium_memzero(m_try, sizeof(m_try));
            continue;
        }

        DerivedKeys keys_try(m_try, 32);
        uint8_t r_try[32], expected_eph[32];
        rc = keys_try.derive_e(r_try, expected_eph);
        sodium_memzero(m_try, sizeof(m_try));
        if (rc != ENCHANT_VEIL_SUCCESS) {
            sodium_memzero(r_try, sizeof(r_try));
            continue;
        }

        if (sodium_memcmp(expected_eph, ephemeral_public, PUBLIC_KEY_LEN) == 0) {
            sodium_memzero(r_try, sizeof(r_try));
            found_index = static_cast<int>(i);
            found_c = entry_c;
            found_at = entry_at;
            break;
        }
        sodium_memzero(r_try, sizeof(r_try));
    }

    if (found_index < 0)
        return ENCHANT_VEIL_ERROR_CERTIFICATE_SIGNATURE_INVALID;

    uint8_t m[32];
    int rc = apply_agreement_xor(recipient_private_key, recipient_public,
                                  ephemeral_public,
                                  Direction::Receiving,
                                  found_c, m);
    if (rc != ENCHANT_VEIL_SUCCESS) return rc;

    DerivedKeys keys(m, 32);

    uint8_t k[32];
    rc = keys.derive_k(k);
    sodium_memzero(m, 32);
    if (rc != ENCHANT_VEIL_SUCCESS) return rc;

    size_t plaintext_size = ciphertext_len - AUTH_TAG_LEN;
    std::vector<uint8_t> usmc_bytes(plaintext_size);
    rc = primitives::xchacha20_decrypt(ciphertext, ciphertext_len,
                                        k, message_nonce, usmc_bytes.data(), usmc_bytes.size());
    sodium_memzero(k, 32);
    if (rc != ENCHANT_SUCCESS) return rc;

    rc = out_usmc.deserialize(usmc_bytes.data(), plaintext_size);
    if (rc != ENCHANT_VEIL_SUCCESS) return rc;

    uint8_t at_check[AUTH_TAG_LEN];
    rc = compute_authentication_tag(recipient_private_key, recipient_public,
                                     out_usmc.sender().key().data(),
                                     Direction::Receiving,
                                     ephemeral_public, found_c, at_check);
    if (rc != ENCHANT_VEIL_SUCCESS) return rc;

    if (sodium_memcmp(found_at, at_check, AUTH_TAG_LEN) != 0)
        return ENCHANT_VEIL_ERROR_CERTIFICATE_SIGNATURE_INVALID;

    return ENCHANT_VEIL_SUCCESS;
}

}
}

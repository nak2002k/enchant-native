#include "veil_v1.hpp"
#include "primitives/x25519.hpp"
#include "primitives/hkdf.hpp"
#include "primitives/hmac.hpp"
#include "primitives/xchacha20.hpp"
#include "primitives/ed25519.hpp"
#include "primitives/random.hpp"
#include <cstring>

namespace enchant {
namespace veil {

static constexpr uint8_t SALT_PREFIX[] = {
    'U', 'n', 'i', 'd', 'e', 'n', 't', 'i', 'f', 'i', 'e', 'd',
    'D', 'e', 'l', 'i', 'v', 'e', 'r', 'y'
};

SealedSenderV1::EphemeralKeys SealedSenderV1::EphemeralKeys::calculate(
    const uint8_t* our_private,
    const uint8_t* our_public,
    const uint8_t* their_public,
    Direction direction) {

    uint8_t shared_secret[32];
    int rc = primitives::x25519_dh(our_private, their_public, shared_secret);
    if (rc != ENCHANT_SUCCESS) {
        sodium_memzero(shared_secret, sizeof(shared_secret));
        EphemeralKeys empty{};
        return empty;
    }

    std::vector<uint8_t> salt;
    salt.insert(salt.end(), SALT_PREFIX, SALT_PREFIX + sizeof(SALT_PREFIX));
    if (direction == Direction::Sending) {
        salt.insert(salt.end(), their_public, their_public + 32);
        salt.insert(salt.end(), our_public, our_public + 32);
    } else {
        salt.insert(salt.end(), our_public, our_public + 32);
        salt.insert(salt.end(), their_public, their_public + 32);
    }

    secure::SecureBuffer ikm(shared_secret, 32);
    sodium_memzero(shared_secret, sizeof(shared_secret));
    uint8_t derived[96];
    rc = primitives::hkdf_derive(ikm.data(), ikm.size(),
                                 salt.data(), salt.size(),
                                 nullptr, 0,
                                 derived, 96);
    if (rc != ENCHANT_SUCCESS) {
        EphemeralKeys empty{};
        return empty;
    }

    EphemeralKeys keys;
    std::memcpy(keys.chain_key, derived, 32);
    std::memcpy(keys.cipher_key, derived + 32, 32);
    std::memcpy(keys.mac_key, derived + 64, 32);
    sodium_memzero(derived, sizeof(derived));

    return keys;
}

SealedSenderV1::StaticKeys SealedSenderV1::StaticKeys::calculate(
    const uint8_t* our_private,
    const uint8_t* their_public,
    const uint8_t* chain_key,
    const uint8_t* ciphertext, size_t ciphertext_len) {

    uint8_t shared_secret[32];
    int rc = primitives::x25519_dh(our_private, their_public, shared_secret);
    if (rc != ENCHANT_SUCCESS) {
        sodium_memzero(shared_secret, sizeof(shared_secret));
        StaticKeys empty{};
        return empty;
    }

    std::vector<uint8_t> salt;
    salt.insert(salt.end(), chain_key, chain_key + 32);
    salt.insert(salt.end(), ciphertext, ciphertext + ciphertext_len);

    secure::SecureBuffer ikm(shared_secret, 32);
    sodium_memzero(shared_secret, sizeof(shared_secret));
    uint8_t derived[64];
    rc = primitives::hkdf_derive(ikm.data(), ikm.size(),
                                 salt.data(), salt.size(),
                                 nullptr, 0,
                                 derived, 64);
    if (rc != ENCHANT_SUCCESS) {
        StaticKeys empty{};
        return empty;
    }

    StaticKeys keys;
    std::memcpy(keys.cipher_key, derived, 32);
    std::memcpy(keys.mac_key, derived + 32, 32);
    sodium_memzero(derived, sizeof(derived));

    return keys;
}

int SealedSenderV1::encrypt_message(const uint8_t* plaintext, size_t plaintext_len,
                                     const uint8_t* cipher_key, const uint8_t* mac_key,
                                     uint8_t* output, size_t* output_len) {
    if (!cipher_key || !mac_key || !output || !output_len) return ENCHANT_VEIL_ERROR_NULL_POINTER;
    if (!plaintext && plaintext_len > 0) return ENCHANT_VEIL_ERROR_NULL_POINTER;

    uint8_t nonce[24];
    primitives::random_bytes(nonce, 24);

    secure::SecureBuffer ct(plaintext_len + 16);
    int rc = primitives::xchacha20_encrypt(
        plaintext, plaintext_len,
        cipher_key, nonce,
        ct.data(), ct.size()
    );
    if (rc != ENCHANT_SUCCESS) return rc;

    std::vector<uint8_t> hmac_input(24 + ct.size() + 32);
    std::memcpy(hmac_input.data(), nonce, 24);
    std::memcpy(hmac_input.data() + 24, ct.data(), ct.size());
    std::memcpy(hmac_input.data() + 24 + ct.size(), mac_key, 32);

    uint8_t mac[32];
    rc = primitives::hmac_sha256(mac_key, 32, hmac_input.data(), hmac_input.size(), mac);
    if (rc != ENCHANT_SUCCESS) return rc;

    std::memcpy(output, nonce, 24);
    std::memcpy(output + 24, ct.data(), ct.size());
    std::memcpy(output + 24 + ct.size(), mac, 32);
    *output_len = 24 + ct.size() + 32;

    return ENCHANT_VEIL_SUCCESS;
}

int SealedSenderV1::decrypt_message(const uint8_t* ciphertext, size_t ciphertext_len,
                                     const uint8_t* cipher_key, const uint8_t* mac_key,
                                     uint8_t* plaintext, size_t* plaintext_len) {
    if (!ciphertext || !cipher_key || !mac_key || !plaintext || !plaintext_len)
        return ENCHANT_VEIL_ERROR_NULL_POINTER;
    if (ciphertext_len < 24 + 16 + 32) return ENCHANT_VEIL_ERROR_CIPHERTEXT_TOO_SHORT;

    const uint8_t* nonce = ciphertext;
    size_t ct_len = ciphertext_len - 24 - 32;
    const uint8_t* ct = ciphertext + 24;
    const uint8_t* mac_received = ct + ct_len;

    std::vector<uint8_t> hmac_input(24 + ct_len + 32);
    std::memcpy(hmac_input.data(), nonce, 24);
    std::memcpy(hmac_input.data() + 24, ct, ct_len);
    std::memcpy(hmac_input.data() + 24 + ct_len, mac_key, 32);

    uint8_t mac_expected[32];
    int rc = primitives::hmac_sha256(mac_key, 32, hmac_input.data(), hmac_input.size(), mac_expected);
    if (rc != ENCHANT_SUCCESS) return rc;

    if (sodium_memcmp(mac_received, mac_expected, 32) != 0) {
        return ENCHANT_VEIL_ERROR_CERTIFICATE_SIGNATURE_INVALID;
    }

    rc = primitives::xchacha20_decrypt(
        ct, ct_len,
        cipher_key, nonce,
        plaintext, *plaintext_len
    );
    if (rc != ENCHANT_SUCCESS) return rc;

    *plaintext_len = ct_len;
    return ENCHANT_VEIL_SUCCESS;
}

int sealed_sender_encrypt_from_usmc(const uint8_t* our_identity_private,
                                     const uint8_t* our_identity_public,
                                     const uint8_t* their_identity_public,
                                     const UnidentifiedSenderMessageContent& usmc,
                                     uint8_t* output, size_t* output_len) {
    if (!our_identity_private || !our_identity_public || !their_identity_public ||
        !output || !output_len) {
        return ENCHANT_VEIL_ERROR_NULL_POINTER;
    }

    uint8_t ephemeral_private[32], ephemeral_public[32];
    primitives::x25519_keypair(ephemeral_public, ephemeral_private);

    auto eph_keys = SealedSenderV1::EphemeralKeys::calculate(
        ephemeral_private, ephemeral_public,
        their_identity_public,
        Direction::Sending);

    uint8_t static_key_ctext[32 + 16];
    uint8_t nonce[24] = {0};
    int rc = primitives::xchacha20_encrypt(
        our_identity_public, 32,
        eph_keys.cipher_key, nonce,
        static_key_ctext, sizeof(static_key_ctext)
    );
    if (rc != ENCHANT_SUCCESS) return rc;

    uint8_t static_mac[32];
    std::vector<uint8_t> mac_input(32 + 16 + 32);
    std::memcpy(mac_input.data(), static_key_ctext, 32 + 16);
    std::memcpy(mac_input.data() + 32 + 16, eph_keys.mac_key, 32);
    rc = primitives::hmac_sha256(eph_keys.mac_key, 32, mac_input.data(), mac_input.size(), static_mac);
    if (rc != ENCHANT_SUCCESS) return rc;

    auto stat_keys = SealedSenderV1::StaticKeys::calculate(
        our_identity_private, their_identity_public,
        eph_keys.chain_key, static_key_ctext, 32 + 16);

    const auto& usmc_data = usmc.serialized();
    size_t msg_out_len = usmc_data.size() + 32;
    std::vector<uint8_t> msg_buf(msg_out_len);
    rc = SealedSenderV1::encrypt_message(usmc_data.data(), usmc_data.size(),
                                          stat_keys.cipher_key, stat_keys.mac_key,
                                          msg_buf.data(), &msg_out_len);
    if (rc != ENCHANT_VEIL_SUCCESS) return rc;

    size_t offset = 0;
    output[offset++] = SEALED_SENDER_V1_FULL_VERSION;

    enchant::proto::sealed_sender::UnidentifiedSenderMessage pb;
    pb.set_ephemeralpublic(std::string(reinterpret_cast<const char*>(ephemeral_public), 32));

    std::vector<uint8_t> enc_static(32 + 16 + 32);
    std::memcpy(enc_static.data(), static_key_ctext, 32 + 16);
    std::memcpy(enc_static.data() + 32 + 16, static_mac, 32);
    pb.set_encryptedstatic(std::string(reinterpret_cast<const char*>(enc_static.data()), enc_static.size()));

    pb.set_encryptedmessage(std::string(reinterpret_cast<const char*>(msg_buf.data()), msg_out_len));

    std::string serialized = pb.SerializeAsString();
    if (offset + serialized.size() > *output_len) {
        *output_len = offset + serialized.size();
        return ENCHANT_VEIL_ERROR_BUFFER_TOO_SMALL;
    }
    std::memcpy(output + offset, serialized.data(), serialized.size());
    *output_len = offset + serialized.size();

    return ENCHANT_VEIL_SUCCESS;
}

int sealed_sender_decrypt_to_usmc(const uint8_t* our_identity_private,
                                   const uint8_t* our_identity_public,
                                   const uint8_t* ephemeral_public,
                                   const uint8_t* encrypted_static, size_t encrypted_static_len,
                                   const uint8_t* encrypted_message, size_t encrypted_message_len,
                                   UnidentifiedSenderMessageContent& out_usmc) {
    if (!our_identity_private || !our_identity_public ||
        !ephemeral_public || !encrypted_static || !encrypted_message) {
        return ENCHANT_VEIL_ERROR_NULL_POINTER;
    }

    if (encrypted_static_len < 32 + 16) {
        return ENCHANT_VEIL_ERROR_CIPHERTEXT_TOO_SHORT;
    }

    auto eph_keys = SealedSenderV1::EphemeralKeys::calculate(
        our_identity_private, our_identity_public,
        ephemeral_public,
        Direction::Receiving);

    uint8_t nonce[24] = {0};
    uint8_t decrypted_sender_key[32];
    int rc = primitives::xchacha20_decrypt(
        encrypted_static, 32 + 16,
        eph_keys.cipher_key, nonce,
        decrypted_sender_key, sizeof(decrypted_sender_key)
    );
    if (rc != ENCHANT_SUCCESS) return rc;

    auto stat_keys = SealedSenderV1::StaticKeys::calculate(
        our_identity_private, decrypted_sender_key,
        eph_keys.chain_key, encrypted_static, 32 + 16);

    std::vector<uint8_t> usmc_bytes(encrypted_message_len);
    size_t usmc_len = 0;
    rc = SealedSenderV1::decrypt_message(encrypted_message, encrypted_message_len,
                                          stat_keys.cipher_key, stat_keys.mac_key,
                                          usmc_bytes.data(), &usmc_len);
    if (rc != ENCHANT_VEIL_SUCCESS) return rc;

    rc = out_usmc.deserialize(usmc_bytes.data(), usmc_len);
    if (rc != ENCHANT_VEIL_SUCCESS) return rc;

    if (sodium_memcmp(decrypted_sender_key, out_usmc.sender().key().data(), 32) != 0) {
        return ENCHANT_VEIL_ERROR_CERTIFICATE_SIGNATURE_INVALID;
    }

    return ENCHANT_VEIL_SUCCESS;
}

}
}

#ifndef ENCHANT_PROTOCOL_X3DH_HPP
#define ENCHANT_PROTOCOL_X3DH_HPP

#include <cstdint>
#include <cstddef>
#include <vector>
#include <optional>
#include "enchant/error.h"
#include "secure/buffer.hpp"

namespace enchant {
namespace protocol {

constexpr size_t X3DH_SHARED_SECRET_SIZE = 32;
constexpr size_t X3DH_ROOT_KEY_SIZE = 32;
constexpr size_t X3DH_CHAIN_KEY_SIZE = 32;
constexpr size_t ML_KEM_768_PUBLIC_KEY_SIZE = 1184;
constexpr size_t ML_KEM_768_SECRET_KEY_SIZE = 2400;
constexpr size_t ML_KEM_768_CIPHERTEXT_SIZE = 1088;
constexpr size_t ML_KEM_768_SHARED_SECRET_SIZE = 32;
constexpr size_t ML_KEM_1024_PUBLIC_KEY_SIZE = 1568;
constexpr size_t ML_KEM_1024_SECRET_KEY_SIZE = 3168;
constexpr size_t ML_KEM_1024_CIPHERTEXT_SIZE = 1568;
constexpr size_t ML_KEM_1024_SHARED_SECRET_SIZE = 32;
constexpr size_t KYBER_PUBLIC_KEY_SIZE = ML_KEM_768_PUBLIC_KEY_SIZE;
constexpr size_t KYBER_SECRET_KEY_SIZE = ML_KEM_768_SECRET_KEY_SIZE;
constexpr size_t KYBER_CIPHERTEXT_SIZE = ML_KEM_768_CIPHERTEXT_SIZE;
constexpr size_t KYBER_SHARED_SECRET_SIZE = ML_KEM_768_SHARED_SECRET_SIZE;
constexpr size_t PQXDH_IKM_SIZE = 192;
constexpr size_t PQXDH_IKM_WITH_OPK_SIZE = 224;

constexpr uint8_t PQXDH_DISCONTINUITY_BYTES[32] = {
    0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
    0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
    0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
    0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF
};

// Protocol version: 4 = current with PQXDH/SPQR support, 3 = legacy X3DH-only
constexpr uint8_t ENVELOPE_PROTOCOL_VERSION = 4;
constexpr uint8_t ENVELOPE_LEGACY_VERSION = 3;

struct KeyBundle {
    secure::SecureBuffer identity_public;
    secure::SecureBuffer signed_prekey_public;
    secure::SecureBuffer signed_prekey_sig;
    secure::SecureBuffer one_time_prekey_public;
    std::optional<secure::SecureBuffer> kyber_prekey_public;
    std::optional<std::vector<uint8_t>> kyber_prekey_sig;
    uint32_t registration_id;
    uint32_t device_id;
    uint32_t signed_prekey_id;
    std::optional<uint32_t> kyber_prekey_id;
    std::optional<uint32_t> one_time_prekey_id;

    KeyBundle();
};

struct X3DHResult {
    secure::SecureBuffer shared_secret;
    secure::SecureBuffer root_key;
    secure::SecureBuffer sending_chain_key;
    secure::SecureBuffer receiving_chain_key;
    secure::SecureBuffer pqr_key;

    X3DHResult() : shared_secret(32), root_key(32), sending_chain_key(32), receiving_chain_key(32), pqr_key(32) {}
};

struct PQXDHResult {
    secure::SecureBuffer root_key;
    secure::SecureBuffer chain_key;
    secure::SecureBuffer pqr_key;
    secure::SecureBuffer kyber_ciphertext;
    secure::SecureBuffer shared_secret;

    PQXDHResult() : root_key(32), chain_key(32), pqr_key(32), kyber_ciphertext(ML_KEM_768_CIPHERTEXT_SIZE), shared_secret(32) {}
    explicit PQXDHResult(size_t ciphertext_size) : root_key(32), chain_key(32), pqr_key(32), kyber_ciphertext(ciphertext_size), shared_secret(32) {}
};

int x3dh_initiate(
    const uint8_t* our_identity_private,
    const uint8_t* our_ephemeral_private,
    const uint8_t* their_identity_public,
    const uint8_t* their_signed_prekey,
    const uint8_t* their_one_time_prekey,
    X3DHResult& result
);

int x3dh_respond(
    const uint8_t* our_identity_private,
    const uint8_t* our_signed_prekey_private,
    const uint8_t* our_one_time_prekey_private,
    const uint8_t* their_identity_public,
    const uint8_t* their_ephemeral_public,
    X3DHResult& result
);

int pqxdh_initiate(
    const uint8_t* our_identity_private,
    const uint8_t* our_ephemeral_private,
    const uint8_t* their_identity_public,
    const uint8_t* their_signed_prekey,
    const uint8_t* their_kyber_prekey, size_t their_kyber_prekey_size,
    const uint8_t* their_one_time_prekey,
    PQXDHResult& result
);

int pqxdh_respond(
    const uint8_t* our_identity_private,
    const uint8_t* our_signed_prekey_private,
    const uint8_t* our_one_time_prekey_private,
    const uint8_t* our_kyber_prekey_private,
    const uint8_t* their_identity_public,
    const uint8_t* their_ephemeral_public,
    const uint8_t* their_kyber_ciphertext,
    size_t their_kyber_ciphertext_size,
    PQXDHResult& result
);

int key_bundle_validate(const KeyBundle& bundle);

} // namespace protocol
} // namespace enchant

#endif
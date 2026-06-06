#include "protocol/x3dh.hpp"
#include "primitives/x25519.hpp"
#include "primitives/ed25519.hpp"
#include "primitives/hkdf.hpp"
#include "primitives/hash.hpp"
#include "enchant/protocol/constants.hpp"
#include "pq/ml_kem.hpp"
#include "secure/buffer.hpp"
#include <sodium.h>
#include <cstring>

namespace enchant {
namespace protocol {

static int compute_x3dh(
    const uint8_t* dh1_priv, const uint8_t* dh1_pub,
    const uint8_t* dh2_priv, const uint8_t* dh2_pub,
    const uint8_t* dh3_priv, const uint8_t* dh3_pub,
    const uint8_t* dh4_priv, const uint8_t* dh4_pub,
    bool has_dh4,
    X3DHResult& result
) {
    secure::SecureBuffer dh1_out(32), dh2_out(32), dh3_out(32);
    secure::SecureBuffer dh4_out(32);

    int rc = primitives::x25519_dh(dh1_priv, dh1_pub, dh1_out.data());
    if (rc != ENCHANT_SUCCESS) return rc;

    rc = primitives::x25519_dh(dh2_priv, dh2_pub, dh2_out.data());
    if (rc != ENCHANT_SUCCESS) return rc;

    rc = primitives::x25519_dh(dh3_priv, dh3_pub, dh3_out.data());
    if (rc != ENCHANT_SUCCESS) return rc;

    if (has_dh4) {
        rc = primitives::x25519_dh(dh4_priv, dh4_pub, dh4_out.data());
        if (rc != ENCHANT_SUCCESS) return rc;
    }

    size_t ikm_len = 32 * 3 + (has_dh4 ? 32 : 0);
    secure::SecureBuffer ikm(ikm_len);
    memcpy(ikm.data(), dh1_out.data(), 32);
    memcpy(ikm.data() + 32, dh2_out.data(), 32);
    memcpy(ikm.data() + 64, dh3_out.data(), 32);
    if (has_dh4) {
        memcpy(ikm.data() + 96, dh4_out.data(), 32);
    }

    uint8_t salt[32] = {0};
    const uint8_t info[] = "X3DH";
    rc = primitives::hkdf_derive(
        ikm.data(), ikm_len,
        salt, 32,
        info, sizeof(info) - 1,
        result.shared_secret.data(), 32
    );
    if (rc != ENCHANT_SUCCESS) return rc;

    const uint8_t info2[] = "EnvelopeRatchet";
    secure::SecureBuffer root_material(64);
    rc = primitives::hkdf_derive(
        result.shared_secret.data(), 32,
        salt, 32,
        info2, sizeof(info2) - 1,
        root_material.data(), 64
    );
    if (rc != ENCHANT_SUCCESS) return rc;

    memcpy(result.root_key.data(), root_material.data(), 32);
    memcpy(result.sending_chain_key.data(), root_material.data() + 32, 32);
    memcpy(result.receiving_chain_key.data(), root_material.data() + 32, 32);

    return ENCHANT_SUCCESS;
}

int x3dh_initiate(
    const uint8_t* our_identity_private,
    const uint8_t* our_ephemeral_private,
    const uint8_t* their_identity_public,
    const uint8_t* their_signed_prekey,
    const uint8_t* their_one_time_prekey,
    X3DHResult& result
) {
    if (!our_identity_private ||
        !their_identity_public || !their_signed_prekey) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    bool has_opk = (their_one_time_prekey != nullptr);

    secure::SecureBuffer generated_ephemeral;
    const uint8_t* ephemeral_to_use = our_ephemeral_private;
    if (ephemeral_to_use == nullptr) {
        generated_ephemeral = secure::SecureBuffer(32);
        primitives::x25519_keypair(nullptr, generated_ephemeral.data());
        ephemeral_to_use = generated_ephemeral.data();
    }

    return compute_x3dh(
        our_identity_private, their_signed_prekey,
        ephemeral_to_use, their_identity_public,
        ephemeral_to_use, their_signed_prekey,
        ephemeral_to_use, their_one_time_prekey,
        has_opk,
        result
    );
}

int x3dh_respond(
    const uint8_t* our_identity_private,
    const uint8_t* our_signed_prekey_private,
    const uint8_t* our_one_time_prekey_private,
    const uint8_t* their_identity_public,
    const uint8_t* their_ephemeral_public,
    X3DHResult& result
) {
    if (!our_identity_private || !our_signed_prekey_private ||
        !their_identity_public || !their_ephemeral_public) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    bool has_opk = our_one_time_prekey_private != nullptr;

    int rc = compute_x3dh(
        our_signed_prekey_private, their_identity_public,
        our_identity_private, their_ephemeral_public,
        our_signed_prekey_private, their_ephemeral_public,
        our_one_time_prekey_private, their_ephemeral_public,
        has_opk,
        result
    );
    return rc;
}

static int derive_pqxdh_keys(const uint8_t* ikm, size_t ikm_len,
                             secure::SecureBuffer& root_key,
                             secure::SecureBuffer& chain_key,
                             secure::SecureBuffer& pqr_key) {
    const uint8_t info[] = "EnvelopeText_X25519_SHA-256_ML-KEM-768";
    uint8_t salt[32] = {0};
    secure::SecureBuffer derived(96);

    int rc = primitives::hkdf_derive(ikm, ikm_len, salt, 32, info, sizeof(info) - 1, derived.data(), 96);
    if (rc != ENCHANT_SUCCESS) return rc;

    memcpy(root_key.data(), derived.data(), 32);
    memcpy(chain_key.data(), derived.data() + 32, 32);
    memcpy(pqr_key.data(), derived.data() + 64, 32);

    return ENCHANT_SUCCESS;
}

int pqxdh_initiate(
    const uint8_t* our_identity_private,
    const uint8_t* our_ephemeral_private,
    const uint8_t* their_identity_public,
    const uint8_t* their_signed_prekey,
    const uint8_t* their_kyber_prekey, size_t their_kyber_prekey_size,
    const uint8_t* their_one_time_prekey,
    PQXDHResult& result
) {
    if (!our_identity_private || !our_ephemeral_private ||
        !their_identity_public || !their_signed_prekey || !their_kyber_prekey) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    bool has_opk = (their_one_time_prekey != nullptr);
    size_t ikm_len = 32 + 32 * 4 + 32;
    if (!has_opk) ikm_len -= 32;

    secure::SecureBuffer ikm(ikm_len);
    size_t offset = 0;

    memcpy(ikm.data(), PQXDH_DISCONTINUITY_BYTES, 32);
    offset += 32;

    secure::SecureBuffer dh_out(32);
    int rc;

    rc = primitives::x25519_dh(our_identity_private, their_signed_prekey, dh_out.data());
    if (rc != ENCHANT_SUCCESS) return rc;
    memcpy(ikm.data() + offset, dh_out.data(), 32);
    offset += 32;

    rc = primitives::x25519_dh(our_ephemeral_private, their_identity_public, dh_out.data());
    if (rc != ENCHANT_SUCCESS) return rc;
    memcpy(ikm.data() + offset, dh_out.data(), 32);
    offset += 32;

    rc = primitives::x25519_dh(our_ephemeral_private, their_signed_prekey, dh_out.data());
    if (rc != ENCHANT_SUCCESS) return rc;
    memcpy(ikm.data() + offset, dh_out.data(), 32);
    offset += 32;

    if (has_opk) {
        rc = primitives::x25519_dh(our_ephemeral_private, their_one_time_prekey, dh_out.data());
        if (rc != ENCHANT_SUCCESS) return rc;
        memcpy(ikm.data() + offset, dh_out.data(), 32);
        offset += 32;
    }

    bool is_1024 = (their_kyber_prekey_size == pq::ML_KEM_1024_PUBLIC_KEY_SIZE);
    size_t ct_size = is_1024 ? pq::ML_KEM_1024_CIPHERTEXT_SIZE : pq::ML_KEM_768_CIPHERTEXT_SIZE;
    PQXDHResult local_result(ct_size);
    if (is_1024) {
        rc = pq::ml_kem_1024_encapsulate(local_result.kyber_ciphertext.data(), local_result.shared_secret.data(), their_kyber_prekey);
    } else {
        rc = pq::ml_kem_768_encapsulate(local_result.kyber_ciphertext.data(), local_result.shared_secret.data(), their_kyber_prekey);
    }
    if (rc != ENCHANT_SUCCESS) return rc;
    result.kyber_ciphertext = std::move(local_result.kyber_ciphertext);
    result.shared_secret = std::move(local_result.shared_secret);
    memcpy(ikm.data() + offset, result.shared_secret.data(), 32);

    rc = derive_pqxdh_keys(ikm.data(), ikm_len, result.root_key, result.chain_key, result.pqr_key);
    if (rc != ENCHANT_SUCCESS) return rc;

    return ENCHANT_SUCCESS;
}

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
) {
    if (!our_identity_private || !our_signed_prekey_private ||
        !our_kyber_prekey_private || !their_identity_public ||
        !their_ephemeral_public || !their_kyber_ciphertext) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    bool has_opk = (our_one_time_prekey_private != nullptr);
    size_t ikm_len = 32 + 32 * 4 + 32;
    if (!has_opk) ikm_len -= 32;

    secure::SecureBuffer ikm(ikm_len);
    size_t offset = 0;

    memcpy(ikm.data(), PQXDH_DISCONTINUITY_BYTES, 32);
    offset += 32;

    secure::SecureBuffer dh_out(32);
    int rc;

    rc = primitives::x25519_dh(our_signed_prekey_private, their_identity_public, dh_out.data());
    if (rc != ENCHANT_SUCCESS) return rc;
    memcpy(ikm.data() + offset, dh_out.data(), 32);
    offset += 32;

    rc = primitives::x25519_dh(our_identity_private, their_ephemeral_public, dh_out.data());
    if (rc != ENCHANT_SUCCESS) return rc;
    memcpy(ikm.data() + offset, dh_out.data(), 32);
    offset += 32;

    rc = primitives::x25519_dh(our_signed_prekey_private, their_ephemeral_public, dh_out.data());
    if (rc != ENCHANT_SUCCESS) return rc;
    memcpy(ikm.data() + offset, dh_out.data(), 32);
    offset += 32;

    if (has_opk) {
        rc = primitives::x25519_dh(our_one_time_prekey_private, their_ephemeral_public, dh_out.data());
        if (rc != ENCHANT_SUCCESS) return rc;
        memcpy(ikm.data() + offset, dh_out.data(), 32);
        offset += 32;
    }

    if (their_kyber_ciphertext_size == pq::ML_KEM_1024_CIPHERTEXT_SIZE) {
        rc = pq::ml_kem_1024_decapsulate(result.shared_secret.data(), their_kyber_ciphertext, our_kyber_prekey_private);
    } else if (their_kyber_ciphertext_size == pq::ML_KEM_768_CIPHERTEXT_SIZE) {
        rc = pq::ml_kem_768_decapsulate(result.shared_secret.data(), their_kyber_ciphertext, our_kyber_prekey_private);
    } else {
        return ENCHANT_ERROR_INVALID_FORMAT;
    }
    if (rc != ENCHANT_SUCCESS) return rc;
    memcpy(ikm.data() + offset, result.shared_secret.data(), 32);

    rc = derive_pqxdh_keys(ikm.data(), ikm_len, result.root_key, result.chain_key, result.pqr_key);
    if (rc != ENCHANT_SUCCESS) return rc;

    return ENCHANT_SUCCESS;
}

KeyBundle::KeyBundle()
    : identity_public(32), signed_prekey_public(32), signed_prekey_sig(64),
      one_time_prekey_public(32), registration_id(0), device_id(0),
      signed_prekey_id(0) {}

int key_bundle_validate(const KeyBundle& bundle) {
    if (bundle.identity_public.empty() || bundle.identity_public.size() != 32) {
        return ENCHANT_ERROR_INVALID_KEY_SIZE;
    }
    if (bundle.signed_prekey_public.empty() || bundle.signed_prekey_public.size() != 32) {
        return ENCHANT_ERROR_INVALID_KEY_SIZE;
    }
    if (bundle.signed_prekey_sig.empty() || bundle.signed_prekey_sig.size() != 64) {
        return ENCHANT_ERROR_INVALID_FORMAT;
    }
    if (!is_valid_registration_id(bundle.registration_id)) {
        return ENCHANT_ERROR_INVALID_REGISTRATION_ID;
    }
    if (bundle.signed_prekey_id == 0) {
        return ENCHANT_ERROR_INVALID_KEY_ID;
    }

    int crc = primitives::is_canonical_x25519_public_key(bundle.identity_public.data());
    if (crc != 1) return ENCHANT_ERROR_INVALID_KEY_SIZE;

    crc = primitives::is_canonical_x25519_public_key(bundle.signed_prekey_public.data());
    if (crc != 1) return ENCHANT_ERROR_INVALID_KEY_SIZE;

    if (!bundle.one_time_prekey_public.empty()) {
        if (bundle.one_time_prekey_public.size() != 32) {
            return ENCHANT_ERROR_INVALID_KEY_SIZE;
        }
        crc = primitives::is_canonical_x25519_public_key(bundle.one_time_prekey_public.data());
        if (crc != 1) return ENCHANT_ERROR_INVALID_KEY_SIZE;
    }

    if (bundle.kyber_prekey_public.has_value()) {
        if (bundle.kyber_prekey_public->size() != KYBER_PUBLIC_KEY_SIZE) {
            return ENCHANT_ERROR_INVALID_KEY_SIZE;
        }
        if (!bundle.kyber_prekey_id.has_value()) {
            return ENCHANT_ERROR_INVALID_KEY_ID;
        }
    }
    if (bundle.kyber_prekey_id.has_value() && !bundle.kyber_prekey_public.has_value()) {
        return ENCHANT_ERROR_INVALID_FORMAT;
    }

    return ENCHANT_SUCCESS;
}

} // namespace protocol
} // namespace enchant
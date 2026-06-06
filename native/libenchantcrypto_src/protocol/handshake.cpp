#include "protocol/handshake.hpp"
#include "primitives/x25519.hpp"
#include "primitives/ed25519.hpp"
#include "primitives/hkdf.hpp"
#include "primitives/random.hpp"
#include "pq/ml_kem.hpp"
#include <cstring>

namespace enchant {
namespace protocol {

HandshakeResult::HandshakeResult()
    : shared_secret(32)
    , root_key(32)
    , sending_chain_key(32)
    , receiving_chain_key(32)
    , ephemeral_public(32)
    , identity_key(32)
    , kyber_ciphertext(ML_KEM_768_CIPHERTEXT_SIZE)
    , pqr_key(32)
    , type(HandshakeType::X3DH)
    , state(HandshakeState::INITIAL) {}

void HandshakeResult::zero() {
    sodium_memzero(shared_secret.data(), shared_secret.size());
    sodium_memzero(root_key.data(), root_key.size());
    sodium_memzero(sending_chain_key.data(), sending_chain_key.size());
    sodium_memzero(receiving_chain_key.data(), receiving_chain_key.size());
    sodium_memzero(ephemeral_public.data(), ephemeral_public.size());
    sodium_memzero(identity_key.data(), identity_key.size());
    sodium_memzero(kyber_ciphertext.data(), kyber_ciphertext.size());
    sodium_memzero(pqr_key.data(), pqr_key.size());
    state = HandshakeState::INITIAL;
}

X3DHHandshake::X3DHHandshake()
    : state_(HandshakeState::INITIAL) {}

X3DHHandshake::X3DHHandshake(const secure::SecureBuffer& our_identity_private)
    : state_(HandshakeState::INITIAL) {
    if (our_identity_private.size() > 0) {
        our_identity_private_.assign(our_identity_private.data(), our_identity_private.size());
    }
}

X3DHHandshake::~X3DHHandshake() {
    our_identity_private_.zero();
    our_signed_prekey_private_.zero();
    our_ephemeral_private_.zero();
    sodium_memzero(their_identity_public_.data(), their_identity_public_.size());
    sodium_memzero(their_signed_prekey_.data(), their_signed_prekey_.size());
    sodium_memzero(their_one_time_prekey_.data(), their_one_time_prekey_.size());
}

void X3DHHandshake::set_our_signed_prekey_private(const uint8_t* priv, size_t len) {
    if (priv && len > 0) {
        our_signed_prekey_private_.assign(priv, len);
    }
}

int X3DHHandshake::initiate(
    const uint8_t* our_identity_private,
    const uint8_t* our_ephemeral_private,
    const uint8_t* their_identity_public,
    const uint8_t* their_signed_prekey,
    const uint8_t* their_one_time_prekey,
    HandshakeResult& result
) {
    if (!our_identity_private || !our_ephemeral_private ||
        !their_identity_public || !their_signed_prekey) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    our_identity_private_.assign(our_identity_private, 32);
    our_ephemeral_private_.assign(our_ephemeral_private, 32);
    their_identity_public_.assign(their_identity_public, 32);
    their_signed_prekey_.assign(their_signed_prekey, 32);
    if (their_one_time_prekey) {
        their_one_time_prekey_.assign(their_one_time_prekey, 32);
    }

    secure::SecureBuffer ephemeral_pub(32);
    int rc = primitives::x25519_pubkey_from_priv(our_ephemeral_private, ephemeral_pub.data());
    if (rc != ENCHANT_SUCCESS) return rc;

X3DHResult x3dh_result;
    rc = x3dh_initiate(
        our_identity_private, our_ephemeral_private,
        their_identity_public, their_signed_prekey,
        their_one_time_prekey,
        x3dh_result
    );
    if (rc != ENCHANT_SUCCESS) {
        state_ = HandshakeState::FAILED;
        return rc;
    }

    result.root_key = std::move(x3dh_result.root_key);
    result.sending_chain_key = std::move(x3dh_result.sending_chain_key);
    result.receiving_chain_key = std::move(x3dh_result.receiving_chain_key);
    result.shared_secret = std::move(x3dh_result.shared_secret);
    result.ephemeral_public = std::move(ephemeral_pub);
    result.type = HandshakeType::X3DH;
    result.state = HandshakeState::SESSION_ESTABLISHED;
    state_ = HandshakeState::SESSION_ESTABLISHED;

    return ENCHANT_SUCCESS;
}

int X3DHHandshake::respond(
    const uint8_t* our_identity_private,
    const uint8_t* our_signed_prekey_private,
    const uint8_t* our_one_time_prekey_private,
    const uint8_t* their_identity_public,
    const uint8_t* their_ephemeral_public,
    HandshakeResult& result
) {
    if (!our_identity_private || !our_signed_prekey_private ||
        !their_identity_public || !their_ephemeral_public) {
        state_ = HandshakeState::FAILED;
        return ENCHANT_ERROR_NULL_POINTER;
    }

    X3DHResult x3dh_result;
    int rc = x3dh_respond(
        our_identity_private, our_signed_prekey_private,
        our_one_time_prekey_private,
        their_identity_public, their_ephemeral_public,
        x3dh_result
    );
    if (rc != ENCHANT_SUCCESS) {
        state_ = HandshakeState::FAILED;
        return rc;
    }

    result.root_key = std::move(x3dh_result.root_key);
    result.sending_chain_key = std::move(x3dh_result.sending_chain_key);
    result.receiving_chain_key = std::move(x3dh_result.receiving_chain_key);
    result.shared_secret = std::move(x3dh_result.shared_secret);
    result.type = HandshakeType::X3DH;
    result.state = HandshakeState::SESSION_ESTABLISHED;
    state_ = HandshakeState::SESSION_ESTABLISHED;

    return ENCHANT_SUCCESS;
}

int X3DHHandshake::process_message(
    const uint8_t* message, size_t message_len,
    HandshakeResult& result
) {
    if (!message) {
        state_ = HandshakeState::FAILED;
        return ENCHANT_ERROR_NULL_POINTER;
    }

    if (message_len < 32) {
        state_ = HandshakeState::FAILED;
        return ENCHANT_ERROR_INVALID_FORMAT;
    }

    const uint8_t* their_ephemeral = message;

    const uint8_t* spk_priv = our_signed_prekey_private_.empty()
        ? nullptr : our_signed_prekey_private_.data();

    return respond(
        our_identity_private_.data(),
        spk_priv,
        nullptr,
        their_identity_public_.data(),
        their_ephemeral,
        result
    );
}

int X3DHHandshake::get_message(
    uint8_t* output, size_t* output_len
) {
    if (!output || !output_len) return ENCHANT_ERROR_NULL_POINTER;

    if (pending_message_.empty()) {
        return ENCHANT_ERROR_INVALID_FORMAT;
    }

    if (*output_len < pending_message_.size()) {
        *output_len = pending_message_.size();
        return ENCHANT_ERROR_BUFFER_TOO_SMALL;
    }

    memcpy(output, pending_message_.data(), pending_message_.size());
    *output_len = pending_message_.size();
    pending_message_.clear();

    return ENCHANT_SUCCESS;
}

PQXDHHandshake::PQXDHHandshake()
    : state_(HandshakeState::INITIAL) {}

PQXDHHandshake::PQXDHHandshake(const secure::SecureBuffer& our_identity_private)
    : state_(HandshakeState::INITIAL) {
    if (our_identity_private.size() > 0) {
        our_identity_private_.assign(our_identity_private.data(), our_identity_private.size());
    }
}

PQXDHHandshake::~PQXDHHandshake() {
    our_identity_private_.zero();
    our_ephemeral_private_.zero();
    our_signed_prekey_private_.zero();
    sodium_memzero(their_identity_public_.data(), their_identity_public_.size());
    sodium_memzero(their_signed_prekey_.data(), their_signed_prekey_.size());
    sodium_memzero(their_kyber_prekey_.data(), their_kyber_prekey_.size());
    sodium_memzero(their_kyber_prekey_sig_.data(), their_kyber_prekey_sig_.size());
    sodium_memzero(their_one_time_prekey_.data(), their_one_time_prekey_.size());
    our_kyber_prekey_private_.zero();
    parsed_kyber_ciphertext_.zero();
}

void PQXDHHandshake::set_their_kyber_prekey(const uint8_t* prekey, size_t len) {
    if (prekey && len > 0) {
        their_kyber_prekey_.assign(prekey, len);
    }
}

void PQXDHHandshake::set_their_kyber_prekey_sig(const uint8_t* sig, size_t len) {
    if (sig && len > 0) {
        their_kyber_prekey_sig_.assign(sig, len);
    }
}

void PQXDHHandshake::set_our_kyber_prekey_private(const uint8_t* priv, size_t len) {
    if (priv && len > 0) {
        our_kyber_prekey_private_.assign(priv, len);
    }
}

void PQXDHHandshake::set_our_signed_prekey_private(const uint8_t* priv, size_t len) {
    if (priv && len > 0) {
        our_signed_prekey_private_.assign(priv, len);
    }
}

int PQXDHHandshake::initiate(
    const uint8_t* our_identity_private,
    const uint8_t* our_ephemeral_private,
    const uint8_t* their_identity_public,
    const uint8_t* their_signed_prekey,
    const uint8_t* their_one_time_prekey,
    HandshakeResult& result
) {
    if (!our_identity_private || !our_ephemeral_private ||
        !their_identity_public || !their_signed_prekey) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    our_identity_private_.assign(our_identity_private, 32);
    our_ephemeral_private_.assign(our_ephemeral_private, 32);
    their_identity_public_.assign(their_identity_public, 32);
    their_signed_prekey_.assign(their_signed_prekey, 32);
    if (their_one_time_prekey) {
        their_one_time_prekey_.assign(their_one_time_prekey, 32);
    }

    secure::SecureBuffer ephemeral_pub(32);
    int rc = primitives::x25519_pubkey_from_priv(our_ephemeral_private, ephemeral_pub.data());
    if (rc != ENCHANT_SUCCESS) {
        state_ = HandshakeState::FAILED;
        return rc;
    }

    PQXDHResult pqxdh_result;
    rc = pqxdh_initiate(
        our_identity_private, our_ephemeral_private,
        their_identity_public, their_signed_prekey,
        their_kyber_prekey_.data(), their_kyber_prekey_.size(),
        their_one_time_prekey, pqxdh_result
    );
    if (rc != ENCHANT_SUCCESS) {
        state_ = HandshakeState::FAILED;
        return rc;
    }

    size_t msg_size = 32 + pqxdh_result.kyber_ciphertext.size();
    pending_message_.resize(msg_size);
    memcpy(pending_message_.data(), ephemeral_pub.data(), 32);
    memcpy(pending_message_.data() + 32, pqxdh_result.kyber_ciphertext.data(), pqxdh_result.kyber_ciphertext.size());

    result.root_key = std::move(pqxdh_result.root_key);
    result.sending_chain_key = std::move(pqxdh_result.chain_key);
    result.shared_secret = std::move(pqxdh_result.shared_secret);
    result.kyber_ciphertext = std::move(pqxdh_result.kyber_ciphertext);
    result.pqr_key = std::move(pqxdh_result.pqr_key);

    result.ephemeral_public.resize(32);
    memcpy(result.ephemeral_public.data(), ephemeral_pub.data(), 32);

    result.type = HandshakeType::PQXDH;
    result.state = HandshakeState::SESSION_ESTABLISHED;
    state_ = HandshakeState::SESSION_ESTABLISHED;

    return ENCHANT_SUCCESS;
}

int PQXDHHandshake::respond(
    const uint8_t* our_identity_private,
    const uint8_t* our_signed_prekey_private,
    const uint8_t* our_one_time_prekey_private,
    const uint8_t* their_identity_public,
    const uint8_t* their_ephemeral_public,
    HandshakeResult& result
) {
    const uint8_t* actual_signed_prekey_priv = nullptr;
    
    if (our_signed_prekey_private != nullptr) {
        actual_signed_prekey_priv = our_signed_prekey_private;
    } else if (!our_signed_prekey_private_.empty()) {
        actual_signed_prekey_priv = our_signed_prekey_private_.data();
    }

    if (!our_identity_private || !actual_signed_prekey_priv ||
        !their_identity_public || !their_ephemeral_public) {
        state_ = HandshakeState::FAILED;
        return ENCHANT_ERROR_NULL_POINTER;
    }

    if (our_kyber_prekey_private_.empty() || parsed_kyber_ciphertext_.empty()) {
        state_ = HandshakeState::FAILED;
        return ENCHANT_ERROR_INVALID_FORMAT;
    }

    PQXDHResult pqxdh_result;
    int rc = pqxdh_respond(
        our_identity_private,
        actual_signed_prekey_priv,
        our_one_time_prekey_private,
        our_kyber_prekey_private_.data(),
        their_identity_public,
        their_ephemeral_public,
        parsed_kyber_ciphertext_.data(),
        parsed_kyber_ciphertext_.size(),
        pqxdh_result
    );
    if (rc != ENCHANT_SUCCESS) {
        state_ = HandshakeState::FAILED;
        return rc;
    }

    result.root_key = std::move(pqxdh_result.root_key);
    result.sending_chain_key = std::move(pqxdh_result.chain_key);
    result.receiving_chain_key = pqxdh_result.chain_key.clone();
    result.shared_secret = std::move(pqxdh_result.shared_secret);
    result.kyber_ciphertext = std::move(pqxdh_result.kyber_ciphertext);
    result.pqr_key = std::move(pqxdh_result.pqr_key);
    result.ephemeral_public.resize(32);
    memcpy(result.ephemeral_public.data(), their_ephemeral_public, 32);
    result.identity_key.resize(32);
    memcpy(result.identity_key.data(), their_identity_public, 32);
    result.type = HandshakeType::PQXDH;
    result.state = HandshakeState::SESSION_ESTABLISHED;
    state_ = HandshakeState::SESSION_ESTABLISHED;

    return ENCHANT_SUCCESS;
}

int PQXDHHandshake::process_message(
    const uint8_t* message, size_t message_len,
    HandshakeResult& result
) {
    if (!message) {
        state_ = HandshakeState::FAILED;
        return ENCHANT_ERROR_NULL_POINTER;
    }

    size_t expected = 32 + ML_KEM_768_CIPHERTEXT_SIZE;
    if (message_len < expected) {
        state_ = HandshakeState::FAILED;
        return ENCHANT_ERROR_INVALID_FORMAT;
    }

    const uint8_t* their_ephemeral = message;
    const uint8_t* their_kyber_ct = message + 32;

    parsed_kyber_ciphertext_.assign(their_kyber_ct, ML_KEM_768_CIPHERTEXT_SIZE);

    const uint8_t* signed_prekey_priv = our_signed_prekey_private_.empty()
        ? nullptr
        : our_signed_prekey_private_.data();

    if (!signed_prekey_priv) {
        state_ = HandshakeState::FAILED;
        return ENCHANT_ERROR_INVALID_FORMAT;
    }

    return respond(
        our_identity_private_.data(),
        signed_prekey_priv,
        nullptr,
        their_identity_public_.data(),
        their_ephemeral,
        result
    );
}

int PQXDHHandshake::get_message(
    uint8_t* output, size_t* output_len
) {
    if (!output || !output_len) return ENCHANT_ERROR_NULL_POINTER;

    if (pending_message_.empty()) {
        return ENCHANT_ERROR_INVALID_FORMAT;
    }

    if (*output_len < pending_message_.size()) {
        *output_len = pending_message_.size();
        return ENCHANT_ERROR_BUFFER_TOO_SMALL;
    }

    memcpy(output, pending_message_.data(), pending_message_.size());
    *output_len = pending_message_.size();
    pending_message_.clear();

    return ENCHANT_SUCCESS;
}

std::unique_ptr<IHandshake> create_handshake(
    HandshakeType type,
    const secure::SecureBuffer& our_identity_private
) {
    switch (type) {
        case HandshakeType::X3DH:
            return std::make_unique<X3DHHandshake>(our_identity_private);
        case HandshakeType::PQXDH:
            return std::make_unique<PQXDHHandshake>(our_identity_private);
        default:
            return nullptr;
    }
}

HandshakeType detect_handshake_type(
    const uint8_t* message, size_t message_len
) {
    if (!message || message_len < 32) return HandshakeType::X3DH;

    if (message_len >= 32 + ML_KEM_1024_CIPHERTEXT_SIZE) {
        return HandshakeType::PQXDH;
    }

    return HandshakeType::X3DH;
}

} // namespace protocol
} // namespace enchant
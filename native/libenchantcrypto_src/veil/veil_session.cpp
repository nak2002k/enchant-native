#include "veil_session.hpp"
#include "veil_v2.hpp"
#include "primitives/x25519.hpp"
#include <cstring>

namespace enchant {
namespace veil {

VeilSession::VeilSession(IVeilStore& store)
    : store_(store),
      our_identity_(32),
      their_identity_(32),
      has_our_identity_(false),
      has_their_identity_(false) {}

VeilSession::~VeilSession() {}

int VeilSession::init_alice(const uint8_t* root_key, const uint8_t* chain_key,
                             const uint8_t* our_dh_public, const uint8_t* our_dh_private,
                             const uint8_t* their_x25519_public,
                             const uint8_t* our_identity, const uint8_t* their_identity,
                             const uint8_t* pqr_key) {
    if (!root_key || !chain_key)
        return ENCHANT_ERROR_NULL_POINTER;
    return envelope_.init(root_key, chain_key, their_x25519_public,
                         nullptr,
                         our_dh_public, our_dh_private,
                         our_identity, their_identity, pqr_key);
}

int VeilSession::init_bob(const uint8_t* root_key, const uint8_t* chain_key,
                           const uint8_t* our_dh_public, const uint8_t* our_dh_private,
                           const uint8_t* their_x25519_public,
                           const uint8_t* our_identity, const uint8_t* their_identity,
                           const uint8_t* pqr_key) {
    if (!root_key || !chain_key)
        return ENCHANT_ERROR_NULL_POINTER;
    int rc = envelope_.init(root_key, chain_key, their_x25519_public,
                           nullptr,
                           our_dh_public, our_dh_private,
                           our_identity, their_identity, pqr_key);
    if (rc != ENCHANT_SUCCESS) return rc;
    if (our_identity) set_our_identity(our_identity);
    if (their_identity) set_their_identity(their_identity);
    return ENCHANT_SUCCESS;
}

int VeilSession::encrypt(const uint8_t* plaintext, size_t plaintext_len,
                          uint8_t* output, size_t* output_len) {
    if (!plaintext || !output || !output_len)
        return ENCHANT_ERROR_NULL_POINTER;

    size_t total_needed = ENVELOPE_HEADER_SIZE + plaintext_len + 16;
    if (*output_len < total_needed) {
        *output_len = total_needed;
        return ENCHANT_ERROR_BUFFER_TOO_SMALL;
    }

    uint8_t header[ENVELOPE_HEADER_SIZE];
    uint8_t* ct = output + ENVELOPE_HEADER_SIZE;
    size_t ct_len = 0;

    int rc = envelope_.encrypt(plaintext, plaintext_len, header, ct, &ct_len);
    if (rc != ENCHANT_SUCCESS) return rc;

    memcpy(output, header, ENVELOPE_HEADER_SIZE);
    *output_len = ENVELOPE_HEADER_SIZE + ct_len;
    return ENCHANT_SUCCESS;
}

int VeilSession::decrypt(const uint8_t* input, size_t input_len,
                          uint8_t* plaintext, size_t* plaintext_len) {
    if (!input || !plaintext || !plaintext_len)
        return ENCHANT_ERROR_NULL_POINTER;
    if (input_len < ENVELOPE_HEADER_SIZE + 16)
        return ENCHANT_ERROR_CIPHERTEXT_TOO_SHORT;

    const uint8_t* header = input;
    const uint8_t* ct = input + ENVELOPE_HEADER_SIZE;
    size_t ct_len = input_len - ENVELOPE_HEADER_SIZE;

    return envelope_.decrypt(header, ct, ct_len, plaintext, plaintext_len);
}

int VeilSession::seal_and_encrypt(
    const uint8_t* sender_identity_private,
    const uint8_t* sender_identity_public,
    const std::vector<std::pair<uint32_t, secure::SecureBuffer>>& recipients,
    const SenderCertificate& sender_cert,
    const uint8_t* plaintext, size_t plaintext_len,
    uint8_t* output, size_t* output_len) {
    if (!plaintext || !output || !output_len)
        return ENCHANT_ERROR_NULL_POINTER;

    size_t inner_needed = ENVELOPE_HEADER_SIZE + plaintext_len + 16;
    std::vector<uint8_t> envelope_ct(inner_needed);
    size_t envelope_len = inner_needed;

    int rc = encrypt(plaintext, plaintext_len, envelope_ct.data(), &envelope_len);
    if (rc != ENCHANT_SUCCESS) return rc;

    UnidentifiedSenderMessageContent usmc = UnidentifiedSenderMessageContent::new_message(
        CiphertextMessageType::MESSAGE,
        sender_cert,
        envelope_ct.data(), envelope_len,
        ContentHint::RESENDABLE,
        nullptr, 0);

    size_t sealed_needed = *output_len;
    rc = sealed_sender_v2_encrypt(sender_identity_private, sender_identity_public,
                                   recipients, usmc, output, &sealed_needed);
    if (rc == ENCHANT_VEIL_ERROR_BUFFER_TOO_SMALL) {
        *output_len = sealed_needed;
        return rc;
    }
    *output_len = sealed_needed;
    return rc;
}

int VeilSession::unseal_and_decrypt(
    const uint8_t* recipient_private_key,
    const uint8_t* recipient_public,
    const uint8_t* data, size_t data_len,
    uint8_t* plaintext, size_t* plaintext_len) {
    if (!recipient_private_key || !recipient_public || !data || !plaintext || !plaintext_len)
        return ENCHANT_ERROR_NULL_POINTER;

    UnidentifiedSenderMessageContent usmc;
    int rc = sealed_sender_v2_decrypt_to_usmc(recipient_private_key, recipient_public,
                                                data, data_len, usmc);
    if (rc != ENCHANT_VEIL_SUCCESS) return rc;

    const auto& contents = usmc.contents();
    if (contents.size() < ENVELOPE_HEADER_SIZE + 16)
        return ENCHANT_ERROR_CIPHERTEXT_TOO_SHORT;

    const uint8_t* envelope_data = contents.data();
    size_t envelope_len = contents.size();

    return decrypt(envelope_data, envelope_len, plaintext, plaintext_len);
}

void VeilSession::set_our_identity(const uint8_t* identity) {
    if (identity) {
        memcpy(our_identity_.data(), identity, 32);
        has_our_identity_ = true;
        envelope_.set_our_identity(identity);
    }
}

void VeilSession::set_their_identity(const uint8_t* identity) {
    if (identity) {
        memcpy(their_identity_.data(), identity, 32);
        has_their_identity_ = true;
        envelope_.set_their_identity(identity);
    }
}

int VeilSession::get_their_identity(uint8_t* identity) const {
    return envelope_.get_their_identity(identity);
}

int VeilSession::get_our_identity(uint8_t* identity) const {
    return envelope_.get_our_identity(identity);
}

std::string VeilSession::make_store_key(const std::string& session_id) const {
    return "veil_session:" + session_id;
}

int VeilSession::save(const std::string& session_id) {
    size_t ser_len = envelope_.serialized_size();
    std::vector<uint8_t> serialized(4 + ser_len);
    int rc = envelope_.serialize(serialized.data() + 4, &ser_len);
    if (rc != ENCHANT_SUCCESS) return rc;
    serialized[0] = static_cast<uint8_t>((ser_len >> 24) & 0xFF);
    serialized[1] = static_cast<uint8_t>((ser_len >> 16) & 0xFF);
    serialized[2] = static_cast<uint8_t>((ser_len >> 8) & 0xFF);
    serialized[3] = static_cast<uint8_t>(ser_len & 0xFF);
    return store_.store(make_store_key(session_id), serialized.data(), 4 + ser_len);
}

int VeilSession::load(const std::string& session_id) {
    std::string key = make_store_key(session_id);
    if (store_.exists(key) != ENCHANT_SUCCESS)
        return ENCHANT_ERROR_NO_SESSION;

    size_t allocated = envelope_.serialized_size() + 4096;
    std::vector<uint8_t> serialized(allocated);
    size_t ser_len = allocated;
    int rc = store_.load(key, serialized.data(), &ser_len);
    if (rc != ENCHANT_SUCCESS) return rc;
    if (ser_len < 4) return ENCHANT_ERROR_INVALID_FORMAT;

    size_t data_len = (static_cast<size_t>(serialized[0]) << 24) |
                      (static_cast<size_t>(serialized[1]) << 16) |
                      (static_cast<size_t>(serialized[2]) << 8) |
                      static_cast<size_t>(serialized[3]);

    if (data_len == 0 || data_len > ser_len - 4 || data_len > 1024 * 1024)
        return ENCHANT_ERROR_INVALID_FORMAT;

    return envelope_.deserialize(serialized.data() + 4, data_len);
}

} // namespace veil
} // namespace enchant

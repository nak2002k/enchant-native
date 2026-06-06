#include "enchant/protocol/session.hpp"
#include "enchant/protocol/constants.hpp"
#include "veil/envelope_state.hpp"
#include <algorithm>
#include <cstring>
#include <sodium.h>

namespace enchant {
namespace protocol {

Session::Session() = default;

Session::Session(SessionRecord record) : record_(std::move(record)) {}

Session::~Session() {
    zero();
}

Session::Session(Session&& other) noexcept : record_(std::move(other.record_)) {}

Session& Session::operator=(Session&& other) noexcept {
    if (this != &other) {
        zero();
        record_ = std::move(other.record_);
    }
    return *this;
}

bool Session::has_current_session() const {
    return record_.has_current_session();
}

SessionState* Session::current_session() {
    return record_.get_current_session_state();
}

const SessionState* Session::current_session() const {
    return record_.get_current_session_state();
}

int Session::load_state(SessionRecord record) {
    zero();
    record_ = std::move(record);
    return ENCHANT_SUCCESS;
}

SessionRecord Session::snapshot() const {
    return record_;
}

int Session::encrypt(const uint8_t* plaintext, size_t plaintext_len,
                     std::vector<uint8_t>& envelope) {
    if (!has_current_session()) {
        return ENCHANT_ERROR_NO_SESSION;
    }
    auto* state = record_.get_current_session_state();
    if (!state) {
        return ENCHANT_ERROR_INVALID_SESSION;
    }
    if (!plaintext && plaintext_len > 0) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    auto& ratchet = state->ratchet();

    uint8_t header[veil::ENVELOPE_HEADER_SIZE];
    std::vector<uint8_t> ciphertext(plaintext_len + veil::ENVELOPE_AEAD_TAG_SIZE + 64);
    size_t ciphertext_len = ciphertext.size();

    int rc = ratchet.encrypt(plaintext, plaintext_len,
                              header, ciphertext.data(), &ciphertext_len);
    if (rc != ENCHANT_SUCCESS) {
        sodium_memzero(ciphertext.data(), ciphertext.size());
        return rc;
    }

    ciphertext.resize(ciphertext_len);
    envelope.assign(header, header + veil::ENVELOPE_HEADER_SIZE);
    envelope.insert(envelope.end(), ciphertext.begin(), ciphertext.end());

    sodium_memzero(header, sizeof(header));
    return ENCHANT_SUCCESS;
}

Session::DecryptedMessage Session::try_decrypt(const std::vector<uint8_t>& envelope) {
    DecryptedMessage result;
    result.source = DecryptResult::NO_MATCHING_SESSION;
    result.archived_session_index = 0;
    result.plaintext.clear();

    if (envelope.size() < veil::ENVELOPE_HEADER_SIZE) {
        return result;
    }

    std::vector<uint8_t> plaintext;

    if (record_.has_current_session()) {
        auto* state = record_.get_current_session_state();
        if (state) {
            DecryptResult r = try_decrypt_with_state(envelope, *state, plaintext);
            if (r == DecryptResult::CURRENT_SESSION) {
                result.plaintext = std::move(plaintext);
                result.source = DecryptResult::CURRENT_SESSION;
                return result;
            }
        }
    }

    auto& archived = record_.get_previous_session_states();
    for (size_t i = 0; i < archived.size(); ++i) {
        DecryptResult r = try_decrypt_with_state(envelope, archived[i], plaintext);
        if (r == DecryptResult::ARCHIVED_SESSION) {
            record_.promote_archived_session(i);
            result.plaintext = std::move(plaintext);
            result.source = DecryptResult::ARCHIVED_SESSION;
            result.archived_session_index = i;
            return result;
        }
        plaintext.clear();
    }

    return result;
}

Session::DecryptResult Session::try_decrypt_with_state(
    const std::vector<uint8_t>& envelope,
    SessionState& state,
    std::vector<uint8_t>& out_plaintext) {
    out_plaintext.clear();

    if (envelope.size() < veil::ENVELOPE_HEADER_SIZE + veil::ENVELOPE_AEAD_TAG_SIZE) {
        return DecryptResult::NO_MATCHING_SESSION;
    }

    auto& ratchet = state.ratchet();

    const uint8_t* header = envelope.data();
    const uint8_t* ct = envelope.data() + veil::ENVELOPE_HEADER_SIZE;
    size_t ct_len = envelope.size() - veil::ENVELOPE_HEADER_SIZE;

    std::vector<uint8_t> plaintext(ct_len + 64);
    size_t plaintext_len = plaintext.size();

    int rc = ratchet.decrypt(header, ct, ct_len, plaintext.data(), &plaintext_len);
    if (rc != ENCHANT_SUCCESS) {
        sodium_memzero(plaintext.data(), plaintext.size());
        return DecryptResult::NO_MATCHING_SESSION;
    }

    plaintext.resize(plaintext_len);
    out_plaintext = std::move(plaintext);
    return DecryptResult::CURRENT_SESSION;
}

void Session::archive_current_session() {
    record_.archive_current_session();
}

bool Session::promote_archived_session(size_t index) {
    if (index >= record_.get_previous_session_states().size()) {
        return false;
    }
    record_.promote_archived_session(index);
    return true;
}

size_t Session::archived_session_count() const {
    return record_.get_previous_session_states().size();
}

void Session::zero() {
    record_.zero();
}

} // namespace protocol
} // namespace enchant

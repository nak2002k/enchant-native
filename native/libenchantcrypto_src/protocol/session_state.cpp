#include "enchant/protocol/session_state.hpp"
#include "enchant/error.h"
#include "enchant/protocol/constants.hpp"
#include "pq/ml_kem.hpp"
#include <cstring>
#include <sodium.h>

namespace enchant {
namespace protocol {

UnacknowledgedPreKeyMessage::UnacknowledgedPreKeyMessage()
    : registration_id(0), prekey_id(0), created_at_ms(0) {}

void UnacknowledgedPreKeyMessage::zero() {
    sodium_memzero(message.data(), message.size());
    message.clear();
    registration_id = 0;
    prekey_id = 0;
    Kyber_prekey_id.reset();
    created_at_ms = 0;
}

bool UnacknowledgedPreKeyMessage::is_expired(uint64_t timeout_ms) const {
    auto now = std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::steady_clock::now().time_since_epoch()
    ).count();
    return (static_cast<int64_t>(now - created_at_ms)) > static_cast<int64_t>(timeout_ms);
}

PendingKyberPreKey::PendingKyberPreKey() : id(0) {}

void PendingKyberPreKey::zero() {
    id = 0;
    private_key.zero();
}

SessionState::SessionState()
    : version_(SESSION_VERSION_CURRENT)
    , local_registration_id_(0)
    , remote_registration_id_(0)
    , local_identity_key_(SESSION_IDENTITY_KEY_SIZE)
    , remote_identity_key_(SESSION_IDENTITY_KEY_SIZE)
    , is_initialized_(false) {}

SessionState::~SessionState() {
    zero();
}

SessionState::SessionState(const SessionState& other)
    : version_(other.version_)
    , local_registration_id_(other.local_registration_id_)
    , remote_registration_id_(other.remote_registration_id_)
    , local_name_(other.local_name_)
    , remote_name_(other.remote_name_)
    , local_identity_key_(other.local_identity_key_.clone())
    , remote_identity_key_(other.remote_identity_key_.clone())
    , is_initialized_(other.is_initialized_) {
    size_t ratchet_size = other.ratchet_.serialized_size();
    std::vector<uint8_t> ratchet_data(ratchet_size);
    size_t actual = ratchet_size;
    other.ratchet_.serialize(ratchet_data.data(), &actual);
    ratchet_.deserialize(ratchet_data.data(), actual);

    if (other.unacknowledged_pre_key_message_) {
        auto unack = std::make_unique<UnacknowledgedPreKeyMessage>();
        unack->message = other.unacknowledged_pre_key_message_->message;
        unack->registration_id = other.unacknowledged_pre_key_message_->registration_id;
        unack->prekey_id = other.unacknowledged_pre_key_message_->prekey_id;
        unack->Kyber_prekey_id = other.unacknowledged_pre_key_message_->Kyber_prekey_id;
        unack->created_at_ms = other.unacknowledged_pre_key_message_->created_at_ms;
        unacknowledged_pre_key_message_ = std::move(unack);
    }
    if (other.pending_kyber_prekey_) {
        auto pending = std::make_unique<PendingKyberPreKey>();
        pending->id = other.pending_kyber_prekey_->id;
        pending->private_key = other.pending_kyber_prekey_->private_key.clone();
        pending_kyber_prekey_ = std::move(pending);
    }
}

SessionState& SessionState::operator=(const SessionState& other) {
    if (this != &other) {
        zero();
        version_ = other.version_;
        local_registration_id_ = other.local_registration_id_;
        remote_registration_id_ = other.remote_registration_id_;
        local_name_ = other.local_name_;
        remote_name_ = other.remote_name_;
        local_identity_key_ = other.local_identity_key_.clone();
        remote_identity_key_ = other.remote_identity_key_.clone();
        is_initialized_ = other.is_initialized_;

        size_t ratchet_size = other.ratchet_.serialized_size();
        std::vector<uint8_t> ratchet_data(ratchet_size);
        size_t actual = ratchet_size;
        other.ratchet_.serialize(ratchet_data.data(), &actual);
        ratchet_.deserialize(ratchet_data.data(), actual);

        if (other.unacknowledged_pre_key_message_) {
            auto unack = std::make_unique<UnacknowledgedPreKeyMessage>();
            unack->message = other.unacknowledged_pre_key_message_->message;
            unack->registration_id = other.unacknowledged_pre_key_message_->registration_id;
            unack->prekey_id = other.unacknowledged_pre_key_message_->prekey_id;
            unack->Kyber_prekey_id = other.unacknowledged_pre_key_message_->Kyber_prekey_id;
            unack->created_at_ms = other.unacknowledged_pre_key_message_->created_at_ms;
            unacknowledged_pre_key_message_ = std::move(unack);
        }
        if (other.pending_kyber_prekey_) {
            auto pending = std::make_unique<PendingKyberPreKey>();
            pending->id = other.pending_kyber_prekey_->id;
            pending->private_key = other.pending_kyber_prekey_->private_key.clone();
            pending_kyber_prekey_ = std::move(pending);
        }
    }
    return *this;
}

SessionState::SessionState(SessionState&& other) noexcept
    : version_(other.version_)
    , local_registration_id_(other.local_registration_id_)
    , remote_registration_id_(other.remote_registration_id_)
    , local_name_(std::move(other.local_name_))
    , remote_name_(std::move(other.remote_name_))
    , local_identity_key_(std::move(other.local_identity_key_))
    , remote_identity_key_(std::move(other.remote_identity_key_))
    , ratchet_(std::move(other.ratchet_))
    , unacknowledged_pre_key_message_(std::move(other.unacknowledged_pre_key_message_))
    , pending_kyber_prekey_(std::move(other.pending_kyber_prekey_))
    , is_initialized_(other.is_initialized_) {
    other.is_initialized_ = false;
}

SessionState& SessionState::operator=(SessionState&& other) noexcept {
    if (this != &other) {
        zero();
        version_ = other.version_;
        local_registration_id_ = other.local_registration_id_;
        remote_registration_id_ = other.remote_registration_id_;
        local_name_ = std::move(other.local_name_);
        remote_name_ = std::move(other.remote_name_);
        local_identity_key_ = std::move(other.local_identity_key_);
        remote_identity_key_ = std::move(other.remote_identity_key_);
        ratchet_ = std::move(other.ratchet_);
        unacknowledged_pre_key_message_ = std::move(other.unacknowledged_pre_key_message_);
        pending_kyber_prekey_ = std::move(other.pending_kyber_prekey_);
        is_initialized_ = other.is_initialized_;
        other.is_initialized_ = false;
    }
    return *this;
}

int SessionState::init(const uint8_t* root_key,
                       const uint8_t* sending_chain_key,
                       const uint8_t* receiving_chain_key,
                       const uint8_t* their_kyber_prekey,
                       const uint8_t* our_kyber_prekey_private,
                       const uint8_t* our_signed_prekey_private,
                       const uint8_t* our_identity,
                       const uint8_t* their_identity,
                       const uint8_t* pqr_key) {
    if (!root_key || !sending_chain_key || !receiving_chain_key || !our_identity) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    int rc = ratchet_.init(root_key, sending_chain_key,
                           nullptr,
                           receiving_chain_key,
                           nullptr, nullptr,
                           our_identity, their_identity, pqr_key);
    if (rc != ENCHANT_SUCCESS) return rc;

    if (our_identity) {
        local_identity_key_ = secure::SecureBuffer(our_identity, SESSION_IDENTITY_KEY_SIZE);
    }
    if (their_identity) {
        remote_identity_key_ = secure::SecureBuffer(their_identity, SESSION_IDENTITY_KEY_SIZE);
    }

    if (our_kyber_prekey_private) {
        set_pending_kyber_prekey(0, our_kyber_prekey_private, ML_KEM_768_SECRET_KEY_SIZE);
    }

    is_initialized_ = true;
    return ENCHANT_SUCCESS;
}

uint8_t SessionState::session_version() const {
    return version_;
}

int SessionState::local_identity_key(secure::SecureBuffer& out) const {
    if (!is_initialized_) {
        return ENCHANT_ERROR_SESSION_STATE_INVALID;
    }
    if (out.size() < local_identity_key_.size()) {
        return ENCHANT_ERROR_BUFFER_TOO_SMALL;
    }
    out = local_identity_key_.clone();
    return ENCHANT_SUCCESS;
}

int SessionState::remote_identity_key(secure::SecureBuffer& out) const {
    if (!is_initialized_) {
        return ENCHANT_ERROR_SESSION_STATE_INVALID;
    }
    if (out.size() < remote_identity_key_.size()) {
        return ENCHANT_ERROR_BUFFER_TOO_SMALL;
    }
    out = remote_identity_key_.clone();
    return ENCHANT_SUCCESS;
}

bool SessionState::has_unacknowledged_pre_key_message() const {
    return unacknowledged_pre_key_message_ != nullptr;
}

const UnacknowledgedPreKeyMessage* SessionState::get_unacknowledged_pre_key_message() const {
    return unacknowledged_pre_key_message_.get();
}

int SessionState::set_unacknowledged_pre_key_message(const uint8_t* message,
                                                     size_t message_len,
                                                     uint32_t registration_id,
                                                     uint32_t prekey_id,
                                                     std::optional<uint32_t> kyber_prekey_id) {
    if (!message || message_len == 0) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    auto unack = std::make_unique<UnacknowledgedPreKeyMessage>();
    unack->message.assign(message, message + message_len);
    unack->registration_id = registration_id;
    unack->prekey_id = prekey_id;
    unack->Kyber_prekey_id = kyber_prekey_id;
    unack->created_at_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::steady_clock::now().time_since_epoch()
    ).count();

    unacknowledged_pre_key_message_ = std::move(unack);
    return ENCHANT_SUCCESS;
}

int SessionState::clear_unacknowledged_pre_key_message() {
    unacknowledged_pre_key_message_.reset();
    return ENCHANT_SUCCESS;
}

bool SessionState::has_pending_kyber_prekey() const {
    return pending_kyber_prekey_ != nullptr;
}

const PendingKyberPreKey* SessionState::get_pending_kyber_prekey() const {
    return pending_kyber_prekey_.get();
}

int SessionState::set_pending_kyber_prekey(uint32_t id, const uint8_t* private_key, size_t key_len) {
    if (!private_key || key_len == 0) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    auto pending = std::make_unique<PendingKyberPreKey>();
    pending->id = id;
    pending->private_key = secure::SecureBuffer(private_key, key_len);

    pending_kyber_prekey_ = std::move(pending);
    return ENCHANT_SUCCESS;
}

int SessionState::clear_pending_kyber_prekey() {
    if (pending_kyber_prekey_) {
        pending_kyber_prekey_->zero();
    }
    pending_kyber_prekey_.reset();
    return ENCHANT_SUCCESS;
}

int SessionState::set_registration_ids(uint32_t local_id, uint32_t remote_id) {
    if (!is_valid_registration_id(local_id) || !is_valid_registration_id(remote_id)) {
        return ENCHANT_ERROR_INVALID_REGISTRATION_ID;
    }
    local_registration_id_ = local_id;
    remote_registration_id_ = remote_id;
    return ENCHANT_SUCCESS;
}

void SessionState::get_registration_ids(uint32_t& local_id, uint32_t& remote_id) const {
    local_id = local_registration_id_;
    remote_id = remote_registration_id_;
}

bool SessionState::is_same_account(const std::string& local_name,
                                   const secure::SecureBuffer& local_id,
                                   const std::string& remote_name) const {
    if (!is_initialized_) return false;
    if (local_name.empty() || remote_name.empty()) return false;
    if (local_name != remote_name) return false;
    if (remote_identity_key_.empty()) return false;
    if (local_id.size() != remote_identity_key_.size()) return false;
    return sodium_memcmp(local_id.data(), remote_identity_key_.data(), local_id.size()) == 0;
}

veil::EnvelopeState& SessionState::ratchet() {
    return ratchet_;
}

const veil::EnvelopeState& SessionState::ratchet() const {
    return ratchet_;
}

size_t SessionState::serialized_size() const {
    size_t size = 0;
    size += 1;
    size += 4 + 4;
    size += 4 + local_name_.size();
    size += 4 + remote_name_.size();
    size += 4 + local_identity_key_.size();
    size += 4 + remote_identity_key_.size();
    size += 4 + ratchet_.serialized_size();

    size += 1;
    if (unacknowledged_pre_key_message_) {
        size += 4 + unacknowledged_pre_key_message_->message.size();
        size += 4 + 4;
        size += 1;
        if (unacknowledged_pre_key_message_->Kyber_prekey_id) {
            size += 4;
        }
        size += 8;
    }

    size += 1;
    if (pending_kyber_prekey_) {
        size += 4;
        size += 4 + pending_kyber_prekey_->private_key.size();
    }

    return size;
}

int SessionState::serialize(uint8_t* output, size_t* output_len) const {
    if (!output || !output_len) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    size_t needed = serialized_size();
    if (*output_len < needed) {
        *output_len = needed;
        return ENCHANT_ERROR_BUFFER_TOO_SMALL;
    }

    uint8_t* pos = output;

    *pos++ = version_;

    *pos++ = (local_registration_id_ >> 24) & 0xFF;
    *pos++ = (local_registration_id_ >> 16) & 0xFF;
    *pos++ = (local_registration_id_ >> 8) & 0xFF;
    *pos++ = local_registration_id_ & 0xFF;

    *pos++ = (remote_registration_id_ >> 24) & 0xFF;
    *pos++ = (remote_registration_id_ >> 16) & 0xFF;
    *pos++ = (remote_registration_id_ >> 8) & 0xFF;
    *pos++ = remote_registration_id_ & 0xFF;

    *pos++ = (local_name_.size() >> 24) & 0xFF;
    *pos++ = (local_name_.size() >> 16) & 0xFF;
    *pos++ = (local_name_.size() >> 8) & 0xFF;
    *pos++ = local_name_.size() & 0xFF;
    memcpy(pos, local_name_.data(), local_name_.size());
    pos += local_name_.size();

    *pos++ = (remote_name_.size() >> 24) & 0xFF;
    *pos++ = (remote_name_.size() >> 16) & 0xFF;
    *pos++ = (remote_name_.size() >> 8) & 0xFF;
    *pos++ = remote_name_.size() & 0xFF;
    memcpy(pos, remote_name_.data(), remote_name_.size());
    pos += remote_name_.size();

    *pos++ = (local_identity_key_.size() >> 24) & 0xFF;
    *pos++ = (local_identity_key_.size() >> 16) & 0xFF;
    *pos++ = (local_identity_key_.size() >> 8) & 0xFF;
    *pos++ = local_identity_key_.size() & 0xFF;
    memcpy(pos, local_identity_key_.data(), local_identity_key_.size());
    pos += local_identity_key_.size();

    *pos++ = (remote_identity_key_.size() >> 24) & 0xFF;
    *pos++ = (remote_identity_key_.size() >> 16) & 0xFF;
    *pos++ = (remote_identity_key_.size() >> 8) & 0xFF;
    *pos++ = remote_identity_key_.size() & 0xFF;
    memcpy(pos, remote_identity_key_.data(), remote_identity_key_.size());
    pos += remote_identity_key_.size();

    size_t ratchet_size = ratchet_.serialized_size();
    *pos++ = (ratchet_size >> 24) & 0xFF;
    *pos++ = (ratchet_size >> 16) & 0xFF;
    *pos++ = (ratchet_size >> 8) & 0xFF;
    *pos++ = ratchet_size & 0xFF;
    int rc = ratchet_.serialize(pos, &ratchet_size);
    if (rc != ENCHANT_SUCCESS) return rc;
    pos += ratchet_size;

    if (unacknowledged_pre_key_message_) {
        *pos++ = 1;
        size_t msg_size = unacknowledged_pre_key_message_->message.size();
        *pos++ = (msg_size >> 24) & 0xFF;
        *pos++ = (msg_size >> 16) & 0xFF;
        *pos++ = (msg_size >> 8) & 0xFF;
        *pos++ = msg_size & 0xFF;
        memcpy(pos, unacknowledged_pre_key_message_->message.data(), msg_size);
        pos += msg_size;

        *pos++ = (unacknowledged_pre_key_message_->registration_id >> 24) & 0xFF;
        *pos++ = (unacknowledged_pre_key_message_->registration_id >> 16) & 0xFF;
        *pos++ = (unacknowledged_pre_key_message_->registration_id >> 8) & 0xFF;
        *pos++ = unacknowledged_pre_key_message_->registration_id & 0xFF;

        *pos++ = (unacknowledged_pre_key_message_->prekey_id >> 24) & 0xFF;
        *pos++ = (unacknowledged_pre_key_message_->prekey_id >> 16) & 0xFF;
        *pos++ = (unacknowledged_pre_key_message_->prekey_id >> 8) & 0xFF;
        *pos++ = unacknowledged_pre_key_message_->prekey_id & 0xFF;

        *pos++ = unacknowledged_pre_key_message_->Kyber_prekey_id ? 1 : 0;
        if (unacknowledged_pre_key_message_->Kyber_prekey_id) {
            *pos++ = (*unacknowledged_pre_key_message_->Kyber_prekey_id >> 24) & 0xFF;
            *pos++ = (*unacknowledged_pre_key_message_->Kyber_prekey_id >> 16) & 0xFF;
            *pos++ = (*unacknowledged_pre_key_message_->Kyber_prekey_id >> 8) & 0xFF;
            *pos++ = *unacknowledged_pre_key_message_->Kyber_prekey_id & 0xFF;
        }

        uint64_t created = unacknowledged_pre_key_message_->created_at_ms;
        *pos++ = (created >> 56) & 0xFF;
        *pos++ = (created >> 48) & 0xFF;
        *pos++ = (created >> 40) & 0xFF;
        *pos++ = (created >> 32) & 0xFF;
        *pos++ = (created >> 24) & 0xFF;
        *pos++ = (created >> 16) & 0xFF;
        *pos++ = (created >> 8) & 0xFF;
        *pos++ = created & 0xFF;
    } else {
        *pos++ = 0;
    }

    if (pending_kyber_prekey_) {
        *pos++ = 1;
        *pos++ = (pending_kyber_prekey_->id >> 24) & 0xFF;
        *pos++ = (pending_kyber_prekey_->id >> 16) & 0xFF;
        *pos++ = (pending_kyber_prekey_->id >> 8) & 0xFF;
        *pos++ = pending_kyber_prekey_->id & 0xFF;

        size_t key_size = pending_kyber_prekey_->private_key.size();
        *pos++ = (key_size >> 24) & 0xFF;
        *pos++ = (key_size >> 16) & 0xFF;
        *pos++ = (key_size >> 8) & 0xFF;
        *pos++ = key_size & 0xFF;
        memcpy(pos, pending_kyber_prekey_->private_key.data(), key_size);
        pos += key_size;
    } else {
        *pos++ = 0;
    }

    *output_len = pos - output;
    return ENCHANT_SUCCESS;
}

int SessionState::deserialize(const uint8_t* input, size_t input_len) {
    if (!input || input_len == 0) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    zero();

    const uint8_t* pos = input;
    size_t remaining = input_len;

    if (remaining < 1) return ENCHANT_ERROR_INVALID_FORMAT;
    version_ = *pos++;
    remaining--;

    if (remaining < 8) return ENCHANT_ERROR_INVALID_FORMAT;
    local_registration_id_ = (static_cast<uint32_t>(pos[0]) << 24) |
                            (static_cast<uint32_t>(pos[1]) << 16) |
                            (static_cast<uint32_t>(pos[2]) << 8) |
                            static_cast<uint32_t>(pos[3]);
    pos += 4;
    remaining -= 4;

    remote_registration_id_ = (static_cast<uint32_t>(pos[0]) << 24) |
                             (static_cast<uint32_t>(pos[1]) << 16) |
                             (static_cast<uint32_t>(pos[2]) << 8) |
                             static_cast<uint32_t>(pos[3]);
    pos += 4;
    remaining -= 4;

    if (remaining < 4) return ENCHANT_ERROR_INVALID_FORMAT;
    size_t local_name_len = (static_cast<size_t>(pos[0]) << 24) |
                           (static_cast<size_t>(pos[1]) << 16) |
                           (static_cast<size_t>(pos[2]) << 8) |
                           static_cast<size_t>(pos[3]);
    pos += 4;
    remaining -= 4;

    if (remaining < local_name_len) return ENCHANT_ERROR_INVALID_FORMAT;
    local_name_.assign(reinterpret_cast<const char*>(pos), local_name_len);
    pos += local_name_len;
    remaining -= local_name_len;

    if (remaining < 4) return ENCHANT_ERROR_INVALID_FORMAT;
    size_t remote_name_len = (static_cast<size_t>(pos[0]) << 24) |
                            (static_cast<size_t>(pos[1]) << 16) |
                            (static_cast<size_t>(pos[2]) << 8) |
                            static_cast<size_t>(pos[3]);
    pos += 4;
    remaining -= 4;

    if (remaining < remote_name_len) return ENCHANT_ERROR_INVALID_FORMAT;
    remote_name_.assign(reinterpret_cast<const char*>(pos), remote_name_len);
    pos += remote_name_len;
    remaining -= remote_name_len;

    if (remaining < 4) return ENCHANT_ERROR_INVALID_FORMAT;
    size_t local_id_len = (static_cast<size_t>(pos[0]) << 24) |
                         (static_cast<size_t>(pos[1]) << 16) |
                         (static_cast<size_t>(pos[2]) << 8) |
                         static_cast<size_t>(pos[3]);
    pos += 4;
    remaining -= 4;

    if (remaining < local_id_len) return ENCHANT_ERROR_INVALID_FORMAT;
    local_identity_key_ = secure::SecureBuffer(pos, local_id_len);
    pos += local_id_len;
    remaining -= local_id_len;

    if (remaining < 4) return ENCHANT_ERROR_INVALID_FORMAT;
    size_t remote_id_len = (static_cast<size_t>(pos[0]) << 24) |
                          (static_cast<size_t>(pos[1]) << 16) |
                          (static_cast<size_t>(pos[2]) << 8) |
                          static_cast<size_t>(pos[3]);
    pos += 4;
    remaining -= 4;

    if (remaining < remote_id_len) return ENCHANT_ERROR_INVALID_FORMAT;
    remote_identity_key_ = secure::SecureBuffer(pos, remote_id_len);
    pos += remote_id_len;
    remaining -= remote_id_len;

    if (remaining < 4) return ENCHANT_ERROR_INVALID_FORMAT;
    size_t ratchet_size = (static_cast<size_t>(pos[0]) << 24) |
                         (static_cast<size_t>(pos[1]) << 16) |
                         (static_cast<size_t>(pos[2]) << 8) |
                         static_cast<size_t>(pos[3]);
    pos += 4;
    remaining -= 4;

    if (ratchet_size == 0 || remaining < ratchet_size) return ENCHANT_ERROR_INVALID_FORMAT;
    int rc = ratchet_.deserialize(pos, ratchet_size);
    if (rc != ENCHANT_SUCCESS) return rc;
    pos += ratchet_size;
    remaining -= ratchet_size;

    if (remaining < 1) return ENCHANT_ERROR_INVALID_FORMAT;
    bool has_unack = (*pos++ != 0);
    remaining--;

    if (has_unack) {
        auto unack = std::make_unique<UnacknowledgedPreKeyMessage>();

        if (remaining < 4) return ENCHANT_ERROR_INVALID_FORMAT;
        size_t msg_size = (static_cast<size_t>(pos[0]) << 24) |
                         (static_cast<size_t>(pos[1]) << 16) |
                         (static_cast<size_t>(pos[2]) << 8) |
                         static_cast<size_t>(pos[3]);
        pos += 4;
        remaining -= 4;

        if (remaining < msg_size) return ENCHANT_ERROR_INVALID_FORMAT;
        unack->message.assign(pos, pos + msg_size);
        pos += msg_size;
        remaining -= msg_size;

        if (remaining < 8) return ENCHANT_ERROR_INVALID_FORMAT;
        unack->registration_id = (static_cast<uint32_t>(pos[0]) << 24) |
                                 (static_cast<uint32_t>(pos[1]) << 16) |
                                 (static_cast<uint32_t>(pos[2]) << 8) |
                                 static_cast<uint32_t>(pos[3]);
        pos += 4;

        unack->prekey_id = (static_cast<uint32_t>(pos[0]) << 24) |
                          (static_cast<uint32_t>(pos[1]) << 16) |
                          (static_cast<uint32_t>(pos[2]) << 8) |
                          static_cast<uint32_t>(pos[3]);
        pos += 4;
        remaining -= 8;

        if (remaining < 1) return ENCHANT_ERROR_INVALID_FORMAT;
        bool has_kyber = (*pos++ != 0);
        remaining--;

        if (has_kyber) {
            if (remaining < 4) return ENCHANT_ERROR_INVALID_FORMAT;
            uint32_t kyber_id = (static_cast<uint32_t>(pos[0]) << 24) |
                                (static_cast<uint32_t>(pos[1]) << 16) |
                                (static_cast<uint32_t>(pos[2]) << 8) |
                                static_cast<uint32_t>(pos[3]);
            pos += 4;
            remaining -= 4;
            unack->Kyber_prekey_id = kyber_id;
        }

        if (remaining < 8) return ENCHANT_ERROR_INVALID_FORMAT;
        unack->created_at_ms = (static_cast<uint64_t>(pos[0]) << 56) |
                               (static_cast<uint64_t>(pos[1]) << 48) |
                               (static_cast<uint64_t>(pos[2]) << 40) |
                               (static_cast<uint64_t>(pos[3]) << 32) |
                               (static_cast<uint64_t>(pos[4]) << 24) |
                               (static_cast<uint64_t>(pos[5]) << 16) |
                               (static_cast<uint64_t>(pos[6]) << 8) |
                               static_cast<uint64_t>(pos[7]);
        pos += 8;
        remaining -= 8;

        unacknowledged_pre_key_message_ = std::move(unack);
    }

    if (remaining < 1) return ENCHANT_ERROR_INVALID_FORMAT;
    bool has_pending = (*pos++ != 0);
    remaining--;

    if (has_pending) {
        auto pending = std::make_unique<PendingKyberPreKey>();

        if (remaining < 4) return ENCHANT_ERROR_INVALID_FORMAT;
        pending->id = (static_cast<uint32_t>(pos[0]) << 24) |
                     (static_cast<uint32_t>(pos[1]) << 16) |
                     (static_cast<uint32_t>(pos[2]) << 8) |
                     static_cast<uint32_t>(pos[3]);
        pos += 4;
        remaining -= 4;

        if (remaining < 4) return ENCHANT_ERROR_INVALID_FORMAT;
        size_t key_size = (static_cast<size_t>(pos[0]) << 24) |
                         (static_cast<size_t>(pos[1]) << 16) |
                         (static_cast<size_t>(pos[2]) << 8) |
                         static_cast<size_t>(pos[3]);
        pos += 4;
        remaining -= 4;

        if (remaining < key_size) return ENCHANT_ERROR_INVALID_FORMAT;
        pending->private_key = secure::SecureBuffer(pos, key_size);
        pos += key_size;
        remaining -= key_size;

        pending_kyber_prekey_ = std::move(pending);
    }

    is_initialized_ = true;
    return ENCHANT_SUCCESS;
}

void SessionState::zero() {
    version_ = SESSION_VERSION_CURRENT;
    local_registration_id_ = 0;
    remote_registration_id_ = 0;
    local_name_.clear();
    remote_name_.clear();
    local_identity_key_.zero();
    remote_identity_key_.zero();
    ratchet_.zero();
    if (unacknowledged_pre_key_message_) {
        unacknowledged_pre_key_message_->zero();
    }
    unacknowledged_pre_key_message_.reset();
    if (pending_kyber_prekey_) {
        pending_kyber_prekey_->zero();
    }
    pending_kyber_prekey_.reset();
    is_initialized_ = false;
}

} // namespace protocol
} // namespace enchant
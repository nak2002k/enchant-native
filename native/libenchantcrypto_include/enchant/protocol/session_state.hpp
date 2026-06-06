#ifndef ENCHANT_PROTOCOL_SESSION_STATE_HPP
#define ENCHANT_PROTOCOL_SESSION_STATE_HPP

#include "protocol/x3dh.hpp"
#include "veil/envelope_state.hpp"
#include <cstdint>
#include <cstddef>
#include <memory>
#include <optional>
#include <chrono>
#include <string>
#include <vector>

namespace enchant {
namespace protocol {

constexpr uint8_t SESSION_VERSION_CURRENT = 3;
constexpr uint64_t SESSION_UNACKNOWLEDGED_TIMEOUT_MS = 30000;
constexpr size_t SESSION_IDENTITY_KEY_SIZE = 32;

struct UnacknowledgedPreKeyMessage {
    std::vector<uint8_t> message;
    uint32_t registration_id;
    uint32_t prekey_id;
    std::optional<uint32_t> Kyber_prekey_id;
    uint64_t created_at_ms;

    UnacknowledgedPreKeyMessage();
    void zero();

    bool is_expired(uint64_t timeout_ms = SESSION_UNACKNOWLEDGED_TIMEOUT_MS) const;
};

struct PendingKyberPreKey {
    uint32_t id;
    secure::SecureBuffer private_key;

    PendingKyberPreKey();
    void zero();
};

class SessionState {
public:
    SessionState();
    ~SessionState();

    SessionState(const SessionState& other);
    SessionState& operator=(const SessionState& other);
    SessionState(SessionState&& other) noexcept;
    SessionState& operator=(SessionState&& other) noexcept;

    int init(const uint8_t* root_key,
             const uint8_t* sending_chain_key,
             const uint8_t* receiving_chain_key,
             const uint8_t* their_kyber_prekey,
             const uint8_t* our_kyber_prekey_private,
             const uint8_t* our_signed_prekey_private,
             const uint8_t* our_identity,
             const uint8_t* their_identity,
             const uint8_t* pqr_key);

    uint8_t session_version() const;

    int local_identity_key(secure::SecureBuffer& out) const;
    int remote_identity_key(secure::SecureBuffer& out) const;

    bool has_unacknowledged_pre_key_message() const;
    const UnacknowledgedPreKeyMessage* get_unacknowledged_pre_key_message() const;

    int set_unacknowledged_pre_key_message(const uint8_t* message,
                                            size_t message_len,
                                            uint32_t registration_id,
                                            uint32_t prekey_id,
                                            std::optional<uint32_t> kyber_prekey_id);
    int clear_unacknowledged_pre_key_message();

    bool has_pending_kyber_prekey() const;
    const PendingKyberPreKey* get_pending_kyber_prekey() const;
    int set_pending_kyber_prekey(uint32_t id, const uint8_t* private_key, size_t key_len);
    int clear_pending_kyber_prekey();

    int set_registration_ids(uint32_t local_id, uint32_t remote_id);
    void get_registration_ids(uint32_t& local_id, uint32_t& remote_id) const;

    bool is_same_account(const std::string& local_name,
                        const secure::SecureBuffer& local_id,
                        const std::string& remote_name) const;

    veil::EnvelopeState& ratchet();
    const veil::EnvelopeState& ratchet() const;

    bool is_initialized() const { return is_initialized_; }

    size_t serialized_size() const;
    int serialize(uint8_t* output, size_t* output_len) const;
    int deserialize(const uint8_t* input, size_t input_len);

    void zero();

private:
    int serialize_unacknowledged(std::vector<uint8_t>& out) const;
    int deserialize_unacknowledged(const uint8_t*& input, size_t& remaining);

    uint8_t version_;
    uint32_t local_registration_id_;
    uint32_t remote_registration_id_;
    std::string local_name_;
    std::string remote_name_;
    secure::SecureBuffer local_identity_key_;
    secure::SecureBuffer remote_identity_key_;
    veil::EnvelopeState ratchet_;
    std::unique_ptr<UnacknowledgedPreKeyMessage> unacknowledged_pre_key_message_;
    std::unique_ptr<PendingKyberPreKey> pending_kyber_prekey_;
    bool is_initialized_;
};

} // namespace protocol
} // namespace enchant

#endif
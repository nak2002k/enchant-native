#ifndef ENCHANT_VEIL_ENVELOPE_STATE_HPP
#define ENCHANT_VEIL_ENVELOPE_STATE_HPP

#include "chain.hpp"
#include <cstdint>
#include <string>
#include <unordered_map>
#include <vector>

namespace enchant {
namespace veil {

enum class SessionUsability : uint8_t {
    NONE                     = 0,
    CAN_ENCRYPT              = 1 << 0,
    CAN_DECRYPT              = 1 << 1,
    IDENTITY_VERIFIED        = 1 << 2,
    FULL                     = CAN_ENCRYPT | CAN_DECRYPT | IDENTITY_VERIFIED,
    ENCRYPT_ONLY             = CAN_ENCRYPT,
    DECRYPT_ONLY             = CAN_DECRYPT,
};

inline SessionUsability operator|(SessionUsability a, SessionUsability b) {
    return static_cast<SessionUsability>(static_cast<uint8_t>(a) | static_cast<uint8_t>(b));
}

constexpr size_t ENVELOPE_MAX_RECEIVER_CHAINS = 5;
constexpr size_t ENVELOPE_MAX_MESSAGE_KEYS = 2000;
constexpr size_t ENVELOPE_MAX_FORWARD_JUMPS = 100;
constexpr size_t ENVELOPE_DH_KEY_SIZE = 32;
constexpr size_t ENVELOPE_KEY_SIZE = 32;
constexpr size_t ENVELOPE_NONCE_SIZE = 12;
constexpr size_t ENVELOPE_AEAD_TAG_SIZE = 16;
constexpr size_t ENVELOPE_HKDF_SALT_SIZE = 32;
constexpr size_t ENVELOPE_DERIVED_SIZE = 44;
constexpr size_t ENVELOPE_FULL_NONCE_SIZE = 24;

constexpr size_t ENVELOPE_HEADER_SIZE = 41;
constexpr uint8_t ENVELOPE_CURRENT_VERSION = 1;

struct ConsumedEntry {
    secure::SecureBuffer dh_public;
    uint32_t message_number;

    ConsumedEntry();
    ConsumedEntry(secure::SecureBuffer dh, uint32_t num);
};

class EnvelopeState {
public:
    EnvelopeState();
    ~EnvelopeState();

    EnvelopeState(const EnvelopeState&) = delete;
    EnvelopeState& operator=(const EnvelopeState&) = delete;
    EnvelopeState(EnvelopeState&&) noexcept;
    EnvelopeState& operator=(EnvelopeState&&) noexcept;

    int init(const uint8_t* root_key, const uint8_t* sending_chain_key,
             const uint8_t* their_x25519_public = nullptr,
             const uint8_t* receiving_chain_key = nullptr,
             const uint8_t* our_dh_public = nullptr, const uint8_t* our_dh_private = nullptr,
             const uint8_t* our_identity = nullptr, const uint8_t* their_identity = nullptr,
             const uint8_t* pqr_key = nullptr);

    int encrypt(const uint8_t* plaintext, size_t plaintext_len,
                uint8_t* header, uint8_t* ciphertext, size_t* ciphertext_len);

    int decrypt(const uint8_t* header, const uint8_t* ciphertext, size_t ciphertext_len,
                uint8_t* plaintext, size_t* plaintext_len);

    void zero();

    void set_our_identity(const uint8_t* identity);
    void set_their_identity(const uint8_t* identity);
    void set_pqr_key(const uint8_t* pqr_key);

    int get_their_identity(uint8_t* identity) const;
    int get_our_identity(uint8_t* identity) const;
    bool has_their_identity() const;
    bool has_our_identity() const;

    SessionUsability usability() const;

    size_t serialized_size() const;
    int serialize(uint8_t* output, size_t* output_len) const;
    int deserialize(const uint8_t* data, size_t data_len);

    int process_decryption_error(const uint8_t* their_dh_public,
                                  const uint8_t* our_dh_private,
                                  const uint8_t* our_x25519_public);

    int get_failed_message_info(const uint8_t* header, uint32_t& failed_ns,
                                uint8_t* failed_x25519_key) const;

    int delete_skipped_key(const uint8_t* their_ephemeral, uint32_t counter);

    int clear_all_skipped_keys();

    size_t skipped_key_count() const;

    int evict_oldest_skipped_keys(size_t keep_count);

private:
    RootKey root_key_;
    SenderChain sender_chain_;
    uint32_t sending_message_number_;
    std::vector<ReceiverChain> receiver_chains_;
    uint32_t previous_counter_;
    std::vector<SkippedKey> pending_skipped_keys_;
    std::unordered_map<std::string, ConsumedEntry> consumed_keys_;
    secure::SecureBuffer our_identity_;
    secure::SecureBuffer their_identity_;
    bool has_our_identity_;
    bool has_their_identity_;
    secure::SecureBuffer pqr_key_;
    bool has_pqr_key_;
    bool is_initialized_;

    int envelope_rotate(const uint8_t* their_new_public_key);

    ReceiverChain* ensure_receiver_chain(const uint8_t* their_ephemeral);

    int consume_message_key(const uint8_t* their_ephemeral, uint32_t counter,
                            const uint8_t* pqr_key_data,
                            MessageKeys& out_keys);

    int store_skipped_key(const uint8_t* their_ephemeral, const uint8_t* seed, uint32_t counter);

    int take_skipped_key(const uint8_t* their_ephemeral, uint32_t counter,
                         MessageKeys& out, const uint8_t* pqr_key_data);

    int find_receiver_chain_index(const uint8_t* their_ephemeral) const;

    void set_receiver_chain_key(const uint8_t* their_ephemeral, const ChainKey& chain_key);

    std::string make_key_id(const uint8_t* dh_public_key, uint32_t msg_num) const;
};

} // namespace veil
} // namespace enchant

#endif

#ifndef ENCHANT_PROTOCOL_MULTI_RECIPIENT_HPP
#define ENCHANT_PROTOCOL_MULTI_RECIPIENT_HPP

#include <vector>
#include <cstdint>
#include <string>
#include "enchant/error.h"
#include "enchant/i_identity_store.hpp"
#include "enchant/protocol/session_record.hpp"

namespace enchant {
namespace protocol {

int multi_recipient_encrypt(
    const std::vector<EnchantAddress>& addresses,
    std::vector<SessionRecord>& sessions,
    const std::vector<uint8_t>& plaintext,
    std::vector<uint8_t>& output
);

int multi_recipient_decrypt(
    const EnchantAddress& address,
    SessionRecord& session,
    const std::vector<uint8_t>& ciphertext,
    uint32_t sender_device_id,
    std::vector<uint8_t>& plaintext
);

class MultiRecipientEncoder {
public:
    MultiRecipientEncoder();
    ~MultiRecipientEncoder();

    int initialize(const std::vector<EnchantAddress>& addresses,
                   const std::vector<SessionRecord>& sessions);

    int encrypt_message(const std::vector<uint8_t>& plaintext,
                        std::vector<uint8_t>& output);

    int finalize(std::vector<uint8_t>& output);

    size_t get_recipient_count() const { return recipients_.size(); }

private:
    struct RecipientContext {
        EnchantAddress address;
        SessionRecord session;
        std::vector<uint8_t> ciphertext;
    };

    std::vector<RecipientContext> recipients_;
    bool initialized_;
};

class MultiRecipientDecoder {
public:
    MultiRecipientDecoder();
    ~MultiRecipientDecoder();

    int initialize(const EnchantAddress& address,
                   SessionRecord& session,
                   uint32_t sender_device_id);

    int decrypt_message(const std::vector<uint8_t>& ciphertext,
                        std::vector<uint8_t>& plaintext);

    uint32_t get_sender_device_id() const { return sender_device_id_; }

private:
    EnchantAddress address_;
    SessionRecord session_;
    uint32_t sender_device_id_;
    bool initialized_;
};

} // namespace protocol
} // namespace enchant

#endif
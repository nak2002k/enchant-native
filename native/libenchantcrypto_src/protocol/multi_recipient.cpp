#include "enchant/protocol/multi_recipient.hpp"
#include "enchant/protocol/session_record.hpp"
#include "enchant/protocol/session_state.hpp"
#include <cstring>
#include <sodium.h>

namespace enchant {
namespace protocol {

namespace {

constexpr size_t DEVICE_ID_SIZE = 4;
constexpr size_t LENGTH_SIZE = 4;

struct RecipientMessage {
    uint32_t device_id;
    std::vector<uint8_t> ciphertext;
};

int encrypt_for_recipient(SessionRecord& session,
                          const std::vector<uint8_t>& plaintext,
                          RecipientMessage& out) {
    if (!session.has_current_session()) {
        return ENCHANT_ERROR_NO_SESSION;
    }

    auto* state = session.get_current_session_state();
    if (!state) {
        return ENCHANT_ERROR_INVALID_SESSION;
    }

    auto& ratchet = state->ratchet();

    size_t header_len = veil::ENVELOPE_HEADER_SIZE;
    std::vector<uint8_t> header(header_len);
    std::vector<uint8_t> ciphertext(plaintext.size() + 64);
    size_t ciphertext_len = ciphertext.size();

    int rc = ratchet.encrypt(plaintext.data(), plaintext.size(),
                             header.data(), ciphertext.data(), &ciphertext_len);
    if (rc != ENCHANT_SUCCESS) {
        sodium_memzero(ciphertext.data(), ciphertext.size());
        return rc;
    }

    ciphertext.resize(ciphertext_len);
    out.ciphertext = std::move(ciphertext);

    return ENCHANT_SUCCESS;
}

int decrypt_from_recipient(SessionRecord& session,
                          const std::vector<uint8_t>& ciphertext,
                          uint32_t sender_device_id,
                          std::vector<uint8_t>& plaintext) {
    (void)sender_device_id;

    if (!session.has_current_session()) {
        return ENCHANT_ERROR_NO_SESSION;
    }

    auto* state = session.get_current_session_state();
    if (!state) {
        return ENCHANT_ERROR_INVALID_SESSION;
    }

    auto& ratchet = state->ratchet();

    if (ciphertext.size() < veil::ENVELOPE_HEADER_SIZE + 16) {
        return ENCHANT_ERROR_CIPHERTEXT_TOO_SHORT;
    }

    const uint8_t* header = ciphertext.data();
    const uint8_t* ct = ciphertext.data() + veil::ENVELOPE_HEADER_SIZE;
    size_t ct_len = ciphertext.size() - veil::ENVELOPE_HEADER_SIZE;

    plaintext.resize(ct_len + 64);
    size_t plaintext_len = plaintext.size();

    int rc = ratchet.decrypt(header, ct, ct_len, plaintext.data(), &plaintext_len);
    if (rc != ENCHANT_SUCCESS) {
        sodium_memzero(plaintext.data(), plaintext.size());
        plaintext.clear();
        return rc;
    }

    plaintext.resize(plaintext_len);
    return ENCHANT_SUCCESS;
}

}

int multi_recipient_encrypt(
    const std::vector<EnchantAddress>& addresses,
    std::vector<SessionRecord>& sessions,
    const std::vector<uint8_t>& plaintext,
    std::vector<uint8_t>& output) {

    if (addresses.empty() || sessions.empty()) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    if (addresses.size() != sessions.size()) {
        return ENCHANT_ERROR_INVALID_FORMAT;
    }

    output.clear();
    output.reserve(plaintext.size() * addresses.size() + addresses.size() * 1024);

    for (size_t i = 0; i < addresses.size(); ++i) {
        RecipientMessage msg;
        msg.device_id = addresses[i].device_id;

        int rc = encrypt_for_recipient(sessions[i], plaintext, msg);
        if (rc != ENCHANT_SUCCESS) {
            output.clear();
            return rc;
        }

        uint8_t device_id_buf[DEVICE_ID_SIZE];
        device_id_buf[0] = (msg.device_id >> 24) & 0xFF;
        device_id_buf[1] = (msg.device_id >> 16) & 0xFF;
        device_id_buf[2] = (msg.device_id >> 8) & 0xFF;
        device_id_buf[3] = msg.device_id & 0xFF;

        uint8_t len_buf[LENGTH_SIZE];
        size_t ct_len = msg.ciphertext.size();
        len_buf[0] = (ct_len >> 24) & 0xFF;
        len_buf[1] = (ct_len >> 16) & 0xFF;
        len_buf[2] = (ct_len >> 8) & 0xFF;
        len_buf[3] = ct_len & 0xFF;

        output.insert(output.end(), device_id_buf, device_id_buf + DEVICE_ID_SIZE);
        output.insert(output.end(), len_buf, len_buf + LENGTH_SIZE);
        output.insert(output.end(), msg.ciphertext.begin(), msg.ciphertext.end());
    }

    return ENCHANT_SUCCESS;
}

int multi_recipient_decrypt(
    const EnchantAddress& address,
    SessionRecord& session,
    const std::vector<uint8_t>& ciphertext,
    uint32_t sender_device_id,
    std::vector<uint8_t>& plaintext) {

    (void)address;

    if (ciphertext.empty()) {
        return ENCHANT_ERROR_CIPHERTEXT_TOO_SHORT;
    }

    size_t offset = 0;
    bool found = false;
    std::vector<uint8_t> recipient_ct;

    while (offset + DEVICE_ID_SIZE + LENGTH_SIZE <= ciphertext.size()) {
        uint32_t device_id = (static_cast<uint32_t>(ciphertext[offset]) << 24) |
                           (static_cast<uint32_t>(ciphertext[offset + 1]) << 16) |
                           (static_cast<uint32_t>(ciphertext[offset + 2]) << 8) |
                           static_cast<uint32_t>(ciphertext[offset + 3]);

        offset += DEVICE_ID_SIZE;

        uint32_t ct_len = (static_cast<uint32_t>(ciphertext[offset]) << 24) |
                         (static_cast<uint32_t>(ciphertext[offset + 1]) << 16) |
                         (static_cast<uint32_t>(ciphertext[offset + 2]) << 8) |
                         static_cast<uint32_t>(ciphertext[offset + 3]);

        offset += LENGTH_SIZE;

        if (offset + ct_len > ciphertext.size()) {
            return ENCHANT_ERROR_INVALID_FORMAT;
        }

        if (device_id == sender_device_id || device_id == address.device_id) {
            recipient_ct.assign(ciphertext.begin() + offset,
                                ciphertext.begin() + offset + ct_len);
            found = true;
            break;
        }

        offset += ct_len;
    }

    if (!found) {
        return ENCHANT_ERROR_NO_SESSION;
    }

    return decrypt_from_recipient(session, recipient_ct, sender_device_id, plaintext);
}

MultiRecipientEncoder::MultiRecipientEncoder()
    : initialized_(false) {}

MultiRecipientEncoder::~MultiRecipientEncoder() {}

int MultiRecipientEncoder::initialize(const std::vector<EnchantAddress>& addresses,
                                       const std::vector<SessionRecord>& sessions) {
    if (addresses.size() != sessions.size()) {
        return ENCHANT_ERROR_INVALID_FORMAT;
    }

    recipients_.clear();
    for (size_t i = 0; i < addresses.size(); ++i) {
        RecipientContext ctx;
        ctx.address = addresses[i];
        ctx.session = sessions[i];
        recipients_.push_back(std::move(ctx));
    }

    initialized_ = true;
    return ENCHANT_SUCCESS;
}

int MultiRecipientEncoder::encrypt_message(const std::vector<uint8_t>& plaintext,
                                            std::vector<uint8_t>& output) {
    if (!initialized_) {
        return ENCHANT_ERROR_INVALID_SESSION;
    }

    std::vector<EnchantAddress> addrs;
    std::vector<SessionRecord> sess;
    for (const auto& r : recipients_) {
        addrs.push_back(r.address);
        sess.push_back(r.session);
    }

    return multi_recipient_encrypt(addrs, sess, plaintext, output);
}

int MultiRecipientEncoder::finalize(std::vector<uint8_t>& output) {
    (void)output;
    recipients_.clear();
    initialized_ = false;
    return ENCHANT_SUCCESS;
}

MultiRecipientDecoder::MultiRecipientDecoder()
    : sender_device_id_(0), initialized_(false) {}

MultiRecipientDecoder::~MultiRecipientDecoder() {}

int MultiRecipientDecoder::initialize(const EnchantAddress& address,
                                       SessionRecord& session,
                                       uint32_t sender_device_id) {
    address_ = address;
    session_ = session;
    sender_device_id_ = sender_device_id;
    initialized_ = true;
    return ENCHANT_SUCCESS;
}

int MultiRecipientDecoder::decrypt_message(const std::vector<uint8_t>& ciphertext,
                                             std::vector<uint8_t>& plaintext) {
    if (!initialized_) {
        return ENCHANT_ERROR_INVALID_SESSION;
    }

    return multi_recipient_decrypt(address_, session_, ciphertext,
                                    sender_device_id_, plaintext);
}

} // namespace protocol
} // namespace enchant
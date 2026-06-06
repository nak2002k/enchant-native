#ifndef ENCHANT_VEIL_TYPES_HPP
#define ENCHANT_VEIL_TYPES_HPP

#include <cstdint>
#include <string>
#include <vector>
#include <optional>
#include "enchant/error.h"
#include "secure/buffer.hpp"
#include "sealed_sender.pb.h"

namespace enchant {
namespace veil {

enum class CiphertextMessageType : uint8_t {
    PREKEY_MESSAGE = 1,
    MESSAGE = 2,
    SENDERKEY_MESSAGE = 7,
    PLAINTEXT_CONTENT = 8
};

enum class ContentHint : uint32_t {
    DEFAULT = 0,
    RESENDABLE = 1,
    IMPLICIT = 2
};

enum class Direction : uint8_t {
    Sending = 0,
    Receiving = 1
};

constexpr uint8_t SEALED_SENDER_V1_MAJOR_VERSION = 1;
constexpr uint8_t SEALED_SENDER_V1_FULL_VERSION = 0x11;
constexpr uint8_t SEALED_SENDER_V2_MAJOR_VERSION = 2;
constexpr uint8_t SEALED_SENDER_V2_UUID_FULL_VERSION = 0x22;
constexpr uint8_t SEALED_SENDER_V2_SERVICE_ID_FULL_VERSION = 0x23;

constexpr uint32_t VALID_REGISTRATION_ID_MASK = 0x3FFF;

constexpr size_t MESSAGE_KEY_LEN = 32;
constexpr size_t AUTH_TAG_LEN = 16;
constexpr size_t PUBLIC_KEY_LEN = 32;

class ServerCertificate {
public:
    ServerCertificate();
    ServerCertificate(uint32_t key_id, secure::SecureBuffer key,
                     secure::SecureBuffer certificate, secure::SecureBuffer signature);

    static ServerCertificate new_cert(uint32_t key_id, secure::SecureBuffer key,
                                      const uint8_t* trust_root_private,
                                      secure::SecureBuffer& out_serialized);

    int deserialize(const uint8_t* data, size_t data_len);
    int serialize(uint8_t* output, size_t* output_len) const;
    int validate(const uint8_t* trust_root) const;

    uint32_t key_id() const { return key_id_; }
    const secure::SecureBuffer& key() const { return key_; }
    const secure::SecureBuffer& certificate() const { return certificate_; }
    const secure::SecureBuffer& signature() const { return signature_; }
    const secure::SecureBuffer& serialized() const { return serialized_; }

    ServerCertificate clone() const;

private:
    uint32_t key_id_;
    secure::SecureBuffer serialized_;
    secure::SecureBuffer key_;
    secure::SecureBuffer certificate_;
    secure::SecureBuffer signature_;
};

class SenderCertificate {
public:
    SenderCertificate();

    SenderCertificate(const std::string& sender_uuid, uint32_t sender_device_id,
                      const uint8_t* identity_key, uint64_t expiration = 0);

    static SenderCertificate new_cert(
        const std::string& sender_uuid,
        const std::optional<std::string>& sender_e164,
        secure::SecureBuffer key,
        uint32_t sender_device_id,
        uint64_t expiration,
        const ServerCertificate& signer,
        const uint8_t* signer_private_key,
        secure::SecureBuffer& out_serialized);

    int deserialize(const uint8_t* data, size_t data_len);
    int serialize(uint8_t* output, size_t* output_len) const;
    int validate(const uint8_t* trust_root, uint64_t validation_timestamp);
    int validate_with_trust_roots(const uint8_t* const* trust_roots, size_t num_roots,
                                   uint64_t validation_timestamp);

    uint32_t sender_device_id() const { return sender_device_id_; }
    const std::string& sender_uuid() const { return sender_uuid_; }
    const std::string& sender_e164() const { return sender_e164_; }
    uint64_t expiration() const { return expiration_; }
    const secure::SecureBuffer& key() const { return key_; }
    const ServerCertificate& signer() const;
    const secure::SecureBuffer& certificate() const { return certificate_; }
    const secure::SecureBuffer& signature() const { return signature_; }
    const secure::SecureBuffer& serialized() const { return serialized_; }

    SenderCertificate clone() const;

private:
    enum class SignerType : uint8_t {
        Embedded = 0,
        Reference = 1
    };

    uint32_t sender_device_id_;
    std::string sender_uuid_;
    std::string sender_e164_;
    uint64_t expiration_;
    secure::SecureBuffer serialized_;
    secure::SecureBuffer key_;
    secure::SecureBuffer certificate_;
    secure::SecureBuffer signature_;
    SignerType signer_type_;
    ServerCertificate embedded_signer_;
    uint32_t referenced_signer_id_;
};

struct UNIDENTIFIED_SENDER_MESSAGE_CONTENT {
    CiphertextMessageType msg_type;
    SenderCertificate sender;
    std::vector<uint8_t> contents;
    ContentHint content_hint;
    std::vector<uint8_t> group_id;
    std::vector<uint8_t> serialized;
};

class UnidentifiedSenderMessageContent {
public:
    UnidentifiedSenderMessageContent();

    int deserialize(const uint8_t* data, size_t data_len);

    static UnidentifiedSenderMessageContent new_message(
        CiphertextMessageType msg_type,
        const SenderCertificate& sender,
        const uint8_t* contents, size_t contents_len,
        ContentHint content_hint,
        const uint8_t* group_id, size_t group_id_len);

    CiphertextMessageType msg_type() const { return msg_type_; }
    const SenderCertificate& sender() const { return sender_; }
    const std::vector<uint8_t>& contents() const { return contents_; }
    ContentHint content_hint() const { return content_hint_; }
    const std::vector<uint8_t>& group_id() const { return group_id_; }
    const std::vector<uint8_t>& serialized() const { return serialized_; }

    UnidentifiedSenderMessageContent clone() const;

private:
    CiphertextMessageType msg_type_;
    SenderCertificate sender_;
    std::vector<uint8_t> contents_;
    ContentHint content_hint_;
    std::vector<uint8_t> group_id_;
    std::vector<uint8_t> serialized_;
};

struct VeilDecryptionResult {
    secure::SecureBuffer sender_identity;
    uint32_t device_id;
    secure::SecureBuffer plaintext;
    SenderCertificate sender_cert;
    uint8_t message_version;
    ContentHint content_hint;
    int error_code;
};

struct SealedSenderDecryptionResult {
    std::string sender_uuid;
    std::string sender_e164;
    uint32_t device_id;
    std::vector<uint8_t> message;
    int error_code;
};

struct RecipientInfo {
    uint32_t recipient_device_id;
    secure::SecureBuffer recipient_public_key;
};

struct VeilEncryptionResult {
    secure::SecureBuffer ciphertext;
    bool success;
    int error_code;
};

class UnidentifiedSenderMessage {
public:
    UnidentifiedSenderMessage();

    enum class Version : uint8_t {
        V1 = 1,
        V2_UUID = 2,
        V2_SERVICE_ID = 3
    };

    int deserialize(const uint8_t* data, size_t data_len);

    bool is_v1() const { return version_ == Version::V1; }
    bool is_v2() const { return version_ == Version::V2_UUID ||
                                   version_ == Version::V2_SERVICE_ID; }
    Version version() const { return version_; }

    const secure::SecureBuffer& ephemeral_public() const { return ephemeral_public_; }
    const secure::SecureBuffer& encrypted_static() const { return encrypted_static_; }
    const secure::SecureBuffer& encrypted_message() const { return encrypted_message_; }
    const secure::SecureBuffer& encrypted_message_key() const { return encrypted_message_key_; }
    const secure::SecureBuffer& authentication_tag() const { return authentication_tag_; }
    const secure::SecureBuffer& raw_data() const { return raw_data_; }

private:
    Version version_;
    secure::SecureBuffer ephemeral_public_;
    secure::SecureBuffer encrypted_static_;
    secure::SecureBuffer encrypted_message_;
    secure::SecureBuffer encrypted_message_key_;
    secure::SecureBuffer authentication_tag_;
    secure::SecureBuffer raw_data_;
};

}
}

#endif

#include "veil_types.hpp"
#include "veil_error.hpp"
#include "primitives/ed25519.hpp"
#include "primitives/random.hpp"
#include <cstring>
#include <string>

namespace enchant {
namespace veil {

ServerCertificate::ServerCertificate()
    : key_id_(0), serialized_(0), key_(32), certificate_(), signature_() {}

ServerCertificate::ServerCertificate(uint32_t key_id, secure::SecureBuffer key,
                                     secure::SecureBuffer certificate, secure::SecureBuffer signature)
    : key_id_(key_id), serialized_(0), key_(std::move(key)),
      certificate_(std::move(certificate)), signature_(std::move(signature)) {}

ServerCertificate ServerCertificate::clone() const {
    ServerCertificate c;
    c.key_id_ = key_id_;
    c.key_ = key_.clone();
    c.certificate_ = certificate_.clone();
    c.signature_ = signature_.clone();
    c.serialized_ = serialized_.clone();
    return c;
}

ServerCertificate ServerCertificate::new_cert(uint32_t key_id, secure::SecureBuffer key,
                                               const uint8_t* trust_root_private,
                                               secure::SecureBuffer& out_serialized) {
    enchant::proto::sealed_sender::ServerCertificate::Certificate cert_pb;
    cert_pb.set_id(key_id);
    cert_pb.set_key(std::string(reinterpret_cast<const char*>(key.data()), key.size()));

    std::string cert_str;
    if (!cert_pb.SerializeToString(&cert_str)) {
        return ServerCertificate();
    }

    secure::SecureBuffer cert_data(reinterpret_cast<const uint8_t*>(cert_str.data()), cert_str.size());

    uint8_t sig[64];
    primitives::ed25519_sign(cert_data.data(), cert_data.size(), trust_root_private, sig);
    secure::SecureBuffer signature(sig, 64);

    enchant::proto::sealed_sender::ServerCertificate pb;
    pb.set_certificate(cert_str);
    pb.set_signature(std::string(reinterpret_cast<const char*>(signature.data()), signature.size()));

    size_t sz = pb.ByteSizeLong();
    if (sz > INT_MAX) {
        return ServerCertificate();
    }
    out_serialized = secure::SecureBuffer(sz);
    if (!pb.SerializeToArray(out_serialized.data(), static_cast<int>(sz))) {
        out_serialized.clear();
        return ServerCertificate();
    }

    ServerCertificate cert;
    cert.key_id_ = key_id;
    cert.key_ = std::move(key);
    cert.certificate_ = std::move(cert_data);
    cert.signature_ = std::move(signature);
    cert.serialized_ = out_serialized.clone();
    return cert;
}

int ServerCertificate::deserialize(const uint8_t* data, size_t data_len) {
    if (!data) return ENCHANT_VEIL_ERROR_NULL_POINTER;

    serialized_ = secure::SecureBuffer(data, data_len);

    enchant::proto::sealed_sender::ServerCertificate pb;
    if (!pb.ParseFromArray(data, static_cast<int>(data_len))) {
        return ENCHANT_VEIL_ERROR_INVALID_PROTOBUF_ENCODING;
    }

    if (pb.certificate().empty() || pb.signature().empty()) {
        return ENCHANT_VEIL_ERROR_INVALID_FORMAT;
    }

    auto cert_data = pb.certificate();
    enchant::proto::sealed_sender::ServerCertificate::Certificate cert_pb;
    if (!cert_pb.ParseFromString(cert_data)) {
        return ENCHANT_VEIL_ERROR_INVALID_PROTOBUF_ENCODING;
    }

    key_id_ = cert_pb.id();
    if (cert_pb.key().size() != 32) {
        return ENCHANT_VEIL_ERROR_INVALID_KEY_SIZE;
    }

    key_ = secure::SecureBuffer(reinterpret_cast<const uint8_t*>(cert_pb.key().data()), cert_pb.key().size());
    certificate_ = secure::SecureBuffer(reinterpret_cast<const uint8_t*>(cert_data.data()), cert_data.size());
    signature_ = secure::SecureBuffer(reinterpret_cast<const uint8_t*>(pb.signature().data()), pb.signature().size());

    return ENCHANT_VEIL_SUCCESS;
}

int ServerCertificate::serialize(uint8_t* output, size_t* output_len) const {
    if (!output || !output_len) return ENCHANT_VEIL_ERROR_NULL_POINTER;

    enchant::proto::sealed_sender::ServerCertificate::Certificate cert_pb;
    cert_pb.set_id(key_id_);
    cert_pb.set_key(std::string(reinterpret_cast<const char*>(key_.data()), key_.size()));

    std::string cert_str;
    if (!cert_pb.SerializeToString(&cert_str)) {
        return ENCHANT_VEIL_ERROR_INTERNAL;
    }

    enchant::proto::sealed_sender::ServerCertificate pb;
    pb.set_certificate(cert_str);
    pb.set_signature(std::string(reinterpret_cast<const char*>(signature_.data()), signature_.size()));

    size_t serialized_size = pb.ByteSizeLong();
    if (*output_len < serialized_size) {
        *output_len = serialized_size;
        return ENCHANT_VEIL_ERROR_BUFFER_TOO_SMALL;
    }

    if (!pb.SerializeToArray(output, static_cast<int>(serialized_size))) {
        return ENCHANT_VEIL_ERROR_INTERNAL;
    }

    *output_len = serialized_size;
    return ENCHANT_VEIL_SUCCESS;
}

int ServerCertificate::validate(const uint8_t* trust_root) const {
    if (!trust_root) return ENCHANT_VEIL_ERROR_NULL_POINTER;

    int rc = primitives::ed25519_verify(
        certificate_.data(), certificate_.size(),
        signature_.data(), trust_root
    );

    if (rc != ENCHANT_SUCCESS) {
        return ENCHANT_VEIL_ERROR_CERTIFICATE_SIGNATURE_INVALID;
    }

    return ENCHANT_VEIL_SUCCESS;
}

SenderCertificate::SenderCertificate()
    : sender_device_id_(0), expiration_(0), serialized_(0), key_(32),
      certificate_(), signature_(),
      signer_type_(SignerType::Embedded),
      embedded_signer_(), referenced_signer_id_(0) {}

SenderCertificate::SenderCertificate(const std::string& sender_uuid, uint32_t sender_device_id,
                                     const uint8_t* identity_key, uint64_t expiration)
    : sender_device_id_(sender_device_id), sender_uuid_(sender_uuid),
      expiration_(expiration), serialized_(0),
      key_(identity_key, 32),
      certificate_(), signature_(),
      signer_type_(SignerType::Embedded),
      embedded_signer_(), referenced_signer_id_(0) {}

SenderCertificate SenderCertificate::clone() const {
    SenderCertificate c;
    c.sender_device_id_ = sender_device_id_;
    c.sender_uuid_ = sender_uuid_;
    c.sender_e164_ = sender_e164_;
    c.expiration_ = expiration_;
    c.serialized_ = serialized_.clone();
    c.key_ = key_.clone();
    c.certificate_ = certificate_.clone();
    c.signature_ = signature_.clone();
    c.signer_type_ = signer_type_;
    c.embedded_signer_ = embedded_signer_.clone();
    c.referenced_signer_id_ = referenced_signer_id_;
    return c;
}

SenderCertificate SenderCertificate::new_cert(
    const std::string& sender_uuid,
    const std::optional<std::string>& sender_e164,
    secure::SecureBuffer key,
    uint32_t sender_device_id,
    uint64_t expiration,
    const ServerCertificate& signer,
    const uint8_t* signer_private_key,
    secure::SecureBuffer& out_serialized) {

    enchant::proto::sealed_sender::SenderCertificate::Certificate cert_pb;
    cert_pb.set_uuidstring(sender_uuid);
    if (sender_e164.has_value() && !sender_e164->empty()) {
        cert_pb.set_sendere164(*sender_e164);
    }
    cert_pb.set_senderdevice(sender_device_id);
    cert_pb.set_expires(expiration);
    cert_pb.set_identitykey(std::string(reinterpret_cast<const char*>(key.data()), key.size()));

    const auto& signer_ser = signer.serialized();
    cert_pb.set_certificate(std::string(reinterpret_cast<const char*>(signer_ser.data()), signer_ser.size()));

    std::string cert_str;
    if (!cert_pb.SerializeToString(&cert_str)) {
        return SenderCertificate();
    }

    secure::SecureBuffer cert_data(reinterpret_cast<const uint8_t*>(cert_str.data()), cert_str.size());

    uint8_t sig[64];
    primitives::ed25519_sign(cert_data.data(), cert_data.size(), signer_private_key, sig);
    secure::SecureBuffer signature(sig, 64);

    enchant::proto::sealed_sender::SenderCertificate pb;
    pb.set_certificate(cert_str);
    pb.set_signature(std::string(reinterpret_cast<const char*>(signature.data()), signature.size()));

    size_t sz = pb.ByteSizeLong();
    if (sz > INT_MAX) {
        return SenderCertificate();
    }
    out_serialized = secure::SecureBuffer(sz);
    if (!pb.SerializeToArray(out_serialized.data(), static_cast<int>(sz))) {
        out_serialized.clear();
        return SenderCertificate();
    }

    SenderCertificate cert;
    cert.sender_device_id_ = sender_device_id;
    cert.sender_uuid_ = sender_uuid;
    cert.sender_e164_ = sender_e164.has_value() ? *sender_e164 : "";
    cert.expiration_ = expiration;
    cert.serialized_ = out_serialized.clone();
    cert.key_ = std::move(key);
    cert.certificate_ = std::move(cert_data);
    cert.signature_ = std::move(signature);
    cert.signer_type_ = SignerType::Embedded;
    cert.embedded_signer_ = signer.clone();
    return cert;
}

int SenderCertificate::deserialize(const uint8_t* data, size_t data_len) {
    if (!data) return ENCHANT_VEIL_ERROR_NULL_POINTER;

    serialized_ = secure::SecureBuffer(data, data_len);

    enchant::proto::sealed_sender::SenderCertificate pb;
    if (!pb.ParseFromArray(data, static_cast<int>(data_len))) {
        return ENCHANT_VEIL_ERROR_INVALID_PROTOBUF_ENCODING;
    }

    if (pb.certificate().empty() || pb.signature().empty()) {
        return ENCHANT_VEIL_ERROR_INVALID_FORMAT;
    }

    auto cert_data = pb.certificate();
    enchant::proto::sealed_sender::SenderCertificate::Certificate cert_pb;
    if (!cert_pb.ParseFromString(cert_data)) {
        return ENCHANT_VEIL_ERROR_INVALID_PROTOBUF_ENCODING;
    }

    sender_device_id_ = cert_pb.senderdevice();

    if (cert_pb.has_uuidstring()) {
        sender_uuid_ = cert_pb.uuidstring();
    } else if (cert_pb.has_uuidbytes()) {
        char uuid_str[64];
        const auto& uuid_bytes = cert_pb.uuidbytes();
        snprintf(uuid_str, sizeof(uuid_str),
                 "%02x%02x%02x%02x-%02x%02x-%02x%02x-%02x%02x-%02x%02x%02x%02x%02x%02x",
                 uuid_bytes[0], uuid_bytes[1], uuid_bytes[2], uuid_bytes[3],
                 uuid_bytes[4], uuid_bytes[5], uuid_bytes[6], uuid_bytes[7],
                 uuid_bytes[8], uuid_bytes[9], uuid_bytes[10], uuid_bytes[11],
                 uuid_bytes[12], uuid_bytes[13], uuid_bytes[14], uuid_bytes[15]);
        sender_uuid_ = uuid_str;
    } else {
        return ENCHANT_VEIL_ERROR_INVALID_FORMAT;
    }

    if (cert_pb.has_sendere164()) {
        sender_e164_ = cert_pb.sendere164();
    }

    expiration_ = cert_pb.expires();

    if (cert_pb.identitykey().size() != 32) {
        return ENCHANT_VEIL_ERROR_INVALID_KEY_SIZE;
    }
    key_ = secure::SecureBuffer(reinterpret_cast<const uint8_t*>(cert_pb.identitykey().data()),
                                 cert_pb.identitykey().size());

    if (cert_pb.has_certificate()) {
        signer_type_ = SignerType::Embedded;
        int rc = embedded_signer_.deserialize(
            reinterpret_cast<const uint8_t*>(cert_pb.certificate().data()),
            cert_pb.certificate().size()
        );
        if (rc != ENCHANT_VEIL_SUCCESS) return rc;
    } else if (cert_pb.has_id()) {
        signer_type_ = SignerType::Reference;
        referenced_signer_id_ = cert_pb.id();
    } else {
        return ENCHANT_VEIL_ERROR_INVALID_FORMAT;
    }

    certificate_ = secure::SecureBuffer(reinterpret_cast<const uint8_t*>(cert_data.data()), cert_data.size());
    signature_ = secure::SecureBuffer(reinterpret_cast<const uint8_t*>(pb.signature().data()), pb.signature().size());

    return ENCHANT_VEIL_SUCCESS;
}

int SenderCertificate::serialize(uint8_t* output, size_t* output_len) const {
    if (!output || !output_len) return ENCHANT_VEIL_ERROR_NULL_POINTER;

    if (!serialized_.empty()) {
        if (*output_len < serialized_.size()) {
            *output_len = serialized_.size();
            return ENCHANT_VEIL_ERROR_BUFFER_TOO_SMALL;
        }
        memcpy(output, serialized_.data(), serialized_.size());
        *output_len = serialized_.size();
        return ENCHANT_VEIL_SUCCESS;
    }

    enchant::proto::sealed_sender::SenderCertificate::Certificate cert_pb;
    cert_pb.set_senderdevice(sender_device_id_);
    cert_pb.set_uuidstring(sender_uuid_);
    if (!sender_e164_.empty()) {
        cert_pb.set_sendere164(sender_e164_);
    }
    cert_pb.set_expires(expiration_);
    cert_pb.set_identitykey(std::string(reinterpret_cast<const char*>(key_.data()), key_.size()));

    if (signer_type_ == SignerType::Embedded) {
        const auto& signer_ser = embedded_signer_.serialized();
        cert_pb.set_certificate(std::string(reinterpret_cast<const char*>(signer_ser.data()), signer_ser.size()));
    } else {
        cert_pb.set_id(referenced_signer_id_);
    }

    std::string cert_str;
    if (!cert_pb.SerializeToString(&cert_str)) {
        return ENCHANT_VEIL_ERROR_INTERNAL;
    }

    enchant::proto::sealed_sender::SenderCertificate pb;
    pb.set_certificate(cert_str);
    pb.set_signature(std::string(reinterpret_cast<const char*>(signature_.data()), signature_.size()));

    size_t serialized_size = pb.ByteSizeLong();
    if (*output_len < serialized_size) {
        *output_len = serialized_size;
        return ENCHANT_VEIL_ERROR_BUFFER_TOO_SMALL;
    }

    if (!pb.SerializeToArray(output, static_cast<int>(serialized_size))) {
        return ENCHANT_VEIL_ERROR_INTERNAL;
    }

    *output_len = serialized_size;
    return ENCHANT_VEIL_SUCCESS;
}

int SenderCertificate::validate(const uint8_t* trust_root, uint64_t validation_timestamp) {
    if (!trust_root) return ENCHANT_VEIL_ERROR_NULL_POINTER;

    if (validation_timestamp > expiration_) {
        return ENCHANT_VEIL_ERROR_CERTIFICATE_EXPIRED;
    }

    const ServerCertificate* signer_ptr = nullptr;
    if (signer_type_ == SignerType::Embedded) {
        signer_ptr = &embedded_signer_;
    }

    if (!signer_ptr) {
        return ENCHANT_VEIL_ERROR_SERVER_CERTIFICATE_UNKNOWN;
    }

    int rc = signer_ptr->validate(trust_root);
    if (rc != ENCHANT_VEIL_SUCCESS) {
        return rc;
    }

    rc = primitives::ed25519_verify(
        certificate_.data(), certificate_.size(),
        signature_.data(), signer_ptr->key().data()
    );

    if (rc != ENCHANT_SUCCESS) {
        return ENCHANT_VEIL_ERROR_CERTIFICATE_SIGNATURE_INVALID;
    }

    return ENCHANT_VEIL_SUCCESS;
}

int SenderCertificate::validate_with_trust_roots(const uint8_t* const* trust_roots,
                                                   size_t num_roots,
                                                   uint64_t validation_timestamp) {
    if (!trust_roots || num_roots == 0) return ENCHANT_VEIL_ERROR_NULL_POINTER;

    if (validation_timestamp > expiration_) {
        return ENCHANT_VEIL_ERROR_CERTIFICATE_EXPIRED;
    }

    const ServerCertificate* signer_ptr = nullptr;
    if (signer_type_ == SignerType::Embedded) {
        signer_ptr = &embedded_signer_;
    }

    if (!signer_ptr) {
        return ENCHANT_VEIL_ERROR_SERVER_CERTIFICATE_UNKNOWN;
    }

    uint8_t any_valid = 0;
    for (size_t i = 0; i < num_roots; i++) {
        int rc = signer_ptr->validate(trust_roots[i]);
        uint8_t success = (rc == ENCHANT_VEIL_SUCCESS) ? 1 : 0;
        any_valid |= success;
    }

    if (sodium_memcmp(&any_valid, "\x01", 1) != 0) {
        return ENCHANT_VEIL_ERROR_CERTIFICATE_SIGNATURE_INVALID;
    }

    int rc = primitives::ed25519_verify(
        certificate_.data(), certificate_.size(),
        signature_.data(), signer_ptr->key().data()
    );

    if (rc != ENCHANT_SUCCESS) {
        return ENCHANT_VEIL_ERROR_CERTIFICATE_SIGNATURE_INVALID;
    }

    return ENCHANT_VEIL_SUCCESS;
}

const ServerCertificate& SenderCertificate::signer() const {
    return embedded_signer_;
}

UnidentifiedSenderMessageContent::UnidentifiedSenderMessageContent()
    : msg_type_(CiphertextMessageType::MESSAGE), sender_(),
      content_hint_(ContentHint::DEFAULT) {}

UnidentifiedSenderMessageContent UnidentifiedSenderMessageContent::clone() const {
    UnidentifiedSenderMessageContent c;
    c.msg_type_ = msg_type_;
    c.sender_ = sender_.clone();
    c.contents_ = contents_;
    c.content_hint_ = content_hint_;
    c.group_id_ = group_id_;
    c.serialized_ = serialized_;
    return c;
}

int UnidentifiedSenderMessageContent::deserialize(const uint8_t* data, size_t data_len) {
    if (!data) return ENCHANT_VEIL_ERROR_NULL_POINTER;

    serialized_ = std::vector<uint8_t>(data, data + data_len);

    enchant::proto::sealed_sender::UnidentifiedSenderMessage::Message pb;
    if (!pb.ParseFromArray(data, static_cast<int>(data_len))) {
        return ENCHANT_VEIL_ERROR_INVALID_PROTOBUF_ENCODING;
    }

    if (!pb.has_type() || !pb.has_sendercertificate() || !pb.has_content()) {
        return ENCHANT_VEIL_ERROR_INVALID_FORMAT;
    }

    switch (pb.type()) {
        case enchant::proto::sealed_sender::UnidentifiedSenderMessage_Message_Type_PREKEY_MESSAGE:
            msg_type_ = CiphertextMessageType::PREKEY_MESSAGE;
            break;
        case enchant::proto::sealed_sender::UnidentifiedSenderMessage_Message_Type_MESSAGE:
            msg_type_ = CiphertextMessageType::MESSAGE;
            break;
        case enchant::proto::sealed_sender::UnidentifiedSenderMessage_Message_Type_SENDERKEY_MESSAGE:
            msg_type_ = CiphertextMessageType::SENDERKEY_MESSAGE;
            break;
        case enchant::proto::sealed_sender::UnidentifiedSenderMessage_Message_Type_PLAINTEXT_CONTENT:
            msg_type_ = CiphertextMessageType::PLAINTEXT_CONTENT;
            break;
        default:
            return ENCHANT_VEIL_ERROR_INVALID_FORMAT;
    }

    const auto& sender_cert_data = pb.sendercertificate();
    int rc = sender_.deserialize(
        reinterpret_cast<const uint8_t*>(sender_cert_data.data()),
        sender_cert_data.size()
    );
    if (rc != ENCHANT_VEIL_SUCCESS) return rc;

    const auto& content_data = pb.content();
    contents_ = std::vector<uint8_t>(reinterpret_cast<const uint8_t*>(content_data.data()),
                                      reinterpret_cast<const uint8_t*>(content_data.data()) + content_data.size());

    if (pb.has_contenthint()) {
        switch (pb.contenthint()) {
            case enchant::proto::sealed_sender::UnidentifiedSenderMessage_Message_ContentHint_RESENDABLE:
                content_hint_ = ContentHint::RESENDABLE;
                break;
            case enchant::proto::sealed_sender::UnidentifiedSenderMessage_Message_ContentHint_IMPLICIT:
                content_hint_ = ContentHint::IMPLICIT;
                break;
            default:
                content_hint_ = ContentHint::DEFAULT;
                break;
        }
    } else {
        content_hint_ = ContentHint::DEFAULT;
    }

    if (pb.has_groupid()) {
        const auto& gid = pb.groupid();
        group_id_ = std::vector<uint8_t>(reinterpret_cast<const uint8_t*>(gid.data()),
                                          reinterpret_cast<const uint8_t*>(gid.data()) + gid.size());
    }

    return ENCHANT_VEIL_SUCCESS;
}

UnidentifiedSenderMessageContent UnidentifiedSenderMessageContent::new_message(
    CiphertextMessageType msg_type,
    const SenderCertificate& sender,
    const uint8_t* contents, size_t contents_len,
    ContentHint content_hint,
    const uint8_t* group_id, size_t group_id_len) {

    UnidentifiedSenderMessageContent result;
    result.msg_type_ = msg_type;
    result.sender_ = sender.clone();
    result.contents_ = std::vector<uint8_t>(contents, contents + contents_len);
    result.content_hint_ = content_hint;
    if (group_id && group_id_len > 0) {
        result.group_id_ = std::vector<uint8_t>(group_id, group_id + group_id_len);
    }

    enchant::proto::sealed_sender::UnidentifiedSenderMessage::Message pb;
    switch (msg_type) {
        case CiphertextMessageType::PREKEY_MESSAGE:
            pb.set_type(enchant::proto::sealed_sender::UnidentifiedSenderMessage_Message_Type_PREKEY_MESSAGE);
            break;
        case CiphertextMessageType::MESSAGE:
            pb.set_type(enchant::proto::sealed_sender::UnidentifiedSenderMessage_Message_Type_MESSAGE);
            break;
        case CiphertextMessageType::SENDERKEY_MESSAGE:
            pb.set_type(enchant::proto::sealed_sender::UnidentifiedSenderMessage_Message_Type_SENDERKEY_MESSAGE);
            break;
        case CiphertextMessageType::PLAINTEXT_CONTENT:
            pb.set_type(enchant::proto::sealed_sender::UnidentifiedSenderMessage_Message_Type_PLAINTEXT_CONTENT);
            break;
    }

    const auto& sender_ser = sender.serialized();
    pb.set_sendercertificate(std::string(reinterpret_cast<const char*>(sender_ser.data()), sender_ser.size()));

    pb.set_content(std::string(reinterpret_cast<const char*>(contents), contents_len));

    if (content_hint != ContentHint::DEFAULT) {
        switch (content_hint) {
            case ContentHint::RESENDABLE:
                pb.set_contenthint(enchant::proto::sealed_sender::UnidentifiedSenderMessage_Message_ContentHint_RESENDABLE);
                break;
            case ContentHint::IMPLICIT:
                pb.set_contenthint(enchant::proto::sealed_sender::UnidentifiedSenderMessage_Message_ContentHint_IMPLICIT);
                break;
            default:
                break;
        }
    }

    if (group_id && group_id_len > 0) {
        pb.set_groupid(std::string(reinterpret_cast<const char*>(group_id), group_id_len));
    }

    size_t sz = pb.ByteSizeLong();
    if (sz > INT_MAX) {
        return result;
    }
    result.serialized_ = std::vector<uint8_t>(sz);
    if (!pb.SerializeToArray(result.serialized_.data(), static_cast<int>(sz))) {
        result.serialized_.clear();
    }

    return result;
}

UnidentifiedSenderMessage::UnidentifiedSenderMessage()
    : version_(Version::V1),
      ephemeral_public_(32),
      encrypted_static_(),
      encrypted_message_(),
      encrypted_message_key_(32),
      authentication_tag_(16) {}

int UnidentifiedSenderMessage::deserialize(const uint8_t* data, size_t data_len) {
    if (!data || data_len < 1) return ENCHANT_VEIL_ERROR_NULL_POINTER;

    uint8_t version_byte = data[0];
    uint8_t major_version = version_byte >> 4;

    if (major_version == 0 || major_version == SEALED_SENDER_V1_MAJOR_VERSION) {
        enchant::proto::sealed_sender::UnidentifiedSenderMessage pb;
        if (!pb.ParseFromArray(data + 1, static_cast<int>(data_len - 1))) {
            return ENCHANT_VEIL_ERROR_INVALID_PROTOBUF_ENCODING;
        }

        version_ = Version::V1;

        if (pb.ephemeralpublic().size() != 32) {
            return ENCHANT_VEIL_ERROR_INVALID_KEY_SIZE;
        }
        ephemeral_public_ = secure::SecureBuffer(
            reinterpret_cast<const uint8_t*>(pb.ephemeralpublic().data()), 32);
        encrypted_static_ = secure::SecureBuffer(
            reinterpret_cast<const uint8_t*>(pb.encryptedstatic().data()),
            pb.encryptedstatic().size());
        encrypted_message_ = secure::SecureBuffer(
            reinterpret_cast<const uint8_t*>(pb.encryptedmessage().data()),
            pb.encryptedmessage().size());

    } else if (major_version == SEALED_SENDER_V2_MAJOR_VERSION) {
        version_ = (version_byte == SEALED_SENDER_V2_UUID_FULL_VERSION)
                       ? Version::V2_UUID : Version::V2_SERVICE_ID;

        if (data_len < 1 + MESSAGE_KEY_LEN + AUTH_TAG_LEN + PUBLIC_KEY_LEN) {
            return ENCHANT_VEIL_ERROR_CIPHERTEXT_TOO_SHORT;
        }

        raw_data_ = secure::SecureBuffer(data, data_len);

        size_t offset = 1;
        encrypted_message_key_ = secure::SecureBuffer(data + offset, MESSAGE_KEY_LEN);
        offset += MESSAGE_KEY_LEN;

        authentication_tag_ = secure::SecureBuffer(data + offset, AUTH_TAG_LEN);
        offset += AUTH_TAG_LEN;

        ephemeral_public_ = secure::SecureBuffer(data + offset, PUBLIC_KEY_LEN);
        offset += PUBLIC_KEY_LEN;

        encrypted_message_ = secure::SecureBuffer(data + offset, data_len - offset);

    } else {
        return ENCHANT_VEIL_ERROR_UNKNOWN_VERSION;
    }

    return ENCHANT_VEIL_SUCCESS;
}

}
}

#include "enchant/protocol/exceptions.hpp"
#include <cstring>

namespace enchant {
namespace protocol {

LegacyMessageException::LegacyMessageException()
    : message_("Legacy message version not supported") {}

LegacyMessageException::LegacyMessageException(const char* message)
    : message_(message ? message : "Legacy message version not supported") {}

const char* LegacyMessageException::what() const noexcept {
    return message_.c_str();
}

InvalidKeyIdException::InvalidKeyIdException()
    : message_("Invalid or unknown key ID") {}

InvalidKeyIdException::InvalidKeyIdException(const char* message)
    : message_(message ? message : "Invalid or unknown key ID") {}

const char* InvalidKeyIdException::what() const noexcept {
    return message_.c_str();
}

InvalidVersionException::InvalidVersionException()
    : message_("Invalid message version") {}

InvalidVersionException::InvalidVersionException(uint32_t version) {
    char buf[64];
    snprintf(buf, sizeof(buf), "Invalid message version: %u", version);
    message_ = buf;
}

InvalidVersionException::InvalidVersionException(const char* message)
    : message_(message ? message : "Invalid message version") {}

const char* InvalidVersionException::what() const noexcept {
    return message_.c_str();
}

InvalidRegistrationIdException::InvalidRegistrationIdException() {}

InvalidRegistrationIdException::InvalidRegistrationIdException(uint32_t registration_id) {
    char buf[64];
    snprintf(buf, sizeof(buf), "Invalid registration ID: %u", registration_id);
    message_ = buf;
}

InvalidRegistrationIdException::InvalidRegistrationIdException(const char* message)
    : message_(message ? message : "Invalid registration ID") {}

const char* InvalidRegistrationIdException::what() const noexcept {
    return message_.c_str();
}

NoSessionException::NoSessionException()
    : message_("No session found"), has_address_(false) {}

NoSessionException::NoSessionException(const EnchantAddress& address)
    : address_(address), has_address_(true) {
    message_ = "No session found for " + address.name + ":" + std::to_string(address.device_id);
}

NoSessionException::NoSessionException(const char* message)
    : message_(message ? message : "No session found"), has_address_(false) {}

const char* NoSessionException::what() const noexcept {
    return message_.c_str();
}

const EnchantAddress* NoSessionException::get_address() const noexcept {
    return has_address_ ? &address_ : nullptr;
}

UntrustedIdentityException::UntrustedIdentityException()
    : message_("Untrusted identity key"), has_address_(false) {}

UntrustedIdentityException::UntrustedIdentityException(const EnchantAddress& address,
                                                       const uint8_t* key_id, size_t key_id_len)
    : address_(address), has_address_(true), key_id_(key_id, key_id + key_id_len) {
    message_ = "Untrusted identity key for " + address.name + ":" + std::to_string(address.device_id);
}

UntrustedIdentityException::UntrustedIdentityException(const char* message)
    : message_(message ? message : "Untrusted identity key"), has_address_(false) {}

const char* UntrustedIdentityException::what() const noexcept {
    return message_.c_str();
}

const EnchantAddress* UntrustedIdentityException::get_address() const noexcept {
    return has_address_ ? &address_ : nullptr;
}

const uint8_t* UntrustedIdentityException::get_key_id() const noexcept {
    return key_id_.empty() ? nullptr : key_id_.data();
}

size_t UntrustedIdentityException::get_key_id_len() const noexcept {
    return key_id_.size();
}

InvalidSessionException::InvalidSessionException()
    : message_("Invalid session state") {}

InvalidSessionException::InvalidSessionException(const char* message)
    : message_(message ? message : "Invalid session state") {}

const char* InvalidSessionException::what() const noexcept {
    return message_.c_str();
}

SelfSendException::SelfSendException()
    : message_("Self-send detected"), has_address_(false) {}

SelfSendException::SelfSendException(const EnchantAddress& address)
    : address_(address), has_address_(true) {
    message_ = "Self-send detected for " + address.name + ":" + std::to_string(address.device_id);
}

SelfSendException::SelfSendException(const char* message)
    : message_(message ? message : "Self-send detected"), has_address_(false) {}

const char* SelfSendException::what() const noexcept {
    return message_.c_str();
}

const EnchantAddress* SelfSendException::get_address() const noexcept {
    return has_address_ ? &address_ : nullptr;
}

DuplicateMessageException::DuplicateMessageException()
    : message_("Duplicate message received") {}

DuplicateMessageException::DuplicateMessageException(const char* message)
    : message_(message ? message : "Duplicate message received") {}

const char* DuplicateMessageException::what() const noexcept {
    return message_.c_str();
}

} // namespace protocol
} // namespace enchant
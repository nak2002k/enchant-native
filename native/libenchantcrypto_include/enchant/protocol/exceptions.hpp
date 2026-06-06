#ifndef ENCHANT_PROTOCOL_EXCEPTIONS_HPP
#define ENCHANT_PROTOCOL_EXCEPTIONS_HPP

#include <exception>
#include <string>
#include <vector>
#include "enchant/error.h"
#include "enchant/i_identity_store.hpp"

namespace enchant {
namespace protocol {

class LegacyMessageException : public std::exception {
public:
    LegacyMessageException();
    LegacyMessageException(const char* message);
    const char* what() const noexcept override;

private:
    std::string message_;
};

class InvalidKeyIdException : public std::exception {
public:
    InvalidKeyIdException();
    InvalidKeyIdException(const char* message);
    const char* what() const noexcept override;

private:
    std::string message_;
};

class InvalidVersionException : public std::exception {
public:
    InvalidVersionException();
    InvalidVersionException(uint32_t version);
    InvalidVersionException(const char* message);
    const char* what() const noexcept override;

private:
    std::string message_;
};

class InvalidRegistrationIdException : public std::exception {
public:
    InvalidRegistrationIdException();
    InvalidRegistrationIdException(uint32_t registration_id);
    InvalidRegistrationIdException(const char* message);
    const char* what() const noexcept override;

private:
    std::string message_;
};

class NoSessionException : public std::exception {
public:
    NoSessionException();
    NoSessionException(const EnchantAddress& address);
    NoSessionException(const char* message);
    const char* what() const noexcept override;
    const EnchantAddress* get_address() const noexcept;

private:
    std::string message_;
    EnchantAddress address_;
    bool has_address_;
};

class UntrustedIdentityException : public std::exception {
public:
    UntrustedIdentityException();
    UntrustedIdentityException(const EnchantAddress& address, const uint8_t* key_id, size_t key_id_len);
    UntrustedIdentityException(const char* message);
    const char* what() const noexcept override;
    const EnchantAddress* get_address() const noexcept;
    const uint8_t* get_key_id() const noexcept;
    size_t get_key_id_len() const noexcept;

private:
    std::string message_;
    EnchantAddress address_;
    bool has_address_;
    std::vector<uint8_t> key_id_;
};

class InvalidSessionException : public std::exception {
public:
    InvalidSessionException();
    InvalidSessionException(const char* message);
    const char* what() const noexcept override;

private:
    std::string message_;
};

class SelfSendException : public std::exception {
public:
    SelfSendException();
    SelfSendException(const EnchantAddress& address);
    SelfSendException(const char* message);
    const char* what() const noexcept override;
    const EnchantAddress* get_address() const noexcept;

private:
    std::string message_;
    EnchantAddress address_;
    bool has_address_;
};

class DuplicateMessageException : public std::exception {
public:
    DuplicateMessageException();
    DuplicateMessageException(const char* message);
    const char* what() const noexcept override;

private:
    std::string message_;
};

} // namespace protocol
} // namespace enchant

#endif
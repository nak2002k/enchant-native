#ifndef ENCHANT_I_IDENTITY_STORE_HPP
#define ENCHANT_I_IDENTITY_STORE_HPP

#include <cstdint>
#include <cstddef>
#include <string>
#include <vector>
#include <optional>
#include <unordered_map>
#include "enchant/error.h"
#include "secure/buffer.hpp"

namespace enchant {
namespace protocol {

struct EnchantAddress {
    std::string name;
    uint32_t device_id;

    EnchantAddress() : device_id(0) {}
    EnchantAddress(const std::string& name_, uint32_t device_id_)
        : name(name_), device_id(device_id_) {}
    EnchantAddress(const char* name_, uint32_t device_id_)
        : name(name_ ? name_ : ""), device_id(device_id_) {}

    bool operator==(const EnchantAddress& other) const {
        return name == other.name && device_id == other.device_id;
    }

    bool operator!=(const EnchantAddress& other) const {
        return !(*this == other);
    }
};

enum class Direction : uint8_t {
    Sending = 0,
    Receiving = 1
};

class IdentityKey {
public:
    secure::SecureBuffer public_key;

    IdentityKey() : public_key(32) {}
    explicit IdentityKey(const uint8_t* key_data) : public_key(key_data, 32) {}
    explicit IdentityKey(const secure::SecureBuffer& key_buffer) : public_key(key_buffer.clone()) {}

    const uint8_t* data() const { return public_key.data(); }
    size_t size() const { return public_key.size(); }

    bool operator==(const IdentityKey& other) const {
        if (public_key.size() != other.public_key.size()) return false;
        return sodium_memcmp(public_key.data(), other.public_key.data(), public_key.size()) == 0;
    }

    bool operator!=(const IdentityKey& other) const {
        return !(*this == other);
    }
};

class IIdentityKeyStore {
public:
    virtual ~IIdentityKeyStore() = default;

    virtual bool save_identity(const EnchantAddress& address, const IdentityKey& identity_key) = 0;
    virtual bool set_trust(const EnchantAddress& address, bool trusted) = 0;
    virtual bool is_trusted_identity(const EnchantAddress& address, const IdentityKey& identity_key, Direction direction) = 0;
    virtual std::optional<IdentityKey> get_identity(const EnchantAddress& address) = 0;
    virtual std::vector<EnchantAddress> get_all_identities() = 0;
    virtual bool delete_identity(const EnchantAddress& address) = 0;
    virtual size_t get_identity_size() = 0;
    virtual int get_identity_key_pair(uint8_t* identity_public, uint8_t* identity_private) = 0;
};

struct IdentityChangeResult {
    std::optional<IdentityKey> old_key;
    std::optional<IdentityKey> new_key;
    bool changed;
};

class InMemoryIdentityKeyStore : public IIdentityKeyStore {
public:
    InMemoryIdentityKeyStore();

    bool save_identity(const EnchantAddress& address, const IdentityKey& identity_key) override;
    bool set_trust(const EnchantAddress& address, bool trusted) override;
    bool is_trusted_identity(const EnchantAddress& address, const IdentityKey& identity_key, Direction direction) override;
    std::optional<IdentityKey> get_identity(const EnchantAddress& address) override;
    std::vector<EnchantAddress> get_all_identities() override;
    bool delete_identity(const EnchantAddress& address) override;
    size_t get_identity_size() override;

    int get_identity_key_pair(uint8_t* identity_public, uint8_t* identity_private) override;
    int get_local_registration_id() const;
    int get_remote_registration_id(const std::string& address) const;
    int set_local_registration_id(uint32_t reg_id);

    bool save_identity_with_trust(const EnchantAddress& address, const IdentityKey& identity_key);
    std::optional<IdentityKey> get_trusted_identity(const EnchantAddress& address);
    IdentityChangeResult detect_identity_change(const EnchantAddress& address, const IdentityKey& new_key);

private:
    struct IdentityRecord {
        secure::SecureBuffer public_key;
        secure::SecureBuffer private_key;
    };

    IdentityRecord own_identity_;
    uint32_t local_registration_id_;
    std::unordered_map<std::string, secure::SecureBuffer> identities_;
    std::unordered_map<std::string, secure::SecureBuffer> trusted_identities_;
    std::unordered_map<std::string, uint32_t> remote_registration_ids_;
};

} // namespace protocol
} // namespace enchant

#endif
#ifndef ENCHANT_PROTOCOL_IDENTITY_STORE_HPP
#define ENCHANT_PROTOCOL_IDENTITY_STORE_HPP

#include <cstdint>
#include <cstddef>
#include <string>
#include <unordered_map>
#include "enchant/error.h"
#include "secure/buffer.hpp"

namespace enchant {
namespace protocol {

enum class IdentityDirection : uint8_t {
    Sending = 0,
    Receiving = 1
};

class IIdentityStore {
public:
    virtual ~IIdentityStore() = default;

    virtual bool is_trusted_identity(const std::string& address,
                                     const uint8_t* identity_key,
                                     IdentityDirection direction) = 0;

    virtual int save_identity(const std::string& address,
                              const uint8_t* identity_key) = 0;

    virtual int get_identity_key_pair(uint8_t* identity_public,
                                     uint8_t* identity_private) = 0;

    virtual int get_local_registration_id() const = 0;

    virtual int get_remote_registration_id(const std::string& address) const = 0;
};

class InMemoryIdentityStore : public IIdentityStore {
public:
    InMemoryIdentityStore();

    bool is_trusted_identity(const std::string& address,
                             const uint8_t* identity_key,
                             IdentityDirection direction) override;

    int save_identity(const std::string& address,
                      const uint8_t* identity_key) override;

    int get_identity_key_pair(uint8_t* identity_public,
                              uint8_t* identity_private) override;

    int get_local_registration_id() const override;
    int get_remote_registration_id(const std::string& address) const override;

    int set_local_registration_id(uint32_t reg_id);
    int set_remote_registration_id(const std::string& address, uint32_t reg_id);

    int save_identity_with_trust(const std::string& address,
                                const uint8_t* identity_key);

private:
    struct IdentityRecord {
        secure::SecureBuffer public_key;
        secure::SecureBuffer private_key;
    };

    IdentityRecord own_identity_;
    uint32_t local_registration_id_;
    std::unordered_map<std::string, secure::SecureBuffer> trusted_identities_;
    std::unordered_map<std::string, uint32_t> remote_registration_ids_;
};

} // namespace protocol
} // namespace enchant

#endif
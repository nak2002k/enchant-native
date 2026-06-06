#include "protocol/identity_store.hpp"
#include "primitives/ed25519.hpp"
#include "primitives/x25519.hpp"
#include "secure/buffer.hpp"
#include "enchant/protocol/constants.hpp"
#include <sodium.h>
#include <cstring>

namespace enchant {
namespace protocol {

InMemoryIdentityStore::InMemoryIdentityStore()
    : own_identity_{secure::SecureBuffer(32), secure::SecureBuffer(64)},
      local_registration_id_(0) {
    primitives::ed25519_keypair(own_identity_.public_key.data(), own_identity_.private_key.data());
}

bool InMemoryIdentityStore::is_trusted_identity(const std::string& address,
                                                const uint8_t* identity_key,
                                                IdentityDirection direction) {
    if (!identity_key) return false;

    auto it = trusted_identities_.find(address);
    if (it == trusted_identities_.end()) {
        return true;
    }

    bool match = sodium_memcmp(it->second.data(), identity_key, 32) == 0;
    return match;
}

int InMemoryIdentityStore::save_identity(const std::string& address,
                                          const uint8_t* identity_key) {
    if (!identity_key) return ENCHANT_ERROR_NULL_POINTER;

    trusted_identities_[address] = secure::SecureBuffer(identity_key, 32);
    return ENCHANT_SUCCESS;
}

int InMemoryIdentityStore::get_identity_key_pair(uint8_t* identity_public,
                                                  uint8_t* identity_private) {
    if (!identity_public || !identity_private) return ENCHANT_ERROR_NULL_POINTER;

    if (own_identity_.public_key.empty() || own_identity_.private_key.empty()) {
        return ENCHANT_ERROR_INTERNAL;
    }

    memcpy(identity_public, own_identity_.public_key.data(), 32);
    memcpy(identity_private, own_identity_.private_key.data(), 64);
    return ENCHANT_SUCCESS;
}

int InMemoryIdentityStore::get_local_registration_id() const {
    return static_cast<int>(local_registration_id_);
}

int InMemoryIdentityStore::get_remote_registration_id(const std::string& address) const {
    auto it = remote_registration_ids_.find(address);
    if (it == remote_registration_ids_.end()) {
        return 0;
    }
    return static_cast<int>(it->second);
}

int InMemoryIdentityStore::set_local_registration_id(uint32_t reg_id) {
    if (!is_valid_registration_id(reg_id)) {
        return ENCHANT_ERROR_INVALID_REGISTRATION_ID;
    }
    local_registration_id_ = reg_id;
    return ENCHANT_SUCCESS;
}

int InMemoryIdentityStore::set_remote_registration_id(const std::string& address, uint32_t reg_id) {
    if (!is_valid_registration_id(reg_id)) {
        return ENCHANT_ERROR_INVALID_REGISTRATION_ID;
    }
    remote_registration_ids_[address] = reg_id;
    return ENCHANT_SUCCESS;
}

int InMemoryIdentityStore::save_identity_with_trust(const std::string& address,
                                                      const uint8_t* identity_key) {
    if (!identity_key) return ENCHANT_ERROR_NULL_POINTER;
    trusted_identities_[address] = secure::SecureBuffer(identity_key, 32);
    return ENCHANT_SUCCESS;
}

} // namespace protocol
} // namespace enchant
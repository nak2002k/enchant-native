#include "enchant/i_identity_store.hpp"
#include <sodium.h>
#include <cstring>

namespace enchant {
namespace protocol {

InMemoryIdentityKeyStore::InMemoryIdentityKeyStore()
    : own_identity_(), local_registration_id_(0) {
    own_identity_.public_key = secure::SecureBuffer(32);
    own_identity_.private_key = secure::SecureBuffer(32);
}

bool InMemoryIdentityKeyStore::save_identity(const EnchantAddress& address,
                                            const IdentityKey& identity_key) {
    auto key_copy = identity_key.public_key.clone();
    identities_[address.name + ":" + std::to_string(address.device_id)] = std::move(key_copy);
    return true;
}

bool InMemoryIdentityKeyStore::set_trust(const EnchantAddress& address, bool trusted) {
    auto key = address.name + ":" + std::to_string(address.device_id);
    auto it = identities_.find(key);
    if (it == identities_.end()) {
        return false;
    }
    if (trusted) {
        trusted_identities_[key] = it->second.clone();
    } else {
        trusted_identities_.erase(key);
    }
    return true;
}

bool InMemoryIdentityKeyStore::is_trusted_identity(const EnchantAddress& address,
                                                  const IdentityKey& identity_key,
                                                  Direction direction) {
    (void)direction;
    auto it = trusted_identities_.find(address.name + ":" + std::to_string(address.device_id));
    if (it == trusted_identities_.end()) {
        return true;
    }
    return sodium_memcmp(it->second.data(), identity_key.data(), 32) == 0;
}

std::optional<IdentityKey> InMemoryIdentityKeyStore::get_identity(const EnchantAddress& address) {
    auto it = identities_.find(address.name + ":" + std::to_string(address.device_id));
    if (it == identities_.end()) {
        return std::nullopt;
    }
    return IdentityKey(it->second);
}

std::vector<EnchantAddress> InMemoryIdentityKeyStore::get_all_identities() {
    std::vector<EnchantAddress> result;
    for (const auto& entry : identities_) {
        size_t sep_pos = entry.first.find(':');
        if (sep_pos != std::string::npos) {
            EnchantAddress addr;
            addr.name = entry.first.substr(0, sep_pos);
            addr.device_id = std::stoul(entry.first.substr(sep_pos + 1));
            result.push_back(addr);
        }
    }
    return result;
}

bool InMemoryIdentityKeyStore::delete_identity(const EnchantAddress& address) {
    auto key = address.name + ":" + std::to_string(address.device_id);
    auto it = identities_.find(key);
    if (it != identities_.end()) {
        identities_.erase(it);
        trusted_identities_.erase(key);
        return true;
    }
    return false;
}

size_t InMemoryIdentityKeyStore::get_identity_size() {
    return identities_.size();
}

int InMemoryIdentityKeyStore::get_identity_key_pair(uint8_t* identity_public,
                                                    uint8_t* identity_private) {
    if (!identity_public || !identity_private) return ENCHANT_ERROR_NULL_POINTER;

    if (own_identity_.public_key.empty() || own_identity_.private_key.empty()) {
        return ENCHANT_ERROR_INTERNAL;
    }

    memcpy(identity_public, own_identity_.public_key.data(), 32);
    memcpy(identity_private, own_identity_.private_key.data(), 32);
    return ENCHANT_SUCCESS;
}

int InMemoryIdentityKeyStore::get_local_registration_id() const {
    return static_cast<int>(local_registration_id_);
}

int InMemoryIdentityKeyStore::get_remote_registration_id(const std::string& address) const {
    auto it = remote_registration_ids_.find(address);
    if (it == remote_registration_ids_.end()) {
        return 0;
    }
    return static_cast<int>(it->second);
}

int InMemoryIdentityKeyStore::set_local_registration_id(uint32_t reg_id) {
    local_registration_id_ = reg_id;
    return ENCHANT_SUCCESS;
}

bool InMemoryIdentityKeyStore::save_identity_with_trust(const EnchantAddress& address,
                                                        const IdentityKey& identity_key) {
    auto key_copy = identity_key.public_key.clone();
    identities_[address.name + ":" + std::to_string(address.device_id)] = key_copy.clone();
    trusted_identities_[address.name + ":" + std::to_string(address.device_id)] = std::move(key_copy);
    return true;
}

std::optional<IdentityKey> InMemoryIdentityKeyStore::get_trusted_identity(const EnchantAddress& address) {
    auto it = trusted_identities_.find(address.name + ":" + std::to_string(address.device_id));
    if (it == trusted_identities_.end()) {
        return std::nullopt;
    }
    return IdentityKey(it->second);
}

IdentityChangeResult InMemoryIdentityKeyStore::detect_identity_change(const EnchantAddress& address,
                                                                     const IdentityKey& new_key) {
    IdentityChangeResult result;
    result.changed = false;
    result.old_key = std::nullopt;
    result.new_key = std::nullopt;

    auto it = trusted_identities_.find(address.name + ":" + std::to_string(address.device_id));
    if (it != trusted_identities_.end()) {
        if (sodium_memcmp(it->second.data(), new_key.data(), 32) != 0) {
            result.old_key.emplace(it->second);
            result.new_key.emplace(new_key.public_key);
            result.changed = true;
        }
    }

    return result;
}

} // namespace protocol
} // namespace enchant
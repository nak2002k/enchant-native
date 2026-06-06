#include "enchant/i_session_store.hpp"
#include "enchant/protocol/session_record.hpp"
#include "protocol/address.hpp"
#include <cstring>
#include <sodium.h>

namespace enchant {
namespace protocol {

InMemorySessionStore::InMemorySessionStore() {}

InMemorySessionStore::~InMemorySessionStore() {}

std::string InMemorySessionStore::make_session_key(const EnchantAddress& address) const {
    return address.name + ":" + std::to_string(address.device_id);
}

bool InMemorySessionStore::store_session(const EnchantAddress& address, const SessionRecord& record) {
    std::string key = make_session_key(address);
    SessionData& data = sessions_[key];
    data.record = record;
    data.archived = false;
    return true;
}

std::optional<SessionRecord> InMemorySessionStore::load_session(const EnchantAddress& address) {
    std::string key = make_session_key(address);
    auto it = sessions_.find(key);
    if (it == sessions_.end()) {
        return std::nullopt;
    }
    if (it->second.archived) {
        return std::nullopt;
    }
    return it->second.record;
}

bool InMemorySessionStore::contains_session(const EnchantAddress& address) {
    std::string key = make_session_key(address);
    auto it = sessions_.find(key);
    if (it == sessions_.end()) {
        return false;
    }
    return !it->second.archived;
}

std::vector<EnchantAddress> InMemorySessionStore::get_all_addresses() {
    std::vector<EnchantAddress> result;
    for (const auto& entry : sessions_) {
        if (entry.second.archived) {
            continue;
        }
        size_t colon_pos = entry.first.find(':');
        if (colon_pos != std::string::npos) {
            std::string name = entry.first.substr(0, colon_pos);
            uint32_t device_id = std::stoul(entry.first.substr(colon_pos + 1));
            result.emplace_back(name, device_id);
        }
    }
    return result;
}

bool InMemorySessionStore::delete_session(const EnchantAddress& address) {
    std::string key = make_session_key(address);
    auto it = sessions_.find(key);
    if (it != sessions_.end()) {
        it->second.record.zero();
        sessions_.erase(it);
        return true;
    }
    return false;
}

bool InMemorySessionStore::archive_session(const EnchantAddress& address) {
    std::string key = make_session_key(address);
    auto it = sessions_.find(key);
    if (it == sessions_.end()) {
        return false;
    }
    it->second.archived = true;
    return true;
}

bool InMemorySessionStore::archive_sibling_sessions(const EnchantAddress& address) {
    std::string prefix = address.name + ":";
    bool any_archived = false;
    for (auto& entry : sessions_) {
        if (entry.first.substr(0, prefix.size()) == prefix) {
            uint32_t device_id = std::stoul(entry.first.substr(prefix.size()));
            if (device_id != address.device_id) {
                entry.second.archived = true;
                any_archived = true;
            }
        }
    }
    return any_archived;
}

size_t InMemorySessionStore::get_session_count() {
    size_t count = 0;
    for (const auto& entry : sessions_) {
        if (!entry.second.archived) {
            count++;
        }
    }
    return count;
}

void InMemorySessionStore::set_session_version(const EnchantAddress& address, uint32_t version) {
    std::string key = make_session_key(address);
    sessions_[key].version = version;
}

uint32_t InMemorySessionStore::get_session_version(const EnchantAddress& address) {
    std::string key = make_session_key(address);
    auto it = sessions_.find(key);
    if (it == sessions_.end()) {
        return 0;
    }
    return it->second.version;
}

} // namespace protocol
} // namespace enchant
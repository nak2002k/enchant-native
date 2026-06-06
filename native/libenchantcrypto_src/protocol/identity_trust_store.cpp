#include "protocol/identity_trust_store.hpp"
#include <sodium.h>
#include <cstring>

namespace enchant {
namespace protocol {

IdentityRecord::IdentityRecord()
    : trust_level(TrustLevel::UNTRUSTED),
      first_seen_ms(0),
      last_seen_ms(0),
      registration_id(0) {}

IdentityRecord::IdentityRecord(const uint8_t* key, size_t key_len, uint32_t reg_id)
    : public_key(key, key_len),
      trust_level(TrustLevel::UNTRUSTED),
      first_seen_ms(0),
      last_seen_ms(0),
      registration_id(reg_id) {
    auto now = std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::system_clock::now().time_since_epoch()).count();
    first_seen_ms = static_cast<uint64_t>(now);
    last_seen_ms = first_seen_ms;
}

void IdentityRecord::zero() {
    public_key.zero();
    trust_level = TrustLevel::UNTRUSTED;
    first_seen_ms = 0;
    last_seen_ms = 0;
    registration_id = 0;
}

InMemoryIdentityTrustStore::InMemoryIdentityTrustStore() {}

TrustLevel InMemoryIdentityTrustStore::get_trust_level(const std::string& address) const {
    auto it = identities_.find(address);
    if (it == identities_.end() || !it->second.has_record) {
        return TrustLevel::UNTRUSTED;
    }
    return it->second.record.trust_level;
}

void InMemoryIdentityTrustStore::set_trust_level(const std::string& address, TrustLevel level) {
    auto& entry = identities_[address];
    if (!entry.has_record) {
        entry.record = IdentityRecord();
        entry.has_record = true;
    }
    entry.record.trust_level = level;
}

bool InMemoryIdentityTrustStore::is_trusted(const std::string& address,
                                             const uint8_t* identity_key,
                                             size_t key_len) const {
    if (!identity_key || key_len != 32) return false;

    TrustLevel level = get_trust_level(address);
    if (level == TrustLevel::BLOCKED) return false;

    auto it = identities_.find(address);
    if (it == identities_.end() || !it->second.has_record) {
        return true;
    }

    if (it->second.record.public_key.size() != 32) return false;

    return sodium_memcmp(it->second.record.public_key.data(), identity_key, 32) == 0;
}

bool InMemoryIdentityTrustStore::save_identity(const std::string& address,
                                                const uint8_t* identity_key,
                                                size_t key_len,
                                                uint32_t registration_id) {
    if (!identity_key || key_len != 32) return false;

    auto now = std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::system_clock::now().time_since_epoch()).count();

    auto& entry = identities_[address];
    if (!entry.has_record) {
        entry.record = IdentityRecord(identity_key, key_len, registration_id);
        entry.has_record = true;
        entry.record.trust_level = TrustLevel::TRUSTED;
    } else {
        entry.record.last_seen_ms = static_cast<uint64_t>(now);
    }

    return true;
}

std::optional<IdentityRecord> InMemoryIdentityTrustStore::get_identity(
    const std::string& address) const {
    auto it = identities_.find(address);
    if (it == identities_.end() || !it->second.has_record) {
        return std::nullopt;
    }
    const auto& rec = it->second.record;
    IdentityRecord result;
    result.public_key = rec.public_key.clone();
    result.trust_level = rec.trust_level;
    result.first_seen_ms = rec.first_seen_ms;
    result.last_seen_ms = rec.last_seen_ms;
    result.registration_id = rec.registration_id;
    return result;
}

std::vector<std::string> InMemoryIdentityTrustStore::get_all_addresses() const {
    std::vector<std::string> addresses;
    addresses.reserve(identities_.size());
    for (const auto& [addr, _] : identities_) {
        addresses.push_back(addr);
    }
    return addresses;
}

IdentityChangeInfo InMemoryIdentityTrustStore::detect_identity_change(
    const std::string& address,
    const uint8_t* new_key,
    size_t key_len) const {
    IdentityChangeInfo info;
    info.address = address;
    info.detected_at_ms = 0;
    info.is_key_change = false;

    if (!new_key || key_len != 32) return info;

    auto now = std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::system_clock::now().time_since_epoch()).count();
    info.detected_at_ms = static_cast<uint64_t>(now);

    auto it = identities_.find(address);
    if (it == identities_.end() || !it->second.has_record) {
        return info;
    }

    const auto& record = it->second.record;
    if (record.public_key.size() != 32) return info;

    if (sodium_memcmp(record.public_key.data(), new_key, 32) != 0) {
        info.is_key_change = true;
        info.old_key = record.public_key.clone();
        info.new_key = secure::SecureBuffer(new_key, key_len);
    }

    return info;
}

bool InMemoryIdentityTrustStore::delete_identity(const std::string& address) {
    return identities_.erase(address) > 0;
}

size_t InMemoryIdentityTrustStore::get_identity_count() const {
    return identities_.size();
}

int InMemoryIdentityTrustStore::serialize(std::vector<uint8_t>& output) const {
    uint32_t count = static_cast<uint32_t>(identities_.size());
    output.clear();
    output.resize(sizeof(uint32_t));

    memcpy(output.data(), &count, sizeof(uint32_t));
    size_t offset = sizeof(uint32_t);

    for (const auto& [address, entry] : identities_) {
        uint32_t addr_len = static_cast<uint32_t>(address.size());
        output.resize(offset + sizeof(uint32_t) + addr_len + sizeof(uint8_t) +
                       sizeof(uint64_t) * 2 + sizeof(uint32_t) + 32);
        memcpy(output.data() + offset, &addr_len, sizeof(uint32_t));
        offset += sizeof(uint32_t);
        memcpy(output.data() + offset, address.data(), addr_len);
        offset += addr_len;

        uint8_t trust = static_cast<uint8_t>(entry.record.trust_level);
        memcpy(output.data() + offset, &trust, sizeof(uint8_t));
        offset += sizeof(uint8_t);

        memcpy(output.data() + offset, &entry.record.first_seen_ms, sizeof(uint64_t));
        offset += sizeof(uint64_t);
        memcpy(output.data() + offset, &entry.record.last_seen_ms, sizeof(uint64_t));
        offset += sizeof(uint64_t);
        memcpy(output.data() + offset, &entry.record.registration_id, sizeof(uint32_t));
        offset += sizeof(uint32_t);

        if (entry.has_record && entry.record.public_key.size() == 32) {
            memcpy(output.data() + offset, entry.record.public_key.data(), 32);
        } else {
            memset(output.data() + offset, 0, 32);
        }
        offset += 32;
    }

    output.resize(offset);
    return ENCHANT_SUCCESS;
}

int InMemoryIdentityTrustStore::deserialize(const uint8_t* input, size_t input_len) {
    if (!input || input_len < sizeof(uint32_t)) return ENCHANT_ERROR_NULL_POINTER;

    size_t offset = 0;
    uint32_t count;
    memcpy(&count, input + offset, sizeof(uint32_t));
    offset += sizeof(uint32_t);

    identities_.clear();

    for (uint32_t i = 0; i < count; i++) {
        if (offset + sizeof(uint32_t) > input_len) return ENCHANT_ERROR_CIPHERTEXT_TOO_SHORT;
        uint32_t addr_len;
        memcpy(&addr_len, input + offset, sizeof(uint32_t));
        offset += sizeof(uint32_t);

        if (offset + addr_len > input_len) return ENCHANT_ERROR_CIPHERTEXT_TOO_SHORT;
        std::string address(reinterpret_cast<const char*>(input + offset), addr_len);
        offset += addr_len;

        if (offset + 1 + 8 + 8 + 4 + 32 > input_len) return ENCHANT_ERROR_CIPHERTEXT_TOO_SHORT;
        uint8_t trust;
        memcpy(&trust, input + offset, sizeof(uint8_t));
        offset += sizeof(uint8_t);

        IdentityRecord record;
        record.trust_level = static_cast<TrustLevel>(trust);
        memcpy(&record.first_seen_ms, input + offset, sizeof(uint64_t));
        offset += sizeof(uint64_t);
        memcpy(&record.last_seen_ms, input + offset, sizeof(uint64_t));
        offset += sizeof(uint64_t);
        memcpy(&record.registration_id, input + offset, sizeof(uint32_t));
        offset += sizeof(uint32_t);

        record.public_key = secure::SecureBuffer(input + offset, 32);
        offset += 32;

        IdentityEntry entry;
        entry.record = std::move(record);
        entry.has_record = true;
        identities_[address] = std::move(entry);
    }

    return ENCHANT_SUCCESS;
}

} // namespace protocol
} // namespace enchant

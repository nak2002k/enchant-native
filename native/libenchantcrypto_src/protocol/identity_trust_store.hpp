#ifndef ENCHANT_PROTOCOL_IDENTITY_TRUST_STORE_HPP
#define ENCHANT_PROTOCOL_IDENTITY_TRUST_STORE_HPP

#include <cstdint>
#include <cstddef>
#include <string>
#include <vector>
#include <optional>
#include <unordered_map>
#include <functional>
#include "enchant/error.h"
#include "secure/buffer.hpp"

namespace enchant {
namespace protocol {

enum class TrustLevel : uint8_t {
    UNTRUSTED = 0,
    TRUSTED = 1,
    BLOCKED = 2
};

struct IdentityRecord {
    secure::SecureBuffer public_key;
    TrustLevel trust_level;
    uint64_t first_seen_ms;
    uint64_t last_seen_ms;
    uint32_t registration_id;

    IdentityRecord();
    IdentityRecord(const uint8_t* key, size_t key_len, uint32_t reg_id);
    void zero();
};

struct IdentityChangeInfo {
    std::string address;
    secure::SecureBuffer old_key;
    secure::SecureBuffer new_key;
    uint64_t detected_at_ms;
    bool is_key_change;
};

class IIdentityTrustStore {
public:
    virtual ~IIdentityTrustStore() = default;

    virtual TrustLevel get_trust_level(const std::string& address) const = 0;
    virtual void set_trust_level(const std::string& address, TrustLevel level) = 0;

    virtual bool is_trusted(const std::string& address, const uint8_t* identity_key,
                            size_t key_len) const = 0;

    virtual bool save_identity(const std::string& address, const uint8_t* identity_key,
                               size_t key_len, uint32_t registration_id) = 0;

    virtual std::optional<IdentityRecord> get_identity(const std::string& address) const = 0;

    virtual std::vector<std::string> get_all_addresses() const = 0;

    virtual IdentityChangeInfo detect_identity_change(const std::string& address,
                                                      const uint8_t* new_key,
                                                      size_t key_len) const = 0;

    virtual bool delete_identity(const std::string& address) = 0;

    virtual size_t get_identity_count() const = 0;

    virtual int serialize(std::vector<uint8_t>& output) const = 0;
    virtual int deserialize(const uint8_t* input, size_t input_len) = 0;
};

class InMemoryIdentityTrustStore : public IIdentityTrustStore {
public:
    InMemoryIdentityTrustStore();

    TrustLevel get_trust_level(const std::string& address) const override;
    void set_trust_level(const std::string& address, TrustLevel level) override;

    bool is_trusted(const std::string& address, const uint8_t* identity_key,
                    size_t key_len) const override;

    bool save_identity(const std::string& address, const uint8_t* identity_key,
                       size_t key_len, uint32_t registration_id) override;

    std::optional<IdentityRecord> get_identity(const std::string& address) const override;

    std::vector<std::string> get_all_addresses() const override;

    IdentityChangeInfo detect_identity_change(const std::string& address,
                                              const uint8_t* new_key,
                                              size_t key_len) const override;

    bool delete_identity(const std::string& address) override;

    size_t get_identity_count() const override;

    int serialize(std::vector<uint8_t>& output) const override;
    int deserialize(const uint8_t* input, size_t input_len) override;

private:
    struct IdentityEntry {
        IdentityRecord record;
        bool has_record;
    };

    std::unordered_map<std::string, IdentityEntry> identities_;
};

} // namespace protocol
} // namespace enchant

#endif

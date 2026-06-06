#ifndef ENCHANT_I_SESSION_STORE_HPP
#define ENCHANT_I_SESSION_STORE_HPP

#include "enchant/error.h"
#include "enchant/i_identity_store.hpp"
#include "enchant/protocol/session_record.hpp"
#include <cstddef>
#include <optional>
#include <string>
#include <unordered_map>
#include <vector>

namespace enchant {
namespace protocol {

class ISessionStore {
public:
    virtual ~ISessionStore() = default;

    virtual bool store_session(const EnchantAddress& address, const SessionRecord& record) = 0;
    virtual std::optional<SessionRecord> load_session(const EnchantAddress& address) = 0;
    virtual bool contains_session(const EnchantAddress& address) = 0;
    virtual std::vector<EnchantAddress> get_all_addresses() = 0;
    virtual bool delete_session(const EnchantAddress& address) = 0;
    virtual bool archive_session(const EnchantAddress& address) = 0;
    virtual bool archive_sibling_sessions(const EnchantAddress& address) = 0;
    virtual size_t get_session_count() = 0;
};

class InMemorySessionStore : public ISessionStore {
public:
    InMemorySessionStore();
    ~InMemorySessionStore() override;

    bool store_session(const EnchantAddress& address, const SessionRecord& record) override;
    std::optional<SessionRecord> load_session(const EnchantAddress& address) override;
    bool contains_session(const EnchantAddress& address) override;
    std::vector<EnchantAddress> get_all_addresses() override;
    bool delete_session(const EnchantAddress& address) override;
    bool archive_session(const EnchantAddress& address) override;
    bool archive_sibling_sessions(const EnchantAddress& address) override;
    size_t get_session_count() override;

    void set_session_version(const EnchantAddress& address, uint32_t version);
    uint32_t get_session_version(const EnchantAddress& address);

private:
    std::string make_session_key(const EnchantAddress& address) const;

    struct SessionData {
        SessionRecord record;
        uint32_t version;
        bool archived;
    };

    std::unordered_map<std::string, SessionData> sessions_;
};

} // namespace protocol
} // namespace enchant

#endif
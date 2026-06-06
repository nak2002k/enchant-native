#ifndef ENCHANT_PROTOCOL_SESSION_ARCHIVE_HPP
#define ENCHANT_PROTOCOL_SESSION_ARCHIVE_HPP

#include <cstdint>
#include <cstddef>
#include <string>
#include <vector>
#include <memory>
#include <functional>
#include "enchant/error.h"
#include "enchant/i_identity_store.hpp"
#include "enchant/i_session_store.hpp"
#include "protocol/identity_trust_store.hpp"

namespace enchant {
namespace protocol {

struct SessionArchiveEvent {
    enum class Type : uint8_t {
        IDENTITY_KEY_CHANGED = 0,
        SESSION_ARCHIVED = 1,
        SESSION_PROMOTED = 2,
        NEW_SESSION_CREATED = 3,
        KEY_BUNDLE_RECEIVED = 4
    };

    Type type;
    std::string address;
    uint64_t timestamp_ms;
    std::string details;

    SessionArchiveEvent() : type(Type::NEW_SESSION_CREATED), timestamp_ms(0) {}
};

using SessionArchiveCallback = std::function<void(const SessionArchiveEvent&)>;

class SessionArchiveManager {
public:
    SessionArchiveManager(
        std::shared_ptr<IIdentityTrustStore> trust_store,
        std::shared_ptr<ISessionStore> session_store
    );

    void set_event_callback(SessionArchiveCallback callback);

    struct ProcessKeyBundleResult {
        bool identity_changed;
        bool session_archived;
        bool previous_session_promoted;
        TrustLevel trust_level;
    };

    ProcessKeyBundleResult process_key_bundle(
        const std::string& address,
        const uint8_t* new_identity_key,
        size_t key_len,
        uint32_t registration_id
    );

    int archive_all_sessions_for_address(const std::string& address);

    int archive_sessions_on_identity_rotation(
        const std::string& address,
        const uint8_t* new_identity_key,
        size_t key_len
    );

    int promote_previous_session(const std::string& address);

    int handle_incoming_session(
        const std::string& address,
        const SessionRecord& new_session
    );

    size_t get_archive_size(const std::string& address) const;

    bool has_current_session(const std::string& address) const;

    std::vector<SessionArchiveEvent> get_event_history(const std::string& address) const;

private:
    void emit_event(const SessionArchiveEvent& event);

    std::shared_ptr<IIdentityTrustStore> trust_store_;
    std::shared_ptr<ISessionStore> session_store_;
    SessionArchiveCallback callback_;
    std::unordered_map<std::string, std::vector<SessionArchiveEvent>> event_history_;
    std::unordered_map<std::string, uint64_t> session_versions_;
};

} // namespace protocol
} // namespace enchant

#endif

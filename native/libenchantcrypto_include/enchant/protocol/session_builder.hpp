#ifndef ENCHANT_PROTOCOL_SESSION_BUILDER_HPP
#define ENCHANT_PROTOCOL_SESSION_BUILDER_HPP

#include "protocol/handshake.hpp"
#include "session_state.hpp"
#include "session_record.hpp"
#include "enchant/i_session_store.hpp"
#include "enchant/i_identity_store.hpp"
#include "protocol/x3dh.hpp"
#include <cstdint>
#include <optional>
#include <memory>

namespace enchant {
namespace protocol {

class SessionBuilder {
public:
    SessionBuilder(ISessionStore& session_store,
                   IIdentityKeyStore& identity_store);
    ~SessionBuilder();

    int process_prekey_bundle(const KeyBundle& bundle,
                              const EnchantAddress& address);

    std::optional<SessionRecord> session(const EnchantAddress& address);

    void set_trust(const EnchantAddress& address, bool trusted);

private:
    ISessionStore& session_store_;
    IIdentityKeyStore& identity_store_;
};

int merge_session(SessionRecord& current, const SessionRecord& previous);

} // namespace protocol
} // namespace enchant

#endif
#ifndef ENCHANT_PROTOCOL_SESSION_HPP
#define ENCHANT_PROTOCOL_SESSION_HPP

#include "session_state.hpp"
#include "session_record.hpp"
#include "protocol/address.hpp"
#include <cstdint>
#include <cstddef>
#include <vector>
#include <memory>
#include <string>

namespace enchant {
namespace protocol {

class Session {
public:
    enum class DecryptResult : uint8_t {
        CURRENT_SESSION = 0,
        ARCHIVED_SESSION = 1,
        NO_MATCHING_SESSION = 2
    };

    struct DecryptedMessage {
        std::vector<uint8_t> plaintext;
        DecryptResult source;
        size_t archived_session_index;
    };

    Session();
    explicit Session(SessionRecord record);
    ~Session();

    Session(const Session&) = delete;
    Session& operator=(const Session&) = delete;
    Session(Session&& other) noexcept;
    Session& operator=(Session&& other) noexcept;

    bool has_current_session() const;
    SessionState* current_session();
    const SessionState* current_session() const;

    int load_state(SessionRecord record);
    SessionRecord snapshot() const;

    int encrypt(const uint8_t* plaintext, size_t plaintext_len,
                std::vector<uint8_t>& envelope);

    DecryptedMessage try_decrypt(const std::vector<uint8_t>& envelope);

    DecryptResult try_decrypt_with_state(const std::vector<uint8_t>& envelope,
                                         SessionState& state,
                                         std::vector<uint8_t>& out_plaintext);

    void archive_current_session();
    bool promote_archived_session(size_t index);
    size_t archived_session_count() const;

    void zero();

private:
    SessionRecord record_;
};

} // namespace protocol
} // namespace enchant

#endif

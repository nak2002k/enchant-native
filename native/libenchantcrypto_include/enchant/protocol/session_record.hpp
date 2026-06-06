#ifndef ENCHANT_PROTOCOL_SESSION_RECORD_HPP
#define ENCHANT_PROTOCOL_SESSION_RECORD_HPP

#include "session_state.hpp"
#include "protocol/address.hpp"
#include <cstdint>
#include <cstddef>
#include <vector>
#include <optional>
#include <memory>

namespace enchant {
namespace protocol {

class SessionRecord {
public:
    SessionRecord();
    ~SessionRecord();

    SessionRecord(const SessionRecord& other);
    SessionRecord& operator=(const SessionRecord& other);
    SessionRecord(SessionRecord&& other) noexcept;
    SessionRecord& operator=(SessionRecord&& other) noexcept;

    bool has_current_session() const;
    SessionState* get_current_session_state();
    const SessionState* get_current_session_state() const;

    std::vector<SessionState>& get_previous_session_states();
    const std::vector<SessionState>& get_previous_session_states() const;

    void promote_previous_to_current();
    size_t promote_archived_session(size_t index);
    void archive_current_session();
    size_t archived_session_count() const { return previous_sessions_.size(); }

    void set_current_session_state(SessionState state);
    void add_previous_session_state(SessionState state);

    void clear();

    size_t serialized_size() const;
    int serialize(uint8_t* output, size_t* output_len) const;
    int deserialize(const uint8_t* input, size_t input_len);

    void zero();

private:
    std::unique_ptr<SessionState> current_session_;
    std::vector<SessionState> previous_sessions_;
    bool is_archived_;
};

} // namespace protocol
} // namespace enchant

#endif
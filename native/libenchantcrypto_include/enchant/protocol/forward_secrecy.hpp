#ifndef ENCHANT_PROTOCOL_FORWARD_SECRECY_HPP
#define ENCHANT_PROTOCOL_FORWARD_SECRECY_HPP

#include "session_state.hpp"
#include "veil/envelope_state.hpp"
#include <cstdint>
#include <cstddef>

namespace enchant {
namespace protocol {

int delete_message_key(SessionState& state, const uint8_t* their_ephemeral,
                       uint32_t counter);

int clear_consumed_message_keys(SessionState& state);

int evict_oldest_keys(SessionState& state, size_t keep_count);

size_t skipped_key_count(const SessionState& state);

} // namespace protocol
} // namespace enchant

#endif

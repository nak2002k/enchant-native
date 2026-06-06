#include "enchant/protocol/forward_secrecy.hpp"
#include "enchant/protocol/constants.hpp"
#include "veil/envelope_state.hpp"
#include <algorithm>

namespace enchant {
namespace protocol {

int delete_message_key(SessionState& state, const uint8_t* their_ephemeral,
                       uint32_t counter) {
    if (!state.is_initialized()) {
        return ENCHANT_ERROR_SESSION_STATE_INVALID;
    }
    return state.ratchet().delete_skipped_key(their_ephemeral, counter);
}

int clear_consumed_message_keys(SessionState& state) {
    if (!state.is_initialized()) {
        return ENCHANT_ERROR_SESSION_STATE_INVALID;
    }
    return state.ratchet().clear_all_skipped_keys();
}

int evict_oldest_keys(SessionState& state, size_t keep_count) {
    if (!state.is_initialized()) {
        return ENCHANT_ERROR_SESSION_STATE_INVALID;
    }
    return state.ratchet().evict_oldest_skipped_keys(keep_count);
}

size_t skipped_key_count(const SessionState& state) {
    if (!state.is_initialized()) {
        return 0;
    }
    return state.ratchet().skipped_key_count();
}

} // namespace protocol
} // namespace enchant

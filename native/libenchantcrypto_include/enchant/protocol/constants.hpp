#ifndef ENCHANT_PROTOCOL_CONSTANTS_HPP
#define ENCHANT_PROTOCOL_CONSTANTS_HPP

#include <cstddef>
#include <cstdint>

namespace enchant {
namespace protocol {

// Session archival and ratchet limits
constexpr size_t MAX_ARCHIVED_STATES = 40;
constexpr size_t MAX_RECEIVER_CHAINS = 5;
constexpr size_t MAX_MESSAGE_KEYS_PER_CHAIN = 2000;

// Registration ID validation
constexpr uint32_t VALID_REGISTRATION_ID_MASK = 0x3FFF;
constexpr uint32_t MAX_REGISTRATION_ID = 16383;

// Fingerprint generation
constexpr uint32_t DEFAULT_FINGERPRINT_ITERATIONS = 5200;
constexpr size_t FINGERPRINT_BYTES = 30;
constexpr size_t DISPLAYABLE_FINGERPRINT_DIGITS = 60;
constexpr size_t SCANNABLE_FINGERPRINT_BYTES = 32;
constexpr size_t SCANNABLE_FINGERPRINT_VERSION = 0;

// Identity key sizes
constexpr size_t IDENTITY_KEY_SIZE = 32;
constexpr size_t SIGNATURE_SIZE = 64;
constexpr size_t EPHEMERAL_KEY_SIZE = 32;

// Timeouts
constexpr uint64_t DEFAULT_PREKEY_MESSAGE_TIMEOUT_MS = 30000;
constexpr uint64_t SIGNED_PREKEY_ROTATION_PERIOD_MS = 30ULL * 24 * 60 * 60 * 1000;

inline bool is_valid_registration_id(uint32_t id) {
    return (id & ~VALID_REGISTRATION_ID_MASK) == 0;
}

} // namespace protocol
} // namespace enchant

#endif

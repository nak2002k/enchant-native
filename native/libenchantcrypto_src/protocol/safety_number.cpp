#include "enchant/protocol/safety_number.hpp"
#include "primitives/hash.hpp"
#include <sodium.h>
#include <cstring>

namespace enchant {
namespace protocol {

secure::SecureBuffer generate_safety_number(const IdentityKey& our_identity,
                                   const IdentityKey& their_identity,
                                   const EnchantAddress& address) {
    secure::SecureBuffer combined(32 + 32 + address.name.size() + 4);

    size_t offset = 0;

    memcpy(combined.data() + offset, our_identity.data(), 32);
    offset += 32;

    memcpy(combined.data() + offset, their_identity.data(), 32);
    offset += 32;

    memcpy(combined.data() + offset, address.name.data(), address.name.size());
    offset += address.name.size();

    combined.data()[offset++] = (address.device_id >> 24) & 0xFF;
    combined.data()[offset++] = (address.device_id >> 16) & 0xFF;
    combined.data()[offset++] = (address.device_id >> 8) & 0xFF;
    combined.data()[offset++] = address.device_id & 0xFF;

    uint8_t hash[crypto_hash_sha256_BYTES];
    int rc = crypto_hash_sha256(hash, combined.data(), combined.size());
    if (rc != 0) {
        sodium_memzero(hash, sizeof(hash));
        return secure::SecureBuffer(SAFETY_NUMBER_BYTES);
    }

    secure::SecureBuffer result(SAFETY_NUMBER_BYTES);
    memcpy(result.data(), hash, SAFETY_NUMBER_BYTES);

    sodium_memzero(hash, sizeof(hash));

    return result;
}

bool compare_safety_numbers(const secure::SecureBuffer& sn1, const secure::SecureBuffer& sn2) {
    if (sn1.size() != sn2.size()) {
        return false;
    }
    return sodium_memcmp(sn1.data(), sn2.data(), sn1.size()) == 0;
}

static const char* SAFETY_NUMBER_CHARS = "0123456789";

std::string format_safety_number(const secure::SecureBuffer& safety_number) {
    std::string formatted;
    formatted.reserve(SAFETY_NUMBER_DISPLAY_LEN);

    for (size_t i = 0; i < SAFETY_NUMBER_BYTES; ++i) {
        uint8_t byte = safety_number.data()[i];
        formatted += SAFETY_NUMBER_CHARS[(byte / 10) % 10];
        formatted += SAFETY_NUMBER_CHARS[byte % 10];
    }

    return formatted;
}

} // namespace protocol
} // namespace enchant
#include "primitives/hash.hpp"
#include <sodium.h>

namespace enchant {
namespace primitives {

int sha256(const uint8_t* data, size_t len, uint8_t* hash) {
    if (!hash) return ENCHANT_ERROR_NULL_POINTER;
    if (!data && len > 0) return ENCHANT_ERROR_NULL_POINTER;
    int rc = crypto_hash_sha256(hash, data, len);
    return (rc == 0) ? ENCHANT_SUCCESS : ENCHANT_ERROR_INTERNAL;
}

int sha512(const uint8_t* data, size_t len, uint8_t* hash) {
    if (!hash) return ENCHANT_ERROR_NULL_POINTER;
    if (!data && len > 0) return ENCHANT_ERROR_NULL_POINTER;
    int rc = crypto_hash_sha512(hash, data, len);
    return (rc == 0) ? ENCHANT_SUCCESS : ENCHANT_ERROR_INTERNAL;
}

} // namespace primitives
} // namespace enchant
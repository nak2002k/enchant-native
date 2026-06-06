#include "primitives/constant_time.hpp"
#include <sodium.h>

namespace enchant {
namespace primitives {

bool constant_time_eq(const uint8_t* a, const uint8_t* b, size_t len) {
    if (!a || !b) return false;
    return sodium_memcmp(a, b, len) == 0;
}

bool constant_time_eq_32(const uint8_t* a, const uint8_t* b) {
    if (!a || !b) return false;
    return sodium_memcmp(a, b, 32) == 0;
}

bool constant_time_eq_64(const uint8_t* a, const uint8_t* b) {
    if (!a || !b) return false;
    return sodium_memcmp(a, b, 64) == 0;
}

bool constant_time_is_zero(const uint8_t* data, size_t len) {
    if (!data) return true;
    return sodium_is_zero(data, len) == 1;
}

int constant_time_compare_sig(const uint8_t* sig1, const uint8_t* sig2) {
    if (!sig1 || !sig2) return -1;
    if (sodium_memcmp(sig1, sig2, 64) == 0) {
        return 0;
    }
    return -1;
}

int constant_time_compare_pubkey(const uint8_t* pk1, const uint8_t* pk2) {
    if (!pk1 || !pk2) return -1;
    if (sodium_memcmp(pk1, pk2, 32) == 0) {
        return 0;
    }
    return -1;
}

void constant_time_select(uint8_t* out, const uint8_t* a, const uint8_t* b, uint8_t condition, size_t len) {
    if (!out || !a || !b) return;
    uint8_t mask = -condition;
    for (size_t i = 0; i < len; i++) {
        out[i] = (a[i] & mask) | (b[i] & ~mask);
    }
}

} // namespace primitives
} // namespace enchant

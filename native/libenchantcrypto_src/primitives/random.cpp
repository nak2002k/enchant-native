#include "primitives/random.hpp"
#include <sodium.h>

namespace enchant {
namespace primitives {

int random_bytes(uint8_t* buf, size_t len) {
    if (!buf || len == 0) return ENCHANT_ERROR_NULL_POINTER;
    randombytes_buf(buf, len);
    return ENCHANT_SUCCESS;
}

} // namespace primitives
} // namespace enchant
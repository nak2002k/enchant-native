#ifndef ENCHANT_PRIMITIVES_RANDOM_HPP
#define ENCHANT_PRIMITIVES_RANDOM_HPP

#include <cstdint>
#include <cstddef>
#include "enchant/error.h"

namespace enchant {
namespace primitives {

int random_bytes(uint8_t* buf, size_t len);

} // namespace primitives
} // namespace enchant

#endif
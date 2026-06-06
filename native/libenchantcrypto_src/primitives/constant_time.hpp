#ifndef ENCHANT_PRIMITIVES_CONSTANT_TIME_HPP
#define ENCHANT_PRIMITIVES_CONSTANT_TIME_HPP

#include <cstdint>
#include <cstddef>
#include <sodium.h>

namespace enchant {
namespace primitives {

bool constant_time_eq(const uint8_t* a, const uint8_t* b, size_t len);

bool constant_time_eq_32(const uint8_t* a, const uint8_t* b);

bool constant_time_eq_64(const uint8_t* a, const uint8_t* b);

bool constant_time_is_zero(const uint8_t* data, size_t len);

int constant_time_compare_sig(const uint8_t* sig1, const uint8_t* sig2);

int constant_time_compare_pubkey(const uint8_t* pk1, const uint8_t* pk2);

void constant_time_select(uint8_t* out, const uint8_t* a, const uint8_t* b, uint8_t condition, size_t len);

} // namespace primitives
} // namespace enchant

#endif
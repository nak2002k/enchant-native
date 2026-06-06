#ifndef ENCHANT_PROTO_VARINT_HPP
#define ENCHANT_PROTO_VARINT_HPP

#include <cstdint>
#include <cstddef>
#include "enchant/error.h"

namespace enchant {
namespace proto {

int encode_varint(uint64_t value, uint8_t* output, size_t* output_len);

int decode_varint(const uint8_t* input, size_t input_len,
                  uint64_t* value, size_t* bytes_consumed);

} // namespace proto
} // namespace enchant

#endif
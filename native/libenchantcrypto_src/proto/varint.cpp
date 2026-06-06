#include "proto/varint.hpp"

namespace enchant {
namespace proto {

int encode_varint(uint64_t value, uint8_t* output, size_t* output_len) {
    if (!output || !output_len) return ENCHANT_ERROR_NULL_POINTER;
    size_t pos = 0;
    while (value > 0x7F) {
        if (pos >= *output_len) return ENCHANT_ERROR_BUFFER_TOO_SMALL;
        output[pos++] = (value & 0x7F) | 0x80;
        value >>= 7;
    }
    if (pos >= *output_len) return ENCHANT_ERROR_BUFFER_TOO_SMALL;
    output[pos++] = value & 0x7F;
    *output_len = pos;
    return ENCHANT_SUCCESS;
}

int decode_varint(const uint8_t* input, size_t input_len,
                  uint64_t* value, size_t* bytes_consumed) {
    if (!input || !value || !bytes_consumed) return ENCHANT_ERROR_NULL_POINTER;
    *value = 0;
    *bytes_consumed = 0;
    if (input_len == 0) return ENCHANT_ERROR_INVALID_FORMAT;
    for (size_t i = 0; i < input_len && i < 10; i++) {
        *value |= (static_cast<uint64_t>(input[i] & 0x7F) << (7 * i));
        (*bytes_consumed)++;
        if ((input[i] & 0x80) == 0) return ENCHANT_SUCCESS;
    }
    if (input_len >= 10 && (input[9] & 0x80) != 0) {
        return ENCHANT_ERROR_INVALID_FORMAT;
    }
    return ENCHANT_SUCCESS;
}

} // namespace proto
} // namespace enchant
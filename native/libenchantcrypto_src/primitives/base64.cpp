#include "primitives/base64.hpp"
#include <sodium.h>
#include <cstring>

namespace enchant {
namespace primitives {

int base64_encode(const uint8_t* data, size_t len,
                  char* output, size_t output_len) {
    if (!data || !output) return ENCHANT_ERROR_NULL_POINTER;
    size_t needed = sodium_base64_encoded_len(len, sodium_base64_VARIANT_ORIGINAL);
    if (output_len < needed) return ENCHANT_ERROR_BUFFER_TOO_SMALL;
    char* result = sodium_bin2base64(output, output_len, data, len, sodium_base64_VARIANT_ORIGINAL);
    if (!result) return ENCHANT_ERROR_INTERNAL;
    return ENCHANT_SUCCESS;
}

int base64_decode(const char* input, size_t input_len, uint8_t* output, size_t output_len) {
    if (!input || !output) return ENCHANT_ERROR_NULL_POINTER;
    size_t binary_len = 0;
    const char* end_ptr = nullptr;
    if (sodium_base642bin(output, output_len, input, input_len,
                          nullptr, &binary_len, &end_ptr,
                          sodium_base64_VARIANT_ORIGINAL) != 0) {
        return ENCHANT_ERROR_INVALID_FORMAT;
    }
    if (end_ptr != input + input_len) {
        return ENCHANT_ERROR_INVALID_FORMAT;
    }
    return ENCHANT_SUCCESS;
}

int base64_decode(const char* input, uint8_t* output, size_t output_len) {
    if (!input || !output) return ENCHANT_ERROR_NULL_POINTER;
    return base64_decode(input, strlen(input), output, output_len);
}

} // namespace primitives
} // namespace enchant
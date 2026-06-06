#ifndef ENCHANT_PRIMITIVES_BASE64_HPP
#define ENCHANT_PRIMITIVES_BASE64_HPP

#include <cstdint>
#include <cstddef>
#include "enchant/error.h"

namespace enchant {
namespace primitives {

int base64_encode(const uint8_t* data, size_t len,
                  char* output, size_t output_len);

int base64_decode(const char* input, size_t input_len, uint8_t* output, size_t output_len);

int base64_decode(const char* input, uint8_t* output, size_t output_len);

} // namespace primitives
} // namespace enchant

#endif
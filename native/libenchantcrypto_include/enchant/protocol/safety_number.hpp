#ifndef ENCHANT_SAFETY_NUMBER_HPP
#define ENCHANT_SAFETY_NUMBER_HPP

#include <cstdint>
#include <cstddef>
#include <string>
#include "enchant/i_identity_store.hpp"
#include "secure/buffer.hpp"

namespace enchant {
namespace protocol {

constexpr size_t SAFETY_NUMBER_BYTES = 30;
constexpr size_t SAFETY_NUMBER_DISPLAY_LEN = 60;

secure::SecureBuffer generate_safety_number(const IdentityKey& our_identity,
                                   const IdentityKey& their_identity,
                                   const EnchantAddress& address);

bool compare_safety_numbers(const secure::SecureBuffer& sn1, const secure::SecureBuffer& sn2);

std::string format_safety_number(const secure::SecureBuffer& safety_number);

} // namespace protocol
} // namespace enchant

#endif
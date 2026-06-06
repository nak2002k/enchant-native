#ifndef ENCHANT_PROTOCOL_MISMATCHED_DEVICES_HPP
#define ENCHANT_PROTOCOL_MISMATCHED_DEVICES_HPP

#include <exception>
#include <string>
#include <vector>
#include <cstdint>
#include "enchant/i_identity_store.hpp"

namespace enchant {
namespace protocol {

class MismatchedDevicesException : public std::exception {
public:
    MismatchedDevicesException();
    MismatchedDevicesException(const EnchantAddress& address,
                                const std::vector<uint32_t>& missing,
                                const std::vector<uint32_t>& extra);
    const char* what() const noexcept override;

    const EnchantAddress& get_address() const noexcept { return address_; }
    const std::vector<uint32_t>& get_missing_devices() const noexcept { return missing_devices_; }
    const std::vector<uint32_t>& get_extra_devices() const noexcept { return extra_devices_; }

private:
    EnchantAddress address_;
    std::vector<uint32_t> missing_devices_;
    std::vector<uint32_t> extra_devices_;
    mutable std::string message_;
};

} // namespace protocol
} // namespace enchant

#endif
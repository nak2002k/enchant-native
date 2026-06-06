#ifndef ENCHANT_PROTOCOL_STALE_DEVICES_HPP
#define ENCHANT_PROTOCOL_STALE_DEVICES_HPP

#include <exception>
#include <string>
#include <vector>
#include <cstdint>
#include "enchant/i_identity_store.hpp"

namespace enchant {
namespace protocol {

class StaleDevicesException : public std::exception {
public:
    StaleDevicesException();
    StaleDevicesException(const EnchantAddress& address, const std::vector<uint32_t>& stale);
    const char* what() const noexcept override;

    const EnchantAddress& get_address() const noexcept { return address_; }
    const std::vector<uint32_t>& get_stale_devices() const noexcept { return stale_devices_; }

private:
    EnchantAddress address_;
    std::vector<uint32_t> stale_devices_;
    mutable std::string message_;
};

} // namespace protocol
} // namespace enchant

#endif
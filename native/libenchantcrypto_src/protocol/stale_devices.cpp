#include "enchant/protocol/stale_devices.hpp"
#include <cstdio>

namespace enchant {
namespace protocol {

StaleDevicesException::StaleDevicesException()
    : address_() {}

StaleDevicesException::StaleDevicesException(const EnchantAddress& address,
                                             const std::vector<uint32_t>& stale)
    : address_(address), stale_devices_(stale) {
    char buf[1024];
    size_t pos = 0;

    pos += snprintf(buf + pos, sizeof(buf) - pos,
                   "Stale devices for %s: {",
                   address.name.c_str());

    for (size_t i = 0; i < stale_devices_.size() && pos < sizeof(buf) - 20; i++) {
        if (i > 0) pos += snprintf(buf + pos, sizeof(buf) - pos, ",");
        pos += snprintf(buf + pos, sizeof(buf) - pos, "%u", stale_devices_[i]);
    }

    pos += snprintf(buf + pos, sizeof(buf) - pos, "}");

    message_ = buf;
}

const char* StaleDevicesException::what() const noexcept {
    return message_.c_str();
}

} // namespace protocol
} // namespace enchant
#include "enchant/protocol/mismatched_devices.hpp"
#include <cstdio>

namespace enchant {
namespace protocol {

MismatchedDevicesException::MismatchedDevicesException()
    : address_() {}

MismatchedDevicesException::MismatchedDevicesException(const EnchantAddress& address,
                                                       const std::vector<uint32_t>& missing,
                                                       const std::vector<uint32_t>& extra)
    : address_(address), missing_devices_(missing), extra_devices_(extra) {
    char buf[1024];
    size_t pos = 0;

    pos += snprintf(buf + pos, sizeof(buf) - pos,
                   "Mismatched devices for %s: ",
                   address.name.c_str());

    if (!missing_devices_.empty()) {
        pos += snprintf(buf + pos, sizeof(buf) - pos, "missing={");
        for (size_t i = 0; i < missing_devices_.size() && pos < sizeof(buf) - 20; i++) {
            if (i > 0) pos += snprintf(buf + pos, sizeof(buf) - pos, ",");
            pos += snprintf(buf + pos, sizeof(buf) - pos, "%u", missing_devices_[i]);
        }
        pos += snprintf(buf + pos, sizeof(buf) - pos, "}");
    }

    if (!extra_devices_.empty()) {
        if (!missing_devices_.empty()) pos += snprintf(buf + pos, sizeof(buf) - pos, " ");
        pos += snprintf(buf + pos, sizeof(buf) - pos, "extra={");
        for (size_t i = 0; i < extra_devices_.size() && pos < sizeof(buf) - 20; i++) {
            if (i > 0) pos += snprintf(buf + pos, sizeof(buf) - pos, ",");
            pos += snprintf(buf + pos, sizeof(buf) - pos, "%u", extra_devices_[i]);
        }
        pos += snprintf(buf + pos, sizeof(buf) - pos, "}");
    }

    message_ = buf;
}

const char* MismatchedDevicesException::what() const noexcept {
    return message_.c_str();
}

} // namespace protocol
} // namespace enchant
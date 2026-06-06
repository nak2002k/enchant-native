#include "enchant/device_id.hpp"
#include <sodium.h>

namespace enchant {

uint32_t DeviceId::generate() {
    uint32_t device_id;
    randombytes_buf(&device_id, sizeof(device_id));
    if (device_id == UNKNOWN) {
        device_id = PRIMARY;
    }
    return device_id;
}

bool DeviceId::is_valid(uint32_t device_id) {
    return device_id != UNKNOWN;
}

} // namespace enchant
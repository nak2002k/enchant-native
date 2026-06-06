#ifndef ENCHANT_DEVICE_ID_HPP
#define ENCHANT_DEVICE_ID_HPP

#include <cstdint>

namespace enchant {

constexpr uint32_t DEVICE_ID_PRIMARY = 1;
constexpr uint32_t DEVICE_ID_UNKNOWN = 0;
constexpr uint32_t DEVICE_ID_MAX_VALID = 0xFFFFFFFF;

class DeviceId {
public:
    static constexpr uint32_t PRIMARY = DEVICE_ID_PRIMARY;
    static constexpr uint32_t UNKNOWN = DEVICE_ID_UNKNOWN;

    static uint32_t generate();
    static bool is_valid(uint32_t device_id);
};

} // namespace enchant

#endif
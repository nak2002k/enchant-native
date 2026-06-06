#ifndef ENCHANT_PROTOCOL_MULTI_DEVICE_HPP
#define ENCHANT_PROTOCOL_MULTI_DEVICE_HPP

#include <vector>
#include <cstdint>
#include "enchant/i_identity_store.hpp"

namespace enchant {
namespace protocol {

class MultiDeviceSessionManager {
public:
    MultiDeviceSessionManager();
    ~MultiDeviceSessionManager();

    static std::vector<uint32_t> get_device_ids(const EnchantAddress& address);

    static bool has_multiple_devices(const EnchantAddress& address);

    static void remove_device(uint32_t device_id, const EnchantAddress& address);

    static void add_device(uint32_t device_id, const EnchantAddress& address);

    static std::vector<uint32_t> get_stale_devices(const EnchantAddress& address,
                                                    const std::vector<uint32_t>& active_devices);

    static std::vector<uint32_t> get_missing_devices(const EnchantAddress& address,
                                                      const std::vector<uint32_t>& expected_devices);

    static std::vector<uint32_t> get_extra_devices(const EnchantAddress& address,
                                                    const std::vector<uint32_t>& actual_devices);

    static bool validate_device_list(const EnchantAddress& address,
                                      const std::vector<uint32_t>& expected,
                                      const std::vector<uint32_t>& actual);

private:
    static std::string make_device_key(const EnchantAddress& address, uint32_t device_id);

    static std::string make_session_key(const EnchantAddress& address, uint32_t device_id);
};

} // namespace protocol
} // namespace enchant

#endif
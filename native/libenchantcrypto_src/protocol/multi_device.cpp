#include "enchant/protocol/multi_device.hpp"
#include "enchant/device_id.hpp"
#include <unordered_map>
#include <unordered_set>
#include <algorithm>
#include <mutex>
#include <sodium.h>

namespace enchant {
namespace protocol {

namespace {

std::mutex& get_global_mutex() {
    static std::mutex mutex;
    return mutex;
}

std::unordered_set<std::string>& get_global_device_set() {
    static std::unordered_set<std::string> devices;
    return devices;
}

std::unordered_map<std::string, std::vector<uint32_t>>& get_device_list_map() {
    static std::unordered_map<std::string, std::vector<uint32_t>> device_lists;
    return device_lists;
}

}

MultiDeviceSessionManager::MultiDeviceSessionManager() {}

MultiDeviceSessionManager::~MultiDeviceSessionManager() {}

std::string MultiDeviceSessionManager::make_device_key(const EnchantAddress& address,
                                                        uint32_t device_id) {
    return address.name + ":" + std::to_string(address.device_id) + ":device:" + std::to_string(device_id);
}

std::string MultiDeviceSessionManager::make_session_key(const EnchantAddress& address,
                                                         uint32_t device_id) {
    return address.name + ":" + std::to_string(address.device_id) + ":session:" + std::to_string(device_id);
}

std::vector<uint32_t> MultiDeviceSessionManager::get_device_ids(const EnchantAddress& address) {
    std::lock_guard<std::mutex> lock(get_global_mutex());
    auto& device_lists = get_device_list_map();
    auto it = device_lists.find(address.name);
    if (it == device_lists.end()) {
        return std::vector<uint32_t>();
    }
    return it->second;
}

bool MultiDeviceSessionManager::has_multiple_devices(const EnchantAddress& address) {
    auto devices = get_device_ids(address);
    return devices.size() > 1;
}

void MultiDeviceSessionManager::remove_device(uint32_t device_id, const EnchantAddress& address) {
    std::lock_guard<std::mutex> lock(get_global_mutex());
    auto& device_lists = get_device_list_map();
    auto& devices = get_global_device_set();

    std::string device_key = make_device_key(address, device_id);
    devices.erase(device_key);

    auto it = device_lists.find(address.name);
    if (it != device_lists.end()) {
        auto& list = it->second;
        list.erase(std::remove(list.begin(), list.end(), device_id), list.end());
        if (list.empty()) {
            device_lists.erase(it);
        }
    }
}

void MultiDeviceSessionManager::add_device(uint32_t device_id, const EnchantAddress& address) {
    if (!DeviceId::is_valid(device_id)) {
        return;
    }

    std::lock_guard<std::mutex> lock(get_global_mutex());
    auto& device_lists = get_device_list_map();
    auto& devices = get_global_device_set();

    std::string device_key = make_device_key(address, device_id);
    devices.insert(device_key);

    auto& list = device_lists[address.name];
    if (std::find(list.begin(), list.end(), device_id) == list.end()) {
        list.push_back(device_id);
    }
}

std::vector<uint32_t> MultiDeviceSessionManager::get_stale_devices(const EnchantAddress& address,
                                                                     const std::vector<uint32_t>& active_devices) {
    auto current_devices = get_device_ids(address);
    std::vector<uint32_t> stale;

    for (uint32_t device_id : current_devices) {
        if (std::find(active_devices.begin(), active_devices.end(), device_id) == active_devices.end()) {
            stale.push_back(device_id);
        }
    }

    return stale;
}

std::vector<uint32_t> MultiDeviceSessionManager::get_missing_devices(const EnchantAddress& address,
                                                                        const std::vector<uint32_t>& expected_devices) {
    auto current_devices = get_device_ids(address);
    std::vector<uint32_t> missing;

    for (uint32_t device_id : expected_devices) {
        if (std::find(current_devices.begin(), current_devices.end(), device_id) == current_devices.end()) {
            missing.push_back(device_id);
        }
    }

    return missing;
}

std::vector<uint32_t> MultiDeviceSessionManager::get_extra_devices(const EnchantAddress& address,
                                                                      const std::vector<uint32_t>& actual_devices) {
    auto current_devices = get_device_ids(address);
    std::vector<uint32_t> extra;

    for (uint32_t device_id : current_devices) {
        if (std::find(actual_devices.begin(), actual_devices.end(), device_id) == actual_devices.end()) {
            extra.push_back(device_id);
        }
    }

    return extra;
}

bool MultiDeviceSessionManager::validate_device_list(const EnchantAddress& address,
                                                      const std::vector<uint32_t>& expected,
                                                      const std::vector<uint32_t>& actual) {
    auto missing = get_missing_devices(address, expected);
    auto extra = get_extra_devices(address, actual);
    return missing.empty() && extra.empty();
}

} // namespace protocol
} // namespace enchant
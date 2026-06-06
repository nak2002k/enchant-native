#ifndef ENCHANT_SVR_SVR_MANAGER_HPP
#define ENCHANT_SVR_SVR_MANAGER_HPP

#include <cstdint>
#include <cstddef>
#include <vector>
#include <array>
#include <optional>
#include "enchant/error.h"
#include "secure/buffer.hpp"

namespace enchant {
namespace svr {

constexpr size_t SVR_PIN_MAX_LENGTH = 64;
constexpr size_t SVR_MASTER_KEY_SIZE = 32;
constexpr size_t SVR_WRAPPED_KEY_SIZE = 32;
constexpr size_t SVR_BACKUP_NONCE_SIZE = 24;
constexpr size_t SVR_BACKUP_TAG_SIZE = 16;
constexpr size_t SVR_MAX_ATTEMPTS = 10;

struct SvrBackup {
    std::array<uint8_t, SVR_WRAPPED_KEY_SIZE> wrapped_key;
    std::array<uint8_t, SVR_BACKUP_NONCE_SIZE> nonce;
    std::vector<uint8_t> encrypted_value;
    uint32_t version;
    uint32_t attempts_remaining;
};

struct SvrRestoreResult {
    secure::SecureBuffer restored_key;
    bool success;
};

class SvrManager {
public:
    SvrManager();

    int initialize(const uint8_t* master_key, size_t master_key_len);

    int create_backup(
        const uint8_t* pin, size_t pin_len,
        const uint8_t* value_to_backup, size_t value_len,
        SvrBackup& backup_out);

    int restore_backup(
        const uint8_t* pin, size_t pin_len,
        const SvrBackup& backup,
        SvrRestoreResult& result_out);

    int change_pin(
        const uint8_t* old_pin, size_t old_pin_len,
        const uint8_t* new_pin, size_t new_pin_len,
        const SvrBackup& old_backup,
        SvrBackup& new_backup_out);

    int rotate_master_key(
        const uint8_t* new_master_key, size_t new_master_key_len);

    bool is_initialized() const { return initialized_; }

    uint32_t get_attempts_remaining(const SvrBackup& backup) const;

private:
    std::array<uint8_t, SVR_MASTER_KEY_SIZE> master_key_;
    bool initialized_;

    int derive_pin_key(
        const uint8_t* pin, size_t pin_len,
        const uint8_t* salt, size_t salt_len,
        std::array<uint8_t, 32>& derived_key) const;

    int wrap_key_with_pin(
        const uint8_t* pin, size_t pin_len,
        const uint8_t* key_to_wrap, size_t key_len,
        std::array<uint8_t, SVR_WRAPPED_KEY_SIZE>& wrapped_key_out,
        std::array<uint8_t, SVR_BACKUP_NONCE_SIZE>& nonce_out) const;

    int unwrap_key_with_pin(
        const uint8_t* pin, size_t pin_len,
        const std::array<uint8_t, SVR_WRAPPED_KEY_SIZE>& wrapped_key,
        const std::array<uint8_t, SVR_BACKUP_NONCE_SIZE>& nonce,
        secure::SecureBuffer& unwrapped_key_out) const;
};

} // namespace svr
} // namespace enchant

#endif

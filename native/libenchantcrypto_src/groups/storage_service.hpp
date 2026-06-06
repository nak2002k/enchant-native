#ifndef ENCHANT_GROUPS_STORAGE_SERVICE_HPP
#define ENCHANT_GROUPS_STORAGE_SERVICE_HPP

#include <cstdint>
#include <cstddef>
#include <vector>
#include <array>
#include <optional>
#include "enchant/error.h"

namespace enchant {
namespace groups {

constexpr size_t STORAGE_MASTER_KEY_SIZE = 32;
constexpr size_t STORAGE_ITEM_KEY_SIZE = 32;
constexpr size_t STORAGE_ENVELOPE_NONCE_SIZE = 24;
constexpr size_t STORAGE_ENVELOPE_TAG_SIZE = 16;
constexpr size_t STORAGE_VERSION = 1;

struct StorageItem {
    std::vector<uint8_t> data;
    std::array<uint8_t, STORAGE_ITEM_KEY_SIZE> key;
};

struct StorageEnvelope {
    uint32_t version;
    std::array<uint8_t, STORAGE_ENVELOPE_NONCE_SIZE> nonce;
    std::vector<uint8_t> ciphertext;
};

class StorageService {
public:
    StorageService();

    int initialize(const uint8_t* master_key, size_t master_key_len);

    int encrypt_item(const uint8_t* plaintext, size_t plaintext_len,
                     const uint8_t* item_id, size_t item_id_len,
                     StorageEnvelope& envelope);

    int decrypt_item(const StorageEnvelope& envelope,
                     const uint8_t* item_id, size_t item_id_len,
                     std::vector<uint8_t>& plaintext);

    int rotate_master_key(const uint8_t* new_master_key, size_t new_master_key_len);

    int get_item_key(const uint8_t* item_id, size_t item_id_len,
                     std::array<uint8_t, STORAGE_ITEM_KEY_SIZE>& item_key) const;

    bool is_initialized() const { return initialized_; }

private:
    std::array<uint8_t, STORAGE_MASTER_KEY_SIZE> master_key_;
    bool initialized_;

    int derive_item_key(const uint8_t* item_id, size_t item_id_len,
                        std::array<uint8_t, STORAGE_ITEM_KEY_SIZE>& item_key) const;
};

} // namespace groups
} // namespace enchant

#endif

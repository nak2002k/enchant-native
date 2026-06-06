#include "groups/storage_service.hpp"
#include "primitives/hkdf.hpp"
#include "primitives/xchacha20.hpp"
#include "primitives/random.hpp"
#include "primitives/hmac.hpp"
#include "primitives/constant_time.hpp"
#include <sodium.h>
#include <cstring>

namespace enchant {
namespace groups {

constexpr const char* STORAGE_MASTER_KEY_LABEL = "EnchantStorageMasterKey_20240101";
constexpr const char* STORAGE_ITEM_KEY_LABEL = "EnchantStorageItemKey_20240101";
constexpr const char* STORAGE_ENCRYPT_LABEL = "EnchantStorageEncrypt_20240101";

StorageService::StorageService() : initialized_(false) {
    master_key_.fill(0);
}

int StorageService::initialize(const uint8_t* master_key, size_t master_key_len) {
    if (!master_key) return ENCHANT_ERROR_NULL_POINTER;
    if (master_key_len != STORAGE_MASTER_KEY_SIZE) return ENCHANT_ERROR_INVALID_KEY_SIZE;

    uint8_t derived[STORAGE_MASTER_KEY_SIZE];
    uint8_t salt[32] = {0};
    int rc = primitives::hkdf_derive(
        master_key, master_key_len,
        salt, 32,
        reinterpret_cast<const uint8_t*>(STORAGE_MASTER_KEY_LABEL),
        strlen(STORAGE_MASTER_KEY_LABEL),
        derived, STORAGE_MASTER_KEY_SIZE);

    sodium_memzero(salt, sizeof(salt));
    if (rc != ENCHANT_SUCCESS) {
        sodium_memzero(derived, sizeof(derived));
        return rc;
    }

    memcpy(master_key_.data(), derived, STORAGE_MASTER_KEY_SIZE);
    sodium_memzero(derived, sizeof(derived));
    initialized_ = true;
    return ENCHANT_SUCCESS;
}

int StorageService::derive_item_key(const uint8_t* item_id, size_t item_id_len,
                                     std::array<uint8_t, STORAGE_ITEM_KEY_SIZE>& item_key) const {
    if (!initialized_) return ENCHANT_ERROR_INTERNAL;
    if (!item_id) return ENCHANT_ERROR_NULL_POINTER;

    uint8_t salt[32] = {0};
    int rc = primitives::hkdf_derive(
        master_key_.data(), STORAGE_MASTER_KEY_SIZE,
        salt, 32,
        item_id, item_id_len,
        item_key.data(), STORAGE_ITEM_KEY_SIZE);

    sodium_memzero(salt, sizeof(salt));
    return rc;
}

int StorageService::get_item_key(const uint8_t* item_id, size_t item_id_len,
                                  std::array<uint8_t, STORAGE_ITEM_KEY_SIZE>& item_key) const {
    return derive_item_key(item_id, item_id_len, item_key);
}

int StorageService::encrypt_item(const uint8_t* plaintext, size_t plaintext_len,
                                  const uint8_t* item_id, size_t item_id_len,
                                  StorageEnvelope& envelope) {
    if (!initialized_) return ENCHANT_ERROR_INTERNAL;
    if (!plaintext && plaintext_len > 0) return ENCHANT_ERROR_NULL_POINTER;
    if (!item_id) return ENCHANT_ERROR_NULL_POINTER;

    std::array<uint8_t, STORAGE_ITEM_KEY_SIZE> item_key;
    int rc = derive_item_key(item_id, item_id_len, item_key);
    if (rc != ENCHANT_SUCCESS) return rc;

    uint8_t enc_key[32];
    uint8_t salt[32] = {0};
    rc = primitives::hkdf_derive(
        item_key.data(), STORAGE_ITEM_KEY_SIZE,
        salt, 32,
        reinterpret_cast<const uint8_t*>(STORAGE_ENCRYPT_LABEL),
        strlen(STORAGE_ENCRYPT_LABEL),
        enc_key, 32);
    sodium_memzero(salt, sizeof(salt));
    sodium_memzero(item_key.data(), STORAGE_ITEM_KEY_SIZE);
    if (rc != ENCHANT_SUCCESS) {
        sodium_memzero(enc_key, sizeof(enc_key));
        return rc;
    }

    envelope.version = STORAGE_VERSION;
    randombytes_buf(envelope.nonce.data(), STORAGE_ENVELOPE_NONCE_SIZE);

    size_t ciphertext_len = plaintext_len + STORAGE_ENVELOPE_TAG_SIZE;
    envelope.ciphertext.resize(ciphertext_len);

    rc = primitives::xchacha20_encrypt(
        plaintext, plaintext_len,
        enc_key, envelope.nonce.data(),
        envelope.ciphertext.data(), ciphertext_len);

    sodium_memzero(enc_key, sizeof(enc_key));
    if (rc != ENCHANT_SUCCESS) {
        envelope.ciphertext.clear();
        return rc;
    }

    return ENCHANT_SUCCESS;
}

int StorageService::decrypt_item(const StorageEnvelope& envelope,
                                  const uint8_t* item_id, size_t item_id_len,
                                  std::vector<uint8_t>& plaintext) {
    if (!initialized_) return ENCHANT_ERROR_INTERNAL;
    if (!item_id) return ENCHANT_ERROR_NULL_POINTER;
    if (envelope.version != STORAGE_VERSION) return ENCHANT_ERROR_INVALID_FORMAT;
    if (envelope.ciphertext.size() < STORAGE_ENVELOPE_TAG_SIZE)
        return ENCHANT_ERROR_CIPHERTEXT_TOO_SHORT;

    std::array<uint8_t, STORAGE_ITEM_KEY_SIZE> item_key;
    int rc = derive_item_key(item_id, item_id_len, item_key);
    if (rc != ENCHANT_SUCCESS) return rc;

    uint8_t enc_key[32];
    uint8_t salt[32] = {0};
    rc = primitives::hkdf_derive(
        item_key.data(), STORAGE_ITEM_KEY_SIZE,
        salt, 32,
        reinterpret_cast<const uint8_t*>(STORAGE_ENCRYPT_LABEL),
        strlen(STORAGE_ENCRYPT_LABEL),
        enc_key, 32);
    sodium_memzero(salt, sizeof(salt));
    sodium_memzero(item_key.data(), STORAGE_ITEM_KEY_SIZE);
    if (rc != ENCHANT_SUCCESS) {
        sodium_memzero(enc_key, sizeof(enc_key));
        return rc;
    }

    size_t plaintext_len = envelope.ciphertext.size() - STORAGE_ENVELOPE_TAG_SIZE;
    plaintext.resize(plaintext_len);

    rc = primitives::xchacha20_decrypt(
        envelope.ciphertext.data(), envelope.ciphertext.size(),
        enc_key, envelope.nonce.data(),
        plaintext.data(), plaintext_len);

    sodium_memzero(enc_key, sizeof(enc_key));
    if (rc != ENCHANT_SUCCESS) {
        plaintext.clear();
        return rc;
    }

    return ENCHANT_SUCCESS;
}

int StorageService::rotate_master_key(const uint8_t* new_master_key, size_t new_master_key_len) {
    if (!new_master_key) return ENCHANT_ERROR_NULL_POINTER;
    if (new_master_key_len != STORAGE_MASTER_KEY_SIZE) return ENCHANT_ERROR_INVALID_KEY_SIZE;

    uint8_t derived[STORAGE_MASTER_KEY_SIZE];
    uint8_t salt[32] = {0};
    int rc = primitives::hkdf_derive(
        new_master_key, new_master_key_len,
        salt, 32,
        reinterpret_cast<const uint8_t*>(STORAGE_MASTER_KEY_LABEL),
        strlen(STORAGE_MASTER_KEY_LABEL),
        derived, STORAGE_MASTER_KEY_SIZE);

    sodium_memzero(salt, sizeof(salt));
    if (rc != ENCHANT_SUCCESS) {
        sodium_memzero(derived, sizeof(derived));
        return rc;
    }

    sodium_memzero(master_key_.data(), STORAGE_MASTER_KEY_SIZE);
    memcpy(master_key_.data(), derived, STORAGE_MASTER_KEY_SIZE);
    sodium_memzero(derived, sizeof(derived));
    return ENCHANT_SUCCESS;
}

} // namespace groups
} // namespace enchant

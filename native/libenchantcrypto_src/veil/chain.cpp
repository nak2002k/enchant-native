#include "chain.hpp"
#include "primitives/hmac.hpp"
#include "primitives/hkdf.hpp"
#include "primitives/x25519.hpp"
#include <cstring>
#include <sodium.h>

namespace enchant {
namespace veil {

ChainKey::ChainKey() : index_(0) {
    memset(key_, 0, CHAIN_KEY_SIZE);
}

ChainKey::ChainKey(const uint8_t* key, uint32_t index) : index_(index) {
    memcpy(key_, key, CHAIN_KEY_SIZE);
}

ChainKey::~ChainKey() {
    sodium_memzero(key_, CHAIN_KEY_SIZE);
}

const uint8_t* ChainKey::key() const {
    return key_;
}

uint32_t ChainKey::index() const {
    return index_;
}

static int hmac_sha256_single(const uint8_t* key, size_t key_len,
                              uint8_t seed_byte, uint8_t* output) {
    return enchant::primitives::hmac_sha256(key, key_len, &seed_byte, 1, output);
}

ChainKey ChainKey::next_chain_key() const {
    ChainKey next;
    next.index_ = index_ + 1;
    hmac_sha256_single(key_, CHAIN_KEY_SIZE, CHAIN_KEY_STEP_BYTE, next.key_);
    return next;
}

int ChainKey::message_key_seed(uint8_t* seed) const {
    if (!seed) return ENCHANT_ERROR_NULL_POINTER;
    return hmac_sha256_single(key_, CHAIN_KEY_SIZE, MESSAGE_KEY_SEED_BYTE, seed);
}

RootKey::RootKey() {
    memset(key_, 0, ROOT_KEY_SIZE);
}

RootKey::RootKey(const uint8_t* key) {
    memcpy(key_, key, ROOT_KEY_SIZE);
}

RootKey::~RootKey() {
    sodium_memzero(key_, ROOT_KEY_SIZE);
}

const uint8_t* RootKey::key() const {
    return key_;
}

int RootKey::create_chain(const uint8_t* our_private, const uint8_t* their_public,
                          RootKey& new_root_key, ChainKey& new_chain_key) const {
    if (!our_private || !their_public)
        return ENCHANT_ERROR_NULL_POINTER;

    secure::SecureBuffer shared_secret(32);
    int rc = enchant::primitives::x25519_dh(our_private, their_public, shared_secret.data());
    if (rc != ENCHANT_SUCCESS) return rc;

    uint8_t ikm[64];
    memcpy(ikm, key_, ROOT_KEY_SIZE);
    memcpy(ikm + ROOT_KEY_SIZE, shared_secret.data(), 32);

    uint8_t salt[32] = {0};
    const uint8_t info[] = "EnvelopeRatchet";
    uint8_t derived[64];
    rc = enchant::primitives::hkdf_derive(ikm, 64, salt, 32,
                                           info, sizeof(info) - 1, derived, 64);
    if (rc != ENCHANT_SUCCESS) return rc;

    new_root_key = RootKey(derived);
    new_chain_key = ChainKey(derived + ROOT_KEY_SIZE, 0);

    sodium_memzero(derived, 64);
    sodium_memzero(shared_secret.data(), 32);

    return ENCHANT_SUCCESS;
}

MessageKeys::MessageKeys() : counter(0) {
    memset(cipher_key, 0, CIPHER_KEY_SIZE);
    memset(mac_key, 0, MAC_KEY_SIZE);
    memset(iv, 0, IV_SIZE);
}

int MessageKeys::derive(const uint8_t* seed, const uint8_t* pqr_key, uint32_t counter,
                        MessageKeys& out) {
    if (!seed) return ENCHANT_ERROR_NULL_POINTER;

    uint8_t salt[32] = {0};
    const uint8_t info[] = "EnvelopeMessageKeys";
    uint8_t derived[DERIVED_KEYS_SIZE];

    int rc = enchant::primitives::hkdf_derive(seed, MESSAGE_KEY_SIZE,
                                               salt, 32,
                                               info, sizeof(info) - 1,
                                               derived, DERIVED_KEYS_SIZE);
    if (rc != ENCHANT_SUCCESS) return rc;

    memcpy(out.cipher_key, derived, CIPHER_KEY_SIZE);
    memcpy(out.mac_key, derived + CIPHER_KEY_SIZE, MAC_KEY_SIZE);
    memcpy(out.iv, derived + CIPHER_KEY_SIZE + MAC_KEY_SIZE, IV_SIZE);
    out.counter = counter;

    if (pqr_key) {
        for (size_t i = 0; i < CIPHER_KEY_SIZE; i++) {
            out.cipher_key[i] ^= pqr_key[i];
        }
    }

    sodium_memzero(derived, DERIVED_KEYS_SIZE);
    return ENCHANT_SUCCESS;
}

SkippedKey::SkippedKey() : seed(MESSAGE_KEY_SIZE), counter(0) {
}

SkippedKey::SkippedKey(const uint8_t* seed_data, uint32_t counter)
    : seed(seed_data, MESSAGE_KEY_SIZE), counter(counter) {
}

ReceiverChain::ReceiverChain()
    : sender_x25519_key(CHAIN_KEY_SIZE),
      chain_key(CHAIN_KEY_SIZE),
      chain_index(0) {
}

ReceiverChain::ReceiverChain(ReceiverChain&& other) noexcept
    : sender_x25519_key(std::move(other.sender_x25519_key)),
      chain_key(std::move(other.chain_key)),
      chain_index(other.chain_index),
      message_keys(std::move(other.message_keys)) {
    other.chain_index = 0;
}

ReceiverChain& ReceiverChain::operator=(ReceiverChain&& other) noexcept {
    if (this != &other) {
        sender_x25519_key = std::move(other.sender_x25519_key);
        chain_key = std::move(other.chain_key);
        chain_index = other.chain_index;
        message_keys = std::move(other.message_keys);
        other.chain_index = 0;
    }
    return *this;
}

} // namespace veil
} // namespace enchant

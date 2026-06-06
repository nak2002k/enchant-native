#ifndef ENCHANT_VEIL_CHAIN_HPP
#define ENCHANT_VEIL_CHAIN_HPP

#include <cstdint>
#include <cstddef>
#include <vector>
#include "enchant/error.h"
#include "secure/buffer.hpp"

namespace enchant {
namespace veil {

constexpr size_t CHAIN_KEY_SIZE = 32;
constexpr size_t ROOT_KEY_SIZE = 32;
constexpr size_t MESSAGE_KEY_SIZE = 32;
constexpr size_t CIPHER_KEY_SIZE = 32;
constexpr size_t MAC_KEY_SIZE = 32;
constexpr size_t IV_SIZE = 16;
constexpr size_t DERIVED_KEYS_SIZE = 80;
constexpr uint8_t CHAIN_KEY_STEP_BYTE = 0x02;
constexpr uint8_t MESSAGE_KEY_SEED_BYTE = 0x01;

class ChainKey {
public:
    ChainKey();
    ChainKey(const uint8_t* key, uint32_t index);
    ~ChainKey();

    const uint8_t* key() const;
    uint32_t index() const;

    ChainKey next_chain_key() const;

    int message_key_seed(uint8_t* seed) const;

private:
    uint8_t key_[CHAIN_KEY_SIZE];
    uint32_t index_;
};

class RootKey {
public:
    RootKey();
    RootKey(const uint8_t* key);
    ~RootKey();

    const uint8_t* key() const;

    int create_chain(const uint8_t* our_private, const uint8_t* their_public,
                     RootKey& new_root_key, ChainKey& new_chain_key) const;

private:
    uint8_t key_[ROOT_KEY_SIZE];
};

struct MessageKeys {
    uint8_t cipher_key[CIPHER_KEY_SIZE];
    uint8_t mac_key[MAC_KEY_SIZE];
    uint8_t iv[IV_SIZE];
    uint32_t counter;

    MessageKeys();

    static int derive(const uint8_t* seed, const uint8_t* pqr_key, uint32_t counter,
                      MessageKeys& out);
};

struct SkippedKey {
    secure::SecureBuffer seed;
    uint32_t counter;

    SkippedKey();
    SkippedKey(const uint8_t* seed_data, uint32_t counter);
};

struct ReceiverChain {
    secure::SecureBuffer sender_x25519_key;
    secure::SecureBuffer chain_key;
    uint32_t chain_index;
    std::vector<SkippedKey> message_keys;

    ReceiverChain();
    ReceiverChain(const ReceiverChain&) = delete;
    ReceiverChain& operator=(const ReceiverChain&) = delete;
    ReceiverChain(ReceiverChain&&) noexcept;
    ReceiverChain& operator=(ReceiverChain&&) noexcept;
};

struct SenderChain {
    secure::SecureBuffer x25519_private;
    secure::SecureBuffer x25519_public;
    ChainKey chain_key;

    SenderChain() : x25519_private(CHAIN_KEY_SIZE), x25519_public(CHAIN_KEY_SIZE) {}
};

} // namespace veil
} // namespace enchant

#endif

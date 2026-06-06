#ifndef ENCHANT_GROUPS_SENDER_KEY_HPP
#define ENCHANT_GROUPS_SENDER_KEY_HPP

#include <cstdint>
#include <cstddef>
#include <cstring>
#include <string>
#include <vector>
#include <sodium.h>
#include "enchant/error.h"
#include "secure/buffer.hpp"

namespace enchant {
namespace groups {

constexpr size_t SENDER_KEY_SEED_SIZE = 32;
constexpr size_t SENDER_KEY_IV_SIZE = 16;
constexpr size_t SENDER_KEY_CIPHER_KEY_SIZE = 32;
constexpr size_t SENDER_KEY_ITERATION_SIZE = 4;
constexpr size_t SENDER_KEY_MAX_FORWARD_JUMPS = 2000;
constexpr size_t SENDER_KEY_MAX_MESSAGE_KEYS = 2000;

struct SenderMessageKey;

struct SenderChainKey {
    uint32_t iteration;
    secure::SecureBuffer seed;

    SenderChainKey();
    SenderChainKey(uint32_t iteration, const uint8_t* seed);
    SenderChainKey(uint32_t iteration, secure::SecureBuffer seed);

    SenderChainKey next() const;
    SenderMessageKey sender_message_key() const;
};

struct SenderMessageKey {
    uint32_t iteration;
    uint8_t seed[SENDER_KEY_SEED_SIZE];
    uint8_t iv[SENDER_KEY_IV_SIZE];
    uint8_t cipher_key[SENDER_KEY_CIPHER_KEY_SIZE];

    SenderMessageKey();
    ~SenderMessageKey();
    static SenderMessageKey derive(uint32_t iteration, const uint8_t* seed);
};

struct SenderKeyState {
    std::string sender_id;
    uint32_t key_id;
    SenderChainKey chain_key;
    std::vector<SenderMessageKey> sender_message_keys;
    uint32_t epoch;

    SenderKeyState();
    SenderKeyState(SenderKeyState&&) noexcept;
    SenderKeyState& operator=(SenderKeyState&&) noexcept;
    SenderKeyState(const SenderKeyState&) = delete;
    SenderKeyState& operator=(const SenderKeyState&) = delete;

    void add_sender_message_key(const SenderMessageKey& key);
    bool remove_sender_message_key(uint32_t iteration, SenderMessageKey& out);
    int get_sender_key(uint32_t iteration, SenderMessageKey& out);
};

struct SenderKeyRecord {
    std::vector<SenderKeyState> states;
    uint32_t sender_key_id_counter;

    SenderKeyRecord();
    SenderKeyState* get_state(uint32_t key_id);
    SenderKeyState* add_state();
};

struct DistributionMessage {
    uint32_t sender_key_id;
    uint32_t epoch;
    uint32_t iteration;
    secure::SecureBuffer chain_key;
    secure::SecureBuffer signature;

    DistributionMessage();

    int serialize(uint8_t* output, size_t* output_len) const;
    int deserialize(const uint8_t* input, size_t input_len);
};

int sender_key_create(const char* sender_id, uint32_t key_id, SenderKeyState& state);

int sender_key_encrypt(SenderKeyState& state,
                       const uint8_t* plaintext, size_t plaintext_len,
                       uint8_t* output, size_t* output_len);

int sender_key_decrypt(SenderKeyState& state,
                       const uint8_t* input, size_t input_len,
                       uint8_t* plaintext, size_t* plaintext_len);

int sender_key_create_distribution_message(const SenderKeyState& state,
                                           const uint8_t* signing_private,
                                           uint8_t* output, size_t* output_len);

int sender_key_process_distribution_message(SenderKeyState& state,
                                             const uint8_t* input, size_t input_len,
                                             const uint8_t* signing_public);

int sender_key_record_serialize(const SenderKeyRecord& record,
                                uint8_t* output, size_t* output_len);

int sender_key_record_deserialize(SenderKeyRecord& record,
                                  const uint8_t* input, size_t input_len);

} // namespace groups
} // namespace enchant

#endif

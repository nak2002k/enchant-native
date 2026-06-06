#include "groups/sender_key.hpp"
#include "proto/protobuf_serializer.hpp"
#include "primitives/hkdf.hpp"
#include "primitives/hmac.hpp"
#include "primitives/xchacha20.hpp"
#include "primitives/random.hpp"
#include "primitives/ed25519.hpp"
#include <cstring>
#include <sodium.h>

namespace enchant {
namespace groups {

SenderChainKey::SenderChainKey() : iteration(0), seed(SENDER_KEY_SEED_SIZE) {}

SenderChainKey::SenderChainKey(uint32_t iteration, const uint8_t* seed_data)
    : iteration(iteration), seed(seed_data, SENDER_KEY_SEED_SIZE) {}

SenderChainKey::SenderChainKey(uint32_t iteration, secure::SecureBuffer seed_data)
    : iteration(iteration), seed(std::move(seed_data)) {}

SenderChainKey SenderChainKey::next() const {
    uint8_t hmac_input = 0x02;
    secure::SecureBuffer next_seed(SENDER_KEY_SEED_SIZE);
    primitives::hmac_sha256(seed.data(), SENDER_KEY_SEED_SIZE,
                            &hmac_input, 1, next_seed.data());
    return SenderChainKey(iteration + 1, std::move(next_seed));
}

SenderMessageKey SenderChainKey::sender_message_key() const {
    uint8_t hmac_input = 0x01;
    uint8_t msg_seed[SENDER_KEY_SEED_SIZE];
    primitives::hmac_sha256(seed.data(), SENDER_KEY_SEED_SIZE,
                            &hmac_input, 1, msg_seed);
    SenderMessageKey smk = SenderMessageKey::derive(iteration, msg_seed);
    sodium_memzero(msg_seed, SENDER_KEY_SEED_SIZE);
    return smk;
}

SenderMessageKey::SenderMessageKey()
    : iteration(0) {
    memset(seed, 0, SENDER_KEY_SEED_SIZE);
    memset(iv, 0, SENDER_KEY_IV_SIZE);
    memset(cipher_key, 0, SENDER_KEY_CIPHER_KEY_SIZE);
}

SenderMessageKey::~SenderMessageKey() {
    sodium_memzero(seed, SENDER_KEY_SEED_SIZE);
    sodium_memzero(iv, SENDER_KEY_IV_SIZE);
    sodium_memzero(cipher_key, SENDER_KEY_CIPHER_KEY_SIZE);
    iteration = 0;
}

SenderMessageKey SenderMessageKey::derive(uint32_t iteration, const uint8_t* seed_data) {
    SenderMessageKey key;
    key.iteration = iteration;
    memcpy(key.seed, seed_data, SENDER_KEY_SEED_SIZE);

    uint8_t derived[48];
    uint8_t salt[32] = {0};
    const uint8_t info[] = "EnvelopeGroup";
    int rc = primitives::hkdf_derive(seed_data, SENDER_KEY_SEED_SIZE,
                                     salt, 32, info, sizeof(info) - 1,
                                     derived, 48);
    if (rc != ENCHANT_SUCCESS) {
        sodium_memzero(derived, sizeof(derived));
        key.iteration = 0;
        return key;
    }

    memcpy(key.iv, derived, 16);
    memcpy(key.cipher_key, derived + 16, 32);
    sodium_memzero(derived, sizeof(derived));
    return key;
}

SenderKeyState::SenderKeyState()
    : sender_id(), key_id(0), chain_key(), epoch(0) {}

SenderKeyState::SenderKeyState(SenderKeyState&& other) noexcept
    : sender_id(std::move(other.sender_id)),
      key_id(other.key_id),
      chain_key(std::move(other.chain_key)),
      sender_message_keys(std::move(other.sender_message_keys)),
      epoch(other.epoch) {
    other.key_id = 0;
    other.epoch = 0;
}

SenderKeyState& SenderKeyState::operator=(SenderKeyState&& other) noexcept {
    if (this != &other) {
        sender_id = std::move(other.sender_id);
        key_id = other.key_id;
        chain_key = std::move(other.chain_key);
        sender_message_keys = std::move(other.sender_message_keys);
        epoch = other.epoch;
        other.key_id = 0;
        other.epoch = 0;
    }
    return *this;
}

void SenderKeyState::add_sender_message_key(const SenderMessageKey& key) {
    sender_message_keys.insert(sender_message_keys.begin(), key);
    while (sender_message_keys.size() > SENDER_KEY_MAX_MESSAGE_KEYS) {
        sender_message_keys.pop_back();
    }
}

bool SenderKeyState::remove_sender_message_key(uint32_t iteration,
                                                SenderMessageKey& out) {
    for (auto it = sender_message_keys.begin(); it != sender_message_keys.end(); ++it) {
        if (it->iteration == iteration) {
            out = std::move(*it);
            sender_message_keys.erase(it);
            return true;
        }
    }
    return false;
}

int SenderKeyState::get_sender_key(uint32_t iteration, SenderMessageKey& out) {
    if (chain_key.iteration > iteration) {
        if (remove_sender_message_key(iteration, out)) {
            return ENCHANT_SUCCESS;
        }
        return ENCHANT_ERROR_REPLAY_DETECTED;
    }

    uint32_t jump = iteration - chain_key.iteration;
    if (jump > SENDER_KEY_MAX_FORWARD_JUMPS) {
        return ENCHANT_ERROR_DECRYPTION_FAILED;
    }

    while (chain_key.iteration < iteration) {
        add_sender_message_key(chain_key.sender_message_key());
        chain_key = chain_key.next();
    }

    out = chain_key.sender_message_key();
    chain_key = chain_key.next();
    return ENCHANT_SUCCESS;
}

SenderKeyRecord::SenderKeyRecord() : sender_key_id_counter(0) {}

SenderKeyState* SenderKeyRecord::get_state(uint32_t key_id) {
    for (auto& state : states) {
        if (state.key_id == key_id) {
            return &state;
        }
    }
    return nullptr;
}

SenderKeyState* SenderKeyRecord::add_state() {
    states.emplace_back();
    return &states.back();
}

DistributionMessage::DistributionMessage()
    : sender_key_id(0), epoch(0), iteration(0) {}

int sender_key_create(const char* sender_id, uint32_t key_id,
                      SenderKeyState& state) {
    if (!sender_id) return ENCHANT_ERROR_NULL_POINTER;
    if (sender_id[0] == '\0') return ENCHANT_ERROR_INVALID_FORMAT;

    secure::SecureBuffer seed(32);
    primitives::random_bytes(seed.data(), 32);

    uint8_t salt[32] = {0};
    const uint8_t info[] = "EnchantSenderKey";
    int rc = primitives::hkdf_derive(seed.data(), 32, salt, 32,
                                      info, sizeof(info) - 1,
                                      seed.data(), 32);
    if (rc != ENCHANT_SUCCESS) {
        seed.zero();
        return rc;
    }

    state.sender_id = sender_id;
    state.key_id = key_id;
    state.epoch = 0;
    state.chain_key = SenderChainKey(0, seed.data());
    return ENCHANT_SUCCESS;
}

int sender_key_encrypt(SenderKeyState& state,
                       const uint8_t* plaintext, size_t plaintext_len,
                       uint8_t* output, size_t* output_len) {
    if (!plaintext || !output || !output_len)
        return ENCHANT_ERROR_NULL_POINTER;

    SenderMessageKey msg_key = state.chain_key.sender_message_key();
    state.chain_key = state.chain_key.next();

    uint8_t iteration_bytes[4];
    memcpy(iteration_bytes, &msg_key.iteration, 4);

    uint8_t nonce[24] = {0};
    memcpy(nonce, msg_key.iv, 16);

    size_t needed = 4 + plaintext_len + 16;
    if (*output_len < needed) {
        *output_len = needed;
        return ENCHANT_ERROR_BUFFER_TOO_SMALL;
    }

    memcpy(output, iteration_bytes, 4);
    int rc = primitives::xchacha20_encrypt(
        plaintext, plaintext_len,
        msg_key.cipher_key, nonce,
        output + 4, *output_len - 4);
    if (rc != ENCHANT_SUCCESS) return rc;

    *output_len = 4 + plaintext_len + 16;
    return ENCHANT_SUCCESS;
}

int sender_key_decrypt(SenderKeyState& state,
                       const uint8_t* input, size_t input_len,
                       uint8_t* plaintext, size_t* plaintext_len) {
    if (!input || !plaintext || !plaintext_len)
        return ENCHANT_ERROR_NULL_POINTER;
    if (input_len < 4 + 16)
        return ENCHANT_ERROR_CIPHERTEXT_TOO_SHORT;

    uint32_t message_iteration;
    memcpy(&message_iteration, input, 4);

    SenderMessageKey msg_key;
    int rc = state.get_sender_key(message_iteration, msg_key);
    if (rc != ENCHANT_SUCCESS) return rc;

    uint8_t nonce[24] = {0};
    memcpy(nonce, msg_key.iv, 16);

    rc = primitives::xchacha20_decrypt(
        input + 4, input_len - 4,
        msg_key.cipher_key, nonce,
        plaintext, *plaintext_len);
    if (rc != ENCHANT_SUCCESS) return rc;

    *plaintext_len = input_len - 4 - 16;
    return ENCHANT_SUCCESS;
}

int sender_key_create_distribution_message(const SenderKeyState& state,
                                           const uint8_t* signing_private,
                                           uint8_t* output, size_t* output_len) {
    if (!signing_private || !output || !output_len)
        return ENCHANT_ERROR_NULL_POINTER;

    size_t needed = 4 + 4 + 4 + 32 + 32;
    if (*output_len < needed) {
        *output_len = needed;
        return ENCHANT_ERROR_BUFFER_TOO_SMALL;
    }

    size_t offset = 0;
    memcpy(output + offset, &state.key_id, 4); offset += 4;
    memcpy(output + offset, &state.epoch, 4); offset += 4;
    memcpy(output + offset, &state.chain_key.iteration, 4); offset += 4;
    memcpy(output + offset, state.chain_key.seed.data(), 32); offset += 32;

    secure::SecureBuffer sig(64);
    int rc = primitives::ed25519_sign(output, offset, signing_private, sig.data());
    if (rc != ENCHANT_SUCCESS) return rc;

    memcpy(output + offset, sig.data(), 64); offset += 64;
    *output_len = offset;
    return ENCHANT_SUCCESS;
}

int sender_key_process_distribution_message(SenderKeyState& state,
                                             const uint8_t* input, size_t input_len,
                                             const uint8_t* signing_public) {
    if (!input || !signing_public) return ENCHANT_ERROR_NULL_POINTER;
    if (input_len < 4 + 4 + 4 + 32 + 64)
        return ENCHANT_ERROR_CIPHERTEXT_TOO_SHORT;

    size_t sig_offset = 4 + 4 + 4 + 32;
    int rc = primitives::ed25519_verify(input, sig_offset,
                                         input + sig_offset, signing_public);
    if (rc != ENCHANT_SUCCESS) return rc;

    size_t offset = 0;
    uint32_t key_id, epoch, iteration;
    memcpy(&key_id, input + offset, 4); offset += 4;
    memcpy(&epoch, input + offset, 4); offset += 4;
    memcpy(&iteration, input + offset, 4); offset += 4;

    state.key_id = key_id;
    state.epoch = epoch;
    state.chain_key = SenderChainKey(iteration, input + offset);
    offset += 32;

    return ENCHANT_SUCCESS;
}

int sender_key_record_serialize(const SenderKeyRecord& record,
                                uint8_t* output, size_t* output_len) {
    if (!output || !output_len) return ENCHANT_ERROR_NULL_POINTER;

    std::vector<uint8_t> proto_output;
    int rc = proto::ProtobufSerializer::serialize_sender_key_record(record, proto_output);
    if (rc != ENCHANT_SUCCESS) return rc;

    if (proto_output.size() > *output_len) {
        *output_len = proto_output.size();
        return ENCHANT_ERROR_BUFFER_TOO_SMALL;
    }

    memcpy(output, proto_output.data(), proto_output.size());
    *output_len = proto_output.size();
    return ENCHANT_SUCCESS;
}

int sender_key_record_deserialize(SenderKeyRecord& record,
                                  const uint8_t* input, size_t input_len) {
    if (!input) return ENCHANT_ERROR_NULL_POINTER;

    return proto::ProtobufSerializer::deserialize_sender_key_record(input, input_len, record);
}

} // namespace groups
} // namespace enchant

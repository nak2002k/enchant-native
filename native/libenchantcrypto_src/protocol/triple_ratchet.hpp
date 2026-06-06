#ifndef ENCHANT_PROTOCOL_TRIPLE_RATCHET_HPP
#define ENCHANT_PROTOCOL_TRIPLE_RATCHET_HPP

#include <cstdint>
#include <cstddef>
#include <vector>
#include <array>
#include <optional>
#include "enchant/error.h"
#include "secure/buffer.hpp"

namespace enchant {
namespace protocol {

constexpr size_t TRIPLE_RATCHET_KEY_SIZE = 32;
constexpr size_t TRIPLE_RATCHET_CHAIN_KEY_SIZE = 32;
constexpr size_t TRIPLE_RATCHET_ROOT_KEY_SIZE = 32;
constexpr size_t TRIPLE_RATCHET_MESSAGE_KEY_SIZE = 32;
constexpr size_t TRIPLE_RATCHET_MAX_SKIP = 1000;

struct TripleRatchetMessageKey {
    std::array<uint8_t, TRIPLE_RATCHET_MESSAGE_KEY_SIZE> key;
    std::array<uint8_t, 24> nonce;
    uint32_t counter;
};

struct TripleRatchetChainKey {
    std::array<uint8_t, TRIPLE_RATCHET_CHAIN_KEY_SIZE> key;
    uint32_t counter;
};

struct TripleRatchetPublicKey {
    std::array<uint8_t, 32> ec_public;
    std::vector<uint8_t> pq_public;
};

struct TripleRatchetSecretKey {
    secure::SecureBuffer ec_private;
    secure::SecureBuffer pq_private;
};

class TripleRatchet {
public:
    TripleRatchet();

    int initialize(
        const uint8_t* shared_secret, size_t shared_secret_len,
        const TripleRatchetPublicKey& their_public,
        const uint8_t* our_ec_private, size_t ec_private_len,
        bool is_alice
    );

    int ratchet_forward();

    int encrypt(
        const uint8_t* plaintext, size_t plaintext_len,
        std::vector<uint8_t>& ciphertext_out
    );

    int decrypt(
        const uint8_t* ciphertext, size_t ciphertext_len,
        std::vector<uint8_t>& plaintext_out
    );

    int skip_message_keys(uint32_t until_counter);

    bool is_initialized() const { return initialized_; }

    uint32_t send_counter() const { return send_chain_.counter; }
    uint32_t receive_counter() const { return receive_counter_; }

    void zero();

private:
    int derive_message_key(
        const TripleRatchetChainKey& chain,
        TripleRatchetMessageKey& message_key_out,
        TripleRatchetChainKey& next_chain_out
    ) const;

    int dh_ratchet_step(
        const uint8_t* their_ec_public, size_t ec_public_len
    );

    int pq_ratchet_step(
        const uint8_t* their_pq_public, size_t pq_public_len
    );

    int combine_ratchet_keys(
        const uint8_t* ec_shared, size_t ec_len,
        const uint8_t* pq_shared, size_t pq_len,
        std::array<uint8_t, TRIPLE_RATCHET_ROOT_KEY_SIZE>& new_root,
        TripleRatchetChainKey& new_send_chain,
        TripleRatchetChainKey& new_receive_chain
    ) const;

    bool initialized_;
    std::array<uint8_t, TRIPLE_RATCHET_ROOT_KEY_SIZE> root_key_;
    TripleRatchetChainKey send_chain_;
    TripleRatchetChainKey receive_chain_;
    TripleRatchetPublicKey our_public_;
    TripleRatchetSecretKey our_secret_;
    TripleRatchetPublicKey their_public_;
    uint32_t receive_counter_;
    uint32_t previous_counter_;
    std::vector<std::pair<uint32_t, TripleRatchetMessageKey>> skipped_keys_;
};

} // namespace protocol
} // namespace enchant

#endif

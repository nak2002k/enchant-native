#ifndef ENCHANT_PROTOCOL_TRIPLE_RATCHET_OUTGOING_HPP
#define ENCHANT_PROTOCOL_TRIPLE_RATCHET_OUTGOING_HPP

#include <cstdint>
#include <cstddef>
#include <vector>
#include <array>
#include "enchant/error.h"
#include "protocol/triple_ratchet.hpp"
#include "secure/buffer.hpp"

namespace enchant {
namespace protocol {

class OutgoingTripleRatchet {
public:
    OutgoingTripleRatchet();

    int initialize(
        const uint8_t* root_key, size_t root_key_len,
        const uint8_t* ec_private, size_t ec_private_len,
        const TripleRatchetPublicKey& their_public
    );

    int encrypt(
        const uint8_t* plaintext, size_t plaintext_len,
        std::vector<uint8_t>& ciphertext_out
    );

    int advance_ratchet();

    int derive_next_chain();

    bool is_initialized() const { return initialized_; }

    uint32_t message_counter() const { return message_counter_; }

    void zero();

private:
    bool initialized_;
    std::array<uint8_t, 32> root_key_;
    TripleRatchetChainKey send_chain_;
    TripleRatchetPublicKey their_public_;
    secure::SecureBuffer ec_private_;
    uint32_t message_counter_;
    uint32_t ratchet_step_count_;
};

} // namespace protocol
} // namespace enchant

#endif

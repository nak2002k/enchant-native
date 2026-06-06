#include "protocol/triple_ratchet.hpp"
#include "primitives/x25519.hpp"
#include "primitives/hkdf.hpp"
#include "primitives/hmac.hpp"
#include "primitives/xchacha20.hpp"
#include "primitives/hash.hpp"
#include "primitives/random.hpp"
#include "pq/ml_kem.hpp"
#include <sodium.h>
#include <cstring>

namespace enchant {
namespace protocol {

constexpr const char* TR_RATCHET_DH_LABEL = "enchant_TripleRatchet_DH_20240101";
constexpr const char* TR_RATCHET_PQ_LABEL = "enchant_TripleRatchet_PQ_20240101";
constexpr const char* TR_RATCHET_COMBINE_LABEL = "enchant_TripleRatchet_Combine_20240101";
constexpr const char* TR_CHAIN_LABEL = "enchant_TripleRatchet_ChainKey_20240101";
constexpr const char* TR_MESSAGE_LABEL = "enchant_TripleRatchet_MessageKey_20240101";

TripleRatchet::TripleRatchet()
    : initialized_(false), receive_counter_(0), previous_counter_(0) {
    root_key_.fill(0);
    send_chain_.key.fill(0);
    send_chain_.counter = 0;
    receive_chain_.key.fill(0);
    receive_chain_.counter = 0;
}

int TripleRatchet::initialize(
    const uint8_t* shared_secret, size_t shared_secret_len,
    const TripleRatchetPublicKey& their_public,
    const uint8_t* our_ec_private, size_t ec_private_len,
    bool is_alice) {
    if (!shared_secret || shared_secret_len < 32) return ENCHANT_ERROR_INVALID_KEY_SIZE;
    if (!our_ec_private || ec_private_len < 32) return ENCHANT_ERROR_INVALID_KEY_SIZE;

    root_key_.fill(0);
    memcpy(root_key_.data(), shared_secret, 32);

    their_public_ = their_public;

    our_secret_.ec_private = secure::SecureBuffer(our_ec_private, ec_private_len);
    our_secret_.pq_private = secure::SecureBuffer(32);
    randombytes_buf(our_secret_.pq_private.data(), 32);

    our_public_.ec_public.fill(0);
    int rc = primitives::x25519_pubkey_from_priv(our_ec_private, our_public_.ec_public.data());
    if (rc != ENCHANT_SUCCESS) return rc;

    our_public_.pq_public.resize(32);
    randombytes_buf(our_public_.pq_public.data(), 32);

    uint8_t salt[32] = {0};
    uint8_t derived[96];
    rc = primitives::hkdf_derive(shared_secret, 32, salt, 32,
                                      reinterpret_cast<const uint8_t*>(TR_RATCHET_COMBINE_LABEL),
                                      strlen(TR_RATCHET_COMBINE_LABEL), derived, 96);
    sodium_memzero(salt, 32);
    if (rc != ENCHANT_SUCCESS) return rc;

    memcpy(root_key_.data(), derived, 32);
    if (is_alice) {
        memcpy(send_chain_.key.data(), derived + 32, 32);
        memcpy(receive_chain_.key.data(), derived + 64, 32);
    } else {
        memcpy(send_chain_.key.data(), derived + 64, 32);
        memcpy(receive_chain_.key.data(), derived + 32, 32);
    }
    sodium_memzero(derived, 96);

    send_chain_.counter = 0;
    receive_chain_.counter = 0;
    initialized_ = true;
    return ENCHANT_SUCCESS;
}

int TripleRatchet::derive_message_key(
    const TripleRatchetChainKey& chain,
    TripleRatchetMessageKey& message_key_out,
    TripleRatchetChainKey& next_chain_out) const {
    uint8_t hmac_input = 0x01;
    int rc = primitives::hmac_sha256(chain.key.data(), TRIPLE_RATCHET_CHAIN_KEY_SIZE,
                                     &hmac_input, 1, next_chain_out.key.data());
    if (rc != ENCHANT_SUCCESS) return rc;

    hmac_input = 0x02;
    uint8_t msg_seed[32];
    rc = primitives::hmac_sha256(chain.key.data(), TRIPLE_RATCHET_CHAIN_KEY_SIZE,
                                  &hmac_input, 1, msg_seed);
    if (rc != ENCHANT_SUCCESS) return rc;

    uint8_t salt[32] = {0};
    uint8_t derived[56];
    rc = primitives::hkdf_derive(msg_seed, 32, salt, 32,
                                  reinterpret_cast<const uint8_t*>(TR_MESSAGE_LABEL),
                                  strlen(TR_MESSAGE_LABEL), derived, 56);
    sodium_memzero(salt, 32);
    sodium_memzero(msg_seed, 32);
    if (rc != ENCHANT_SUCCESS) return rc;

    memcpy(message_key_out.key.data(), derived, 32);
    memcpy(message_key_out.nonce.data(), derived + 32, 24);
    sodium_memzero(derived, 56);

    message_key_out.counter = chain.counter;
    next_chain_out.counter = chain.counter + 1;

    return ENCHANT_SUCCESS;
}

int TripleRatchet::dh_ratchet_step(
    const uint8_t* their_ec_public, size_t ec_public_len) {
    if (!their_ec_public || ec_public_len < 32) return ENCHANT_ERROR_INVALID_KEY_SIZE;

    uint8_t ec_shared[32];
    int rc = primitives::x25519_dh(our_secret_.ec_private.data(), their_ec_public, ec_shared);
    if (rc != ENCHANT_SUCCESS) {
        sodium_memzero(ec_shared, 32);
        return rc;
    }

    uint8_t new_priv[32];
    randombytes_buf(new_priv, 32);
    our_secret_.ec_private = secure::SecureBuffer(new_priv, 32);
    sodium_memzero(new_priv, 32);

    rc = primitives::x25519_pubkey_from_priv(our_secret_.ec_private.data(),
                                              our_public_.ec_public.data());
    if (rc != ENCHANT_SUCCESS) {
        sodium_memzero(ec_shared, 32);
        return rc;
    }

    uint8_t pq_shared[32] = {0};
    TripleRatchetChainKey new_send, new_recv;
    rc = combine_ratchet_keys(ec_shared, 32, pq_shared, 32,
                               root_key_, new_send, new_recv);
    sodium_memzero(ec_shared, 32);
    sodium_memzero(pq_shared, 32);
    if (rc != ENCHANT_SUCCESS) return rc;

    send_chain_ = new_send;
    receive_chain_ = new_recv;

    return ENCHANT_SUCCESS;
}

int TripleRatchet::pq_ratchet_step(
    const uint8_t* their_pq_public, size_t pq_public_len) {
    if (!their_pq_public || pq_public_len < 32) return ENCHANT_ERROR_INVALID_KEY_SIZE;

    uint8_t pq_shared[32] = {0};
    randombytes_buf(pq_shared, 32);

    uint8_t ec_shared[32] = {0};
    TripleRatchetChainKey new_send, new_recv;
    int rc = combine_ratchet_keys(ec_shared, 32, pq_shared, 32,
                                   root_key_, new_send, new_recv);
    sodium_memzero(ec_shared, 32);
    sodium_memzero(pq_shared, 32);
    if (rc != ENCHANT_SUCCESS) return rc;

    send_chain_ = new_send;
    receive_chain_ = new_recv;

    (void)their_pq_public;
    return ENCHANT_SUCCESS;
}

int TripleRatchet::combine_ratchet_keys(
    const uint8_t* ec_shared, size_t ec_len,
    const uint8_t* pq_shared, size_t pq_len,
    std::array<uint8_t, TRIPLE_RATCHET_ROOT_KEY_SIZE>& new_root,
    TripleRatchetChainKey& new_send_chain,
    TripleRatchetChainKey& new_receive_chain) const {
    if (!ec_shared || ec_len < 32) return ENCHANT_ERROR_INVALID_KEY_SIZE;
    if (!pq_shared || pq_len < 32) return ENCHANT_ERROR_INVALID_KEY_SIZE;

    uint8_t combined[64];
    memcpy(combined, ec_shared, 32);
    memcpy(combined + 32, pq_shared, 32);

    uint8_t salt[32] = {0};
    uint8_t derived[96];
    int rc = primitives::hkdf_derive(combined, 64, salt, 32,
                                      reinterpret_cast<const uint8_t*>(TR_RATCHET_COMBINE_LABEL),
                                      strlen(TR_RATCHET_COMBINE_LABEL), derived, 96);
    sodium_memzero(salt, 32);
    sodium_memzero(combined, 64);
    if (rc != ENCHANT_SUCCESS) return rc;

    memcpy(new_root.data(), derived, 32);
    memcpy(new_send_chain.key.data(), derived + 32, 32);
    memcpy(new_receive_chain.key.data(), derived + 64, 32);
    sodium_memzero(derived, 96);

    new_send_chain.counter = 0;
    new_receive_chain.counter = 0;

    return ENCHANT_SUCCESS;
}

int TripleRatchet::ratchet_forward() {
    if (!initialized_) return ENCHANT_ERROR_INTERNAL;

    int rc = dh_ratchet_step(their_public_.ec_public.data(), their_public_.ec_public.size());
    if (rc != ENCHANT_SUCCESS) return rc;

    previous_counter_ = receive_counter_;
    receive_counter_ = 0;

    return ENCHANT_SUCCESS;
}

int TripleRatchet::encrypt(
    const uint8_t* plaintext, size_t plaintext_len,
    std::vector<uint8_t>& ciphertext_out) {
    if (!initialized_) return ENCHANT_ERROR_INTERNAL;
    if (!plaintext && plaintext_len > 0) return ENCHANT_ERROR_NULL_POINTER;

    TripleRatchetMessageKey msg_key;
    TripleRatchetChainKey next_chain;
    int rc = derive_message_key(send_chain_, msg_key, next_chain);
    if (rc != ENCHANT_SUCCESS) return rc;

    send_chain_ = next_chain;

    ciphertext_out.resize(4 + plaintext_len + 16);

    uint8_t counter_bytes[4];
    counter_bytes[0] = (msg_key.counter >> 24) & 0xFF;
    counter_bytes[1] = (msg_key.counter >> 16) & 0xFF;
    counter_bytes[2] = (msg_key.counter >> 8) & 0xFF;
    counter_bytes[3] = msg_key.counter & 0xFF;
    memcpy(ciphertext_out.data(), counter_bytes, 4);

    rc = primitives::xchacha20_encrypt(
        plaintext, plaintext_len,
        msg_key.key.data(), msg_key.nonce.data(),
        ciphertext_out.data() + 4, ciphertext_out.size() - 4);

    if (rc != ENCHANT_SUCCESS) {
        ciphertext_out.clear();
        return rc;
    }

    sodium_memzero(msg_key.key.data(), TRIPLE_RATCHET_MESSAGE_KEY_SIZE);
    sodium_memzero(msg_key.nonce.data(), 24);

    return ENCHANT_SUCCESS;
}

int TripleRatchet::decrypt(
    const uint8_t* ciphertext, size_t ciphertext_len,
    std::vector<uint8_t>& plaintext_out) {
    if (!initialized_) return ENCHANT_ERROR_INTERNAL;
    if (!ciphertext) return ENCHANT_ERROR_NULL_POINTER;
    if (ciphertext_len < 4 + 16) return ENCHANT_ERROR_CIPHERTEXT_TOO_SHORT;

    uint32_t message_counter;
    memcpy(&message_counter, ciphertext, 4);
    message_counter = ((message_counter >> 24) & 0xFF) |
                      ((message_counter >> 8) & 0xFF00) |
                      ((message_counter << 8) & 0xFF0000) |
                      ((message_counter << 24) & 0xFF000000);

    if (message_counter < receive_chain_.counter) {
        for (auto it = skipped_keys_.begin(); it != skipped_keys_.end(); ++it) {
            if (it->first == message_counter) {
                TripleRatchetMessageKey& msg_key = it->second;
                plaintext_out.resize(ciphertext_len - 4 - 16);
                int rc = primitives::xchacha20_decrypt(
                    ciphertext + 4, ciphertext_len - 4,
                    msg_key.key.data(), msg_key.nonce.data(),
                    plaintext_out.data(), plaintext_out.size());
                if (rc != ENCHANT_SUCCESS) {
                    plaintext_out.clear();
                    return rc;
                }
                skipped_keys_.erase(it);
                return ENCHANT_SUCCESS;
            }
        }
        return ENCHANT_ERROR_REPLAY_DETECTED;
    }

    if (message_counter > receive_chain_.counter + TRIPLE_RATCHET_MAX_SKIP) {
        return ENCHANT_ERROR_MAX_SKIPPED_KEYS;
    }

    while (receive_chain_.counter < message_counter) {
        TripleRatchetMessageKey skipped_key;
        TripleRatchetChainKey next_chain;
        int rc = derive_message_key(receive_chain_, skipped_key, next_chain);
        if (rc != ENCHANT_SUCCESS) return rc;
        skipped_keys_.emplace_back(skipped_key.counter, skipped_key);
        receive_chain_ = next_chain;
    }

    TripleRatchetMessageKey msg_key;
    TripleRatchetChainKey next_chain;
    int rc = derive_message_key(receive_chain_, msg_key, next_chain);
    if (rc != ENCHANT_SUCCESS) return rc;
    receive_chain_ = next_chain;

    plaintext_out.resize(ciphertext_len - 4 - 16);
    rc = primitives::xchacha20_decrypt(
        ciphertext + 4, ciphertext_len - 4,
        msg_key.key.data(), msg_key.nonce.data(),
        plaintext_out.data(), plaintext_out.size());

    if (rc != ENCHANT_SUCCESS) {
        plaintext_out.clear();
        return rc;
    }

    sodium_memzero(msg_key.key.data(), TRIPLE_RATCHET_MESSAGE_KEY_SIZE);
    sodium_memzero(msg_key.nonce.data(), 24);

    return ENCHANT_SUCCESS;
}

int TripleRatchet::skip_message_keys(uint32_t until_counter) {
    if (until_counter > receive_chain_.counter + TRIPLE_RATCHET_MAX_SKIP) {
        return ENCHANT_ERROR_MAX_SKIPPED_KEYS;
    }

    while (receive_chain_.counter < until_counter) {
        TripleRatchetMessageKey skipped_key;
        TripleRatchetChainKey next_chain;
        int rc = derive_message_key(receive_chain_, skipped_key, next_chain);
        if (rc != ENCHANT_SUCCESS) return rc;
        skipped_keys_.emplace_back(skipped_key.counter, skipped_key);
        receive_chain_ = next_chain;
    }

    return ENCHANT_SUCCESS;
}

void TripleRatchet::zero() {
    sodium_memzero(root_key_.data(), TRIPLE_RATCHET_ROOT_KEY_SIZE);
    sodium_memzero(send_chain_.key.data(), TRIPLE_RATCHET_CHAIN_KEY_SIZE);
    sodium_memzero(receive_chain_.key.data(), TRIPLE_RATCHET_CHAIN_KEY_SIZE);
    our_secret_.ec_private.zero();
    our_secret_.pq_private.zero();
    initialized_ = false;
    receive_counter_ = 0;
    previous_counter_ = 0;
    for (auto& [counter, key] : skipped_keys_) {
        sodium_memzero(key.key.data(), TRIPLE_RATCHET_MESSAGE_KEY_SIZE);
        sodium_memzero(key.nonce.data(), 24);
    }
    skipped_keys_.clear();
}

} // namespace protocol
} // namespace enchant

#include "protocol/triple_ratchet_outgoing.hpp"
#include "primitives/x25519.hpp"
#include "primitives/hkdf.hpp"
#include "primitives/hmac.hpp"
#include "primitives/xchacha20.hpp"
#include "primitives/random.hpp"
#include <sodium.h>
#include <cstring>

namespace enchant {
namespace protocol {

constexpr const char* OT_RATCHET_LABEL = "enchant_OutgoingTripleRatchet_20240101";
constexpr const char* OT_CHAIN_LABEL = "enchant_OutgoingTripleRatchet_ChainKey_20240101";
constexpr const char* OT_MESSAGE_LABEL = "enchant_OutgoingTripleRatchet_MessageKey_20240101";

OutgoingTripleRatchet::OutgoingTripleRatchet()
    : initialized_(false), message_counter_(0), ratchet_step_count_(0) {
    root_key_.fill(0);
    send_chain_.key.fill(0);
    send_chain_.counter = 0;
}

int OutgoingTripleRatchet::initialize(
    const uint8_t* root_key, size_t root_key_len,
    const uint8_t* ec_private, size_t ec_private_len,
    const TripleRatchetPublicKey& their_public) {
    if (!root_key || root_key_len < 32) return ENCHANT_ERROR_INVALID_KEY_SIZE;
    if (!ec_private || ec_private_len < 32) return ENCHANT_ERROR_INVALID_KEY_SIZE;

    memcpy(root_key_.data(), root_key, 32);
    ec_private_ = secure::SecureBuffer(ec_private, ec_private_len);
    their_public_ = their_public;

    uint8_t dh_shared[32];
    int rc = primitives::x25519_dh(ec_private, their_public.ec_public.data(), dh_shared);
    if (rc != ENCHANT_SUCCESS) {
        sodium_memzero(dh_shared, 32);
        return rc;
    }

    uint8_t salt[32] = {0};
    uint8_t derived[64];
    rc = primitives::hkdf_derive(dh_shared, 32, salt, 32,
                                  reinterpret_cast<const uint8_t*>(OT_RATCHET_LABEL),
                                  strlen(OT_RATCHET_LABEL), derived, 64);
    sodium_memzero(salt, 32);
    sodium_memzero(dh_shared, 32);
    if (rc != ENCHANT_SUCCESS) return rc;

    memcpy(root_key_.data(), derived, 32);
    memcpy(send_chain_.key.data(), derived + 32, 32);
    sodium_memzero(derived, 64);

    send_chain_.counter = 0;
    message_counter_ = 0;
    ratchet_step_count_ = 0;
    initialized_ = true;

    return ENCHANT_SUCCESS;
}

int OutgoingTripleRatchet::derive_next_chain() {
    if (!initialized_) return ENCHANT_ERROR_INTERNAL;

    uint8_t new_priv[32];
    randombytes_buf(new_priv, 32);
    ec_private_ = secure::SecureBuffer(new_priv, 32);
    sodium_memzero(new_priv, 32);

    uint8_t new_pub[32];
    int rc = primitives::x25519_pubkey_from_priv(ec_private_.data(), new_pub);
    if (rc != ENCHANT_SUCCESS) {
        sodium_memzero(new_pub, 32);
        return rc;
    }

    uint8_t dh_shared[32];
    rc = primitives::x25519_dh(ec_private_.data(), their_public_.ec_public.data(), dh_shared);
    sodium_memzero(new_pub, 32);
    if (rc != ENCHANT_SUCCESS) {
        sodium_memzero(dh_shared, 32);
        return rc;
    }

    uint8_t salt[32] = {0};
    uint8_t derived[64];
    rc = primitives::hkdf_derive(dh_shared, 32, salt, 32,
                                  reinterpret_cast<const uint8_t*>(OT_RATCHET_LABEL),
                                  strlen(OT_RATCHET_LABEL), derived, 64);
    sodium_memzero(salt, 32);
    sodium_memzero(dh_shared, 32);
    if (rc != ENCHANT_SUCCESS) return rc;

    memcpy(root_key_.data(), derived, 32);
    memcpy(send_chain_.key.data(), derived + 32, 32);
    sodium_memzero(derived, 64);

    send_chain_.counter = 0;
    ratchet_step_count_++;

    return ENCHANT_SUCCESS;
}

int OutgoingTripleRatchet::encrypt(
    const uint8_t* plaintext, size_t plaintext_len,
    std::vector<uint8_t>& ciphertext_out) {
    if (!initialized_) return ENCHANT_ERROR_INTERNAL;
    if (!plaintext && plaintext_len > 0) return ENCHANT_ERROR_NULL_POINTER;

    uint8_t chain_hmac[32];
    int rc = primitives::hmac_sha256(send_chain_.key.data(), 32,
                                      reinterpret_cast<const uint8_t*>(OT_CHAIN_LABEL),
                                      strlen(OT_CHAIN_LABEL), chain_hmac);
    if (rc != ENCHANT_SUCCESS) return rc;

    uint8_t msg_seed[32];
    uint8_t hmac_input = 0x01;
    rc = primitives::hmac_sha256(chain_hmac, 32, &hmac_input, 1, msg_seed);
    sodium_memzero(chain_hmac, 32);
    if (rc != ENCHANT_SUCCESS) {
        sodium_memzero(msg_seed, 32);
        return rc;
    }

    uint8_t next_chain[32];
    hmac_input = 0x02;
    rc = primitives::hmac_sha256(send_chain_.key.data(), 32, &hmac_input, 1, next_chain);
    if (rc != ENCHANT_SUCCESS) {
        sodium_memzero(msg_seed, 32);
        sodium_memzero(next_chain, 32);
        return rc;
    }
    memcpy(send_chain_.key.data(), next_chain, 32);
    sodium_memzero(next_chain, 32);

    uint8_t salt[32] = {0};
    uint8_t derived[56];
    rc = primitives::hkdf_derive(msg_seed, 32, salt, 32,
                                  reinterpret_cast<const uint8_t*>(OT_MESSAGE_LABEL),
                                  strlen(OT_MESSAGE_LABEL), derived, 56);
    sodium_memzero(salt, 32);
    sodium_memzero(msg_seed, 32);
    if (rc != ENCHANT_SUCCESS) return rc;

    uint8_t msg_key[32], msg_nonce[24];
    memcpy(msg_key, derived, 32);
    memcpy(msg_nonce, derived + 32, 24);
    sodium_memzero(derived, 56);

    ciphertext_out.resize(4 + plaintext_len + 16);

    uint8_t counter_bytes[4];
    counter_bytes[0] = (message_counter_ >> 24) & 0xFF;
    counter_bytes[1] = (message_counter_ >> 16) & 0xFF;
    counter_bytes[2] = (message_counter_ >> 8) & 0xFF;
    counter_bytes[3] = message_counter_ & 0xFF;
    memcpy(ciphertext_out.data(), counter_bytes, 4);

    rc = primitives::xchacha20_encrypt(
        plaintext, plaintext_len,
        msg_key, msg_nonce,
        ciphertext_out.data() + 4, ciphertext_out.size() - 4);

    sodium_memzero(msg_key, 32);
    sodium_memzero(msg_nonce, 24);

    if (rc != ENCHANT_SUCCESS) {
        ciphertext_out.clear();
        return rc;
    }

    message_counter_++;
    send_chain_.counter++;

    return ENCHANT_SUCCESS;
}

int OutgoingTripleRatchet::advance_ratchet() {
    return derive_next_chain();
}

void OutgoingTripleRatchet::zero() {
    sodium_memzero(root_key_.data(), 32);
    sodium_memzero(send_chain_.key.data(), 32);
    ec_private_.zero();
    initialized_ = false;
    message_counter_ = 0;
    ratchet_step_count_ = 0;
}

} // namespace protocol
} // namespace enchant

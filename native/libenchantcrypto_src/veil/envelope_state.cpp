#include "envelope_state.hpp"
#include "primitives/x25519.hpp"
#include "primitives/xchacha20.hpp"
#include "primitives/hkdf.hpp"
#include "primitives/hmac.hpp"
#include "primitives/hash.hpp"
#include "primitives/random.hpp"
#include <sodium.h>
#include <cstring>
#include <cstdio>

namespace enchant {
namespace veil {

static constexpr size_t ENVELOPE_HEADER_VERSION_OFFSET = 0;
static constexpr size_t ENVELOPE_HEADER_DH_OFFSET = 1;
static constexpr size_t ENVELOPE_HEADER_NS_OFFSET = 33;
static constexpr size_t ENVELOPE_HEADER_PN_OFFSET = 37;

static inline void write_u32_be(uint8_t* dest, uint32_t value) {
    dest[0] = static_cast<uint8_t>((value >> 24) & 0xFF);
    dest[1] = static_cast<uint8_t>((value >> 16) & 0xFF);
    dest[2] = static_cast<uint8_t>((value >> 8) & 0xFF);
    dest[3] = static_cast<uint8_t>(value & 0xFF);
}

static inline uint32_t read_u32_be(const uint8_t* src) {
    return (static_cast<uint32_t>(src[0]) << 24) |
           (static_cast<uint32_t>(src[1]) << 16) |
           (static_cast<uint32_t>(src[2]) << 8) |
           static_cast<uint32_t>(src[3]);
}

ConsumedEntry::ConsumedEntry() : dh_public(ENVELOPE_DH_KEY_SIZE), message_number(0) {}
ConsumedEntry::ConsumedEntry(secure::SecureBuffer dh, uint32_t num)
    : dh_public(std::move(dh)), message_number(num) {}

EnvelopeState::EnvelopeState()
    : sending_message_number_(0),
      previous_counter_(0),
      our_identity_(32),
      their_identity_(32),
      has_our_identity_(false),
      has_their_identity_(false),
      pqr_key_(32),
      has_pqr_key_(false),
      is_initialized_(false) {}

EnvelopeState::~EnvelopeState() {
    zero();
}

EnvelopeState::EnvelopeState(EnvelopeState&& other) noexcept
    : root_key_(std::move(other.root_key_)),
      sender_chain_(std::move(other.sender_chain_)),
      sending_message_number_(other.sending_message_number_),
      receiver_chains_(std::move(other.receiver_chains_)),
      previous_counter_(other.previous_counter_),
      pending_skipped_keys_(std::move(other.pending_skipped_keys_)),
      consumed_keys_(std::move(other.consumed_keys_)),
      our_identity_(std::move(other.our_identity_)),
      their_identity_(std::move(other.their_identity_)),
      has_our_identity_(other.has_our_identity_),
      has_their_identity_(other.has_their_identity_),
      pqr_key_(std::move(other.pqr_key_)),
      has_pqr_key_(other.has_pqr_key_),
      is_initialized_(other.is_initialized_) {
    other.sending_message_number_ = 0;
    other.previous_counter_ = 0;
    other.has_our_identity_ = false;
    other.has_their_identity_ = false;
    other.has_pqr_key_ = false;
    other.is_initialized_ = false;
}

EnvelopeState& EnvelopeState::operator=(EnvelopeState&& other) noexcept {
    if (this != &other) {
        zero();
        root_key_ = std::move(other.root_key_);
        sender_chain_ = std::move(other.sender_chain_);
        sending_message_number_ = other.sending_message_number_;
        receiver_chains_ = std::move(other.receiver_chains_);
        previous_counter_ = other.previous_counter_;
        pending_skipped_keys_ = std::move(other.pending_skipped_keys_);
        consumed_keys_ = std::move(other.consumed_keys_);
        our_identity_ = std::move(other.our_identity_);
        their_identity_ = std::move(other.their_identity_);
        has_our_identity_ = other.has_our_identity_;
        has_their_identity_ = other.has_their_identity_;
        pqr_key_ = std::move(other.pqr_key_);
        has_pqr_key_ = other.has_pqr_key_;
        is_initialized_ = other.is_initialized_;
        other.sending_message_number_ = 0;
        other.previous_counter_ = 0;
        other.has_our_identity_ = false;
        other.has_their_identity_ = false;
        other.has_pqr_key_ = false;
        other.is_initialized_ = false;
    }
    return *this;
}

int EnvelopeState::init(const uint8_t* root_key, const uint8_t* sending_chain_key,
                       const uint8_t* their_x25519_public,
                       const uint8_t* receiving_chain_key,
                       const uint8_t* our_dh_public, const uint8_t* our_dh_private,
                       const uint8_t* our_identity, const uint8_t* their_identity,
                       const uint8_t* pqr_key) {
    if (!root_key || !sending_chain_key)
        return ENCHANT_ERROR_NULL_POINTER;

    root_key_ = RootKey(root_key);

    if (our_dh_public && our_dh_private) {
        memcpy(sender_chain_.x25519_public.data(), our_dh_public, ENVELOPE_DH_KEY_SIZE);
        memcpy(sender_chain_.x25519_private.data(), our_dh_private, ENVELOPE_DH_KEY_SIZE);
    } else {
        int rc = enchant::primitives::x25519_keypair(
            sender_chain_.x25519_public.data(),
            sender_chain_.x25519_private.data());
        if (rc != ENCHANT_SUCCESS) return rc;
    }

    sender_chain_.chain_key = ChainKey(sending_chain_key, 0);

    ReceiverChain rc_chain;
    if (their_x25519_public) {
        memcpy(rc_chain.sender_x25519_key.data(), their_x25519_public, ENVELOPE_DH_KEY_SIZE);

        const uint8_t* rck = receiving_chain_key ? receiving_chain_key : sending_chain_key;
        if (our_dh_private && their_x25519_public) {
            RootKey dummy_root;
            ChainKey derived_rck;
            int rc = root_key_.create_chain(our_dh_private, their_x25519_public,
                                            dummy_root, derived_rck);
            if (rc == ENCHANT_SUCCESS) {
                memcpy(rc_chain.chain_key.data(), derived_rck.key(), ENVELOPE_KEY_SIZE);
            } else {
                memcpy(rc_chain.chain_key.data(), rck, ENVELOPE_KEY_SIZE);
            }
        } else {
            memcpy(rc_chain.chain_key.data(), rck, ENVELOPE_KEY_SIZE);
        }
        rc_chain.chain_index = 0;
        receiver_chains_.push_back(std::move(rc_chain));
    }

    if (our_dh_public && our_dh_private && their_x25519_public) {
        RootKey new_root;
        ChainKey derived_ck;
        int rc = root_key_.create_chain(our_dh_private, their_x25519_public,
                                        new_root, derived_ck);
        if (rc == ENCHANT_SUCCESS) {
            root_key_ = std::move(new_root);
            sender_chain_.chain_key = std::move(derived_ck);
        }
    }

    sending_message_number_ = 0;
    previous_counter_ = 0;

    pending_skipped_keys_.clear();

    if (our_identity) {
        memcpy(our_identity_.data(), our_identity, ENVELOPE_KEY_SIZE);
        has_our_identity_ = true;
    }
    if (their_identity) {
        memcpy(their_identity_.data(), their_identity, ENVELOPE_KEY_SIZE);
        has_their_identity_ = true;
    }
    if (pqr_key) {
        memcpy(pqr_key_.data(), pqr_key, ENVELOPE_KEY_SIZE);
        has_pqr_key_ = true;
    }

    is_initialized_ = true;
    return ENCHANT_SUCCESS;
}

void EnvelopeState::set_our_identity(const uint8_t* identity) {
    if (identity) {
        memcpy(our_identity_.data(), identity, ENVELOPE_KEY_SIZE);
        has_our_identity_ = true;
    }
}

void EnvelopeState::set_their_identity(const uint8_t* identity) {
    if (identity) {
        memcpy(their_identity_.data(), identity, ENVELOPE_KEY_SIZE);
        has_their_identity_ = true;
    }
}

void EnvelopeState::set_pqr_key(const uint8_t* pqr_key) {
    if (pqr_key) {
        memcpy(pqr_key_.data(), pqr_key, ENVELOPE_KEY_SIZE);
        has_pqr_key_ = true;
    }
}

int EnvelopeState::get_their_identity(uint8_t* identity) const {
    if (!identity) return ENCHANT_ERROR_NULL_POINTER;
    if (!has_their_identity_) return ENCHANT_ERROR_SESSION_STATE_INVALID;
    memcpy(identity, their_identity_.data(), ENVELOPE_KEY_SIZE);
    return ENCHANT_SUCCESS;
}

int EnvelopeState::get_our_identity(uint8_t* identity) const {
    if (!identity) return ENCHANT_ERROR_NULL_POINTER;
    if (!has_our_identity_) return ENCHANT_ERROR_SESSION_STATE_INVALID;
    memcpy(identity, our_identity_.data(), ENVELOPE_KEY_SIZE);
    return ENCHANT_SUCCESS;
}

bool EnvelopeState::has_their_identity() const { return has_their_identity_; }
bool EnvelopeState::has_our_identity() const { return has_our_identity_; }

SessionUsability EnvelopeState::usability() const {
    SessionUsability flags = SessionUsability::NONE;
    if (!is_initialized_) return flags;

    flags = flags | SessionUsability::CAN_DECRYPT;
    flags = flags | SessionUsability::CAN_ENCRYPT;

    if (has_our_identity_ && has_their_identity_) {
        flags = flags | SessionUsability::IDENTITY_VERIFIED;
    }
    return flags;
}

void EnvelopeState::zero() {
    root_key_ = RootKey();
    sender_chain_ = SenderChain();
    sending_message_number_ = 0;
    receiver_chains_.clear();
    previous_counter_ = 0;
    pending_skipped_keys_.clear();
    consumed_keys_.clear();
    our_identity_.zero();
    their_identity_.zero();
    has_our_identity_ = false;
    has_their_identity_ = false;
    pqr_key_.zero();
    has_pqr_key_ = false;
    is_initialized_ = false;
}

std::string EnvelopeState::make_key_id(const uint8_t* dh_public_key, uint32_t msg_num) const {
    uint8_t hash[32];
    enchant::primitives::sha256(dh_public_key, 32, hash);
    char id[73];
    int pos = 0;
    for (int i = 0; i < 32; i++) {
        pos += snprintf(id + pos, sizeof(id) - pos, "%02x", hash[i]);
    }
    snprintf(id + pos, sizeof(id) - pos, ":%u", msg_num);
    return std::string(id);
}

int EnvelopeState::find_receiver_chain_index(const uint8_t* their_ephemeral) const {
    for (size_t i = 0; i < receiver_chains_.size(); i++) {
        if (sodium_memcmp(receiver_chains_[i].sender_x25519_key.data(),
                          their_ephemeral, ENVELOPE_DH_KEY_SIZE) == 0) {
            return static_cast<int>(i);
        }
    }
    return -1;
}

ReceiverChain* EnvelopeState::ensure_receiver_chain(const uint8_t* their_ephemeral) {
    int idx = find_receiver_chain_index(their_ephemeral);
    if (idx >= 0) {
        return &receiver_chains_[idx];
    }
    return nullptr;
}

void EnvelopeState::set_receiver_chain_key(const uint8_t* their_ephemeral,
                                           const ChainKey& chain_key) {
    int idx = find_receiver_chain_index(their_ephemeral);
    if (idx >= 0) {
        memcpy(receiver_chains_[idx].chain_key.data(), chain_key.key(), ENVELOPE_KEY_SIZE);
        receiver_chains_[idx].chain_index = chain_key.index();
    }
}

int EnvelopeState::store_skipped_key(const uint8_t* their_ephemeral,
                                     const uint8_t* seed, uint32_t counter) {
    int idx = find_receiver_chain_index(their_ephemeral);
    if (idx < 0) return ENCHANT_ERROR_INTERNAL;

    SkippedKey sk(seed, counter);
    receiver_chains_[idx].message_keys.insert(
        receiver_chains_[idx].message_keys.begin(), std::move(sk));
    while (receiver_chains_[idx].message_keys.size() > ENVELOPE_MAX_MESSAGE_KEYS) {
        receiver_chains_[idx].message_keys.pop_back();
    }
    return ENCHANT_SUCCESS;
}

int EnvelopeState::take_skipped_key(const uint8_t* their_ephemeral, uint32_t counter,
                                    MessageKeys& out, const uint8_t* pqr_key_data) {
    int idx = find_receiver_chain_index(their_ephemeral);
    if (idx < 0) return ENCHANT_ERROR_INTERNAL;

    auto& keys = receiver_chains_[idx].message_keys;
    for (auto it = keys.begin(); it != keys.end(); ++it) {
        if (it->counter == counter) {
            int rc = MessageKeys::derive(it->seed.data(), pqr_key_data, counter, out);
            keys.erase(it);
            return rc;
        }
    }
    return ENCHANT_ERROR_REPLAY_DETECTED;
}

int EnvelopeState::consume_message_key(const uint8_t* their_ephemeral, uint32_t counter,
                                       const uint8_t* pqr_key_data,
                                       MessageKeys& out_keys) {
    int idx = find_receiver_chain_index(their_ephemeral);
    if (idx < 0) return ENCHANT_ERROR_INTERNAL;

    auto& chain = receiver_chains_[idx];
    uint32_t chain_index = chain.chain_index;

    if (chain_index > counter) {
        int rc = take_skipped_key(their_ephemeral, counter, out_keys, pqr_key_data);
        if (rc != ENCHANT_SUCCESS) return ENCHANT_ERROR_REPLAY_DETECTED;
        return ENCHANT_SUCCESS;
    }

    uint32_t jump = counter - chain_index;
    if (jump > ENVELOPE_MAX_FORWARD_JUMPS) {
        return ENCHANT_ERROR_DECRYPTION_FAILED;
    }

    uint8_t current_chain_key_buf[ENVELOPE_KEY_SIZE];
    memcpy(current_chain_key_buf, chain.chain_key.data(), ENVELOPE_KEY_SIZE);
    ChainKey ck(current_chain_key_buf, chain_index);

    while (ck.index() < counter) {
        uint8_t seed[MESSAGE_KEY_SIZE];
        ck.message_key_seed(seed);
        store_skipped_key(their_ephemeral, seed, ck.index());
        sodium_memzero(seed, sizeof(seed));
        ck = ck.next_chain_key();
    }

    uint8_t seed[MESSAGE_KEY_SIZE];
    ck.message_key_seed(seed);
    int rc = MessageKeys::derive(seed, pqr_key_data, counter, out_keys);
    sodium_memzero(seed, sizeof(seed));
    sodium_memzero(current_chain_key_buf, sizeof(current_chain_key_buf));
    if (rc != ENCHANT_SUCCESS) return rc;

    set_receiver_chain_key(their_ephemeral, ck.next_chain_key());
    return ENCHANT_SUCCESS;
}

int EnvelopeState::encrypt(const uint8_t* plaintext, size_t plaintext_len,
                          uint8_t* header, uint8_t* ciphertext, size_t* ciphertext_len) {
    if (!header || !ciphertext || !ciphertext_len)
        return ENCHANT_ERROR_NULL_POINTER;
    if (plaintext_len > 0 && !plaintext)
        return ENCHANT_ERROR_NULL_POINTER;

    header[ENVELOPE_HEADER_VERSION_OFFSET] = ENVELOPE_CURRENT_VERSION;
    memcpy(header + ENVELOPE_HEADER_DH_OFFSET,
           sender_chain_.x25519_public.data(), ENVELOPE_DH_KEY_SIZE);
    uint32_t ns = sending_message_number_;
    write_u32_be(header + ENVELOPE_HEADER_NS_OFFSET, ns);
    uint32_t pn = previous_counter_;
    write_u32_be(header + ENVELOPE_HEADER_PN_OFFSET, pn);

    uint8_t seed[MESSAGE_KEY_SIZE];
    int rc = sender_chain_.chain_key.message_key_seed(seed);
    if (rc != ENCHANT_SUCCESS) return rc;

    const uint8_t* pqr = has_pqr_key_ ? pqr_key_.data() : nullptr;
    MessageKeys msg_keys;
    rc = MessageKeys::derive(seed, pqr, sending_message_number_, msg_keys);
    sodium_memzero(seed, MESSAGE_KEY_SIZE);
    if (rc != ENCHANT_SUCCESS) return rc;

    sender_chain_.chain_key = sender_chain_.chain_key.next_chain_key();

    uint8_t full_nonce[ENVELOPE_FULL_NONCE_SIZE] = {0};
    memcpy(full_nonce, msg_keys.iv, IV_SIZE);

    if (has_our_identity_ && has_their_identity_) {
        uint8_t ad[ENVELOPE_KEY_SIZE * 2];
        memcpy(ad, our_identity_.data(), ENVELOPE_KEY_SIZE);
        memcpy(ad + ENVELOPE_KEY_SIZE, their_identity_.data(), ENVELOPE_KEY_SIZE);
        rc = enchant::primitives::xchacha20_encrypt_ad(
            plaintext, plaintext_len, ad, sizeof(ad),
            msg_keys.cipher_key, full_nonce, ciphertext, ciphertext_len);
    } else {
        size_t enc_capacity = plaintext_len + ENVELOPE_AEAD_TAG_SIZE;
        rc = enchant::primitives::xchacha20_encrypt(
            plaintext, plaintext_len,
            msg_keys.cipher_key, full_nonce, ciphertext, enc_capacity);
        *ciphertext_len = plaintext_len + ENVELOPE_AEAD_TAG_SIZE;
    }

    sodium_memzero(&msg_keys, sizeof(msg_keys));
    if (rc != ENCHANT_SUCCESS) return rc;

    sending_message_number_++;
    return ENCHANT_SUCCESS;
}

int EnvelopeState::envelope_rotate(const uint8_t* their_new_public_key) {
    previous_counter_ = sending_message_number_ > 0 ? sending_message_number_ - 1 : 0;

    RootKey saved_root = root_key_;

    RootKey new_root;
    ChainKey recv_chain;
    int rc = root_key_.create_chain(
        sender_chain_.x25519_private.data(), their_new_public_key,
        new_root, recv_chain);
    if (rc != ENCHANT_SUCCESS) return rc;

    root_key_ = std::move(new_root);

    ReceiverChain rc_chain;
    memcpy(rc_chain.sender_x25519_key.data(), their_new_public_key, ENVELOPE_DH_KEY_SIZE);
    memcpy(rc_chain.chain_key.data(), recv_chain.key(), ENVELOPE_KEY_SIZE);
    rc_chain.chain_index = recv_chain.index();
    receiver_chains_.push_back(std::move(rc_chain));
    while (receiver_chains_.size() > ENVELOPE_MAX_RECEIVER_CHAINS) {
        receiver_chains_.erase(receiver_chains_.begin());
    }

    secure::SecureBuffer new_sender_priv(ENVELOPE_DH_KEY_SIZE);
    secure::SecureBuffer new_sender_pub(ENVELOPE_DH_KEY_SIZE);
    rc = enchant::primitives::x25519_keypair(new_sender_pub.data(), new_sender_priv.data());
    if (rc != ENCHANT_SUCCESS) return rc;

    RootKey final_root;
    ChainKey sender_chain_key;
    rc = saved_root.create_chain(new_sender_priv.data(), their_new_public_key,
                                final_root, sender_chain_key);
    if (rc != ENCHANT_SUCCESS) return rc;

    root_key_ = std::move(final_root);
    memcpy(sender_chain_.x25519_public.data(), new_sender_pub.data(), ENVELOPE_DH_KEY_SIZE);
    memcpy(sender_chain_.x25519_private.data(), new_sender_priv.data(), ENVELOPE_DH_KEY_SIZE);
    sender_chain_.chain_key = std::move(sender_chain_key);

    return ENCHANT_SUCCESS;
}

int EnvelopeState::decrypt(const uint8_t* header, const uint8_t* ciphertext,
                          size_t ciphertext_len, uint8_t* plaintext, size_t* plaintext_len) {
    if (!header || !ciphertext || !plaintext || !plaintext_len)
        return ENCHANT_ERROR_NULL_POINTER;
    if (ciphertext_len < ENVELOPE_AEAD_TAG_SIZE)
        return ENCHANT_ERROR_CIPHERTEXT_TOO_SHORT;

    uint8_t ver = header[ENVELOPE_HEADER_VERSION_OFFSET];
    if (ver != ENVELOPE_CURRENT_VERSION) return ENCHANT_ERROR_INVALID_FORMAT;

    const uint8_t* their_dh_public = header + ENVELOPE_HEADER_DH_OFFSET;
    uint32_t ns = read_u32_be(header + ENVELOPE_HEADER_NS_OFFSET);

    std::string key_id = make_key_id(their_dh_public, ns);
    if (consumed_keys_.count(key_id)) {
        return ENCHANT_ERROR_REPLAY_DETECTED;
    }

    int existing_idx = find_receiver_chain_index(their_dh_public);
    bool new_x25519_key = (existing_idx < 0);

    const uint8_t* pqr_k = has_pqr_key_ ? pqr_key_.data() : nullptr;

    if (!new_x25519_key) {
        auto& chain = receiver_chains_[existing_idx];
        size_t saved_mk_count = chain.message_keys.size();
        uint32_t saved_chain_index = chain.chain_index;
        uint8_t saved_chain_key_buf[ENVELOPE_KEY_SIZE];
        memcpy(saved_chain_key_buf, chain.chain_key.data(), ENVELOPE_KEY_SIZE);

        MessageKeys msg_keys;
        int rc = consume_message_key(their_dh_public, ns, pqr_k, msg_keys);
        if (rc == ENCHANT_ERROR_REPLAY_DETECTED) {
            return ENCHANT_ERROR_REPLAY_DETECTED;
        }
        if (rc != ENCHANT_SUCCESS) return rc;

        uint8_t full_nonce[ENVELOPE_FULL_NONCE_SIZE] = {0};
        memcpy(full_nonce, msg_keys.iv, IV_SIZE);

        if (has_our_identity_ && has_their_identity_) {
            uint8_t ad[ENVELOPE_KEY_SIZE * 2];
            memcpy(ad, their_identity_.data(), ENVELOPE_KEY_SIZE);
            memcpy(ad + ENVELOPE_KEY_SIZE, our_identity_.data(), ENVELOPE_KEY_SIZE);
            rc = enchant::primitives::xchacha20_decrypt_ad(
                ciphertext, ciphertext_len, ad, sizeof(ad),
                msg_keys.cipher_key, full_nonce, plaintext, plaintext_len);
        } else {
            size_t decrypt_capacity = (ciphertext_len > ENVELOPE_AEAD_TAG_SIZE)
                ? ciphertext_len - ENVELOPE_AEAD_TAG_SIZE : 0;
            rc = enchant::primitives::xchacha20_decrypt(
                ciphertext, ciphertext_len,
                msg_keys.cipher_key, full_nonce, plaintext, decrypt_capacity);
            *plaintext_len = ciphertext_len - ENVELOPE_AEAD_TAG_SIZE;
        }

        sodium_memzero(&msg_keys, sizeof(msg_keys));
        if (rc != ENCHANT_SUCCESS) {
            chain.chain_index = saved_chain_index;
            memcpy(chain.chain_key.data(), saved_chain_key_buf, ENVELOPE_KEY_SIZE);
            while (chain.message_keys.size() > saved_mk_count) {
                chain.message_keys.pop_back();
            }
            return rc;
        }

        secure::SecureBuffer dh_copy(ENVELOPE_DH_KEY_SIZE);
        memcpy(dh_copy.data(), their_dh_public, ENVELOPE_DH_KEY_SIZE);
        consumed_keys_.emplace(key_id, ConsumedEntry(std::move(dh_copy), ns));

        return ENCHANT_SUCCESS;
    }

    uint8_t saved_root_key_buf[ENVELOPE_KEY_SIZE];
    memcpy(saved_root_key_buf, root_key_.key(), ENVELOPE_KEY_SIZE);
    uint8_t saved_sender_pub[ENVELOPE_DH_KEY_SIZE];
    uint8_t saved_sender_priv[ENVELOPE_DH_KEY_SIZE];
    uint8_t saved_sender_ck[ENVELOPE_KEY_SIZE];
    uint32_t saved_sender_ck_index = 0;
    memcpy(saved_sender_pub, sender_chain_.x25519_public.data(), ENVELOPE_DH_KEY_SIZE);
    memcpy(saved_sender_priv, sender_chain_.x25519_private.data(), ENVELOPE_DH_KEY_SIZE);
    memcpy(saved_sender_ck, sender_chain_.chain_key.key(), ENVELOPE_KEY_SIZE);
    saved_sender_ck_index = sender_chain_.chain_key.index();
    size_t saved_rc_count = receiver_chains_.size();
    uint32_t saved_prev_counter = previous_counter_;
    std::vector<size_t> saved_mk_counts;
    saved_mk_counts.reserve(receiver_chains_.size());
    for (const auto& rc : receiver_chains_) {
        saved_mk_counts.push_back(rc.message_keys.size());
    }

    if (!receiver_chains_.empty()) {
        int old_idx = find_receiver_chain_index(receiver_chains_[0].sender_x25519_key.data());
        (void)old_idx;
        if (receiver_chains_.size() > 0) {
            auto& old_chain = receiver_chains_.back();
            uint8_t current_ck_buf[ENVELOPE_KEY_SIZE];
            memcpy(current_ck_buf, old_chain.chain_key.data(), ENVELOPE_KEY_SIZE);
            ChainKey ck(current_ck_buf, old_chain.chain_index);
            uint32_t old_index = old_chain.chain_index;

            if (old_index < ns) {
                if (ns - old_index > ENVELOPE_MAX_FORWARD_JUMPS) {
                    return ENCHANT_ERROR_DECRYPTION_FAILED;
                }
                while (ck.index() < ns) {
                    uint8_t seed[MESSAGE_KEY_SIZE];
                    ck.message_key_seed(seed);
                    store_skipped_key(old_chain.sender_x25519_key.data(), seed, ck.index());
                    ck = ck.next_chain_key();
                }
            }
        }
    }

    int rc = envelope_rotate(their_dh_public);
    if (rc != ENCHANT_SUCCESS) return rc;

    MessageKeys msg_keys;
    rc = consume_message_key(their_dh_public, ns, pqr_k, msg_keys);
    if (rc != ENCHANT_SUCCESS) return rc;

    uint8_t full_nonce[ENVELOPE_FULL_NONCE_SIZE] = {0};
    memcpy(full_nonce, msg_keys.iv, IV_SIZE);

    if (has_our_identity_ && has_their_identity_) {
        uint8_t ad[ENVELOPE_KEY_SIZE * 2];
        memcpy(ad, their_identity_.data(), ENVELOPE_KEY_SIZE);
        memcpy(ad + ENVELOPE_KEY_SIZE, our_identity_.data(), ENVELOPE_KEY_SIZE);
        rc = enchant::primitives::xchacha20_decrypt_ad(
            ciphertext, ciphertext_len, ad, sizeof(ad),
            msg_keys.cipher_key, full_nonce, plaintext, plaintext_len);
    } else {
        size_t decrypt_capacity = (ciphertext_len > ENVELOPE_AEAD_TAG_SIZE)
            ? ciphertext_len - ENVELOPE_AEAD_TAG_SIZE : 0;
        rc = enchant::primitives::xchacha20_decrypt(
            ciphertext, ciphertext_len,
            msg_keys.cipher_key, full_nonce, plaintext, decrypt_capacity);
        *plaintext_len = ciphertext_len - ENVELOPE_AEAD_TAG_SIZE;
    }

    sodium_memzero(&msg_keys, sizeof(msg_keys));
    if (rc != ENCHANT_SUCCESS) {
        root_key_ = RootKey(saved_root_key_buf);
        memcpy(sender_chain_.x25519_public.data(), saved_sender_pub, ENVELOPE_DH_KEY_SIZE);
        memcpy(sender_chain_.x25519_private.data(), saved_sender_priv, ENVELOPE_DH_KEY_SIZE);
        sender_chain_.chain_key = ChainKey(saved_sender_ck, saved_sender_ck_index);
        while (receiver_chains_.size() > saved_rc_count) {
            receiver_chains_.pop_back();
        }
        for (size_t i = 0; i < receiver_chains_.size() && i < saved_mk_counts.size(); i++) {
            while (receiver_chains_[i].message_keys.size() > saved_mk_counts[i]) {
                receiver_chains_[i].message_keys.pop_back();
            }
        }
        previous_counter_ = saved_prev_counter;
        sodium_memzero(saved_sender_ck, ENVELOPE_KEY_SIZE);
        return rc;
    }

    secure::SecureBuffer dh_copy(ENVELOPE_DH_KEY_SIZE);
    memcpy(dh_copy.data(), their_dh_public, ENVELOPE_DH_KEY_SIZE);
    consumed_keys_.emplace(key_id, ConsumedEntry(std::move(dh_copy), ns));

    return ENCHANT_SUCCESS;
}

int EnvelopeState::get_failed_message_info(const uint8_t* header, uint32_t& failed_ns,
                                           uint8_t* failed_x25519_key) const {
    if (!header || !failed_x25519_key) return ENCHANT_ERROR_NULL_POINTER;
    failed_ns = read_u32_be(header + ENVELOPE_HEADER_NS_OFFSET);
    memcpy(failed_x25519_key, header + ENVELOPE_HEADER_DH_OFFSET, ENVELOPE_DH_KEY_SIZE);
    return ENCHANT_SUCCESS;
}

int EnvelopeState::process_decryption_error(const uint8_t* their_dh_public,
                                            const uint8_t* our_dh_private,
                                            const uint8_t* our_x25519_public) {
    if (!their_dh_public || !our_dh_private || !our_x25519_public)
        return ENCHANT_ERROR_NULL_POINTER;

    secure::SecureBuffer dh_out(ENVELOPE_KEY_SIZE);
    int rc = enchant::primitives::x25519_dh(our_dh_private, their_dh_public, dh_out.data());
    if (rc != ENCHANT_SUCCESS) return rc;

    uint8_t ikm[64];
    memcpy(ikm, root_key_.key(), ENVELOPE_KEY_SIZE);
    memcpy(ikm + ENVELOPE_KEY_SIZE, dh_out.data(), ENVELOPE_KEY_SIZE);

    uint8_t salt[ENVELOPE_HKDF_SALT_SIZE] = {0};
    const uint8_t info[] = "EnchantRecovery";
    uint8_t derived[64];
    rc = enchant::primitives::hkdf_derive(ikm, 64, salt, ENVELOPE_HKDF_SALT_SIZE,
                                           info, sizeof(info) - 1, derived, 64);
    sodium_memzero(ikm, sizeof(ikm));
    if (rc != ENCHANT_SUCCESS) {
        sodium_memzero(derived, sizeof(derived));
        return rc;
    }

    root_key_ = RootKey(derived);

    ReceiverChain rc_chain;
    memcpy(rc_chain.sender_x25519_key.data(), their_dh_public, ENVELOPE_DH_KEY_SIZE);
    memcpy(rc_chain.chain_key.data(), derived + ENVELOPE_KEY_SIZE, ENVELOPE_KEY_SIZE);
    rc_chain.chain_index = 0;
    receiver_chains_.push_back(std::move(rc_chain));
    while (receiver_chains_.size() > ENVELOPE_MAX_RECEIVER_CHAINS) {
        receiver_chains_.erase(receiver_chains_.begin());
    }

    sodium_memzero(derived, sizeof(derived));
    return ENCHANT_SUCCESS;
}

size_t EnvelopeState::serialized_size() const {
    size_t needed = 4
        + ENVELOPE_KEY_SIZE
        + ENVELOPE_DH_KEY_SIZE * 2
        + ENVELOPE_KEY_SIZE
        + sizeof(uint32_t)
        + sizeof(uint32_t) * 2
        + 4;
    for (const auto& rc : receiver_chains_) {
        needed += ENVELOPE_DH_KEY_SIZE + ENVELOPE_KEY_SIZE + sizeof(uint32_t) + sizeof(uint32_t);
        needed += rc.message_keys.size() * (MESSAGE_KEY_SIZE + sizeof(uint32_t));
    }
    needed += ENVELOPE_KEY_SIZE * 2
        + sizeof(uint8_t) * 2
        + ENVELOPE_KEY_SIZE
        + sizeof(uint8_t)
        + 4;
    for (size_t i = 0; i < consumed_keys_.size(); i++) {
        (void)i;
        needed += ENVELOPE_DH_KEY_SIZE + sizeof(uint32_t);
    }
    return needed;
}

int EnvelopeState::serialize(uint8_t* output, size_t* output_len) const {
    if (!output || !output_len) return ENCHANT_ERROR_NULL_POINTER;

    size_t needed = serialized_size();

    if (*output_len < needed) {
        *output_len = needed;
        return ENCHANT_ERROR_BUFFER_TOO_SMALL;
    }

    size_t off = 0;

    uint32_t version = ENVELOPE_CURRENT_VERSION;
    write_u32_be(output + off, version); off += 4;

    memcpy(output + off, root_key_.key(), ENVELOPE_KEY_SIZE); off += ENVELOPE_KEY_SIZE;

    memcpy(output + off, sender_chain_.x25519_public.data(), ENVELOPE_DH_KEY_SIZE);
    off += ENVELOPE_DH_KEY_SIZE;
    memcpy(output + off, sender_chain_.x25519_private.data(), ENVELOPE_DH_KEY_SIZE);
    off += ENVELOPE_DH_KEY_SIZE;

    memcpy(output + off, sender_chain_.chain_key.key(), ENVELOPE_KEY_SIZE); off += ENVELOPE_KEY_SIZE;
    write_u32_be(output + off, sender_chain_.chain_key.index()); off += 4;
    write_u32_be(output + off, sending_message_number_); off += 4;
    write_u32_be(output + off, previous_counter_); off += 4;

    uint32_t rc_count = static_cast<uint32_t>(receiver_chains_.size());
    write_u32_be(output + off, rc_count); off += 4;

    for (const auto& rc : receiver_chains_) {
        memcpy(output + off, rc.sender_x25519_key.data(), ENVELOPE_DH_KEY_SIZE);
        off += ENVELOPE_DH_KEY_SIZE;
        memcpy(output + off, rc.chain_key.data(), ENVELOPE_KEY_SIZE); off += ENVELOPE_KEY_SIZE;
        write_u32_be(output + off, rc.chain_index); off += 4;

        uint32_t mk_count = static_cast<uint32_t>(rc.message_keys.size());
        write_u32_be(output + off, mk_count); off += 4;
        for (const auto& mk : rc.message_keys) {
            memcpy(output + off, mk.seed.data(), MESSAGE_KEY_SIZE); off += MESSAGE_KEY_SIZE;
            write_u32_be(output + off, mk.counter); off += 4;
        }
    }

    memcpy(output + off, our_identity_.data(), ENVELOPE_KEY_SIZE); off += ENVELOPE_KEY_SIZE;
    memcpy(output + off, their_identity_.data(), ENVELOPE_KEY_SIZE); off += ENVELOPE_KEY_SIZE;
    output[off++] = has_our_identity_ ? 1 : 0;
    output[off++] = has_their_identity_ ? 1 : 0;

    memcpy(output + off, pqr_key_.data(), ENVELOPE_KEY_SIZE); off += ENVELOPE_KEY_SIZE;
    output[off++] = has_pqr_key_ ? 1 : 0;

    uint32_t ck_count = static_cast<uint32_t>(consumed_keys_.size());
    write_u32_be(output + off, ck_count); off += 4;
    for (const auto& pair : consumed_keys_) {
        memcpy(output + off, pair.second.dh_public.data(), ENVELOPE_DH_KEY_SIZE);
        off += ENVELOPE_DH_KEY_SIZE;
        write_u32_be(output + off, pair.second.message_number); off += 4;
    }

    *output_len = off;
    return ENCHANT_SUCCESS;
}

int EnvelopeState::deserialize(const uint8_t* data, size_t data_len) {
    if (!data) return ENCHANT_ERROR_NULL_POINTER;
    if (data_len < 4) return ENCHANT_ERROR_INVALID_FORMAT;

    constexpr size_t MIN_SERIALIZED_SIZE = 4 + ENVELOPE_KEY_SIZE * 5 + ENVELOPE_DH_KEY_SIZE * 2
        + sizeof(uint32_t) * 5 + sizeof(uint8_t) * 3;
    if (data_len < MIN_SERIALIZED_SIZE) return ENCHANT_ERROR_INVALID_FORMAT;

    zero();

    size_t off = 0;
    uint32_t version = read_u32_be(data + off); off += 4;
    if (version > ENVELOPE_CURRENT_VERSION) {
        return ENCHANT_ERROR_INVALID_FORMAT;
    }

    uint8_t root_buf[ENVELOPE_KEY_SIZE];
    memcpy(root_buf, data + off, ENVELOPE_KEY_SIZE); off += ENVELOPE_KEY_SIZE;
    root_key_ = RootKey(root_buf);
    sodium_memzero(root_buf, ENVELOPE_KEY_SIZE);

    memcpy(sender_chain_.x25519_public.data(), data + off, ENVELOPE_DH_KEY_SIZE);
    off += ENVELOPE_DH_KEY_SIZE;
    memcpy(sender_chain_.x25519_private.data(), data + off, ENVELOPE_DH_KEY_SIZE);
    off += ENVELOPE_DH_KEY_SIZE;

    uint8_t ck_buf[ENVELOPE_KEY_SIZE];
    memcpy(ck_buf, data + off, ENVELOPE_KEY_SIZE); off += ENVELOPE_KEY_SIZE;
    uint32_t ck_index = read_u32_be(data + off); off += 4;
    sender_chain_.chain_key = ChainKey(ck_buf, ck_index);
    sodium_memzero(ck_buf, ENVELOPE_KEY_SIZE);

    sending_message_number_ = read_u32_be(data + off); off += 4;
    previous_counter_ = read_u32_be(data + off); off += 4;

    uint32_t rc_count = read_u32_be(data + off); off += 4;
    receiver_chains_.reserve(rc_count);
    for (uint32_t i = 0; i < rc_count; i++) {
        ReceiverChain rc;
        memcpy(rc.sender_x25519_key.data(), data + off, ENVELOPE_DH_KEY_SIZE);
        off += ENVELOPE_DH_KEY_SIZE;
        memcpy(rc.chain_key.data(), data + off, ENVELOPE_KEY_SIZE); off += ENVELOPE_KEY_SIZE;
        rc.chain_index = read_u32_be(data + off); off += 4;

        uint32_t mk_count = read_u32_be(data + off); off += 4;
        for (uint32_t j = 0; j < mk_count; j++) {
            uint8_t sd[MESSAGE_KEY_SIZE];
            memcpy(sd, data + off, MESSAGE_KEY_SIZE); off += MESSAGE_KEY_SIZE;
            uint32_t ctr = read_u32_be(data + off); off += 4;
            rc.message_keys.emplace_back(sd, ctr);
        }
        receiver_chains_.push_back(std::move(rc));
    }

    memcpy(our_identity_.data(), data + off, ENVELOPE_KEY_SIZE); off += ENVELOPE_KEY_SIZE;
    memcpy(their_identity_.data(), data + off, ENVELOPE_KEY_SIZE); off += ENVELOPE_KEY_SIZE;
    has_our_identity_ = (data[off++] != 0);
    has_their_identity_ = (data[off++] != 0);

    memcpy(pqr_key_.data(), data + off, ENVELOPE_KEY_SIZE); off += ENVELOPE_KEY_SIZE;
    has_pqr_key_ = (data[off++] != 0);

    uint32_t ck_count = read_u32_be(data + off); off += 4;
    for (uint32_t i = 0; i < ck_count; i++) {
        secure::SecureBuffer dh(ENVELOPE_DH_KEY_SIZE);
        memcpy(dh.data(), data + off, ENVELOPE_DH_KEY_SIZE); off += ENVELOPE_DH_KEY_SIZE;
        uint32_t mn = read_u32_be(data + off); off += 4;
        std::string key_id = make_key_id(dh.data(), mn);
        consumed_keys_.emplace(std::move(key_id), ConsumedEntry(std::move(dh), mn));
    }

    is_initialized_ = true;
    return ENCHANT_SUCCESS;
}

int EnvelopeState::delete_skipped_key(const uint8_t* their_ephemeral, uint32_t counter) {
    if (!their_ephemeral) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    int receiver_idx = find_receiver_chain_index(their_ephemeral);
    if (receiver_idx < 0) {
        return ENCHANT_ERROR_INVALID_FORMAT;
    }

    auto& chain = receiver_chains_[receiver_idx];
    for (auto it = chain.message_keys.begin(); it != chain.message_keys.end(); ++it) {
        if (it->counter == counter) {
            it->seed.zero();
            chain.message_keys.erase(it);
            return ENCHANT_SUCCESS;
        }
    }
    return ENCHANT_ERROR_INVALID_KEY_ID;
}

int EnvelopeState::clear_all_skipped_keys() {
    for (auto& chain : receiver_chains_) {
        for (auto& key : chain.message_keys) {
            key.seed.zero();
        }
        chain.message_keys.clear();
    }
    return ENCHANT_SUCCESS;
}

size_t EnvelopeState::skipped_key_count() const {
    size_t total = 0;
    for (const auto& chain : receiver_chains_) {
        total += chain.message_keys.size();
    }
    return total;
}

int EnvelopeState::evict_oldest_skipped_keys(size_t keep_count) {
    if (skipped_key_count() <= keep_count) {
        return ENCHANT_SUCCESS;
    }

    size_t to_remove = skipped_key_count() - keep_count;
    size_t removed = 0;

    for (auto& chain : receiver_chains_) {
        while (!chain.message_keys.empty() && removed < to_remove) {
            chain.message_keys.front().seed.zero();
            chain.message_keys.erase(chain.message_keys.begin());
            removed++;
        }
        if (removed >= to_remove) break;
    }

    return ENCHANT_SUCCESS;
}

} // namespace veil
} // namespace enchant

#ifndef ENCHANT_VEIL_SESSION_HPP
#define ENCHANT_VEIL_SESSION_HPP

#include "envelope_state.hpp"
#include "veil_types.hpp"
#include "secure/buffer.hpp"
#include <cstdint>
#include <string>
#include <vector>

namespace enchant {
namespace veil {

class IVeilStore {
public:
    virtual ~IVeilStore() = default;
    virtual int store(const std::string& key, const uint8_t* data, size_t len) = 0;
    virtual int load(const std::string& key, uint8_t* data, size_t* len) = 0;
    virtual int remove(const std::string& key) = 0;
    virtual int exists(const std::string& key) = 0;
};

class VeilSession {
public:
    VeilSession(IVeilStore& store);
    ~VeilSession();

    VeilSession(const VeilSession&) = delete;
    VeilSession& operator=(const VeilSession&) = delete;

    int init_alice(const uint8_t* root_key, const uint8_t* chain_key,
                   const uint8_t* our_dh_public, const uint8_t* our_dh_private,
                   const uint8_t* their_x25519_public,
                   const uint8_t* our_identity, const uint8_t* their_identity,
                   const uint8_t* pqr_key);

    int init_bob(const uint8_t* root_key, const uint8_t* chain_key,
                 const uint8_t* our_dh_public, const uint8_t* our_dh_private,
                 const uint8_t* their_x25519_public,
                 const uint8_t* our_identity, const uint8_t* their_identity,
                 const uint8_t* pqr_key);

    int encrypt(const uint8_t* plaintext, size_t plaintext_len,
                uint8_t* output, size_t* output_len);

    int decrypt(const uint8_t* input, size_t input_len,
                uint8_t* plaintext, size_t* plaintext_len);

    int seal_and_encrypt(const uint8_t* sender_identity_private,
                         const uint8_t* sender_identity_public,
                         const std::vector<std::pair<uint32_t, secure::SecureBuffer>>& recipients,
                         const SenderCertificate& sender_cert,
                         const uint8_t* plaintext, size_t plaintext_len,
                         uint8_t* output, size_t* output_len);

    int unseal_and_decrypt(const uint8_t* recipient_private_key,
                           const uint8_t* recipient_public,
                           const uint8_t* data, size_t data_len,
                           uint8_t* plaintext, size_t* plaintext_len);

    int save(const std::string& session_id);
    int load(const std::string& session_id);

    void set_our_identity(const uint8_t* identity);
    void set_their_identity(const uint8_t* identity);

    int get_their_identity(uint8_t* identity) const;
    int get_our_identity(uint8_t* identity) const;

private:
    EnvelopeState envelope_;
    IVeilStore& store_;
    secure::SecureBuffer our_identity_;
    secure::SecureBuffer their_identity_;
    bool has_our_identity_;
    bool has_their_identity_;

    std::string make_store_key(const std::string& session_id) const;
};

} // namespace veil
} // namespace enchant

#endif

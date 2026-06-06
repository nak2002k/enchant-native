#ifndef ENCHANT_VEIL_V2_HPP
#define ENCHANT_VEIL_V2_HPP

#include "veil_types.hpp"
#include "veil_error.hpp"
#include <vector>
#include <utility>

namespace enchant {
namespace veil {

static constexpr uint8_t LABEL_R[] = "Sealed Sender v2: r (2023-08)";
static constexpr uint8_t LABEL_K[] = "Sealed Sender v2: K";
static constexpr uint8_t LABEL_DH[] = "Sealed Sender v2: DH";
static constexpr uint8_t LABEL_DH_S[] = "Sealed Sender v2: DH-sender";

class DerivedKeys {
public:
    DerivedKeys(const uint8_t* m, size_t m_len);

    int derive_e(uint8_t* private_key, uint8_t* public_key);
    int derive_k(uint8_t* k);

private:
    uint8_t m_[32];
};

int apply_agreement_xor(const uint8_t* our_private, const uint8_t* our_public,
                        const uint8_t* their_public,
                        Direction direction,
                        const uint8_t* input,
                        uint8_t* output);

int compute_authentication_tag(const uint8_t* our_private,
                                const uint8_t* our_public,
                                const uint8_t* their_identity_public,
                                Direction direction,
                                const uint8_t* ephemeral_public,
                                const uint8_t* encrypted_message_key,
                                uint8_t* tag);

int sealed_sender_v2_encrypt(const uint8_t* sender_identity_private,
                              const uint8_t* sender_identity_public,
                              const std::vector<std::pair<uint32_t, secure::SecureBuffer>>& recipients,
                              const UnidentifiedSenderMessageContent& usmc,
                              uint8_t* output, size_t* output_len);

int sealed_sender_v2_decrypt_to_usmc(const uint8_t* recipient_private_key,
                                      const uint8_t* recipient_public,
                                      const uint8_t* data, size_t data_len,
                                      UnidentifiedSenderMessageContent& out_usmc);

}
}

#endif

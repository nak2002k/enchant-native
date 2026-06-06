#ifndef ENCHANT_VEIL_V1_HPP
#define ENCHANT_VEIL_V1_HPP

#include "veil_types.hpp"
#include "veil_error.hpp"

namespace enchant {
namespace veil {

class SealedSenderV1 {
public:
    struct EphemeralKeys {
        uint8_t chain_key[32];
        uint8_t cipher_key[32];
        uint8_t mac_key[32];

        static EphemeralKeys calculate(const uint8_t* our_private,
                                       const uint8_t* our_public,
                                       const uint8_t* their_public,
                                       Direction direction);
    };

    struct StaticKeys {
        uint8_t cipher_key[32];
        uint8_t mac_key[32];

        static StaticKeys calculate(const uint8_t* our_private,
                                    const uint8_t* their_public,
                                    const uint8_t* chain_key,
                                    const uint8_t* ciphertext, size_t ciphertext_len);
    };

    static int encrypt_message(const uint8_t* plaintext, size_t plaintext_len,
                               const uint8_t* cipher_key, const uint8_t* mac_key,
                               uint8_t* output, size_t* output_len);

    static int decrypt_message(const uint8_t* ciphertext, size_t ciphertext_len,
                               const uint8_t* cipher_key, const uint8_t* mac_key,
                               uint8_t* plaintext, size_t* plaintext_len);
};

int sealed_sender_encrypt_from_usmc(const uint8_t* our_identity_private,
                                     const uint8_t* our_identity_public,
                                     const uint8_t* their_identity_public,
                                     const UnidentifiedSenderMessageContent& usmc,
                                     uint8_t* output, size_t* output_len);

int sealed_sender_decrypt_to_usmc(const uint8_t* our_identity_private,
                                   const uint8_t* our_identity_public,
                                   const uint8_t* ephemeral_public,
                                   const uint8_t* encrypted_static, size_t encrypted_static_len,
                                   const uint8_t* encrypted_message, size_t encrypted_message_len,
                                   UnidentifiedSenderMessageContent& out_usmc);

}
}

#endif

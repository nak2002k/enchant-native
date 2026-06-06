#ifndef ENCHANT_VEIL_ASYNC_HPP
#define ENCHANT_VEIL_ASYNC_HPP

#include "veil_types.hpp"
#include "veil_error.hpp"
#include <future>
#include <vector>

namespace enchant {
namespace veil {

class VeilAsync {
public:
    static std::future<VeilDecryptionResult> decrypt_async(
        const secure::SecureBuffer& private_key,
        const secure::SecureBuffer& public_key,
        const UnidentifiedSenderMessage& message);

    static std::future<VeilEncryptionResult> encrypt_async(
        const secure::SecureBuffer& identity_private,
        const secure::SecureBuffer& identity_public,
        const std::vector<RecipientInfo>& recipients,
        const SenderCertificate& sender_cert,
        const uint8_t* plaintext, size_t plaintext_len);

    static std::future<SealedSenderDecryptionResult> decrypt_message_async(
        const secure::SecureBuffer& private_key,
        const secure::SecureBuffer& public_key,
        const uint8_t* ciphertext, size_t ciphertext_len);
};

}
}

#endif

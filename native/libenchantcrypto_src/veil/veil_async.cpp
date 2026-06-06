#include "veil_async.hpp"
#include "veil_v1.hpp"
#include "veil_v2.hpp"
#include "primitives/x25519.hpp"
#include <memory>
#include <thread>
#include <future>
#include <cstring>

namespace enchant {
namespace veil {

static void secure_buffer_clear(secure::SecureBuffer& buf) {
    if (!buf.empty()) {
        buf.zero();
    }
}

std::future<VeilDecryptionResult> VeilAsync::decrypt_async(
    const secure::SecureBuffer& private_key,
    const secure::SecureBuffer& public_key,
    const UnidentifiedSenderMessage& message) {
    auto priv = private_key.clone();
    auto pub = public_key.clone();

    bool is_v1 = message.is_v1();
    bool is_v2 = message.is_v2();

    secure::SecureBuffer eph;
    secure::SecureBuffer enc_static;
    secure::SecureBuffer enc_msg_key;
    secure::SecureBuffer auth_tag;
    secure::SecureBuffer enc_msg;
    secure::SecureBuffer raw_v2_data;

    if (is_v1) {
        eph = message.ephemeral_public().clone();
        enc_static = message.encrypted_static().clone();
        enc_msg = message.encrypted_message().clone();
    } else if (is_v2) {
        raw_v2_data = message.raw_data().clone();
        eph = message.ephemeral_public().clone();
        enc_msg_key = message.encrypted_message_key().clone();
        auth_tag = message.authentication_tag().clone();
        enc_msg = message.encrypted_message().clone();
    }

    return std::async(std::launch::async,
        [priv = std::move(priv), pub = std::move(pub),
         is_v1, is_v2,
         eph = std::move(eph), enc_static = std::move(enc_static),
         enc_msg_key = std::move(enc_msg_key), auth_tag = std::move(auth_tag),
         enc_msg = std::move(enc_msg), raw_v2_data = std::move(raw_v2_data)]() {
        VeilDecryptionResult result;
        result.error_code = 0;

        if (is_v1) {
            UnidentifiedSenderMessageContent usmc;
            int rc = sealed_sender_decrypt_to_usmc(
                priv.data(), pub.data(),
                eph.data(),
                enc_static.data(), enc_static.size(),
                enc_msg.data(), enc_msg.size(),
                usmc
            );

            if (rc != ENCHANT_VEIL_SUCCESS) {
                secure_buffer_clear(result.plaintext);
                result.error_code = rc;
                return result;
            }

            result.plaintext = secure::SecureBuffer(usmc.contents().data(), usmc.contents().size());
            result.sender_cert = usmc.sender().clone();
            result.device_id = usmc.sender().sender_device_id();
            result.content_hint = usmc.content_hint();

        } else if (is_v2) {
            UnidentifiedSenderMessageContent usmc;
            int rc = sealed_sender_v2_decrypt_to_usmc(
                priv.data(), pub.data(),
                raw_v2_data.data(), raw_v2_data.size(),
                usmc
            );

            if (rc != ENCHANT_VEIL_SUCCESS) {
                secure_buffer_clear(result.plaintext);
                result.error_code = rc;
                return result;
            }
            result.plaintext = secure::SecureBuffer(usmc.contents().data(), usmc.contents().size());
            result.sender_cert = usmc.sender().clone();
            result.device_id = usmc.sender().sender_device_id();
            result.content_hint = usmc.content_hint();
            result.message_version = SEALED_SENDER_V2_SERVICE_ID_FULL_VERSION;
        } else {
            result.error_code = ENCHANT_VEIL_ERROR_INVALID_FORMAT;
        }

        return result;
    });
}

std::future<VeilEncryptionResult> VeilAsync::encrypt_async(
    const secure::SecureBuffer& identity_private,
    const secure::SecureBuffer& identity_public,
    const std::vector<RecipientInfo>& recipients,
    const SenderCertificate& sender_cert,
    const uint8_t* plaintext, size_t plaintext_len) {
    auto v2_recipients = std::make_shared<std::vector<std::pair<uint32_t, secure::SecureBuffer>>>();
    for (const auto& r : recipients) {
        v2_recipients->emplace_back(r.recipient_device_id, r.recipient_public_key.clone());
    }

    auto priv = identity_private.clone();
    auto pub = identity_public.clone();
    auto pt = std::vector<uint8_t>(plaintext, plaintext + plaintext_len);
    auto cert = sender_cert.clone();

    return std::async(std::launch::async,
        [v2_recipients = std::move(v2_recipients),
         priv = std::move(priv), pub = std::move(pub),
         pt = std::move(pt), cert = std::move(cert)]() {
        VeilEncryptionResult result;
        result.success = false;
        result.error_code = ENCHANT_VEIL_ERROR_PREKEY_NOT_FOUND;

        if (v2_recipients->empty())
            return result;

        UnidentifiedSenderMessageContent usmc = UnidentifiedSenderMessageContent::new_message(
            CiphertextMessageType::MESSAGE,
            cert,
            pt.data(), pt.size(),
            ContentHint::RESENDABLE,
            nullptr, 0);

        size_t needed = 8192;
        secure::SecureBuffer output(needed);
        size_t output_len = needed;

        int rc = sealed_sender_v2_encrypt(
            priv.data(), pub.data(),
            *v2_recipients,
            usmc,
            output.data(), &output_len
        );

        if (rc == ENCHANT_VEIL_ERROR_BUFFER_TOO_SMALL) {
            output = secure::SecureBuffer(output_len);
            rc = sealed_sender_v2_encrypt(
                priv.data(), pub.data(),
                *v2_recipients,
                usmc,
                output.data(), &output_len
            );
        }

        if (rc == ENCHANT_VEIL_SUCCESS) {
            result.ciphertext = secure::SecureBuffer(output_len);
            std::memcpy(result.ciphertext.data(), output.data(), output_len);
            result.success = true;
            result.error_code = ENCHANT_VEIL_SUCCESS;
        } else {
            secure_buffer_clear(result.ciphertext);
            result.error_code = rc;
        }

        return result;
    });
}

std::future<SealedSenderDecryptionResult> VeilAsync::decrypt_message_async(
    const secure::SecureBuffer& private_key,
    const secure::SecureBuffer& public_key,
    const uint8_t* ciphertext, size_t ciphertext_len) {
    auto priv = private_key.clone();
    auto pub = public_key.clone();
    auto ct = std::vector<uint8_t>(ciphertext, ciphertext + ciphertext_len);

    return std::async(std::launch::async,
        [priv = std::move(priv), pub = std::move(pub), ct = std::move(ct)]() {
        SealedSenderDecryptionResult result;
        result.error_code = ENCHANT_VEIL_SUCCESS;

        UnidentifiedSenderMessageContent usmc;
        int rc = sealed_sender_v2_decrypt_to_usmc(
            priv.data(), pub.data(),
            ct.data(), ct.size(),
            usmc
        );

        if (rc == ENCHANT_VEIL_SUCCESS) {
            result.sender_uuid = usmc.sender().sender_uuid();
            result.sender_e164 = usmc.sender().sender_e164();
            result.device_id = usmc.sender().sender_device_id();
            result.message = usmc.contents();
        } else {
            result.error_code = rc;
        }

        return result;
    });
}

}
}

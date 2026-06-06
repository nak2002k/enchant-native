#include "zk/zkgroup/client_zk_receipt.hpp"
#include "primitives/hkdf.hpp"
#include "primitives/xchacha20.hpp"
#include "primitives/random.hpp"
#include "primitives/hash.hpp"
#include <sodium.h>
#include <cstring>

namespace enchant {
namespace zk {
namespace zkgroup {

constexpr const char* RECEIPT_ID_LABEL = "enchant_ZKGroup_ReceiptId_20240101";
constexpr const char* RECEIPT_STORAGE_LABEL = "enchant_ZKGroup_ReceiptStorage_20240101";

ClientZkReceiptOperations::ClientZkReceiptOperations()
    : initialized_(false) {}

int ClientZkReceiptOperations::initialize(
    const ServerPublicParams& server_params,
    const GroupSecretParams& group_params) {
    server_params_ = server_params;
    group_params_ = group_params;
    initialized_ = true;
    return ENCHANT_SUCCESS;
}

RistrettoPoint ClientZkReceiptOperations::derive_receipt_id_point(
    const std::array<uint8_t, CLIENT_ZK_RECEIPT_ID_SIZE>& receipt_id) const {
    ShoHmacSha256 sho(reinterpret_cast<const uint8_t*>(RECEIPT_ID_LABEL),
                      strlen(RECEIPT_ID_LABEL));
    sho.absorb_and_ratchet(receipt_id.data(), CLIENT_ZK_RECEIPT_ID_SIZE);
    return sho_get_point(sho).value();
}

int ClientZkReceiptOperations::issue_receipt_credential(
    const std::array<uint8_t, CLIENT_ZK_RECEIPT_ID_SIZE>& receipt_id,
    uint64_t receipt_level,
    uint64_t receipt_expiration,
    const uint8_t* randomness, size_t randomness_len,
    ReceiptCredential& credential_out) {
    if (!initialized_) return ENCHANT_ERROR_INTERNAL;
    if (!randomness || randomness_len < 32) return ENCHANT_ERROR_NULL_POINTER;

    auto receipt_point = derive_receipt_id_point(receipt_id);

    ReceiptCredentialIssuer issuer;
    auto response = issuer.issue(
        server_params_, receipt_point, receipt_level, receipt_expiration, randomness);

    credential_out.credential = response.proof.credential;
    credential_out.receipt_level = response.receipt_level;
    credential_out.receipt_expiration = response.receipt_expiration;

    return ENCHANT_SUCCESS;
}

int ClientZkReceiptOperations::present_receipt_credential(
    const ReceiptCredential& credential,
    const std::array<uint8_t, CLIENT_ZK_RECEIPT_ID_SIZE>& receipt_id,
    const uint8_t* randomness, size_t randomness_len,
    ReceiptCredentialPresentation& presentation_out) {
    if (!initialized_) return ENCHANT_ERROR_INTERNAL;
    if (!randomness || randomness_len < 32) return ENCHANT_ERROR_NULL_POINTER;

    auto receipt_point = derive_receipt_id_point(receipt_id);

    ReceiptCredentialPresenter presenter;
    presentation_out = presenter.present(
        server_params_, credential, receipt_point,
        group_params_.get_uid_enc_key_pair(), randomness);

    return presentation_out.proof.enchant_zkp_proof.empty() ? ENCHANT_ERROR_INTERNAL : ENCHANT_SUCCESS;
}

int ClientZkReceiptOperations::verify_receipt_credential(
    const ReceiptCredentialPresentation& presentation,
    const std::array<uint8_t, CLIENT_ZK_RECEIPT_ID_SIZE>& receipt_id,
    bool& valid_out) {
    if (!initialized_) return ENCHANT_ERROR_INTERNAL;

    auto receipt_point = derive_receipt_id_point(receipt_id);

    auto uid_enc = group_params_.get_uid_enc_key_pair();
    auto ct = encrypt_attribute(uid_enc, receipt_point, RistrettoPoint());

    ReceiptCredentialVerifier verifier;
    auto result = verifier.verify(
        server_params_, presentation, ct.E_A1, ct.E_A2, uid_enc);

    valid_out = (result == ZkGroupError::Ok);
    return ENCHANT_SUCCESS;
}

int ClientZkReceiptOperations::encrypt_receipt_for_storage(
    const uint8_t* receipt_data, size_t receipt_data_len,
    const std::array<uint8_t, 32>& encryption_key,
    EncryptedReceipt& encrypted_out) {
    if (!receipt_data && receipt_data_len > 0) return ENCHANT_ERROR_NULL_POINTER;

    uint8_t enc_key[32];
    uint8_t salt[32] = {0};
    int rc = primitives::hkdf_derive(
        encryption_key.data(), 32, salt, 32,
        reinterpret_cast<const uint8_t*>(RECEIPT_STORAGE_LABEL),
        strlen(RECEIPT_STORAGE_LABEL), enc_key, 32);
    sodium_memzero(salt, 32);
    if (rc != ENCHANT_SUCCESS) return rc;

    uint8_t nonce[24];
    randombytes_buf(nonce, sizeof(nonce));

    encrypted_out.version = 1;
    encrypted_out.encrypted_data.resize(24 + receipt_data_len + 16);
    memcpy(encrypted_out.encrypted_data.data(), nonce, 24);

    rc = primitives::xchacha20_encrypt(
        receipt_data, receipt_data_len,
        enc_key, nonce,
        encrypted_out.encrypted_data.data() + 24,
        encrypted_out.encrypted_data.size() - 24);

    sodium_memzero(enc_key, 32);
    if (rc != ENCHANT_SUCCESS) {
        encrypted_out.encrypted_data.clear();
        return rc;
    }

    return ENCHANT_SUCCESS;
}

int ClientZkReceiptOperations::decrypt_receipt(
    const EncryptedReceipt& encrypted,
    const std::array<uint8_t, 32>& encryption_key,
    std::vector<uint8_t>& receipt_data_out) {
    if (encrypted.encrypted_data.size() < 24 + 16) return ENCHANT_ERROR_CIPHERTEXT_TOO_SHORT;

    uint8_t enc_key[32];
    uint8_t salt[32] = {0};
    int rc = primitives::hkdf_derive(
        encryption_key.data(), 32, salt, 32,
        reinterpret_cast<const uint8_t*>(RECEIPT_STORAGE_LABEL),
        strlen(RECEIPT_STORAGE_LABEL), enc_key, 32);
    sodium_memzero(salt, 32);
    if (rc != ENCHANT_SUCCESS) return rc;

    const uint8_t* nonce = encrypted.encrypted_data.data();
    const uint8_t* ciphertext = encrypted.encrypted_data.data() + 24;
    size_t ciphertext_len = encrypted.encrypted_data.size() - 24;

    receipt_data_out.resize(ciphertext_len - 16);

    rc = primitives::xchacha20_decrypt(
        ciphertext, ciphertext_len,
        enc_key, nonce,
        receipt_data_out.data(), receipt_data_out.size());

    sodium_memzero(enc_key, 32);
    if (rc != ENCHANT_SUCCESS) {
        receipt_data_out.clear();
        return rc;
    }

    return ENCHANT_SUCCESS;
}

} // namespace zkgroup
} // namespace zk
} // namespace enchant

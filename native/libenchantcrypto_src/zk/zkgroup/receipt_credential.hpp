#ifndef ENCHANT_ZK_ZKGROUP_RECEIPT_CREDENTIAL_HPP
#define ENCHANT_ZK_ZKGROUP_RECEIPT_CREDENTIAL_HPP

#include <cstdint>
#include <cstddef>
#include <vector>
#include <array>
#include <optional>
#include "enchant/error.h"
#include "zk/enchant_zkp/ristretto.hpp"
#include "zk/zkcredential/attributes.hpp"
#include "zk/zkcredential/credentials.hpp"
#include "zk/zkcredential/issuance.hpp"
#include "zk/zkcredential/presentation.hpp"
#include "server_params.hpp"
#include "errors.hpp"

namespace enchant {
namespace zk {
namespace zkgroup {

using enchant_zkp::RistrettoPoint;
using enchant_zkp::RistrettoScalar;

constexpr size_t RECEIPT_CREDENTIAL_RANDOMNESS_LEN = 32;
constexpr const char* RECEIPT_CREDENTIAL_LABEL = "enchant_receipt_credential";
constexpr size_t RECEIPT_CREDENTIAL_LABEL_LEN = 26;

struct ReceiptCredential {
    zkcredential::Credential credential;
    uint64_t receipt_level;
    uint64_t receipt_expiration;
};

struct ReceiptCredentialResponse {
    zkcredential::IssuanceProof proof;
    uint64_t receipt_level;
    uint64_t receipt_expiration;
};

struct ReceiptCredentialPresentation {
    zkcredential::PresentationProof proof;
    uint64_t receipt_level;
    uint64_t receipt_expiration;
};

class ReceiptCredentialIssuer {
public:
    ReceiptCredentialResponse issue(
        const ServerSecretParams& server_params,
        const RistrettoPoint& receipt_id_point,
        uint64_t receipt_level,
        uint64_t receipt_expiration,
        const uint8_t* randomness
    ) {
        if (!randomness) return {};

        const auto& key_pair = server_params.receipt_credential_key_pair;

        zkcredential::IssuanceProofBuilder builder(
            reinterpret_cast<const uint8_t*>(RECEIPT_CREDENTIAL_LABEL),
            RECEIPT_CREDENTIAL_LABEL_LEN);

        std::array<RistrettoPoint, 2> receipt_pts = {receipt_id_point, RistrettoPoint()};
        builder.add_attribute(receipt_pts);

        auto proof = builder.issue(key_pair, randomness);
        return {proof, receipt_level, receipt_expiration};
    }
};

class ReceiptCredentialPresenter {
public:
    ReceiptCredentialPresentation present(
        const ServerPublicParams& server_params,
        const ReceiptCredential& credential,
        const RistrettoPoint& receipt_id_point,
        const zkcredential::AttributeKeyPair& enc_key,
        const uint8_t* randomness
    ) {
        if (!randomness) return {};

        zkcredential::PresentationProofBuilder builder(
            reinterpret_cast<const uint8_t*>(RECEIPT_CREDENTIAL_LABEL),
            RECEIPT_CREDENTIAL_LABEL_LEN);

        std::array<RistrettoPoint, 2> pts = {receipt_id_point, RistrettoPoint()};
        builder.add_attribute(pts, enc_key.to_any_key_pair());

        auto proof = builder.present(
            server_params.receipt_credential_public_key,
            credential.credential, randomness);

        return {proof, credential.receipt_level, credential.receipt_expiration};
    }
};

class ReceiptCredentialVerifier {
public:
    ZkGroupError verify(
        const ServerSecretParams& server_params,
        const ReceiptCredentialPresentation& presentation,
        const RistrettoPoint& receipt_ct_1,
        const RistrettoPoint& receipt_ct_2,
        const zkcredential::AttributeKeyPair& enc_key
    ) {
        zkcredential::PresentationProofVerifier verifier(
            reinterpret_cast<const uint8_t*>(RECEIPT_CREDENTIAL_LABEL),
            RECEIPT_CREDENTIAL_LABEL_LEN);

        std::array<RistrettoPoint, 2> ct = {receipt_ct_1, receipt_ct_2};
        verifier.add_attribute(ct,
            zkcredential::PublicKeyOrId::from_public_key(enc_key.get_public_key()));

        bool ok = verifier.verify(
            server_params.receipt_credential_key_pair, presentation.proof);
        return ok ? ZkGroupError::Ok : ZkGroupError::VerificationFailure;
    }
};

} // namespace zkgroup
} // namespace zk
} // namespace enchant

#endif

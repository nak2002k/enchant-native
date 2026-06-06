#ifndef ENCHANT_ZK_ZKGROUP_EXPIRING_PROFILE_KEY_CREDENTIAL_HPP
#define ENCHANT_ZK_ZKGROUP_EXPIRING_PROFILE_KEY_CREDENTIAL_HPP

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

constexpr size_t EXPIRING_PK_CREDENTIAL_LABEL_LEN = 38;
constexpr const char* EXPIRING_PK_CREDENTIAL_LABEL = "enchant_expiring_profile_key_credential";

struct ExpiringProfileKeyCredential {
    zkcredential::Credential credential;
    uint64_t expiration_time;
};

struct ExpiringProfileKeyCredentialResponse {
    zkcredential::IssuanceProof proof;
    uint64_t expiration_time;
};

struct ExpiringProfileKeyCredentialPresentation {
    zkcredential::PresentationProof proof;
    uint64_t expiration_time;
};

class ExpiringProfileKeyCredentialIssuer {
public:
    ExpiringProfileKeyCredentialResponse issue(
        const ServerSecretParams& server_params,
        const RistrettoPoint& user_id_point,
        const RistrettoPoint& profile_key_point,
        uint64_t expiration_time,
        const uint8_t* randomness
    ) {
        if (!randomness) return {};

        const auto& key_pair = server_params.profile_key_credential_key_pair;

        zkcredential::IssuanceProofBuilder builder(
            reinterpret_cast<const uint8_t*>(EXPIRING_PK_CREDENTIAL_LABEL),
            EXPIRING_PK_CREDENTIAL_LABEL_LEN);

        std::array<RistrettoPoint, 2> uid_pts = {user_id_point, RistrettoPoint()};
        builder.add_attribute(uid_pts);

        std::array<RistrettoPoint, 2> pk_pts = {profile_key_point, RistrettoPoint()};
        builder.add_attribute(pk_pts);

        auto proof = builder.issue(key_pair, randomness);
        return {proof, expiration_time};
    }
};

class ExpiringProfileKeyCredentialPresenter {
public:
    ExpiringProfileKeyCredentialPresentation present(
        const ServerPublicParams& server_params,
        const ExpiringProfileKeyCredential& credential,
        const RistrettoPoint& user_id_point,
        const RistrettoPoint& profile_key_point,
        const zkcredential::AttributeKeyPair& uid_enc_key,
        const zkcredential::AttributeKeyPair& profile_key_enc_key,
        const uint8_t* randomness
    ) {
        if (!randomness) return {};

        zkcredential::PresentationProofBuilder builder(
            reinterpret_cast<const uint8_t*>(EXPIRING_PK_CREDENTIAL_LABEL),
            EXPIRING_PK_CREDENTIAL_LABEL_LEN);

        std::array<RistrettoPoint, 2> uid_pts = {user_id_point, RistrettoPoint()};
        builder.add_attribute(uid_pts, uid_enc_key.to_any_key_pair());

        std::array<RistrettoPoint, 2> pk_pts = {profile_key_point, RistrettoPoint()};
        builder.add_attribute(pk_pts, profile_key_enc_key.to_any_key_pair());

        auto proof = builder.present(
            server_params.profile_key_credential_public_key,
            credential.credential, randomness);

        return {proof, credential.expiration_time};
    }
};

class ExpiringProfileKeyCredentialVerifier {
public:
    static bool is_expired(uint64_t expiration_time) {
        auto now = std::chrono::duration_cast<std::chrono::seconds>(
            std::chrono::system_clock::now().time_since_epoch()).count();
        return static_cast<uint64_t>(now) > expiration_time;
    }

    ZkGroupError verify(
        const ServerSecretParams& server_params,
        const ExpiringProfileKeyCredentialPresentation& presentation,
        const RistrettoPoint& uid_ct_1,
        const RistrettoPoint& uid_ct_2,
        const RistrettoPoint& pk_ct_1,
        const RistrettoPoint& pk_ct_2,
        const zkcredential::AttributeKeyPair& uid_enc_key,
        const zkcredential::AttributeKeyPair& profile_key_enc_key
    ) {
        if (is_expired(presentation.expiration_time)) {
            return ZkGroupError::VerificationFailure;
        }

        zkcredential::PresentationProofVerifier verifier(
            reinterpret_cast<const uint8_t*>(EXPIRING_PK_CREDENTIAL_LABEL),
            EXPIRING_PK_CREDENTIAL_LABEL_LEN);

        std::array<RistrettoPoint, 2> uid_ct = {uid_ct_1, uid_ct_2};
        verifier.add_attribute(uid_ct,
            zkcredential::PublicKeyOrId::from_public_key(uid_enc_key.get_public_key()));

        std::array<RistrettoPoint, 2> pk_ct = {pk_ct_1, pk_ct_2};
        verifier.add_attribute(pk_ct,
            zkcredential::PublicKeyOrId::from_public_key(profile_key_enc_key.get_public_key()));

        bool ok = verifier.verify(
            server_params.profile_key_credential_key_pair, presentation.proof);
        return ok ? ZkGroupError::Ok : ZkGroupError::VerificationFailure;
    }
};

} // namespace zkgroup
} // namespace zk
} // namespace enchant

#endif

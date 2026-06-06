#ifndef ENCHANT_ZK_ZKGROUP_AUTH_CREDENTIAL_HPP
#define ENCHANT_ZK_ZKGROUP_AUTH_CREDENTIAL_HPP

#include <array>
#include <cstdint>
#include <cstring>
#include <vector>
#include "zk/enchant_zkp/ristretto.hpp"
#include "zk/enchant_zkp/sho_hmac_sha256.hpp"
#include "zk/enchant_zkp/sho_ext.hpp"
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
using enchant_zkp::ShoHmacSha256;
using enchant_zkp::sho_get_point;
using enchant_zkp::sho_get_scalar;

constexpr size_t AUTH_CREDENTIAL_RANDOMNESS_LEN = 32;

struct AuthCredential {
    zkcredential::Credential credential;
    uint32_t redemption_time;
};

struct AuthCredentialResponse {
    zkcredential::IssuanceProof proof;
    uint32_t redemption_time;
};

struct AuthCredentialPresentation {
    zkcredential::PresentationProof proof;
    uint32_t redemption_time;
};

class AuthCredentialIssuer {
public:
    AuthCredentialResponse issue(
        const ServerSecretParams& server_params,
        const RistrettoPoint& user_id_point,
        uint32_t redemption_time,
        const uint8_t* randomness
    ) {
        if (!randomness) return {};

        const auto& key_pair = server_params.auth_credential_key_pair;

        zkcredential::IssuanceProofBuilder builder(
            reinterpret_cast<const uint8_t*>("enchant_auth_credential"), 23);

        std::array<RistrettoPoint, 2> attr_pts = {user_id_point, RistrettoPoint()};
        builder.add_attribute(attr_pts);

        auto proof = builder.issue(key_pair, randomness);

        return {proof, redemption_time};
    }
};

class AuthCredentialPresenter {
public:
    AuthCredentialPresentation present(
        const ServerPublicParams& server_params,
        const AuthCredential& credential,
        const RistrettoPoint& user_id_point,
        const zkcredential::AttributeKeyPair& enc_key,
        const uint8_t* randomness
    ) {
        if (!randomness) return {};

        zkcredential::PresentationProofBuilder builder(
            reinterpret_cast<const uint8_t*>("enchant_auth_credential"), 23);

        std::array<RistrettoPoint, 2> pts = {user_id_point, RistrettoPoint()};
        builder.add_attribute(pts, enc_key.to_any_key_pair());

        auto proof = builder.present(
            server_params.auth_credential_public_key,
            credential.credential, randomness);

        return {proof, credential.redemption_time};
    }
};

class AuthCredentialVerifier {
public:
    ZkGroupError verify(
        const ServerSecretParams& server_params,
        const AuthCredentialPresentation& presentation,
        const RistrettoPoint& user_id_ciphertext_1,
        const RistrettoPoint& user_id_ciphertext_2,
        const zkcredential::AttributeKeyPair& enc_key
    ) {
        zkcredential::PresentationProofVerifier verifier(
            reinterpret_cast<const uint8_t*>("enchant_auth_credential"), 23);

        std::array<RistrettoPoint, 2> ct = {user_id_ciphertext_1, user_id_ciphertext_2};
        zkcredential::PublicKeyOrId pk_or_id = zkcredential::PublicKeyOrId::from_public_key(
            enc_key.get_public_key());
        verifier.add_attribute(ct, pk_or_id);

        bool ok = verifier.verify(server_params.auth_credential_key_pair, presentation.proof);
        return ok ? ZkGroupError::Ok : ZkGroupError::VerificationFailure;
    }
};

} // namespace zkgroup
} // namespace zk
} // namespace enchant

#endif

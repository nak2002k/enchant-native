#ifndef ENCHANT_ZK_ZKGROUP_PROFILE_KEY_CREDENTIAL_HPP
#define ENCHANT_ZK_ZKGROUP_PROFILE_KEY_CREDENTIAL_HPP

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

constexpr size_t PROFILE_KEY_CREDENTIAL_RANDOMNESS_LEN = 32;
constexpr const char* PROFILE_KEY_CREDENTIAL_DOMAIN_LABEL = "enchant_profile_key_credential";
constexpr size_t PROFILE_KEY_CREDENTIAL_DOMAIN_LABEL_LEN = 31;

struct ProfileKeyCredential {
    zkcredential::Credential credential;
    uint32_t redemption_time;
};

struct ProfileKeyCredentialResponse {
    zkcredential::IssuanceProof proof;
    uint32_t redemption_time;
};

struct ProfileKeyCredentialPresentation {
    zkcredential::PresentationProof proof;
    uint32_t redemption_time;
};

class ProfileKeyCredentialIssuer {
public:
    ProfileKeyCredentialResponse issue(
        const ServerSecretParams& server_params,
        const RistrettoPoint& user_id_point,
        const RistrettoPoint& profile_key_point,
        uint32_t redemption_time,
        const uint8_t* randomness
    ) {
        if (!randomness) return {};

        const auto& key_pair = server_params.profile_key_credential_key_pair;

        zkcredential::IssuanceProofBuilder builder(
            reinterpret_cast<const uint8_t*>(PROFILE_KEY_CREDENTIAL_DOMAIN_LABEL),
            PROFILE_KEY_CREDENTIAL_DOMAIN_LABEL_LEN);

        // Attribute 0: user_id
        std::array<RistrettoPoint, 2> uid_pts = {user_id_point, RistrettoPoint()};
        builder.add_attribute(uid_pts);

        // Attribute 1: profile_key
        std::array<RistrettoPoint, 2> pk_pts = {profile_key_point, RistrettoPoint()};
        builder.add_attribute(pk_pts);

        auto proof = builder.issue(key_pair, randomness);

        return {proof, redemption_time};
    }
};

class ProfileKeyCredentialPresenter {
public:
    ProfileKeyCredentialPresentation present(
        const ServerPublicParams& server_params,
        const ProfileKeyCredential& credential,
        const RistrettoPoint& user_id_point,
        const RistrettoPoint& profile_key_point,
        const zkcredential::AttributeKeyPair& uid_enc_key,
        const zkcredential::AttributeKeyPair& profile_key_enc_key,
        const uint8_t* randomness
    ) {
        if (!randomness) return {};

        zkcredential::PresentationProofBuilder builder(
            reinterpret_cast<const uint8_t*>(PROFILE_KEY_CREDENTIAL_DOMAIN_LABEL),
            PROFILE_KEY_CREDENTIAL_DOMAIN_LABEL_LEN);

        // Attribute 0: user_id encrypted under uid_enc_key
        std::array<RistrettoPoint, 2> uid_pts = {user_id_point, RistrettoPoint()};
        builder.add_attribute(uid_pts, uid_enc_key.to_any_key_pair());

        // Attribute 1: profile_key encrypted under profile_key_enc_key
        std::array<RistrettoPoint, 2> pk_pts = {profile_key_point, RistrettoPoint()};
        builder.add_attribute(pk_pts, profile_key_enc_key.to_any_key_pair());

        auto proof = builder.present(
            server_params.profile_key_credential_public_key,
            credential.credential, randomness);

        return {proof, credential.redemption_time};
    }
};

class ProfileKeyCredentialVerifier {
public:
    ZkGroupError verify(
        const ServerSecretParams& server_params,
        const ProfileKeyCredentialPresentation& presentation,
        const RistrettoPoint& user_id_ciphertext_1,
        const RistrettoPoint& user_id_ciphertext_2,
        const RistrettoPoint& profile_key_ciphertext_1,
        const RistrettoPoint& profile_key_ciphertext_2,
        const zkcredential::AttributeKeyPair& uid_enc_key,
        const zkcredential::AttributeKeyPair& profile_key_enc_key
    ) {
        zkcredential::PresentationProofVerifier verifier(
            reinterpret_cast<const uint8_t*>(PROFILE_KEY_CREDENTIAL_DOMAIN_LABEL),
            PROFILE_KEY_CREDENTIAL_DOMAIN_LABEL_LEN);

        // Attribute 0: user_id ciphertext
        std::array<RistrettoPoint, 2> uid_ct = {user_id_ciphertext_1, user_id_ciphertext_2};
        verifier.add_attribute(uid_ct,
            zkcredential::PublicKeyOrId::from_public_key(uid_enc_key.get_public_key()));

        // Attribute 1: profile_key ciphertext
        std::array<RistrettoPoint, 2> pk_ct = {profile_key_ciphertext_1, profile_key_ciphertext_2};
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

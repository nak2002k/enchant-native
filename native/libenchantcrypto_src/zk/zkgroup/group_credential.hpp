#ifndef ENCHANT_ZK_ZKGROUP_GROUP_CREDENTIAL_HPP
#define ENCHANT_ZK_ZKGROUP_GROUP_CREDENTIAL_HPP

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

constexpr size_t GROUP_CREDENTIAL_RANDOMNESS_LEN = 32;

struct GroupMembershipCredential {
    zkcredential::Credential credential;
    uint64_t group_id;
    uint32_t expiration;
};

struct GroupMembershipCredentialResponse {
    zkcredential::IssuanceProof proof;
    uint64_t group_id;
    uint32_t expiration;
};

struct GroupMembershipPresentation {
    zkcredential::PresentationProof proof;
    uint64_t group_id;
};

class GroupMembershipIssuer {
public:
    GroupMembershipCredentialResponse issue(
        const ServerSecretParams& server_params,
        const RistrettoPoint& member_id_point,
        const RistrettoPoint& group_id_point,
        uint32_t expiration,
        const uint8_t* randomness
    ) {
        if (!randomness) return {};

        const auto& key_pair = server_params.group_credential_key_pair;

        zkcredential::IssuanceProofBuilder builder(
            reinterpret_cast<const uint8_t*>("enchant_group_membership"), 24);

        std::array<RistrettoPoint, 2> member_pts = {member_id_point, RistrettoPoint()};
        builder.add_attribute(member_pts);

        std::array<RistrettoPoint, 2> group_pts = {group_id_point, RistrettoPoint()};
        builder.add_attribute(group_pts);

        auto proof = builder.issue(key_pair, randomness);

        uint64_t gid = 0;
        return {proof, gid, expiration};
    }
};

class GroupMembershipPresenter {
public:
    GroupMembershipPresentation present(
        const ServerPublicParams& server_params,
        const GroupMembershipCredential& credential,
        const RistrettoPoint& member_id_point,
        const RistrettoPoint& group_id_point,
        const zkcredential::AttributeKeyPair& member_enc_key,
        const zkcredential::AttributeKeyPair& group_enc_key,
        const uint8_t* randomness
    ) {
        if (!randomness) return {};

        zkcredential::PresentationProofBuilder builder(
            reinterpret_cast<const uint8_t*>("enchant_group_membership"), 24);

        std::array<RistrettoPoint, 2> member_pts = {member_id_point, RistrettoPoint()};
        builder.add_attribute(member_pts, member_enc_key.to_any_key_pair());

        std::array<RistrettoPoint, 2> group_pts = {group_id_point, RistrettoPoint()};
        builder.add_attribute(group_pts, group_enc_key.to_any_key_pair());

        auto proof = builder.present(
            server_params.group_credential_public_key,
            credential.credential, randomness);

        return {proof, credential.group_id};
    }
};

class GroupMembershipVerifier {
public:
    ZkGroupError verify(
        const ServerSecretParams& server_params,
        const GroupMembershipPresentation& presentation,
        const RistrettoPoint& member_ct_1,
        const RistrettoPoint& member_ct_2,
        const RistrettoPoint& group_ct_1,
        const RistrettoPoint& group_ct_2,
        const zkcredential::AttributeKeyPair& member_enc_key,
        const zkcredential::AttributeKeyPair& group_enc_key
    ) {
        zkcredential::PresentationProofVerifier verifier(
            reinterpret_cast<const uint8_t*>("enchant_group_membership"), 24);

        std::array<RistrettoPoint, 2> member_ct = {member_ct_1, member_ct_2};
        verifier.add_attribute(member_ct,
            zkcredential::PublicKeyOrId::from_public_key(member_enc_key.get_public_key()));

        std::array<RistrettoPoint, 2> group_ct = {group_ct_1, group_ct_2};
        verifier.add_attribute(group_ct,
            zkcredential::PublicKeyOrId::from_public_key(group_enc_key.get_public_key()));

        bool ok = verifier.verify(
            server_params.group_credential_key_pair, presentation.proof);
        return ok ? ZkGroupError::Ok : ZkGroupError::VerificationFailure;
    }
};

} // namespace zkgroup
} // namespace zk
} // namespace enchant

#endif

#ifndef ENCHANT_ZK_ZKGROUP_GROUP_SECRET_PARAMS_HPP
#define ENCHANT_ZK_ZKGROUP_GROUP_SECRET_PARAMS_HPP

#include <array>
#include <cstdint>
#include <cstring>
#include "zk/zkcredential/attributes.hpp"
#include "zk/zkcredential/credentials.hpp"
#include "zk/zkgroup/encryption_domains.hpp"
#include "zk/enchant_zkp/ristretto.hpp"
#include "zk/enchant_zkp/sho_hmac_sha256.hpp"
#include "zk/enchant_zkp/sho_ext.hpp"

namespace enchant {
namespace zk {
namespace zkgroup {

using enchant_zkp::ShoHmacSha256;

constexpr size_t GROUP_MASTER_KEY_SIZE = 32;

struct GroupSecretParams {
    zkcredential::AttributeKeyPair uid_enc_key_pair;
    zkcredential::AttributeKeyPair profile_key_enc_key_pair;
    zkcredential::CredentialKeyPair auth_credential_key_pair;
    zkcredential::CredentialKeyPair group_credential_key_pair;

    static GroupSecretParams derive_from(const uint8_t* master_key, size_t key_len) {
        GroupSecretParams params;
        params.uid_enc_key_pair = UidEncryptionDomain::derive_from(master_key, key_len);
        params.profile_key_enc_key_pair = ProfileKeyEncryptionDomain::derive_from(master_key, key_len);

        // Derive credential key pairs from the master key
        ShoHmacSha256 sho(reinterpret_cast<const uint8_t*>(
            "enchant_ZKGroup_GroupSecretParams_CredentialKeys_20240101"), 56);
        sho.absorb_and_ratchet(master_key, key_len);

        uint8_t auth_rand[32], group_rand[32];
        sho.squeeze_and_ratchet_into(auth_rand, 32);
        sho.squeeze_and_ratchet_into(group_rand, 32);

        params.auth_credential_key_pair = zkcredential::CredentialKeyPair::generate(auth_rand);
        params.group_credential_key_pair = zkcredential::CredentialKeyPair::generate(group_rand);

        sodium_memzero(auth_rand, sizeof(auth_rand));
        sodium_memzero(group_rand, sizeof(group_rand));

        return params;
    }

    zkcredential::AttributeKeyPair get_uid_enc_key_pair() const {
        return uid_enc_key_pair;
    }

    zkcredential::AttributeKeyPair get_profile_key_enc_key_pair() const {
        return profile_key_enc_key_pair;
    }

    const zkcredential::CredentialPublicKey& get_auth_credential_public_key() const {
        return auth_credential_key_pair.get_public_key();
    }

    const zkcredential::CredentialPublicKey& get_group_credential_public_key() const {
        return group_credential_key_pair.get_public_key();
    }
};

} // namespace zkgroup
} // namespace zk
} // namespace enchant

#endif

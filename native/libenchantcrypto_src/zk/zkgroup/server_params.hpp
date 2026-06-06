#ifndef ENCHANT_ZK_ZKGROUP_SERVER_PARAMS_HPP
#define ENCHANT_ZK_ZKGROUP_SERVER_PARAMS_HPP

#include <array>
#include <cstdint>
#include <cstring>
#include <sodium.h>
#include "zk/enchant_zkp/ristretto.hpp"
#include "zk/enchant_zkp/sho_hmac_sha256.hpp"
#include "zk/enchant_zkp/sho_ext.hpp"
#include "zk/zkcredential/credentials.hpp"
#include "errors.hpp"

namespace enchant {
namespace zk {
namespace zkgroup {

using enchant_zkp::RistrettoPoint;
using enchant_zkp::RistrettoScalar;
using enchant_zkp::ShoHmacSha256;
using enchant_zkp::sho_get_point;
using enchant_zkp::sho_get_scalar;

struct ServerSecretParams {
    zkcredential::CredentialKeyPair auth_credential_key_pair;
    zkcredential::CredentialKeyPair profile_key_credential_key_pair;
    zkcredential::CredentialKeyPair group_credential_key_pair;
    RistrettoScalar sig_private_key;
    RistrettoPoint sig_public_key;

    static ServerSecretParams generate(const uint8_t* randomness) {
        if (!randomness) return {};

        ShoHmacSha256 sho(reinterpret_cast<const uint8_t*>(
            "enchant_ZKGroup_ServerSecretParams_20240101"), 46);
        sho.absorb_and_ratchet(randomness, 32);

        ServerSecretParams params;
        uint8_t auth_rand[32], pk_rand[32], group_rand[32];
        sho.squeeze_and_ratchet_into(auth_rand, 32);
        sho.squeeze_and_ratchet_into(pk_rand, 32);
        sho.squeeze_and_ratchet_into(group_rand, 32);

        params.auth_credential_key_pair = zkcredential::CredentialKeyPair::generate(auth_rand);
        params.profile_key_credential_key_pair = zkcredential::CredentialKeyPair::generate(pk_rand);
        params.group_credential_key_pair = zkcredential::CredentialKeyPair::generate(group_rand);

        sodium_memzero(auth_rand, sizeof(auth_rand));
        sodium_memzero(pk_rand, sizeof(pk_rand));
        sodium_memzero(group_rand, sizeof(group_rand));

        params.sig_private_key = sho_get_scalar(sho);
        uint8_t one[32] = {0};
        one[0] = 1;
        RistrettoPoint G;
        if (crypto_scalarmult_ristretto255_base(G.mutable_data(), one) != 0) {
            return {};
        }
        params.sig_public_key = params.sig_private_key.scalar_mul_point(G).value();

        return params;
    }
};

struct ServerPublicParams {
    zkcredential::CredentialPublicKey auth_credential_public_key;
    zkcredential::CredentialPublicKey profile_key_credential_public_key;
    zkcredential::CredentialPublicKey group_credential_public_key;
    RistrettoPoint sig_public_key;

    static ServerPublicParams from_secret(const ServerSecretParams& secret) {
        ServerPublicParams pub;
        pub.auth_credential_public_key = secret.auth_credential_key_pair.get_public_key();
        pub.profile_key_credential_public_key = secret.profile_key_credential_key_pair.get_public_key();
        pub.group_credential_public_key = secret.group_credential_key_pair.get_public_key();
        pub.sig_public_key = secret.sig_public_key;
        return pub;
    }
};

} // namespace zkgroup
} // namespace zk
} // namespace enchant

#endif

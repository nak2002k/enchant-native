#ifndef ENCHANT_ZK_ATTEST_ATTEST_HPP
#define ENCHANT_ZK_ATTEST_ATTEST_HPP

#include <cstdint>
#include <cstring>
#include <vector>
#include <sodium.h>
#include "zk/enchant_zkp/ristretto.hpp"
#include "zk/enchant_zkp/sho_hmac_sha256.hpp"
#include "zk/enchant_zkp/sho_ext.hpp"
#include "zk/enchant_zkp/enchant_zkp.hpp"
#include "secure/buffer.hpp"

namespace enchant {
namespace attest {

using enchant::zk::enchant_zkp::RistrettoPoint;
using enchant::zk::enchant_zkp::RistrettoScalar;
using enchant::zk::enchant_zkp::ShoHmacSha256;
using enchant::zk::enchant_zkp::sho_get_point;
using enchant::zk::enchant_zkp::sho_get_scalar;
using enchant::zk::enchant_zkp::Statement;

constexpr size_t ATTEST_KEY_SIZE = 32;
constexpr size_t ATTEST_NONCE_SIZE = 32;
constexpr size_t ATTEST_CHALLENGE_SIZE = 32;

struct AttestKeyPair {
    secure::SecureBuffer private_key;
    RistrettoPoint public_key;

    static AttestKeyPair generate(const uint8_t* randomness) {
        AttestKeyPair kp;
        kp.private_key = secure::SecureBuffer(ATTEST_KEY_SIZE);

        ShoHmacSha256 sho(reinterpret_cast<const uint8_t*>(
            "enchant_AttestKeyPair_generate_20240101"), 45);
        sho.absorb_and_ratchet(randomness, ATTEST_KEY_SIZE);
        RistrettoScalar sk = sho_get_scalar(sho);
        std::memcpy(kp.private_key.data(), sk.data(), ATTEST_KEY_SIZE);

        kp.public_key = sk.scalar_mul_point(base_point()).value();
        return kp;
    }

    static AttestKeyPair from_private_key(const uint8_t* priv) {
        AttestKeyPair kp;
        kp.private_key = secure::SecureBuffer(priv, ATTEST_KEY_SIZE);
        RistrettoScalar sk = RistrettoScalar::from_bytes(priv);
        kp.public_key = sk.scalar_mul_point(base_point()).value();
        return kp;
    }

    void zero() {
        private_key.zero();
    }

private:
    static RistrettoPoint base_point() {
        RistrettoPoint G;
        uint8_t one[32] = {0};
        one[0] = 1;
        crypto_scalarmult_ristretto255_base(G.mutable_data(), one);
        return G;
    }
};

struct AttestProof {
    RistrettoPoint commitment;
    std::vector<uint8_t> enchant_zkp_proof;
};

struct AttestChallenge {
    uint8_t nonce[ATTEST_NONCE_SIZE];
    uint8_t challenge[ATTEST_CHALLENGE_SIZE];
};

class AttestProver {
public:
    AttestProver(const uint8_t* label, size_t label_len)
        : label_(label, label + label_len) {}

    AttestProof prove(
        const AttestKeyPair& key_pair,
        const AttestChallenge& challenge
    ) {
        RistrettoScalar sk = RistrettoScalar::from_bytes(key_pair.private_key.data());
        ShoHmacSha256 sho(label_.data(), label_.size());
        RistrettoScalar r = sho_get_scalar(sho);
        RistrettoPoint G;
        uint8_t one[32] = {0};
        one[0] = 1;
        crypto_scalarmult_ristretto255_base(G.mutable_data(), one);
        RistrettoPoint commitment = r.scalar_mul_point(G).value();

        Statement st;
        st.add("PK", {{"sk", "G"}});
        st.add("R", {{"r", "G"}});

        std::vector<std::pair<std::string, RistrettoScalar>> scalar_args;
        scalar_args.emplace_back("sk", sk);
        scalar_args.emplace_back("r", r);

        std::vector<std::pair<std::string, RistrettoPoint>> point_args;
        point_args.emplace_back("PK", key_pair.public_key);
        point_args.emplace_back("R", commitment);

        std::vector<uint8_t> proof;
        uint8_t rand[32];
        randombytes_buf(rand, 32);
        st.prove(scalar_args, point_args,
                 challenge.challenge, ATTEST_CHALLENGE_SIZE,
                 rand, proof);

        return {commitment, proof};
    }

private:
    std::vector<uint8_t> label_;
};

class AttestVerifier {
public:
    AttestVerifier(const uint8_t* label, size_t label_len)
        : label_(label, label + label_len) {}

    bool verify(
        const RistrettoPoint& public_key,
        const AttestChallenge& challenge,
        const AttestProof& proof
    ) {
        if (proof.enchant_zkp_proof.empty()) return false;

        Statement st;
        st.add("PK", {{"sk", "G"}});
        st.add("R", {{"r", "G"}});

        std::vector<std::pair<std::string, RistrettoPoint>> point_args;
        point_args.emplace_back("PK", public_key);
        point_args.emplace_back("R", proof.commitment);

        return enchant::zk::enchant_zkp::PokshoError::Ok == st.verify_proof(
            proof.enchant_zkp_proof, point_args,
            challenge.challenge, ATTEST_CHALLENGE_SIZE);
    }

    bool verify_against_trusted_keys(
        const std::vector<RistrettoPoint>& trusted_keys,
        const AttestChallenge& challenge,
        const AttestProof& proof
    ) {
        if (proof.enchant_zkp_proof.empty() || trusted_keys.empty()) return false;

        for (const auto& key : trusted_keys) {
            if (verify(key, challenge, proof)) {
                return true;
            }
        }
        return false;
    }

private:
    std::vector<uint8_t> label_;
};

inline AttestChallenge generate_challenge(const uint8_t* server_randomness) {
    AttestChallenge challenge;
    std::memcpy(challenge.nonce, server_randomness, ATTEST_NONCE_SIZE);

    ShoHmacSha256 sho(reinterpret_cast<const uint8_t*>(
        "enchant_AttestChallenge_20240101"), 35);
    sho.absorb_and_ratchet(server_randomness, ATTEST_NONCE_SIZE);
    auto challenge_bytes = sho.squeeze_and_ratchet(ATTEST_CHALLENGE_SIZE);
    std::memcpy(challenge.challenge, challenge_bytes.data(), ATTEST_CHALLENGE_SIZE);
    return challenge;
}

} // namespace attest
} // namespace enchant

#endif

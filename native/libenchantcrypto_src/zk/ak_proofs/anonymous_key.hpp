#ifndef ENCHANT_ZK_AK_PROOFS_ANONYMOUS_KEY_HPP
#define ENCHANT_ZK_AK_PROOFS_ANONYMOUS_KEY_HPP

#include <array>
#include <cstdint>
#include <cstring>
#include <vector>
#include <sodium.h>
#include "zk/enchant_zkp/enchant_zkp.hpp"
#include "zk/enchant_zkp/ristretto.hpp"
#include "zk/enchant_zkp/sho_hmac_sha256.hpp"
#include "zk/enchant_zkp/sho_ext.hpp"
#include "errors.hpp"

namespace enchant {
namespace zk {
namespace ak_proofs {

using enchant_zkp::RistrettoPoint;
using enchant_zkp::RistrettoScalar;
using enchant_zkp::ShoHmacSha256;
using enchant_zkp::sho_get_point;
using enchant_zkp::sho_get_scalar;
using enchant_zkp::Statement;

constexpr size_t ANONYMOUS_KEY_RANDOMNESS_LEN = 32;

struct AnonymousKeySignature {
    RistrettoPoint commitment;
    std::vector<uint8_t> enchant_zkp_proof;
};

class AnonymousKeyProver {
public:
    AnonymousKeyProver(const uint8_t* label, size_t label_len)
        : label_(label, label + label_len) {}

    void set_public_key(const RistrettoPoint& public_key) {
        public_key_ = public_key;
    }

    AnonymousKeySignature sign(
        const RistrettoScalar& private_key,
        const uint8_t* message, size_t message_len,
        const uint8_t* randomness
    ) {
        if (!randomness) return {};

        ShoHmacSha256 sho(label_.data(), label_.size());
        sho.absorb_and_ratchet(randomness, ANONYMOUS_KEY_RANDOMNESS_LEN);
        RistrettoScalar r = sho_get_scalar(sho);
        RistrettoPoint R = r.scalar_mul_point(base_point()).value();

        Statement st;
        st.add("R", {{"r", "G"}});
        st.add("PK", {{"sk", "G"}});

        std::vector<std::pair<std::string, RistrettoScalar>> scalar_args;
        scalar_args.emplace_back("r", r);
        scalar_args.emplace_back("sk", private_key);

        std::vector<std::pair<std::string, RistrettoPoint>> point_args;
        point_args.emplace_back("R", R);
        point_args.emplace_back("PK", public_key_);

        std::vector<uint8_t> proof;
        st.prove(scalar_args, point_args, message, message_len,
                 sho.squeeze_and_ratchet_32().data(), proof);

        return {R, proof};
    }

private:
    static RistrettoPoint base_point() {
        RistrettoPoint G;
        uint8_t one[32] = {0};
        one[0] = 1;
        crypto_scalarmult_ristretto255_base(G.mutable_data(), one);
        return G;
    }

    std::vector<uint8_t> label_;
    RistrettoPoint public_key_;
};

class AnonymousKeyVerifier {
public:
    AnonymousKeyVerifier(const uint8_t* label, size_t label_len)
        : label_(label, label + label_len) {}

    void set_public_key(const RistrettoPoint& public_key) {
        public_key_ = public_key;
    }

    AkProofError verify(
        const AnonymousKeySignature& sig,
        const uint8_t* message, size_t message_len
    ) {
        if (sig.enchant_zkp_proof.empty()) {
            return AkProofError::BadArgs;
        }

        Statement st;
        st.add("R", {{"r", "G"}});
        st.add("PK", {{"sk", "G"}});

        std::vector<std::pair<std::string, RistrettoPoint>> point_args;
        point_args.emplace_back("R", sig.commitment);
        point_args.emplace_back("PK", public_key_);

        enchant_zkp::PokshoError err = st.verify_proof(
            sig.enchant_zkp_proof, point_args, message, message_len);

        return (err == enchant_zkp::PokshoError::Ok)
            ? AkProofError::Ok
            : AkProofError::VerificationFailure;
    }

private:
    std::vector<uint8_t> label_;
    RistrettoPoint public_key_;
};

} // namespace ak_proofs
} // namespace zk
} // namespace enchant

#endif

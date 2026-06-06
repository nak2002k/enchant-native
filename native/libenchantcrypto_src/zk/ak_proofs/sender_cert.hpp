#ifndef ENCHANT_ZK_AK_PROOFS_SENDER_CERT_HPP
#define ENCHANT_ZK_AK_PROOFS_SENDER_CERT_HPP

#include <array>
#include <cstdint>
#include <cstring>
#include <vector>
#include "zk/enchant_zkp/enchant_zkp.hpp"
#include "zk/enchant_zkp/ristretto.hpp"
#include "zk/enchant_zkp/sho_hmac_sha256.hpp"
#include "zk/enchant_zkp/sho_ext.hpp"
#include "zk/zkcredential/credentials.hpp"
#include "zk/zkcredential/issuance.hpp"
#include "zk/zkcredential/presentation.hpp"
#include "anonymous_key.hpp"
#include "errors.hpp"

namespace enchant {
namespace zk {
namespace ak_proofs {

using enchant_zkp::RistrettoPoint;
using enchant_zkp::RistrettoScalar;
using enchant_zkp::ShoHmacSha256;
using enchant_zkp::sho_get_point;
using enchant_zkp::sho_get_scalar;

constexpr size_t SENDER_CERT_RANDOMNESS_LEN = 32;

struct SenderCertificateProof {
    zkcredential::PresentationProof credential_proof;
    AnonymousKeySignature key_signature;
};

struct SenderKeyPair {
    RistrettoScalar private_key;
    RistrettoPoint public_key;

    static SenderKeyPair generate(const uint8_t* randomness) {
        ShoHmacSha256 sho(reinterpret_cast<const uint8_t*>(
            "enchant_AKProofs_SenderKeyPair_20240101"), 41);
        sho.absorb_and_ratchet(randomness, SENDER_CERT_RANDOMNESS_LEN);

        SenderKeyPair kp;
        kp.private_key = sho_get_scalar(sho);
        kp.public_key = kp.private_key.scalar_mul_point(base_point()).value();
        return kp;
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

class SenderCertificateProver {
public:
    SenderCertificateProver(const uint8_t* label, size_t label_len)
        : label_(label, label + label_len) {}

    void set_sender_key(const RistrettoPoint& sender_key_point) {
        sender_key_point_ = sender_key_point;
    }

    SenderCertificateProof prove(
        const zkcredential::CredentialPublicKey& cred_public_key,
        const zkcredential::Credential& credential,
        const SenderKeyPair& sender_key,
        const uint8_t* message, size_t message_len,
        const uint8_t* randomness
    ) {
        if (!randomness) return {};

        zkcredential::PresentationProofBuilder builder(label_.data(), label_.size());

        std::array<RistrettoPoint, 2> pts = {sender_key_point_, RistrettoPoint()};
        zkcredential::AnyKeyPair akp;
        akp.a1 = sender_key.private_key;
        akp.a2 = RistrettoScalar::zero();
        akp.public_key_or_id = zkcredential::PublicKeyOrId::from_id(
            std::string(label_.begin(), label_.end()));
        builder.add_attribute(pts, akp);

        auto cred_proof = builder.present(cred_public_key, credential, randomness);

        AnonymousKeyProver key_prover(label_.data(), label_.size());
        key_prover.set_public_key(sender_key.public_key);
        auto key_sig = key_prover.sign(sender_key.private_key,
                                        message, message_len, randomness);

        return {cred_proof, key_sig};
    }

private:
    std::vector<uint8_t> label_;
    RistrettoPoint sender_key_point_;
};

class SenderCertificateVerifier {
public:
    SenderCertificateVerifier(const uint8_t* label, size_t label_len)
        : label_(label, label + label_len) {}

    void set_sender_key_ciphertext(
        const std::array<RistrettoPoint, 2>& ciphertext
    ) {
        ciphertext_ = ciphertext;
    }

    void set_sender_public_key(const RistrettoPoint& public_key) {
        sender_public_key_ = public_key;
    }

    AkProofError verify(
        const zkcredential::CredentialKeyPair& cred_key_pair,
        const SenderCertificateProof& proof,
        const uint8_t* message, size_t message_len
    ) {
        zkcredential::PresentationProofVerifier cred_verifier(label_.data(), label_.size());

        zkcredential::PublicKeyOrId pk_or_id = zkcredential::PublicKeyOrId::from_id(
            std::string(label_.begin(), label_.end()));
        cred_verifier.add_attribute(ciphertext_, pk_or_id);

        bool cred_ok = cred_verifier.verify(cred_key_pair, proof.credential_proof);
        if (!cred_ok) {
            return AkProofError::VerificationFailure;
        }

        AnonymousKeyVerifier key_verifier(label_.data(), label_.size());
        key_verifier.set_public_key(sender_public_key_);
        AkProofError key_err = key_verifier.verify(proof.key_signature, message, message_len);

        return key_err;
    }

private:
    std::vector<uint8_t> label_;
    std::array<RistrettoPoint, 2> ciphertext_;
    RistrettoPoint sender_public_key_;
};

} // namespace ak_proofs
} // namespace zk
} // namespace enchant

#endif

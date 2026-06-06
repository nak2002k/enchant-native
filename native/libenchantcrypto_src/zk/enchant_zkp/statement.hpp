#ifndef ENCHANT_ZK_POKSHO_STATEMENT_HPP
#define ENCHANT_ZK_POKSHO_STATEMENT_HPP

#include <cstdint>
#include <cstring>
#include <string>
#include <unordered_map>
#include <vector>
#include <sodium.h>
#include <sodium/crypto_scalarmult_ristretto255.h>
#include "ristretto.hpp"
#include "sho_hmac_sha256.hpp"
#include "errors.hpp"

namespace enchant {
namespace zk {
namespace enchant_zkp {

using ScalarIndex = uint8_t;
using PointIndex = uint8_t;

struct Term {
    ScalarIndex scalar;
    PointIndex point;
};

struct Equation {
    PointIndex lhs;
    std::vector<Term> rhs;
};

struct Proof {
    RistrettoScalar challenge;
    G1 response;

    std::vector<uint8_t> to_bytes() const {
        std::vector<uint8_t> bytes;
        bytes.insert(bytes.end(), challenge.data(), challenge.data() + RISTRETTO_SCALAR_BYTES);
        for (const auto& r : response) {
            bytes.insert(bytes.end(), r.data(), r.data() + RISTRETTO_SCALAR_BYTES);
        }
        return bytes;
    }

    bool from_bytes(const std::vector<uint8_t>& bytes) {
        if (bytes.size() < RISTRETTO_SCALAR_BYTES) return false;
        if ((bytes.size() - RISTRETTO_SCALAR_BYTES) % RISTRETTO_SCALAR_BYTES != 0) return false;

        challenge = RistrettoScalar::from_bytes(bytes.data());

        size_t num_scalars = (bytes.size() - RISTRETTO_SCALAR_BYTES) / RISTRETTO_SCALAR_BYTES;
        if (num_scalars == 0 || num_scalars > 255) return false;

        response.clear();
        response.reserve(num_scalars);
        for (size_t i = 0; i < num_scalars; i++) {
            response.push_back(RistrettoScalar::from_bytes(
                bytes.data() + RISTRETTO_SCALAR_BYTES + i * RISTRETTO_SCALAR_BYTES));
        }
        return true;
    }
};

class Statement {
public:
    Statement() {
        point_map_["G"] = 0;
        point_vec_.push_back("G");
    }

    bool add(const char* lhs_str, const std::vector<std::pair<const char*, const char*>>& rhs_pairs) {
        if (!lhs_str || std::strlen(lhs_str) == 0 ||
            rhs_pairs.empty() ||
            rhs_pairs.size() > 255 ||
            equations_.size() >= 255) {
            return false;
        }

        PointIndex lhs = add_point(lhs_str);
        std::vector<Term> rhs;
        rhs.reserve(rhs_pairs.size());

        for (const auto& pair : rhs_pairs) {
            if (!pair.first || !pair.second ||
                std::strlen(pair.first) == 0 || std::strlen(pair.second) == 0) {
                return false;
            }
            ScalarIndex scalar = add_scalar(pair.first);
            PointIndex point = add_point(pair.second);
            rhs.push_back({scalar, point});
        }

        equations_.push_back({lhs, std::move(rhs)});
        return true;
    }

    bool add(const std::string& lhs_str,
             const std::vector<std::pair<std::string, std::string>>& rhs_pairs) {
        if (lhs_str.empty() || rhs_pairs.empty() ||
            rhs_pairs.size() > 255 || equations_.size() >= 255) {
            return false;
        }

        PointIndex lhs = add_point(lhs_str.c_str());
        std::vector<Term> rhs;
        rhs.reserve(rhs_pairs.size());

        for (const auto& pair : rhs_pairs) {
            if (pair.first.empty() || pair.second.empty()) {
                return false;
            }
            ScalarIndex scalar = add_scalar(pair.first.c_str());
            PointIndex point = add_point(pair.second.c_str());
            rhs.push_back({scalar, point});
        }

        equations_.push_back({lhs, std::move(rhs)});
        return true;
    }

    PokshoError prove(
        const std::vector<std::pair<std::string, RistrettoScalar>>& scalar_args,
        const std::vector<std::pair<std::string, RistrettoPoint>>& point_args,
        const uint8_t* message, size_t message_len,
        const uint8_t* randomness, // must be 32 bytes
        std::vector<uint8_t>& proof_out
    ) {
        if (!randomness) return PokshoError::BadArgs;

        G1 g1;
        if (!sort_scalars(scalar_args, g1)) {
            return PokshoError::BadArgsWrongNumberOfScalarArgs;
        }

        std::vector<RistrettoPoint> all_points;
        if (!sort_points(point_args, all_points)) {
            return PokshoError::BadArgsWrongNumberOfPointArgs;
        }

        // Absorb the protocol label L, description of statement D, and point values A
        const char* label = "POKSHO_Ristretto_SHOHMACSHA256";
        ShoHmacSha256 sho(reinterpret_cast<const uint8_t*>(label), std::strlen(label));
        auto desc = to_bytes();
        sho.absorb(desc.data(), desc.size());
        for (const auto& point : all_points) {
            sho.absorb(point.data(), RISTRETTO_POINT_BYTES);
        }
        sho.ratchet();

        // Synthetic nonce from randomness, witness (private scalars), and message
        ShoHmacSha256 sho2 = sho;
        sho2.absorb(randomness, 32);
        for (const auto& scalar : g1) {
            sho2.absorb(scalar.data(), RISTRETTO_SCALAR_BYTES);
        }
        sho2.ratchet();
        sho2.absorb_and_ratchet(message, message_len);

        auto blinding_scalar_bytes = sho2.squeeze_and_ratchet(g1.size() * 64);

        G1 nonce;
        nonce.reserve(g1.size());
        for (size_t i = 0; i < g1.size(); i++) {
            nonce.push_back(RistrettoScalar::from_bytes_mod_order_wide(
                blinding_scalar_bytes.data() + i * 64));
        }

        // Commitment R = F(nonce) where F is the homomorphism from G1 -> G2
        G2 commitment = homomorphism(nonce, all_points);

        // Challenge h = H(D || A || R || M)
        for (const auto& point : commitment) {
            sho.absorb(point.data(), RISTRETTO_POINT_BYTES);
        }
        sho.absorb_and_ratchet(message, message_len);
        auto challenge_bytes = sho.squeeze_and_ratchet_64();
        RistrettoScalar challenge = RistrettoScalar::from_bytes_mod_order_wide(challenge_bytes.data());

        // Response s = nonce + witness * challenge (scalar arithmetic)
        G1 response;
        response.reserve(g1.size());
        for (size_t i = 0; i < g1.size(); i++) {
            response.push_back(nonce[i] + g1[i] * challenge);
        }

        Proof proof;
        proof.challenge = challenge;
        proof.response = std::move(response);

        proof_out = proof.to_bytes();

        // Verify before returning
        PokshoError verify_err = verify_proof(proof_out, point_args, message, message_len);
        if (verify_err == PokshoError::VerificationFailure) {
            return PokshoError::ProofCreationVerificationFailure;
        }
        return verify_err;
    }

    PokshoError verify_proof(
        const std::vector<uint8_t>& proof_bytes,
        const std::vector<std::pair<std::string, RistrettoPoint>>& point_args,
        const uint8_t* message, size_t message_len
    ) {
        Proof proof;
        if (!proof.from_bytes(proof_bytes)) {
            return PokshoError::VerificationFailure;
        }

        if (proof.response.size() != scalar_vec_.size()) {
            return PokshoError::VerificationFailure;
        }

        std::vector<RistrettoPoint> all_points;
        if (!sort_points(point_args, all_points)) {
            return PokshoError::BadArgsWrongNumberOfPointArgs;
        }

        // Absorb L, D, A
        const char* label = "POKSHO_Ristretto_SHOHMACSHA256";
        ShoHmacSha256 sho(reinterpret_cast<const uint8_t*>(label), std::strlen(label));
        auto desc = to_bytes();
        sho.absorb(desc.data(), desc.size());
        for (const auto& point : all_points) {
            sho.absorb(point.data(), RISTRETTO_POINT_BYTES);
        }
        sho.ratchet();

        // Reconstruct commitment R = F(s) - h*A
        // F(s) is the homomorphism applied to the response vector
        // h*A is challenge * (LHS points, one per equation)
        G2 commitment = homomorphism_with_subtraction(proof.response, all_points, proof.challenge);

        // Reconstruct challenge h' = H(D || A || R || M)
        for (const auto& point : commitment) {
            sho.absorb(point.data(), RISTRETTO_POINT_BYTES);
        }
        sho.absorb_and_ratchet(message, message_len);
        auto challenge_bytes = sho.squeeze_and_ratchet_64();
        RistrettoScalar expected_challenge = RistrettoScalar::from_bytes_mod_order_wide(challenge_bytes.data());

        if (expected_challenge == proof.challenge) {
            return PokshoError::Ok;
        }
        return PokshoError::VerificationFailure;
    }

    PokshoError sign(
        const RistrettoScalar& private_key,
        const RistrettoPoint& public_key,
        const uint8_t* message, size_t message_len,
        const uint8_t* randomness,
        std::vector<uint8_t>& signature_out
    ) {
        std::vector<std::pair<std::string, RistrettoScalar>> s_args;
        s_args.emplace_back("private_key", private_key);
        std::vector<std::pair<std::string, RistrettoPoint>> p_args;
        p_args.emplace_back("public_key", public_key);
        return prove(s_args, p_args, message, message_len, randomness, signature_out);
    }

    PokshoError verify_signature(
        const std::vector<uint8_t>& signature,
        const RistrettoPoint& public_key,
        const uint8_t* message, size_t message_len
    ) {
        std::vector<std::pair<std::string, RistrettoPoint>> p_args;
        p_args.emplace_back("public_key", public_key);
        return verify_proof(signature, p_args, message, message_len);
    }

private:
    ScalarIndex add_scalar(const char* name) {
        auto it = scalar_map_.find(name);
        if (it != scalar_map_.end()) {
            return it->second;
        }
        ScalarIndex idx = static_cast<ScalarIndex>(scalar_vec_.size());
        scalar_map_[name] = idx;
        scalar_vec_.push_back(name);
        return idx;
    }

    PointIndex add_point(const char* name) {
        auto it = point_map_.find(name);
        if (it != point_map_.end()) {
            return it->second;
        }
        PointIndex idx = static_cast<PointIndex>(point_vec_.size());
        point_map_[name] = idx;
        point_vec_.push_back(name);
        return idx;
    }

    std::vector<uint8_t> to_bytes() const {
        std::vector<uint8_t> v;
        v.push_back(static_cast<uint8_t>(equations_.size()));

        for (const auto& eq : equations_) {
            v.push_back(eq.lhs);
            v.push_back(static_cast<uint8_t>(eq.rhs.size()));
            for (const auto& term : eq.rhs) {
                v.push_back(term.scalar);
                v.push_back(term.point);
            }
        }
        return v;
    }

    // Apply homomorphism F: G1 -> G2
    // For each equation, compute sum(s_i * P_i)
    G2 homomorphism(const G1& scalars, const std::vector<RistrettoPoint>& all_points) const {
        G2 result;
        result.reserve(equations_.size());

        for (const auto& eq : equations_) {
            bool first = true;
            RistrettoPoint accumulated;
            for (const auto& term : eq.rhs) {
                RistrettoPoint term_result = all_points[term.point].scalar_mul(scalars[term.scalar].data()).value();
                if (first) {
                    accumulated = term_result;
                    first = false;
                } else {
                    accumulated = accumulated + term_result;
                }
            }
            result.push_back(accumulated);
        }
        return result;
    }

    // Apply homomorphism and subtract challenge * A (LHS points)
    // R = F(s) - h * A
    G2 homomorphism_with_subtraction(
        const G1& scalars,
        const std::vector<RistrettoPoint>& all_points,
        RistrettoScalar challenge
    ) const {
        G2 result;
        result.reserve(equations_.size());

        for (const auto& eq : equations_) {
            // Compute F(s) for this equation
            bool first = true;
            RistrettoPoint accumulated;
            for (const auto& term : eq.rhs) {
                RistrettoPoint term_result = all_points[term.point].scalar_mul(scalars[term.scalar].data()).value();
                if (first) {
                    accumulated = term_result;
                    first = false;
                } else {
                    accumulated = accumulated + term_result;
                }
            }

            // Subtract challenge * A (LHS point)
            RistrettoPoint lhs_point = all_points[eq.lhs].scalar_mul(challenge.data()).value();
            accumulated = accumulated - lhs_point;

            result.push_back(accumulated);
        }
        return result;
    }

    bool sort_scalars(
        const std::vector<std::pair<std::string, RistrettoScalar>>& scalar_args,
        G1& g1_out
    ) const {
        std::unordered_map<std::string, RistrettoScalar> arg_map;
        for (const auto& [name, val] : scalar_args) {
            arg_map[name] = val;
        }

        if (arg_map.size() != scalar_vec_.size()) {
            return false;
        }

        g1_out.clear();
        g1_out.reserve(scalar_vec_.size());
        for (const auto& name : scalar_vec_) {
            auto it = arg_map.find(name);
            if (it == arg_map.end()) {
                return false;
            }
            g1_out.push_back(it->second);
        }
        return true;
    }

    bool sort_points(
        const std::vector<std::pair<std::string, RistrettoPoint>>& point_args,
        std::vector<RistrettoPoint>& all_points_out
    ) const {
        std::unordered_map<std::string, RistrettoPoint> arg_map;
        for (const auto& [name, val] : point_args) {
            arg_map[name] = val;
        }

        // point_vec_[0] is always "G" (base point)
        if (arg_map.size() != point_vec_.size() - 1) {
            return false;
        }

        all_points_out.clear();
        all_points_out.reserve(point_vec_.size());

        // Use the actual Ristretto base point G, not identity
        RistrettoPoint G;
        uint8_t one[32] = {0};
        one[0] = 1;
        [[maybe_unused]] int rc = crypto_scalarmult_ristretto255_base(G.mutable_data(), one);
        all_points_out.push_back(G);

        for (size_t i = 1; i < point_vec_.size(); i++) {
            auto it = arg_map.find(point_vec_[i]);
            if (it == arg_map.end()) {
                return false;
            }
            all_points_out.push_back(it->second);
        }
        return true;
    }

    std::vector<Equation> equations_;
    std::unordered_map<std::string, ScalarIndex> scalar_map_;
    std::vector<std::string> scalar_vec_;
    std::unordered_map<std::string, PointIndex> point_map_;
    std::vector<std::string> point_vec_;
};

} // namespace enchant_zkp
} // namespace zk
} // namespace enchant

#endif

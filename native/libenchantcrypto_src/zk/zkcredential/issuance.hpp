#ifndef ENCHANT_ZK_ZKCREDENTIAL_ISSUANCE_HPP
#define ENCHANT_ZK_ZKCREDENTIAL_ISSUANCE_HPP

#include <array>
#include <cstdint>
#include <cstring>
#include <vector>
#include "credentials.hpp"
#include "zk/enchant_zkp/enchant_zkp.hpp"
#include "zk/enchant_zkp/sho_hmac_sha256.hpp"

namespace enchant {
namespace zk {
namespace zkcredential {

using enchant_zkp::RistrettoPoint;
using enchant_zkp::RistrettoScalar;
using enchant_zkp::ShoHmacSha256;
using enchant_zkp::sho_get_point;
using enchant_zkp::sho_get_scalar;
using enchant_zkp::Statement;

struct IssuanceProof {
    Credential credential;
    std::vector<uint8_t> enchant_zkp_proof;
    int error;
};

struct VerificationResult {
    Credential credential;
    bool success;
};

class IssuanceProofBuilder {
public:
    explicit IssuanceProofBuilder(const uint8_t* label, size_t label_len)
        : label_(label, label + label_len) {
        // attr_points[0] is reserved for public attributes (identity placeholder)
        attr_points_[0] = RistrettoPoint(); // identity
    }

    void add_attribute(const std::array<RistrettoPoint, 2>& points) {
        size_t idx = num_pts_ + 1;
        if (idx + 1 >= NUM_SUPPORTED_ATTRS) return;
        attr_points_[idx] = points[0];
        attr_points_[idx + 1] = points[1];
        num_attrs_++;
        num_pts_ += 2;
    }

    IssuanceProof issue(const CredentialKeyPair& key_pair, const uint8_t* randomness) {
        // Hash public attributes into a single point
        ShoHmacSha256 public_sho(label_.data(), label_.size());
        attr_points_[0] = sho_get_point(public_sho).value();

        // Generate credential
        ShoHmacSha256 issuance_sho(reinterpret_cast<const uint8_t*>(
            "enchant_ZKCredential_Issuance_20240101"), 39);
        issuance_sho.absorb(randomness, RANDOMNESS_LEN);
        issuance_sho.ratchet();

        Credential credential = key_pair.get_private_key().credential_core(attr_points_, issuance_sho);

        // Build enchant_zkp statement
        // See Chase-Perrin-Zaverucha section 3.2
        Statement st;

        // C_W = w * G_w + wprime * G_wprime
        st.add("C_W", {{"w", "G_w"}, {"wprime", "G_wprime"}});

        // G_V - I = x0 * G_x0 + x1 * G_x1 + sum(yi * G_yi, i = 0..n)
        std::vector<std::pair<std::string, std::string>> gv_terms = {
            {"x0", "G_x0"}, {"x1", "G_x1"}, {"y0", "G_y0"}
        };
        for (size_t i = 1; i < num_pts_ + 1 && i < NUM_SUPPORTED_ATTRS; i++) {
            gv_terms.push_back({"y" + std::to_string(i), "G_y" + std::to_string(i)});
        }
        st.add("G_V-I", gv_terms);

        // V = w * G_w + x0 * U + x1 * tU + sum(yi * Mi, i = 0..n)
        std::vector<std::pair<std::string, std::string>> v_terms = {
            {"w", "G_w"}, {"x0", "U"}, {"x1", "tU"}, {"y0", "M0"}
        };
        for (size_t i = 1; i < num_pts_ + 1 && i < NUM_SUPPORTED_ATTRS; i++) {
            v_terms.push_back({"y" + std::to_string(i), "M" + std::to_string(i)});
        }
        st.add("V", v_terms);

        // Prepare scalar args
        std::vector<std::pair<std::string, RistrettoScalar>> scalar_args;
        scalar_args.emplace_back("w", key_pair.get_private_key().w);
        scalar_args.emplace_back("wprime", key_pair.get_private_key().wprime);
        scalar_args.emplace_back("x0", key_pair.get_private_key().x0);
        scalar_args.emplace_back("x1", key_pair.get_private_key().x1);
        for (size_t i = 0; i < num_pts_ + 1 && i < NUM_SUPPORTED_ATTRS; i++) {
            char name[8];
            snprintf(name, sizeof(name), "y%zu", i);
            RistrettoScalar y_val = key_pair.get_private_key().y[i];
            scalar_args.emplace_back(name, y_val);
        }

        // Prepare point args
        auto system = SystemParams::get_hardcoded();
        std::vector<std::pair<std::string, RistrettoPoint>> point_args;
        point_args.emplace_back("C_W", key_pair.get_public_key().C_W);
        point_args.emplace_back("G_w", system.G_w);
        point_args.emplace_back("G_wprime", system.G_wprime);
        RistrettoPoint gv_minus_i = system.G_V;
        gv_minus_i = gv_minus_i - key_pair.get_public_key().get_I(num_pts_ + 1);
        point_args.emplace_back("G_V-I", gv_minus_i);
        point_args.emplace_back("G_x0", system.G_x0);
        point_args.emplace_back("G_x1", system.G_x1);
        for (size_t i = 0; i < num_pts_ + 1 && i < NUM_SUPPORTED_ATTRS; i++) {
            char gname[8];
            snprintf(gname, sizeof(gname), "G_y%zu", i);
            point_args.emplace_back(gname, system.G_y[i]);
        }
        point_args.emplace_back("V", credential.V);
        point_args.emplace_back("U", credential.U);
        RistrettoPoint tU = credential.t.scalar_mul_point(credential.U).value();
        point_args.emplace_back("tU", tU);
        for (size_t i = 0; i < num_pts_ + 1 && i < NUM_SUPPORTED_ATTRS; i++) {
            char mname[8];
            snprintf(mname, sizeof(mname), "M%zu", i);
            point_args.emplace_back(mname, attr_points_[i]);
        }

        // Generate proof
        std::vector<uint8_t> proof;
        enchant_zkp::PokshoError err = st.prove(
            scalar_args, point_args,
            nullptr, 0,
            issuance_sho.squeeze_and_ratchet_32().data(),
            proof
        );

        IssuanceProof result;
        result.error = static_cast<int>(err);
        if (err != enchant_zkp::PokshoError::Ok) {
            return result;
        }

        result.credential = credential;
        result.enchant_zkp_proof = proof;
        return result;
    }

    VerificationResult verify(const CredentialPublicKey& public_key, const IssuanceProof& proof) {
        // Hash public attributes
        ShoHmacSha256 public_sho(label_.data(), label_.size());
        attr_points_[0] = sho_get_point(public_sho).value();

        // Build same enchant_zkp statement
        Statement st;
        st.add("C_W", {{"w", "G_w"}, {"wprime", "G_wprime"}});

        std::vector<std::pair<std::string, std::string>> gv_terms = {
            {"x0", "G_x0"}, {"x1", "G_x1"}, {"y0", "G_y0"}
        };
        for (size_t i = 1; i < num_pts_ + 1 && i < NUM_SUPPORTED_ATTRS; i++) {
            gv_terms.push_back({"y" + std::to_string(i), "G_y" + std::to_string(i)});
        }
        st.add("G_V-I", gv_terms);

        std::vector<std::pair<std::string, std::string>> v_terms = {
            {"w", "G_w"}, {"x0", "U"}, {"x1", "tU"}, {"y0", "M0"}
        };
        for (size_t i = 1; i < num_pts_ + 1 && i < NUM_SUPPORTED_ATTRS; i++) {
            v_terms.push_back({"y" + std::to_string(i), "M" + std::to_string(i)});
        }
        st.add("V", v_terms);

        // Prepare point args
        auto system = SystemParams::get_hardcoded();
        std::vector<std::pair<std::string, RistrettoPoint>> point_args;
        point_args.emplace_back("C_W", public_key.C_W);
        point_args.emplace_back("G_w", system.G_w);
        point_args.emplace_back("G_wprime", system.G_wprime);
        RistrettoPoint gv_minus_i = system.G_V;
        gv_minus_i = gv_minus_i - public_key.get_I(num_pts_ + 1);
        point_args.emplace_back("G_V-I", gv_minus_i);
        point_args.emplace_back("G_x0", system.G_x0);
        point_args.emplace_back("G_x1", system.G_x1);
        for (size_t i = 0; i < num_pts_ + 1 && i < NUM_SUPPORTED_ATTRS; i++) {
            char gname[8];
            snprintf(gname, sizeof(gname), "G_y%zu", i);
            point_args.emplace_back(gname, system.G_y[i]);
        }
        point_args.emplace_back("V", proof.credential.V);
        point_args.emplace_back("U", proof.credential.U);
        RistrettoPoint tU = proof.credential.t.scalar_mul_point(proof.credential.U).value();
        point_args.emplace_back("tU", tU);
        for (size_t i = 0; i < num_pts_ + 1 && i < NUM_SUPPORTED_ATTRS; i++) {
            char mname[8];
            snprintf(mname, sizeof(mname), "M%zu", i);
            point_args.emplace_back(mname, attr_points_[i]);
        }

        enchant_zkp::PokshoError err = st.verify_proof(proof.enchant_zkp_proof, point_args, nullptr, 0);
        VerificationResult result;
        result.credential = proof.credential;
        result.success = (err == enchant_zkp::PokshoError::Ok);
        return result;
    }

private:
    std::vector<uint8_t> label_;
    std::array<RistrettoPoint, NUM_SUPPORTED_ATTRS> attr_points_{};
    size_t num_attrs_ = 0;
    size_t num_pts_ = 0;
};

} // namespace zkcredential
} // namespace zk
} // namespace enchant

#endif

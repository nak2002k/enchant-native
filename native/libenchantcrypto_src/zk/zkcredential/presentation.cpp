#include "presentation.hpp"

namespace enchant {
namespace zk {
namespace zkcredential {

namespace {

bool get_enchant_zkp_statement(const PresentationProofBuilderCore& core, enchant_zkp::Statement& st_out) {
    enchant_zkp::Statement st;

    if (!st.add(std::string("Z"), {std::pair<std::string, std::string>("z", "I")})) return false;
    if (!st.add(std::string("C_x1"), {
        std::pair<std::string, std::string>("t", "C_x0"),
        std::pair<std::string, std::string>("z0", "G_x0"),
        std::pair<std::string, std::string>("z", "G_x1")
    })) return false;

    std::vector<std::pair<std::string, std::string>> enc_sum;
    for (const auto& key : core.encryption_keys()) {
        const std::string& key_id = key.public_key_or_id.id;
        std::string a1 = "a1_" + key_id;
        if (!st.add(std::string("0"), {
            std::pair<std::string, std::string>("z1_" + key_id, "I"),
            std::pair<std::string, std::string>(a1, "Z")
        })) return false;
        if (key.public_key_or_id.has_public_key) {
            enc_sum.push_back({a1, "G_a1_" + key_id});
            enc_sum.push_back({"a2_" + key_id, "G_a2_" + key_id});
        }
    }
    if (!enc_sum.empty()) {
        std::vector<std::pair<std::string, std::string>> sum_terms;
        for (const auto& t : enc_sum) sum_terms.push_back({t.first, t.second});
        if (!st.add(std::string("sum(A)"), sum_terms)) return false;
    }

    for (const auto& attr : core.attributes()) {
        if (attr.has_key) {
            if (attr.key_index >= core.encryption_keys().size()) continue;
            const std::string& key_id = core.encryption_keys()[attr.key_index].public_key_or_id.id;
            if (!st.add("E_A" + std::to_string(attr.first_point_index), {
                std::pair<std::string, std::string>("a1_" + key_id, "C_y" + std::to_string(attr.first_point_index)),
                std::pair<std::string, std::string>("z1_" + key_id, "G_y" + std::to_string(attr.first_point_index))
            })) return false;
            if (!st.add("C_y" + std::to_string(attr.second_point_index) + "-E_A" + std::to_string(attr.second_point_index), {
                std::pair<std::string, std::string>("z", "G_y" + std::to_string(attr.second_point_index)),
                std::pair<std::string, std::string>("a2_" + key_id, "-E_A" + std::to_string(attr.first_point_index))
            })) return false;
        } else {
            if (!st.add("C_y" + std::to_string(attr.first_point_index), {
                std::pair<std::string, std::string>("z", "G_y" + std::to_string(attr.first_point_index))
            })) return false;
        }
    }
    if (!st.add(std::string("C_y0"), {std::pair<std::string, std::string>("z", "G_y0")})) return false;
    st_out = std::move(st);
    return true;
}

} // namespace

PresentationProof PresentationProofBuilder::present(
        const CredentialPublicKey& public_key,
        const Credential& credential,
        const uint8_t* randomness) {
    const auto& core = *core_;
    auto system = SystemParams::get_hardcoded();

    ShoHmacSha256 sho(reinterpret_cast<const uint8_t*>(
        "enchant_ZKCredential_Presentation_20240101"), 45);
    sho.absorb_and_ratchet(randomness, RANDOMNESS_LEN);
    RistrettoScalar z = sho_get_scalar(sho);

    std::vector<RistrettoPoint> C_y;
    for (size_t i = 0; i < core.attr_points().size(); i++) {
        C_y.push_back(z.scalar_mul_point(system.G_y[i]).value() + core.attr_points()[i]);
    }

    RistrettoPoint C_x0 = z.scalar_mul_point(system.G_x0).value() + credential.U;
    RistrettoPoint C_V = z.scalar_mul_point(system.G_V).value() + credential.V;
    RistrettoPoint C_x1 = z.scalar_mul_point(system.G_x1).value() + (credential.t).scalar_mul_point(credential.U).value();

    PresentationProofCommitments commitments{C_x0, C_x1, C_V, C_y};
    RistrettoScalar z0 = z.negate() * credential.t;

    size_t num_attrs = core.attr_points().size();
    RistrettoPoint I = public_key.get_I(num_attrs);
    RistrettoPoint Z_pt = z.scalar_mul_point(I).value();

    std::vector<std::pair<std::string, RistrettoScalar>> scalar_args;
    scalar_args.emplace_back("z", z);
    scalar_args.emplace_back("t", credential.t);
    scalar_args.emplace_back("z0", z0);
    for (const auto& key : core.encryption_keys()) {
        const std::string& key_id = key.public_key_or_id.id;
        scalar_args.emplace_back("a1_" + key_id, key.a1);
        scalar_args.emplace_back("a2_" + key_id, key.a2);
        scalar_args.emplace_back("z1_" + key_id, z.negate() * key.a1);
    }

    std::vector<std::pair<std::string, RistrettoPoint>> point_args;
    point_args.emplace_back("I", I);
    point_args.emplace_back("C_x0", commitments.C_x0);
    point_args.emplace_back("C_x1", commitments.C_x1);
    point_args.emplace_back("G_x0", system.G_x0);
    point_args.emplace_back("G_x1", system.G_x1);

    if (!core.encryption_keys().empty()) {
        point_args.emplace_back("0", RistrettoPoint());
        RistrettoPoint sum_A;
        for (const auto& key : core.encryption_keys()) {
            if (key.public_key_or_id.has_public_key) {
                const auto& G_a = key.public_key_or_id.public_key.G_a;
                const std::string& key_id = key.public_key_or_id.id;
                point_args.emplace_back("G_a1_" + key_id, G_a[0]);
                point_args.emplace_back("G_a2_" + key_id, G_a[1]);
                sum_A = sum_A + key.public_key_or_id.public_key.A;
            }
        }
        if (sum_A != RistrettoPoint()) {
            point_args.emplace_back("sum(A)", sum_A);
        }
    }

    for (size_t i = 0; i < core.attr_points().size() && i < NUM_SUPPORTED_ATTRS; i++) {
        point_args.emplace_back("G_y" + std::to_string(i), system.G_y[i]);
    }
    point_args.emplace_back("C_y0", commitments.C_y[0]);
    point_args.emplace_back("Z", Z_pt);

    for (const auto& attr : core.attributes()) {
        point_args.emplace_back("C_y" + std::to_string(attr.first_point_index),
                                commitments.C_y[attr.first_point_index]);
        if (attr.has_key && attr.key_index < core.encryption_keys().size()) {
            const auto& key = core.encryption_keys()[attr.key_index];
            RistrettoPoint E_A1 = key.a1.scalar_mul_point(core.attr_points()[attr.first_point_index]).value();
            RistrettoPoint E_A2 = key.a2.scalar_mul_point(E_A1).value() + core.attr_points()[attr.second_point_index];
            point_args.emplace_back("E_A" + std::to_string(attr.first_point_index), E_A1);
            point_args.emplace_back("-E_A" + std::to_string(attr.first_point_index), RistrettoPoint() - E_A1);
            point_args.emplace_back(
                "C_y" + std::to_string(attr.second_point_index) + "-E_A" + std::to_string(attr.second_point_index),
                commitments.C_y[attr.second_point_index] - E_A2);
        }
    }

    enchant_zkp::Statement st;
    if (!get_enchant_zkp_statement(core, st)) {
        return {commitments, {}};
    }
    std::vector<uint8_t> proof_bytes;
    enchant_zkp::PokshoError prove_err = st.prove(scalar_args, point_args,
             core.authenticated_message().data(), core.authenticated_message().size(),
             sho.squeeze_and_ratchet_32().data(), proof_bytes);

    if (prove_err != enchant_zkp::PokshoError::Ok) {
        proof_bytes.clear();
    }

    return {commitments, proof_bytes};
}

bool PresentationProofVerifier::verify(const CredentialKeyPair& key_pair,
                                        const PresentationProof& proof) {
    RistrettoPoint public_attr_point = sho_get_point(public_attrs_sho_).value();
    core_.set_public_attrs_point(public_attr_point);

    const auto& private_key = key_pair.get_private_key();
    const auto& commitments = proof.commitments;
    const auto& C_x0 = commitments.C_x0;
    const auto& C_x1 = commitments.C_x1;
    const auto& C_V = commitments.C_V;
    const auto& C_y = commitments.C_y;

    if (C_y.size() != core_.attr_points().size()) return false;

    RistrettoPoint Z_pt = C_V - private_key.W;
    Z_pt = Z_pt - private_key.x0.scalar_mul_point(C_x0).value();
    Z_pt = Z_pt - private_key.x1.scalar_mul_point(C_x1).value();
    for (size_t i = 0; i < private_key.y.size() && i < C_y.size(); i++) {
        Z_pt = Z_pt - private_key.y[i].scalar_mul_point(C_y[i]).value();
    }
    Z_pt = Z_pt - private_key.y[0].scalar_mul_point(public_attr_point).value();

    for (const auto& attr : core_.attributes()) {
        if (!attr.has_key) {
            Z_pt = Z_pt - private_key.y[attr.first_point_index].scalar_mul_point(
                core_.attr_points()[attr.first_point_index]).value();
        }
    }

    auto system = SystemParams::get_hardcoded();
    const auto& public_key = key_pair.get_public_key();
    size_t num_attrs = core_.attr_points().size();
    RistrettoPoint I = public_key.get_I(num_attrs);

    std::vector<std::pair<std::string, RistrettoPoint>> point_args;
    point_args.emplace_back("I", I);
    point_args.emplace_back("C_x0", C_x0);
    point_args.emplace_back("C_x1", C_x1);
    point_args.emplace_back("G_x0", system.G_x0);
    point_args.emplace_back("G_x1", system.G_x1);

    if (!core_.encryption_keys().empty()) {
        point_args.emplace_back("0", RistrettoPoint());
        RistrettoPoint sum_A;
        for (const auto& key : core_.encryption_keys()) {
            if (key.public_key_or_id.has_public_key) {
                const auto& G_a = key.public_key_or_id.public_key.G_a;
                const std::string& key_id = key.public_key_or_id.id;
                point_args.emplace_back("G_a1_" + key_id, G_a[0]);
                point_args.emplace_back("G_a2_" + key_id, G_a[1]);
                sum_A = sum_A + key.public_key_or_id.public_key.A;
            }
        }
        if (sum_A != RistrettoPoint()) {
            point_args.emplace_back("sum(A)", sum_A);
        }
    }

    for (size_t i = 0; i < core_.attr_points().size() && i < NUM_SUPPORTED_ATTRS; i++) {
        point_args.emplace_back("G_y" + std::to_string(i), system.G_y[i]);
    }
    point_args.emplace_back("C_y0", C_y[0]);

    for (const auto& attr : core_.attributes()) {
        point_args.emplace_back("C_y" + std::to_string(attr.first_point_index),
                                C_y[attr.first_point_index]);
        if (attr.has_key) {
            if (attr.key_index < core_.encryption_keys().size()) {
                point_args.emplace_back("E_A" + std::to_string(attr.first_point_index),
                                        core_.attr_points()[attr.first_point_index]);
                point_args.emplace_back("-E_A" + std::to_string(attr.first_point_index),
                                        RistrettoPoint() - core_.attr_points()[attr.first_point_index]);
                point_args.emplace_back(
                    "C_y" + std::to_string(attr.second_point_index) + "-E_A" + std::to_string(attr.second_point_index),
                    C_y[attr.second_point_index] - core_.attr_points()[attr.second_point_index]);
            }
        }
    }

    point_args.emplace_back("Z", Z_pt);

    enchant_zkp::Statement st;
    if (!get_enchant_zkp_statement(core_, st)) {
        return false;
    }
    return enchant_zkp::PokshoError::Ok == st.verify_proof(
        proof.enchant_zkp_proof, point_args,
        core_.authenticated_message().data(), core_.authenticated_message().size());
}

} // namespace zkcredential
} // namespace zk
} // namespace enchant

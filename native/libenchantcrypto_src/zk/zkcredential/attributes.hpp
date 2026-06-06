#ifndef ENCHANT_ZK_ZKCREDENTIAL_ATTRIBUTES_HPP
#define ENCHANT_ZK_ZKCREDENTIAL_ATTRIBUTES_HPP

#include <array>
#include <cstdint>
#include <cstring>
#include <optional>
#include <string>
#include "zk/enchant_zkp/ristretto.hpp"
#include "zk/enchant_zkp/sho_hmac_sha256.hpp"
#include "zk/enchant_zkp/sho_ext.hpp"
#include "zk/zkcredential/presentation.hpp"

namespace enchant {
namespace zk {
namespace zkcredential {

constexpr size_t ATTRIBUTE_RANDOMNESS_LEN = 32;

// System parameters for attribute encryption: two generators G_a1, G_a2.
// Derived per-domain via domain-separated SHO.
struct AttributeSystemParams {
    std::array<RistrettoPoint, 2> G_a;

    static AttributeSystemParams generate(const std::string& domain_id) {
        std::string label = "enchant_AttributeSystemParams_" + domain_id;
        ShoHmacSha256 sho(reinterpret_cast<const uint8_t*>(label.data()), label.size());
        AttributeSystemParams p;
        p.G_a[0] = sho_get_point(sho).value();
        p.G_a[1] = sho_get_point(sho).value();
        return p;
    }
};

// Encryption key pair for verifiable attribute encryption.
// Each key has two scalars (a1, a2) and derives a public key A = a1*G_a1 + a2*G_a2.
struct AttributeKeyPair {
    RistrettoScalar a1;
    RistrettoScalar a2;
    std::string domain_id;
    AttributeSystemParams params;

    AnyPublicKey get_public_key() const {
        AnyPublicKey pk;
        pk.id = domain_id;
        pk.G_a = params.G_a;
        pk.A = a1.scalar_mul_point(params.G_a[0]).value() +
               a2.scalar_mul_point(params.G_a[1]).value();
        return pk;
    }

    AnyKeyPair to_any_key_pair() const {
        AnyKeyPair akp;
        akp.a1 = a1;
        akp.a2 = a2;
        akp.public_key_or_id = PublicKeyOrId::from_public_key(get_public_key());
        return akp;
    }

    static AttributeKeyPair generate(const uint8_t* randomness,
                                     const std::string& domain_id) {
        AttributeKeyPair kp;
        kp.domain_id = domain_id;
        kp.params = AttributeSystemParams::generate(domain_id);

        std::string sho_label = "enchant_AttributeKeyPair_" + domain_id;
        ShoHmacSha256 sho(reinterpret_cast<const uint8_t*>(sho_label.data()), sho_label.size());
        sho.absorb_and_ratchet(randomness, ATTRIBUTE_RANDOMNESS_LEN);
        kp.a1 = sho_get_scalar(sho);
        kp.a2 = sho_get_scalar(sho);
        return kp;
    }
};

// Ciphertext for a single attribute (two Ristretto points = 64 bytes).
struct AttributeCiphertext {
    RistrettoPoint E_A1;
    RistrettoPoint E_A2;

    // Serialize to 64 bytes
    std::array<uint8_t, 64> serialize() const {
        std::array<uint8_t, 64> out;
        auto c1 = E_A1.compress();
        auto c2 = E_A2.compress();
        std::memcpy(out.data(), c1.data(), 32);
        std::memcpy(out.data() + 32, c2.data(), 32);
        return out;
    }

    // Deserialize from 64 bytes
    static std::optional<AttributeCiphertext> deserialize(const uint8_t* data, size_t len) {
        if (!data || len != 64) return std::nullopt;
        auto p1 = RistrettoPoint::from_bytes(data);
        auto p2 = RistrettoPoint::from_bytes(data + 32);
        if (!p1 || !p2) return std::nullopt;
        return AttributeCiphertext{*p1, *p2};
    }
};

// Encrypt an attribute pair (M1, M2) under the given key.
// Returns (E_A1, E_A2) where:
//   E_A1 = a1 * M1
//   E_A2 = a2 * E_A1 + M2
inline AttributeCiphertext encrypt_attribute(
    const AttributeKeyPair& key_pair,
    const RistrettoPoint& M1,
    const RistrettoPoint& M2) {
    auto E_A1 = key_pair.a1.scalar_mul_point(M1).value();
    auto E_A2 = key_pair.a2.scalar_mul_point(E_A1).value() + M2;
    return AttributeCiphertext{E_A1, E_A2};
}

// Decrypt ciphertext back to attribute points.
// Returns (M1, M2) where:
//   M1 = a1^{-1} * E_A1
//   M2 = E_A2 - a2 * E_A1
inline std::optional<std::pair<RistrettoPoint, RistrettoPoint>> decrypt_attribute(
    const AttributeKeyPair& key_pair,
    const RistrettoPoint& E_A1,
    const RistrettoPoint& E_A2) {
    auto a1_inv = key_pair.a1.invert();
    if (!a1_inv) return std::nullopt;
    auto M1 = a1_inv->scalar_mul_point(E_A1).value();
    auto a2_E_A1 = key_pair.a2.scalar_mul_point(E_A1).value();
    auto M2 = E_A2 - a2_E_A1;
    return std::make_pair(M1, M2);
}

// Derive a Ristretto point from raw attribute bytes.
// Uses hash-to-group with domain separation.
inline RistrettoPoint derive_attribute_point(
    const uint8_t* attr_bytes, size_t attr_len,
    const std::string& domain_context) {
    std::string label = "enchant_AttributePoint_" + domain_context;
    ShoHmacSha256 sho(reinterpret_cast<const uint8_t*>(label.data()), label.size());
    sho.absorb_and_ratchet(attr_bytes, attr_len);
    return sho_get_point(sho).value();
}

// Derive two Ristretto points from raw attribute bytes (for the two-point scheme).
// M1 = HashToPoint(1, domain_context, attr_bytes)
// M2 = HashToPoint(2, domain_context, attr_bytes)
inline std::array<RistrettoPoint, 2> derive_attribute_points(
    const uint8_t* attr_bytes, size_t attr_len,
    const std::string& domain_context) {
    std::string label1 = "enchant_AttributePoint_1_" + domain_context;
    ShoHmacSha256 sho1(reinterpret_cast<const uint8_t*>(label1.data()), label1.size());
    sho1.absorb_and_ratchet(attr_bytes, attr_len);

    std::string label2 = "enchant_AttributePoint_2_" + domain_context;
    ShoHmacSha256 sho2(reinterpret_cast<const uint8_t*>(label2.data()), label2.size());
    sho2.absorb_and_ratchet(attr_bytes, attr_len);

    return {sho_get_point(sho1).value(), sho_get_point(sho2).value()};
}

} // namespace zkcredential
} // namespace zk
} // namespace enchant

#endif

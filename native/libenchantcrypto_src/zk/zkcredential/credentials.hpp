#ifndef ENCHANT_ZK_ZKCREDENTIAL_CREDENTIALS_HPP
#define ENCHANT_ZK_ZKCREDENTIAL_CREDENTIALS_HPP

#include <array>
#include <cstdint>
#include <cstring>
#include <sodium.h>
#include "zk/enchant_zkp/sho_hmac_sha256.hpp"
#include "zk/enchant_zkp/sho.hpp"
#include "zk/enchant_zkp/sho_ext.hpp"
#include "zk/enchant_zkp/ristretto.hpp"

namespace enchant {
namespace zk {
namespace zkcredential {

using enchant_zkp::RistrettoPoint;
using enchant_zkp::RistrettoScalar;
using enchant_zkp::ShoHmacSha256;
using enchant_zkp::sho_get_point;
using enchant_zkp::sho_get_scalar;

constexpr size_t RANDOMNESS_LEN = 32;
constexpr size_t NUM_SUPPORTED_ATTRS = 7;
constexpr size_t SYSTEM_PARAMS_BYTES = 8 * 32 + 7 * 32; // 8 generators + 7 G_y

using CredentialRandomness = std::array<uint8_t, RANDOMNESS_LEN>;

struct SystemParams {
    RistrettoPoint G_w;
    RistrettoPoint G_wprime;
    RistrettoPoint G_x0;
    RistrettoPoint G_x1;
    RistrettoPoint G_V;
    RistrettoPoint G_z;
    std::array<RistrettoPoint, NUM_SUPPORTED_ATTRS> G_y;

    static SystemParams get_hardcoded() {
        static const SystemParams params = generate();
        return params;
    }

    static SystemParams generate() {
        ShoHmacSha256 sho(reinterpret_cast<const uint8_t*>(
            "enchant_ZKCredential_ConstantSystemParams_generate_20240101"),
            59);

        SystemParams p;
        p.G_w = sho_get_point(sho).value();
        p.G_wprime = sho_get_point(sho).value();
        p.G_x0 = sho_get_point(sho).value();
        p.G_x1 = sho_get_point(sho).value();
        p.G_V = sho_get_point(sho).value();
        p.G_z = sho_get_point(sho).value();
        for (size_t i = 0; i < NUM_SUPPORTED_ATTRS; i++) {
            p.G_y[i] = sho_get_point(sho).value();
        }
        return p;
    }
};

struct Credential {
    RistrettoScalar t;
    RistrettoPoint U;
    RistrettoPoint V;
};

struct CredentialPrivateKey {
    RistrettoScalar w;
    RistrettoScalar wprime;
    RistrettoPoint W;
    RistrettoScalar x0;
    RistrettoScalar x1;
    std::array<RistrettoScalar, NUM_SUPPORTED_ATTRS> y;

    static CredentialPrivateKey generate(const uint8_t* randomness) {
        if (!randomness) return CredentialPrivateKey{};

        ShoHmacSha256 sho(reinterpret_cast<const uint8_t*>(
            "enchant_ZKCredential_CredentialPrivateKey_generate_20240101"),
            60);
        sho.absorb(randomness, RANDOMNESS_LEN);
        sho.ratchet();

        auto system = SystemParams::get_hardcoded();

        CredentialPrivateKey pk;
        pk.w = sho_get_scalar(sho);
        pk.W = pk.w.scalar_mul_point(system.G_w).value();
        pk.wprime = sho_get_scalar(sho);
        pk.x0 = sho_get_scalar(sho);
        pk.x1 = sho_get_scalar(sho);
        for (size_t i = 0; i < NUM_SUPPORTED_ATTRS; i++) {
            pk.y[i] = sho_get_scalar(sho);
        }
        return pk;
    }

    Credential credential_core(const std::array<RistrettoPoint, NUM_SUPPORTED_ATTRS>& M,
                                ShoHmacSha256& sho) const {
        Credential c;
        c.t = sho_get_scalar(sho);
        c.U = sho_get_point(sho).value();

        // V = W + (x0 + x1*t) * U + sum(yi * Mi)
        c.V = W;
        RistrettoScalar x0_plus_x1_t = x0 + x1 * c.t;
        c.V = c.V + x0_plus_x1_t.scalar_mul_point(c.U).value();
        for (size_t i = 0; i < M.size(); i++) {
            c.V = c.V + y[i].scalar_mul_point(M[i]).value();
        }
        return c;
    }
};

struct CredentialPublicKey {
    RistrettoPoint C_W;
    std::array<RistrettoPoint, NUM_SUPPORTED_ATTRS - 1> I;

    RistrettoPoint get_I(size_t num_attrs) const {
        // num_attrs is 1..NUM_SUPPORTED_ATTRS
        // I[0] is for 1 attribute, I[1] for 2 attributes, etc.
        if (num_attrs < 1 || num_attrs > NUM_SUPPORTED_ATTRS) {
            return RistrettoPoint(); // identity
        }
        return I[num_attrs - 1];
    }

    static CredentialPublicKey from_private(const CredentialPrivateKey& pk) {
        auto system = SystemParams::get_hardcoded();

        CredentialPublicKey pub;
        pub.C_W = pk.W;
        pub.C_W = pub.C_W + pk.wprime.scalar_mul_point(system.G_wprime).value();

        // I_i = G_V - x0*G_x0 - x1*G_x1 - sum(yi*G_yi, i=0..n)
        RistrettoPoint I_val = system.G_V;
        I_val = I_val - pk.x0.scalar_mul_point(system.G_x0).value();
        I_val = I_val - pk.x1.scalar_mul_point(system.G_x1).value();
        I_val = I_val - pk.y[0].scalar_mul_point(system.G_y[0]).value();

        for (size_t i = 0; i < NUM_SUPPORTED_ATTRS - 1; i++) {
            pub.I[i] = I_val;
            I_val = I_val - pk.y[i + 1].scalar_mul_point(system.G_y[i + 1]).value();
        }
        return pub;
    }
};

struct CredentialKeyPair {
    CredentialPrivateKey private_key;
    CredentialPublicKey public_key;

    static CredentialKeyPair generate(const uint8_t* randomness) {
        CredentialKeyPair kp;
        kp.private_key = CredentialPrivateKey::generate(randomness);
        kp.public_key = CredentialPublicKey::from_private(kp.private_key);
        return kp;
    }

    const CredentialPublicKey& get_public_key() const { return public_key; }
    const CredentialPrivateKey& get_private_key() const { return private_key; }
};

} // namespace zkcredential
} // namespace zk
} // namespace enchant

#endif

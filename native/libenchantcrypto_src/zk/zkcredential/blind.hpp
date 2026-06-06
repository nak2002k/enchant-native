#ifndef ENCHANT_ZK_ZKCREDENTIAL_BLIND_HPP
#define ENCHANT_ZK_ZKCREDENTIAL_BLIND_HPP

#include <array>
#include <cstdint>
#include <cstring>
#include <vector>
#include "credentials.hpp"
#include "zk/enchant_zkp/ristretto.hpp"
#include "zk/enchant_zkp/sho_hmac_sha256.hpp"
#include "zk/enchant_zkp/sho_ext.hpp"

namespace enchant {
namespace zk {
namespace zkcredential {

using enchant_zkp::RistrettoPoint;
using enchant_zkp::RistrettoScalar;
using enchant_zkp::ShoHmacSha256;
using enchant_zkp::sho_get_point;
using enchant_zkp::sho_get_scalar;

struct BlindingPrivateKey {
    RistrettoScalar y;
};

struct BlindingPublicKey {
    RistrettoPoint Y;
};

struct BlindingKeyPair {
    BlindingPrivateKey private_key;
    BlindingPublicKey public_key;

    static BlindingKeyPair generate(const uint8_t* randomness) {
        ShoHmacSha256 sho(reinterpret_cast<const uint8_t*>(
            "enchant_ZKCredential_BlindingKeyPair_20240101"), 47);
        sho.absorb_and_ratchet(randomness, RANDOMNESS_LEN);

        BlindingKeyPair kp;
        kp.private_key.y = sho_get_scalar(sho);
        kp.public_key.Y = kp.private_key.y.scalar_mul_point(base_point()).value();
        return kp;
    }

    static RistrettoPoint base_point() {
        RistrettoPoint G;
        uint8_t one[32] = {0};
        one[0] = 1;
        crypto_scalarmult_ristretto255_base(G.mutable_data(), one);
        return G;
    }
};

struct BlindedPoint {
    RistrettoPoint D1;
    RistrettoPoint D2;
};

struct BlindedAttribute {
    std::array<BlindedPoint, 2> blinded_points;
};

inline BlindedPoint blind_point(
    const BlindingKeyPair& key,
    const RistrettoPoint& M,
    ShoHmacSha256& sho
) {
    RistrettoScalar r = sho_get_scalar(sho);
    RistrettoPoint D1 = r.scalar_mul_point(BlindingKeyPair::base_point()).value();
    RistrettoPoint D2 = r.scalar_mul_point(key.public_key.Y).value() + M;
    return {D1, D2};
}

inline BlindedAttribute blind_attribute(
    const BlindingKeyPair& key,
    const std::array<RistrettoPoint, 2>& points,
    ShoHmacSha256& sho
) {
    BlindedAttribute attr;
    attr.blinded_points[0] = blind_point(key, points[0], sho);
    attr.blinded_points[1] = blind_point(key, points[1], sho);
    return attr;
}

inline RistrettoPoint unblind_point(
    const BlindingKeyPair& key,
    const BlindedPoint& blinded
) {
    RistrettoPoint yD1 = key.private_key.y.scalar_mul_point(blinded.D1).value();
    return blinded.D2 - yD1;
}

inline std::array<RistrettoPoint, 2> unblind_attribute(
    const BlindingKeyPair& key,
    const BlindedAttribute& blinded
) {
    return {
        unblind_point(key, blinded.blinded_points[0]),
        unblind_point(key, blinded.blinded_points[1])
    };
}

} // namespace zkcredential
} // namespace zk
} // namespace enchant

#endif

#ifndef ENCHANT_ZK_POKSHO_SHO_EXT_HPP
#define ENCHANT_ZK_POKSHO_SHO_EXT_HPP

#include "sho_hmac_sha256.hpp"
#include "ristretto.hpp"

namespace enchant {
namespace zk {
namespace enchant_zkp {

inline std::optional<RistrettoPoint> sho_get_point(ShoHmacSha256& sho) {
    std::array<uint8_t, 64> buf{};
    sho.squeeze_and_ratchet_into(buf.data(), 64);
    return RistrettoPoint::from_hash(buf.data());
}

inline RistrettoScalar sho_get_scalar(ShoHmacSha256& sho) {
    std::array<uint8_t, 64> buf{};
    sho.squeeze_and_ratchet_into(buf.data(), 64);
    return RistrettoScalar::from_bytes_mod_order_wide(buf.data());
}

} // namespace enchant_zkp
} // namespace zk
} // namespace enchant

#endif

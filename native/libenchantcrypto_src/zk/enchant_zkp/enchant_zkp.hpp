#ifndef ENCHANT_ZK_POKSHO_POKSHO_HPP
#define ENCHANT_ZK_POKSHO_POKSHO_HPP

#include "ristretto.hpp"
#include "sho.hpp"
#include "sho_hmac_sha256.hpp"
#include "statement.hpp"
#include "errors.hpp"

namespace enchant {
namespace zk {
namespace enchant_zkp {

// Convenience sign/verify functions
inline PokshoError sign(
    const RistrettoScalar& private_key,
    const RistrettoPoint& public_key,
    const uint8_t* message, size_t message_len,
    const uint8_t* randomness, // must be 32 bytes
    std::vector<uint8_t>& signature_out
) {
    Statement st;
    st.add("public_key", {{"private_key", "G"}});
    return st.sign(private_key, public_key, message, message_len, randomness, signature_out);
}

inline PokshoError verify_signature(
    const std::vector<uint8_t>& signature,
    const RistrettoPoint& public_key,
    const uint8_t* message, size_t message_len
) {
    Statement st;
    st.add("public_key", {{"private_key", "G"}});
    return st.verify_signature(signature, public_key, message, message_len);
}

} // namespace enchant_zkp
} // namespace zk
} // namespace enchant

#endif

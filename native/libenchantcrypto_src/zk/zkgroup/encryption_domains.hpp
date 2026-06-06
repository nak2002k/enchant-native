#ifndef ENCHANT_ZK_ZKGROUP_ENCRYPTION_DOMAINS_HPP
#define ENCHANT_ZK_ZKGROUP_ENCRYPTION_DOMAINS_HPP

#include "zk/zkcredential/attributes.hpp"

namespace enchant {
namespace zk {
namespace zkgroup {

using enchant_zkp::ShoHmacSha256;

// UID encryption domain: encrypts UUIDs (ACI, PNI).
// Each group derives its own key pair from the group master key.
struct UidEncryptionDomain {
    static constexpr const char* DOMAIN_ID = "enchant_ZKGroup_UidEncryption_20240101";
    static constexpr const char* KEY_LABEL = "enchant_ZKGroup_UidEncKeyPair_20240101";

    static zkcredential::AttributeSystemParams system_params() {
        return zkcredential::AttributeSystemParams::generate(DOMAIN_ID);
    }

    static zkcredential::AttributeKeyPair derive_from(const uint8_t* master_key, size_t key_len) {
        ShoHmacSha256 sho(reinterpret_cast<const uint8_t*>(KEY_LABEL),
                          std::strlen(KEY_LABEL));
        sho.absorb_and_ratchet(master_key, key_len);
        auto randomness = sho.squeeze_and_ratchet_32();
        return zkcredential::AttributeKeyPair::generate(randomness.data(), DOMAIN_ID);
    }
};

// Profile key encryption domain: encrypts profile keys.
struct ProfileKeyEncryptionDomain {
    static constexpr const char* DOMAIN_ID = "enchant_ZKGroup_ProfileKeyEncryption_20240101";
    static constexpr const char* KEY_LABEL = "enchant_ZKGroup_ProfileKeyEncKeyPair_20240101";

    static zkcredential::AttributeSystemParams system_params() {
        return zkcredential::AttributeSystemParams::generate(DOMAIN_ID);
    }

    static zkcredential::AttributeKeyPair derive_from(const uint8_t* master_key, size_t key_len) {
        ShoHmacSha256 sho(reinterpret_cast<const uint8_t*>(KEY_LABEL),
                          std::strlen(KEY_LABEL));
        sho.absorb_and_ratchet(master_key, key_len);
        auto randomness = sho.squeeze_and_ratchet_32();
        return zkcredential::AttributeKeyPair::generate(randomness.data(), DOMAIN_ID);
    }
};

} // namespace zkgroup
} // namespace zk
} // namespace enchant

#endif

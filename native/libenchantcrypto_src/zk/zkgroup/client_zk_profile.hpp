#ifndef ENCHANT_ZK_ZKGROUP_CLIENT_ZK_PROFILE_HPP
#define ENCHANT_ZK_ZKGROUP_CLIENT_ZK_PROFILE_HPP

#include <cstdint>
#include <cstddef>
#include <vector>
#include <array>
#include <optional>
#include "enchant/error.h"
#include "zk/enchant_zkp/ristretto.hpp"
#include "zk/zkcredential/attributes.hpp"
#include "zk/zkcredential/credentials.hpp"
#include "zk/zkgroup/server_params.hpp"
#include "zk/zkgroup/profile_key_credential.hpp"
#include "zk/zkgroup/group_secret_params.hpp"

namespace enchant {
namespace zk {
namespace zkgroup {

constexpr size_t CLIENT_ZK_UUID_SIZE = 16;
constexpr size_t CLIENT_ZK_PROFILE_KEY_SIZE = 32;

struct EncryptedProfile {
    std::vector<uint8_t> encrypted_data;
    uint32_t version;
};

struct ProfileKeyVersion {
    std::array<uint8_t, 32> version_bytes;
};

class ClientZkProfileOperations {
public:
    ClientZkProfileOperations();

    int initialize(const ServerPublicParams& server_params,
                   const GroupSecretParams& group_params);

    int encrypt_profile_for_storage(
        const uint8_t* profile_data, size_t profile_data_len,
        const std::array<uint8_t, CLIENT_ZK_PROFILE_KEY_SIZE>& profile_key,
        EncryptedProfile& encrypted_profile);

    int decrypt_profile(
        const EncryptedProfile& encrypted_profile,
        const std::array<uint8_t, CLIENT_ZK_PROFILE_KEY_SIZE>& profile_key,
        std::vector<uint8_t>& profile_data);

    int encrypt_profile_for_multiple_recipients(
        const uint8_t* profile_data, size_t profile_data_len,
        const std::array<uint8_t, CLIENT_ZK_PROFILE_KEY_SIZE>& profile_key,
        const std::vector<std::array<uint8_t, 32>>& recipient_keys,
        std::vector<EncryptedProfile>& encrypted_profiles);

    int show_uuid(
        const std::array<uint8_t, CLIENT_ZK_UUID_SIZE>& uuid,
        const uint8_t* randomness, size_t randomness_len,
        zkcredential::PresentationProof& proof);

    int show_profile_key(
        const std::array<uint8_t, CLIENT_ZK_PROFILE_KEY_SIZE>& profile_key,
        const uint8_t* randomness, size_t randomness_len,
        zkcredential::PresentationProof& proof);

    int show_uuid_from_credential(
        const ProfileKeyCredential& credential,
        const std::array<uint8_t, CLIENT_ZK_UUID_SIZE>& uuid,
        const std::array<uint8_t, CLIENT_ZK_PROFILE_KEY_SIZE>& profile_key,
        const uint8_t* randomness, size_t randomness_len,
        ProfileKeyCredentialPresentation& presentation);

    ProfileKeyVersion get_profile_key_version(
        const std::array<uint8_t, CLIENT_ZK_PROFILE_KEY_SIZE>& profile_key) const;

    bool is_initialized() const { return initialized_; }

private:
    ServerPublicParams server_params_;
    GroupSecretParams group_params_;
    bool initialized_;

    int derive_encryption_key(
        const std::array<uint8_t, CLIENT_ZK_PROFILE_KEY_SIZE>& profile_key,
        std::array<uint8_t, 32>& derived_key) const;
};

} // namespace zkgroup
} // namespace zk
} // namespace enchant

#endif

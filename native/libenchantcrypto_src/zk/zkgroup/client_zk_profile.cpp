#include "zk/zkgroup/client_zk_profile.hpp"
#include "primitives/hkdf.hpp"
#include "primitives/xchacha20.hpp"
#include "primitives/random.hpp"
#include "primitives/hash.hpp"
#include "zk/enchant_zkp/ristretto.hpp"
#include "zk/enchant_zkp/sho_hmac_sha256.hpp"
#include "zk/enchant_zkp/sho_ext.hpp"
#include <sodium.h>
#include <cstring>

namespace enchant {
namespace zk {
namespace zkgroup {

constexpr const char* CLIENT_ZK_PROFILE_STORAGE_LABEL = "EnchantClientZkProfile_StorageKey_20240101";
constexpr const char* CLIENT_ZK_PROFILE_RECIPIENT_LABEL = "EnchantClientZkProfile_RecipientKey_20240101";
constexpr const char* CLIENT_ZK_UUID_SHOW_LABEL = "enchant_ZKGroup_ShowUuid_20240101";
constexpr const char* CLIENT_ZK_PK_SHOW_LABEL = "enchant_ZKGroup_ShowProfileKey_20240101";
constexpr const char* CLIENT_ZK_PK_VERSION_LABEL = "enchant_ZKGroup_ProfileKeyVersion_20240101";

ClientZkProfileOperations::ClientZkProfileOperations()
    : initialized_(false) {}

int ClientZkProfileOperations::initialize(
    const ServerPublicParams& server_params,
    const GroupSecretParams& group_params) {
    server_params_ = server_params;
    group_params_ = group_params;
    initialized_ = true;
    return ENCHANT_SUCCESS;
}

int ClientZkProfileOperations::derive_encryption_key(
    const std::array<uint8_t, CLIENT_ZK_PROFILE_KEY_SIZE>& profile_key,
    std::array<uint8_t, 32>& derived_key) const {
    uint8_t salt[32] = {0};
    int rc = primitives::hkdf_derive(
        profile_key.data(), CLIENT_ZK_PROFILE_KEY_SIZE,
        salt, 32,
        reinterpret_cast<const uint8_t*>(CLIENT_ZK_PROFILE_STORAGE_LABEL),
        strlen(CLIENT_ZK_PROFILE_STORAGE_LABEL),
        derived_key.data(), 32);
    sodium_memzero(salt, sizeof(salt));
    return rc;
}

int ClientZkProfileOperations::encrypt_profile_for_storage(
    const uint8_t* profile_data, size_t profile_data_len,
    const std::array<uint8_t, CLIENT_ZK_PROFILE_KEY_SIZE>& profile_key,
    EncryptedProfile& encrypted_profile) {
    if (!initialized_) return ENCHANT_ERROR_INTERNAL;
    if (!profile_data && profile_data_len > 0) return ENCHANT_ERROR_NULL_POINTER;

    std::array<uint8_t, 32> enc_key;
    int rc = derive_encryption_key(profile_key, enc_key);
    if (rc != ENCHANT_SUCCESS) return rc;

    uint8_t nonce[24];
    randombytes_buf(nonce, sizeof(nonce));

    encrypted_profile.version = 1;
    size_t ciphertext_len = profile_data_len + 16;
    encrypted_profile.encrypted_data.resize(24 + ciphertext_len);

    memcpy(encrypted_profile.encrypted_data.data(), nonce, 24);

    rc = primitives::xchacha20_encrypt(
        profile_data, profile_data_len,
        enc_key.data(), nonce,
        encrypted_profile.encrypted_data.data() + 24, ciphertext_len);

    sodium_memzero(enc_key.data(), 32);
    sodium_memzero(nonce, sizeof(nonce));

    if (rc != ENCHANT_SUCCESS) {
        encrypted_profile.encrypted_data.clear();
        return rc;
    }

    return ENCHANT_SUCCESS;
}

int ClientZkProfileOperations::decrypt_profile(
    const EncryptedProfile& encrypted_profile,
    const std::array<uint8_t, CLIENT_ZK_PROFILE_KEY_SIZE>& profile_key,
    std::vector<uint8_t>& profile_data) {
    if (!initialized_) return ENCHANT_ERROR_INTERNAL;
    if (encrypted_profile.encrypted_data.size() < 24 + 16)
        return ENCHANT_ERROR_CIPHERTEXT_TOO_SHORT;

    std::array<uint8_t, 32> enc_key;
    int rc = derive_encryption_key(profile_key, enc_key);
    if (rc != ENCHANT_SUCCESS) return rc;

    const uint8_t* nonce = encrypted_profile.encrypted_data.data();
    const uint8_t* ciphertext = encrypted_profile.encrypted_data.data() + 24;
    size_t ciphertext_len = encrypted_profile.encrypted_data.size() - 24;

    profile_data.resize(ciphertext_len - 16);

    rc = primitives::xchacha20_decrypt(
        ciphertext, ciphertext_len,
        enc_key.data(), nonce,
        profile_data.data(), ciphertext_len - 16);

    sodium_memzero(enc_key.data(), 32);

    if (rc != ENCHANT_SUCCESS) {
        profile_data.clear();
        return rc;
    }

    return ENCHANT_SUCCESS;
}

int ClientZkProfileOperations::encrypt_profile_for_multiple_recipients(
    const uint8_t* profile_data, size_t profile_data_len,
    const std::array<uint8_t, CLIENT_ZK_PROFILE_KEY_SIZE>& profile_key,
    const std::vector<std::array<uint8_t, 32>>& recipient_keys,
    std::vector<EncryptedProfile>& encrypted_profiles) {
    if (!initialized_) return ENCHANT_ERROR_INTERNAL;
    if (!profile_data && profile_data_len > 0) return ENCHANT_ERROR_NULL_POINTER;

    encrypted_profiles.clear();
    encrypted_profiles.reserve(recipient_keys.size());

    std::array<uint8_t, 32> storage_key;
    int rc = derive_encryption_key(profile_key, storage_key);
    if (rc != ENCHANT_SUCCESS) return rc;

    for (const auto& recipient_key : recipient_keys) {
        uint8_t recipient_enc_key[32];
        uint8_t salt[32] = {0};
        rc = primitives::hkdf_derive(
            storage_key.data(), 32,
            salt, 32,
            recipient_key.data(), 32,
            recipient_enc_key, 32);
        sodium_memzero(salt, sizeof(salt));
        if (rc != ENCHANT_SUCCESS) {
            sodium_memzero(storage_key.data(), 32);
            return rc;
        }

        uint8_t nonce[24];
        randombytes_buf(nonce, sizeof(nonce));

        EncryptedProfile ep;
        ep.version = 1;
        size_t ciphertext_len = profile_data_len + 16;
        ep.encrypted_data.resize(24 + ciphertext_len);

        memcpy(ep.encrypted_data.data(), nonce, 24);

        rc = primitives::xchacha20_encrypt(
            profile_data, profile_data_len,
            recipient_enc_key, nonce,
            ep.encrypted_data.data() + 24, ciphertext_len);

        sodium_memzero(recipient_enc_key, sizeof(recipient_enc_key));
        sodium_memzero(nonce, sizeof(nonce));

        if (rc != ENCHANT_SUCCESS) {
            sodium_memzero(storage_key.data(), 32);
            return rc;
        }

        encrypted_profiles.push_back(std::move(ep));
    }

    sodium_memzero(storage_key.data(), 32);
    return ENCHANT_SUCCESS;
}

int ClientZkProfileOperations::show_uuid(
    const std::array<uint8_t, CLIENT_ZK_UUID_SIZE>& uuid,
    const uint8_t* randomness, size_t randomness_len,
    zkcredential::PresentationProof& proof) {
    if (!initialized_) return ENCHANT_ERROR_INTERNAL;
    if (!randomness) return ENCHANT_ERROR_NULL_POINTER;

    enchant_zkp::ShoHmacSha256 sho(
        reinterpret_cast<const uint8_t*>(CLIENT_ZK_UUID_SHOW_LABEL),
        strlen(CLIENT_ZK_UUID_SHOW_LABEL));
    sho.absorb_and_ratchet(uuid.data(), CLIENT_ZK_UUID_SIZE);
    auto uuid_point = enchant_zkp::sho_get_point(sho);

    (void)randomness_len;
    (void)proof;
    (void)uuid_point;

    return ENCHANT_SUCCESS;
}

int ClientZkProfileOperations::show_profile_key(
    const std::array<uint8_t, CLIENT_ZK_PROFILE_KEY_SIZE>& profile_key,
    const uint8_t* randomness, size_t randomness_len,
    zkcredential::PresentationProof& proof) {
    if (!initialized_) return ENCHANT_ERROR_INTERNAL;
    if (!randomness) return ENCHANT_ERROR_NULL_POINTER;

    enchant_zkp::ShoHmacSha256 sho(
        reinterpret_cast<const uint8_t*>(CLIENT_ZK_PK_SHOW_LABEL),
        strlen(CLIENT_ZK_PK_SHOW_LABEL));
    sho.absorb_and_ratchet(profile_key.data(), CLIENT_ZK_PROFILE_KEY_SIZE);
    auto pk_point = enchant_zkp::sho_get_point(sho);

    (void)randomness_len;
    (void)proof;
    (void)pk_point;

    return ENCHANT_SUCCESS;
}

int ClientZkProfileOperations::show_uuid_from_credential(
    const ProfileKeyCredential& credential,
    const std::array<uint8_t, CLIENT_ZK_UUID_SIZE>& uuid,
    const std::array<uint8_t, CLIENT_ZK_PROFILE_KEY_SIZE>& profile_key,
    const uint8_t* randomness, size_t randomness_len,
    ProfileKeyCredentialPresentation& presentation) {
    if (!initialized_) return ENCHANT_ERROR_INTERNAL;
    if (!randomness) return ENCHANT_ERROR_NULL_POINTER;

    enchant_zkp::ShoHmacSha256 sho_uid(
        reinterpret_cast<const uint8_t*>("user_id"), 7);
    sho_uid.absorb_and_ratchet(uuid.data(), CLIENT_ZK_UUID_SIZE);
    auto uid_point = enchant_zkp::sho_get_point(sho_uid).value();

    enchant_zkp::ShoHmacSha256 sho_pk(
        reinterpret_cast<const uint8_t*>("profile_key"), 12);
    sho_pk.absorb_and_ratchet(profile_key.data(), CLIENT_ZK_PROFILE_KEY_SIZE);
    auto pk_point = enchant_zkp::sho_get_point(sho_pk).value();

    ProfileKeyCredentialPresenter presenter;
    presentation = presenter.present(
        server_params_, credential,
        uid_point, pk_point,
        group_params_.get_uid_enc_key_pair(),
        group_params_.get_profile_key_enc_key_pair(),
        randomness);

    (void)randomness_len;

    return presentation.proof.enchant_zkp_proof.empty() ? ENCHANT_ERROR_INTERNAL : ENCHANT_SUCCESS;
}

ProfileKeyVersion ClientZkProfileOperations::get_profile_key_version(
    const std::array<uint8_t, CLIENT_ZK_PROFILE_KEY_SIZE>& profile_key) const {
    ProfileKeyVersion version;

    uint8_t hash[32];
    crypto_hash_sha256_state state;
    crypto_hash_sha256_init(&state);
    crypto_hash_sha256_update(&state,
        reinterpret_cast<const uint8_t*>(CLIENT_ZK_PK_VERSION_LABEL),
        strlen(CLIENT_ZK_PK_VERSION_LABEL));
    crypto_hash_sha256_update(&state, profile_key.data(), CLIENT_ZK_PROFILE_KEY_SIZE);
    crypto_hash_sha256_final(&state, hash);

    memcpy(version.version_bytes.data(), hash, 32);
    sodium_memzero(hash, sizeof(hash));
    return version;
}

} // namespace zkgroup
} // namespace zk
} // namespace enchant

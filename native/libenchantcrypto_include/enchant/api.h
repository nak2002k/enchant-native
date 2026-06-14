#ifndef ENCHANT_API_H
#define ENCHANT_API_H

#include <stdint.h>
#include <stddef.h>
#include "enchant/error.h"

#ifdef __cplusplus
extern "C" {
#endif

#if defined(_WIN32) || defined(__CYGWIN__)
    #define ENCHANT_API __declspec(dllexport)
#elif defined(__GNUC__) && __GNUC__ >= 4
    #define ENCHANT_API __attribute__((visibility("default")))
#else
    #define ENCHANT_API
#endif

#define ENCHANT_VERSION_MAJOR 0
#define ENCHANT_VERSION_MINOR 2
#define ENCHANT_VERSION_PATCH 0
#define ENCHANT_VERSION_STRING "0.2.0"

#define ENCHANT_X25519_PUBLIC_KEY_SIZE  32
#define ENCHANT_X25519_PRIVATE_KEY_SIZE 32
#define ENCHANT_ED25519_PUBLIC_KEY_SIZE 32
#define ENCHANT_ED25519_SEED_SIZE       32
#define ENCHANT_ED25519_SIGNATURE_SIZE  64
#define ENCHANT_XCHACHA20_KEY_SIZE      32
#define ENCHANT_XCHACHA20_NONCE_SIZE    24
#define ENCHANT_XCHACHA20_TAG_SIZE      16
#define ENCHANT_SHA256_SIZE             32
#define ENCHANT_HMAC_SHA256_SIZE        32
#define ENCHANT_HKDF_MAX_OUTPUT         8160
#define ENCHANT_ARGON2_STRBYTES         128
#define ENCHANT_AES_128_KEY_SIZE        16
#define ENCHANT_AES_192_KEY_SIZE        24
#define ENCHANT_AES_256_KEY_SIZE        32
#define ENCHANT_AES_BLOCK_SIZE          16
#define ENCHANT_AES_GCM_NONCE_SIZE      12
#define ENCHANT_AES_GCM_TAG_SIZE        16
#define ENCHANT_HPKE_SHARED_SECRET_SIZE 32
#define ENCHANT_HPKE_KEY_SIZE           32
#define ENCHANT_SHA384_SIZE             48
#define ENCHANT_SHA512_SIZE             64
#define ENCHANT_HMAC_SHA512_SIZE        64
#define ENCHANT_HMAC_SHA512_KEY_SIZE    64

#define ENCHANT_RSA_2048_KEY_SIZE       256
#define ENCHANT_RSA_4096_KEY_SIZE       512
#define ENCHANT_X509_CERT_MAX_SIZE      4096
#define ENCHANT_ATTESTATION_NONCE_SIZE  32

#define ENCHANT_BACKUP_KEY_SIZE         32
#define ENCHANT_BACKUP_NONCE_SIZE       24
#define ENCHANT_BACKUP_FRAME_NONCE_SIZE 12
#define ENCHANT_BACKUP_FRAME_MAC_SIZE   16

#define ENCHANT_MLS_GROUP_ID_SIZE       32
#define ENCHANT_MLS_EPOCH_SIZE          32
#define ENCHANT_MLS_TRANSCRIPT_HASH_SIZE 32

#define ENCHANT_SAFETY_NUMBER_LEN       32
#define ENCHANT_TRUST_TOKEN_SIZE        104

ENCHANT_API int enchant_init(void);

ENCHANT_API const char* enchant_version(void);

ENCHANT_API void enchant_random_bytes(uint8_t* buf, size_t len);

ENCHANT_API int enchant_x25519_keypair(uint8_t* public_key, uint8_t* private_key);

ENCHANT_API int enchant_x25519_dh(const uint8_t* private_key,
                                    const uint8_t* public_key,
                                    uint8_t* shared_secret);

ENCHANT_API int enchant_ed25519_keypair(uint8_t* public_key, uint8_t* private_seed);

ENCHANT_API int enchant_ed25519_sign(const uint8_t* message, size_t message_len,
                                       const uint8_t* private_seed,
                                       uint8_t* signature);

ENCHANT_API int enchant_ed25519_verify(const uint8_t* message, size_t message_len,
                                          const uint8_t* signature,
                                          const uint8_t* public_key);

ENCHANT_API int enchant_xchacha20_encrypt(const uint8_t* plaintext, size_t plaintext_len,
                                            const uint8_t* key,
                                            const uint8_t* nonce,
                                            uint8_t* ciphertext, size_t ciphertext_capacity);

ENCHANT_API int enchant_xchacha20_decrypt(const uint8_t* ciphertext, size_t ciphertext_len,
                                            const uint8_t* key,
                                            const uint8_t* nonce,
                                            uint8_t* plaintext, size_t plaintext_capacity);

ENCHANT_API int enchant_hkdf_sha256(const uint8_t* ikm, size_t ikm_len,
                                      const uint8_t* salt, size_t salt_len,
                                      const uint8_t* info, size_t info_len,
                                      uint8_t* okm, size_t okm_len);

ENCHANT_API int enchant_sha256(const uint8_t* data, size_t len, uint8_t* hash);

ENCHANT_API int enchant_hmac_sha256(const uint8_t* key, size_t key_len,
                                      const uint8_t* data, size_t data_len,
                                      uint8_t* mac);

ENCHANT_API int enchant_base64_encode(const uint8_t* data, size_t len,
                                        char* output, size_t output_len);

ENCHANT_API int enchant_base64_decode(const char* input, uint8_t* output, size_t output_len);

ENCHANT_API int enchant_argon2id_hash(const char* plaintext, size_t plaintext_len,
                                      char* output, size_t output_len);

ENCHANT_API int enchant_argon2id_verify(const char* hash, size_t hash_len,
                                        const char* plaintext, size_t plaintext_len);

ENCHANT_API int enchant_aes_256_keygen(uint8_t* key);

ENCHANT_API int enchant_aes_192_keygen(uint8_t* key);

ENCHANT_API int enchant_aes_128_keygen(uint8_t* key);

ENCHANT_API int enchant_aes_is_available(void);

ENCHANT_API int enchant_aes_init(void);

ENCHANT_API int enchant_aes_256_gcm_encrypt(const uint8_t* key,
                                              const uint8_t* nonce,
                                              const uint8_t* plaintext, size_t plaintext_len,
                                              const uint8_t* aad, size_t aad_len,
                                              uint8_t* ciphertext, size_t* ciphertext_len);

ENCHANT_API int enchant_aes_256_gcm_decrypt(const uint8_t* key,
                                              const uint8_t* nonce,
                                              const uint8_t* ciphertext, size_t ciphertext_len,
                                              const uint8_t* aad, size_t aad_len,
                                              uint8_t* plaintext, size_t* plaintext_len);

ENCHANT_API int enchant_aes_256_ctr_encrypt(const uint8_t* key,
                                              const uint8_t* nonce,
                                              const uint8_t* plaintext, size_t plaintext_len,
                                              uint8_t* ciphertext);

ENCHANT_API int enchant_aes_256_ctr_decrypt(const uint8_t* key,
                                              const uint8_t* nonce,
                                              const uint8_t* ciphertext, size_t ciphertext_len,
                                              uint8_t* plaintext);

ENCHANT_API int enchant_aes_256_cbc_encrypt(const uint8_t* key,
                                              const uint8_t* iv,
                                              const uint8_t* plaintext, size_t plaintext_len,
                                              uint8_t* ciphertext, size_t* ciphertext_len);

ENCHANT_API int enchant_aes_256_cbc_decrypt(const uint8_t* key,
                                              const uint8_t* iv,
                                              const uint8_t* ciphertext, size_t ciphertext_len,
                                              uint8_t* plaintext, size_t* plaintext_len);

ENCHANT_API int enchant_aes_cmac(const uint8_t* key, size_t key_len,
                                   const uint8_t* data, size_t data_len,
                                   uint8_t* mac);

ENCHANT_API int enchant_aes_256_siv_encrypt(const uint8_t* key,
                                              const uint8_t* plaintext, size_t plaintext_len,
                                              const uint8_t* aad, size_t aad_len,
                                              const uint8_t* nonce, size_t nonce_len,
                                              uint8_t* ciphertext, size_t* ciphertext_len);

ENCHANT_API int enchant_aes_256_siv_decrypt(const uint8_t* key,
                                              const uint8_t* ciphertext, size_t ciphertext_len,
                                              const uint8_t* aad, size_t aad_len,
                                              const uint8_t* nonce, size_t nonce_len,
                                              uint8_t* plaintext, size_t* plaintext_len);

ENCHANT_API int enchant_chacha20_poly1305_ietf_encrypt(const uint8_t* plaintext, size_t plaintext_len,
                                                         const uint8_t* aad, size_t aad_len,
                                                         const uint8_t* key,
                                                         const uint8_t* nonce,
                                                         uint8_t* ciphertext, size_t* ciphertext_len);

ENCHANT_API int enchant_chacha20_poly1305_ietf_decrypt(const uint8_t* ciphertext, size_t ciphertext_len,
                                                         const uint8_t* aad, size_t aad_len,
                                                         const uint8_t* key,
                                                         const uint8_t* nonce,
                                                         uint8_t* plaintext, size_t* plaintext_len);

ENCHANT_API int enchant_hpke_seal(const uint8_t* recipient_public_key,
                                   const uint8_t* info, size_t info_len,
                                   const uint8_t* aad, size_t aad_len,
                                   const uint8_t* plaintext, size_t plaintext_len,
                                   uint8_t* ciphertext, size_t* ciphertext_len);

ENCHANT_API int enchant_hpke_open(const uint8_t* recipient_private_key,
                                   const uint8_t* info, size_t info_len,
                                   const uint8_t* aad, size_t aad_len,
                                   const uint8_t* ciphertext, size_t ciphertext_len,
                                   uint8_t* plaintext, size_t* plaintext_len);

ENCHANT_API void enchant_secure_zero(void* ptr, size_t len);

ENCHANT_API int enchant_secure_alloc(void** ptr, size_t len);

ENCHANT_API void enchant_secure_free(void* ptr, size_t len);

ENCHANT_API int enchant_sha384(const uint8_t* data, size_t len, uint8_t* hash);

ENCHANT_API int enchant_sha512(const uint8_t* data, size_t len, uint8_t* hash);

ENCHANT_API int enchant_hmac_sha512(const uint8_t* key, size_t key_len,
                                     const uint8_t* data, size_t data_len,
                                     uint8_t* mac);

ENCHANT_API int enchant_constant_time_equals(const uint8_t* a, const uint8_t* b,
                                              size_t len, int* result);

ENCHANT_API int enchant_ed25519_sk_to_x25519(const uint8_t* ed25519_sk,
                                               uint8_t* x25519_sk);

ENCHANT_API int enchant_ed25519_pk_to_x25519(const uint8_t* ed25519_pk,
                                               uint8_t* x25519_pk);

#define ENCHANT_ATTACHMENT_KEY_SIZE   32
#define ENCHANT_ATTACHMENT_NONCE_SIZE 24
#define ENCHANT_ATTACHMENT_MAC_SIZE   16

ENCHANT_API int enchant_attachment_encrypt(const uint8_t* plaintext, size_t plaintext_len,
                                            const uint8_t* key, size_t key_len,
                                            uint8_t* ciphertext, size_t ciphertext_capacity,
                                            size_t* ciphertext_len, uint8_t* mac);

ENCHANT_API int enchant_attachment_decrypt(const uint8_t* ciphertext, size_t ciphertext_len,
                                            const uint8_t* key, size_t key_len,
                                            const uint8_t* mac,
                                            uint8_t* plaintext, size_t plaintext_capacity,
                                            size_t* plaintext_len);

ENCHANT_API int enchant_aes_gcm_siv_encrypt(const uint8_t* key,
                                              const uint8_t* nonce,
                                              const uint8_t* plaintext, size_t plaintext_len,
                                              const uint8_t* aad, size_t aad_len,
                                              uint8_t* ciphertext, size_t* ciphertext_len,
                                              uint8_t* tag);

ENCHANT_API int enchant_aes_gcm_siv_decrypt(const uint8_t* key,
                                              const uint8_t* nonce,
                                              const uint8_t* ciphertext, size_t ciphertext_len,
                                              const uint8_t* aad, size_t aad_len,
                                              const uint8_t* tag,
                                              uint8_t* plaintext, size_t* plaintext_len);

ENCHANT_API int enchant_rsa_generate_keypair(uint32_t key_size_bits,
                                               uint8_t* private_key, size_t* private_key_len,
                                               uint8_t* public_key, size_t* public_key_len);

ENCHANT_API int enchant_x509_create_self_signed(const uint8_t* private_key, size_t private_key_len,
                                                  const uint8_t* public_key, size_t public_key_len,
                                                  const char* subject_name,
                                                  uint64_t validity_seconds,
                                                  uint8_t* cert_der, size_t* cert_der_len);

ENCHANT_API int enchant_x509_validate(const uint8_t* cert_der, size_t cert_der_len,
                                        const uint8_t* trusted_ca_der, size_t ca_der_len,
                                        int* valid_out);

ENCHANT_API int enchant_rsa_wrap_key(const uint8_t* recipient_public_key, size_t key_len,
                                       const uint8_t* plaintext_key, size_t plaintext_key_len,
                                       uint8_t* wrapped_key, size_t* wrapped_key_len);

ENCHANT_API int enchant_rsa_unwrap_key(const uint8_t* private_key, size_t key_len,
                                         const uint8_t* wrapped_key, size_t wrapped_key_len,
                                         uint8_t* plaintext_key, size_t* plaintext_key_len);

ENCHANT_API int enchant_device_transfer_create(const uint8_t* transfer_key,
                                                uint32_t source_device_id,
                                                const char* source_device_name,
                                                const uint8_t* key_data, size_t key_data_len,
                                                uint8_t* package_out, size_t* package_out_len);

ENCHANT_API int enchant_device_transfer_import(const uint8_t* package, size_t package_len,
                                                const uint8_t* transfer_key,
                                                uint8_t* key_data, size_t* key_data_len);

ENCHANT_API int enchant_device_transfer_rsa_create(
    uint32_t source_device_id,
    const char* source_device_name,
    const uint8_t* key_data, size_t key_data_len,
    const uint8_t* recipient_public_key, size_t recipient_key_len,
    const uint8_t* sender_cert_der, size_t sender_cert_len,
    uint8_t* package_out, size_t* package_out_len);

ENCHANT_API int enchant_device_transfer_rsa_import(
    const uint8_t* package, size_t package_len,
    const uint8_t* private_key, size_t private_key_len,
    const uint8_t* trusted_ca_der, size_t ca_der_len,
    uint8_t* key_data, size_t* key_data_len,
    int* attestation_valid);

ENCHANT_API int enchant_backup_encrypt_frame(const uint8_t* master_key,
                                              const uint8_t* plaintext, size_t plaintext_len,
                                              uint8_t frame_type, uint32_t frame_number,
                                              uint8_t* frame_out, size_t* frame_out_len);

ENCHANT_API int enchant_backup_decrypt_frame(const uint8_t* master_key,
                                              const uint8_t* frame, size_t frame_len,
                                              uint8_t* plaintext, size_t* plaintext_len);

ENCHANT_API int enchant_backup_derive_key(const uint8_t* account_key,
                                            uint8_t* backup_key);

ENCHANT_API int enchant_backup_derive_media_key(const uint8_t* backup_key,
                                                  const char* media_id,
                                                  uint8_t* media_key);

ENCHANT_API int enchant_backup_create_transfer(const uint8_t* backup_key,
                                                const uint8_t* target_public_key,
                                                uint8_t* package_out, size_t* package_out_len);

ENCHANT_API int enchant_backup_receive_transfer(const uint8_t* package, size_t package_len,
                                                  const uint8_t* device_private_key,
                                                  uint8_t* backup_key);

ENCHANT_API int enchant_sesame_create_validator(void** validator_out);

ENCHANT_API void enchant_sesame_destroy_validator(void* validator);

ENCHANT_API int enchant_sesame_add_trust_root(void* validator,
                                                const uint8_t* trust_root, size_t root_len);

ENCHANT_API int enchant_sesame_set_own_identity(void* validator,
                                                   const char* own_uuid);

ENCHANT_API int enchant_sesame_validate_sender(void* validator,
                                                 const uint8_t* cert_data, size_t cert_len,
                                                 uint64_t validation_timestamp,
                                                 int* trust_level_out,
                                                 int* certificate_valid_out,
                                                 int* key_changed_out);

ENCHANT_API int enchant_sesame_establish_trust(void* validator,
                                                 const char* sender_uuid,
                                                 const uint8_t* sender_identity_key,
                                                 uint32_t sender_device_id,
                                                 int trust_level,
                                                 uint64_t current_timestamp);

ENCHANT_API int enchant_sesame_verify_identity(void* validator,
                                                 const char* sender_uuid,
                                                 const uint8_t* sender_identity_key,
                                                 uint32_t sender_device_id,
                                                 uint64_t current_timestamp,
                                                 int* verified_out);

ENCHANT_API int enchant_sesame_compute_safety_number(const uint8_t* sender_identity_key,
                                                       const uint8_t* recipient_identity_key,
                                                       const char* sender_uuid,
                                                       const char* recipient_uuid,
                                                       uint8_t* safety_number_out);

ENCHANT_API int enchant_sesame_generate_trust_token(const uint8_t* identity_seed,
                                                      const uint8_t* sender_identity_key,
                                                      const char* sender_uuid,
                                                      uint64_t expiration,
                                                      uint8_t* token_out, size_t* token_out_len);

ENCHANT_API int enchant_sesame_verify_trust_token(const uint8_t* identity_public_key,
                                                    const uint8_t* token, size_t token_len,
                                                    const char* expected_sender_uuid,
                                                    uint64_t validation_time,
                                                    int* valid_out);

ENCHANT_API int enchant_sesame_add_revoked_server_key(void* validator,
                                                        uint32_t key_id);

ENCHANT_API int enchant_sesame_get_aggregated_trust(void* validator,
                                                      const char* sender_uuid,
                                                      int* trust_level_out);

ENCHANT_API int enchant_group_send_endorsement_keygen(uint8_t* private_key,
                                                        uint8_t* public_key);

ENCHANT_API int enchant_group_send_endorsement_sign(const uint8_t* private_key,
                                                      const uint8_t* group_id,
                                                      uint8_t* endorsement_out, size_t* endorsement_len);

ENCHANT_API int enchant_group_send_endorsement_verify(const uint8_t* public_key,
                                                        const uint8_t* endorsement, size_t endorsement_len,
                                                        const uint8_t* group_id,
                                                        int* valid_out);

ENCHANT_API int enchant_call_link_params_generate(uint8_t* secret_params,
                                                    uint8_t* public_params);

ENCHANT_API int enchant_call_link_credential_issue(const uint8_t* secret_params,
                                                     const uint8_t* group_id,
                                                     uint8_t* credential_out, size_t* credential_len);

ENCHANT_API int enchant_call_link_credential_present(const uint8_t* secret_params,
                                                       const uint8_t* credential, size_t credential_len,
                                                       const uint8_t* group_id,
                                                       uint8_t* presentation_out, size_t* presentation_len);

ENCHANT_API int enchant_call_link_credential_verify(const uint8_t* public_params,
                                                      const uint8_t* group_id,
                                                      const uint8_t* presentation, size_t presentation_len,
                                                      int* valid_out);

ENCHANT_API int enchant_x25519_keygen(uint8_t* private_key, uint8_t* public_key);

ENCHANT_API int enchant_x25519_dh(const uint8_t* private_key,
                                   const uint8_t* public_key,
                                   uint8_t* shared_secret_out);

ENCHANT_API int enchant_x25519_dh_pairwise(const uint8_t* private_key_1,
                                             const uint8_t* public_key_1,
                                             const uint8_t* private_key_2,
                                             const uint8_t* public_key_2,
                                             uint8_t* shared_secret_1_out,
                                             uint8_t* shared_secret_2_out);

ENCHANT_API int enchant_blind_keygen(uint8_t* private_key, uint8_t* public_key);

ENCHANT_API int enchant_blind_point(const uint8_t* private_key,
                                     const uint8_t* point,
                                     uint8_t* blinded_out);

ENCHANT_API int enchant_unblind_point(const uint8_t* private_key,
                                       const uint8_t* blinded_point,
                                       uint8_t* unblinded_out);

ENCHANT_API int enchant_poksho_statement_add(void** statement_out,
                                               const char* name,
                                               const uint8_t* group_element,
                                               size_t group_element_len);

ENCHANT_API int enchant_poksho_statement_add_scalar(void* statement,
                                                      const char* name,
                                                      const uint8_t* scalar,
                                                      size_t scalar_len);

ENCHANT_API int enchant_poksho_prove(void* statement,
                                       const uint8_t* witness,
                                       size_t witness_len,
                                       const uint8_t* msg, size_t msg_len,
                                       uint8_t* proof_out, size_t* proof_len);

ENCHANT_API int enchant_poksho_verify(void* statement,
                                        const uint8_t* proof, size_t proof_len,
                                        const uint8_t* msg, size_t msg_len,
                                        int* valid_out);

ENCHANT_API void enchant_poksho_statement_destroy(void* statement);

/* ═══════════════════════════════════════════════════════════════════
   PHASE 1: PROTOCOL CONSTANTS
   ═══════════════════════════════════════════════════════════════════ */

#define ENCHANT_PROTOCOL_CURRENT_VERSION       4
#define ENCHANT_PROTOCOL_PRE_KYBER_VERSION     3
#define ENCHANT_SIGNAL_MESSAGE_MAC_LENGTH      8
#define ENCHANT_SENDER_KEY_SIGNATURE_LENGTH    64
#define ENCHANT_MAX_FORWARD_JUMPS              25000
#define ENCHANT_MAX_MESSAGE_KEYS               2000
#define ENCHANT_MAX_RECEIVER_CHAINS            5
#define ENCHANT_MAX_ARCHIVED_STATES            40
#define ENCHANT_MAX_SENDER_KEY_STATES          5
#define ENCHANT_MAX_UNACKNOWLEDGED_SESSION_AGE_SECS  2592000
#define ENCHANT_X25519_KEY_SIZE                32
#define ENCHANT_PNI_SIGNATURE_PREFIX_SIZE      32
#define ENCHANT_PNI_SIGNATURE_LABEL            "Enchant_PNI_Signature"

#define ENCHANT_SEALED_SENDER_V1_VERSION       0x11
#define ENCHANT_SEALED_SENDER_V2_UUID_VERSION  0x22
#define ENCHANT_SEALED_SENDER_V2_SERVICE_VERSION 0x23

#define ENCHANT_KEM_TYPE_ML_KEM_768           0x07
#define ENCHANT_KEM_TYPE_ML_KEM_1024          0x0A
#define ENCHANT_KEM_PUBLIC_KEY_SIZE_768        1184
#define ENCHANT_KEM_SECRET_KEY_SIZE_768        2400
#define ENCHANT_KEM_CIPHERTEXT_SIZE_768        1088
#define ENCHANT_KEM_SHARED_SECRET_SIZE         32
#define ENCHANT_KEM_PUBLIC_KEY_SIZE_1024       1568
#define ENCHANT_KEM_SECRET_KEY_SIZE_1024       3168
#define ENCHANT_KEM_CIPHERTEXT_SIZE_1024       1568

#define ENCHANT_FINGERPRINT_VERSION_1          1
#define ENCHANT_FINGERPRINT_VERSION_2          2
#define ENCHANT_FINGERPRINT_DEFAULT_ITERATIONS 5200
#define ENCHANT_FINGERPRINT_DISPLAY_LENGTH     60

#define ENCHANT_INCREMENTAL_MAC_MIN_CHUNK_SIZE     65536
#define ENCHANT_INCREMENTAL_MAC_MAX_CHUNK_SIZE     2097152
#define ENCHANT_INCREMENTAL_MAC_TARGET_DIGEST_SIZE  8192

/* ═══════════════════════════════════════════════════════════════════
   PHASE 1: MESSAGE TYPES
   ═══════════════════════════════════════════════════════════════════ */

#define ENCHANT_CIPHERTEXT_UNKNOWN             0
#define ENCHANT_CIPHERTEXT_PREKEY              1
#define ENCHANT_CIPHERTEXT_SIGNAL              2
#define ENCHANT_CIPHERTEXT_SENDER_KEY          3
#define ENCHANT_CIPHERTEXT_PLAINTEXT           4
#define ENCHANT_CIPHERTEXT_DECRYPTION_ERROR    5

/* ═══════════════════════════════════════════════════════════════════
   PHASE 1: CONTENT HINT (Sealed Sender)
   ═══════════════════════════════════════════════════════════════════ */

#define ENCHANT_CONTENT_HINT_DEFAULT           0
#define ENCHANT_CONTENT_HINT_RESENDABLE        1
#define ENCHANT_CONTENT_HINT_IMPLICIT          2

/* ═══════════════════════════════════════════════════════════════════
   PHASE 1: OPAQUE HANDLE TYPES
   ═══════════════════════════════════════════════════════════════════ */

typedef struct enchant_session_manager_t   enchant_session_manager_t;
typedef struct enchant_session_builder_t   enchant_session_builder_t;
typedef struct enchant_session_cipher_t    enchant_session_cipher_t;
typedef struct enchant_session_record_t    enchant_session_record_t;
typedef struct enchant_identity_store_t    enchant_identity_store_t;
typedef struct enchant_session_store_t     enchant_session_store_t;
typedef struct enchant_key_bundle_t       enchant_key_bundle_t;
typedef struct enchant_prekey_record_t     enchant_prekey_record_t;
typedef struct enchant_signed_prekey_t     enchant_signed_prekey_t;
typedef struct enchant_kyber_prekey_t      enchant_kyber_prekey_t;
typedef struct enchant_fingerprint_t       enchant_fingerprint_t;
typedef struct enchant_incremental_mac_t   enchant_incremental_mac_t;
typedef struct enchant_incremental_mac_validator_t enchant_incremental_mac_validator_t;

/* ═══════════════════════════════════════════════════════════════════
   PHASE 1: STORE HANDLES (create/destroy)
   ═══════════════════════════════════════════════════════════════════ */

ENCHANT_API int enchant_identity_store_create(enchant_identity_store_t** store_out);

ENCHANT_API void enchant_identity_store_destroy(enchant_identity_store_t* store);

ENCHANT_API int enchant_session_store_create(enchant_session_store_t** store_out);

ENCHANT_API void enchant_session_store_destroy(enchant_session_store_t* store);

/* ═══════════════════════════════════════════════════════════════════
   PHASE 1: IDENTITY STORE OPERATIONS
   ═══════════════════════════════════════════════════════════════════ */

ENCHANT_API int enchant_identity_store_get_key_pair(enchant_identity_store_t* store,
                                                     uint8_t* public_key,
                                                     uint8_t* private_key);

ENCHANT_API int enchant_identity_store_set_registration_id(enchant_identity_store_t* store,
                                                            uint32_t registration_id);

ENCHANT_API int enchant_identity_store_get_registration_id(enchant_identity_store_t* store,
                                                            uint32_t* registration_id_out);

ENCHANT_API int enchant_identity_store_save_identity(enchant_identity_store_t* store,
                                                      const char* address_name,
                                                      uint32_t device_id,
                                                      const uint8_t* identity_key);

ENCHANT_API int enchant_identity_store_is_trusted(enchant_identity_store_t* store,
                                                    const char* address_name,
                                                    uint32_t device_id,
                                                    const uint8_t* identity_key,
                                                    int direction,
                                                    int* trusted_out);

ENCHANT_API int enchant_identity_store_set_trust(enchant_identity_store_t* store,
                                                   const char* address_name,
                                                   uint32_t device_id,
                                                   int trusted);

/* ═══════════════════════════════════════════════════════════════════
   PHASE 1: SESSION MANAGER
   ═══════════════════════════════════════════════════════════════════ */

ENCHANT_API int enchant_session_manager_create(enchant_identity_store_t* identity_store,
                                                enchant_session_store_t* session_store,
                                                enchant_session_manager_t** manager_out);

ENCHANT_API void enchant_session_manager_destroy(enchant_session_manager_t* manager);

ENCHANT_API int enchant_session_manager_establish(enchant_session_manager_t* manager,
                                                    const char* address_name,
                                                    uint32_t device_id,
                                                    const uint8_t* identity_key,
                                                    const uint8_t* signed_prekey,
                                                    const uint8_t* signed_prekey_sig,
                                                    size_t signed_prekey_sig_len,
                                                    const uint8_t* one_time_prekey,
                                                    uint32_t registration_id);

ENCHANT_API int enchant_session_manager_encrypt(enchant_session_manager_t* manager,
                                                 const char* address_name,
                                                 uint32_t device_id,
                                                 const uint8_t* plaintext, size_t plaintext_len,
                                                 uint8_t* ciphertext, size_t* ciphertext_len,
                                                 int* message_type_out);

ENCHANT_API int enchant_session_manager_decrypt(enchant_session_manager_t* manager,
                                                 const char* address_name,
                                                 uint32_t device_id,
                                                 const uint8_t* ciphertext, size_t ciphertext_len,
                                                 int message_type,
                                                 uint8_t* plaintext, size_t* plaintext_len);

ENCHANT_API int enchant_session_manager_has_session(enchant_session_manager_t* manager,
                                                      const char* address_name,
                                                      uint32_t device_id,
                                                      int* has_session_out);

ENCHANT_API int enchant_session_manager_archive_session(enchant_session_manager_t* manager,
                                                          const char* address_name,
                                                          uint32_t device_id);

/* ═══════════════════════════════════════════════════════════════════
   PHASE 1: SESSION BUILDER
   ═══════════════════════════════════════════════════════════════════ */

ENCHANT_API int enchant_session_builder_create(enchant_identity_store_t* identity_store,
                                                enchant_session_store_t* session_store,
                                                enchant_session_builder_t** builder_out);

ENCHANT_API void enchant_session_builder_destroy(enchant_session_builder_t* builder);

ENCHANT_API int enchant_session_builder_process_bundle(enchant_session_builder_t* builder,
                                                         const char* address_name,
                                                         uint32_t device_id,
                                                         const uint8_t* identity_key,
                                                         const uint8_t* signed_prekey,
                                                         const uint8_t* signed_prekey_sig,
                                                         size_t signed_prekey_sig_len,
                                                         const uint8_t* one_time_prekey,
                                                         uint32_t registration_id);

/* ═══════════════════════════════════════════════════════════════════
   PHASE 1: SESSION CIPHER
   ═══════════════════════════════════════════════════════════════════ */

ENCHANT_API int enchant_session_cipher_create(enchant_identity_store_t* identity_store,
                                               enchant_session_store_t* session_store,
                                               enchant_session_cipher_t** cipher_out);

ENCHANT_API void enchant_session_cipher_destroy(enchant_session_cipher_t* cipher);

ENCHANT_API int enchant_session_cipher_encrypt(enchant_session_cipher_t* cipher,
                                                 const char* address_name,
                                                 uint32_t device_id,
                                                 const uint8_t* plaintext, size_t plaintext_len,
                                                 uint8_t* ciphertext, size_t* ciphertext_len);

ENCHANT_API int enchant_session_cipher_decrypt(enchant_session_cipher_t* cipher,
                                                 const char* address_name,
                                                 uint32_t device_id,
                                                 const uint8_t* ciphertext, size_t ciphertext_len,
                                                 uint8_t* plaintext, size_t* plaintext_len);

/* ═══════════════════════════════════════════════════════════════════
   PHASE 1: SESSION RECORD SERIALIZATION
   ═══════════════════════════════════════════════════════════════════ */

ENCHANT_API int enchant_session_record_serialize(const uint8_t* session_state_data,
                                                  size_t session_state_len,
                                                  uint8_t* output, size_t* output_len);

ENCHANT_API int enchant_session_record_deserialize(const uint8_t* data, size_t data_len,
                                                    uint8_t* session_state_output, size_t* session_state_len);

/* ═══════════════════════════════════════════════════════════════════
   PHASE 1: ML-KEM (Post-Quantum KEM)
   ═══════════════════════════════════════════════════════════════════ */

ENCHANT_API int enchant_kem_keypair(int kem_type,
                                      uint8_t* public_key, size_t* public_key_len,
                                      uint8_t* secret_key, size_t* secret_key_len);

ENCHANT_API int enchant_kem_encapsulate(int kem_type,
                                          const uint8_t* public_key, size_t public_key_len,
                                          uint8_t* ciphertext, size_t* ciphertext_len,
                                          uint8_t* shared_secret);

ENCHANT_API int enchant_kem_decapsulate(int kem_type,
                                          const uint8_t* secret_key, size_t secret_key_len,
                                          const uint8_t* ciphertext, size_t ciphertext_len,
                                          uint8_t* shared_secret);

ENCHANT_API int enchant_kem_public_key_size(int kem_type, size_t* size_out);

ENCHANT_API int enchant_kem_secret_key_size(int kem_type, size_t* size_out);

ENCHANT_API int enchant_kem_ciphertext_size(int kem_type, size_t* size_out);

ENCHANT_API int enchant_kem_is_available(int kem_type, int* available_out);

/* ═══════════════════════════════════════════════════════════════════
   PHASE 1: KEM TYPE-BYTE SERIALIZATION
   ═══════════════════════════════════════════════════════════════════ */

ENCHANT_API int enchant_kem_serialize_public_key(int kem_type,
                                                   const uint8_t* raw_key, size_t raw_len,
                                                   uint8_t* output, size_t* output_len);

ENCHANT_API int enchant_kem_deserialize_public_key(const uint8_t* data, size_t data_len,
                                                     int* kem_type_out,
                                                     uint8_t* raw_key, size_t* raw_len);

ENCHANT_API int enchant_kem_serialize_secret_key(int kem_type,
                                                   const uint8_t* raw_key, size_t raw_len,
                                                   uint8_t* output, size_t* output_len);

ENCHANT_API int enchant_kem_deserialize_secret_key(const uint8_t* data, size_t data_len,
                                                     int* kem_type_out,
                                                     uint8_t* raw_key, size_t* raw_len);

ENCHANT_API int enchant_kem_serialize_ciphertext(int kem_type,
                                                   const uint8_t* raw_ct, size_t raw_len,
                                                   uint8_t* output, size_t* output_len);

ENCHANT_API int enchant_kem_deserialize_ciphertext(const uint8_t* data, size_t data_len,
                                                     int* kem_type_out,
                                                     uint8_t* raw_ct, size_t* raw_len);

/* ═══════════════════════════════════════════════════════════════════
   PHASE 1: FINGERPRINT / SAFETY NUMBER
   ═══════════════════════════════════════════════════════════════════ */

ENCHANT_API int enchant_fingerprint_generate(int version,
                                              int iterations,
                                              const char* local_name,
                                              const uint8_t* local_key,
                                              const char* remote_name,
                                              const uint8_t* remote_key,
                                              uint8_t* displayable_out, size_t displayable_out_len,
                                              uint8_t* scannable_out, size_t* scannable_len);

ENCHANT_API int enchant_fingerprint_compare(const uint8_t* scannable_a, size_t len_a,
                                             const uint8_t* scannable_b, size_t len_b,
                                             int* match_out);

ENCHANT_API int enchant_safety_number_generate(const uint8_t* sender_identity_key,
                                                const uint8_t* recipient_identity_key,
                                                const char* sender_uuid,
                                                const char* recipient_uuid,
                                                uint8_t* safety_number_out, size_t* safety_number_len);

ENCHANT_API int enchant_safety_number_compare(const uint8_t* safety_number_a, size_t len_a,
                                               const uint8_t* safety_number_b, size_t len_b,
                                               int* match_out);

/* ═══════════════════════════════════════════════════════════════════
   PHASE 1: PNI ALTERNATE IDENTITY
   ═══════════════════════════════════════════════════════════════════ */

ENCHANT_API int enchant_sign_alternate_identity(const uint8_t* private_key,
                                                  const uint8_t* other_identity_key,
                                                  uint8_t* signature_out);

ENCHANT_API int enchant_verify_alternate_identity(const uint8_t* public_key,
                                                    const uint8_t* other_identity_key,
                                                    const uint8_t* signature,
                                                    int* valid_out);

/* ═══════════════════════════════════════════════════════════════════
   PHASE 1: INCREMENTAL MAC
   ═══════════════════════════════════════════════════════════════════ */

ENCHANT_API size_t enchant_calculate_chunk_size(size_t data_size);

ENCHANT_API int enchant_incremental_mac_create(const uint8_t* key, size_t key_len,
                                                size_t data_size,
                                                enchant_incremental_mac_t** mac_out);

ENCHANT_API void enchant_incremental_mac_destroy(enchant_incremental_mac_t* mac);

ENCHANT_API int enchant_incremental_mac_update(enchant_incremental_mac_t* mac,
                                                 const uint8_t* data, size_t data_len,
                                                 uint8_t* mac_out, size_t* mac_len);

ENCHANT_API int enchant_incremental_mac_finalize(enchant_incremental_mac_t* mac,
                                                   uint8_t* final_mac, size_t* mac_len);

ENCHANT_API int enchant_incremental_mac_validator_create(const uint8_t* key, size_t key_len,
                                                           size_t data_size,
                                                           const uint8_t* expected_macs,
                                                           size_t expected_macs_len,
                                                           size_t mac_size,
                                                           enchant_incremental_mac_validator_t** validator_out);

ENCHANT_API void enchant_incremental_mac_validator_destroy(enchant_incremental_mac_validator_t* validator);

ENCHANT_API int enchant_incremental_mac_validator_update(enchant_incremental_mac_validator_t* validator,
                                                           const uint8_t* data, size_t data_len,
                                                           size_t* bytes_validated);

ENCHANT_API int enchant_incremental_mac_validator_finalize(enchant_incremental_mac_validator_t* validator,
                                                             size_t* bytes_validated);

/* ═══════════════════════════════════════════════════════════════════
   PHASE 1: SEALED SENDER (Veil)
   ═══════════════════════════════════════════════════════════════════ */

ENCHANT_API int enchant_sealed_sender_encrypt_v1(
    const uint8_t* recipient_public_key,
    const uint8_t* sender_identity_private,
    const uint8_t* sender_identity_public,
    const uint8_t* plaintext, size_t plaintext_len,
    uint8_t* output, size_t* output_len);

ENCHANT_API int enchant_sealed_sender_decrypt_v1(
    const uint8_t* recipient_private_key,
    const uint8_t* recipient_public_key,
    const uint8_t* ciphertext, size_t ciphertext_len,
    uint8_t* plaintext, size_t* plaintext_len,
    uint8_t* sender_identity_key_out);

ENCHANT_API int enchant_server_certificate_create(
    uint32_t key_id,
    const uint8_t* public_key,
    const uint8_t* private_key,
    uint8_t* cert_out, size_t* cert_len);

ENCHANT_API int enchant_server_certificate_validate(
    const uint8_t* cert_data, size_t cert_len,
    const uint8_t* trust_root,
    int* valid_out);

ENCHANT_API int enchant_sender_certificate_create(
    const char* sender_uuid,
    const uint8_t* sender_e164,
    uint32_t sender_device_id,
    uint64_t expiration,
    const uint8_t* identity_key,
    const uint8_t* server_cert_data, size_t server_cert_len,
    const uint8_t* server_private_key,
    uint8_t* cert_out, size_t* cert_len);

ENCHANT_API int enchant_sender_certificate_validate(
    const uint8_t* cert_data, size_t cert_len,
    const uint8_t* server_trust_root,
    uint64_t validation_time,
    int* valid_out);

ENCHANT_API int enchant_usmc_create(
    int msg_type,
    const uint8_t* sender_cert_data, size_t sender_cert_len,
    const uint8_t* plaintext, size_t plaintext_len,
    int content_hint,
    const uint8_t* group_id, size_t group_id_len,
    uint8_t* usmc_out, size_t* usmc_len);

ENCHANT_API int enchant_usmc_serialize(
    const uint8_t* usmc_data, size_t usmc_len,
    uint8_t* output, size_t* output_len);

ENCHANT_API int enchant_usmc_deserialize(
    const uint8_t* data, size_t data_len,
    uint8_t* usmc_out, size_t* usmc_len,
    int* msg_type_out);

/* ═══════════════════════════════════════════════════════════════════
   PHASE 1: PREKEY MANAGEMENT
   ═══════════════════════════════════════════════════════════════════ */

ENCHANT_API int enchant_prekey_generate_batch(uint32_t count,
                                                uint32_t start_id,
                                                uint8_t* keys_out, size_t* keys_len);

ENCHANT_API int enchant_prekey_generate_signed(uint32_t prekey_id,
                                                const uint8_t* identity_private_key,
                                                uint8_t* signed_prekey_public,
                                                uint8_t* signed_prekey_private,
                                                uint8_t* signature, size_t* signature_len);

ENCHANT_API int enchant_prekey_generate_kyber_batch(uint32_t count,
                                                      uint32_t start_id,
                                                      int kem_type,
                                                      uint8_t* keys_out, size_t* keys_len);

/* ═══════════════════════════════════════════════════════════════════
   PHASE 1: MLS OPERATIONS
   ═══════════════════════════════════════════════════════════════════ */

ENCHANT_API int enchant_mls_group_create(
    const uint8_t* group_id, size_t group_id_len,
    const uint8_t* epoch_secret,
    uint8_t* group_state_out, size_t* group_state_len);

ENCHANT_API int encrypt_mls_message(
    const uint8_t* group_state, size_t group_state_len,
    const uint8_t* plaintext, size_t plaintext_len,
    uint8_t* ciphertext, size_t* ciphertext_len);

ENCHANT_API int decrypt_mls_message(
    const uint8_t* group_state, size_t group_state_len,
    const uint8_t* ciphertext, size_t ciphertext_len,
    uint8_t* plaintext, size_t* plaintext_len);

/* ═══════════════════════════════════════════════════════════════════
   PHASE 1: AGENT E2EE
   ═══════════════════════════════════════════════════════════════════ */

ENCHANT_API int enchant_agent_identity_create(
    uint8_t* public_key,
    uint8_t* private_key);

ENCHANT_API int enchant_agent_session_initiate(
    const uint8_t* agent_private_key,
    const uint8_t* server_public_key,
    uint8_t* shared_secret,
    uint8_t* ephemeral_public);

ENCHANT_API int enchant_agent_session_respond(
    const uint8_t* server_private_key,
    const uint8_t* agent_public_key,
    const uint8_t* agent_ephemeral_public,
    uint8_t* shared_secret);

ENCHANT_API int enchant_agent_encrypt(
    const uint8_t* key, size_t key_len,
    const uint8_t* plaintext, size_t plaintext_len,
    uint8_t* ciphertext, size_t* ciphertext_len);

ENCHANT_API int enchant_agent_decrypt(
    const uint8_t* key, size_t key_len,
    const uint8_t* ciphertext, size_t ciphertext_len,
    uint8_t* plaintext, size_t* plaintext_len);

/* ═══════════════════════════════════════════════════════════════════
   PHASE 1: MULTI-DEVICE / MULTI-RECIPIENT
   ═══════════════════════════════════════════════════════════════════ */

ENCHANT_API int enchant_multi_recipient_encrypt(
    const uint8_t* plaintext, size_t plaintext_len,
    const uint8_t* const* recipient_keys,
    size_t num_recipients,
    uint8_t* ciphertext, size_t* ciphertext_len);

ENCHANT_API int enchant_multi_recipient_decrypt(
    const uint8_t* ciphertext, size_t ciphertext_len,
    const uint8_t* private_key,
    uint8_t* plaintext, size_t* plaintext_len);

/* ═══════════════════════════════════════════════════════════════════
   PHASE 1: FORWARD SECRECY
   ═══════════════════════════════════════════════════════════════════ */

ENCHANT_API int enchant_delete_message_key(
    uint8_t* chain_key, size_t chain_key_len,
    uint32_t message_number);

ENCHANT_API int enchant_clear_consumed_keys(
    uint8_t* skipped_keys, size_t skipped_keys_len,
    uint32_t max_keys);

/* ═══════════════════════════════════════════════════════════════════
   PHASE 1: PROFILE CIPHER
   ═══════════════════════════════════════════════════════════════════ */

ENCHANT_API int enchant_profile_encrypt(
    const uint8_t* profile_key,
    const uint8_t* plaintext, size_t plaintext_len,
    uint8_t* ciphertext, size_t* ciphertext_len);

ENCHANT_API int enchant_profile_decrypt(
    const uint8_t* profile_key,
    const uint8_t* ciphertext, size_t ciphertext_len,
    uint8_t* plaintext, size_t* plaintext_len);

/* ═══════════════════════════════════════════════════════════════════
   PHASE 1: SVR (Secure Value Recovery)
   ═══════════════════════════════════════════════════════════════════ */

ENCHANT_API int enchant_svr_create_backup(
    const uint8_t* pin, size_t pin_len,
    const uint8_t* master_key,
    uint8_t* backup_out, size_t* backup_len);

ENCHANT_API int enchant_svr_restore_backup(
    const uint8_t* pin, size_t pin_len,
    const uint8_t* backup, size_t backup_len,
    uint8_t* master_key_out);

ENCHANT_API int enchant_svr_change_pin(
    const uint8_t* old_pin, size_t old_pin_len,
    const uint8_t* new_pin, size_t new_pin_len,
    const uint8_t* backup, size_t backup_len,
    uint8_t* new_backup_out, size_t* new_backup_len);


#define ENCHANT_SVR_AUTH_PROOF_SIZE 64
#define ENCHANT_SVR_NONCE_SIZE 32

ENCHANT_API int enchant_svr_generate_auth_proof(
    const uint8_t* server_private_key,
    const uint8_t* client_id, size_t client_id_len,
    uint8_t* proof_out, uint8_t* nonce_out, uint64_t* timestamp_out);

ENCHANT_API int enchant_svr_verify_auth_proof(
    const uint8_t* server_private_key,
    const uint8_t* client_id, size_t client_id_len,
    const uint8_t* proof, const uint8_t* nonce, uint64_t timestamp);

/* ═══════════════════════════════════════════════════════════════════
   PHASE 4: ACCOUNT KEYS
   ═══════════════════════════════════════════════════════════════════ */

ENCHANT_API int enchant_account_entropy_create(uint8_t* entropy_out);

ENCHANT_API int enchant_account_entropy_from_passphrase(
    const char* passphrase, size_t passphrase_len,
    uint8_t* entropy_out);

ENCHANT_API int enchant_account_key_derive(
    const uint8_t* entropy,
    uint8_t* account_key_out);

ENCHANT_API int enchant_backup_key_derive(
    const uint8_t* account_key,
    uint8_t* backup_key_out);

ENCHANT_API int enchant_media_key_derive(
    const uint8_t* account_key,
    const char* media_id,
    uint8_t* media_key_out);

/* ═══════════════════════════════════════════════════════════════════
   PHASE 4: ZK CREDENTIAL SUITE
   ═══════════════════════════════════════════════════════════════════ */

ENCHANT_API int enchant_auth_credential_present(
    const uint8_t* credential_data, size_t credential_len,
    const uint8_t* server_params,
    uint8_t* presentation_out, size_t* presentation_len);

ENCHANT_API int enchant_profile_key_credential_present(
    const uint8_t* credential_data, size_t credential_len,
    const uint8_t* server_params,
    uint8_t* presentation_out, size_t* presentation_len);

ENCHANT_API int enchant_group_credential_present(
    const uint8_t* credential_data, size_t credential_len,
    const uint8_t* server_params,
    uint8_t* presentation_out, size_t* presentation_len);

/* ═══════════════════════════════════════════════════════════════════
   PHASE 4: KEY TRANSPARENCY
   ═══════════════════════════════════════════════════════════════════ */

ENCHANT_API int enchant_key_transparency_generate_keypair(
    uint8_t* public_key,
    uint8_t* private_key);

ENCHANT_API int enchant_key_transparency_prove(
    const uint8_t* private_key,
    const uint8_t* commitment,
    uint8_t* proof_out, size_t* proof_len);

ENCHANT_API int enchant_key_transparency_verify(
    const uint8_t* public_key,
    const uint8_t* commitment,
    const uint8_t* proof, size_t proof_len,
    int* valid_out);

#define ENCHANT_KEY_TRANSPARENCY_VRF_PROOF_SIZE 80

ENCHANT_API int enchant_key_transparency_vrf_prove(
    const uint8_t private_key[32],
    const uint8_t* message, size_t message_len,
    uint8_t proof_out[ENCHANT_KEY_TRANSPARENCY_VRF_PROOF_SIZE]);

ENCHANT_API int enchant_key_transparency_vrf_verify(
    const uint8_t public_key[32],
    const uint8_t* message, size_t message_len,
    const uint8_t proof[ENCHANT_KEY_TRANSPARENCY_VRF_PROOF_SIZE],
    int* valid_out);

ENCHANT_API int enchant_key_transparency_vrf_proof_to_hash(
    const uint8_t proof[ENCHANT_KEY_TRANSPARENCY_VRF_PROOF_SIZE],
    uint8_t hash_out[32]);

ENCHANT_API int enchant_key_transparency_verify_search(
    const uint8_t vrf_public_key[32],
    const uint8_t* user_id, size_t user_id_len,
    const uint8_t* expected_value, size_t expected_value_len,
    const uint8_t* vrf_proof, size_t vrf_proof_len,
    int* valid_out);

typedef struct enchant_streaming_aead* enchant_streaming_aead_t;

ENCHANT_API int enchant_streaming_aead_encrypt_init(
    enchant_streaming_aead_t* handle_out,
    const uint8_t key[32],
    const uint8_t nonce[12],
    const uint8_t* aad, size_t aad_len);

ENCHANT_API int enchant_streaming_aead_encrypt_update(
    enchant_streaming_aead_t handle,
    const uint8_t* plaintext, size_t plaintext_len,
    uint8_t* ciphertext, size_t* ciphertext_len);

ENCHANT_API int enchant_streaming_aead_encrypt_finalize(
    enchant_streaming_aead_t handle,
    uint8_t* tag_out);

ENCHANT_API void enchant_streaming_aead_encrypt_free(
    enchant_streaming_aead_t handle);

ENCHANT_API int enchant_streaming_aead_decrypt_init(
    enchant_streaming_aead_t* handle_out,
    const uint8_t key[32],
    const uint8_t nonce[12],
    const uint8_t* aad, size_t aad_len);

ENCHANT_API int enchant_streaming_aead_decrypt_update(
    enchant_streaming_aead_t handle,
    const uint8_t* ciphertext, size_t ciphertext_len,
    uint8_t* plaintext, size_t* plaintext_len);

ENCHANT_API int enchant_streaming_aead_decrypt_finalize(
    enchant_streaming_aead_t handle,
    const uint8_t tag[16]);

ENCHANT_API void enchant_streaming_aead_decrypt_free(
    enchant_streaming_aead_t handle);


/* ═══════════════════════════════════════════════════════════════════
   PHASE 5: MEDIA SANITIZATION
   ═══════════════════════════════════════════════════════════════════ */

ENCHANT_API int enchant_media_sanitize_mp4(
    const uint8_t* data, size_t data_len,
    uint8_t* output, size_t* output_len);

ENCHANT_API int enchant_media_sanitize_webp(
    const uint8_t* data, size_t data_len,
    uint8_t* output, size_t* output_len);

#ifdef __cplusplus
}
#endif

#endif /* ENCHANT_API_H */

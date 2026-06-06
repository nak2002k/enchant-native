#ifndef ENCHANT_ZK_ZKGROUP_CLIENT_ZK_RECEIPT_HPP
#define ENCHANT_ZK_ZKGROUP_CLIENT_ZK_RECEIPT_HPP

#include <cstdint>
#include <cstddef>
#include <vector>
#include <array>
#include <optional>
#include "enchant/error.h"
#include "zk/enchant_zkp/ristretto.hpp"
#include "zk/enchant_zkp/sho_hmac_sha256.hpp"
#include "zk/enchant_zkp/sho_ext.hpp"
#include "zk/zkcredential/attributes.hpp"
#include "zk/zkgroup/server_params.hpp"
#include "zk/zkgroup/group_secret_params.hpp"
#include "zk/zkgroup/receipt_credential.hpp"

namespace enchant {
namespace zk {
namespace zkgroup {

using enchant_zkp::RistrettoPoint;
using enchant_zkp::ShoHmacSha256;
using enchant_zkp::sho_get_point;

constexpr size_t CLIENT_ZK_RECEIPT_ID_SIZE = 16;

struct EncryptedReceipt {
    std::vector<uint8_t> encrypted_data;
    uint32_t version;
};

class ClientZkReceiptOperations {
public:
    ClientZkReceiptOperations();

    int initialize(const ServerPublicParams& server_params,
                   const GroupSecretParams& group_params);

    int issue_receipt_credential(
        const std::array<uint8_t, CLIENT_ZK_RECEIPT_ID_SIZE>& receipt_id,
        uint64_t receipt_level,
        uint64_t receipt_expiration,
        const uint8_t* randomness, size_t randomness_len,
        ReceiptCredential& credential_out);

    int present_receipt_credential(
        const ReceiptCredential& credential,
        const std::array<uint8_t, CLIENT_ZK_RECEIPT_ID_SIZE>& receipt_id,
        const uint8_t* randomness, size_t randomness_len,
        ReceiptCredentialPresentation& presentation_out);

    int verify_receipt_credential(
        const ReceiptCredentialPresentation& presentation,
        const std::array<uint8_t, CLIENT_ZK_RECEIPT_ID_SIZE>& receipt_id,
        bool& valid_out);

    int encrypt_receipt_for_storage(
        const uint8_t* receipt_data, size_t receipt_data_len,
        const std::array<uint8_t, 32>& encryption_key,
        EncryptedReceipt& encrypted_out);

    int decrypt_receipt(
        const EncryptedReceipt& encrypted,
        const std::array<uint8_t, 32>& encryption_key,
        std::vector<uint8_t>& receipt_data_out);

    bool is_initialized() const { return initialized_; }

private:
    ServerPublicParams server_params_;
    GroupSecretParams group_params_;
    bool initialized_;

    RistrettoPoint derive_receipt_id_point(
        const std::array<uint8_t, CLIENT_ZK_RECEIPT_ID_SIZE>& receipt_id) const;
};

} // namespace zkgroup
} // namespace zk
} // namespace enchant

#endif

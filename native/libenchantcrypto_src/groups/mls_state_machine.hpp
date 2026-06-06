#ifndef ENCHANT_GROUPS_MLS_STATE_MACHINE_HPP
#define ENCHANT_GROUPS_MLS_STATE_MACHINE_HPP

#include <cstdint>
#include <cstddef>
#include <vector>
#include <array>
#include <string>
#include <optional>
#include <map>
#include "enchant/error.h"
#include "mls_tree_kem.hpp"

namespace enchant {
namespace groups {

constexpr size_t MLS_GROUP_ID_SIZE = 32;
constexpr size_t MLS_EPOCH_SIZE = 32;
constexpr size_t MLS_TRANSCRIPT_HASH_SIZE = 32;
constexpr size_t MLS_WELCOME_NONCE_SIZE = 24;
constexpr size_t MLS_MAX_PROPOSALS_PER_COMMIT = 64;

enum class MlsProposalType : uint8_t {
    ADD = 1,
    UPDATE = 2,
    REMOVE = 3
};

struct MlsProposal {
    MlsProposalType type;
    uint32_t sender_index;
    std::array<uint8_t, 32> target_key;
    std::array<uint8_t, MLS_GROUP_ID_SIZE> group_id;
    uint64_t timestamp;
};

struct MlsCommit {
    std::vector<MlsProposal> proposals;
    uint32_t commit_index;
    uint64_t timestamp;
};

struct MlsWelcome {
    std::array<uint8_t, MLS_GROUP_ID_SIZE> group_id;
    uint64_t epoch;
    std::vector<uint8_t> encrypted_group_secrets;
    std::vector<uint8_t> encrypted_welcome;
};

struct MlsGroupMember {
    uint32_t index;
    std::array<uint8_t, 32> identity_key;
    std::array<uint8_t, 32> leaf_secret;
    bool is_admin;
    bool is_active;
    uint64_t joined_at;
};

struct MlsGroupState {
    std::array<uint8_t, MLS_GROUP_ID_SIZE> group_id;
    uint64_t epoch;
    std::array<uint8_t, MLS_EPOCH_SIZE> epoch_secret;
    std::array<uint8_t, MLS_TRANSCRIPT_HASH_SIZE> transcript_hash;
    MlsTreeKEM tree;
    std::vector<MlsGroupMember> members;
    std::vector<MlsProposal> pending_proposals;
    std::vector<uint8_t> group_context;
};

struct MlsEpochSecrets {
    std::array<uint8_t, MLS_EPOCH_SIZE> epoch_secret;
    std::array<uint8_t, MLS_EPOCH_SIZE> encryption_secret;
    std::array<uint8_t, MLS_EPOCH_SIZE> sender_data_secret;
    std::array<uint8_t, MLS_EPOCH_SIZE> membership_key;
    std::array<uint8_t, MLS_EPOCH_SIZE> resumption_psk;
};

class MlsStateMachine {
public:
    MlsStateMachine();

    int create_group(const std::array<uint8_t, MLS_GROUP_ID_SIZE>& group_id,
                     const std::array<uint8_t, 32>& creator_identity,
                     const std::array<uint8_t, 32>& creator_leaf_secret,
                     MlsGroupState& state);

    int add_proposal(MlsGroupState& state,
                     const std::array<uint8_t, 32>& new_member_identity,
                     const std::array<uint8_t, 32>& new_member_leaf_secret);

    int update_proposal(MlsGroupState& state,
                        uint32_t sender_index,
                        const std::array<uint8_t, 32>& new_leaf_secret);

    int remove_proposal(MlsGroupState& state,
                        uint32_t target_index);

    int apply_commit(MlsGroupState& state,
                     const MlsCommit& commit,
                     MlsEpochSecrets& secrets_out);

    int generate_welcome(const MlsGroupState& state,
                         const MlsCommit& commit,
                         const MlsEpochSecrets& secrets,
                         MlsWelcome& welcome_out);

    int process_welcome(const MlsWelcome& welcome,
                        const std::array<uint8_t, 32>& new_member_identity,
                        const std::array<uint8_t, 32>& new_member_leaf_secret,
                        MlsGroupState& state_out);

    int encrypt_message(const MlsGroupState& state,
                        const MlsEpochSecrets& secrets,
                        const uint8_t* plaintext, size_t plaintext_len,
                        std::vector<uint8_t>& ciphertext_out);

    int decrypt_message(const MlsGroupState& state,
                        const MlsEpochSecrets& secrets,
                        const uint8_t* ciphertext, size_t ciphertext_len,
                        std::vector<uint8_t>& plaintext_out);

    int get_member(const MlsGroupState& state,
                   uint32_t index,
                   MlsGroupMember& member_out) const;

    int get_active_members(const MlsGroupState& state,
                           std::vector<MlsGroupMember>& members_out) const;

    size_t get_epoch(const MlsGroupState& state) const;

    int compute_transcript_hash(const MlsGroupState& state,
                                const MlsCommit& commit,
                                const MlsEpochSecrets& secrets,
                                std::array<uint8_t, MLS_TRANSCRIPT_HASH_SIZE>& hash_out) const;

private:
    int derive_epoch_secrets(const std::array<uint8_t, MLS_EPOCH_SIZE>& epoch_secret,
                             const std::array<uint8_t, MLS_TRANSCRIPT_HASH_SIZE>& transcript_hash,
                             MlsEpochSecrets& secrets_out) const;

    int compute_epoch_secret(const MlsGroupState& state,
                             const MlsCommit& commit,
                             std::array<uint8_t, MLS_EPOCH_SIZE>& epoch_secret_out) const;

    int update_tree_for_commit(MlsGroupState& state,
                               const MlsCommit& commit) const;

    static std::array<uint8_t, MLS_GROUP_ID_SIZE> generate_group_id();

    static uint64_t current_timestamp();
};

} // namespace groups
} // namespace enchant

#endif

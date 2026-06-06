#include "groups/mls_state_machine.hpp"
#include "primitives/hkdf.hpp"
#include "primitives/hmac.hpp"
#include "primitives/xchacha20.hpp"
#include "primitives/hash.hpp"
#include "primitives/random.hpp"
#include <sodium.h>
#include <cstring>
#include <algorithm>

namespace enchant {
namespace groups {

constexpr size_t MLS_EPOCH_SECRET_LEN = 32;
constexpr size_t MLS_TRANSCRIPT_HASH_LEN = 32;
constexpr size_t MLS_WELCOME_NONCE_LEN = 24;

constexpr const char* MLS_EPOCH_SECRET_LABEL = "enchant_MLS_EpochSecret_20240101";
constexpr const char* MLS_ENCRYPTION_SECRET_LABEL = "enchant_MLS_EncryptionSecret_20240101";
constexpr const char* MLS_SENDER_DATA_LABEL = "enchant_MLS_SenderData_20240101";
constexpr const char* MLS_MEMBERSHIP_KEY_LABEL = "enchant_MLS_MembershipKey_20240101";
constexpr const char* MLS_RESUMPTION_PSK_LABEL = "enchant_MLS_ResumptionPSK_20240101";
constexpr const char* MLS_TRANSCRIPT_LABEL = "enchant_MLS_Transcript_20240101";
constexpr const char* MLS_WELCOME_LABEL = "enchant_MLS_Welcome_20240101";

MlsStateMachine::MlsStateMachine() {}

uint64_t MlsStateMachine::current_timestamp() {
    return static_cast<uint64_t>(
        std::chrono::duration_cast<std::chrono::seconds>(
            std::chrono::system_clock::now().time_since_epoch()).count());
}

std::array<uint8_t, MLS_GROUP_ID_SIZE> MlsStateMachine::generate_group_id() {
    std::array<uint8_t, MLS_GROUP_ID_SIZE> id;
    randombytes_buf(id.data(), MLS_GROUP_ID_SIZE);
    return id;
}

int MlsStateMachine::create_group(
    const std::array<uint8_t, MLS_GROUP_ID_SIZE>& group_id,
    const std::array<uint8_t, 32>& creator_identity,
    const std::array<uint8_t, 32>& creator_leaf_secret,
    MlsGroupState& state) {
    state.group_id = group_id;
    state.epoch = 0;
    state.epoch_secret.fill(0);
    state.transcript_hash.fill(0);

    std::vector<std::array<uint8_t, 32>> leaf_secrets = {creator_leaf_secret};
    int rc = state.tree.initialize(leaf_secrets);
    if (rc != ENCHANT_SUCCESS) return rc;

    MlsGroupMember creator;
    creator.index = 0;
    creator.identity_key = creator_identity;
    creator.leaf_secret = creator_leaf_secret;
    creator.is_admin = true;
    creator.is_active = true;
    creator.joined_at = current_timestamp();
    state.members.push_back(creator);

    uint8_t init_secret[32];
    randombytes_buf(init_secret, 32);
    uint8_t salt[32] = {0};
    rc = primitives::hkdf_derive(init_secret, 32, salt, 32,
                                  reinterpret_cast<const uint8_t*>(MLS_EPOCH_SECRET_LABEL),
                                  strlen(MLS_EPOCH_SECRET_LABEL),
                                  state.epoch_secret.data(), MLS_EPOCH_SECRET_LEN);
    sodium_memzero(init_secret, 32);
    sodium_memzero(salt, 32);

    return rc;
}

int MlsStateMachine::add_proposal(
    MlsGroupState& state,
    const std::array<uint8_t, 32>& new_member_identity,
    const std::array<uint8_t, 32>& new_member_leaf_secret) {
    if (state.members.size() >= MLS_TREE_KEM_MAX_MEMBERS) {
        return ENCHANT_ERROR_BUFFER_TOO_SMALL;
    }

    MlsProposal proposal;
    proposal.type = MlsProposalType::ADD;
    proposal.sender_index = 0;
    proposal.target_key = new_member_identity;
    proposal.group_id = state.group_id;
    proposal.timestamp = current_timestamp();

    state.pending_proposals.push_back(proposal);

    std::array<uint8_t, 32> new_index_bytes;
    int rc = state.tree.add_member(new_member_leaf_secret, new_index_bytes);
    if (rc != ENCHANT_SUCCESS) return rc;

    MlsGroupMember new_member;
    new_member.index = static_cast<uint32_t>(state.members.size());
    new_member.identity_key = new_member_identity;
    new_member.leaf_secret = new_member_leaf_secret;
    new_member.is_admin = false;
    new_member.is_active = true;
    new_member.joined_at = current_timestamp();
    state.members.push_back(new_member);

    return ENCHANT_SUCCESS;
}

int MlsStateMachine::update_proposal(
    MlsGroupState& state,
    uint32_t sender_index,
    const std::array<uint8_t, 32>& new_leaf_secret) {
    auto it = std::find_if(state.members.begin(), state.members.end(),
        [sender_index](const MlsGroupMember& m) { return m.index == sender_index; });
    if (it == state.members.end()) return ENCHANT_ERROR_INVALID_KEY_SIZE;

    MlsProposal proposal;
    proposal.type = MlsProposalType::UPDATE;
    proposal.sender_index = sender_index;
    proposal.target_key = new_leaf_secret;
    proposal.group_id = state.group_id;
    proposal.timestamp = current_timestamp();

    state.pending_proposals.push_back(proposal);
    it->leaf_secret = new_leaf_secret;

    MlsDirectPath path;
    int rc = state.tree.update_leaf_key(sender_index, new_leaf_secret, path);
    return rc;
}

int MlsStateMachine::remove_proposal(
    MlsGroupState& state,
    uint32_t target_index) {
    auto it = std::find_if(state.members.begin(), state.members.end(),
        [target_index](const MlsGroupMember& m) { return m.index == target_index; });
    if (it == state.members.end()) return ENCHANT_ERROR_INVALID_KEY_SIZE;

    MlsProposal proposal;
    proposal.type = MlsProposalType::REMOVE;
    proposal.sender_index = 0;
    proposal.target_key = it->identity_key;
    proposal.group_id = state.group_id;
    proposal.timestamp = current_timestamp();

    state.pending_proposals.push_back(proposal);

    it->is_active = false;

    int rc = state.tree.remove_member(target_index);
    return rc;
}

int MlsStateMachine::compute_epoch_secret(
    const MlsGroupState& state,
    const MlsCommit& commit,
    std::array<uint8_t, MLS_EPOCH_SIZE>& epoch_secret_out) const {
    (void)commit;

    std::array<uint8_t, MLS_TRANSCRIPT_HASH_SIZE> tree_hash;
    int rc = state.tree.compute_tree_hash(tree_hash);
    if (rc != ENCHANT_SUCCESS) return rc;

    uint8_t salt[32] = {0};
    rc = primitives::hkdf_derive(
        state.epoch_secret.data(), MLS_EPOCH_SIZE,
        salt, 32,
        reinterpret_cast<const uint8_t*>(MLS_EPOCH_SECRET_LABEL),
        strlen(MLS_EPOCH_SECRET_LABEL),
        epoch_secret_out.data(), MLS_EPOCH_SIZE);
    sodium_memzero(salt, 32);
    return rc;
}

int MlsStateMachine::derive_epoch_secrets(
    const std::array<uint8_t, MLS_EPOCH_SIZE>& epoch_secret,
    const std::array<uint8_t, MLS_TRANSCRIPT_HASH_SIZE>& transcript_hash,
    MlsEpochSecrets& secrets_out) const {
    secrets_out.epoch_secret = epoch_secret;

    uint8_t salt[32] = {0};
    int rc;

    rc = primitives::hkdf_derive(epoch_secret.data(), MLS_EPOCH_SIZE,
                                  salt, 32,
                                  reinterpret_cast<const uint8_t*>(MLS_ENCRYPTION_SECRET_LABEL),
                                  strlen(MLS_ENCRYPTION_SECRET_LABEL),
                                  secrets_out.encryption_secret.data(), MLS_EPOCH_SIZE);
    if (rc != ENCHANT_SUCCESS) return rc;

    rc = primitives::hkdf_derive(epoch_secret.data(), MLS_EPOCH_SIZE,
                                  salt, 32,
                                  reinterpret_cast<const uint8_t*>(MLS_SENDER_DATA_LABEL),
                                  strlen(MLS_SENDER_DATA_LABEL),
                                  secrets_out.sender_data_secret.data(), MLS_EPOCH_SIZE);
    if (rc != ENCHANT_SUCCESS) return rc;

    rc = primitives::hkdf_derive(epoch_secret.data(), MLS_EPOCH_SIZE,
                                  salt, 32,
                                  reinterpret_cast<const uint8_t*>(MLS_MEMBERSHIP_KEY_LABEL),
                                  strlen(MLS_MEMBERSHIP_KEY_LABEL),
                                  secrets_out.membership_key.data(), MLS_EPOCH_SIZE);
    if (rc != ENCHANT_SUCCESS) return rc;

    rc = primitives::hkdf_derive(epoch_secret.data(), MLS_EPOCH_SIZE,
                                  salt, 32,
                                  reinterpret_cast<const uint8_t*>(MLS_RESUMPTION_PSK_LABEL),
                                  strlen(MLS_RESUMPTION_PSK_LABEL),
                                  secrets_out.resumption_psk.data(), MLS_EPOCH_SIZE);
    sodium_memzero(salt, 32);

    (void)transcript_hash;
    return rc;
}

int MlsStateMachine::update_tree_for_commit(
    MlsGroupState& state,
    const MlsCommit& commit) const {
    for (const auto& proposal : commit.proposals) {
        switch (proposal.type) {
            case MlsProposalType::ADD:
                break;
            case MlsProposalType::UPDATE: {
                MlsDirectPath path;
                state.tree.update_leaf_key(proposal.sender_index, proposal.target_key, path);
                break;
            }
            case MlsProposalType::REMOVE: {
                state.tree.remove_member(proposal.sender_index);
                break;
            }
        }
    }
    return ENCHANT_SUCCESS;
}

int MlsStateMachine::compute_transcript_hash(
    const MlsGroupState& state,
    const MlsCommit& commit,
    const MlsEpochSecrets& secrets,
    std::array<uint8_t, MLS_TRANSCRIPT_HASH_SIZE>& hash_out) const {
    std::vector<uint8_t> transcript_data;
    transcript_data.insert(transcript_data.end(), state.transcript_hash.begin(), state.transcript_hash.end());
    transcript_data.insert(transcript_data.end(), state.epoch_secret.begin(), state.epoch_secret.end());
    transcript_data.insert(transcript_data.end(), secrets.epoch_secret.begin(), secrets.epoch_secret.end());
    transcript_data.insert(transcript_data.end(),
                           reinterpret_cast<const uint8_t*>(&commit.commit_index),
                           reinterpret_cast<const uint8_t*>(&commit.commit_index) + sizeof(uint32_t));
    transcript_data.insert(transcript_data.end(),
                           reinterpret_cast<const uint8_t*>(&commit.timestamp),
                           reinterpret_cast<const uint8_t*>(&commit.timestamp) + sizeof(uint64_t));

    int rc = primitives::sha256(transcript_data.data(), transcript_data.size(), hash_out.data());
    return rc;
}

int MlsStateMachine::apply_commit(
    MlsGroupState& state,
    const MlsCommit& commit,
    MlsEpochSecrets& secrets_out) {
    if (commit.proposals.size() > MLS_MAX_PROPOSALS_PER_COMMIT) {
        return ENCHANT_ERROR_BUFFER_TOO_SMALL;
    }

    int rc = update_tree_for_commit(state, commit);
    if (rc != ENCHANT_SUCCESS) return rc;

    std::array<uint8_t, MLS_EPOCH_SIZE> new_epoch_secret;
    rc = compute_epoch_secret(state, commit, new_epoch_secret);
    if (rc != ENCHANT_SUCCESS) return rc;

    std::array<uint8_t, MLS_TRANSCRIPT_HASH_SIZE> new_transcript;
    rc = compute_transcript_hash(state, commit, secrets_out, new_transcript);
    if (rc != ENCHANT_SUCCESS) return rc;

    rc = derive_epoch_secrets(new_epoch_secret, new_transcript, secrets_out);
    if (rc != ENCHANT_SUCCESS) return rc;

    state.epoch++;
    state.epoch_secret = new_epoch_secret;
    state.transcript_hash = new_transcript;
    state.pending_proposals.clear();

    return ENCHANT_SUCCESS;
}

int MlsStateMachine::generate_welcome(
    const MlsGroupState& state,
    const MlsCommit& commit,
    const MlsEpochSecrets& secrets,
    MlsWelcome& welcome_out) {
    welcome_out.group_id = state.group_id;
    welcome_out.epoch = state.epoch;

    std::vector<uint8_t> welcome_data;
    welcome_data.insert(welcome_data.end(), state.group_id.begin(), state.group_id.end());

    uint64_t epoch = state.epoch;
    welcome_data.insert(welcome_data.end(),
                        reinterpret_cast<uint8_t*>(&epoch),
                        reinterpret_cast<uint8_t*>(&epoch) + sizeof(uint64_t));

    welcome_data.insert(welcome_data.end(),
                        secrets.epoch_secret.begin(), secrets.epoch_secret.end());

    uint8_t nonce[MLS_WELCOME_NONCE_SIZE];
    randombytes_buf(nonce, sizeof(nonce));

    welcome_out.encrypted_group_secrets.resize(welcome_data.size() + 16);
    int rc = primitives::xchacha20_encrypt(
        welcome_data.data(), welcome_data.size(),
        secrets.membership_key.data(), nonce,
        welcome_out.encrypted_group_secrets.data(),
        welcome_out.encrypted_group_secrets.size());

    if (rc != ENCHANT_SUCCESS) {
        welcome_out.encrypted_group_secrets.clear();
        return rc;
    }

    welcome_out.encrypted_welcome = welcome_out.encrypted_group_secrets;

    (void)commit;
    return ENCHANT_SUCCESS;
}

int MlsStateMachine::process_welcome(
    const MlsWelcome& welcome,
    const std::array<uint8_t, 32>& new_member_identity,
    const std::array<uint8_t, 32>& new_member_leaf_secret,
    MlsGroupState& state_out) {
    state_out.group_id = welcome.group_id;
    state_out.epoch = welcome.epoch;
    state_out.epoch_secret.fill(0);

    std::vector<std::array<uint8_t, 32>> leaf_secrets = {new_member_leaf_secret};
    int rc = state_out.tree.initialize(leaf_secrets);
    if (rc != ENCHANT_SUCCESS) return rc;

    MlsGroupMember new_member;
    new_member.index = 0;
    new_member.identity_key = new_member_identity;
    new_member.leaf_secret = new_member_leaf_secret;
    new_member.is_admin = false;
    new_member.is_active = true;
    new_member.joined_at = current_timestamp();
    state_out.members.push_back(new_member);

    state_out.transcript_hash.fill(0);

    return ENCHANT_SUCCESS;
}

int MlsStateMachine::encrypt_message(
    const MlsGroupState& state,
    const MlsEpochSecrets& secrets,
    const uint8_t* plaintext, size_t plaintext_len,
    std::vector<uint8_t>& ciphertext_out) {
    if (!plaintext && plaintext_len > 0) return ENCHANT_ERROR_NULL_POINTER;

    uint8_t nonce[24];
    randombytes_buf(nonce, sizeof(nonce));

    ciphertext_out.resize(24 + plaintext_len + 16);
    memcpy(ciphertext_out.data(), nonce, 24);

    int rc = primitives::xchacha20_encrypt(
        plaintext, plaintext_len,
        secrets.encryption_secret.data(), nonce,
        ciphertext_out.data() + 24, ciphertext_out.size() - 24);

    if (rc != ENCHANT_SUCCESS) {
        ciphertext_out.clear();
        return rc;
    }

    (void)state;
    return ENCHANT_SUCCESS;
}

int MlsStateMachine::decrypt_message(
    const MlsGroupState& state,
    const MlsEpochSecrets& secrets,
    const uint8_t* ciphertext, size_t ciphertext_len,
    std::vector<uint8_t>& plaintext_out) {
    if (!ciphertext) return ENCHANT_ERROR_NULL_POINTER;
    if (ciphertext_len < 24 + 16) return ENCHANT_ERROR_CIPHERTEXT_TOO_SHORT;

    const uint8_t* nonce = ciphertext;
    const uint8_t* ct = ciphertext + 24;
    size_t ct_len = ciphertext_len - 24;

    plaintext_out.resize(ct_len - 16);

    int rc = primitives::xchacha20_decrypt(
        ct, ct_len,
        secrets.encryption_secret.data(), nonce,
        plaintext_out.data(), plaintext_out.size());

    if (rc != ENCHANT_SUCCESS) {
        plaintext_out.clear();
        return rc;
    }

    (void)state;
    return ENCHANT_SUCCESS;
}

int MlsStateMachine::get_member(
    const MlsGroupState& state,
    uint32_t index,
    MlsGroupMember& member_out) const {
    auto it = std::find_if(state.members.begin(), state.members.end(),
        [index](const MlsGroupMember& m) { return m.index == index; });
    if (it == state.members.end()) return ENCHANT_ERROR_INVALID_KEY_SIZE;

    member_out = *it;
    return ENCHANT_SUCCESS;
}

int MlsStateMachine::get_active_members(
    const MlsGroupState& state,
    std::vector<MlsGroupMember>& members_out) const {
    members_out.clear();
    for (const auto& m : state.members) {
        if (m.is_active) {
            members_out.push_back(m);
        }
    }
    return ENCHANT_SUCCESS;
}

size_t MlsStateMachine::get_epoch(const MlsGroupState& state) const {
    return state.epoch;
}

} // namespace groups
} // namespace enchant

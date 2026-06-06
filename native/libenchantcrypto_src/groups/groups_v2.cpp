#include "groups/groups_v2.hpp"
#include "primitives/random.hpp"
#include "primitives/hkdf.hpp"
#include "primitives/hmac.hpp"
#include "primitives/constant_time.hpp"
#include <sodium.h>
#include <cstring>
#include <algorithm>

namespace enchant {
namespace groups {

constexpr const char* GROUPS_V2_EPOCH_SECRET_LABEL = "EnchantGroupsV2_EpochSecret_20240101";

GroupsV2Manager::GroupsV2Manager() {}

std::array<uint8_t, GROUPS_V2_GROUP_ID_SIZE> GroupsV2Manager::generate_group_id() const {
    std::array<uint8_t, GROUPS_V2_GROUP_ID_SIZE> id;
    randombytes_buf(id.data(), GROUPS_V2_GROUP_ID_SIZE);
    return id;
}

int GroupsV2Manager::create_group(
    const std::array<uint8_t, GROUPS_V2_MEMBER_ID_SIZE>& creator_id,
    const std::array<uint8_t, 32>& creator_secret,
    const std::string& title,
    GroupState& state) {
    state.group_id = generate_group_id();
    state.epoch = 0;
    state.title = title;

    GroupMember creator;
    creator.member_id = creator_id;
    creator.leaf_secret = creator_secret;
    creator.leaf_index = 0;
    creator.is_admin = true;
    state.members.push_back(creator);

    std::vector<std::array<uint8_t, 32>> leaf_secrets = {creator_secret};
    int rc = state.tree.initialize(leaf_secrets);
    if (rc != ENCHANT_SUCCESS) return rc;

    uint8_t salt[32] = {0};
    rc = primitives::hkdf_derive(
        state.group_id.data(), GROUPS_V2_GROUP_ID_SIZE,
        salt, 32,
        reinterpret_cast<const uint8_t*>(GROUPS_V2_EPOCH_SECRET_LABEL),
        strlen(GROUPS_V2_EPOCH_SECRET_LABEL),
        state.epoch_secret.data(), GROUPS_V2_EPOCH_SECRET_SIZE);
    sodium_memzero(salt, sizeof(salt));

    return rc;
}

int GroupsV2Manager::add_member(
    GroupState& state,
    const std::array<uint8_t, GROUPS_V2_MEMBER_ID_SIZE>& new_member_id,
    const std::array<uint8_t, 32>& new_member_secret,
    GroupCommit& commit) {
    if (state.members.size() >= GROUPS_V2_MAX_MEMBERS) {
        return ENCHANT_ERROR_BUFFER_TOO_SMALL;
    }

    for (const auto& m : state.members) {
        uint8_t cmp = 0;
        for (size_t i = 0; i < GROUPS_V2_MEMBER_ID_SIZE; i++) {
            cmp |= m.member_id[i] ^ new_member_id[i];
        }
        if (cmp == 0) return ENCHANT_ERROR_INVALID_FORMAT;
    }

    GroupProposal proposal;
    proposal.action = GroupActionType::ADD_MEMBER;
    proposal.target_member = new_member_id;
    proposal.new_leaf_secret = new_member_secret;
    commit.applied_proposals.push_back(proposal);

    std::array<uint8_t, 32> new_index_bytes;
    int rc = state.tree.add_member(new_member_secret, new_index_bytes);
    if (rc != ENCHANT_SUCCESS) return rc;

    uint32_t new_index = static_cast<uint32_t>(state.members.size());

    GroupMember new_member;
    new_member.member_id = new_member_id;
    new_member.leaf_secret = new_member_secret;
    new_member.leaf_index = new_index;
    new_member.is_admin = false;
    state.members.push_back(new_member);

    commit.new_epoch = state.epoch + 1;
    memcpy(commit.epoch_secret.data(), state.epoch_secret.data(), GROUPS_V2_EPOCH_SECRET_SIZE);

    return ENCHANT_SUCCESS;
}

int GroupsV2Manager::remove_member(
    GroupState& state,
    const std::array<uint8_t, GROUPS_V2_MEMBER_ID_SIZE>& target_member_id,
    GroupCommit& commit) {
    auto it = std::find_if(state.members.begin(), state.members.end(),
        [&target_member_id](const GroupMember& m) {
            uint8_t cmp = 0;
            for (size_t i = 0; i < GROUPS_V2_MEMBER_ID_SIZE; i++) {
                cmp |= m.member_id[i] ^ target_member_id[i];
            }
            return cmp == 0;
        });

    if (it == state.members.end()) return ENCHANT_ERROR_INVALID_KEY_SIZE;

    GroupProposal proposal;
    proposal.action = GroupActionType::REMOVE_MEMBER;
    proposal.target_member = target_member_id;
    commit.applied_proposals.push_back(proposal);

    int rc = state.tree.remove_member(it->leaf_index);
    if (rc != ENCHANT_SUCCESS) return rc;

    state.members.erase(it);

    commit.new_epoch = state.epoch + 1;
    memcpy(commit.epoch_secret.data(), state.epoch_secret.data(), GROUPS_V2_EPOCH_SECRET_SIZE);

    return ENCHANT_SUCCESS;
}

int GroupsV2Manager::update_member_key(
    GroupState& state,
    const std::array<uint8_t, GROUPS_V2_MEMBER_ID_SIZE>& member_id,
    const std::array<uint8_t, 32>& new_secret,
    GroupCommit& commit) {
    auto it = std::find_if(state.members.begin(), state.members.end(),
        [&member_id](const GroupMember& m) {
            uint8_t cmp = 0;
            for (size_t i = 0; i < GROUPS_V2_MEMBER_ID_SIZE; i++) {
                cmp |= m.member_id[i] ^ member_id[i];
            }
            return cmp == 0;
        });

    if (it == state.members.end()) return ENCHANT_ERROR_INVALID_KEY_SIZE;

    GroupProposal proposal;
    proposal.action = GroupActionType::UPDATE_KEY;
    proposal.target_member = member_id;
    proposal.new_leaf_secret = new_secret;
    commit.applied_proposals.push_back(proposal);

    MlsDirectPath path;
    int rc = state.tree.update_leaf_key(it->leaf_index, new_secret, path);
    if (rc != ENCHANT_SUCCESS) return rc;

    it->leaf_secret = new_secret;
    commit.path = path;

    commit.new_epoch = state.epoch + 1;
    memcpy(commit.epoch_secret.data(), state.epoch_secret.data(), GROUPS_V2_EPOCH_SECRET_SIZE);

    return ENCHANT_SUCCESS;
}

int GroupsV2Manager::apply_commit(GroupState& state, const GroupCommit& commit) {
    state.epoch = commit.new_epoch;
    memcpy(state.epoch_secret.data(), commit.epoch_secret.data(), GROUPS_V2_EPOCH_SECRET_SIZE);
    return ENCHANT_SUCCESS;
}

int GroupsV2Manager::find_member(
    const GroupState& state,
    const std::array<uint8_t, GROUPS_V2_MEMBER_ID_SIZE>& member_id,
    GroupMember& member) const {
    auto it = std::find_if(state.members.begin(), state.members.end(),
        [&member_id](const GroupMember& m) {
            uint8_t cmp = 0;
            for (size_t i = 0; i < GROUPS_V2_MEMBER_ID_SIZE; i++) {
                cmp |= m.member_id[i] ^ member_id[i];
            }
            return cmp == 0;
        });

    if (it == state.members.end()) return ENCHANT_ERROR_INVALID_KEY_SIZE;

    member = *it;
    return ENCHANT_SUCCESS;
}

int GroupsV2Manager::get_member_count(const GroupState& state) const {
    return static_cast<int>(state.members.size());
}

int GroupsV2Manager::serialize_group_state(const GroupState& state,
                                            std::vector<uint8_t>& output) const {
    size_t total_size = GROUPS_V2_GROUP_ID_SIZE + 4 + 4 +
                        state.title.size() + 4 +
                        state.members.size() * (GROUPS_V2_MEMBER_ID_SIZE + 4 + 1 + 32) +
                        GROUPS_V2_EPOCH_SECRET_SIZE;

    output.resize(total_size);
    size_t offset = 0;

    memcpy(output.data() + offset, state.group_id.data(), GROUPS_V2_GROUP_ID_SIZE);
    offset += GROUPS_V2_GROUP_ID_SIZE;

    memcpy(output.data() + offset, &state.epoch, 4);
    offset += 4;

    uint32_t title_len = static_cast<uint32_t>(state.title.size());
    memcpy(output.data() + offset, &title_len, 4);
    offset += 4;
    memcpy(output.data() + offset, state.title.data(), title_len);
    offset += title_len;

    uint32_t member_count = static_cast<uint32_t>(state.members.size());
    memcpy(output.data() + offset, &member_count, 4);
    offset += 4;

    for (const auto& m : state.members) {
        memcpy(output.data() + offset, m.member_id.data(), GROUPS_V2_MEMBER_ID_SIZE);
        offset += GROUPS_V2_MEMBER_ID_SIZE;
        memcpy(output.data() + offset, &m.leaf_index, 4);
        offset += 4;
        output[offset] = m.is_admin ? 1 : 0;
        offset += 1;
        memcpy(output.data() + offset, m.leaf_secret.data(), 32);
        offset += 32;
    }

    memcpy(output.data() + offset, state.epoch_secret.data(), GROUPS_V2_EPOCH_SECRET_SIZE);
    offset += GROUPS_V2_EPOCH_SECRET_SIZE;

    output.resize(offset);
    return ENCHANT_SUCCESS;
}

int GroupsV2Manager::deserialize_group_state(GroupState& state,
                                              const uint8_t* input, size_t input_len) {
    if (!input) return ENCHANT_ERROR_NULL_POINTER;
    if (input_len < GROUPS_V2_GROUP_ID_SIZE + 4 + 4 + 4 + GROUPS_V2_EPOCH_SECRET_SIZE)
        return ENCHANT_ERROR_CIPHERTEXT_TOO_SHORT;

    size_t offset = 0;

    memcpy(state.group_id.data(), input + offset, GROUPS_V2_GROUP_ID_SIZE);
    offset += GROUPS_V2_GROUP_ID_SIZE;

    memcpy(&state.epoch, input + offset, 4);
    offset += 4;

    uint32_t title_len;
    memcpy(&title_len, input + offset, 4);
    offset += 4;
    if (offset + title_len > input_len) return ENCHANT_ERROR_CIPHERTEXT_TOO_SHORT;
    state.title.assign(reinterpret_cast<const char*>(input + offset), title_len);
    offset += title_len;

    if (offset + 4 > input_len) return ENCHANT_ERROR_CIPHERTEXT_TOO_SHORT;
    uint32_t member_count;
    memcpy(&member_count, input + offset, 4);
    offset += 4;

    state.members.clear();
    for (uint32_t i = 0; i < member_count; i++) {
        if (offset + GROUPS_V2_MEMBER_ID_SIZE + 4 + 1 + 32 > input_len)
            return ENCHANT_ERROR_CIPHERTEXT_TOO_SHORT;

        GroupMember m;
        memcpy(m.member_id.data(), input + offset, GROUPS_V2_MEMBER_ID_SIZE);
        offset += GROUPS_V2_MEMBER_ID_SIZE;
        memcpy(&m.leaf_index, input + offset, 4);
        offset += 4;
        m.is_admin = input[offset] != 0;
        offset += 1;
        memcpy(m.leaf_secret.data(), input + offset, 32);
        offset += 32;
        state.members.push_back(m);
    }

    if (offset + GROUPS_V2_EPOCH_SECRET_SIZE > input_len)
        return ENCHANT_ERROR_CIPHERTEXT_TOO_SHORT;
    memcpy(state.epoch_secret.data(), input + offset, GROUPS_V2_EPOCH_SECRET_SIZE);
    offset += GROUPS_V2_EPOCH_SECRET_SIZE;

    std::vector<std::array<uint8_t, 32>> leaf_secrets;
    for (const auto& m : state.members) {
        leaf_secrets.push_back(m.leaf_secret);
    }
    if (!leaf_secrets.empty()) {
        int rc = state.tree.initialize(leaf_secrets);
        if (rc != ENCHANT_SUCCESS) return rc;
    }

    return ENCHANT_SUCCESS;
}

} // namespace groups
} // namespace enchant

#ifndef ENCHANT_GROUPS_GROUPS_V2_HPP
#define ENCHANT_GROUPS_GROUPS_V2_HPP

#include <cstdint>
#include <cstddef>
#include <vector>
#include <array>
#include <string>
#include <optional>
#include "enchant/error.h"
#include "mls_tree_kem.hpp"

namespace enchant {
namespace groups {

constexpr size_t GROUPS_V2_GROUP_ID_SIZE = 32;
constexpr size_t GROUPS_V2_EPOCH_SECRET_SIZE = 32;
constexpr size_t GROUPS_V2_MEMBER_ID_SIZE = 32;
constexpr size_t GROUPS_V2_MAX_MEMBERS = MLS_TREE_KEM_MAX_MEMBERS;

enum class GroupActionType : uint8_t {
    ADD_MEMBER = 1,
    REMOVE_MEMBER = 2,
    UPDATE_KEY = 3,
    CHANGE_TITLE = 4
};

struct GroupMember {
    std::array<uint8_t, GROUPS_V2_MEMBER_ID_SIZE> member_id;
    std::array<uint8_t, 32> leaf_secret;
    uint32_t leaf_index;
    bool is_admin;
};

struct GroupProposal {
    GroupActionType action;
    std::array<uint8_t, GROUPS_V2_MEMBER_ID_SIZE> target_member;
    std::array<uint8_t, 32> new_leaf_secret;
    std::string new_title;
};

struct GroupCommit {
    uint32_t new_epoch;
    std::array<uint8_t, GROUPS_V2_EPOCH_SECRET_SIZE> epoch_secret;
    std::vector<GroupProposal> applied_proposals;
    MlsDirectPath path;
};

struct GroupState {
    std::array<uint8_t, GROUPS_V2_GROUP_ID_SIZE> group_id;
    uint32_t epoch;
    std::string title;
    std::vector<GroupMember> members;
    MlsTreeKEM tree;
    std::array<uint8_t, GROUPS_V2_EPOCH_SECRET_SIZE> epoch_secret;
};

class GroupsV2Manager {
public:
    GroupsV2Manager();

    int create_group(const std::array<uint8_t, GROUPS_V2_MEMBER_ID_SIZE>& creator_id,
                     const std::array<uint8_t, 32>& creator_secret,
                     const std::string& title,
                     GroupState& state);

    int add_member(GroupState& state,
                   const std::array<uint8_t, GROUPS_V2_MEMBER_ID_SIZE>& new_member_id,
                   const std::array<uint8_t, 32>& new_member_secret,
                   GroupCommit& commit);

    int remove_member(GroupState& state,
                      const std::array<uint8_t, GROUPS_V2_MEMBER_ID_SIZE>& target_member_id,
                      GroupCommit& commit);

    int update_member_key(GroupState& state,
                          const std::array<uint8_t, GROUPS_V2_MEMBER_ID_SIZE>& member_id,
                          const std::array<uint8_t, 32>& new_secret,
                          GroupCommit& commit);

    int apply_commit(GroupState& state, const GroupCommit& commit);

    int find_member(const GroupState& state,
                    const std::array<uint8_t, GROUPS_V2_MEMBER_ID_SIZE>& member_id,
                    GroupMember& member) const;

    int get_member_count(const GroupState& state) const;

    int serialize_group_state(const GroupState& state,
                              std::vector<uint8_t>& output) const;

    int deserialize_group_state(GroupState& state,
                                const uint8_t* input, size_t input_len);

private:
    std::array<uint8_t, GROUPS_V2_GROUP_ID_SIZE> generate_group_id() const;
};

} // namespace groups
} // namespace enchant

#endif

#ifndef ENCHANT_GROUPS_MLS_TREE_KEM_HPP
#define ENCHANT_GROUPS_MLS_TREE_KEM_HPP

#include <cstdint>
#include <cstddef>
#include <vector>
#include <array>
#include <optional>
#include "enchant/error.h"
#include "primitives/x25519.hpp"

namespace enchant {
namespace groups {

constexpr size_t MLS_TREE_KEM_NODE_SIZE = 32;
constexpr size_t MLS_TREE_KEM_MAX_MEMBERS = 256;
constexpr size_t MLS_TREE_KEM_PATH_SECRET_SIZE = 32;
constexpr size_t MLS_TREE_KEM_HMAC_SIZE = 32;
constexpr size_t MLS_TREE_KEM_NONCE_SIZE = 12;
constexpr size_t MLS_TREE_KEM_GROUP_SECRET_SIZE = 32;

struct MlsNode {
    std::array<uint8_t, MLS_TREE_KEM_NODE_SIZE> public_key;
    std::array<uint8_t, MLS_TREE_KEM_HMAC_SIZE> tree_hash;
    std::optional<std::array<uint8_t, 32>> private_key;
    bool is_leaf;
    uint32_t leaf_index;

    MlsNode();
};

struct MlsTreeKEMPathSecret {
    uint32_t node_index;
    std::array<uint8_t, MLS_TREE_KEM_PATH_SECRET_SIZE> secret;
};

struct MlsUpdatePathNode {
    uint32_t node_index;
    std::array<uint8_t, MLS_TREE_KEM_NODE_SIZE> public_key;
    std::vector<MlsTreeKEMPathSecret> path_secrets;
};

struct MlsDirectPath {
    std::vector<MlsUpdatePathNode> nodes;
};

struct MlsLeafNode {
    std::array<uint8_t, MLS_TREE_KEM_NODE_SIZE> identity_key;
    std::array<uint8_t, 32> leaf_secret;
};

struct MlsTreeState {
    std::vector<MlsNode> nodes;
    uint32_t total_leaves;
    uint32_t current_epoch;
    std::array<uint8_t, MLS_TREE_KEM_GROUP_SECRET_SIZE> epoch_secret;
};

class MlsTreeKEM {
public:
    MlsTreeKEM();

    int initialize(const std::vector<std::array<uint8_t, 32>>& initial_leaf_secrets);

    int add_member(const std::array<uint8_t, 32>& leaf_secret,
                   std::array<uint8_t, 32>& new_member_index);

    int remove_member(uint32_t leaf_index);

    int update_leaf_key(uint32_t leaf_index,
                        const std::array<uint8_t, 32>& new_secret,
                        MlsDirectPath& path);

    int encrypt_path(const MlsDirectPath& path,
                     uint32_t sender_leaf_index,
                     std::array<uint8_t, MLS_TREE_KEM_GROUP_SECRET_SIZE>& group_secret);

    int decrypt_path(const MlsDirectPath& path,
                     uint32_t receiver_leaf_index,
                     std::array<uint8_t, MLS_TREE_KEM_GROUP_SECRET_SIZE>& group_secret);

    int compute_tree_hash(std::array<uint8_t, MLS_TREE_KEM_HMAC_SIZE>& root_hash) const;

    int get_node_public_key(uint32_t node_index,
                            std::array<uint8_t, MLS_TREE_KEM_NODE_SIZE>& public_key) const;

    int set_node_public_key(uint32_t node_index,
                            const std::array<uint8_t, MLS_TREE_KEM_NODE_SIZE>& public_key);

    int set_node_private_key(uint32_t node_index,
                             const std::array<uint8_t, 32>& private_key);

    int get_root_public_key(std::array<uint8_t, MLS_TREE_KEM_NODE_SIZE>& public_key) const;

    size_t leaf_count() const { return total_leaves_; }
    size_t node_count() const { return nodes_.size(); }
    uint32_t current_epoch() const { return current_epoch_; }
    const std::vector<MlsNode>& nodes() const { return nodes_; }

    static uint32_t leaf_to_node(uint32_t leaf_index);
    static uint32_t node_to_leaf(uint32_t node_index);
    static uint32_t parent(uint32_t node_index);
    static uint32_t sibling(uint32_t node_index);
    static uint32_t left_child(uint32_t node_index);
    static uint32_t right_child(uint32_t node_index);
    static size_t tree_size(size_t leaf_count);
    static size_t level_offset(size_t level, size_t leaf_count);
    static size_t root_index(size_t leaf_count);

    static int derive_path_secret(
        const std::array<uint8_t, MLS_TREE_KEM_PATH_SECRET_SIZE>& path_secret,
        uint32_t generation,
        std::array<uint8_t, MLS_TREE_KEM_PATH_SECRET_SIZE>& next_secret);

    static int derive_node_secret(
        const std::array<uint8_t, MLS_TREE_KEM_PATH_SECRET_SIZE>& path_secret,
        std::array<uint8_t, 32>& node_secret,
        std::array<uint8_t, MLS_TREE_KEM_NODE_SIZE>& public_key);

    static int compute_node_hash(
        const std::array<uint8_t, MLS_TREE_KEM_HMAC_SIZE>& left_hash,
        const std::array<uint8_t, MLS_TREE_KEM_HMAC_SIZE>& right_hash,
        const std::array<uint8_t, MLS_TREE_KEM_NODE_SIZE>& public_key,
        std::array<uint8_t, MLS_TREE_KEM_HMAC_SIZE>& output_hash);

private:
    std::vector<MlsNode> nodes_;
    size_t total_leaves_;
    uint32_t current_epoch_;
    std::array<uint8_t, MLS_TREE_KEM_GROUP_SECRET_SIZE> epoch_secret_;

    int derive_public_key(const std::array<uint8_t, 32>& private_key,
                          std::array<uint8_t, MLS_TREE_KEM_NODE_SIZE>& public_key) const;

    int derive_private_key(const std::array<uint8_t, MLS_TREE_KEM_PATH_SECRET_SIZE>& path_secret,
                           std::array<uint8_t, 32>& private_key) const;

    int compute_subtree_hash(uint32_t node_index,
                             std::array<uint8_t, MLS_TREE_KEM_HMAC_SIZE>& output_hash) const;

    std::vector<uint32_t> direct_path(uint32_t leaf_index) const;
    std::vector<uint32_t> copath(uint32_t node_index) const;
};

} // namespace groups
} // namespace enchant

#endif

#include "groups/mls_tree_kem.hpp"
#include "primitives/x25519.hpp"
#include "primitives/hkdf.hpp"
#include "primitives/hmac.hpp"
#include "primitives/random.hpp"
#include "primitives/constant_time.hpp"
#include "secure/buffer.hpp"
#include <sodium.h>
#include <cstring>
#include <algorithm>

namespace enchant {
namespace groups {

MlsNode::MlsNode()
    : public_key{}, tree_hash{}, private_key(std::nullopt),
      is_leaf(false), leaf_index(0) {
    public_key.fill(0);
    tree_hash.fill(0);
}

MlsTreeKEM::MlsTreeKEM()
    : nodes_(), total_leaves_(0), current_epoch_(0), epoch_secret_{} {
    epoch_secret_.fill(0);
}

uint32_t MlsTreeKEM::leaf_to_node(uint32_t leaf_index) {
    return (1u << 8) + leaf_index;
}

uint32_t MlsTreeKEM::node_to_leaf(uint32_t node_index) {
    if (node_index < (1u << 8)) {
        return UINT32_MAX;
    }
    return node_index - (1u << 8);
}

uint32_t MlsTreeKEM::parent(uint32_t node_index) {
    return node_index >> 1;
}

uint32_t MlsTreeKEM::sibling(uint32_t node_index) {
    return node_index ^ 1;
}

uint32_t MlsTreeKEM::left_child(uint32_t node_index) {
    return node_index << 1;
}

uint32_t MlsTreeKEM::right_child(uint32_t node_index) {
    return (node_index << 1) | 1;
}

size_t MlsTreeKEM::level_offset(size_t level, size_t leaf_count) {
    (void)leaf_count;
    return (1u << level);
}

size_t MlsTreeKEM::tree_size(size_t leaf_count) {
    if (leaf_count == 0) return 0;
    size_t n = 1;
    while (n < leaf_count) n <<= 1;
    return n << 1;
}

size_t MlsTreeKEM::root_index(size_t leaf_count) {
    (void)leaf_count;
    return 1;
}

int MlsTreeKEM::derive_public_key(const std::array<uint8_t, 32>& private_key,
                                  std::array<uint8_t, MLS_TREE_KEM_NODE_SIZE>& public_key) const {
    uint8_t clamped[32];
    memcpy(clamped, private_key.data(), 32);
    clamped[0] &= 0xF8;
    clamped[31] &= 0x7F;
    clamped[31] |= 0x40;

    int rc = primitives::x25519_pubkey_from_priv(clamped, public_key.data());
    sodium_memzero(clamped, 32);
    if (rc != ENCHANT_SUCCESS) {
        public_key.fill(0);
        return rc;
    }
    return ENCHANT_SUCCESS;
}

int MlsTreeKEM::derive_private_key(const std::array<uint8_t, MLS_TREE_KEM_PATH_SECRET_SIZE>& path_secret,
                                    std::array<uint8_t, 32>& private_key) const {
    uint8_t hmac_input = 0x01;
    uint8_t output[32];
    int rc = primitives::hmac_sha256(path_secret.data(), path_secret.size(),
                                     &hmac_input, 1, output);
    if (rc != ENCHANT_SUCCESS) {
        sodium_memzero(output, sizeof(output));
        return rc;
    }
    memcpy(private_key.data(), output, 32);
    sodium_memzero(output, sizeof(output));
    return ENCHANT_SUCCESS;
}

int MlsTreeKEM::derive_path_secret(
    const std::array<uint8_t, MLS_TREE_KEM_PATH_SECRET_SIZE>& path_secret,
    uint32_t generation,
    std::array<uint8_t, MLS_TREE_KEM_PATH_SECRET_SIZE>& next_secret) {
    uint8_t salt[32] = {0};
    uint8_t generation_bytes[4];
    memcpy(generation_bytes, &generation, 4);

    const uint8_t info[] = "MLS_TREE_KEM_PATH_SECRET_DERIVE";
    int rc = primitives::hkdf_derive(
        path_secret.data(), path_secret.size(),
        salt, 32,
        info, sizeof(info) - 1,
        next_secret.data(), MLS_TREE_KEM_PATH_SECRET_SIZE);

    sodium_memzero(salt, sizeof(salt));
    (void)generation_bytes;
    return rc;
}

int MlsTreeKEM::derive_node_secret(
    const std::array<uint8_t, MLS_TREE_KEM_PATH_SECRET_SIZE>& path_secret,
    std::array<uint8_t, 32>& node_secret,
    std::array<uint8_t, MLS_TREE_KEM_NODE_SIZE>& public_key) {
    const uint8_t info[] = "MLS_TREE_KEM_NODE_SECRET";
    uint8_t derived[64];
    uint8_t salt[32] = {0};

    int rc = primitives::hkdf_derive(
        path_secret.data(), path_secret.size(),
        salt, 32,
        info, sizeof(info) - 1,
        derived, 64);

    sodium_memzero(salt, sizeof(salt));
    if (rc != ENCHANT_SUCCESS) {
        return rc;
    }

    memcpy(node_secret.data(), derived, 32);

    uint8_t clamped[32];
    memcpy(clamped, derived, 32);
    clamped[0] &= 0xF8;
    clamped[31] &= 0x7F;
    clamped[31] |= 0x40;
    rc = primitives::x25519_pubkey_from_priv(clamped, public_key.data());
    sodium_memzero(clamped, 32);
    sodium_memzero(derived, sizeof(derived));

    if (rc != ENCHANT_SUCCESS) {
        return rc;
    }
    return ENCHANT_SUCCESS;
}

int MlsTreeKEM::compute_node_hash(
    const std::array<uint8_t, MLS_TREE_KEM_HMAC_SIZE>& left_hash,
    const std::array<uint8_t, MLS_TREE_KEM_HMAC_SIZE>& right_hash,
    const std::array<uint8_t, MLS_TREE_KEM_NODE_SIZE>& public_key,
    std::array<uint8_t, MLS_TREE_KEM_HMAC_SIZE>& output_hash) {
    uint8_t key[MLS_TREE_KEM_HMAC_SIZE];
    memcpy(key, public_key.data(), MLS_TREE_KEM_HMAC_SIZE);

    crypto_auth_hmacsha256_state state;
    crypto_auth_hmacsha256_init(&state, key, MLS_TREE_KEM_HMAC_SIZE);
    crypto_auth_hmacsha256_update(&state, left_hash.data(), left_hash.size());
    crypto_auth_hmacsha256_update(&state, right_hash.data(), right_hash.size());
    crypto_auth_hmacsha256_final(&state, output_hash.data());

    sodium_memzero(key, sizeof(key));
    return ENCHANT_SUCCESS;
}

int MlsTreeKEM::initialize(const std::vector<std::array<uint8_t, 32>>& initial_leaf_secrets) {
    if (initial_leaf_secrets.empty() || initial_leaf_secrets.size() > MLS_TREE_KEM_MAX_MEMBERS) {
        return ENCHANT_ERROR_INVALID_KEY_SIZE;
    }

    size_t n_leaves = initial_leaf_secrets.size();
    nodes_.clear();
    nodes_.resize(MLS_TREE_KEM_MAX_MEMBERS * 2);
    total_leaves_ = n_leaves;

    for (size_t i = 0; i < nodes_.size(); i++) {
        nodes_[i].public_key.fill(0);
        nodes_[i].tree_hash.fill(0);
        nodes_[i].private_key.reset();
        nodes_[i].is_leaf = (i >= (1u << 8)) && node_to_leaf(i) < n_leaves;
        nodes_[i].leaf_index = nodes_[i].is_leaf ? node_to_leaf(i) : 0;
    }

    for (size_t i = 0; i < n_leaves; i++) {
        uint32_t node_idx = leaf_to_node(i);
        if (node_idx >= nodes_.size()) {
            continue;
        }
        std::array<uint8_t, 32> node_secret;
        int rc = derive_node_secret(initial_leaf_secrets[i], node_secret, nodes_[node_idx].public_key);
        if (rc != ENCHANT_SUCCESS) {
            return rc;
        }
        nodes_[node_idx].private_key = node_secret;
    }

    for (int level = 8; level >= 0; level--) {
        size_t start = level_offset(level, n_leaves);
        size_t count = 1u << level;
        for (size_t i = 0; i < count; i++) {
            uint32_t node_idx = start + i;
            if (node_idx >= nodes_.size()) continue;

            if (nodes_[node_idx].is_leaf) {
                std::array<uint8_t, MLS_TREE_KEM_HMAC_SIZE> leaf_hash;
                crypto_hash_sha256_state hash_state;
                crypto_hash_sha256_init(&hash_state);
                crypto_hash_sha256_update(&hash_state, nodes_[node_idx].public_key.data(),
                                          nodes_[node_idx].public_key.size());
                crypto_hash_sha256_final(&hash_state, leaf_hash.data());
                nodes_[node_idx].tree_hash = leaf_hash;
            } else {
                uint32_t left = left_child(node_idx);
                uint32_t right = right_child(node_idx);
                if (left < nodes_.size() && right < nodes_.size()) {
                    compute_node_hash(nodes_[left].tree_hash, nodes_[right].tree_hash,
                                      nodes_[node_idx].public_key, nodes_[node_idx].tree_hash);
                }
            }
        }
    }

    const uint8_t info[] = "MLS_TREE_KEM_INIT_GROUP_SECRET";
    uint8_t salt[32] = {0};
    size_t root_idx = root_index(n_leaves);
    if (root_idx >= nodes_.size()) {
        sodium_memzero(salt, sizeof(salt));
        return ENCHANT_ERROR_INTERNAL;
    }
    int rc = primitives::hkdf_derive(
        nodes_[root_idx].tree_hash.data(), nodes_[root_idx].tree_hash.size(),
        salt, 32,
        info, sizeof(info) - 1,
        epoch_secret_.data(), MLS_TREE_KEM_GROUP_SECRET_SIZE);
    sodium_memzero(salt, sizeof(salt));

    current_epoch_ = 0;
    return rc;
}

int MlsTreeKEM::add_member(const std::array<uint8_t, 32>& leaf_secret,
                           std::array<uint8_t, 32>& new_member_index) {
    if (total_leaves_ >= MLS_TREE_KEM_MAX_MEMBERS) {
        return ENCHANT_ERROR_BUFFER_TOO_SMALL;
    }

    size_t new_leaves = total_leaves_ + 1;

    uint32_t new_node = leaf_to_node(total_leaves_);
    if (new_node < nodes_.size()) {
        std::array<uint8_t, 32> node_secret;
        int rc = derive_node_secret(leaf_secret, node_secret, nodes_[new_node].public_key);
        if (rc != ENCHANT_SUCCESS) {
            return rc;
        }
        nodes_[new_node].private_key = node_secret;
        nodes_[new_node].is_leaf = true;
        nodes_[new_node].leaf_index = total_leaves_;
    }

    for (size_t i = leaf_to_node(0); i < nodes_.size() && node_to_leaf(i) < new_leaves; i++) {
        nodes_[i].is_leaf = (node_to_leaf(i) < new_leaves);
        nodes_[i].leaf_index = nodes_[i].is_leaf ? node_to_leaf(i) : 0;
    }

    total_leaves_ = new_leaves;

    for (int level = 8; level >= 0; level--) {
        size_t start = level_offset(level, new_leaves);
        size_t count = 1u << level;
        for (size_t i = 0; i < count; i++) {
            uint32_t node_idx = start + i;
            if (node_idx >= nodes_.size()) continue;

            if (nodes_[node_idx].is_leaf) {
                std::array<uint8_t, MLS_TREE_KEM_HMAC_SIZE> leaf_hash;
                crypto_hash_sha256_state hash_state;
                crypto_hash_sha256_init(&hash_state);
                crypto_hash_sha256_update(&hash_state, nodes_[node_idx].public_key.data(),
                                          nodes_[node_idx].public_key.size());
                crypto_hash_sha256_final(&hash_state, leaf_hash.data());
                nodes_[node_idx].tree_hash = leaf_hash;
            } else {
                uint32_t left = left_child(node_idx);
                uint32_t right = right_child(node_idx);
                if (left < nodes_.size() && right < nodes_.size()) {
                    compute_node_hash(nodes_[left].tree_hash, nodes_[right].tree_hash,
                                      nodes_[node_idx].public_key, nodes_[node_idx].tree_hash);
                }
            }
        }
    }

    const uint8_t info[] = "MLS_TREE_KEM_ADD_GROUP_SECRET";
    uint8_t salt[32] = {0};
    size_t root_idx = root_index(new_leaves);
    if (root_idx < nodes_.size()) {
        int rc = primitives::hkdf_derive(
            nodes_[root_idx].tree_hash.data(), nodes_[root_idx].tree_hash.size(),
            salt, 32,
            info, sizeof(info) - 1,
            epoch_secret_.data(), MLS_TREE_KEM_GROUP_SECRET_SIZE);
        sodium_memzero(salt, sizeof(salt));
        current_epoch_++;

        memcpy(new_member_index.data(), &total_leaves_, sizeof(uint32_t));
        for (size_t i = 4; i < 32; i++) {
            new_member_index[i] = 0;
        }
        return rc;
    }
    sodium_memzero(salt, sizeof(salt));
    return ENCHANT_ERROR_INTERNAL;
}

int MlsTreeKEM::remove_member(uint32_t leaf_index) {
    if (leaf_index >= total_leaves_ || total_leaves_ == 0) {
        return ENCHANT_ERROR_INVALID_KEY_SIZE;
    }

    uint32_t node_idx = leaf_to_node(leaf_index);
    if (node_idx < nodes_.size()) {
        nodes_[node_idx].public_key.fill(0);
        nodes_[node_idx].tree_hash.fill(0);
        nodes_[node_idx].private_key.reset();
    }

    for (int level = 8; level >= 0; level--) {
        size_t start = level_offset(level, total_leaves_);
        size_t count = 1u << level;
        for (size_t i = 0; i < count; i++) {
            uint32_t idx = start + i;
            if (idx >= nodes_.size()) continue;

            if (!nodes_[idx].is_leaf) {
                uint8_t zero_check = 0;
                for (size_t j = 0; j < nodes_[idx].public_key.size(); j++) {
                    zero_check |= nodes_[idx].public_key[j];
                }
                if (zero_check != 0) {
                    uint32_t left = left_child(idx);
                    uint32_t right = right_child(idx);
                    if (left < nodes_.size() && right < nodes_.size()) {
                        compute_node_hash(nodes_[left].tree_hash, nodes_[right].tree_hash,
                                          nodes_[idx].public_key, nodes_[idx].tree_hash);
                    }
                }
            }
        }
    }

    current_epoch_++;
    return ENCHANT_SUCCESS;
}

std::vector<uint32_t> MlsTreeKEM::direct_path(uint32_t leaf_index) const {
    std::vector<uint32_t> path;
    uint32_t node = leaf_to_node(leaf_index);
    while (node > root_index(total_leaves_)) {
        node = parent(node);
        path.push_back(node);
    }
    return path;
}

std::vector<uint32_t> MlsTreeKEM::copath(uint32_t node_index) const {
    std::vector<uint32_t> result;
    uint32_t current = node_index;
    while (current > root_index(total_leaves_)) {
        uint32_t s = sibling(current);
        result.push_back(s);
        current = parent(current);
    }
    std::reverse(result.begin(), result.end());
    return result;
}

int MlsTreeKEM::update_leaf_key(uint32_t leaf_index,
                                const std::array<uint8_t, 32>& new_secret,
                                MlsDirectPath& path) {
    if (leaf_index >= total_leaves_) {
        return ENCHANT_ERROR_INVALID_KEY_SIZE;
    }

    uint32_t leaf_node = leaf_to_node(leaf_index);
    if (leaf_node >= nodes_.size()) {
        return ENCHANT_ERROR_INVALID_KEY_SIZE;
    }

    std::array<uint8_t, 32> node_secret;
    int rc = derive_node_secret(new_secret, node_secret, nodes_[leaf_node].public_key);
    if (rc != ENCHANT_SUCCESS) {
        return rc;
    }
    nodes_[leaf_node].private_key = node_secret;

    std::array<uint8_t, MLS_TREE_KEM_PATH_SECRET_SIZE> current_secret = new_secret;
    auto dp = direct_path(leaf_index);

    path.nodes.clear();
    for (size_t i = 0; i < dp.size(); i++) {
        if (dp[i] >= nodes_.size()) continue;

        MlsUpdatePathNode path_node;
        path_node.node_index = dp[i];

        MlsTreeKEMPathSecret ps;
        ps.node_index = dp[i];
        ps.secret = current_secret;

        std::array<uint8_t, 32> parent_node_secret;
        int rc2 = derive_node_secret(current_secret, parent_node_secret, path_node.public_key);
        if (rc2 != ENCHANT_SUCCESS) {
            return rc2;
        }

        nodes_[dp[i]].public_key = path_node.public_key;
        nodes_[dp[i]].private_key = parent_node_secret;

        path_node.path_secrets.push_back(ps);
        path.nodes.push_back(path_node);

        std::array<uint8_t, MLS_TREE_KEM_PATH_SECRET_SIZE> next_secret;
        derive_path_secret(current_secret, i + 1, next_secret);
        current_secret = next_secret;
    }

    for (int level = 8; level >= 0; level--) {
        size_t start = level_offset(level, total_leaves_);
        size_t count = 1u << level;
        for (size_t i = 0; i < count; i++) {
            uint32_t idx = start + i;
            if (idx >= nodes_.size()) continue;

            if (nodes_[idx].is_leaf) {
                std::array<uint8_t, MLS_TREE_KEM_HMAC_SIZE> leaf_hash;
                crypto_hash_sha256_state hash_state;
                crypto_hash_sha256_init(&hash_state);
                crypto_hash_sha256_update(&hash_state, nodes_[idx].public_key.data(),
                                          nodes_[idx].public_key.size());
                crypto_hash_sha256_final(&hash_state, leaf_hash.data());
                nodes_[idx].tree_hash = leaf_hash;
            } else {
                uint32_t left = left_child(idx);
                uint32_t right = right_child(idx);
                if (left < nodes_.size() && right < nodes_.size()) {
                    compute_node_hash(nodes_[left].tree_hash, nodes_[right].tree_hash,
                                      nodes_[idx].public_key, nodes_[idx].tree_hash);
                }
            }
        }
    }

    current_epoch_++;
    return ENCHANT_SUCCESS;
}

int MlsTreeKEM::encrypt_path(const MlsDirectPath& path,
                             uint32_t sender_leaf_index,
                             std::array<uint8_t, MLS_TREE_KEM_GROUP_SECRET_SIZE>& group_secret) {
    (void)sender_leaf_index;

    if (path.nodes.empty()) {
        memcpy(group_secret.data(), epoch_secret_.data(), MLS_TREE_KEM_GROUP_SECRET_SIZE);
        return ENCHANT_SUCCESS;
    }

    std::array<uint8_t, 32> root_secret;
    for (const auto& path_node : path.nodes) {
        if (path_node.path_secrets.empty()) continue;
        const auto& ps = path_node.path_secrets[0];
        std::array<uint8_t, 32> ns;
        derive_node_secret(ps.secret, ns, root_secret);
        break;
    }

    const uint8_t info[] = "MLS_TREE_KEM_ENCRYPT_PATH";
    uint8_t salt[32] = {0};
    int rc = primitives::hkdf_derive(
        root_secret.data(), root_secret.size(),
        salt, 32,
        info, sizeof(info) - 1,
        group_secret.data(), MLS_TREE_KEM_GROUP_SECRET_SIZE);
    sodium_memzero(salt, sizeof(salt));
    sodium_memzero(root_secret.data(), root_secret.size());

    return rc;
}

int MlsTreeKEM::decrypt_path(const MlsDirectPath& path,
                             uint32_t receiver_leaf_index,
                             std::array<uint8_t, MLS_TREE_KEM_GROUP_SECRET_SIZE>& group_secret) {
    (void)receiver_leaf_index;

    if (path.nodes.empty()) {
        memcpy(group_secret.data(), epoch_secret_.data(), MLS_TREE_KEM_GROUP_SECRET_SIZE);
        return ENCHANT_SUCCESS;
    }

    std::array<uint8_t, 32> root_secret;
    for (const auto& path_node : path.nodes) {
        if (path_node.path_secrets.empty()) continue;
        const auto& ps = path_node.path_secrets[0];
        std::array<uint8_t, 32> ns;
        derive_node_secret(ps.secret, ns, root_secret);
        break;
    }

    const uint8_t info[] = "MLS_TREE_KEM_ENCRYPT_PATH";
    uint8_t salt[32] = {0};
    int rc = primitives::hkdf_derive(
        root_secret.data(), root_secret.size(),
        salt, 32,
        info, sizeof(info) - 1,
        group_secret.data(), MLS_TREE_KEM_GROUP_SECRET_SIZE);
    sodium_memzero(salt, sizeof(salt));
    sodium_memzero(root_secret.data(), root_secret.size());

    return rc;
}

int MlsTreeKEM::compute_tree_hash(std::array<uint8_t, MLS_TREE_KEM_HMAC_SIZE>& root_hash) const {
    if (total_leaves_ == 0) {
        root_hash.fill(0);
        return ENCHANT_ERROR_INVALID_KEY_SIZE;
    }
    size_t root_idx = root_index(total_leaves_);
    if (root_idx >= nodes_.size()) {
        root_hash.fill(0);
        return ENCHANT_ERROR_INVALID_KEY_SIZE;
    }
    root_hash = nodes_[root_idx].tree_hash;
    return ENCHANT_SUCCESS;
}

int MlsTreeKEM::get_node_public_key(uint32_t node_index,
                                    std::array<uint8_t, MLS_TREE_KEM_NODE_SIZE>& public_key) const {
    if (node_index >= nodes_.size()) {
        return ENCHANT_ERROR_INVALID_KEY_SIZE;
    }
    public_key = nodes_[node_index].public_key;
    return ENCHANT_SUCCESS;
}

int MlsTreeKEM::set_node_public_key(uint32_t node_index,
                                    const std::array<uint8_t, MLS_TREE_KEM_NODE_SIZE>& public_key) {
    if (node_index >= nodes_.size()) {
        return ENCHANT_ERROR_INVALID_KEY_SIZE;
    }
    nodes_[node_index].public_key = public_key;
    return ENCHANT_SUCCESS;
}

int MlsTreeKEM::set_node_private_key(uint32_t node_index,
                                     const std::array<uint8_t, 32>& private_key) {
    if (node_index >= nodes_.size()) {
        return ENCHANT_ERROR_INVALID_KEY_SIZE;
    }
    nodes_[node_index].private_key = private_key;
    return ENCHANT_SUCCESS;
}

int MlsTreeKEM::get_root_public_key(std::array<uint8_t, MLS_TREE_KEM_NODE_SIZE>& public_key) const {
    if (total_leaves_ == 0) {
        public_key.fill(0);
        return ENCHANT_ERROR_INVALID_KEY_SIZE;
    }
    size_t root_idx = root_index(total_leaves_);
    if (root_idx >= nodes_.size()) {
        public_key.fill(0);
        return ENCHANT_ERROR_INVALID_KEY_SIZE;
    }
    public_key = nodes_[root_idx].public_key;
    return ENCHANT_SUCCESS;
}

} // namespace groups
} // namespace enchant

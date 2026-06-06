#include "groups/key_transparency.hpp"
#include "primitives/hash.hpp"
#include "primitives/hmac.hpp"
#include "primitives/random.hpp"
#include "primitives/constant_time.hpp"
#include <sodium.h>
#include <cstring>
#include <algorithm>

namespace enchant {
namespace groups {

constexpr const char* KT_LEAF_LABEL = "EnchantKT_LeafHash_20240101";
constexpr const char* KT_INTERNAL_LABEL = "EnchantKT_InternalHash_20240101";

KeyTransparency::KeyTransparency() {}

int KeyTransparency::initialize() {
    return ENCHANT_SUCCESS;
}

size_t KeyTransparency::next_power_of_two(size_t n) {
    if (n == 0) return 1;
    size_t p = 1;
    while (p < n) p <<= 1;
    return p;
}

int KeyTransparency::compute_leaf_hash(const KtEntry& entry,
                                        std::array<uint8_t, KT_HASH_SIZE>& hash) {
    uint8_t key[KT_HASH_SIZE];
    memcpy(key, KT_LEAF_LABEL, std::min(strlen(KT_LEAF_LABEL), (size_t)KT_HASH_SIZE));
    memset(key + strlen(KT_LEAF_LABEL), 0, KT_HASH_SIZE - strlen(KT_LEAF_LABEL));

    crypto_auth_hmacsha256_state state;
    crypto_auth_hmacsha256_init(&state, key, KT_HASH_SIZE);
    crypto_auth_hmacsha256_update(&state, entry.key_id.data(), KT_KEY_SIZE);
    crypto_auth_hmacsha256_update(&state, entry.public_key.data(), KT_KEY_SIZE);
    uint8_t type_byte = static_cast<uint8_t>(entry.type);
    crypto_auth_hmacsha256_update(&state, &type_byte, 1);
    crypto_auth_hmacsha256_update(&state,
        reinterpret_cast<const uint8_t*>(&entry.timestamp), 8);
    crypto_auth_hmacsha256_final(&state, hash.data());

    sodium_memzero(key, sizeof(key));
    return ENCHANT_SUCCESS;
}

int KeyTransparency::compute_internal_hash(const std::array<uint8_t, KT_HASH_SIZE>& left,
                                            const std::array<uint8_t, KT_HASH_SIZE>& right,
                                            std::array<uint8_t, KT_HASH_SIZE>& hash) {
    uint8_t key[KT_HASH_SIZE];
    memcpy(key, KT_INTERNAL_LABEL, std::min(strlen(KT_INTERNAL_LABEL), (size_t)KT_HASH_SIZE));
    memset(key + strlen(KT_INTERNAL_LABEL), 0, KT_HASH_SIZE - strlen(KT_INTERNAL_LABEL));

    crypto_auth_hmacsha256_state state;
    crypto_auth_hmacsha256_init(&state, key, KT_HASH_SIZE);
    crypto_auth_hmacsha256_update(&state, left.data(), KT_HASH_SIZE);
    crypto_auth_hmacsha256_update(&state, right.data(), KT_HASH_SIZE);
    crypto_auth_hmacsha256_final(&state, hash.data());

    sodium_memzero(key, sizeof(key));
    return ENCHANT_SUCCESS;
}

int KeyTransparency::rebuild_tree(KtDirectoryState& state) const {
    if (state.entries.empty()) {
        state.leaf_hashes.clear();
        state.root_hash.fill(0);
        state.tree_size = 0;
        return ENCHANT_SUCCESS;
    }

    state.leaf_hashes.resize(state.entries.size());
    for (size_t i = 0; i < state.entries.size(); i++) {
        int rc = compute_leaf_hash(state.entries[i], state.leaf_hashes[i]);
        if (rc != ENCHANT_SUCCESS) return rc;
    }

    state.tree_size = state.entries.size();

    std::vector<std::array<uint8_t, KT_HASH_SIZE>> current_level = state.leaf_hashes;
    while (current_level.size() > 1) {
        std::vector<std::array<uint8_t, KT_HASH_SIZE>> next_level;
        for (size_t i = 0; i < current_level.size(); i += 2) {
            std::array<uint8_t, KT_HASH_SIZE> hash;
            if (i + 1 < current_level.size()) {
                int rc = compute_internal_hash(current_level[i], current_level[i + 1], hash);
                if (rc != ENCHANT_SUCCESS) return rc;
            } else {
                hash = current_level[i];
            }
            next_level.push_back(hash);
        }
        current_level = next_level;
    }

    state.root_hash = current_level[0];
    return ENCHANT_SUCCESS;
}

int KeyTransparency::add_entry(KtDirectoryState& state, const KtEntry& entry) {
    if (state.entries.size() >= KT_MAX_ENTRIES) return ENCHANT_ERROR_BUFFER_TOO_SMALL;

    for (const auto& existing : state.entries) {
        uint8_t cmp = 0;
        for (size_t i = 0; i < KT_KEY_SIZE; i++) {
            cmp |= existing.key_id[i] ^ entry.key_id[i];
        }
        if (cmp == 0) return ENCHANT_ERROR_INVALID_FORMAT;
    }

    state.entries.push_back(entry);
    state.version++;

    return rebuild_tree(state);
}

int KeyTransparency::update_entry(KtDirectoryState& state, const KtEntry& entry) {
    for (size_t i = 0; i < state.entries.size(); i++) {
        uint8_t cmp = 0;
        for (size_t j = 0; j < KT_KEY_SIZE; j++) {
            cmp |= state.entries[i].key_id[j] ^ entry.key_id[j];
        }
        if (cmp == 0) {
            state.entries[i] = entry;
            state.version++;
            return rebuild_tree(state);
        }
    }
    return ENCHANT_ERROR_INVALID_KEY_SIZE;
}

int KeyTransparency::remove_entry(KtDirectoryState& state,
                                   const std::array<uint8_t, KT_KEY_SIZE>& key_id) {
    for (auto it = state.entries.begin(); it != state.entries.end(); ++it) {
        uint8_t cmp = 0;
        for (size_t j = 0; j < KT_KEY_SIZE; j++) {
            cmp |= it->key_id[j] ^ key_id[j];
        }
        if (cmp == 0) {
            state.entries.erase(it);
            state.version++;
            return rebuild_tree(state);
        }
    }
    return ENCHANT_ERROR_INVALID_KEY_SIZE;
}

int KeyTransparency::generate_audit_proof(const KtDirectoryState& state,
                                           size_t leaf_index,
                                           KtAuditProof& proof) const {
    if (leaf_index >= state.leaf_hashes.size()) return ENCHANT_ERROR_OUT_OF_BOUNDS;

    proof.leaf_index = leaf_index;
    proof.root_hash = state.root_hash;
    proof.path.clear();

    size_t index = leaf_index;
    std::vector<std::array<uint8_t, KT_HASH_SIZE>> current_level = state.leaf_hashes;

    while (current_level.size() > 1) {
        bool is_right = (index & 1) != 0;
        size_t sibling_index = is_right ? (index - 1) : (index + 1);

        KtProofNode node;
        if (sibling_index < current_level.size()) {
            node.hash = current_level[sibling_index];
        } else {
            node.hash = current_level[index];
        }
        node.is_left = is_right;
        proof.path.push_back(node);

        std::vector<std::array<uint8_t, KT_HASH_SIZE>> next_level;
        for (size_t i = 0; i < current_level.size(); i += 2) {
            std::array<uint8_t, KT_HASH_SIZE> hash;
            if (i + 1 < current_level.size()) {
                compute_internal_hash(current_level[i], current_level[i + 1], hash);
            } else {
                hash = current_level[i];
            }
            next_level.push_back(hash);
        }

        current_level = next_level;
        index /= 2;
    }

    return ENCHANT_SUCCESS;
}

int KeyTransparency::verify_audit_proof(const KtAuditProof& proof,
                                         const KtEntry& entry,
                                         bool& valid) const {
    std::array<uint8_t, KT_HASH_SIZE> current_hash;
    int rc = compute_leaf_hash(entry, current_hash);
    if (rc != ENCHANT_SUCCESS) return rc;

    for (const auto& node : proof.path) {
        std::array<uint8_t, KT_HASH_SIZE> hash;
        if (node.is_left) {
            rc = compute_internal_hash(node.hash, current_hash, hash);
        } else {
            rc = compute_internal_hash(current_hash, node.hash, hash);
        }
        if (rc != ENCHANT_SUCCESS) return rc;
        current_hash = hash;
    }

    uint8_t cmp = 0;
    for (size_t i = 0; i < KT_HASH_SIZE; i++) {
        cmp |= current_hash[i] ^ proof.root_hash[i];
    }
    valid = (cmp == 0);

    return ENCHANT_SUCCESS;
}

int KeyTransparency::compute_root_hash(const KtDirectoryState& state,
                                         std::array<uint8_t, KT_HASH_SIZE>& root_hash) const {
    root_hash = state.root_hash;
    return ENCHANT_SUCCESS;
}

int KeyTransparency::get_entry(const KtDirectoryState& state,
                                const std::array<uint8_t, KT_KEY_SIZE>& key_id,
                                KtEntry& entry) const {
    for (const auto& e : state.entries) {
        uint8_t cmp = 0;
        for (size_t j = 0; j < KT_KEY_SIZE; j++) {
            cmp |= e.key_id[j] ^ key_id[j];
        }
        if (cmp == 0) {
            entry = e;
            return ENCHANT_SUCCESS;
        }
    }
    return ENCHANT_ERROR_INVALID_KEY_SIZE;
}

int KeyTransparency::get_entries_in_range(const KtDirectoryState& state,
                                           size_t start_index, size_t end_index,
                                           std::vector<KtEntry>& entries) const {
    if (start_index >= state.entries.size()) return ENCHANT_ERROR_OUT_OF_BOUNDS;
    if (end_index > state.entries.size()) end_index = state.entries.size();
    if (start_index >= end_index) return ENCHANT_ERROR_INVALID_FORMAT;

    entries.assign(state.entries.begin() + start_index,
                   state.entries.begin() + end_index);
    return ENCHANT_SUCCESS;
}

size_t KeyTransparency::get_tree_size(const KtDirectoryState& state) const {
    return state.entries.size();
}

int KeyTransparency::serialize_directory_state(const KtDirectoryState& state,
                                                std::vector<uint8_t>& output) const {
    size_t total_size = 8 + 4 + KT_HASH_SIZE + 4 +
                        state.entries.size() * (KT_KEY_SIZE + KT_KEY_SIZE + 1 + 8);

    output.resize(total_size);
    size_t offset = 0;

    memcpy(output.data() + offset, &state.version, 8);
    offset += 8;

    uint32_t entry_count = static_cast<uint32_t>(state.entries.size());
    memcpy(output.data() + offset, &entry_count, 4);
    offset += 4;

    memcpy(output.data() + offset, state.root_hash.data(), KT_HASH_SIZE);
    offset += KT_HASH_SIZE;

    uint32_t tree_size32 = static_cast<uint32_t>(state.tree_size);
    memcpy(output.data() + offset, &tree_size32, 4);
    offset += 4;

    for (const auto& entry : state.entries) {
        memcpy(output.data() + offset, entry.key_id.data(), KT_KEY_SIZE);
        offset += KT_KEY_SIZE;
        memcpy(output.data() + offset, entry.public_key.data(), KT_KEY_SIZE);
        offset += KT_KEY_SIZE;
        output[offset] = static_cast<uint8_t>(entry.type);
        offset += 1;
        memcpy(output.data() + offset, &entry.timestamp, 8);
        offset += 8;
    }

    output.resize(offset);
    return ENCHANT_SUCCESS;
}

int KeyTransparency::deserialize_directory_state(KtDirectoryState& state,
                                                  const uint8_t* input, size_t input_len) {
    if (!input) return ENCHANT_ERROR_NULL_POINTER;
    if (input_len < 8 + 4 + KT_HASH_SIZE + 4) return ENCHANT_ERROR_CIPHERTEXT_TOO_SHORT;

    size_t offset = 0;

    memcpy(&state.version, input + offset, 8);
    offset += 8;

    uint32_t entry_count;
    memcpy(&entry_count, input + offset, 4);
    offset += 4;

    memcpy(state.root_hash.data(), input + offset, KT_HASH_SIZE);
    offset += KT_HASH_SIZE;

    uint32_t tree_size32;
    memcpy(&tree_size32, input + offset, 4);
    offset += 4;
    state.tree_size = tree_size32;

    size_t expected = 8 + 4 + KT_HASH_SIZE + 4 + entry_count * (KT_KEY_SIZE + KT_KEY_SIZE + 1 + 8);
    if (input_len < expected) return ENCHANT_ERROR_CIPHERTEXT_TOO_SHORT;

    state.entries.clear();
    for (uint32_t i = 0; i < entry_count; i++) {
        KtEntry entry;
        memcpy(entry.key_id.data(), input + offset, KT_KEY_SIZE);
        offset += KT_KEY_SIZE;
        memcpy(entry.public_key.data(), input + offset, KT_KEY_SIZE);
        offset += KT_KEY_SIZE;
        entry.type = static_cast<KtEntryType>(input[offset]);
        offset += 1;
        memcpy(&entry.timestamp, input + offset, 8);
        offset += 8;
        state.entries.push_back(entry);
    }

    return rebuild_tree(state);
}

} // namespace groups
} // namespace enchant

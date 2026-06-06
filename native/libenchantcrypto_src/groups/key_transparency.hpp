#ifndef ENCHANT_GROUPS_KEY_TRANSPARENCY_HPP
#define ENCHANT_GROUPS_KEY_TRANSPARENCY_HPP

#include <cstdint>
#include <cstddef>
#include <vector>
#include <array>
#include <optional>
#include <map>
#include "enchant/error.h"

namespace enchant {
namespace groups {

constexpr size_t KT_HASH_SIZE = 32;
constexpr size_t KT_KEY_SIZE = 32;
constexpr size_t KT_PROOF_MAX_DEPTH = 32;
constexpr size_t KT_MAX_ENTRIES = 1u << 20;

enum class KtEntryType : uint8_t {
    IDENTITY_KEY = 1,
    SIGNED_PREKEY = 2,
    ONE_TIME_PREKEY = 3
};

struct KtEntry {
    std::array<uint8_t, KT_KEY_SIZE> key_id;
    std::array<uint8_t, KT_KEY_SIZE> public_key;
    KtEntryType type;
    uint64_t timestamp;
};

struct KtProofNode {
    std::array<uint8_t, KT_HASH_SIZE> hash;
    bool is_left;
};

struct KtAuditProof {
    std::vector<KtProofNode> path;
    std::array<uint8_t, KT_HASH_SIZE> root_hash;
    size_t leaf_index;
};

struct KtDirectoryState {
    std::vector<KtEntry> entries;
    std::vector<std::array<uint8_t, KT_HASH_SIZE>> leaf_hashes;
    std::array<uint8_t, KT_HASH_SIZE> root_hash;
    uint64_t version;
    size_t tree_size;
};

class KeyTransparency {
public:
    KeyTransparency();

    int initialize();

    int add_entry(KtDirectoryState& state, const KtEntry& entry);

    int update_entry(KtDirectoryState& state, const KtEntry& entry);

    int remove_entry(KtDirectoryState& state,
                     const std::array<uint8_t, KT_KEY_SIZE>& key_id);

    int generate_audit_proof(const KtDirectoryState& state,
                             size_t leaf_index,
                             KtAuditProof& proof) const;

    int verify_audit_proof(const KtAuditProof& proof,
                           const KtEntry& entry,
                           bool& valid) const;

    int compute_root_hash(const KtDirectoryState& state,
                          std::array<uint8_t, KT_HASH_SIZE>& root_hash) const;

    int get_entry(const KtDirectoryState& state,
                  const std::array<uint8_t, KT_KEY_SIZE>& key_id,
                  KtEntry& entry) const;

    int get_entries_in_range(const KtDirectoryState& state,
                             size_t start_index, size_t end_index,
                             std::vector<KtEntry>& entries) const;

    size_t get_tree_size(const KtDirectoryState& state) const;

    int serialize_directory_state(const KtDirectoryState& state,
                                  std::vector<uint8_t>& output) const;

    int deserialize_directory_state(KtDirectoryState& state,
                                    const uint8_t* input, size_t input_len);

private:
    static int compute_leaf_hash(const KtEntry& entry,
                                 std::array<uint8_t, KT_HASH_SIZE>& hash);

    static int compute_internal_hash(const std::array<uint8_t, KT_HASH_SIZE>& left,
                                     const std::array<uint8_t, KT_HASH_SIZE>& right,
                                     std::array<uint8_t, KT_HASH_SIZE>& hash);

    static size_t next_power_of_two(size_t n);

    int rebuild_tree(KtDirectoryState& state) const;
};

} // namespace groups
} // namespace enchant

#endif

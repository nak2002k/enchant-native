#ifndef ENCHANT_PROTOCOL_PREKEY_HPP
#define ENCHANT_PROTOCOL_PREKEY_HPP

#include <cstdint>
#include <cstddef>
#include <string>
#include <vector>
#include <optional>
#include "enchant/error.h"
#include "secure/buffer.hpp"
#include "protocol/x3dh.hpp"

namespace enchant {
namespace protocol {

constexpr size_t PREKEY_BATCH_SIZE = 100;
constexpr size_t PREKEY_TOPUP_THRESHOLD = 10;
constexpr size_t PREKEY_SPK_ROTATION_DAYS = 25;
constexpr uint64_t PREKEY_DEFAULT_EXPIRY_MS = 7 * 24 * 60 * 60 * 1000ULL;

struct PreKeyId {
    uint32_t id;

    PreKeyId() : id(0) {}
    explicit PreKeyId(uint32_t v) : id(v) {}

    bool operator==(const PreKeyId& other) const { return id == other.id; }
    bool operator!=(const PreKeyId& other) const { return id != other.id; }
    bool operator<(const PreKeyId& other) const { return id < other.id; }
};

struct SignedPreKeyId {
    uint32_t id;

    SignedPreKeyId() : id(0) {}
    explicit SignedPreKeyId(uint32_t v) : id(v) {}

    bool operator==(const SignedPreKeyId& other) const { return id == other.id; }
    bool operator!=(const SignedPreKeyId& other) const { return id != other.id; }
};

struct KyberPreKeyId {
    uint32_t id;

    KyberPreKeyId() : id(0) {}
    explicit KyberPreKeyId(uint32_t v) : id(v) {}

    bool operator==(const KyberPreKeyId& other) const { return id == other.id; }
    bool operator!=(const KyberPreKeyId& other) const { return id != other.id; }
};

struct PreKeyRecord {
    PreKeyId id;
    secure::SecureBuffer public_key{32};
    secure::SecureBuffer private_key{32};
    bool is_last_resort = false;
};

struct SignedPreKeyRecord {
    SignedPreKeyId id;
    secure::SecureBuffer public_key{32};
    secure::SecureBuffer private_key{32};
    secure::SecureBuffer signature{64};
};

struct KyberPreKeyRecord {
    KyberPreKeyId id;
    secure::SecureBuffer public_key{ML_KEM_768_PUBLIC_KEY_SIZE};
    secure::SecureBuffer private_key{ML_KEM_768_SECRET_KEY_SIZE};
    secure::SecureBuffer signature{64};
};

struct KyberPreKeyRecord1024 {
    KyberPreKeyId id;
    secure::SecureBuffer public_key{ML_KEM_1024_PUBLIC_KEY_SIZE};
    secure::SecureBuffer private_key{ML_KEM_1024_SECRET_KEY_SIZE};
    secure::SecureBuffer signature{64};
};

struct PreKeyMetadata {
    uint64_t created_at = 0;
    uint64_t expires_at = 0;
    uint32_t registration_id = 0;
};

struct WrappedPreKey {
    PreKeyRecord record;
    PreKeyMetadata metadata;
};

class PreKeyStore {
public:
    PreKeyStore();
    ~PreKeyStore();

    int generate_batch(uint32_t start_id);
    int consume(PreKeyId id, PreKeyRecord& out);
    int get_count() const;
    int top_up();

    int generate_signed_prekey(SignedPreKeyId id, const uint8_t* signing_private);
    int get_signed_prekey(SignedPreKeyRecord& out) const;
    int rotate_signed_prekey(SignedPreKeyId new_id, const uint8_t* signing_private);

    int get_last_resort(PreKeyRecord& out) const;

    int wrap_prekey(const PreKeyRecord& record, uint32_t registration_id, WrappedPreKey& out);
    int unwrap_prekey(PreKeyId id, PreKeyRecord& out, PreKeyMetadata& metadata);
    bool is_expired(const WrappedPreKey& wrapped) const;

private:
    std::vector<WrappedPreKey> opks_;
    SignedPreKeyRecord spk_;
    bool spk_generated_;
    PreKeyRecord last_resort_;
    uint32_t next_id_;
};

class KyberPreKeyStore {
public:
    KyberPreKeyStore();
    ~KyberPreKeyStore();

    int generate_batch(uint32_t start_id);
    int consume(KyberPreKeyId id, KyberPreKeyRecord& out);
    int get_count() const;
    int top_up();

    int get_latest(KyberPreKeyRecord& out) const;

    int serialize(const std::vector<KyberPreKeyRecord>& keys, uint8_t* output, size_t* output_len);
    int deserialize(std::vector<KyberPreKeyRecord>& keys, const uint8_t* input, size_t input_len);

private:
    std::vector<KyberPreKeyRecord> keys_;
    uint32_t next_id_;
};

class KyberPreKeyStore1024 {
public:
    KyberPreKeyStore1024();
    ~KyberPreKeyStore1024();

    int generate_batch(uint32_t start_id);
    int consume(KyberPreKeyId id, KyberPreKeyRecord1024& out);
    int get_count() const;
    int top_up();

    int get_latest(KyberPreKeyRecord1024& out) const;

    int serialize(const std::vector<KyberPreKeyRecord1024>& keys, uint8_t* output, size_t* output_len);
    int deserialize(std::vector<KyberPreKeyRecord1024>& keys, const uint8_t* input, size_t input_len);

private:
    std::vector<KyberPreKeyRecord1024> keys_;
    uint32_t next_id_;
};

int prekey_record_serialize(const PreKeyRecord& record, uint8_t* output, size_t* output_len);
int prekey_record_deserialize(PreKeyRecord& record, const uint8_t* input, size_t input_len);

int signed_prekey_record_serialize(const SignedPreKeyRecord& record, uint8_t* output, size_t* output_len);
int signed_prekey_record_deserialize(SignedPreKeyRecord& record, const uint8_t* input, size_t input_len);

} // namespace protocol
} // namespace enchant

#endif
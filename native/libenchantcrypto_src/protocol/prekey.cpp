#include "protocol/prekey.hpp"
#include "proto/protobuf_serializer.hpp"
#include "primitives/ed25519.hpp"
#include "primitives/x25519.hpp"
#include "primitives/random.hpp"
#include "pq/ml_kem.hpp"
#include <cstring>
#include <chrono>

namespace enchant {
namespace protocol {

PreKeyStore::PreKeyStore() : opks_(), spk_(), spk_generated_(false), last_resort_(), next_id_(1) {
    last_resort_.id = PreKeyId(0);
    last_resort_.is_last_resort = true;
    primitives::x25519_keypair(last_resort_.public_key.data(), last_resort_.private_key.data());
}

PreKeyStore::~PreKeyStore() {}

int PreKeyStore::generate_batch(uint32_t start_id) {
    next_id_ = start_id;
    opks_.clear();
    opks_.reserve(PREKEY_BATCH_SIZE);

    for (size_t i = 0; i < PREKEY_BATCH_SIZE; i++) {
        WrappedPreKey wrapped;
        wrapped.record.id = PreKeyId(next_id_++);
        wrapped.record.is_last_resort = false;

        int rc = primitives::x25519_keypair(
            wrapped.record.public_key.data(), wrapped.record.private_key.data()
        );
        if (rc != ENCHANT_SUCCESS) return rc;

        wrapped.metadata.created_at = 0;
        auto now_ms = static_cast<uint64_t>(
            std::chrono::duration_cast<std::chrono::milliseconds>(
                std::chrono::system_clock::now().time_since_epoch()
            ).count());
        wrapped.metadata.expires_at = now_ms + PREKEY_DEFAULT_EXPIRY_MS;
        wrapped.metadata.registration_id = 0;

        opks_.push_back(std::move(wrapped));
    }
    return ENCHANT_SUCCESS;
}

int PreKeyStore::consume(PreKeyId id, PreKeyRecord& out) {
    for (auto it = opks_.begin(); it != opks_.end(); ++it) {
        if (it->record.id == id) {
            out.id = it->record.id;
            out.public_key = secure::SecureBuffer(it->record.public_key.data(), it->record.public_key.size());
            out.private_key = secure::SecureBuffer(it->record.private_key.data(), it->record.private_key.size());
            out.is_last_resort = it->record.is_last_resort;
            opks_.erase(it);
            return ENCHANT_SUCCESS;
        }
    }
    return ENCHANT_ERROR_PREKEY_NOT_FOUND;
}

int PreKeyStore::get_count() const {
    return static_cast<int>(opks_.size());
}

int PreKeyStore::top_up() {
    if (get_count() < static_cast<int>(PREKEY_TOPUP_THRESHOLD)) {
        return generate_batch(next_id_);
    }
    return ENCHANT_SUCCESS;
}

int PreKeyStore::generate_signed_prekey(SignedPreKeyId id, const uint8_t* signing_private) {
    if (!signing_private) return ENCHANT_ERROR_NULL_POINTER;

    spk_.id = id;
    spk_generated_ = true;

    int rc = primitives::x25519_keypair(spk_.public_key.data(), spk_.private_key.data());
    if (rc != ENCHANT_SUCCESS) return rc;

    return primitives::ed25519_sign(
        spk_.public_key.data(), 32, signing_private, spk_.signature.data()
    );
}

int PreKeyStore::get_signed_prekey(SignedPreKeyRecord& out) const {
    if (!spk_generated_) return ENCHANT_ERROR_KEY_EXPIRED;
    out.id = spk_.id;
    out.public_key = secure::SecureBuffer(spk_.public_key.data(), spk_.public_key.size());
    out.private_key = secure::SecureBuffer(spk_.private_key.data(), spk_.private_key.size());
    out.signature = secure::SecureBuffer(spk_.signature.data(), spk_.signature.size());
    return ENCHANT_SUCCESS;
}

int PreKeyStore::rotate_signed_prekey(SignedPreKeyId new_id, const uint8_t* signing_private) {
    return generate_signed_prekey(new_id, signing_private);
}

int PreKeyStore::get_last_resort(PreKeyRecord& out) const {
    out.id = last_resort_.id;
    out.public_key = secure::SecureBuffer(last_resort_.public_key.data(), last_resort_.public_key.size());
    out.private_key = secure::SecureBuffer(last_resort_.private_key.data(), last_resort_.private_key.size());
    out.is_last_resort = last_resort_.is_last_resort;
    return ENCHANT_SUCCESS;
}

int PreKeyStore::wrap_prekey(const PreKeyRecord& record, uint32_t registration_id, WrappedPreKey& out) {
    out.record.id = record.id;
    out.record.public_key = secure::SecureBuffer(record.public_key.data(), record.public_key.size());
    out.record.private_key = secure::SecureBuffer(record.private_key.data(), record.private_key.size());
    out.record.is_last_resort = record.is_last_resort;
    out.metadata.created_at = 0;
    auto now_ms = static_cast<uint64_t>(
        std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::system_clock::now().time_since_epoch()
        ).count());
    out.metadata.expires_at = now_ms + PREKEY_DEFAULT_EXPIRY_MS;
    out.metadata.registration_id = registration_id;
    return ENCHANT_SUCCESS;
}

int PreKeyStore::unwrap_prekey(PreKeyId id, PreKeyRecord& out, PreKeyMetadata& metadata) {
    for (auto it = opks_.begin(); it != opks_.end(); ++it) {
        if (it->record.id == id) {
            out.id = it->record.id;
            out.public_key = secure::SecureBuffer(it->record.public_key.data(), it->record.public_key.size());
            out.private_key = secure::SecureBuffer(it->record.private_key.data(), it->record.private_key.size());
            out.is_last_resort = it->record.is_last_resort;
            metadata = it->metadata;
            opks_.erase(it);
            return ENCHANT_SUCCESS;
        }
    }
    return ENCHANT_ERROR_PREKEY_NOT_FOUND;
}

bool PreKeyStore::is_expired(const WrappedPreKey& wrapped) const {
    if (wrapped.metadata.expires_at == 0) return false;
    auto now = std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::system_clock::now().time_since_epoch()
    ).count();
    return static_cast<uint64_t>(now) >= wrapped.metadata.expires_at;
}

KyberPreKeyStore::KyberPreKeyStore() : next_id_(1) {}

KyberPreKeyStore::~KyberPreKeyStore() {}

int KyberPreKeyStore::generate_batch(uint32_t start_id) {
    next_id_ = start_id;
    keys_.clear();
    keys_.reserve(PREKEY_BATCH_SIZE);

    for (size_t i = 0; i < PREKEY_BATCH_SIZE; i++) {
        KyberPreKeyRecord record;
        record.id = KyberPreKeyId(next_id_++);

        int rc = pq::ml_kem_768_keypair(record.public_key.data(), record.private_key.data());
        if (rc != ENCHANT_SUCCESS) return rc;

        keys_.push_back(std::move(record));
    }
    return ENCHANT_SUCCESS;
}

int KyberPreKeyStore::consume(KyberPreKeyId id, KyberPreKeyRecord& out) {
    for (auto it = keys_.begin(); it != keys_.end(); ++it) {
        if (it->id == id) {
            out.id = it->id;
            out.public_key = secure::SecureBuffer(it->public_key.data(), it->public_key.size());
            out.private_key = secure::SecureBuffer(it->private_key.data(), it->private_key.size());
            out.signature = secure::SecureBuffer(it->signature.data(), it->signature.size());
            keys_.erase(it);
            return ENCHANT_SUCCESS;
        }
    }
    return ENCHANT_ERROR_PREKEY_NOT_FOUND;
}

int KyberPreKeyStore::get_count() const {
    return static_cast<int>(keys_.size());
}

int KyberPreKeyStore::top_up() {
    if (get_count() < static_cast<int>(PREKEY_TOPUP_THRESHOLD)) {
        return generate_batch(next_id_);
    }
    return ENCHANT_SUCCESS;
}

int KyberPreKeyStore::get_latest(KyberPreKeyRecord& out) const {
    if (keys_.empty()) return ENCHANT_ERROR_KEY_EXPIRED;
    const auto& latest = keys_.back();
    out.id = latest.id;
    out.public_key = secure::SecureBuffer(latest.public_key.data(), latest.public_key.size());
    out.private_key = secure::SecureBuffer(latest.private_key.data(), latest.private_key.size());
    out.signature = secure::SecureBuffer(latest.signature.data(), latest.signature.size());
    return ENCHANT_SUCCESS;
}

int KyberPreKeyStore::serialize(const std::vector<KyberPreKeyRecord>& keys, uint8_t* output, size_t* output_len) {
    if (!output || !output_len) return ENCHANT_ERROR_NULL_POINTER;

    size_t needed = 4 + keys.size() * (4 + ML_KEM_768_PUBLIC_KEY_SIZE + ML_KEM_768_SECRET_KEY_SIZE + 64);
    if (*output_len < needed) {
        *output_len = needed;
        return ENCHANT_ERROR_BUFFER_TOO_SMALL;
    }

    size_t offset = 0;
    uint32_t num_keys_768 = static_cast<uint32_t>(keys.size());
    output[offset++] = (num_keys_768 >> 24) & 0xFF;
    output[offset++] = (num_keys_768 >> 16) & 0xFF;
    output[offset++] = (num_keys_768 >> 8) & 0xFF;
    output[offset++] = num_keys_768 & 0xFF;

    for (const auto& key : keys) {
        memcpy(output + offset, &key.id.id, 4);
        offset += 4;
        memcpy(output + offset, key.public_key.data(), ML_KEM_768_PUBLIC_KEY_SIZE);
        offset += ML_KEM_768_PUBLIC_KEY_SIZE;
        memcpy(output + offset, key.private_key.data(), ML_KEM_768_SECRET_KEY_SIZE);
        offset += ML_KEM_768_SECRET_KEY_SIZE;
        memcpy(output + offset, key.signature.data(), 64);
        offset += 64;
    }

    *output_len = offset;
    return ENCHANT_SUCCESS;
}

int KyberPreKeyStore::deserialize(std::vector<KyberPreKeyRecord>& keys, const uint8_t* input, size_t input_len) {
    if (!input) return ENCHANT_ERROR_NULL_POINTER;
    if (input_len < 4) return ENCHANT_ERROR_INVALID_FORMAT;

    size_t offset = 0;
    size_t num_keys = (static_cast<size_t>(input[offset]) << 24) |
                      (static_cast<size_t>(input[offset + 1]) << 16) |
                      (static_cast<size_t>(input[offset + 2]) << 8) |
                      static_cast<size_t>(input[offset + 3]);
    offset += 4;

    if (num_keys > 10000) return ENCHANT_ERROR_INVALID_FORMAT;

    keys.clear();
    keys.reserve(num_keys);

    for (size_t i = 0; i < num_keys; i++) {
        if (input_len < offset + 4 + ML_KEM_768_PUBLIC_KEY_SIZE + ML_KEM_768_SECRET_KEY_SIZE + 64) return ENCHANT_ERROR_INVALID_FORMAT;

        KyberPreKeyRecord record;
        record.id.id = (static_cast<uint32_t>(input[offset]) << 24) |
                       (static_cast<uint32_t>(input[offset + 1]) << 16) |
                       (static_cast<uint32_t>(input[offset + 2]) << 8) |
                       static_cast<uint32_t>(input[offset + 3]);
        offset += 4;
        record.public_key = secure::SecureBuffer(input + offset, ML_KEM_768_PUBLIC_KEY_SIZE);
        offset += ML_KEM_768_PUBLIC_KEY_SIZE;
        record.private_key = secure::SecureBuffer(input + offset, ML_KEM_768_SECRET_KEY_SIZE);
        offset += ML_KEM_768_SECRET_KEY_SIZE;
        record.signature = secure::SecureBuffer(input + offset, 64);
        offset += 64;

        keys.push_back(std::move(record));
    }

    return ENCHANT_SUCCESS;
}

KyberPreKeyStore1024::KyberPreKeyStore1024() : next_id_(1) {}

KyberPreKeyStore1024::~KyberPreKeyStore1024() {}

int KyberPreKeyStore1024::generate_batch(uint32_t start_id) {
    next_id_ = start_id;
    keys_.clear();
    keys_.reserve(PREKEY_BATCH_SIZE);

    for (size_t i = 0; i < PREKEY_BATCH_SIZE; i++) {
        KyberPreKeyRecord1024 record;
        record.id = KyberPreKeyId(next_id_++);

        int rc = pq::ml_kem_1024_keypair(record.public_key.data(), record.private_key.data());
        if (rc != ENCHANT_SUCCESS) return rc;

        keys_.push_back(std::move(record));
    }
    return ENCHANT_SUCCESS;
}

int KyberPreKeyStore1024::consume(KyberPreKeyId id, KyberPreKeyRecord1024& out) {
    for (auto it = keys_.begin(); it != keys_.end(); ++it) {
        if (it->id == id) {
            out.id = it->id;
            out.public_key = secure::SecureBuffer(it->public_key.data(), it->public_key.size());
            out.private_key = secure::SecureBuffer(it->private_key.data(), it->private_key.size());
            out.signature = secure::SecureBuffer(it->signature.data(), it->signature.size());
            keys_.erase(it);
            return ENCHANT_SUCCESS;
        }
    }
    return ENCHANT_ERROR_PREKEY_NOT_FOUND;
}

int KyberPreKeyStore1024::get_count() const {
    return static_cast<int>(keys_.size());
}

int KyberPreKeyStore1024::top_up() {
    if (get_count() < static_cast<int>(PREKEY_TOPUP_THRESHOLD)) {
        return generate_batch(next_id_);
    }
    return ENCHANT_SUCCESS;
}

int KyberPreKeyStore1024::get_latest(KyberPreKeyRecord1024& out) const {
    if (keys_.empty()) return ENCHANT_ERROR_KEY_EXPIRED;
    const auto& latest = keys_.back();
    out.id = latest.id;
    out.public_key = secure::SecureBuffer(latest.public_key.data(), latest.public_key.size());
    out.private_key = secure::SecureBuffer(latest.private_key.data(), latest.private_key.size());
    out.signature = secure::SecureBuffer(latest.signature.data(), latest.signature.size());
    return ENCHANT_SUCCESS;
}

int KyberPreKeyStore1024::serialize(const std::vector<KyberPreKeyRecord1024>& keys, uint8_t* output, size_t* output_len) {
    if (!output || !output_len) return ENCHANT_ERROR_NULL_POINTER;

    size_t needed = 4 + keys.size() * (4 + ML_KEM_1024_PUBLIC_KEY_SIZE + ML_KEM_1024_SECRET_KEY_SIZE + 64);
    if (*output_len < needed) {
        *output_len = needed;
        return ENCHANT_ERROR_BUFFER_TOO_SMALL;
    }

    size_t offset = 0;
    uint32_t num_keys_1024 = static_cast<uint32_t>(keys.size());
    output[offset++] = (num_keys_1024 >> 24) & 0xFF;
    output[offset++] = (num_keys_1024 >> 16) & 0xFF;
    output[offset++] = (num_keys_1024 >> 8) & 0xFF;
    output[offset++] = num_keys_1024 & 0xFF;

    for (const auto& key : keys) {
        memcpy(output + offset, &key.id.id, 4);
        offset += 4;
        memcpy(output + offset, key.public_key.data(), ML_KEM_1024_PUBLIC_KEY_SIZE);
        offset += ML_KEM_1024_PUBLIC_KEY_SIZE;
        memcpy(output + offset, key.private_key.data(), ML_KEM_1024_SECRET_KEY_SIZE);
        offset += ML_KEM_1024_SECRET_KEY_SIZE;
        memcpy(output + offset, key.signature.data(), 64);
        offset += 64;
    }

    *output_len = offset;
    return ENCHANT_SUCCESS;
}

int KyberPreKeyStore1024::deserialize(std::vector<KyberPreKeyRecord1024>& keys, const uint8_t* input, size_t input_len) {
    if (!input) return ENCHANT_ERROR_NULL_POINTER;
    if (input_len < 4) return ENCHANT_ERROR_INVALID_FORMAT;

    size_t offset = 0;
    size_t num_keys = (static_cast<size_t>(input[offset]) << 24) |
                      (static_cast<size_t>(input[offset + 1]) << 16) |
                      (static_cast<size_t>(input[offset + 2]) << 8) |
                      static_cast<size_t>(input[offset + 3]);
    offset += 4;

    if (num_keys > 10000) return ENCHANT_ERROR_INVALID_FORMAT;

    keys.clear();
    keys.reserve(num_keys);

    for (size_t i = 0; i < num_keys; i++) {
        if (input_len < offset + 4 + ML_KEM_1024_PUBLIC_KEY_SIZE + ML_KEM_1024_SECRET_KEY_SIZE + 64) return ENCHANT_ERROR_INVALID_FORMAT;

        KyberPreKeyRecord1024 record;
        record.id.id = (static_cast<uint32_t>(input[offset]) << 24) |
                       (static_cast<uint32_t>(input[offset + 1]) << 16) |
                       (static_cast<uint32_t>(input[offset + 2]) << 8) |
                       static_cast<uint32_t>(input[offset + 3]);
        offset += 4;
        record.public_key = secure::SecureBuffer(input + offset, ML_KEM_1024_PUBLIC_KEY_SIZE);
        offset += ML_KEM_1024_PUBLIC_KEY_SIZE;
        record.private_key = secure::SecureBuffer(input + offset, ML_KEM_1024_SECRET_KEY_SIZE);
        offset += ML_KEM_1024_SECRET_KEY_SIZE;
        record.signature = secure::SecureBuffer(input + offset, 64);
        offset += 64;

        keys.push_back(std::move(record));
    }

    return ENCHANT_SUCCESS;
}

int prekey_record_serialize(const PreKeyRecord& record, uint8_t* output, size_t* output_len) {
    if (!output || !output_len) return ENCHANT_ERROR_NULL_POINTER;

    std::vector<uint8_t> proto_output;
    int rc = proto::ProtobufSerializer::serialize_pre_key_record(record, proto_output);
    if (rc != ENCHANT_SUCCESS) return rc;

    if (proto_output.size() > *output_len) {
        *output_len = proto_output.size();
        return ENCHANT_ERROR_BUFFER_TOO_SMALL;
    }

    memcpy(output, proto_output.data(), proto_output.size());
    *output_len = proto_output.size();
    return ENCHANT_SUCCESS;
}

int prekey_record_deserialize(PreKeyRecord& record, const uint8_t* input, size_t input_len) {
    if (!input) return ENCHANT_ERROR_NULL_POINTER;
    return proto::ProtobufSerializer::deserialize_pre_key_record(input, input_len, record);
}

int signed_prekey_record_serialize(const SignedPreKeyRecord& record, uint8_t* output, size_t* output_len) {
    if (!output || !output_len) return ENCHANT_ERROR_NULL_POINTER;

    std::vector<uint8_t> proto_output;
    int rc = proto::ProtobufSerializer::serialize_signed_pre_key_record(record, proto_output);
    if (rc != ENCHANT_SUCCESS) return rc;

    if (proto_output.size() > *output_len) {
        *output_len = proto_output.size();
        return ENCHANT_ERROR_BUFFER_TOO_SMALL;
    }

    memcpy(output, proto_output.data(), proto_output.size());
    *output_len = proto_output.size();
    return ENCHANT_SUCCESS;
}

int signed_prekey_record_deserialize(SignedPreKeyRecord& record, const uint8_t* input, size_t input_len) {
    if (!input) return ENCHANT_ERROR_NULL_POINTER;
    return proto::ProtobufSerializer::deserialize_signed_pre_key_record(input, input_len, record);
}

} // namespace protocol
} // namespace enchant
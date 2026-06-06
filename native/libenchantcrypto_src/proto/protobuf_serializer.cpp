#include "proto/protobuf_serializer.hpp"
#include <sodium.h>
#include <cstring>

namespace enchant {
namespace proto {

int ProtobufSerializer::serialize_session_record(
    const veil::EnvelopeState& envelope_state,
    uint32_t session_version,
    uint32_t remote_registration_id,
    uint32_t local_registration_id,
    const uint8_t* local_identity_public,
    const uint8_t* remote_identity_public,
    const std::vector<veil::EnvelopeState>* previous_sessions,
    std::vector<uint8_t>& output
) {
    if (!local_identity_public || !remote_identity_public) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    storage::RecordStructure record;
    storage::SessionStructure* session = record.mutable_current_session();

    if (previous_sessions) {
        for (const auto& prev : *previous_sessions) {
            size_t prev_size = prev.serialized_size();
            std::vector<uint8_t> prev_buf(prev_size);
            size_t prev_len = prev_size;
            int prc = prev.serialize(prev_buf.data(), &prev_len);
            if (prc == ENCHANT_SUCCESS) {
                record.add_previous_sessions(prev_buf.data(), prev_len);
            }
        }
    }

    session->set_session_version(session_version);
    session->set_local_identity_public(local_identity_public, 32);
    session->set_remote_identity_public(remote_identity_public, 32);
    session->set_remote_registration_id(remote_registration_id);
    session->set_local_registration_id(local_registration_id);

    std::vector<uint8_t> envelope_buf(envelope_state.serialized_size());
    size_t envelope_len = envelope_buf.size();
    int rc = envelope_state.serialize(envelope_buf.data(), &envelope_len);
    if (rc != ENCHANT_SUCCESS) return rc;
    session->set_envelope_state_data(envelope_buf.data(), envelope_len);

    size_t serialized_size = record.ByteSizeLong();
    output.resize(serialized_size);

    if (!record.SerializeToArray(output.data(), static_cast<int>(serialized_size))) {
        return ENCHANT_ERROR_INTERNAL;
    }

    return ENCHANT_SUCCESS;
}

int ProtobufSerializer::deserialize_session_record(
    const uint8_t* data,
    size_t data_len,
    veil::EnvelopeState& envelope_state,
    uint32_t& session_version,
    uint32_t& remote_registration_id,
    uint32_t& local_registration_id,
    std::vector<uint8_t>& local_identity_public,
    std::vector<uint8_t>& remote_identity_public
) {
    if (!data) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    if (data_len == 0) {
        return ENCHANT_ERROR_INVALID_FORMAT;
    }

    storage::RecordStructure record;
    if (!record.ParseFromArray(data, static_cast<int>(data_len))) {
        return ENCHANT_ERROR_INVALID_FORMAT;
    }

    const storage::SessionStructure& session = record.current_session();

    session_version = session.session_version();
    remote_registration_id = session.remote_registration_id();
    local_registration_id = session.local_registration_id();

    if (session.local_identity_public().size() != 32 ||
        session.remote_identity_public().size() != 32) {
        return ENCHANT_ERROR_INVALID_FORMAT;
    }
    local_identity_public.resize(32);
    remote_identity_public.resize(32);
    std::memcpy(local_identity_public.data(), session.local_identity_public().data(), 32);
    std::memcpy(remote_identity_public.data(), session.remote_identity_public().data(), 32);

    const std::string& envelope_data = session.envelope_state_data();
    if (!envelope_data.empty()) {
        int rc = envelope_state.deserialize(
            reinterpret_cast<const uint8_t*>(envelope_data.data()),
            envelope_data.size());
        if (rc != ENCHANT_SUCCESS) return rc;
    }

    return ENCHANT_SUCCESS;
}

int ProtobufSerializer::serialize_sender_key_record(
    const groups::SenderKeyRecord& record,
    std::vector<uint8_t>& output
) {
    storage::SenderKeyRecordStructure proto_record;

    for (const auto& state : record.states) {
        storage::SenderKeyStateStructure* state_proto = proto_record.add_sender_key_states();

        state_proto->set_chain_id(state.key_id);
        state_proto->set_message_version(1);

        auto* chain_key = state_proto->mutable_sender_chain_key();
        chain_key->set_iteration(state.chain_key.iteration);
        chain_key->set_seed(state.chain_key.seed.data(), groups::SENDER_KEY_SEED_SIZE);
    }

    proto_record.set_sender_key_id_counter(record.sender_key_id_counter);

    size_t serialized_size = proto_record.ByteSizeLong();
    output.resize(serialized_size);

    if (!proto_record.SerializeToArray(output.data(), static_cast<int>(serialized_size))) {
        return ENCHANT_ERROR_INTERNAL;
    }

    return ENCHANT_SUCCESS;
}

int ProtobufSerializer::deserialize_sender_key_record(
    const uint8_t* data,
    size_t data_len,
    groups::SenderKeyRecord& record
) {
    if (!data) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    if (data_len == 0) {
        return ENCHANT_ERROR_INVALID_FORMAT;
    }

    storage::SenderKeyRecordStructure proto_record;
    if (!proto_record.ParseFromArray(data, static_cast<int>(data_len))) {
        return ENCHANT_ERROR_INVALID_FORMAT;
    }

    record.states.clear();
    record.sender_key_id_counter = proto_record.sender_key_id_counter();

    for (const auto& state_proto : proto_record.sender_key_states()) {
        groups::SenderKeyState state;
        state.key_id = state_proto.chain_id();

        uint32_t iteration = state_proto.sender_chain_key().iteration();
        size_t seed_size = state_proto.sender_chain_key().seed().size();
        if (seed_size != groups::SENDER_KEY_SEED_SIZE) {
            return ENCHANT_ERROR_INVALID_FORMAT;
        }
        state.chain_key = groups::SenderChainKey(
            iteration,
            reinterpret_cast<const uint8_t*>(state_proto.sender_chain_key().seed().data()));

        std::string sender_id(state_proto.sender_signing_key().public_().begin(),
                             state_proto.sender_signing_key().public_().end());
        state.sender_id = sender_id;

        record.states.push_back(std::move(state));
    }

    return ENCHANT_SUCCESS;
}

int ProtobufSerializer::serialize_pre_key_record(
    const protocol::PreKeyRecord& record,
    std::vector<uint8_t>& output
) {
    storage::PreKeyRecordStructure proto;
    proto.set_id(record.id.id);
    proto.set_public_key(record.public_key.data(), record.public_key.size());
    proto.set_private_key(record.private_key.data(), record.private_key.size());

    size_t serialized_size = proto.ByteSizeLong();
    output.resize(serialized_size);

    if (!proto.SerializeToArray(output.data(), static_cast<int>(serialized_size))) {
        return ENCHANT_ERROR_INTERNAL;
    }

    return ENCHANT_SUCCESS;
}

int ProtobufSerializer::deserialize_pre_key_record(
    const uint8_t* data,
    size_t data_len,
    protocol::PreKeyRecord& record
) {
    if (!data) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    if (data_len == 0) {
        return ENCHANT_ERROR_INVALID_FORMAT;
    }

    storage::PreKeyRecordStructure proto;
    if (!proto.ParseFromArray(data, static_cast<int>(data_len))) {
        return ENCHANT_ERROR_INVALID_FORMAT;
    }

    record.id.id = proto.id();

    if (proto.public_key().size() != record.public_key.size() ||
        proto.private_key().size() != record.private_key.size()) {
        return ENCHANT_ERROR_INVALID_KEY_SIZE;
    }
    std::memcpy(record.public_key.data(), proto.public_key().data(), proto.public_key().size());
    std::memcpy(record.private_key.data(), proto.private_key().data(), proto.private_key().size());

    return ENCHANT_SUCCESS;
}

int ProtobufSerializer::serialize_signed_pre_key_record(
    const protocol::SignedPreKeyRecord& record,
    std::vector<uint8_t>& output
) {
    storage::SignedPreKeyRecordStructure proto;
    proto.set_id(record.id.id);
    proto.set_public_key(record.public_key.data(), record.public_key.size());
    proto.set_private_key(record.private_key.data(), record.private_key.size());
    proto.set_signature(record.signature.data(), record.signature.size());
    proto.set_timestamp(0);

    size_t serialized_size = proto.ByteSizeLong();
    output.resize(serialized_size);

    if (!proto.SerializeToArray(output.data(), static_cast<int>(serialized_size))) {
        return ENCHANT_ERROR_INTERNAL;
    }

    return ENCHANT_SUCCESS;
}

int ProtobufSerializer::deserialize_signed_pre_key_record(
    const uint8_t* data,
    size_t data_len,
    protocol::SignedPreKeyRecord& record
) {
    if (!data) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    if (data_len == 0) {
        return ENCHANT_ERROR_INVALID_FORMAT;
    }

    storage::SignedPreKeyRecordStructure proto;
    if (!proto.ParseFromArray(data, static_cast<int>(data_len))) {
        return ENCHANT_ERROR_INVALID_FORMAT;
    }

    record.id.id = proto.id();

    if (proto.public_key().size() != record.public_key.size() ||
        proto.private_key().size() != record.private_key.size() ||
        proto.signature().size() != record.signature.size()) {
        return ENCHANT_ERROR_INVALID_KEY_SIZE;
    }
    std::memcpy(record.public_key.data(), proto.public_key().data(), proto.public_key().size());
    std::memcpy(record.private_key.data(), proto.private_key().data(), proto.private_key().size());
    std::memcpy(record.signature.data(), proto.signature().data(), proto.signature().size());

    return ENCHANT_SUCCESS;
}

int serialize_session_record(
    const veil::EnvelopeState& envelope_state,
    uint32_t session_version,
    uint32_t remote_registration_id,
    uint32_t local_registration_id,
    const uint8_t* local_identity_public,
    const uint8_t* remote_identity_public,
    const std::vector<veil::EnvelopeState>* previous_sessions,
    uint8_t* output,
    size_t* output_len
) {
    if (!output || !output_len) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    std::vector<uint8_t> temp_output;
    int rc = ProtobufSerializer::serialize_session_record(
        envelope_state, session_version, remote_registration_id, local_registration_id,
        local_identity_public, remote_identity_public, previous_sessions, temp_output
    );

    if (rc != ENCHANT_SUCCESS) {
        return rc;
    }

    if (temp_output.size() > *output_len) {
        *output_len = temp_output.size();
        return ENCHANT_ERROR_BUFFER_TOO_SMALL;
    }

    std::memcpy(output, temp_output.data(), temp_output.size());
    *output_len = temp_output.size();
    return ENCHANT_SUCCESS;
}

int deserialize_session_record(
    const uint8_t* data,
    size_t data_len,
    veil::EnvelopeState& envelope_state,
    uint32_t* session_version,
    uint32_t* remote_registration_id,
    uint32_t* local_registration_id,
    uint8_t* local_identity_public,
    uint8_t* remote_identity_public
) {
    if (!data) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    std::vector<uint8_t> local_pub, remote_pub;
    uint32_t version = 0;
    uint32_t remote_reg = 0;
    uint32_t local_reg = 0;

    int rc = ProtobufSerializer::deserialize_session_record(
        data, data_len, envelope_state, version, remote_reg, local_reg, local_pub, remote_pub
    );

    if (rc != ENCHANT_SUCCESS) {
        return rc;
    }

    if (session_version) *session_version = version;
    if (remote_registration_id) *remote_registration_id = remote_reg;
    if (local_registration_id) *local_registration_id = local_reg;
    if (local_identity_public) std::memcpy(local_identity_public, local_pub.data(), 32);
    if (remote_identity_public) std::memcpy(remote_identity_public, remote_pub.data(), 32);

    return ENCHANT_SUCCESS;
}

} // namespace proto
} // namespace enchant
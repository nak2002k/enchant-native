#ifndef ENCHANT_PROTO_PROTOBUF_SERIALIZER_HPP
#define ENCHANT_PROTO_PROTOBUF_SERIALIZER_HPP

#include <cstdint>
#include <cstddef>
#include <string>
#include <vector>
#include <unordered_map>
#include "enchant/error.h"
#include "secure/buffer.hpp"
#include "veil/envelope_state.hpp"
#include "groups/sender_key.hpp"
#include "protocol/prekey.hpp"
#include "storage.pb.h"

namespace enchant {
namespace proto {

class ProtobufSerializer {
public:
    static int serialize_session_record(
        const veil::EnvelopeState& envelope_state,
        uint32_t session_version,
        uint32_t remote_registration_id,
        uint32_t local_registration_id,
        const uint8_t* local_identity_public,
        const uint8_t* remote_identity_public,
        const std::vector<veil::EnvelopeState>* previous_sessions,
        std::vector<uint8_t>& output
    );

    static int deserialize_session_record(
        const uint8_t* data,
        size_t data_len,
        veil::EnvelopeState& envelope_state,
        uint32_t& session_version,
        uint32_t& remote_registration_id,
        uint32_t& local_registration_id,
        std::vector<uint8_t>& local_identity_public,
        std::vector<uint8_t>& remote_identity_public
    );

    static int serialize_sender_key_record(
        const groups::SenderKeyRecord& record,
        std::vector<uint8_t>& output
    );

    static int deserialize_sender_key_record(
        const uint8_t* data,
        size_t data_len,
        groups::SenderKeyRecord& record
    );

    static int serialize_pre_key_record(
        const protocol::PreKeyRecord& record,
        std::vector<uint8_t>& output
    );

    static int deserialize_pre_key_record(
        const uint8_t* data,
        size_t data_len,
        protocol::PreKeyRecord& record
    );

    static int serialize_signed_pre_key_record(
        const protocol::SignedPreKeyRecord& record,
        std::vector<uint8_t>& output
    );

    static int deserialize_signed_pre_key_record(
        const uint8_t* data,
        size_t data_len,
        protocol::SignedPreKeyRecord& record
    );
};

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
);

int deserialize_session_record(
    const uint8_t* data,
    size_t data_len,
    veil::EnvelopeState& envelope_state,
    uint32_t* session_version,
    uint32_t* remote_registration_id,
    uint32_t* local_registration_id,
    uint8_t* local_identity_public,
    uint8_t* remote_identity_public
);

} // namespace proto
} // namespace enchant

#endif
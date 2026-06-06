#ifndef ENCHANT_AGENTS_AGENT_SESSION_HPP
#define ENCHANT_AGENTS_AGENT_SESSION_HPP

#include <cstdint>
#include <cstddef>
#include "enchant/error.h"
#include "secure/buffer.hpp"
#include "agents/agent_id.hpp"
#include "protocol/x3dh.hpp"
#include "veil/envelope_state.hpp"

namespace enchant {
namespace agents {

struct AgentSession {
    veil::EnvelopeState envelope;
    std::string peer_id;

    AgentSession() = default;
    AgentSession(AgentSession&&) noexcept = default;
    AgentSession& operator=(AgentSession&&) noexcept = default;
    AgentSession(const AgentSession&) = delete;
    AgentSession& operator=(const AgentSession&) = delete;
};

int agent_session_initiate(
    const AgentIdentity& agent_identity,
    const protocol::KeyBundle& user_key_bundle,
    AgentSession& session
);

int agent_session_respond(
    const AgentIdentity& agent_identity,
    const uint8_t* prekey_message, size_t prekey_message_len,
    AgentSession& session,
    uint8_t* plaintext, size_t* plaintext_len
);

int agent_encrypt(
    AgentSession& session,
    const uint8_t* plaintext, size_t plaintext_len,
    uint8_t* output, size_t* output_len
);

int agent_decrypt(
    AgentSession& session,
    const uint8_t* message, size_t message_len,
    uint8_t* plaintext, size_t* plaintext_len
);

} // namespace agents
} // namespace enchant

#endif
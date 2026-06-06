#ifndef ENCHANT_AGENTS_AGENT_ID_HPP
#define ENCHANT_AGENTS_AGENT_ID_HPP

#include <cstdint>
#include <cstddef>
#include <string>
#include <vector>
#include "enchant/error.h"
#include "secure/buffer.hpp"

namespace enchant {
namespace agents {

constexpr size_t AGENT_OPK_BATCH_SIZE = 100;

struct AgentIdentity {
    std::string agent_id;
    secure::SecureBuffer identity_public;        // 32 bytes  - X25519 public key
    secure::SecureBuffer identity_private;       // 32 bytes  - X25519 private scalar
    secure::SecureBuffer signing_public;         // 32 bytes  - Ed25519 public key
    secure::SecureBuffer signing_private;        // 32 bytes  - Ed25519 seed
    secure::SecureBuffer signed_prekey_public;   // 32 bytes  - X25519
    secure::SecureBuffer signed_prekey_private;  // 32 bytes  - X25519
    secure::SecureBuffer signed_prekey_sig;      // 64 bytes  - Ed25519 signature
    std::vector<secure::SecureBuffer> opk_public_keys;
    std::vector<secure::SecureBuffer> opk_private_keys;

    AgentIdentity();
    AgentIdentity(AgentIdentity&&) noexcept;
    AgentIdentity& operator=(AgentIdentity&&) noexcept;
    AgentIdentity(const AgentIdentity&) = delete;
    AgentIdentity& operator=(const AgentIdentity&) = delete;
    ~AgentIdentity();

    void zero();
};

int agent_identity_create(const char* agent_id, AgentIdentity& identity);

void agent_identity_destroy(AgentIdentity& identity);

} // namespace agents
} // namespace enchant

#endif
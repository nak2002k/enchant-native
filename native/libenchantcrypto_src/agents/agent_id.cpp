#include "agents/agent_id.hpp"
#include "primitives/x25519.hpp"
#include "primitives/ed25519.hpp"
#include "primitives/random.hpp"

namespace enchant {
namespace agents {

AgentIdentity::AgentIdentity()
    : identity_public(32), identity_private(32),
      signing_public(32), signing_private(32),
      signed_prekey_public(32), signed_prekey_private(32),
      signed_prekey_sig(64) {}

AgentIdentity::AgentIdentity(AgentIdentity&& other) noexcept
    : agent_id(std::move(other.agent_id)),
      identity_public(std::move(other.identity_public)),
      identity_private(std::move(other.identity_private)),
      signing_public(std::move(other.signing_public)),
      signing_private(std::move(other.signing_private)),
      signed_prekey_public(std::move(other.signed_prekey_public)),
      signed_prekey_private(std::move(other.signed_prekey_private)),
      signed_prekey_sig(std::move(other.signed_prekey_sig)),
      opk_public_keys(std::move(other.opk_public_keys)),
      opk_private_keys(std::move(other.opk_private_keys)) {}

AgentIdentity& AgentIdentity::operator=(AgentIdentity&& other) noexcept {
    if (this != &other) {
        zero();
        agent_id = std::move(other.agent_id);
        identity_public = std::move(other.identity_public);
        identity_private = std::move(other.identity_private);
        signing_public = std::move(other.signing_public);
        signing_private = std::move(other.signing_private);
        signed_prekey_public = std::move(other.signed_prekey_public);
        signed_prekey_private = std::move(other.signed_prekey_private);
        signed_prekey_sig = std::move(other.signed_prekey_sig);
        opk_public_keys = std::move(other.opk_public_keys);
        opk_private_keys = std::move(other.opk_private_keys);
    }
    return *this;
}

AgentIdentity::~AgentIdentity() { zero(); }

void AgentIdentity::zero() {
    identity_public.zero();
    identity_private.zero();
    signing_public.zero();
    signing_private.zero();
    signed_prekey_public.zero();
    signed_prekey_private.zero();
    signed_prekey_sig.zero();
    for (auto& k : opk_public_keys) k.zero();
    for (auto& k : opk_private_keys) k.zero();
    opk_public_keys.clear();
    opk_private_keys.clear();
}

int agent_identity_create(const char* agent_id, AgentIdentity& identity) {
    if (!agent_id) return ENCHANT_ERROR_NULL_POINTER;

    identity.agent_id = agent_id;

    int rc = primitives::x25519_keypair(
        identity.identity_public.data(),
        identity.identity_private.data()
    );
    if (rc != ENCHANT_SUCCESS) return rc;

    rc = primitives::ed25519_keypair(
        identity.signing_public.data(),
        identity.signing_private.data()
    );
    if (rc != ENCHANT_SUCCESS) return rc;

    rc = primitives::x25519_keypair(
        identity.signed_prekey_public.data(),
        identity.signed_prekey_private.data()
    );
    if (rc != ENCHANT_SUCCESS) return rc;

    rc = primitives::ed25519_sign(
        identity.signed_prekey_public.data(), 32,
        identity.signing_private.data(),
        identity.signed_prekey_sig.data()
    );
    if (rc != ENCHANT_SUCCESS) return rc;

    identity.opk_public_keys.reserve(AGENT_OPK_BATCH_SIZE);
    identity.opk_private_keys.reserve(AGENT_OPK_BATCH_SIZE);

    for (size_t i = 0; i < AGENT_OPK_BATCH_SIZE; i++) {
        secure::SecureBuffer pub(32), priv(32);
        rc = primitives::x25519_keypair(pub.data(), priv.data());
        if (rc != ENCHANT_SUCCESS) return rc;
        identity.opk_public_keys.push_back(std::move(pub));
        identity.opk_private_keys.push_back(std::move(priv));
    }

    return ENCHANT_SUCCESS;
}

void agent_identity_destroy(AgentIdentity& identity) {
    identity.zero();
}

} // namespace agents
} // namespace enchant
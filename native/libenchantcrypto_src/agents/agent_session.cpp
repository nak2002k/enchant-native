#include "agents/agent_session.hpp"
#include "protocol/x3dh.hpp"
#include "primitives/x25519.hpp"
#include "primitives/random.hpp"
#include <cstring>

namespace enchant {
namespace agents {

int agent_session_initiate(
    const AgentIdentity& agent_identity,
    const protocol::KeyBundle& user_key_bundle,
    AgentSession& session
) {
    secure::SecureBuffer eph_pub(32), eph_priv(32);
    int rc = primitives::x25519_keypair(eph_pub.data(), eph_priv.data());
    if (rc != ENCHANT_SUCCESS) return rc;

    protocol::X3DHResult x3dh_result;
    rc = protocol::x3dh_initiate(
        agent_identity.identity_private.data(),
        eph_priv.data(),
        user_key_bundle.identity_public.data(),
        user_key_bundle.signed_prekey_public.data(),
        user_key_bundle.one_time_prekey_public.empty() ? nullptr
                                                       : user_key_bundle.one_time_prekey_public.data(),
        x3dh_result
    );
    if (rc != ENCHANT_SUCCESS) return rc;

    return session.envelope.init(
        x3dh_result.root_key.data(),
        x3dh_result.sending_chain_key.data(),
        user_key_bundle.signed_prekey_public.data(),
        x3dh_result.receiving_chain_key.data(),
        eph_pub.data(),
        eph_priv.data(),
        agent_identity.identity_public.data(),
        user_key_bundle.identity_public.data(),
        x3dh_result.pqr_key.data()
    );
}

int agent_session_respond(
    const AgentIdentity& agent_identity,
    const uint8_t* prekey_message, size_t prekey_message_len,
    AgentSession& session,
    uint8_t* plaintext, size_t* plaintext_len
) {
    if (!prekey_message || !plaintext || !plaintext_len)
        return ENCHANT_ERROR_NULL_POINTER;

    if (prekey_message_len < 32 + 32 + 4 + 4 + veil::ENVELOPE_HEADER_SIZE + 16)
        return ENCHANT_ERROR_CIPHERTEXT_TOO_SHORT;

    const uint8_t* their_ik = prekey_message;
    const uint8_t* their_ek = prekey_message + 32;

    protocol::X3DHResult x3dh_result;
    int rc = protocol::x3dh_respond(
        agent_identity.identity_private.data(),
        agent_identity.signed_prekey_private.data(),
        nullptr,
        their_ik, their_ek,
        x3dh_result
    );
    if (rc != ENCHANT_SUCCESS) return rc;

    rc = session.envelope.init(
        x3dh_result.root_key.data(),
        x3dh_result.sending_chain_key.data(),
        their_ek,
        x3dh_result.receiving_chain_key.data(),
        agent_identity.signed_prekey_public.data(),
        agent_identity.signed_prekey_private.data(),
        agent_identity.identity_public.data(),
        their_ik,
        x3dh_result.pqr_key.data()
    );
    if (rc != ENCHANT_SUCCESS) return rc;

    const uint8_t* header = prekey_message + 32 + 32 + 4 + 4;
    const uint8_t* ct = header + veil::ENVELOPE_HEADER_SIZE;
    size_t ct_len = prekey_message_len - (32 + 32 + 4 + 4 + veil::ENVELOPE_HEADER_SIZE);

    return session.envelope.decrypt(header, ct, ct_len, plaintext, plaintext_len);
}

int agent_encrypt(
    AgentSession& session,
    const uint8_t* plaintext, size_t plaintext_len,
    uint8_t* output, size_t* output_len
) {
    if (!plaintext || !output || !output_len) return ENCHANT_ERROR_NULL_POINTER;

    size_t max_ct_len = veil::ENVELOPE_HEADER_SIZE + plaintext_len + 16;
    if (*output_len < max_ct_len) {
        *output_len = max_ct_len;
        return ENCHANT_ERROR_BUFFER_TOO_SMALL;
    }

    secure::SecureBuffer header(veil::ENVELOPE_HEADER_SIZE);
    secure::SecureBuffer ct(plaintext_len + 16);
    size_t ct_len = plaintext_len + 16;

    int rc = session.envelope.encrypt(plaintext, plaintext_len, header.data(), ct.data(), &ct_len);
    if (rc != ENCHANT_SUCCESS) return rc;

    if (veil::ENVELOPE_HEADER_SIZE + ct_len > *output_len) {
        *output_len = veil::ENVELOPE_HEADER_SIZE + ct_len;
        return ENCHANT_ERROR_BUFFER_TOO_SMALL;
    }

    memcpy(output, header.data(), veil::ENVELOPE_HEADER_SIZE);
    memcpy(output + veil::ENVELOPE_HEADER_SIZE, ct.data(), ct_len);
    *output_len = veil::ENVELOPE_HEADER_SIZE + ct_len;

    return ENCHANT_SUCCESS;
}

int agent_decrypt(
    AgentSession& session,
    const uint8_t* message, size_t message_len,
    uint8_t* plaintext, size_t* plaintext_len
) {
    if (message_len < veil::ENVELOPE_HEADER_SIZE + 16) return ENCHANT_ERROR_CIPHERTEXT_TOO_SHORT;

    const uint8_t* header = message;
    const uint8_t* ct = message + veil::ENVELOPE_HEADER_SIZE;
    size_t ct_len = message_len - veil::ENVELOPE_HEADER_SIZE;

    return session.envelope.decrypt(header, ct, ct_len, plaintext, plaintext_len);
}

} // namespace agents
} // namespace enchant
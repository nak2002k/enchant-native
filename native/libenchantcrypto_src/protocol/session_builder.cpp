#include "enchant/protocol/session_builder.hpp"
#include "enchant/protocol/constants.hpp"
#include "protocol/prekey.hpp"
#include "protocol/address.hpp"
#include "primitives/x25519.hpp"
#include "primitives/ed25519.hpp"
#include "primitives/random.hpp"
#include "pq/ml_kem.hpp"
#include <cstring>

namespace enchant {
namespace protocol {

SessionBuilder::SessionBuilder(ISessionStore& session_store,
                               IIdentityKeyStore& identity_store)
    : session_store_(session_store), identity_store_(identity_store) {}

SessionBuilder::~SessionBuilder() {}

int SessionBuilder::process_prekey_bundle(const KeyBundle& bundle,
                                          const EnchantAddress& address) {
    if (!bundle.identity_public.data() || !bundle.signed_prekey_public.data()) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    if (!is_valid_registration_id(bundle.registration_id)) {
        return ENCHANT_ERROR_INVALID_REGISTRATION_ID;
    }

    int vrc = key_bundle_validate(bundle);
    if (vrc != ENCHANT_SUCCESS) return vrc;

    secure::SecureBuffer our_identity_private(32);
    secure::SecureBuffer our_ephemeral_private(32);
    secure::SecureBuffer our_signed_prekey_private(32);
    secure::SecureBuffer our_kyber_prekey_private(ML_KEM_768_SECRET_KEY_SIZE);

    {
        secure::SecureBuffer our_identity_public(32);
        int irc = identity_store_.get_identity_key_pair(
            our_identity_public.data(), our_identity_private.data());
        if (irc != ENCHANT_SUCCESS) return irc;
    }
    primitives::random_bytes(our_ephemeral_private.data(), 32);
    primitives::random_bytes(our_signed_prekey_private.data(), 32);

    {
        secure::SecureBuffer kyber_pub(ML_KEM_768_PUBLIC_KEY_SIZE);
        int krc = pq::ml_kem_768_keypair(kyber_pub.data(), our_kyber_prekey_private.data());
        if (krc != ENCHANT_SUCCESS) return krc;
    }

    IdentityKey identity_key(bundle.identity_public);
    if (!identity_store_.is_trusted_identity(address, identity_key, Direction::Sending)) {
        return ENCHANT_ERROR_UNTRUSTED_IDENTITY;
    }

    HandshakeResult result;
    std::unique_ptr<IHandshake> handshake;

    bool has_kyber = bundle.kyber_prekey_public.has_value() && bundle.kyber_prekey_id.has_value();
    if (has_kyber) {
        size_t kyber_size = bundle.kyber_prekey_public->size();
        size_t ct_size = (kyber_size == pq::ML_KEM_1024_PUBLIC_KEY_SIZE)
            ? pq::ML_KEM_1024_CIPHERTEXT_SIZE
            : pq::ML_KEM_768_CIPHERTEXT_SIZE;
        auto pqxdh = std::make_unique<PQXDHHandshake>(our_identity_private);
        pqxdh->set_their_kyber_prekey(bundle.kyber_prekey_public->data(), bundle.kyber_prekey_public->size());
        if (bundle.kyber_prekey_sig) {
            pqxdh->set_their_kyber_prekey_sig(bundle.kyber_prekey_sig->data(), bundle.kyber_prekey_sig->size());
        }
        pqxdh->set_our_signed_prekey_private(our_signed_prekey_private.data(), our_signed_prekey_private.size());
        handshake = std::move(pqxdh);
    } else {
        auto x3dh = std::make_unique<X3DHHandshake>(our_identity_private);
        x3dh->set_our_signed_prekey_private(our_signed_prekey_private.data(), our_signed_prekey_private.size());
        handshake = std::move(x3dh);
    }

    int rc = handshake->initiate(
        our_identity_private.data(),
        our_ephemeral_private.data(),
        bundle.identity_public.data(),
        bundle.signed_prekey_public.data(),
        bundle.one_time_prekey_public.data() ? bundle.one_time_prekey_public.data() : nullptr,
        result
    );

    if (rc != ENCHANT_SUCCESS) {
        return rc;
    }

    SessionState state;
    rc = state.init(result.root_key.data(),
                   result.sending_chain_key.data(),
                   result.receiving_chain_key.data(),
                   has_kyber ? bundle.kyber_prekey_public->data() : nullptr,
                   our_kyber_prekey_private.data(),
                   our_signed_prekey_private.data(),
                   our_identity_private.data(),
                   bundle.identity_public.data(),
                   result.pqr_key.data());
    if (rc != ENCHANT_SUCCESS) return rc;

    rc = state.set_registration_ids(0, bundle.registration_id);
    if (rc != ENCHANT_SUCCESS) return rc;

    SessionRecord record;
    record.set_current_session_state(std::move(state));

    rc = session_store_.store_session(address, record);
    if (rc != ENCHANT_SUCCESS) return rc;

    rc = identity_store_.save_identity(address, identity_key);
    if (rc != ENCHANT_SUCCESS) return rc;

    return ENCHANT_SUCCESS;
}

std::optional<SessionRecord> SessionBuilder::session(const EnchantAddress& address) {
    return session_store_.load_session(address);
}

void SessionBuilder::set_trust(const EnchantAddress& address, bool trusted) {
    identity_store_.set_trust(address, trusted);
}

int merge_session(SessionRecord& current, const SessionRecord& previous) {
    if (!previous.has_current_session()) {
        return ENCHANT_SUCCESS;
    }

    if (!current.has_current_session()) {
        current.set_current_session_state(*previous.get_current_session_state());
        for (const auto& prev : previous.get_previous_session_states()) {
            current.add_previous_session_state(prev);
        }
        return ENCHANT_SUCCESS;
    }

    const auto* current_state = current.get_current_session_state();
    const auto* previous_state = previous.get_current_session_state();

    if (!current_state || !previous_state) {
        return ENCHANT_SUCCESS;
    }

    current.add_previous_session_state(*current_state);
    current.set_current_session_state(*previous_state);

    for (const auto& prev : previous.get_previous_session_states()) {
        current.add_previous_session_state(prev);
    }

    return ENCHANT_SUCCESS;
}

} // namespace protocol
} // namespace enchant
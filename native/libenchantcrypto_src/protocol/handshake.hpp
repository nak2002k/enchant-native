#ifndef ENCHANT_PROTOCOL_HANDSHAKE_HPP
#define ENCHANT_PROTOCOL_HANDSHAKE_HPP

#include <cstdint>
#include <cstddef>
#include <memory>
#include "enchant/error.h"
#include "secure/buffer.hpp"
#include "protocol/x3dh.hpp"

namespace enchant {
namespace protocol {

enum class HandshakeType : uint8_t {
    X3DH = 1,
    PQXDH = 2
};

enum class HandshakeState : uint8_t {
    INITIAL = 0,
    PREKEYS_EXCHANGED = 1,
    SESSION_ESTABLISHED = 2,
    FAILED = 3
};

struct HandshakeResult {
    secure::SecureBuffer shared_secret;
    secure::SecureBuffer root_key;
    secure::SecureBuffer sending_chain_key;
    secure::SecureBuffer receiving_chain_key;
    secure::SecureBuffer ephemeral_public;
    secure::SecureBuffer identity_key;
    secure::SecureBuffer kyber_ciphertext;
    secure::SecureBuffer pqr_key;
    HandshakeType type;
    HandshakeState state;

    HandshakeResult();
    void zero();
};

class IHandshake {
public:
    virtual ~IHandshake() = default;

    [[nodiscard]] virtual HandshakeType get_type() const = 0;
    [[nodiscard]] virtual HandshakeState get_state() const = 0;

    [[nodiscard]] virtual int initiate(
        const uint8_t* our_identity_private,
        const uint8_t* our_ephemeral_private,
        const uint8_t* their_identity_public,
        const uint8_t* their_signed_prekey,
        const uint8_t* their_one_time_prekey,
        HandshakeResult& result
    ) = 0;

    [[nodiscard]] virtual int respond(
        const uint8_t* our_identity_private,
        const uint8_t* our_signed_prekey_private,
        const uint8_t* our_one_time_prekey_private,
        const uint8_t* their_identity_public,
        const uint8_t* their_ephemeral_public,
        HandshakeResult& result
    ) = 0;

    [[nodiscard]] virtual int process_message(
        const uint8_t* message, size_t message_len,
        HandshakeResult& result
    ) = 0;

    [[nodiscard]] virtual int get_message(
        uint8_t* output, size_t* output_len
    ) = 0;

    [[nodiscard]] virtual bool is_complete() const = 0;
    [[nodiscard]] virtual bool is_failed() const = 0;
};

class X3DHHandshake : public IHandshake {
public:
    X3DHHandshake();
    explicit X3DHHandshake(const secure::SecureBuffer& our_identity_private);
    ~X3DHHandshake() override;

    void set_our_signed_prekey_private(const uint8_t* priv, size_t len);

    [[nodiscard]] HandshakeType get_type() const override { return HandshakeType::X3DH; }
    [[nodiscard]] HandshakeState get_state() const override { return state_; }

    [[nodiscard]] int initiate(
        const uint8_t* our_identity_private,
        const uint8_t* our_ephemeral_private,
        const uint8_t* their_identity_public,
        const uint8_t* their_signed_prekey,
        const uint8_t* their_one_time_prekey,
        HandshakeResult& result
    ) override;

    [[nodiscard]] int respond(
        const uint8_t* our_identity_private,
        const uint8_t* our_signed_prekey_private,
        const uint8_t* our_one_time_prekey_private,
        const uint8_t* their_identity_public,
        const uint8_t* their_ephemeral_public,
        HandshakeResult& result
    ) override;

    [[nodiscard]] int process_message(
        const uint8_t* message, size_t message_len,
        HandshakeResult& result
    ) override;

    [[nodiscard]] int get_message(
        uint8_t* output, size_t* output_len
    ) override;

    [[nodiscard]] bool is_complete() const override { return state_ == HandshakeState::SESSION_ESTABLISHED; }
    [[nodiscard]] bool is_failed() const override { return state_ == HandshakeState::FAILED; }

private:
    secure::SecureBuffer our_identity_private_;
    secure::SecureBuffer our_signed_prekey_private_;
    secure::SecureBuffer our_ephemeral_private_;
    secure::SecureBuffer their_identity_public_;
    secure::SecureBuffer their_signed_prekey_;
    secure::SecureBuffer their_one_time_prekey_;
    HandshakeState state_;
    secure::SecureBuffer pending_message_;
};

class PQXDHHandshake : public IHandshake {
public:
    PQXDHHandshake();
    explicit PQXDHHandshake(const secure::SecureBuffer& our_identity_private);
    ~PQXDHHandshake() override;

    [[nodiscard]] HandshakeType get_type() const override { return HandshakeType::PQXDH; }
    [[nodiscard]] HandshakeState get_state() const override { return state_; }

    [[nodiscard]] int initiate(
        const uint8_t* our_identity_private,
        const uint8_t* our_ephemeral_private,
        const uint8_t* their_identity_public,
        const uint8_t* their_signed_prekey,
        const uint8_t* their_one_time_prekey,
        HandshakeResult& result
    ) override;

    [[nodiscard]] int respond(
        const uint8_t* our_identity_private,
        const uint8_t* our_signed_prekey_private,
        const uint8_t* our_one_time_prekey_private,
        const uint8_t* their_identity_public,
        const uint8_t* their_ephemeral_public,
        HandshakeResult& result
    ) override;

    [[nodiscard]] int process_message(
        const uint8_t* message, size_t message_len,
        HandshakeResult& result
    ) override;

    [[nodiscard]] int get_message(
        uint8_t* output, size_t* output_len
    ) override;

    [[nodiscard]] bool is_complete() const override { return state_ == HandshakeState::SESSION_ESTABLISHED; }
    [[nodiscard]] bool is_failed() const override { return state_ == HandshakeState::FAILED; }

    void set_their_kyber_prekey(const uint8_t* prekey, size_t len);
    void set_their_kyber_prekey_sig(const uint8_t* sig, size_t len);
    void set_our_kyber_prekey_private(const uint8_t* priv, size_t len);
    void set_our_signed_prekey_private(const uint8_t* priv, size_t len);

private:
    secure::SecureBuffer our_identity_private_;
    secure::SecureBuffer our_ephemeral_private_;
    secure::SecureBuffer our_signed_prekey_private_;
    secure::SecureBuffer their_identity_public_;
    secure::SecureBuffer their_signed_prekey_;
    secure::SecureBuffer their_kyber_prekey_;
    secure::SecureBuffer their_kyber_prekey_sig_;
    secure::SecureBuffer their_one_time_prekey_;
    secure::SecureBuffer our_kyber_prekey_private_;
    secure::SecureBuffer parsed_kyber_ciphertext_;
    HandshakeState state_;
    secure::SecureBuffer pending_message_;
};

std::unique_ptr<IHandshake> create_handshake(
    HandshakeType type,
    const secure::SecureBuffer& our_identity_private
);

HandshakeType detect_handshake_type(
    const uint8_t* message, size_t message_len
);

} // namespace protocol
} // namespace enchant

#endif
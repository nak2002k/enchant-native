#ifndef ENCHANT_ZK_ZKCREDENTIAL_PRESENTATION_HPP
#define ENCHANT_ZK_ZKCREDENTIAL_PRESENTATION_HPP

#include <array>
#include <cstdint>
#include <cstring>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include "credentials.hpp"
#include "zk/enchant_zkp/enchant_zkp.hpp"
#include "zk/enchant_zkp/sho_hmac_sha256.hpp"

namespace enchant {
namespace zk {
namespace zkcredential {

using enchant_zkp::RistrettoPoint;
using enchant_zkp::RistrettoScalar;
using enchant_zkp::ShoHmacSha256;
using enchant_zkp::sho_get_point;
using enchant_zkp::sho_get_scalar;
using enchant_zkp::Statement;

struct PresentationProofCommitments {
    RistrettoPoint C_x0;
    RistrettoPoint C_x1;
    RistrettoPoint C_V;
    std::vector<RistrettoPoint> C_y;
};

struct PresentationProof {
    PresentationProofCommitments commitments;
    std::vector<uint8_t> enchant_zkp_proof;
};

struct AttributeRef {
    size_t key_index;
    size_t first_point_index;
    size_t second_point_index;
    bool has_key;
};

struct AnyPublicKey {
    std::string id;
    std::array<RistrettoPoint, 2> G_a;
    RistrettoPoint A;
};

struct PublicKeyOrId {
    bool has_public_key;
    AnyPublicKey public_key;
    std::string id;

    static PublicKeyOrId from_public_key(const AnyPublicKey& pk) {
        PublicKeyOrId r;
        r.has_public_key = true;
        r.public_key = pk;
        r.id = pk.id;
        return r;
    }
    static PublicKeyOrId from_id(const std::string& id_str) {
        PublicKeyOrId r;
        r.has_public_key = false;
        r.id = id_str;
        return r;
    }
};

struct AnyKeyPair {
    RistrettoScalar a1;
    RistrettoScalar a2;
    PublicKeyOrId public_key_or_id;

    static AnyKeyPair without_public_key(AnyKeyPair kp) {
        kp.public_key_or_id.has_public_key = false;
        return kp;
    }
};

class PresentationProofBuilderCore {
public:
    PresentationProofBuilderCore() : authenticated_message_() {
        attr_points_.push_back(RistrettoPoint()); // identity placeholder
    }

    PresentationProofBuilderCore(const uint8_t* message, size_t message_len)
        : authenticated_message_(message_len) {
        if (message && message_len > 0) {
            authenticated_message_.assign(message, message + message_len);
        }
        attr_points_.push_back(RistrettoPoint()); // identity placeholder
    }

    void add_attribute(const std::vector<RistrettoPoint>& points,
                       const std::optional<AnyKeyPair>& key) {
        size_t first_index = attr_points_.size();
        for (const auto& p : points) {
            attr_points_.push_back(p);
        }

        size_t key_index = 0;
        bool has_key = false;
        if (key.has_value()) {
            has_key = true;
            const auto& k = key.value();
            bool found = false;
            for (size_t i = 0; i < encryption_keys_.size(); i++) {
                if (encryption_keys_[i].public_key_or_id.id == k.public_key_or_id.id) {
                    key_index = i;
                    found = true;
                    break;
                }
            }
            if (!found) {
                key_index = encryption_keys_.size();
                encryption_keys_.push_back(k);
            }
        }

        AttributeRef ref;
        ref.key_index = key_index;
        ref.has_key = has_key;
        ref.first_point_index = first_index;
        ref.second_point_index = first_index + points.size() - 1;
        attributes_.push_back(ref);
    }

    void set_public_attrs_point(const RistrettoPoint& p) {
        if (!attr_points_.empty()) {
            attr_points_[0] = p;
        }
    }

    const std::vector<uint8_t>& authenticated_message() const { return authenticated_message_; }
    const std::vector<AnyKeyPair>& encryption_keys() const { return encryption_keys_; }
    const std::vector<AttributeRef>& attributes() const { return attributes_; }
    const std::vector<RistrettoPoint>& attr_points() const { return attr_points_; }

private:
    std::vector<AnyKeyPair> encryption_keys_;
    std::vector<AttributeRef> attributes_;
    std::vector<RistrettoPoint> attr_points_;
    std::vector<uint8_t> authenticated_message_;
};

class PresentationProofBuilder {
public:
    explicit PresentationProofBuilder(const uint8_t* label, size_t label_len)
        : label_(label, label + label_len) {
        core_ = std::make_unique<PresentationProofBuilderCore>(label, label_len);
    }

    PresentationProofBuilder& add_attribute(const std::array<RistrettoPoint, 2>& points,
                                             const AnyKeyPair& key) {
        std::vector<RistrettoPoint> pts = {points[0], points[1]};
        core_->add_attribute(pts, key);
        return *this;
    }

    PresentationProofBuilder& add_revealed_attribute(const RistrettoPoint& point) {
        // Revealed attributes use identity as the attr_point.
        // The verifier checks them via the Z computation (subtracting yi * attr_point).
        // See Rust: self.core.add_attribute(&[RistrettoPoint::identity()], None);
        std::vector<RistrettoPoint> pts = {RistrettoPoint()}; // identity
        core_->add_attribute(pts, std::nullopt);
        return *this;
    }

    PresentationProof present(const CredentialPublicKey& public_key,
                               const Credential& credential,
                               const uint8_t* randomness);

private:
    std::vector<uint8_t> label_;
    std::unique_ptr<PresentationProofBuilderCore> core_;
};

class PresentationProofVerifier {
public:
    explicit PresentationProofVerifier(const uint8_t* label, size_t label_len)
        : label_(label, label + label_len),
          core_(label, label_len),
          public_attrs_sho_(label, label_len) {}

    PresentationProofVerifier& add_public_attribute(const std::vector<uint8_t>& attr) {
        public_attrs_sho_.absorb_and_ratchet(attr.data(), attr.size());
        return *this;
    }

    PresentationProofVerifier& add_attribute(const std::array<RistrettoPoint, 2>& points,
                                              const PublicKeyOrId& key) {
        std::vector<RistrettoPoint> pts = {points[0], points[1]};
        AnyKeyPair akp;
        akp.public_key_or_id = key;
        core_.add_attribute(pts, akp);
        return *this;
    }

    PresentationProofVerifier& add_attribute_without_verified_key(
            const std::array<RistrettoPoint, 2>& points,
            const std::string& key_id) {
        std::vector<RistrettoPoint> pts = {points[0], points[1]};
        AnyKeyPair akp;
        akp.public_key_or_id = PublicKeyOrId::from_id(key_id);
        core_.add_attribute(pts, akp);
        return *this;
    }

    PresentationProofVerifier& add_revealed_attribute(const RistrettoPoint& point) {
        std::vector<RistrettoPoint> pts = {point};
        core_.add_attribute(pts, std::nullopt);
        return *this;
    }

    bool verify(const CredentialKeyPair& key_pair, const PresentationProof& proof);

private:
    std::vector<uint8_t> label_;
    PresentationProofBuilderCore core_;
    ShoHmacSha256 public_attrs_sho_;
};

} // namespace zkcredential
} // namespace zk
} // namespace enchant

#endif

#ifndef ENCHANT_ZK_AK_PROOFS_ERRORS_HPP
#define ENCHANT_ZK_AK_PROOFS_ERRORS_HPP

namespace enchant {
namespace zk {
namespace ak_proofs {

enum class AkProofError {
    Ok = 0,
    BadArgs,
    VerificationFailure,
    ProofCreationFailure,
    InvalidKeySet,
    KeyNotFound,
};

} // namespace ak_proofs
} // namespace zk
} // namespace enchant

#endif

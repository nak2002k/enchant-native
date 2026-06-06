#ifndef ENCHANT_ZK_POKSHO_ERRORS_HPP
#define ENCHANT_ZK_POKSHO_ERRORS_HPP

namespace enchant {
namespace zk {
namespace enchant_zkp {

enum class PokshoError {
    Ok = 0,
    BadArgs,
    BadArgsWrongNumberOfScalarArgs,
    BadArgsWrongNumberOfPointArgs,
    BadArgsMissingScalarArg,
    BadArgsMissingPointArg,
    VerificationFailure,
    ProofCreationVerificationFailure,
};

} // namespace enchant_zkp
} // namespace zk
} // namespace enchant

#endif

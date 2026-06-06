#ifndef ENCHANT_ZK_ZKGROUP_ERRORS_HPP
#define ENCHANT_ZK_ZKGROUP_ERRORS_HPP

namespace enchant {
namespace zk {
namespace zkgroup {

enum class ZkGroupError {
    Ok = 0,
    BadArgs,
    VerificationFailure,
    ProofCreationFailure,
    InvalidCredential,
    ExpiredCredential,
};

} // namespace zkgroup
} // namespace zk
} // namespace enchant

#endif

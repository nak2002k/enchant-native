#ifndef ENCHANT_PROTOCOL_RECIPIENT_ERRORS_HPP
#define ENCHANT_PROTOCOL_RECIPIENT_ERRORS_HPP

#include <string>
#include <vector>
#include <cstdint>
#include "enchant/error.h"
#include "enchant/i_identity_store.hpp"

namespace enchant {
namespace protocol {

struct RecipientError {
    EnchantAddress address;
    int error_code;
    std::string message;

    RecipientError() : error_code(ENCHANT_SUCCESS) {}
    RecipientError(const EnchantAddress& addr, int code)
        : address(addr), error_code(code) {}
    RecipientError(const EnchantAddress& addr, int code, const std::string& msg)
        : address(addr), error_code(code), message(msg) {}
};

class RecipientErrorAggregator {
public:
    RecipientErrorAggregator();

    void add_error(const EnchantAddress& address, int error);
    void add_error(const EnchantAddress& address, int error, const std::string& message);

    const std::vector<RecipientError>& get_errors() const;
    bool has_errors() const;
    size_t error_count() const;
    void clear();

    std::vector<RecipientError> get_mismatched_errors() const;
    std::vector<RecipientError> get_untrusted_errors() const;
    std::vector<RecipientError> get_no_session_errors() const;

    std::string summarize() const;

private:
    std::vector<RecipientError> errors_;
};

} // namespace protocol
} // namespace enchant

#endif
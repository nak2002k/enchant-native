#include "enchant/protocol/recipient_errors.hpp"
#include <cstdio>

namespace enchant {
namespace protocol {

RecipientErrorAggregator::RecipientErrorAggregator() : errors_() {}

void RecipientErrorAggregator::add_error(const EnchantAddress& address, int error) {
    errors_.emplace_back(address, error);
}

void RecipientErrorAggregator::add_error(const EnchantAddress& address,
                                          int error,
                                          const std::string& message) {
    errors_.emplace_back(address, error, message);
}

const std::vector<RecipientError>& RecipientErrorAggregator::get_errors() const {
    return errors_;
}

bool RecipientErrorAggregator::has_errors() const {
    return !errors_.empty();
}

size_t RecipientErrorAggregator::error_count() const {
    return errors_.size();
}

void RecipientErrorAggregator::clear() {
    errors_.clear();
}

std::vector<RecipientError> RecipientErrorAggregator::get_mismatched_errors() const {
    std::vector<RecipientError> result;
    for (const auto& err : errors_) {
        if (err.error_code == ENCHANT_ERROR_MISMATCHED_DEVICES ||
            err.error_code == ENCHANT_ERROR_MISSING_DEVICES ||
            err.error_code == ENCHANT_ERROR_EXTRA_DEVICES) {
            result.push_back(err);
        }
    }
    return result;
}

std::vector<RecipientError> RecipientErrorAggregator::get_untrusted_errors() const {
    std::vector<RecipientError> result;
    for (const auto& err : errors_) {
        if (err.error_code == ENCHANT_ERROR_UNTRUSTED_IDENTITY) {
            result.push_back(err);
        }
    }
    return result;
}

std::vector<RecipientError> RecipientErrorAggregator::get_no_session_errors() const {
    std::vector<RecipientError> result;
    for (const auto& err : errors_) {
        if (err.error_code == ENCHANT_ERROR_NO_SESSION) {
            result.push_back(err);
        }
    }
    return result;
}

std::string RecipientErrorAggregator::summarize() const {
    char buf[4096];
    size_t pos = 0;

    pos += snprintf(buf + pos, sizeof(buf) - pos,
                   "Recipient errors (total %zu):\n",
                   errors_.size());

    for (size_t i = 0; i < errors_.size() && pos < sizeof(buf) - 100; i++) {
        const auto& err = errors_[i];
        pos += snprintf(buf + pos, sizeof(buf) - pos,
                       "  [%zu] %s:%u - error=%d (%s)\n",
                       i, err.address.name.c_str(), err.address.device_id,
                       static_cast<int>(err.error_code), err.message.c_str());
    }

    return std::string(buf);
}

} // namespace protocol
} // namespace enchant
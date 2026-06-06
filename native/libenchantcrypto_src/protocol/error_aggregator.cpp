#include "protocol/error_aggregator.hpp"
#include <cstdio>
#include <cstring>
#include <string>

namespace enchant {
namespace protocol {

DecryptionErrorAggregator::DecryptionErrorAggregator()
    : has_current_session_(false),
      has_previous_sessions_(false),
      receiver_chain_count_(0),
      last_failed_counter_(0),
      last_failed_device_id_(0) {
}

void DecryptionErrorAggregator::record_attempt(int error_code, const char* error_msg) {
    DecryptionFailureRecord record;
    record.error_code = error_code;
    if (error_msg) {
        record.error_message = error_msg;
    }
    record.failed_counter = last_failed_counter_;
    record.device_id = last_failed_device_id_;
    record.had_current_session = has_current_session_;
    record.had_previous_sessions = has_previous_sessions_;
    record.receiver_chain_count = receiver_chain_count_;
    failures_.push_back(record);
}

void DecryptionErrorAggregator::set_session_info(bool has_current, bool has_previous,
                                                   size_t chain_count) {
    has_current_session_ = has_current;
    has_previous_sessions_ = has_previous;
    receiver_chain_count_ = chain_count;
}

void DecryptionErrorAggregator::set_failed_message_info(uint32_t counter, uint32_t device_id) {
    last_failed_counter_ = counter;
    last_failed_device_id_ = device_id;
}

int DecryptionErrorAggregator::get_worst_error() const {
    if (failures_.empty()) {
        return ENCHANT_SUCCESS;
    }
    
    int worst = ENCHANT_SUCCESS;
    for (const auto& f : failures_) {
        if (f.error_code < worst) {
            worst = f.error_code;
        }
    }
    return worst;
}

std::string DecryptionErrorAggregator::summarize() const {
    std::string result;
    result.reserve(2048);

    result += "Decryption failures for peer (total " + std::to_string(failures_.size()) + "):\n";

    for (size_t i = 0; i < failures_.size(); i++) {
        const auto& f = failures_[i];
        result += "  [" + std::to_string(i) + "] error=" + std::to_string(f.error_code)
                + " (" + f.error_message + ") counter=" + std::to_string(f.failed_counter)
                + " device=" + std::to_string(f.device_id)
                + " chains=" + std::to_string(f.receiver_chain_count) + "\n";
    }

    if (has_current_session_) {
        result += "  Has current session with " + std::to_string(receiver_chain_count_) + " receiver chains\n";
    } else {
        result += "  No current session\n";
    }

    if (has_previous_sessions_) {
        result += "  Has previous sessions\n";
    }

    return result;
}

void DecryptionErrorAggregator::clear() {
    failures_.clear();
    has_current_session_ = false;
    has_previous_sessions_ = false;
    receiver_chain_count_ = 0;
    last_failed_counter_ = 0;
    last_failed_device_id_ = 0;
}

void SessionErrorLogger::log_decryption_failure(
    const std::string& peer_id,
    const DecryptionErrorAggregator& errors,
    const char* context
) {
    (void)peer_id;
    (void)errors;
    (void)context;
}

void SessionErrorLogger::log_identity_change(
    const std::string& peer_id,
    const uint8_t* old_key,
    const uint8_t* new_key
) {
    (void)peer_id;
    (void)old_key;
    (void)new_key;
}

void SessionErrorLogger::log_chain_corruption(
    const std::string& peer_id,
    size_t chain_index,
    const char* reason
) {
    (void)peer_id;
    (void)chain_index;
    (void)reason;
}

} // namespace protocol
} // namespace enchant
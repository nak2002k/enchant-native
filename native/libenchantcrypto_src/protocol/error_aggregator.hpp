#ifndef ENCHANT_PROTOCOL_ERROR_AGGREGATOR_HPP
#define ENCHANT_PROTOCOL_ERROR_AGGREGATOR_HPP

#include <string>
#include <vector>
#include <cstdint>
#include <cstddef>
#include "enchant/error.h"

namespace enchant {
namespace protocol {

struct DecryptionFailureRecord {
    int error_code;
    std::string error_message;
    uint32_t failed_counter;
    uint32_t device_id;
    bool had_current_session;
    bool had_previous_sessions;
    size_t receiver_chain_count;
    
    DecryptionFailureRecord()
        : error_code(ENCHANT_SUCCESS), failed_counter(0), device_id(0),
          had_current_session(false), had_previous_sessions(false),
          receiver_chain_count(0) {}
};

class DecryptionErrorAggregator {
public:
    DecryptionErrorAggregator();
    
    void record_attempt(int error_code, const char* error_msg);
    
    void set_session_info(bool has_current, bool has_previous, size_t chain_count);
    
    void set_failed_message_info(uint32_t counter, uint32_t device_id);
    
    int get_worst_error() const;
    
    std::string summarize() const;
    
    void clear();
    
    size_t failure_count() const { return failures_.size(); }
    
private:
    std::vector<DecryptionFailureRecord> failures_;
    bool has_current_session_;
    bool has_previous_sessions_;
    size_t receiver_chain_count_;
    uint32_t last_failed_counter_;
    uint32_t last_failed_device_id_;
};

class SessionErrorLogger {
public:
    static void log_decryption_failure(
        const std::string& peer_id,
        const DecryptionErrorAggregator& errors,
        const char* context
    );
    
    static void log_identity_change(
        const std::string& peer_id,
        const uint8_t* old_key,
        const uint8_t* new_key
    );
    
    static void log_chain_corruption(
        const std::string& peer_id,
        size_t chain_index,
        const char* reason
    );
};

} // namespace protocol
} // namespace enchant

#endif
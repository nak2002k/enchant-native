#include "enchant/protocol/session_record.hpp"
#include "enchant/error.h"
#include "enchant/protocol/constants.hpp"
#include <cstring>
#include <sodium.h>

namespace enchant {
namespace protocol {

SessionRecord::SessionRecord()
    : is_archived_(false) {}

SessionRecord::~SessionRecord() {
    zero();
}

SessionRecord::SessionRecord(const SessionRecord& other)
    : current_session_(other.current_session_ ? std::make_unique<SessionState>(*other.current_session_) : nullptr)
    , previous_sessions_(other.previous_sessions_)
    , is_archived_(other.is_archived_) {}

SessionRecord& SessionRecord::operator=(const SessionRecord& other) {
    if (this != &other) {
        zero();
        if (other.current_session_) {
            current_session_ = std::make_unique<SessionState>(*other.current_session_);
        }
        previous_sessions_ = other.previous_sessions_;
        is_archived_ = other.is_archived_;
    }
    return *this;
}

SessionRecord::SessionRecord(SessionRecord&& other) noexcept
    : current_session_(std::move(other.current_session_))
    , previous_sessions_(std::move(other.previous_sessions_))
    , is_archived_(other.is_archived_) {
    other.is_archived_ = false;
}

SessionRecord& SessionRecord::operator=(SessionRecord&& other) noexcept {
    if (this != &other) {
        zero();
        current_session_ = std::move(other.current_session_);
        previous_sessions_ = std::move(other.previous_sessions_);
        is_archived_ = other.is_archived_;
        other.is_archived_ = false;
    }
    return *this;
}

bool SessionRecord::has_current_session() const {
    return current_session_ != nullptr;
}

SessionState* SessionRecord::get_current_session_state() {
    return current_session_.get();
}

const SessionState* SessionRecord::get_current_session_state() const {
    return current_session_.get();
}

std::vector<SessionState>& SessionRecord::get_previous_session_states() {
    return previous_sessions_;
}

const std::vector<SessionState>& SessionRecord::get_previous_session_states() const {
    return previous_sessions_;
}

void SessionRecord::promote_previous_to_current() {
    if (previous_sessions_.empty()) {
        return;
    }

    current_session_ = std::make_unique<SessionState>(std::move(previous_sessions_.back()));
    previous_sessions_.pop_back();
}

size_t SessionRecord::promote_archived_session(size_t index) {
    if (index >= previous_sessions_.size()) {
        return 0;
    }

    auto it = previous_sessions_.begin() + index;
    auto promoted = std::make_unique<SessionState>(std::move(*it));
    previous_sessions_.erase(it);

    if (current_session_) {
        archive_current_session();
    }
    current_session_ = std::move(promoted);
    is_archived_ = false;
    return previous_sessions_.size();
}

void SessionRecord::archive_current_session() {
    if (!current_session_) {
        return;
    }

    while (previous_sessions_.size() >= MAX_ARCHIVED_STATES) {
        previous_sessions_.front().zero();
        previous_sessions_.erase(previous_sessions_.begin());
    }

    previous_sessions_.push_back(std::move(*current_session_));
    current_session_.reset();
    is_archived_ = true;
}

void SessionRecord::set_current_session_state(SessionState state) {
    current_session_ = std::make_unique<SessionState>(std::move(state));
    is_archived_ = false;
}

void SessionRecord::add_previous_session_state(SessionState state) {
    previous_sessions_.push_back(std::move(state));
}

void SessionRecord::clear() {
    zero();
}

size_t SessionRecord::serialized_size() const {
    size_t size = 0;
    size += 1;
    size += 1;

    if (current_session_) {
        size += 4 + current_session_->serialized_size();
    }

    size += 4;
    for (const auto& prev : previous_sessions_) {
        size += 4 + prev.serialized_size();
    }

    return size;
}

int SessionRecord::serialize(uint8_t* output, size_t* output_len) const {
    if (!output || !output_len) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    size_t needed = serialized_size();
    if (*output_len < needed) {
        *output_len = needed;
        return ENCHANT_ERROR_BUFFER_TOO_SMALL;
    }

    uint8_t* pos = output;

    *pos++ = has_current_session() ? 1 : 0;
    *pos++ = is_archived_ ? 1 : 0;

    if (current_session_) {
        size_t current_size = current_session_->serialized_size();
        *pos++ = (current_size >> 24) & 0xFF;
        *pos++ = (current_size >> 16) & 0xFF;
        *pos++ = (current_size >> 8) & 0xFF;
        *pos++ = current_size & 0xFF;

        size_t written = current_size;
        int rc = current_session_->serialize(pos, &written);
        if (rc != ENCHANT_SUCCESS) return rc;
        pos += current_size;
    }

    *pos++ = (previous_sessions_.size() >> 24) & 0xFF;
    *pos++ = (previous_sessions_.size() >> 16) & 0xFF;
    *pos++ = (previous_sessions_.size() >> 8) & 0xFF;
    *pos++ = previous_sessions_.size() & 0xFF;

    for (const auto& prev : previous_sessions_) {
        size_t prev_size = prev.serialized_size();
        *pos++ = (prev_size >> 24) & 0xFF;
        *pos++ = (prev_size >> 16) & 0xFF;
        *pos++ = (prev_size >> 8) & 0xFF;
        *pos++ = prev_size & 0xFF;

        size_t written = prev_size;
        int rc = prev.serialize(pos, &written);
        if (rc != ENCHANT_SUCCESS) return rc;
        pos += prev_size;
    }

    *output_len = pos - output;
    return ENCHANT_SUCCESS;
}

int SessionRecord::deserialize(const uint8_t* input, size_t input_len) {
    if (!input || input_len == 0) {
        return ENCHANT_ERROR_NULL_POINTER;
    }

    zero();

    const uint8_t* pos = input;
    size_t remaining = input_len;

    if (remaining < 2) return ENCHANT_ERROR_INVALID_FORMAT;
    bool has_current = (*pos++ != 0);
    is_archived_ = (*pos++ != 0);
    remaining -= 2;

    if (has_current) {
        if (remaining < 4) return ENCHANT_ERROR_INVALID_FORMAT;
        size_t current_size = (static_cast<size_t>(pos[0]) << 24) |
                             (static_cast<size_t>(pos[1]) << 16) |
                             (static_cast<size_t>(pos[2]) << 8) |
                             static_cast<size_t>(pos[3]);
        pos += 4;
        remaining -= 4;

        if (remaining < current_size) return ENCHANT_ERROR_INVALID_FORMAT;
        auto state = std::make_unique<SessionState>();
        int rc = state->deserialize(pos, current_size);
        if (rc != ENCHANT_SUCCESS) return rc;
        current_session_ = std::move(state);
        pos += current_size;
        remaining -= current_size;
    }

    if (remaining < 4) return ENCHANT_ERROR_INVALID_FORMAT;
    size_t prev_count = (static_cast<size_t>(pos[0]) << 24) |
                       (static_cast<size_t>(pos[1]) << 16) |
                       (static_cast<size_t>(pos[2]) << 8) |
                       static_cast<size_t>(pos[3]);
    pos += 4;
    remaining -= 4;

    previous_sessions_.reserve(prev_count);
    for (size_t i = 0; i < prev_count; ++i) {
        if (remaining < 4) return ENCHANT_ERROR_INVALID_FORMAT;
        size_t prev_size = (static_cast<size_t>(pos[0]) << 24) |
                          (static_cast<size_t>(pos[1]) << 16) |
                          (static_cast<size_t>(pos[2]) << 8) |
                          static_cast<size_t>(pos[3]);
        pos += 4;
        remaining -= 4;

        if (remaining < prev_size) return ENCHANT_ERROR_INVALID_FORMAT;
        SessionState prev_state;
        int rc = prev_state.deserialize(pos, prev_size);
        if (rc != ENCHANT_SUCCESS) return rc;
        previous_sessions_.push_back(std::move(prev_state));
        pos += prev_size;
        remaining -= prev_size;
    }

    return ENCHANT_SUCCESS;
}

void SessionRecord::zero() {
    if (current_session_) {
        current_session_->zero();
    }
    current_session_.reset();
    for (auto& prev : previous_sessions_) {
        prev.zero();
    }
    previous_sessions_.clear();
    is_archived_ = false;
}

} // namespace protocol
} // namespace enchant
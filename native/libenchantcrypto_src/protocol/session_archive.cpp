#include "protocol/session_archive.hpp"
#include <sodium.h>
#include <cstring>
#include <algorithm>

namespace enchant {
namespace protocol {

SessionArchiveManager::SessionArchiveManager(
    std::shared_ptr<IIdentityTrustStore> trust_store,
    std::shared_ptr<ISessionStore> session_store)
    : trust_store_(std::move(trust_store)),
      session_store_(std::move(session_store)) {}

void SessionArchiveManager::set_event_callback(SessionArchiveCallback callback) {
    callback_ = std::move(callback);
}

void SessionArchiveManager::emit_event(const SessionArchiveEvent& event) {
    event_history_[event.address].push_back(event);
    if (callback_) {
        callback_(event);
    }
}

SessionArchiveManager::ProcessKeyBundleResult SessionArchiveManager::process_key_bundle(
    const std::string& address,
    const uint8_t* new_identity_key,
    size_t key_len,
    uint32_t registration_id) {
    ProcessKeyBundleResult result{};
    result.trust_level = TrustLevel::UNTRUSTED;

    if (!trust_store_ || !session_store_) return result;

    auto change_info = trust_store_->detect_identity_change(address, new_identity_key, key_len);
    result.identity_changed = change_info.is_key_change;

    if (change_info.is_key_change) {
        auto now = std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::system_clock::now().time_since_epoch()).count();

        SessionArchiveEvent event;
        event.type = SessionArchiveEvent::Type::IDENTITY_KEY_CHANGED;
        event.address = address;
        event.timestamp_ms = static_cast<uint64_t>(now);
        emit_event(event);

        session_store_->archive_sibling_sessions(EnchantAddress(address, 0));
        result.session_archived = true;

        auto previous = session_store_->load_session(EnchantAddress(address, 0));
        if (previous.has_value()) {
            session_store_->archive_session(EnchantAddress(address, 0));
            result.previous_session_promoted = true;
        }

        trust_store_->save_identity(address, new_identity_key, key_len, registration_id);
    } else {
        trust_store_->save_identity(address, new_identity_key, key_len, registration_id);
    }

    result.trust_level = trust_store_->get_trust_level(address);
    return result;
}

int SessionArchiveManager::archive_all_sessions_for_address(const std::string& address) {
    if (!session_store_) return ENCHANT_ERROR_INTERNAL;

    session_store_->archive_sibling_sessions(EnchantAddress(address, 0));

    SessionArchiveEvent event;
    event.type = SessionArchiveEvent::Type::SESSION_ARCHIVED;
    event.address = address;
    event.timestamp_ms = 0;
    auto now = std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::system_clock::now().time_since_epoch()).count();
    event.timestamp_ms = static_cast<uint64_t>(now);
    emit_event(event);

    return ENCHANT_SUCCESS;
}

int SessionArchiveManager::archive_sessions_on_identity_rotation(
    const std::string& address,
    const uint8_t* new_identity_key,
    size_t key_len) {
    if (!trust_store_ || !session_store_) return ENCHANT_ERROR_INTERNAL;

    archive_all_sessions_for_address(address);

    trust_store_->save_identity(address, new_identity_key, key_len, 0);

    return ENCHANT_SUCCESS;
}

int SessionArchiveManager::promote_previous_session(const std::string& address) {
    if (!session_store_) return ENCHANT_ERROR_INTERNAL;

    auto record = session_store_->load_session(EnchantAddress(address, 0));
    if (!record.has_value()) return ENCHANT_ERROR_NO_SESSION;

    if (record->archived_session_count() == 0) return ENCHANT_ERROR_NO_SESSION;

    record->promote_previous_to_current();
    session_store_->store_session(EnchantAddress(address, 0), *record);

    SessionArchiveEvent event;
    event.type = SessionArchiveEvent::Type::SESSION_PROMOTED;
    event.address = address;
    auto now = std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::system_clock::now().time_since_epoch()).count();
    event.timestamp_ms = static_cast<uint64_t>(now);
    emit_event(event);

    return ENCHANT_SUCCESS;
}

int SessionArchiveManager::handle_incoming_session(
    const std::string& address,
    const SessionRecord& new_session) {
    if (!session_store_) return ENCHANT_ERROR_INTERNAL;

    auto existing = session_store_->load_session(EnchantAddress(address, 0));
    if (existing.has_value()) {
        if (existing->has_current_session()) {
            session_store_->archive_session(EnchantAddress(address, 0));
        }
    }

    session_store_->store_session(EnchantAddress(address, 0), new_session);

    SessionArchiveEvent event;
    event.type = SessionArchiveEvent::Type::NEW_SESSION_CREATED;
    event.address = address;
    auto now = std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::system_clock::now().time_since_epoch()).count();
    event.timestamp_ms = static_cast<uint64_t>(now);
    emit_event(event);

    return ENCHANT_SUCCESS;
}

size_t SessionArchiveManager::get_archive_size(const std::string& address) const {
    if (!session_store_) return 0;

    auto record = session_store_->load_session(EnchantAddress(address, 0));
    if (!record.has_value()) return 0;

    return record->archived_session_count();
}

bool SessionArchiveManager::has_current_session(const std::string& address) const {
    if (!session_store_) return false;

    auto record = session_store_->load_session(EnchantAddress(address, 0));
    return record.has_value() && record->has_current_session();
}

std::vector<SessionArchiveEvent> SessionArchiveManager::get_event_history(
    const std::string& address) const {
    auto it = event_history_.find(address);
    if (it == event_history_.end()) return {};
    return it->second;
}

} // namespace protocol
} // namespace enchant

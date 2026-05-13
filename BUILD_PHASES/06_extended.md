# Phase 6 — Extended Features

## Overview

Build stickers (browse, install, send), polls (create, vote, close), location sharing (map picker), all settings screens (account, security, privacy, notifications, appearance, chats, storage, about, blocked users), backup/restore, and notification preferences.

**Estimated files:** 25 files
**Backend endpoints:** Stickers (8017), Polls (8013), Backup (8015), Notif Pref (8020), Chats (8011), Blocking (8007)

---

## Backend API Contracts

### Stickers (:8017)
| Endpoint | Auth | Request | Response |
|---|---|---|---|
| `GET /v1/stickers/packs/featured` | None | — | `{"packs": [{"pack_id": "uuid", "title": "...", "cover": "media_id", "sticker_count": N}]}` |
| `GET /v1/stickers/packs/search?q=` | None | — | `{"packs": [...]}` |
| `GET /v1/stickers/packs/{pack_id}` | None | — | `{"pack_id": "uuid", "title": "...", "author": "...", "stickers": [{"sticker_id": "uuid", "emoji": "😊"}]}` |
| `POST /v1/stickers/library/{pack_id}` | JWT | — | `{"installed": true}` |
| `DELETE /v1/stickers/library/{pack_id}` | JWT | — | `{"uninstalled": true}` |
| `GET /v1/stickers/library` | JWT | — | `{"packs": [{"pack_id": "uuid", "title": "...", "installed_at": "timestamp"}]}` |
| `GET /v1/stickers/recent` | JWT | — | `{"stickers": [{"pack_id": "uuid", "sticker_id": "uuid", "emoji": "😊", "last_used": "timestamp"}]}` |
| `POST /v1/stickers/recent/{sticker_id}` | JWT | — | `{"recorded": true}` |

### Polls (:8013)
| Endpoint | Auth | Request | Response |
|---|---|---|---|
| `POST /v1/polls` | JWT | `{"conversation_id": "uuid", "question": "?", "options": [{"text": "..."}], "closes_in_seconds": 3600, "allow_multiple": false, "anonymous": false}` | `{"poll_id": "uuid", "question": "...", "options": [...], "status": "OPEN"}`. Options: 2-12, closes_in: 60-604800, 5/day |
| `POST /v1/polls/{id}/vote` | JWT | `{"option_ids": ["uuid"]}` | `{"your_vote": ["uuid"], "results": {...}, "total_votes": N}` |
| `GET /v1/polls/{id}` | JWT | — | `{"poll_id": "...", "question": "...", "options": [...], "results": {...}, "your_vote": [...]}` |
| `PUT /v1/polls/{id}/close` | JWT | — | `{"closed": true}` | Creator only |
| `DELETE /v1/polls/{id}` | JWT | — | `{"deleted": true}` | Creator only |

### Backup (:8015)
| Endpoint | Auth | Request | Response |
|---|---|---|---|
| `POST /v1/backup/initiate` | JWT | `{"version": 1, "total_chunks": N, "total_size": N}` | `{"backup_id": "uuid", "status": "INITIATED"}`. Max 3 versions |
| `PUT /v1/backup/chunk/{backup_id}` | JWT | Raw binary + `X-Chunk-Index` + `X-Byte-Offset` | `{"received": true}` |
| `POST /v1/backup/finalize/{backup_id}` | JWT | `{"sha256": "hex"}` | `{"status": "COMPLETED", "version": N}` |
| `GET /v1/backup/latest` | JWT | — | `{"backup_id": "uuid", "version": N, "total_size": N, "completed_ts": "..."}` |
| `GET /v1/backup/download/{backup_id}` | JWT | — | Raw binary. 3/day |
| `DELETE /v1/backup` | JWT | — | `{"deleted": true}` |

### Notif Pref (:8020)
| Endpoint | Auth | Request | Response |
|---|---|---|---|
| `GET /v1/notifications/preferences` | JWT | — | `{"master_notifications_on": true, "message_notifications_on": true, "show_preview": true, "dnd_enabled": false, "dnd_start_time": "", "dnd_end_time": "", "dnd_timezone": ""}` |
| `PUT /v1/notifications/preferences` | JWT | Partial update of any fields | `{"updated": true}` |
| `PUT /v1/notifications/preferences/conversations/{conversation_id}` | JWT | `{"muted": true, "mute_duration_seconds": 28800, "mentions_only": false}` | `{"updated": true}` |

---

## File Manifest

### `feature/stickers/src/main/java/org/enchant/stickers/StickerViewModel.kt`
| Function | Signature | Description |
|---|---|---|
| `loadFeatured` | `suspend fun loadFeatured(): Result<List<StickerPack>>` | GET /v1/stickers/packs/featured | No auth needed |
| `searchPacks` | `suspend fun searchPacks(query: String): Result<List<StickerPack>>` | GET /v1/stickers/packs/search | Debounced 300ms |
| `loadPackDetail` | `suspend fun loadPackDetail(packId: String): Result<StickerPackDetail>` | GET /v1/stickers/packs/{pack_id} | |
| `installPack` | `suspend fun installPack(packId: String): Result<Unit>` | POST /v1/stickers/library/{pack_id} | |
| `uninstallPack` | `suspend fun uninstallPack(packId: String): Result<Unit>` | DELETE /v1/stickers/library/{pack_id} | |
| `loadLibrary` | `suspend fun loadLibrary(): Result<List<LibraryPack>>` | GET /v1/stickers/library | Installed packs |
| `loadRecent` | `suspend fun loadRecent(): Result<List<RecentSticker>>` | GET /v1/stickers/recent | |
| `recordStickerUse` | `suspend fun recordStickerUse(stickerId: String)` | POST /v1/stickers/recent/{sticker_id} | |
| `sendSticker` | `suspend fun sendSticker(packId: String, stickerId: String, conversationId: String)` | Send as sticker message via chat pipeline | |

**Tests:** 8 — load featured, search, load detail, install/uninstall, library, recent, send sticker, error handling

### `feature/stickers/src/main/java/org/enchant/stickers/StickerPicker.kt`
Bottom sheet component: tabs for installed packs + recent. Grid of stickers per pack. Tap to send.

**Tests:** 4 — render with packs, switch tab, tap sticker sends, empty state

### `feature/polls/src/main/java/org/enchant/polls/PollViewModel.kt`
| Function | Signature | Description |
|---|---|---|
| `createPoll` | `suspend fun createPoll(question: String, options: List<String>, allowMultiple: Boolean, closeInSeconds: Int?): Result<String>` | POST /v1/polls | 2-12 options, 60-604800 close time |
| `vote` | `suspend fun vote(pollId: String, optionIds: List<String>)` | POST /v1/polls/{id}/vote | Optimistic UI update |
| `loadPoll` | `suspend fun loadPoll(pollId: String): Result<PollData>` | GET /v1/polls/{id} |
| `closePoll` | `suspend fun closePoll(pollId: String)` | PUT /v1/polls/{id}/close | Creator only |

**Tests:** 6 — create (various options), vote, close, get poll, errors

### `feature/polls/src/main/java/org/enchant/polls/PollBubble.kt`
Composable that renders poll: question, options with vote bars, percentage, voted indicator. Tap to vote. Gray out if closed.

**Tests:** 4 — render open/closed, vote, see results, multiple votes

### `feature/location/src/main/java/org/enchant/location/LocationPickerScreen.kt`
Map (OpenStreetMap via osmdroid), current location, pin drop, search address.

| Function | Description |
|---|---|
| `onMapClick(lat, lng)` | Drop pin, reverse geocode address |
| `onCurrentLocation()` | Request location permission → center map on GPS |
| `searchAddress(query)` | Geocoder.getFromLocationName |
| `sendLocation()` | Return lat/lng/address to caller → encrypt → send as message → POST /v1/location |

**Tests:** 4 — render map, pin drop, current location, search address

### `feature/settings/src/main/java/org/enchant/settings/SettingsViewModel.kt`
| Function | Signature | Description |
|---|---|---|
| `loadSettings` | `fun loadSettings()` | Load from SecurePreferences + Notif Pref API |
| `updateTheme` | `suspend fun updateTheme(mode: ThemeMode)` | Store in SecurePreferences, apply immediately |
| `updateFontSize` | `suspend fun updateFontSize(scale: Float)` | Store, apply to chat screens (0.8-1.4 range) |
| `updateNotificationPrefs` | `suspend fun updateNotificationPrefs(prefs: NotificationPrefs)` | PUT /v1/notif-pref/global |
| `updatePrivacy` | `suspend fun updatePrivacy(settings: PrivacySettings)` | PUT /v1/profile/privacy |
| `muteConversation` | `suspend fun muteConversation(convId: String, durationSeconds: Long?)` | PUT /v1/notif-pref/conversations/{conv_id} |
| `loadDevices` | `suspend fun loadDevices(): Result<List<DeviceInfo>>` | GET /v1/auth/devices |
| `revokeDevice` | `suspend fun revokeDevice(deviceId: String)` | DELETE /v1/auth/devices/{id} |
| `getStorageUsage` | `suspend fun getStorageUsage(): StorageStats` | Local DB stats + media cache size |
| `clearCache` | `suspend fun clearCache()` | Clear image cache, media cache, old messages |
| `deleteAccount` | `suspend fun deleteAccount(): Result<Unit>` | DELETE /v1/auth/account + clear ALL local data |

**Screens:** SettingsHomeScreen, AccountSettingsScreen, SecuritySettingsScreen, PrivacySettingsScreen, NotificationsSettingsScreen, AppearanceSettingsScreen, ChatsSettingsScreen, StorageSettingsScreen, AboutScreen, BlockedUsersScreen

**Tests per screen:** 4-8 — render, interact, save, load persisted state

### `feature/backup/src/main/java/org/enchant/backup/BackupViewModel.kt`
| Function | Signature | Description |
|---|---|---|
| `initiateBackup` | `suspend fun initiateBackup(): Result<String>` | POST /v1/backup/initiate with total chunks |
| `uploadChunk` | `suspend fun uploadChunk(backupId: String, chunkIndex: Int, data: ByteArray)` | PUT /v1/backup/chunk/{backup_id} with `X-Chunk-Index` and `X-Byte-Offset` headers |
| `finalizeBackup` | `suspend fun finalizeBackup(backupId: String, sha256: String)` | POST /v1/backup/finalize/{backup_id} |
| `getLatestBackup` | `suspend fun getLatestBackup(): Result<BackupInfo>` | GET /v1/backup/latest |
| `downloadBackup` | `suspend fun downloadBackup(backupId: String): Result<ByteArray>` | GET /v1/backup/download/{backup_id} | 3/day |
| `deleteBackup` | `suspend fun deleteBackup(): Result<Unit>` | DELETE /v1/backup |
| `restoreBackup` | `suspend fun restoreBackup(backupData: ByteArray): Result<Unit>` | Parse + restore each message/contact/group to DB |

**Tests:** 8 — initiate, chunk upload, finalize, get latest, download, delete, restore, progress tracking

---

## Module: `feature/backup/src/main/java/org/enchant/backup/archive/`

**Purpose:** Per-type archive exporters and importers for granular backup/restore. Signal's `ChatArchiveExporter`, `ContactArchiveExporter`, `GroupArchiveExporter`, etc. equivalent.

Each archive type handles its own serialization format. All are bundled into a single encrypted backup file.

### `BackupArchive.kt` — Archive Container Format

The backup file is an encrypted archive containing multiple sections:

```
Backup file structure:
  [Header: magic bytes "ENCHBKP" + version byte + backup_key_id]
  [Encrypted Section 1: Chat Archives]
  [Encrypted Section 2: Contact Archives]
  [Encrypted Section 3: Group Archives]
  [Encrypted Section 4: Call Archives]
  [Encrypted Section 5: Settings]
  [HMAC-SHA256: integrity check over entire file]
```

| Function | Signature | Description |
|---|---|---|
| `EncryptSection` | `suspend fun encryptSection(data: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray` | XChaCha20-Poly1305 encrypt a section | Key derived from backup key via HKDF |
| `DecryptSection` | `suspend fun decryptSection(ciphertext: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray` | Decrypt a section | Integrity check via AEAD |
| `verifyIntegrity` | `suspend fun verifyIntegrity(file: File, backupKey: ByteArray): Boolean` | Verify HMAC over entire file | — |
| `backupKey` | `val backupKey: ByteArray` | Get derived backup encryption key from HKDF(backup_key_id, user_id) | Key stored in SignalStore.backup |

### `ChatArchiveExporter.kt`

| Function | Signature | Description |
|---|---|---|
| `exportChats` | `suspend fun exportChats(): List<ChatArchive>` | Export all messages + conversations from DB | Messages are exported as-is (already E2EE encrypted); no re-encryption needed |
| `importChats` | `suspend fun importChats(archives: List<ChatArchive>)` | Import messages back to DB | Deduplicate by envelopeId; preserve timestamps |

```kotlin
data class ChatArchive(val conversationId: String, val messages: List<ArchivedMessage>)
data class ArchivedMessage(val envelopeId: String, val senderId: String, val type: String, val payload: ByteArray,
    val timestamp: Long, val status: String, val reactions: List<ArchivedReaction>)
data class ArchivedReaction(val emoji: String, val userId: String, val timestamp: Long)
```

### `ContactArchiveExporter.kt`

| Function | Signature | Description |
|---|---|---|
| `exportContacts` | `suspend fun exportContacts(): List<ContactArchive>` | Export all contacts | Only contacts that were synced from server |
| `importContacts` | `suspend fun importContacts(archives: List<ContactArchive>)` | Import contacts back | Dedup by userId |

```kotlin
data class ContactArchive(val userId: String, val username: String, val displayName: String?, val phoneNumber: String?, val customName: String?)
```

### `GroupArchiveExporter.kt`

| Function | Signature | Description |
|---|---|---|
| `exportGroups` | `suspend fun exportGroups(context: Context): List<GroupArchive>` | Export group metadata (not messages — those are in ChatArchive) | Group membership, name, description, settings |
| `importGroups` | `suspend fun importGroups(context: Context, archives: List<GroupArchive>)` | Import group data | Must validate group still exists on server |

```kotlin
data class GroupArchive(val groupId: String, val name: String, val description: String?, val memberIds: List<String>, val settings: GroupSettings?)
```

### `AdHocCallArchiveExporter.kt`

| Function | Signature | Description |
|---|---|---|
| `exportCalls` | `suspend fun exportCalls(): List<CallArchive>` | Export call log entries | — |
| `importCalls` | `suspend fun importCalls(archives: List<CallArchive>)` | Import call log | Dedup by callId |

```kotlin
data class CallArchive(val callId: String, val remoteUserId: String, val type: String, val direction: String, val status: String, val durationSeconds: Int, val timestamp: Long)
```

### `BackupExporter.kt` — Orchestrator

| Function | Signature | Description |
|---|---|---|
| `exportFullBackup` | `suspend fun exportFullBackup(context: Context, outputPath: String, backupKey: ByteArray): Result<Unit>` | Run all exporters → encrypt each section → write to file + HMAC | One fails → partial backup with success/failure per section |
| `importFullBackup` | `suspend fun importFullBackup(context: Context, inputPath: String, backupKey: ByteArray, sections: Set<BackupSection>): Result<Unit>` | Read file → verify HMAC → decrypt sections → run importers | Selective restore (user can choose which sections to restore) |

```kotlin
enum class BackupSection { CHATS, CONTACTS, GROUPS, CALLS, SETTINGS }
```

**Tests per archive exporter:** 4 tests — export format, import roundtrip, import dedup, empty data
**Tests for backup orchestrator:** 5 tests — full export, full import, selective import, partial failure, HMAC verification fails
**Total backup tests:** 25 tests

---

## Acceptance Criteria (expanded)

All existing criteria plus:
- [ ] Sticker browser: featured/search/install/uninstall/send
- [ ] Recently used stickers tracked
- [ ] Poll creation with 2-12 options, vote (single/multi), results display, close
- [ ] Location picker: map, pin, search, send
- [ ] All 11 settings screens functional
- [ ] Theme (light/dark/system) applies immediately
- [ ] Font size changes reflected in chat immediately
- [ ] Notification preferences sync to server
- [ ] Backup flow: initiate → upload chunks → finalize
- [ ] Backup download and restore works
- [ ] Per-type archive: chats, contacts, groups, calls all export/import correctly
- [ ] Backup file integrity verification (HMAC)
- [ ] Selective restore: choose which sections to restore
- [ ] Device management: list, revoke
- [ ] Cache clearing works
- [ ] All tests pass (target: 130+ tests)

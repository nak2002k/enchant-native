# Phase 5 — Social

## Overview

Build groups (create, join via link, members, roles, settings), contacts (phone sync, match, friend requests), status/stories (24h expire, view receipts, privacy controls), channels (subscribe, discover, posts), and user profiles.

**Estimated files:** 30 files
**Backend endpoints:** Groups (8010), Contacts (8009), Status (8016), Channels (8018), Profile (8008), Blocking (8007)
**Prerequisites:** Phase 2 (auth) + Phase 3 (chat)

---

## Backend API Contracts

### Groups Service (:8010)

| Endpoint | Auth | Request | Response | Constraints |
|---|---|---|---|---|
| `POST /v1/groups` | JWT | `{"name": "...", "description": "?", "initial_member_ids": ["uuid"]?, "add_members_policy": "ALL_MEMBERS"?, "join_type": "INVITE_ONLY"?}` | `{"group_id": "uuid", "name": "...", "member_count": N}` | name 1-100 chars, description max 512, max 500 members, 5 groups/day |
| `GET /v1/groups` | JWT | — | `{"groups": [{"group_id": "uuid", "name": "...", "role": "MEMBER", ...}]}` | 30/min |
| `GET /v1/groups/{id}/members` | JWT | — | `{"members": [{"user_id": "uuid", "role": "MEMBER"}]}` | 30/min |
| `POST /v1/groups/{id}/members` | JWT | `{"user_ids": ["uuid", ...]}` | `{"added": N}` | at least 1 userId, 100/h |
| `DELETE /v1/groups/{id}/members/{uid}` | JWT | — | `{"removed": true}` | 100/h |
| `PUT /v1/groups/{id}` | JWT | `{"name": "?", "description": "?", "add_members_policy": "?", "edit_info_policy": "?", "join_type": "?"}` | `{"updated": true}` | 10/h |
| `PUT /v1/groups/{id}/members/{uid}/role` | JWT | `{"role": "MEMBER"|"ADMIN"|"SUPERADMIN"}` | `{"updated": true}` | 10/h |
| `PUT /v1/groups/{id}/owner` | JWT | `{"new_owner_user_id": "uuid"}` | `{"updated": true}` | Owner only |
| `DELETE /v1/groups/{id}` | JWT | — | `{"deleted": true}` | Owner only, soft delete |
| `POST /v1/groups/{id}/invite-link` | JWT | `{"expires_ts": "?", "max_uses": 0}` | `{"link_code": "...", "expires_ts": "...", "max_uses": N}` | max_uses 0-1000, 10/day, max 10 active links |
| `DELETE /v1/groups/{id}/invite-link/{link_id}` | JWT | — | `{"revoked": true}` | — |
| `POST /v1/groups/join/{link_code}` | JWT | — | `{"group_id": "uuid", "name": "...", ...}` | 20/h |
| `GET /v1/groups/join/{link_code}` | None | — | `{"name": "...", "description": "...", "member_count": N}` | Preview, no auth |
| `GET /v1/groups/{id}/join-requests` | JWT | — | `{"requests": [{"request_id": "uuid", "requester_user_id": "uuid", "status": "PENDING"}]}` | Admin only |
| `PUT /v1/groups/{id}/join-requests/{rid}` | JWT | `{"approve": true}` | `{"approved": true}` | Admin only |

### Contacts Service (:8009)

| Endpoint | Auth | Request | Response | Constraints |
|---|---|---|---|---|
| `POST /v1/contacts` | JWT | `{"contact_user_id": "uuid", "custom_name": "?"}` | `{"added": true}` | 100/h, cannot add self, must be valid UUID |
| `GET /v1/contacts` | JWT | — | `{"contacts": [{"contact_user_id": "uuid", "custom_name": "?", "username": "?"}]}` | 200/min |
| `DELETE /v1/contacts/{id}` | JWT | — | `{"removed": true}` | 50/h |
| `GET /v1/contacts/check/{id}` | JWT | — | `{"is_contact": true}` | 100/min |
| `POST /v1/contacts/match` | None | `{"phone_hashes": ["sha256", ...]}` | `{"matches": [{"user_id": "uuid", "username": "...", "display_name": "?", "phone_hash": "..."}]}` | 30/min IP, max 1000 hashes |
| `POST /v1/friend-requests` | JWT | `{"to_user_id": "uuid"}` | `{"id": "uuid", "status": "pending"}` | Cannot send to self |
| `GET /v1/friend-requests/incoming` | JWT | — | `{"requests": [{"id": "uuid", "from_user_id": "uuid", "created_ts": "..."}]}` | — |
| `GET /v1/friend-requests/outgoing` | JWT | — | `{"requests": [{"id": "uuid", "to_user_id": "uuid", "created_ts": "..."}]}` | — |
| `PUT /v1/friend-requests/{id}/accept` | JWT | — | `{"status": "accepted", "friend_user_id": "uuid"}` | Creates bidirectional contacts |
| `PUT /v1/friend-requests/{id}/decline` | JWT | — | `{"status": "declined"}` | — |
| `DELETE /v1/friend-requests/{id}` | JWT | — | `{"status": "cancelled"}` | Sender only |

### Status/Stories Service (:8016)

| Endpoint | Auth | Request | Response | Constraints |
|---|---|---|---|---|
| `POST /v1/status` | JWT | `{"status_type": "TEXT", "text_content": "...", "background_color": "#FF5733", "media_id": "?", "privacy": "ALL_CONTACTS", "selected_contacts": ["uuid"]?}` | `{"status_id": "uuid", "expires_at": "iso8601"}` | max 30 active, 1/min |
| `GET /v1/status/feed` | JWT | — | `{"feed": [{"author": {"user_id", "username"}, "statuses": [{"status_id", "type", "text", "timestamp", "viewed"}]}]}` | Grouped by author, unseen first |
| `POST /v1/status/{id}/view` | JWT | — | `{"viewed": true}` | Records view event |
| `GET /v1/status/{id}/views` | JWT | — | `{"views": [{"user_id": "uuid", "viewed_ts": "..."}]}` | 60/h, poster only |
| `DELETE /v1/status/{id}` | JWT | — | `{"deleted": true}` | — |

### Channels Service (:8018)

| Endpoint | Auth | Request | Response | Constraints |
|---|---|---|---|---|
| `POST /v1/channels` | JWT | `{"name": "...", "handle": "...", "channel_type": "PUBLIC"|"PRIVATE", "description": "?"}` | `{"channel_id": "uuid", "handle": "..."}` | max 10 owned, 5/day |
| `POST /v1/channels/{id}/posts` | JWT | `{"post_type": "TEXT", "text_content": "...", "publish_ts": "?"}` | `{"post_id": "uuid"}` | 20/h |
| `GET /v1/channels/{id}/feed?before=&limit=` | Optional | — | `{"posts": [...], "pinned_post": {...}}` | Cursor pagination, limit max 50 |
| `POST /v1/channels/{id}/subscribe` | JWT | — | `{"subscribed": true}` | max 500 subscriptions |
| `DELETE /v1/channels/{id}/subscribe` | JWT | — | `{"unsubscribed": true}` | — |
| `GET /v1/channels/search?q=&page=&limit=` | None | — | `{"channels": [{"channel_id", "name", "handle", "subscriber_count"}]}` | 60/min IP |
| `POST /v1/channels/{id}/invite` | JWT | — | `{"invite_link": "..."}` | Owner only, for private channels |
| `PUT /v1/channels/{id}/admins/{uid}` | JWT | — | `{"updated": true}` | Owner only |
| `DELETE /v1/channels/{id}/admins/{uid}` | JWT | — | `{"updated": true}` | Owner only |
| `PUT /v1/channels/{id}/posts/{pid}` | JWT | `{"text_content": "..."}` | `{"edited": true}` | Owner/admin only |
| `POST /v1/channels/{id}/posts/{pid}/pin` | JWT | — | `{"pinned": true}` | Owner/admin, unpins previous pinned |
| `DELETE /v1/channels/{id}/posts/{pid}` | JWT | — | `{"deleted": true}` | Owner/admin, soft delete |

### Blocking Service (:8007)

| Endpoint | Auth | Response |
|---|---|---|
| `POST /v1/blocks/{uid}` | JWT | `{"blocked": true}` |
| `DELETE /v1/blocks/{uid}` | JWT | `{"unblocked": true}` |
| `GET /v1/blocks` | JWT | `{"blocked_users": [{"user_id": "uuid", "blocked_ts": "..."}]}` |

---

## File Manifest

### `feature/groups/src/main/java/org/enchant/groups/GroupViewModel.kt`
**Purpose:** Group management — Signal's `GroupManagerV2` equivalent.

| Function | Signature | Description |
|---|---|---|
| `loadMyGroups` | `suspend fun loadMyGroups()` | GET /v1/groups → store in DB | Empty → show empty state |
| `createGroup` | `suspend fun createGroup(name: String, description: String?, memberIds: List<String>): Result<String>` | POST /v1/groups | Name validation 1-100, create fails → show error |
| `addMembers` | `suspend fun addMembers(groupId: String, userIds: List<String>): Result<Unit>` | POST /v1/groups/{id}/members | At least 1 userId; max 500 members total |
| `removeMember` | `suspend fun removeMember(groupId: String, userId: String): Result<Unit>` | DELETE /v1/groups/{id}/members/{uid} | Cannot remove if last admin → promote first |
| `updateGroup` | `suspend fun updateGroup(groupId: String, name: String?, description: String?): Result<Unit>` | PUT /v1/groups/{id} | Name 1-100, description max 512 |
| `updateSettings` | `suspend fun updateSettings(groupId: String, settings: GroupSettings)` | PUT /v1/groups/{id}/settings | — |
| `updateMemberRole` | `suspend fun updateMemberRole(groupId: String, userId: String, role: MemberRole): Result<Unit>` | PUT /v1/groups/{id}/members/{uid}/role | Cannot demote self if only admin |
| `transferOwnership` | `suspend fun transferOwnership(groupId: String, newOwnerUserId: String): Result<Unit>` | PUT /v1/groups/{id}/owner | Only current owner can transfer |
| `leaveGroup` | `suspend fun leaveGroup(groupId: String): Result<Unit>` | DELETE /v1/groups/{id}/members/{self} | Cannot leave if only admin → transfer first |
| `deleteGroup` | `suspend fun deleteGroup(groupId: String): Result<Unit>` | DELETE /v1/groups/{id} | Owner only |
| `generateInviteLink` | `suspend fun generateInviteLink(groupId: String, maxUses: Int = 0, expiresAt: Long? = null): Result<String>` | POST /v1/groups/{id}/invite-link | maxUses 0-1000, max 10 active links |
| `revokeInviteLink` | `suspend fun revokeInviteLink(groupId: String, linkId: String): Result<Unit>` | DELETE /v1/groups/{id}/invite-link/{linkId} | — |
| `joinViaLink` | `suspend fun joinViaLink(linkCode: String): Result<String>` | POST /v1/groups/join/{link_code} | Invalid or expired → show error |
| `previewGroupFromLink` | `suspend fun previewGroupFromLink(linkCode: String): Result<GroupPreview>` | GET /v1/groups/join/{link_code} | No auth needed |
| `loadJoinRequests` | `suspend fun loadJoinRequests(groupId: String): Result<List<JoinRequest>>` | GET /v1/groups/{id}/join-requests | Admin only |
| `approveJoinRequest` | `suspend fun approveJoinRequest(groupId: String, requestId: String): Result<Unit>` | PUT /v1/groups/{id}/join-requests/{rid} | Admin only |
| `rejectJoinRequest` | `suspend fun rejectJoinRequest(groupId: String, requestId: String): Result<Unit>` | PUT /v1/groups/{id}/join-requests/{rid} | Admin only |

```kotlin
data class GroupPreview(val groupId: String, val name: String, val description: String?, val memberCount: Int)
data class GroupSettings(val messagingMode: String?, val disappearTimerSeconds: Int?)
enum class MemberRole { MEMBER, ADMIN, SUPERADMIN }
data class JoinRequest(val requestId: String, val requesterUserId: String, val requesterUsername: String, val status: String, val requestedTs: String)
```

**Tests:** 18 — each group operation success + error, role hierarchy enforced, invite link lifecycle, join request flow

---

### `feature/groups/src/main/java/org/enchant/groups/screens/`
| Screen | Contents | Tests |
|---|---|---|
| `GroupsScreen.kt` | List user's groups, FAB to create, tap to open | 4 |
| `CreateGroupScreen.kt` | Name input, description, member selector with search (show contacts + search by username) | 6 |
| `GroupInfoScreen.kt` | Name/avatar/description display, member list with roles, invite link share, settings (name/description), leave group, delete group | 8 |
| `GroupMemberListScreen.kt` | Member list with role indicators, admin management, add member | 4 |
| `GroupInviteScreen.kt` | Display invite link, copy, share, QR code, join via code input | 4 |
| `JoinRequestsScreen.kt` | List pending requests with approve/reject | 3 |

---

### `feature/contacts/src/main/java/org/enchant/contacts/ContactSyncService.kt`
**Purpose:** Phone contact sync with SHA-256 hashing and backend matching.

| Function | Signature | Description |
|---|---|---|
| `syncContacts` | `suspend fun syncContacts(): Result<List<MatchedContact>>` | Read device contacts → hash phones → POST /v1/contacts/match → store matches | No contacts permission → return empty; no matches → return empty |
| `hashPhoneNumber` | `fun hashPhoneNumber(e164: String): String` | SHA-256 of normalized E.164 number | Normalize: strip all non-digit; ensure starts with country code |
| `readDeviceContacts` | `suspend fun readDeviceContacts(): List<PhoneContact>` | Read from system contacts provider via `ContentResolver` | Permission denied → empty list; large contact list → paginated |
| `getMatchedContacts` | `fun getMatchedContacts(): Flow<List<MatchedContact>>` | Reactive list from local DB | — |

```kotlin
data class PhoneContact(val displayName: String, val phoneNumbers: List<String>)
data class MatchedContact(val userId: String, val username: String, val displayName: String?, val phoneHash: String, val matchedPhone: String)
```

**Tests:** 8 — hash correct, read contacts, match API success, match API empty, no permission, store in DB, reactive updates

---

### `feature/contacts/src/main/java/org/enchant/contacts/screens/`
| Screen | Contents | Tests |
|---|---|---|
| `ContactsScreen.kt` | List contacts with search, sync button, friend requests tabs (incoming/outgoing) | 6 |
| `AddContactScreen.kt` | Search by username (debounced), display results with add button | 4 |
| `ContactProfileScreen.kt` | Profile info: avatar, name, username, about. Actions: message, call, video call, block | 4 |
| `FriendRequestsScreen.kt` | Incoming (accept/decline) + outgoing (cancel) | 4 |

---

### `feature/status/src/main/java/org/enchant/status/StatusViewModel.kt`
**Purpose:** Status/stories management — Signal's story viewer equivalent.

| Function | Signature | Description |
|---|---|---|
| `loadFeed` | `suspend fun loadFeed()` | GET /v1/status/feed → group by author, unseen first | Empty → show empty; error → retry |
| `createTextStatus` | `suspend fun createTextStatus(text: String, backgroundColor: String, privacy: StatusPrivacy, selectedContacts: List<String>?): Result<String>` | POST /v1/status | text max 700 chars; privacy: ALL_CONTACTS, SELECTED, CLOSE_FRIENDS |
| `createMediaStatus` | `suspend fun createMediaStatus(mediaId: String, privacy: StatusPrivacy, selectedContacts: List<String>?): Result<String>` | Upload media first → POST /v1/status | — |
| `viewStatus` | `suspend fun viewStatus(statusId: String)` | POST /v1/status/{id}/view | — |
| `getViewers` | `suspend fun getViewers(statusId: String): Result<List<StatusViewer>>` | GET /v1/status/{id}/views | Own status only |
| `deleteStatus` | `suspend fun deleteStatus(statusId: String)` | DELETE /v1/status/{id} | Own status only |

```kotlin
sealed class StatusPrivacy { data object AllContacts : StatusPrivacy(); data class Selected(val userIds: List<String>) : StatusPrivacy(); data object CloseFriends : StatusPrivacy() }
data class StatusViewer(val userId: String, val username: String, val viewedAt: String)
```

**Tests:** 10 — create text/image status, load feed, view status, get viewers, delete, privacy settings, error handling

---

### `feature/status/src/main/java/org/enchant/status/screens/`
| Screen | Contents | Tests |
|---|---|---|
| `StatusFeedScreen.kt` | "My Status" ring → tap to view/create, recent updates (unviewed, sorted by recent), viewed updates (grayed) | 6 |
| `StatusCreateScreen.kt` | Text input + color picker (12 colors), add image/gif, privacy selector | 6 |
| `StatusViewerScreen.kt` | Full-screen with tap-to-advance, progress bar at top, pause on long press, reply button, view info | 4 |

---

### `feature/channels/src/main/java/org/enchant/channels/ChannelViewModel.kt`
| Function | Signature | Description |
|---|---|---|
| `loadFeed` | `suspend fun loadFeed(channelId: String, before: String?, limit: Int)` | GET /v1/channels/{id}/feed | Cursor pagination |
| `subscribe` | `suspend fun subscribe(channelId: String)` | POST /v1/channels/{id}/subscribe | Max 500 |
| `unsubscribe` | `suspend fun unsubscribe(channelId: String)` | DELETE /v1/channels/{id}/subscribe | — |
| `discoverChannels` | `suspend fun discoverChannels(query: String, page: Int)` | GET /v1/channels/search | 60/min |
| `createChannel` | `suspend fun createChannel(name: String, handle: String, description: String?, channelType: ChannelType): Result<String>` | POST /v1/channels | max 10 owned, handle unique |
| `loadMyChannels` | `suspend fun loadMyChannels()` | GET /v1/groups equivalent for channels | — |

**Tests:** 8 — load feed with pagination, subscribe/unsubscribe, discover, create, error states

---

### `feature/profile/src/main/java/org/enchant/profile/ProfileViewModel.kt`
| Function | Signature | Description |
|---|---|---|
| `loadProfile` | `suspend fun loadProfile(userId: String)` | GET /v1/profile/{user_id} | Privacy-filtered by server |
| `updateProfile` | `suspend fun updateProfile(displayName: String?, about: String?): Result<Unit>` | PUT /v1/profile | Validate locally first |
| `updateAvatar` | `suspend fun updateAvatar(uri: Uri): Result<String>` | Compress → POST /v1/profile/avatar | Max 5MB, JPEG/PNG only |
| `searchByUsername` | `suspend fun searchByUsername(prefix: String): Result<List<User>>` | GET /v1/profile/search | Debounced 300ms |
| `getBlockedUsers` | `suspend fun getBlockedUsers(): Result<List<BlockedUser>>` | GET /v1/blocks | — |
| `blockUser` | `suspend fun blockUser(userId: String)` | POST /v1/blocks/{uid} | Also delete conversation? |
| `unblockUser` | `suspend fun unblockUser(userId: String)` | DELETE /v1/blocks/{uid} | — |

**Tests:** 10 — profile CRUD, avatar upload, search, block/unblock, privacy settings

---

## Module: `feature/groups/src/main/java/org/enchant/groups/GroupEditor.kt`

**Purpose:** Processing-lock pattern for group mutations — ensures serialized, conflict-resolved edits. Signal's `GroupEditor` inner class equivalent.

```kotlin
class GroupEditor(private val groupId: String) {
    // All methods return GroupEditResult — success or conflict error
    // All mutations use commitChangeWithConflictResolution internally
```

| Function | Signature | Description | Must Handle |
|---|---|---|---|
| `addMembers` | `suspend fun addMembers(userIds: List<String>): GroupEditResult` | Add members to group | At least 1 userId, max 500 total |
| `removeMember` | `suspend fun removeMember(userId: String): GroupEditResult` | Remove a member | Cannot remove if last admin; cannot remove self (use leaveGroup) |
| `setMemberAdmin` | `suspend fun setMemberAdmin(userId: String, isAdmin: Boolean): GroupEditResult` | Promote/demote admin | Cannot demote self if only admin |
| `updateGroupTimer` | `suspend fun updateGroupTimer(seconds: Int): GroupEditResult` | Set disappearing message timer for group | 0 = off, 86400, 604800, 7776000 |
| `updateAttributesRights` | `suspend fun updateAttributesRights(policy: GroupAccessPolicy): GroupEditResult` | Set who can edit group info (name, description, avatar) | ALL_MEMBERS, ADMIN_ONLY |
| `updateMembershipRights` | `suspend fun updateMembershipRights(policy: GroupAccessPolicy): GroupEditResult` | Set who can add members | ALL_MEMBERS, ADMIN_ONLY |
| `setAnnouncementGroup` | `suspend fun setAnnouncementGroup(isAnnouncementOnly: Boolean): GroupEditResult` | Set announcement-only mode — only admins can send messages | Non-admins cannot send messages |
| `revokeInvites` | `suspend fun revokeInvites(userIds: List<String>): GroupEditResult` | Revoke pending invites | Only pending members can be revoked |
| `approveJoinRequest` | `suspend fun approveJoinRequest(requestId: String): GroupEditResult` | Approve a pending join request | Admin only |
| `denyJoinRequest` | `suspend fun denyJoinRequest(requestId: String): GroupEditResult` | Deny a pending join request | Admin only |
| `banUser` | `suspend fun banUser(userId: String): GroupEditResult` | Ban a user from the group | Banned users cannot rejoin; existing members are removed |
| `unbanUser` | `suspend fun unbanUser(userId: String): GroupEditResult` | Remove a ban | — |
| `ejectMember` | `suspend fun ejectMember(userId: String, block: Boolean = false, removeMessages: Boolean = false): GroupEditResult` | Eject a member with optional ban + message deletion | Admin only; if block=true, user is banned |
| `terminateGroup` | `suspend fun terminateGroup(): GroupEditResult` | Terminate group — all members removed, group marked deleted | Owner only; irreversible |
| `acceptInvite` | `suspend fun acceptInvite(): GroupEditResult` | Accept a pending group invite | Only works if user has a pending invite |
| `cycleGroupLinkPassword` | `suspend fun cycleGroupLinkPassword(): GroupEditResult` | Cycle the group invite link password — old link becomes invalid | Admin only |
| `setJoinByGroupLinkState` | `suspend fun setJoinByGroupLinkState(state: GroupLinkState): GroupEditResult` | Enable/disable join via link | DISABLED, ENABLED, APPROVAL_REQUIRED |
| `commitChangeWithConflictResolution` | `private suspend fun commitChangeWithConflictResolution(actions: GroupChange.Actions): GroupEditResult` | Commit change, retry on conflict by re-fetching state | Conflict → re-fetch latest group state → re-apply change → retry (max 3) |

```kotlin
sealed class GroupEditResult {
    data class Success(val updated: Boolean) : GroupEditResult()
    data class Conflict(val serverRevision: Int) : GroupEditResult()  // Client must retry with fresh data
    data class Failure(val reason: String, val isRetryable: Boolean) : GroupEditResult()
}

enum class GroupAccessPolicy { ALL_MEMBERS, ADMIN_ONLY }
enum class GroupLinkState { DISABLED, ENABLED, APPROVAL_REQUIRED }
```

**Conflict resolution strategy (inside `commitChangeWithConflictResolution`):**
1. Build change actions from current local state
2. Send to server (PUT /v1/groups/{id})
3. If 409 Conflict → re-fetch group state from server → merge local changes with server state → retry
4. Max 3 retries before giving up

**Test requirements:** 15 tests — add/remove member success, conflict resolution, admin promotion/demotion errors, timer update, rights update, announcement group toggle, revoke invites, approve/deny join requests, ban/unban/eject, terminate group, accept invite, cycle link password, link state toggle

---

## Module: `feature/groups/src/main/java/org/enchant/groups/GroupStateProcessor.kt`

**Purpose:** Processes group state updates from server (P2P changes and server pushes). Signal's `GroupsV2StateProcessor` equivalent.

| Function | Signature | Description | Must Handle |
|---|---|---|---|
| `processGroupUpdate` | `suspend fun processGroupUpdate(groupId: String, revision: Int, changeBytes: ByteArray?): GroupUpdateResult` | Apply a group state update from server push or P2P message | Conflicts → re-fetch; stale (local > revision) → ignore |
| `forceUpdateFromServer` | `suspend fun forceUpdateFromServer(groupId: String)` | Force-fetch latest group state from server | Used when P2P update fails or member suspects stale data |
| `getGroupChangeLog` | `suspend fun getGroupChangeLog(groupId: String, sinceRevision: Int): List<GroupChangeLogEntry>` | Get group change history from server | Used for audit and conflict resolution |
| `handleP2PChange` | `suspend fun handleP2PChange(groupId: String, revision: Int, changeData: ByteArray): Boolean` | Handle a P2P group change from another member | Must validate change is newer than local; must validate sender is member |
| `updateLocalGroupToRevision` | `suspend fun updateLocalGroupToRevision(groupId: String, targetRevision: Int): Boolean` | Update local DB to match server revision | Sequential — must apply all intermediate changes |

```kotlin
data class GroupUpdateResult(val success: Boolean, val newRevision: Int, val requiresRefresh: Boolean)
data class GroupChangeLogEntry(val revision: Int, val editor: String, val timestamp: Long, val description: String)
```

**Test requirements:** 6 tests — process update (newer), ignore stale (older), force update from server, get change log, handle P2P change, update to specific revision

---

## Acceptance Criteria (expanded)

All existing criteria plus:
- [ ] GroupEditor: add/remove members, promote/demote admin, timer update, rights update, announcement toggle
- [ ] Conflict resolution: 409 Conflict → re-fetch → retry → success (max 3 retries)
- [ ] Ban/unban users from groups, eject with optional message deletion
- [ ] Revoke pending invites, approve/deny join requests
- [ ] Accept pending invite, terminate group
- [ ] Cycle invite link password, toggle link enable state (ENABLED/APPROVAL_REQUIRED/DISABLED)
- [ ] Announcement-only mode: non-admins cannot send messages
- [ ] GroupStateProcessor: handles P2P updates, server pushes, force refresh
- [ ] Group change log accessible
- [ ] All tests pass (target: 130+ tests)
